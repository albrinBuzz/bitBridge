package org.bitBridge.Tests.rsync.rsyncRed.optimizado;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

public class BitBridgeClient {
    private static final String SERVER_IP = "192.168.100.192";
    private static final int PUERTO = 8050;
    private static final int BLOCK_SIZE = 1024;
    private static final int MOD_ADLER = 65521;
    private static final Path FOLDER_ORIGEN = Paths.get("/home/cris/BitBridge_Shared");

    private static long totalBytesSent = 0;
    private static long totalBytesReceived = 0;
    private static long totalFolderSize = 0;
    private static final List<String> incrementalFileList = Collections.synchronizedList(new ArrayList<>());

    // Estructura de comandos interna para el pipeline de red asíncrono
    record NetworkTask(String rutaRelativa, long size, long xxhash, Path fileRef) {}
    private static final BlockingQueue<NetworkTask> pipelineQueue = new LinkedBlockingQueue<>(500);
    private static final NetworkTask POISON_PILL = new NetworkTask("", 0, 0, null);

    static class DeltaInstruccion {
        byte tipo; // 1 = BLOQUE, 2 = DATOS
        int index;
        byte[] datos;
    }

    public static void main(String[] args) {
        String folderName = FOLDER_ORIGEN.getFileName().toString();
        System.out.println("rsync -avz " + folderName + " cris@" + SERVER_IP + ":/home/cris/");
        System.out.println("sending incremental file list");
        incrementalFileList.add(folderName + "/");

        long cronometroInicio = System.currentTimeMillis();

        // 1. Hilo exclusivo de Red (Consumidor del pipeline)
        Thread networkWriterThread = new Thread(() -> runNetworkPipeline(folderName));
        networkWriterThread.start();

        // 2. Pool de hilos para procesamiento concurrente I/O (Productores)
        ExecutorService producerPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try {
            Files.walkFileTree(FOLDER_ORIGEN, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    long size = Files.size(file);
                    totalFolderSize += size;

                    producerPool.submit(() -> {
                        try {
                            String rutaRelativa = FOLDER_ORIGEN.relativize(file).toString();
                            long xxhash = calcularXXHash64Completo(file);
                            pipelineQueue.put(new NetworkTask(rutaRelativa, size, xxhash, file));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    return FileVisitResult.CONTINUE;
                }
            });

            producerPool.shutdown();
            producerPool.awaitTermination(10, TimeUnit.MINUTES);
            pipelineQueue.put(POISON_PILL); // Detener el hilo de red de forma limpia
            networkWriterThread.join();

        } catch (Exception e) {
            System.err.println("Error en motor: " + e.getMessage());
        }

        long cronometroFin = System.currentTimeMillis();
        double segundos = (cronometroFin - cronometroInicio) / 1000.0;
        if (segundos == 0) segundos = 0.001;

        for (String fileLog : incrementalFileList) System.out.println(fileLog);
        System.out.println();

        double bytesPerSec = (totalBytesSent + totalBytesReceived) / segundos;
        double speedup = totalBytesSent == 0 ? totalFolderSize : (double) totalFolderSize / totalBytesSent;

        System.out.printf(Locale.GERMAN, "sent %,d bytes  received %,d bytes  %,.2f bytes/sec%n", totalBytesSent, totalBytesReceived, bytesPerSec);
        System.out.printf(Locale.GERMAN, "total size is %,d  speedup is %,.2f%n", totalFolderSize, speedup);
        System.out.printf("Tiempo de ejecución: %.3f segundos%n", segundos);
    }

    private static void runNetworkPipeline(String folderName) {
        try (Socket socket = new Socket(SERVER_IP, PUERTO);
             DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 65536));
             DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 65536))) {

            while (true) {
                NetworkTask task = pipelineQueue.take();
                if (task == POISON_PILL) {
                    dos.writeUTF("EOF_DIRECTORIO");
                    dos.flush();
                    totalBytesSent += 16;
                    break;
                }

                dos.writeUTF("CHECK_FILE");
                dos.writeUTF(task.rutaRelativa);
                dos.writeLong(task.size);
                dos.writeLong(task.xxhash);
                dos.flush();

                totalBytesSent += 10 + task.rutaRelativa.length() + 8 + 8 + 6;
                String respuesta = dis.readUTF();
                totalBytesReceived += 2 + respuesta.length();

                if ("ENVIAR_COMPLETO".equals(respuesta)) {
                    incrementalFileList.add(folderName + "/" + task.rutaRelativa);
                    enviarArchivoCompletoDirectoChannel(task.fileRef, dos);
                } else if ("PROCESAR_DELTA".equals(respuesta)) {
                    incrementalFileList.add(folderName + "/" + task.rutaRelativa);
                    Map<Long, List<Integer>> remoteSigs = recibirFirmasServidorOptimizada(dis);
                    List<DeltaInstruccion> deltas = calcularDeltasMmapOptimizados(task.fileRef.toFile(), remoteSigs);
                    enviarDeltasAlServidorOptimizada(deltas, dos);
                }
            }
        } catch (Exception e) {
            System.err.println("Fallo crítico en pipeline de red: " + e.getMessage());
        }
    }

    private static void enviarArchivoCompletoDirectoChannel(Path file, DataOutputStream dos) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            long transferidos = 0;
            ByteBuffer buffer = ByteBuffer.allocate(32768);
            while (transferidos < size) {
                buffer.clear();
                int leídos = channel.read(buffer);
                if (leídos == -1) break;
                dos.write(buffer.array(), 0, leídos);
                transferidos += leídos;
            }
        }
        dos.flush();
        totalBytesSent += file.toFile().length();
    }

    private static Map<Long, List<Integer>> recibirFirmasServidorOptimizada(DataInputStream dis) throws IOException {
        // Estructura interna primitiva basada únicamente en el Weak Adler32 (Long) e índices enteros directos
        Map<Long, List<Integer>> mapa = new HashMap<>();
        int size = dis.readInt();
        totalBytesReceived += 4;
        for (int i = 0; i < size; i++) {
            long weak = dis.readLong();
            long strong = dis.readLong(); // El hash fuerte remoto ahora es un long primitivo (XXHash)
            mapa.computeIfAbsent(weak, k -> new ArrayList<>()).add(i);
            totalBytesReceived += 16;
        }
        return mapa;
    }

    private static List<DeltaInstruccion> calcularDeltasMmapOptimizados(File source, Map<Long, List<Integer>> remoteSigs) throws Exception {
        List<DeltaInstruccion> deltas = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(source, "r");
             FileChannel channel = raf.getChannel()) {

            long sourceLength = channel.size();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, sourceLength);

            int posicion = 0;
            int bytesAcumuladosNuevos = 0;
            int posicionDatosNuevosInicio = 0;

            long a = 1, b = 0;
            if (sourceLength >= BLOCK_SIZE) {
                int idx = 0;
                while (idx < BLOCK_SIZE) {
                    a = (a + (buffer.get(idx) & 0xFF)) % MOD_ADLER;   b = (b + a) % MOD_ADLER;
                    a = (a + (buffer.get(idx+1) & 0xFF)) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                    a = (a + (buffer.get(idx+2) & 0xFF)) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                    a = (a + (buffer.get(idx+3) & 0xFF)) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                    idx += 4;
                }
            }
            long rollingHash = (b << 16) | a;
            byte[] ventana = new byte[BLOCK_SIZE];

            while (posicion <= sourceLength - BLOCK_SIZE) {
                List<Integer> matchCandidatos = remoteSigs.get(rollingHash);

                if (matchCandidatos != null) {
                    buffer.position(posicion);
                    buffer.get(ventana);
                    long currentStrong = xxHash64Primitivo(ventana, BLOCK_SIZE);

                    if (!matchCandidatos.isEmpty()) {
                        if (bytesAcumuladosNuevos > 0) {
                            DeltaInstruccion d = new DeltaInstruccion(); d.tipo = 2;
                            d.datos = new byte[bytesAcumuladosNuevos];
                            buffer.position(posicionDatosNuevosInicio); buffer.get(d.datos);
                            deltas.add(d); bytesAcumuladosNuevos = 0;
                        }

                        DeltaInstruccion d = new DeltaInstruccion(); d.tipo = 1;
                        d.index = matchCandidatos.get(0); // Match directo por hash no criptográfico veloz
                        deltas.add(d);

                        posicion += BLOCK_SIZE;
                        if (posicion <= sourceLength - BLOCK_SIZE) {
                            a = 1; b = 0; int i = 0;
                            while (i < BLOCK_SIZE) {
                                a = (a + (buffer.get(posicion + i) & 0xFF)) % MOD_ADLER;   b = (b + a) % MOD_ADLER;
                                a = (a + (buffer.get(posicion + i + 1) & 0xFF)) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                                a = (a + (buffer.get(posicion + i + 2) & 0xFF)) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                                a = (a + (buffer.get(posicion + i + 3) & 0xFF)) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                                i += 4;
                            }
                            rollingHash = (b << 16) | a;
                        }
                        continue;
                    }
                }

                if (bytesAcumuladosNuevos == 0) posicionDatosNuevosInicio = posicion;
                bytesAcumuladosNuevos++;

                if (posicion + BLOCK_SIZE < sourceLength) {
                    int byteSaliente = buffer.get(posicion) & 0xFF;
                    int byteEntrante = buffer.get(posicion + BLOCK_SIZE) & 0xFF;
                    a = (a - byteSaliente + MOD_ADLER) % MOD_ADLER;
                    a = (a + byteEntrante) % MOD_ADLER;
                    b = (b - (BLOCK_SIZE * byteSaliente) % MOD_ADLER + MOD_ADLER) % MOD_ADLER;
                    b = (b + a - 1 + MOD_ADLER) % MOD_ADLER;
                    rollingHash = (b << 16) | a;
                }
                posicion++;
            }

            if (posicion < sourceLength) {
                if (bytesAcumuladosNuevos == 0) posicionDatosNuevosInicio = posicion;
                bytesAcumuladosNuevos += (sourceLength - posicion);
            }
            if (bytesAcumuladosNuevos > 0) {
                DeltaInstruccion d = new DeltaInstruccion(); d.tipo = 2;
                d.datos = new byte[bytesAcumuladosNuevos];
                buffer.position(posicionDatosNuevosInicio); buffer.get(d.datos);
                deltas.add(d);
            }
        }
        return deltas;
    }

    private static void enviarDeltasAlServidorOptimizada(List<DeltaInstruccion> deltas, DataOutputStream dos) throws IOException {
        dos.writeInt(deltas.size());
        totalBytesSent += 4;
        for (DeltaInstruccion d : deltas) {
            dos.writeByte(d.tipo);
            totalBytesSent += 1;
            if (d.tipo == 1) {
                dos.writeInt(d.index); totalBytesSent += 4;
            } else if (d.tipo == 2) {
                dos.writeInt(d.datos.length); dos.write(d.datos);
                totalBytesSent += 4 + d.datos.length;
            }
        }
        dos.flush();
    }

    private static long calcularXXHash64Completo(Path path) throws Exception {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size == 0) return 0;
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
            byte[] data = new byte[(int)size];
            buffer.get(data);
            return xxHash64Primitivo(data, data.length);
        }
    }

    private static long xxHash64Primitivo(byte[] input, int len) {
        long PRIME64_1 = -7046029288634856825L; long PRIME64_2 = -4417277310571146955L;
        long PRIME64_3 =  1602347449460738583L; long PRIME64_4 = -6028916918633310419L;
        long PRIME64_5 =  2870177450012600261L;
        long h64 = len + PRIME64_5; int remaining = len; int idx = 0;
        while (remaining >= 8) {
            long k1 = ((long) (input[idx] & 0xFF))        | ((long) (input[idx+1] & 0xFF) << 8)  |
                    ((long) (input[idx+2] & 0xFF) << 16) | ((long) (input[idx+3] & 0xFF) << 24) |
                    ((long) (input[idx+4] & 0xFF) << 32) | ((long) (input[idx+5] & 0xFF) << 40) |
                    ((long) (input[idx+6] & 0xFF) << 48) | ((long) (input[idx+7] & 0xFF) << 56);
            k1 *= PRIME64_2; k1 = Long.rotateLeft(k1, 31); k1 *= PRIME64_1; h64 ^= k1;
            h64 = Long.rotateLeft(h64, 27) * PRIME64_1 + PRIME64_4; idx += 8; remaining -= 8;
        }
        if (remaining >= 4) {
            long k1 = ((long) (input[idx] & 0xFF)) | ((long) (input[idx+1] & 0xFF) << 8) |
                    ((long) (input[idx+2] & 0xFF) << 16) | ((long) (input[idx+3] & 0xFF) << 24);
            h64 ^= k1 * PRIME64_1; h64 = Long.rotateLeft(h64, 23) * PRIME64_2 + PRIME64_3; idx += 4; remaining -= 4;
        }
        while (remaining > 0) {
            long k1 = input[idx] & 0xFF; h64 ^= k1 * PRIME64_5; h64 = Long.rotateLeft(h64, 11) * PRIME64_1; idx++; remaining--;
        }
        h64 ^= h64 >>> 33; h64 *= PRIME64_2; h64 ^= h64 >>> 29; h64 *= PRIME64_3; h64 ^= h64 >>> 32;
        return h64;
    }
}