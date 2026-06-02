package org.bitBridge.Client;

import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.*;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.core.comunication.sync.BlockSignature;
import org.bitBridge.shared.core.comunication.sync.RsyncDeltaInstruction;
import org.bitBridge.shared.core.comunication.sync.RsyncDeltaPackage;
import org.bitBridge.shared.core.comunication.sync.RsyncSignatures;
import org.bitBridge.shared.network.NetworkConfig;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.Adler32;

public class FileTransferManager implements TransferManager {
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();

    private final TransferenciaController transferenciaController;
    private final String downloadDir;

    // Bloque estándar fijo de 64KB para consistencia con el ecosistema Rsync de la app
    private static final int BLOCK_SIZE = 64 * 1024;
    // Buffer para transferencias masivas tradicionales (4MB)
    private static final int TRANSFER_CHUNK_SIZE = 4 * 1024 * 1024;

    public FileTransferManager(TransferenciaController transferenciaController) {
        this.transferenciaController = transferenciaController;
        this.downloadDir = ConfiguracionApp.getInstancia().obtener(ConfigKey.DOWNLOAD_DIR);
    }

    // ==========================================
    // --- LÓGICA DE ENVÍO (SENDER INDIVIDUAL) ---
    // ==========================================
    public void sendFile(FileDirectoryCommunication com, File file, String SERVER_ADDRESS, int port) {
        String sessionId = "SENDER_" + new Random().nextInt(10000);
        long fileSize = file.length();

        try (SocketChannel socketChannel = SocketChannel.open()) {
            NetworkConfig.optimizeSocket(socketChannel);
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socketChannel.configureBlocking(true);
            socketChannel.connect(new InetSocketAddress(SERVER_ADDRESS, port));

            if (socketChannel.isConnected()) {
                // Handshake inicial estructurado
                ProtocolService.writeNIO(socketChannel, new Mensaje(sessionId, CommunicationType.MESSAGE));

                // Asegurar que la metadata local lleve la estampa de tiempo correcta
                com.setLastModified(file.lastModified());
                ProtocolService.writeNIO(socketChannel, com);

                FileHandshakeCommunication respuesta = waitForHandshakeNIO(socketChannel);

                if (respuesta != null && respuesta.getAction() == FileHandshakeAction.START_TRANSFER) {
                    String idTransfe = transferenciaController.addTransference(
                            FileTransferState.SENDING.name(), com.getRecipient(), com.getRecipient(),
                            file.getName(), this, fileSize);

                    // Esperar la respuesta de la negociación interna del servidor
                    FileHandshakeCommunication ack = waitForHandshakeNIO(socketChannel);

                    if (ack.getAction() == FileHandshakeAction.SKIP_FILE) {
                        Logger.logInfo("[EMISOR] El receptor omitió el archivo porque ya es idéntico.");
                        transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, fileSize, fileSize);
                        return;
                    }

                    if (ack.getAction() == FileHandshakeAction.PROCESS_DELTAS) {
                        Logger.logWarn("[EMISOR] Procesando delta de Rsync para archivo individual...");

                        // 1. [RECIBE] Leer las firmas remotas enviadas por el receptor
                        RsyncSignatures remoteSignatures = (RsyncSignatures) ProtocolService.readNIO(socketChannel);

                        // Calcular peso aproximado de lo que se recibió en firmas (aprox 24 bytes por firma estructurada)
                        long bytesFirmasRecibidos = remoteSignatures.getSignatures().size() * 24L;

                        // 2. Calcular instrucciones diferenciales basadas en la ventana móvil
                        RsyncDeltaPackage deltas = calcularDeltasLocales(file, remoteSignatures);

                        // Calcular cuántos bytes físicos de datos puros (literales) vamos a inyectar en la red
                        long bytesLiteralesEnviados = 0;
                        long bloquesCopiadosDestino = 0;
                        for (RsyncDeltaInstruction inst : deltas.getInstructions()) {
                            if (inst.isLiteral()) {
                                bytesLiteralesEnviados += inst.getLiteralData().length;
                            } else {
                                bloquesCopiadosDestino++;
                            }
                        }

                        // 3. [ENVÍA] Enviar el paquete de deltas encapsulado
                        ProtocolService.writeNIO(socketChannel, deltas);

                        // 4. Esperar el ACK definitivo de reconstrucción del receptor
                        ProtocolService.readNIO(socketChannel);

                        // --- LOG DE RENDIMIENTO Y EFECTIVIDAD RSYNC (EMISOR) ---
                        double ahorro = (1.0 - ((double) bytesLiteralesEnviados / fileSize)) * 100.0;
                        Logger.logInfo("===============================================================");
                        Logger.logInfo("[EMISOR - REPORTE RSYNC] Sincronización finalizada exitosamente.");
                        Logger.logInfo("   -> Archivo original: " + file.getName() + " (" + formatSize(fileSize) + ")");
                        Logger.logInfo("   -> Bytes de Firmas recibidos (metadata): " + formatSize(bytesFirmasRecibidos));
                        Logger.logInfo("   -> Datos reales modificados (enviados): " + formatSize(bytesLiteralesEnviados));
                        Logger.logInfo("   -> Bloques reutilizados en destino: " + bloquesCopiadosDestino + " (" + formatSize(bloquesCopiadosDestino * BLOCK_SIZE) + ")");
                        Logger.logInfo(String.format("   -> EFICIENCIA DE RED: ¡Se evitó transferir el %.2f%% del archivo!", Math.max(0, ahorro)));
                        Logger.logInfo("===============================================================");

                        transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, fileSize, fileSize);
                        return;
                    }

                    // Flujo por defecto si el archivo es nuevo en el destino
                    Logger.logInfo("[EMISOR] Enviando archivo completo (Zero-Copy)...");
                    transferDataNIO(file, socketChannel, idTransfe, fileSize);
                    ProtocolService.readNIO(socketChannel); // Esperar confirmación de término
                }
            }
        } catch (Exception e) {
            Logger.logError("Error en sendFile con soporte Rsync: " + e.getMessage());
        }
    }

    private RsyncDeltaPackage calcularDeltasLocales(File file, RsyncSignatures remoteSignatures) throws Exception {
        byte[] fileData = Files.readAllBytes(file.toPath());
        int n = fileData.length;

        Map<Long, List<BlockSignature>> adlerMap = new HashMap<>();
        for (BlockSignature sig : remoteSignatures.getSignatures()) {
            adlerMap.computeIfAbsent(sig.getAdler32(), k -> new ArrayList<>()).add(sig);
        }

        List<RsyncDeltaInstruction> instructions = new ArrayList<>();
        ByteArrayOutputStream literalBuffer = new ByteArrayOutputStream();

        Adler32 adler = new Adler32();
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        int i = 0;
        while (i < n) {
            int remainingBytes = n - i;
            int currentBlockSize = Math.min(BLOCK_SIZE, remainingBytes);

            if (currentBlockSize < BLOCK_SIZE && remainingBytes == currentBlockSize) {
                for (int j = i; j < n; j++) {
                    literalBuffer.write(fileData[j]);
                }
                break;
            }

            adler.reset();
            adler.update(fileData, i, currentBlockSize);
            long currentAdler = adler.getValue();

            boolean matchFound = false;
            if (adlerMap.containsKey(currentAdler)) {
                md5.reset();
                md5.update(fileData, i, currentBlockSize);
                byte[] currentMd5 = md5.digest();

                for (BlockSignature sig : adlerMap.get(currentAdler)) {
                    if (Arrays.equals(sig.getMd5(), currentMd5)) {
                        if (literalBuffer.size() > 0) {
                            instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
                            literalBuffer.reset();
                        }
                        instructions.add(new RsyncDeltaInstruction(sig.getBlockIndex()));
                        i += currentBlockSize;
                        matchFound = true;
                        break;
                    }
                }
            }

            if (!matchFound) {
                literalBuffer.write(fileData[i]);
                i++;
            }
        }

        if (literalBuffer.size() > 0) {
            instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
        }

        return new RsyncDeltaPackage(instructions);
    }

    // ===============================================
    // --- LÓGICA DE RECEPCIÓN (RECEPTOR INDIVIDUAL) ---
    // ===============================================
    public void receiveFiles(String SERVER_ADDRESS, String port, FileHandshakeCommunication handshakeCommunication) {
        String sessionId = handshakeCommunication.getSessionId();
        var info = handshakeCommunication.getFileInfo();
        long fileSize = info.getSize();

        try (SocketChannel socketChannel = SocketChannel.open()) {
            NetworkConfig.optimizeSocket(socketChannel);
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socketChannel.configureBlocking(true);
            socketChannel.connect(new InetSocketAddress(SERVER_ADDRESS, Integer.parseInt(port)));

            if (transferenciaController.notifyTranference(handshakeCommunication)) {
                if (socketChannel.isConnected()) {
                    ProtocolService.writeNIO(socketChannel, new Mensaje(sessionId, CommunicationType.MESSAGE));
                    ProtocolService.writeNIO(socketChannel, new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));

                    if (confirmarInicioNIO(socketChannel, sessionId)) {
                        String idTrans = transferenciaController.addTransference(
                                FileTransferState.RECEIVING.name(), info.getRecipient(), info.getRecipient(), info.getName(), this, fileSize);

                        Path destPath = Paths.get(downloadDir, info.getName());

                        // --- QUICK CHECK ESTRUCTURAL ---
                        if (Files.exists(destPath)) {
                            long localSize = Files.size(destPath);
                            long localLastModified = Files.getLastModifiedTime(destPath).toMillis();

                            if (localSize == fileSize && localLastModified >= info.getLastModified()) {
                                Logger.logInfo("[RECEPTOR] -> [OMITIDO] Archivo idéntico (Size + Time): " + info.getName());
                                ProtocolService.writeNIO(socketChannel, new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                return;
                            }

                            // --- MODO RSYNC ACTIVADO ---
                            Logger.logInfo("[RECEPTOR] -> Diferencia detectada. Iniciando flujo diferencial para: " + info.getName());
                            ProtocolService.writeNIO(socketChannel, new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                            // 1. [ENVÍA] Generar y transmitir firmas locales del archivo base
                            RsyncSignatures firmasLocales = generarFirmasLocales(destPath);
                            long bytesFirmasEnviados = firmasLocales.getSignatures().size() * 24L;
                            ProtocolService.writeNIO(socketChannel, firmasLocales);

                            // 2. [RECIBE] Recibir instrucciones estructuradas
                            RsyncDeltaPackage deltaPkg = (RsyncDeltaPackage) ProtocolService.readNIO(socketChannel);

                            // Analizar el contenido del paquete recibido para las estadísticas
                            long bytesLiteralesRecibidos = 0;
                            long bloquesLocalesReutilizados = 0;
                            for (RsyncDeltaInstruction inst : deltaPkg.getInstructions()) {
                                if (inst.isLiteral()) {
                                    bytesLiteralesRecibidos += inst.getLiteralData().length;
                                } else {
                                    bloquesLocalesReutilizados++;
                                }
                            }

                            // 3. Reconstrucción por parches in-place
                            reconstruirArchivoRsync(destPath, deltaPkg);

                            // --- LOG DE RENDIMIENTO Y EFECTIVIDAD RSYNC (RECEPTOR) ---
                            double ahorro = (1.0 - ((double) bytesLiteralesRecibidos / fileSize)) * 100.0;
                            Logger.logInfo("===============================================================");
                            Logger.logInfo("[RECEPTOR - REPORTE RSYNC] Reconstrucción delta finalizada con éxito.");
                            Logger.logInfo("   -> Archivo esperado: " + info.getName() + " (" + formatSize(fileSize) + ")");
                            Logger.logInfo("   -> Bytes de Firmas despachados (metadata): " + formatSize(bytesFirmasEnviados));
                            Logger.logInfo("   -> Datos puros de red descargados: " + formatSize(bytesLiteralesRecibidos));
                            Logger.logInfo("   -> Bloques locales reciclados del disco: " + bloquesLocalesReutilizados + " (" + formatSize(bloquesLocalesReutilizados * BLOCK_SIZE) + ")");
                            Logger.logInfo(String.format("   -> IMPACTO EN RED: ¡Se evitó descargar el %.2f%% del total original!", Math.max(0, ahorro)));
                            Logger.logInfo("===============================================================");

                            // 4. Enviar notificación final de sincronización exitosa
                            ProtocolService.writeNIO(socketChannel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                            transferenciaController.updateProgressMetrics(FileTransferState.RECEIVING, idTrans, fileSize, fileSize);
                            return;
                        }

                        // --- TRANSFERENCIA TRADICIONAL COMPLETA (Archivo Nuevo) ---
                        Files.createDirectories(destPath.getParent());
                        ProtocolService.writeNIO(socketChannel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                        recibirArchivoCompletoNIO(socketChannel, destPath, fileSize, idTrans);
                        ProtocolService.writeNIO(socketChannel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                    }
                }
            } else {
                ProtocolService.writeNIO(socketChannel, new Mensaje(sessionId, CommunicationType.MESSAGE));
                ProtocolService.writeNIO(socketChannel, new FileHandshakeCommunication(FileHandshakeAction.DECLINE_REQUEST, sessionId));
            }
        } catch (Exception e) {
            Logger.logError("Error en receiveFiles con soporte Rsync: " + e.getMessage());
        }
    }

    private RsyncSignatures generarFirmasLocales(Path path) throws Exception {
        List<BlockSignature> list = new ArrayList<>();
        byte[] fileData = Files.readAllBytes(path);
        int totalBytes = fileData.length;
        int index = 0;
        int blockIdx = 0;

        Adler32 adler = new Adler32();
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        while (index < totalBytes) {
            int length = Math.min(BLOCK_SIZE, totalBytes - index);

            adler.reset();
            adler.update(fileData, index, length);
            long adlerHash = adler.getValue();

            md5.reset();
            md5.update(fileData, index, length);
            byte[] md5Hash = md5.digest();

            list.add(new BlockSignature(blockIdx++, adlerHash, md5Hash));
            index += length;
        }
        return new RsyncSignatures(list);
    }

    private void reconstruirArchivoRsync(Path targetPath, RsyncDeltaPackage packageDeltas) throws Exception {
        Path tempFile = Paths.get(targetPath.toString() + ".tmp");
        byte[] originalData = Files.readAllBytes(targetPath);

        try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
            for (RsyncDeltaInstruction inst : packageDeltas.getInstructions()) {
                if (inst.isLiteral()) {
                    fos.write(inst.getLiteralData());
                } else {
                    int blockIdx = inst.getBlockIndex();
                    int startOffset = blockIdx * BLOCK_SIZE;
                    int length = Math.min(BLOCK_SIZE, originalData.length - startOffset);
                    fos.write(originalData, startOffset, length);
                }
            }
        }
        Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void recibirArchivoCompletoNIO(SocketChannel channel, Path dest, long size, String idTrans) throws Exception {
        try (FileChannel fileChannel = FileChannel.open(dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            long readTotal = 0;
            while (readTotal < size) {
                long read = fileChannel.transferFrom(channel, readTotal, Math.min(size - readTotal, TRANSFER_CHUNK_SIZE));
                if (read <= 0) {
                    Thread.sleep(1);
                    if (read == -1) throw new IOException("Desconexión prematura del flujo de datos");
                    continue;
                }
                readTotal += read;
                transferenciaController.updateProgressMetrics(FileTransferState.RECEIVING, idTrans, readTotal, size);
            }
            fileChannel.force(true);
        }
    }

    private void transferDataNIO(File file, SocketChannel socketChannel, String idTrans, long size) throws IOException, InterruptedException {
        try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long position = 0;
            while (position < size && running) {
                checkPaused();
                long transferred = fileChannel.transferTo(position, Math.min(TRANSFER_CHUNK_SIZE, size - position), socketChannel);
                if (transferred <= 0) {
                    Thread.sleep(10);
                    continue;
                }
                position += transferred;
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTrans, position, size);
            }
        }
    }

    private boolean confirmarInicioNIO(SocketChannel channel, String sessionId) throws Exception {
        while (true) {
            Communication comm = ProtocolService.readNIO(channel);
            if (comm instanceof FileHandshakeCommunication f && f.getAction() == FileHandshakeAction.START_TRANSFER) {
                return true;
            }
            return false;
        }
    }

    private FileHandshakeCommunication waitForHandshakeNIO(SocketChannel channel) throws Exception {
        while (running) {
            Communication comm = ProtocolService.readNIO(channel);
            if (comm instanceof FileHandshakeCommunication handshake) {
                return handshake;
            }
            if (comm instanceof Mensaje m) {
                Logger.logInfo("Mensaje del servidor recibido: " + m.getContenido());
            }
        }
        throw new IOException("Se detuvo la espera del handshake porque el manager está inactivo.");
    }

    private void checkPaused() throws InterruptedException {
        synchronized (pauseLock) {
            while (paused) pauseLock.wait();
        }
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    public void stop() { running = false; resume(); }
    public void pause() { paused = true; }
    public void resume() { synchronized (pauseLock) { paused = false; pauseLock.notifyAll(); } }
    @Override public void cancel() { stop(); }
}