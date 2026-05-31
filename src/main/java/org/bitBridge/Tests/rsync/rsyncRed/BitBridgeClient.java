package org.bitBridge.Tests.rsync.rsyncRed;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

public class BitBridgeClient {
    private static final String SERVER_IP = "192.168.100.192";
    private static final int PUERTO = 8050;
    private static final int BLOCK_SIZE = 1024;
    private static final int MOD_ADLER = 65521;
    //private static final Path FOLDER_ORIGEN = Paths.get("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/scripts/origen");
    private static final Path FOLDER_ORIGEN = Paths.get("/home/cris/BitBridge_Shared");
    // Métricas globales reales para el reporte final estilo rsync
    private static long totalBytesSent = 0;
    private static long totalBytesReceived = 0;
    private static long totalFolderSize = 0;
    private static final List<String> incrementalFileList = new ArrayList<>();

    record BlockSignature(int index, String strongHash) {}

    static class DeltaInstruccion {
        String tipo;
        int index;
        byte[] datos;
    }

    public static void main(String[] args) {
        // Simulación inicial de shell de comandos rsync
        String folderName = FOLDER_ORIGEN.getFileName().toString();
        System.out.println("rsync -avz " + folderName + " cris@192.168.100.192:/home/cris/");
        System.out.println("sending incremental file list");

        // El directorio raíz siempre encabeza la lista
        incrementalFileList.add(folderName + "/");

        long cronometroInicio = System.currentTimeMillis();

        try (Socket socket = new Socket(SERVER_IP, PUERTO);
             DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {

            Files.walkFileTree(FOLDER_ORIGEN, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String rutaRelativa = FOLDER_ORIGEN.relativize(file).toString();
                    long size = Files.size(file);
                    totalFolderSize += size;

                    try {
                        String hashCompleto = calcularHashCompleto(file.toFile());

                        // Negociación y tracking de overhead de comandos
                        dos.writeUTF("CHECK_FILE");
                        dos.writeUTF(rutaRelativa);
                        dos.writeLong(size);
                        dos.writeUTF(hashCompleto);
                        dos.flush();

                        // Estimación de overhead de control enviado: cmd (2B + len) + ruta (2B + len) + size (8B) + hash (2B + 32B)
                        totalBytesSent += (2 + 10) + (2 + rutaRelativa.length()) + 8 + (2 + 32);

                        String respuesta = dis.readUTF();
                        totalBytesReceived += (2 + respuesta.length()); // Overhead de respuesta

                        if ("ENVIAR_COMPLETO".equals(respuesta)) {
                            incrementalFileList.add(folderName + "/" + rutaRelativa);
                            enviarArchivoCompletoDirecto(file.toFile(), dos);
                        } else if ("PROCESAR_DELTA".equals(respuesta)) {
                            incrementalFileList.add(folderName + "/" + rutaRelativa);

                            // Medir bytes de firmas entrantes
                            long bytesAntesSigs = totalBytesReceived;
                            Map<Long, List<BlockSignature>> remoteSigs = recibirFirmasServidor(dis);

                            List<DeltaInstruccion> deltas = calcularDeltasLocal(file.toFile(), remoteSigs);
                            enviarDeltasAlServidor(deltas, dos);
                        }
                        // Si la respuesta fue "SALTAR", no se añade a la lista incrementada, igual que rsync

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            dos.writeUTF("EOF_DIRECTORIO");
            dos.flush();
            totalBytesSent += (2 + 14);

        } catch (Exception e) {
            System.err.println("Error de conexión: " + e.getMessage());
            return;
        }

        long cronometroFin = System.currentTimeMillis();
        double segundos = (cronometroFin - cronometroInicio) / 1000.0;
        if (segundos == 0) segundos = 0.001; // Evitar división por cero

        // --- IMPRESIÓN DEL LOG FINAL IDÉNTICO A RSYNC ---
        for (String fileLog : incrementalFileList) {
            System.out.println(fileLog);
        }
        System.out.println();

        double bytesPerSec = (totalBytesSent + totalBytesReceived) / segundos;
        double speedup = totalBytesSent == 0 ? totalFolderSize : (double) totalFolderSize / totalBytesSent;

        System.out.printf(Locale.GERMAN, "sent %,d bytes  received %,d bytes  %,.2f bytes/sec%n",
                totalBytesSent, totalBytesReceived, bytesPerSec);
        System.out.printf(Locale.GERMAN, "total size is %,d  speedup is %,.2f%n",
                totalFolderSize, speedup);
    }

    private static void enviarArchivoCompletoDirecto(File file, DataOutputStream dos) throws IOException {
        long bytesAntes = dos.size();
        try (InputStream is = new FileInputStream(file)) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = is.read(buf)) != -1) {
                dos.write(buf, 0, read);
            }
        }
        dos.flush();
        totalBytesSent += (file.length());
    }

    private static Map<Long, List<BlockSignature>> recibirFirmasServidor(DataInputStream dis) throws IOException {
        Map<Long, List<BlockSignature>> mapa = new HashMap<>();
        int size = dis.readInt();
        totalBytesReceived += 4;
        for (int i = 0; i < size; i++) {
            long weak = dis.readLong();
            String strong = dis.readUTF();
            mapa.computeIfAbsent(weak, k -> new ArrayList<>()).add(new BlockSignature(i, strong));
            totalBytesReceived += 8 + (2 + strong.length());
        }
        return mapa;
    }

    private static List<DeltaInstruccion> calcularDeltasLocal(File source, Map<Long, List<BlockSignature>> remoteSigs) throws Exception {
        List<DeltaInstruccion> deltas = new ArrayList<>();
        long sourceLength = source.length();
        MessageDigest md = MessageDigest.getInstance("MD5");

        try (RandomAccessFile raf = new RandomAccessFile(source, "r")) {
            byte[] fileBytes = new byte[(int) sourceLength];
            raf.readFully(fileBytes);

            int posicion = 0;
            int bytesAcumuladosNuevos = 0;
            int posicionDatosNuevosInicio = 0;

            long a = 1, b = 0;
            if (sourceLength >= BLOCK_SIZE) {
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    int val = fileBytes[i] & 0xFF;
                    a = (a + val) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                }
            }
            long rollingHash = (b << 16) | a;

            while (posicion <= sourceLength - BLOCK_SIZE) {
                List<BlockSignature> matchCandidatos = remoteSigs.get(rollingHash);

                if (matchCandidatos != null) {
                    byte[] ventana = Arrays.copyOfRange(fileBytes, posicion, posicion + BLOCK_SIZE);
                    md.reset(); md.update(ventana);
                    StringBuilder sb = new StringBuilder();
                    for (byte bByte : md.digest()) sb.append(String.format("%02x", bByte));
                    String currentStrong = sb.toString();

                    BlockSignature match = null;
                    for (BlockSignature sig : matchCandidatos) {
                        if (sig.strongHash.equalsIgnoreCase(currentStrong)) {
                            match = sig; break;
                        }
                    }

                    if (match != null) {
                        if (bytesAcumuladosNuevos > 0) {
                            DeltaInstruccion d = new DeltaInstruccion();
                            d.tipo = "DATOS";
                            d.datos = Arrays.copyOfRange(fileBytes, posicionDatosNuevosInicio, posicionDatosNuevosInicio + bytesAcumuladosNuevos);
                            deltas.add(d);
                            bytesAcumuladosNuevos = 0;
                        }

                        DeltaInstruccion d = new DeltaInstruccion();
                        d.tipo = "BLOQUE";
                        d.index = match.index;
                        deltas.add(d);

                        posicion += BLOCK_SIZE;
                        if (posicion <= sourceLength - BLOCK_SIZE) {
                            a = 1; b = 0;
                            for (int i = 0; i < BLOCK_SIZE; i++) {
                                int val = fileBytes[posicion + i] & 0xFF;
                                a = (a + val) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                            }
                            rollingHash = (b << 16) | a;
                        }
                        continue;
                    }
                }

                if (bytesAcumuladosNuevos == 0) posicionDatosNuevosInicio = posicion;
                bytesAcumuladosNuevos++;

                if (posicion + BLOCK_SIZE < sourceLength) {
                    int byteSaliente = fileBytes[posicion] & 0xFF;
                    int byteEntrante = fileBytes[posicion + BLOCK_SIZE] & 0xFF;
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
                DeltaInstruccion d = new DeltaInstruccion();
                d.tipo = "DATOS";
                d.datos = Arrays.copyOfRange(fileBytes, posicionDatosNuevosInicio, posicionDatosNuevosInicio + bytesAcumuladosNuevos);
                deltas.add(d);
            }
        }
        return deltas;
    }

    private static void enviarDeltasAlServidor(List<DeltaInstruccion> deltas, DataOutputStream dos) throws IOException {
        dos.writeInt(deltas.size());
        totalBytesSent += 4;
        for (DeltaInstruccion d : deltas) {
            dos.writeUTF(d.tipo);
            totalBytesSent += (2 + d.tipo.length());
            if ("BLOQUE".equals(d.tipo)) {
                dos.writeInt(d.index);
                totalBytesSent += 4;
            } else if ("DATOS".equals(d.tipo)) {
                dos.writeInt(d.datos.length);
                dos.write(d.datos);
                totalBytesSent += 4 + d.datos.length;
            }
        }
        dos.flush();
    }

    private static String calcularHashCompleto(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) md.update(buffer, 0, read);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}