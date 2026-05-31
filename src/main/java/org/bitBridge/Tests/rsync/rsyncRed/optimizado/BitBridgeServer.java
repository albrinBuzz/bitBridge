package org.bitBridge.Tests.rsync.rsyncRed.optimizado;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BitBridgeServer {
    private static final int PUERTO = 8050;
    private static final int BLOCK_SIZE = 1024;
    private static final int MOD_ADLER = 65521;
    private static final Path FOLDER_DESTINO = Paths.get("/home/cris/BitBridge_Shared");

    public static void main(String[] args) {
        System.out.println("=== SERVIDOR BITBRIDGE HIGH-PERFORMANCE: ESCUCHANDO PUERTO " + PUERTO + " ===");
        ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            if (!Files.exists(FOLDER_DESTINO)) Files.createDirectories(FOLDER_DESTINO);
            while (true) {
                Socket socket = serverSocket.accept();
                threadPool.submit(() -> procesarCliente(socket));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private static void procesarCliente(Socket socket) {
        try (Socket s = socket;
             DataInputStream dis = new DataInputStream(new BufferedInputStream(s.getInputStream(), 65536));
             DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 65536))) {

            while (true) {
                try {
                    String comando = dis.readUTF();
                    if ("EOF_DIRECTORIO".equals(comando)) break;

                    if ("CHECK_FILE".equals(comando)) {
                        String rutaRelativa = dis.readUTF();
                        long clienteSize = dis.readLong();
                        long clienteHash = dis.readLong(); // XXHash64 es un long primitivo (Rápido)

                        Path targetFile = FOLDER_DESTINO.resolve(rutaRelativa);
                        if (!Files.exists(targetFile.getParent())) Files.createDirectories(targetFile.getParent());

                        if (!Files.exists(targetFile)) {
                            dos.writeUTF("ENVIAR_COMPLETO");
                            dos.flush();
                            recibirArchivoDirectoChannel(targetFile, dis, clienteSize);
                        } else {
                            long servidorHash = calcularXXHash64Completo(targetFile);
                            if (clienteSize == Files.size(targetFile) && clienteHash == servidorHash) {
                                dos.writeUTF("SALTAR");
                                dos.flush();
                            } else if (clienteSize < BLOCK_SIZE) {
                                dos.writeUTF("ENVIAR_COMPLETO");
                                dos.flush();
                                recibirArchivoDirectoChannel(targetFile, dis, clienteSize);
                            } else {
                                dos.writeUTF("PROCESAR_DELTA");
                                dos.flush();

                                enviarFirmasAlClienteOptimizada(targetFile.toFile(), dos);
                                aplicarDeltasEnServidorOptimizada(targetFile.toFile(), dis);
                            }
                        }
                    }
                } catch (EOFException e) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error procesando sesión: " + e.getMessage());
        }
    }

    private static void recibirArchivoDirectoChannel(Path destino, DataInputStream dis, long size) throws IOException {
        try (FileChannel channel = FileChannel.open(destino, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[32768];
            long leídosTotal = 0;
            while (leídosTotal < size) {
                int aLeer = (int) Math.min(buffer.length, size - leídosTotal);
                int leídos = dis.read(buffer, 0, aLeer);
                if (leídos == -1) break;
                channel.write(ByteBuffer.wrap(buffer, 0, leídos));
                leídosTotal += leídos;
            }
        }
    }

    private static void enviarFirmasAlClienteOptimizada(File file, DataOutputStream dos) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {

            long fileSize = channel.size();
            int numBloques = (int) (fileSize / BLOCK_SIZE);
            dos.writeInt(numBloques);

            MappedByteBuffer mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            byte[] block = new byte[BLOCK_SIZE];

            for (int i = 0; i < numBloques; i++) {
                mappedBuffer.get(block);

                // Adler32 optimizado con Loop Unrolling básico
                long a = 1, b = 0;
                int idx = 0;
                while (idx < BLOCK_SIZE) {
                    a = (a + (block[idx] & 0xFF)) % MOD_ADLER;   b = (b + a) % MOD_ADLER;
                    a = (a + (block[idx+1] & 0xFF)) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                    a = (a + (block[idx+2] & 0xFF)) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                    a = (a + (block[idx+3] & 0xFF)) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                    idx += 4;
                }
                long weak = (b << 16) | a;
                long strong = xxHash64Primitivo(block, BLOCK_SIZE); // Hash fuerte no criptográfico ultra veloz

                dos.writeLong(weak);
                dos.writeLong(strong);
            }
            dos.flush();
        }
    }

    private static void aplicarDeltasEnServidorOptimizada(File target, DataInputStream dis) throws Exception {
        File tempFile = new File(target.getAbsolutePath() + ".tmp");
        try (RandomAccessFile rafViejo = new RandomAccessFile(target, "r");
             FileChannel channelViejo = rafViejo.getChannel();
             RandomAccessFile rafNuevo = new RandomAccessFile(tempFile, "rw");
             FileChannel channelNuevo = rafNuevo.getChannel()) {

            int totalInstrucciones = dis.readInt();
            ByteBuffer bufNio = ByteBuffer.allocateDirect(BLOCK_SIZE);

            for (int i = 0; i < totalInstrucciones; i++) {
                byte tipoId = dis.readByte();
                if (tipoId == 1) {
                    int indexBloque = dis.readInt();
                    channelViejo.position((long) indexBloque * BLOCK_SIZE);
                    bufNio.clear();
                    channelViejo.read(bufNio);
                    bufNio.flip();
                    channelNuevo.write(bufNio);
                } else if (tipoId == 2) {
                    int len = dis.readInt();
                    byte[] raw = new byte[len];
                    dis.readFully(raw);
                    channelNuevo.write(ByteBuffer.wrap(raw));
                }
            }
        }
        target.delete();
        tempFile.renameTo(target);
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

    // Algoritmo Puro XXHash64 optimizado para Java (Evita colisiones sin la lentitud de SHA)
    private static long xxHash64Primitivo(byte[] input, int len) {
        long PRIME64_1 = -7046029288634856825L;
        long PRIME64_2 = -4417277310571146955L;
        long PRIME64_3 =  1602347449460738583L;
        long PRIME64_4 = -6028916918633310419L;
        long PRIME64_5 =  2870177450012600261L;

        long h64 = len + PRIME64_5;
        int remaining = len;
        int idx = 0;

        while (remaining >= 8) {
            long k1 = ((long) (input[idx] & 0xFF))        | ((long) (input[idx+1] & 0xFF) << 8)  |
                    ((long) (input[idx+2] & 0xFF) << 16) | ((long) (input[idx+3] & 0xFF) << 24) |
                    ((long) (input[idx+4] & 0xFF) << 32) | ((long) (input[idx+5] & 0xFF) << 40) |
                    ((long) (input[idx+6] & 0xFF) << 48) | ((long) (input[idx+7] & 0xFF) << 56);
            k1 *= PRIME64_2; k1 = Long.rotateLeft(k1, 31); k1 *= PRIME64_1;
            h64 ^= k1; h64 = Long.rotateLeft(h64, 27) * PRIME64_1 + PRIME64_4;
            idx += 8; remaining -= 8;
        }
        if (remaining >= 4) {
            long k1 = ((long) (input[idx] & 0xFF)) | ((long) (input[idx+1] & 0xFF) << 8) |
                    ((long) (input[idx+2] & 0xFF) << 16) | ((long) (input[idx+3] & 0xFF) << 24);
            h64 ^= k1 * PRIME64_1; h64 = Long.rotateLeft(h64, 23) * PRIME64_2 + PRIME64_3;
            idx += 4; remaining -= 4;
        }
        while (remaining > 0) {
            long k1 = input[idx] & 0xFF;
            h64 ^= k1 * PRIME64_5; h64 = Long.rotateLeft(h64, 11) * PRIME64_1;
            idx++; remaining--;
        }
        h64 ^= h64 >>> 33; h64 *= PRIME64_2; h64 ^= h64 >>> 29; h64 *= PRIME64_3; h64 ^= h64 >>> 32;
        return h64;
    }
}