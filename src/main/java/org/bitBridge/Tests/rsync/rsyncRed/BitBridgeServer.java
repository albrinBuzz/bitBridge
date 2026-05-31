package org.bitBridge.Tests.rsync.rsyncRed;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class BitBridgeServer {
    private static final int PUERTO = 8050;
    private static final int BLOCK_SIZE = 1024;
    private static final int MOD_ADLER = 65521;
    //private static final Path FOLDER_DESTINO = Paths.get("/home/cris/java/java/javafx/proyectos/bitBrige/bitBrige/scripts/destino");
    private static final Path FOLDER_DESTINO = Paths.get("/home/cris/BitBridge_Shared");
    public static void main(String[] args) {
        System.out.println("=== SERVIDOR BITBRIDGE: ESPERANDO CONEXIONES EN PUERTO " + PUERTO + " ===");
        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            if (!Files.exists(FOLDER_DESTINO)) Files.createDirectories(FOLDER_DESTINO);

            while (true) {
                try (Socket socket = serverSocket.accept();
                     DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                     DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

                    while (true) {
                        try {
                            String comando = dis.readUTF();
                            if ("EOF_DIRECTORIO".equals(comando)) break;

                            if ("CHECK_FILE".equals(comando)) {
                                String rutaRelativa = dis.readUTF();
                                long clienteSize = dis.readLong();
                                String clienteHash = dis.readUTF();

                                Path targetFile = FOLDER_DESTINO.resolve(rutaRelativa);
                                if (!Files.exists(targetFile.getParent())) {
                                    Files.createDirectories(targetFile.getParent());
                                }

                                if (!Files.exists(targetFile)) {
                                    dos.writeUTF("ENVIAR_COMPLETO");
                                    dos.flush();
                                    recibirArchivoDirecto(targetFile.toFile(), dis, clienteSize);
                                } else {
                                    String servidorHash = calcularHashCompleto(targetFile.toFile());
                                    if (clienteSize == Files.size(targetFile) && clienteHash.equals(servidorHash)) {
                                        dos.writeUTF("SALTAR");
                                        dos.flush();
                                    } else if (clienteSize < BLOCK_SIZE) {
                                        dos.writeUTF("ENVIAR_COMPLETO");
                                        dos.flush();
                                        recibirArchivoDirecto(targetFile.toFile(), dis, clienteSize);
                                    } else {
                                        dos.writeUTF("PROCESAR_DELTA");
                                        dos.flush();

                                        enviarFirmasAlCliente(targetFile.toFile(), dos);
                                        aplicarDeltasEnServidor(targetFile.toFile(), dis);
                                    }
                                }
                            }
                        } catch (EOFException e) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error en la sesión: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void recibirArchivoDirecto(File destino, DataInputStream dis, long size) throws IOException {
        try (OutputStream os = new FileOutputStream(destino)) {
            byte[] buffer = new byte[4096];
            long leídosTotal = 0;
            while (leídosTotal < size) {
                int aLeer = (int) Math.min(buffer.length, size - leídosTotal);
                int leídos = dis.read(buffer, 0, aLeer);
                if (leídos == -1) break;
                os.write(buffer, 0, leídos);
                leídosTotal += leídos;
            }
        }
    }

    private static void enviarFirmasAlCliente(File file, DataOutputStream dos) throws Exception {
        List<Long> weaks = new ArrayList<>();
        List<String> strongs = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead;
            MessageDigest md = MessageDigest.getInstance("MD5");
            while ((bytesRead = raf.read(buffer)) != -1) {
                if (bytesRead < BLOCK_SIZE) break;

                long a = 1, b = 0;
                for (byte x : buffer) {
                    int val = x & 0xFF;
                    a = (a + val) % MOD_ADLER; b = (b + a) % MOD_ADLER;
                }
                weaks.add((b << 16) | a);

                md.reset(); md.update(buffer);
                StringBuilder sb = new StringBuilder();
                for (byte bByte : md.digest()) sb.append(String.format("%02x", bByte));
                strongs.add(sb.toString());
            }
        }

        dos.writeInt(weaks.size());
        for (int i = 0; i < weaks.size(); i++) {
            dos.writeLong(weaks.get(i));
            dos.writeUTF(strongs.get(i));
        }
        dos.flush();
    }

    private static void aplicarDeltasEnServidor(File target, DataInputStream dis) throws Exception {
        File tempFile = new File(target.getAbsolutePath() + ".tmp");
        try (RandomAccessFile rafViejo = new RandomAccessFile(target, "r");
             RandomAccessFile rafNuevo = new RandomAccessFile(tempFile, "rw")) {

            int totalInstrucciones = dis.readInt();
            for (int i = 0; i < totalInstrucciones; i++) {
                String tipo = dis.readUTF();
                if ("BLOQUE".equals(tipo)) {
                    int indexBloque = dis.readInt();
                    long posOriginal = (long) indexBloque * BLOCK_SIZE;
                    byte[] block = new byte[BLOCK_SIZE];
                    rafViejo.seek(posOriginal);
                    int leídos = rafViejo.read(block);
                    rafNuevo.write(block, 0, leídos);
                } else if ("DATOS".equals(tipo)) {
                    int len = dis.readInt();
                    byte[] raw = new byte[len];
                    dis.readFully(raw);
                    rafNuevo.write(raw);
                }
            }
        }
        target.delete();
        tempFile.renameTo(target);
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