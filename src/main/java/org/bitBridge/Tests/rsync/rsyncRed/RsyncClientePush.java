package org.bitBridge.Tests.rsync.rsyncRed;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RsyncClientePush {
    private final String host;
    private final int puerto;
    private static final int TAMANO_BLOQUE = 1024;
    private static final int BASE = 31;

    private long totalBytesEnviados = 0;
    private long totalBytesRecibidos = 0;
    private long tamañoTotalArchivos = 0;
    private long tiempoInicio;

    public RsyncClientePush(String host, int puerto) {
        this.host = host;
        this.puerto = puerto;
    }

    public void enviarCarpetaRecursiva(String rutaCarpetaLocal) {
        File raiz = new File(rutaCarpetaLocal);
        if (!raiz.exists() || !raiz.isDirectory()) return;

        System.out.println("sending incremental file list");
        tiempoInicio = System.currentTimeMillis();

        recorrerYEnviar(raiz, raiz);

        long tiempoTotal = System.currentTimeMillis() - tiempoInicio;
        double segundos = tiempoTotal / 1000.0 == 0 ? 0.001 : tiempoTotal / 1000.0;
        double velocidad = totalBytesEnviados / segundos;
        double speedup = totalBytesEnviados == 0 ? 0 : (double) tamañoTotalArchivos / totalBytesEnviados;

        System.out.println();
        System.out.printf("sent %,d bytes  received %,d bytes  %,.2f bytes/sec%n",
                totalBytesEnviados, totalBytesRecibidos, velocidad);
        System.out.printf("total size is %,d  speedup is %,.2f%n",
                tamañoTotalArchivos, speedup);
    }

    private void recorrerYEnviar(File archivoActual, File carpetaRaiz) {
        String rutaRelativa = carpetaRaiz.toPath().relativize(archivoActual.toPath()).toString();

        if (archivoActual.isDirectory()) {
            if (!rutaRelativa.isEmpty()) {
                crearCarpetaRemota(rutaRelativa);
            }
            File[] hijos = archivoActual.listFiles();
            if (hijos != null) {
                for (File h : hijos) recorrerYEnviar(h, carpetaRaiz);
            }
        } else if (archivoActual.isFile()) {
            tamañoTotalArchivos += archivoActual.length();
            sincronizarArchivoFiltroDoble(rutaRelativa, archivoActual);
        }
    }

    private void crearCarpetaRemota(String rutaRelativa) {
        try (Socket socket = new Socket(host, puerto);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeUTF("CARPETA");
            dos.writeUTF(rutaRelativa);
            dos.flush();
            totalBytesEnviados += 4 + rutaRelativa.length();
        } catch (IOException e) {
            System.err.println("Error en directorio remoto: " + e.getMessage());
        }
    }

    private void sincronizarArchivoFiltroDoble(String rutaRelativa, File archivoLocal) {
        try (Socket socket = new Socket(host, puerto);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

            // Cálculo ultrarrápido por canales NIO del hash global del archivo local
            String hashFuerteGlobal = calcularHashFuerteGlobal(archivoLocal);

            // Negociar intenciones enviando Tamaño y Hash Global
            dos.writeUTF("ARCHIVO_NEGOCIAR");
            dos.writeUTF(rutaRelativa);
            dos.writeLong(archivoLocal.length());
            dos.writeUTF(hashFuerteGlobal);
            dos.flush();
            totalBytesEnviados += 28 + rutaRelativa.length() + hashFuerteGlobal.length();

            String respuesta = ois.readUTF();
            totalBytesRecibidos += 10;

            if ("SKIP".equals(respuesta)) {
                // Éxito O(1): El archivo no ha cambiado. Cerramos la conexión elegantemente.
                return;
            }

            // Si pasa este punto, el archivo es nuevo o fue editado legítimamente
            System.out.println(rutaRelativa);

            @SuppressWarnings("unchecked")
            List<FirmaBloque> firmasLista = (List<FirmaBloque>) ois.readObject();
            totalBytesRecibidos += (firmasLista.size() * 40L);

            // Mapear firmas al diccionario O(1) usando el hash débil como clave
            Map<Long, FirmaBloque> mapaFirmas = new HashMap<>();
            for (FirmaBloque f : firmasLista) {
                mapaFirmas.put(f.getRollingHash(), f);
            }

            // Disparar motor de ventana deslizante
            calcularYEnviarDeltasAlServidor(archivoLocal, mapaFirmas, dos);

        } catch (Exception e) {
            System.err.println("Error procesando " + archivoLocal.getName() + ": " + e.getMessage());
        }
    }

    private String calcularHashFuerteGlobal(File archivo) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(archivo);
             FileChannel channel = fis.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
            while (channel.read(buffer) > 0) {
                buffer.flip();
                md.update(buffer);
                buffer.clear();
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void calcularYEnviarDeltasAlServidor(File archivo, Map<Long, FirmaBloque> mapaFirmas, DataOutputStream dos) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        try (RandomAccessFile raf = new RandomAccessFile(archivo, "r");
             FileChannel channel = raf.getChannel()) {

            long tamanoTotal = channel.size();
            if (tamanoTotal == 0) {
                dos.writeUTF("FIN");
                return;
            }

            ByteBuffer fileBuffer = ByteBuffer.allocateDirect((int) tamanoTotal);
            channel.read(fileBuffer);
            fileBuffer.flip();

            long posicion = 0;
            int bytesAcumulados = 0;
            long posicionDatosNuevosInicio = 0;

            long multiplicadorPotencia = 1;
            for (int i = 0; i < TAMANO_BLOQUE - 1; i++) {
                multiplicadorPotencia *= BASE;
            }

            while (posicion <= tamanoTotal - TAMANO_BLOQUE) {
                long rollingHash = 0;
                byte[] ventanaBytes = new byte[TAMANO_BLOQUE];

                fileBuffer.position((int) posicion);
                fileBuffer.get(ventanaBytes);

                for (byte b : ventanaBytes) {
                    rollingHash = (rollingHash * BASE) + b;
                }

                boolean coincidenciaEncontrada = false;

                while (posicion <= tamanoTotal - TAMANO_BLOQUE) {
                    // Test de Hash Débil en O(1)
                    FirmaBloque f = mapaFirmas.get(rollingHash);

                    if (f != null) {
                        // Confirmación estricta con Hash Fuerte SHA-256 ante colisiones
                        md.reset();
                        md.update(ventanaBytes);
                        byte[] digest = md.digest();
                        StringBuilder sb = new StringBuilder();
                        for (byte b : digest) sb.append(String.format("%02x", b));

                        if (f.getFuerteHash().equalsIgnoreCase(sb.toString())) {
                            if (bytesAcumulados > 0) {
                                enviarFragmentoDatos(fileBuffer, posicionDatosNuevosInicio, bytesAcumulados, dos);
                                bytesAcumulados = 0;
                            }

                            dos.writeUTF("BLOQUE_EXISTENTE");
                            dos.writeInt(f.getIndex());
                            totalBytesEnviados += 22;

                            posicion += TAMANO_BLOQUE;
                            bytesAcumulados = 0;
                            coincidenciaEncontrada = true;
                            break;
                        }
                    }

                    // Desplazar ventana en O(1)
                    if (posicion + TAMANO_BLOQUE < tamanoTotal) {
                        byte byteSaliente = ventanaBytes[0];
                        byte byteEntrante = fileBuffer.get((int) (posicion + TAMANO_BLOQUE));

                        System.arraycopy(ventanaBytes, 1, ventanaBytes, 0, TAMANO_BLOQUE - 1);
                        ventanaBytes[TAMANO_BLOQUE - 1] = byteEntrante;

                        rollingHash = (rollingHash - byteSaliente * multiplicadorPotencia) * BASE + byteEntrante;
                    }

                    if (bytesAcumulados == 0) {
                        posicionDatosNuevosInicio = posicion;
                    }
                    bytesAcumulados++;
                    posicion++;
                }
                if (coincidenciaEncontrada) continue;
            }

            long bytesRestantesFinales = tamanoTotal - posicion;
            if (bytesRestantesFinales > 0 || bytesAcumulados > 0) {
                enviarFragmentoDatos(fileBuffer, posicionDatosNuevosInicio, (int) (bytesAcumulados + bytesRestantesFinales), dos);
            }

            dos.writeUTF("FIN");
            totalBytesEnviados += 5;
            dos.flush();
        }
    }

    private void enviarFragmentoDatos(ByteBuffer srcBuffer, long inicio, int longitud, DataOutputStream dos) throws IOException {
        dos.writeUTF("DATOS");
        dos.writeInt(longitud);

        byte[] datosAEnviar = new byte[longitud];
        srcBuffer.position((int) inicio);
        srcBuffer.get(datosAEnviar);

        dos.write(datosAEnviar);
        totalBytesEnviados += 10 + longitud;
    }
}