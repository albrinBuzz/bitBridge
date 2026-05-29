package org.bitBridge.Tests.rsync.rsyncRed;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class RsyncServerPush {
    private static final int PUERTO = 9999;
    private static final int TAMANO_BLOQUE = 1024;

    public void iniciar(String carpetaRaizServidor) {
        File raiz = new File(carpetaRaizServidor);
        if (!raiz.exists()) raiz.mkdirs();

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            System.out.println("[SERVER] Protocolo BitBridge-Rsync activo en puerto " + PUERTO);

            while (true) {
                try (Socket socket = serverSocket.accept();
                     DataInputStream dis = new DataInputStream(socket.getInputStream());
                     ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

                    String accion = dis.readUTF();
                    String rutaRelativa = dis.readUTF();
                    File destinoFinal = new File(raiz, rutaRelativa);

                    if ("CARPETA".equals(accion)) {
                        destinoFinal.mkdirs();
                        continue;
                    }

                    if ("ARCHIVO_NEGOCIAR".equals(accion)) {
                        long tamanoCliente = dis.readLong();
                        String hashFuerteGlobalCliente = dis.readUTF();

                        // 1. QUICK CHECK CENTRALIZADO (O(1) sin leer el archivo real)
                        if (destinoFinal.exists() && destinoFinal.isFile() && destinoFinal.length() == tamanoCliente) {
                            String hashPersistido = leerHashPersistido(destinoFinal, raiz);
                            if (hashPersistido != null && hashPersistido.equals(hashFuerteGlobalCliente)) {
                                oos.writeUTF("SKIP");
                                oos.flush();
                                continue; // Archivos idénticos: Saltar inmediatamente
                            }
                        }

                        // 2. Si el Quick Check falla, se activa la lógica por bloques de Rsync
                        oos.writeUTF("PROCESAR_DELTA");

                        List<FirmaBloque> firmas = new ArrayList<>();
                        if (destinoFinal.exists() && destinoFinal.isFile()) {
                            firmas = generarFirmasDeBloques(destinoFinal);
                        }
                        oos.writeObject(firmas);
                        oos.flush();

                        // 3. Reconstruir el archivo temporal desde las instrucciones de red
                        File archivoTemporal = new File(destinoFinal.getAbsolutePath() + ".tmp");
                        ensamblarArchivoDesdeDeltas(destinoFinal, archivoTemporal, dis);

                        // 4. Reemplazo atómico e indexación del nuevo Hash en la caché oculta
                        if (destinoFinal.exists()) destinoFinal.delete();
                        if (archivoTemporal.renameTo(destinoFinal)) {
                            guardarHashPersistido(destinoFinal, hashFuerteGlobalCliente, raiz);
                        }
                    }

                } catch (Exception e) {
                    System.err.println("[SERVER] Error en flujo de sincronización: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Genera la ruta al archivo de metadatos dentro de la carpeta oculta centralizada
    private File obtenerArchivoMeta(File archivoReal, File raiz) {
        File carpetaCache = new File(raiz, ".bitbridge_cache");
        if (!carpetaCache.exists()) carpetaCache.mkdirs();

        // Usamos el path relativo a la raíz para que el nombre del meta sea consistente
        // independientemente de dónde esté montado el servidor
        String pathRelativo = raiz.toPath().relativize(archivoReal.toPath()).toString();
        String nombreSeguro = pathRelativo.replace(File.separator, "_") + ".meta";

        return new File(carpetaCache, nombreSeguro);
    }

    private String leerHashPersistido(File archivo, File raiz) {
        File archivoMeta = obtenerArchivoMeta(archivo, raiz);
        if (!archivoMeta.exists()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(archivoMeta))) {
            return br.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private void guardarHashPersistido(File archivo, String hash, File raiz) {
        File archivoMeta = obtenerArchivoMeta(archivo, raiz);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(archivoMeta))) {
            bw.write(hash);
        } catch (IOException ignored) {}
    }

    private List<FirmaBloque> generarFirmasDeBloques(File archivo) {
        List<FirmaBloque> lista = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(archivo, "r");
             FileChannel channel = raf.getChannel()) {

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            ByteBuffer buffer = ByteBuffer.allocate(TAMANO_BLOQUE);
            int index = 0;

            while (channel.read(buffer) > 0) {
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                // Hash débil polinomial (Adler/Rabin-Karp modificado)
                long rolling = 0;
                for (byte b : bytes) rolling = (rolling * 31) + b;

                // Hash fuerte (SHA-256 discreto del bloque)
                md.reset();
                md.update(bytes);
                byte[] digest = md.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) sb.append(String.format("%02x", b));

                lista.add(new FirmaBloque(index++, rolling, sb.toString()));
                buffer.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lista;
    }

    private void ensamblarArchivoDesdeDeltas(File archivoViejo, File archivoTemporal, DataInputStream dis) throws IOException {
        if (archivoTemporal.getParentFile() != null) archivoTemporal.getParentFile().mkdirs();

        try (RandomAccessFile rafViejo = archivoViejo.exists() ? new RandomAccessFile(archivoViejo, "r") : null;
             FileChannel channelViejo = rafViejo != null ? rafViejo.getChannel() : null;
             RandomAccessFile rafNuevo = new RandomAccessFile(archivoTemporal, "rw");
             FileChannel channelNuevo = rafNuevo.getChannel()) {

            while (true) {
                String comando = dis.readUTF();
                if ("FIN".equals(comando)) break;

                if ("BLOQUE_EXISTENTE".equals(comando)) {
                    int index = dis.readInt();
                    if (channelViejo != null) {
                        long posOriginal = (long) index * TAMANO_BLOQUE;
                        long longitudCopia = Math.min(TAMANO_BLOQUE, channelViejo.size() - posOriginal);
                        channelNuevo.transferFrom(channelViejo, channelNuevo.size(), longitudCopia);
                    }
                } else if ("DATOS".equals(comando)) {
                    int lon = dis.readInt();
                    byte[] rawBytes = new byte[lon];
                    dis.readFully(rawBytes);
                    channelNuevo.write(ByteBuffer.wrap(rawBytes));
                }
            }
        }
    }
}