package org.bitBridge.Client.managers;

import org.bitBridge.controller.TransferenciaController;
import org.bitBridge.server.config.ConfigKey;
import org.bitBridge.shared.*;
import org.bitBridge.shared.config.ConfiguracionApp;
import org.bitBridge.shared.core.comunication.*;
import org.bitBridge.shared.core.comunication.model.basic.FileDirectoryCommunication;
import org.bitBridge.shared.core.comunication.model.basic.FileHandshakeCommunication;
import org.bitBridge.shared.core.comunication.model.basic.HandshakeMessage;
import org.bitBridge.shared.core.comunication.model.basic.Mensaje;
import org.bitBridge.shared.core.comunication.model.sync.BlockSignature;
import org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaInstruction;
import org.bitBridge.shared.core.comunication.model.sync.RsyncDeltaPackage;
import org.bitBridge.shared.core.comunication.model.sync.RsyncSignatures;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Adler32;

import static org.bitBridge.server.transfer.TransferSessionManager.PREFIX_REQUEST;

public class NioDirectoryTransferManager implements TransferManager {
    private volatile boolean running = true;
    private final TransferenciaController transferenciaController;
    private final String downloadDir;
    private long totalBytesProcessed = 0;
    private long totalSize = 0;

    // Variables dinámicas para el cálculo del ahorro físico real en red
    private long totalWireBytesTransmitted = 0;

    private   int BLOCK_SIZE = 64 * 1024; // Bloques de 64KB para el rolling hash

    public NioDirectoryTransferManager(TransferenciaController controller) {
        this.transferenciaController = controller;
        this.downloadDir = ConfiguracionApp.getInstancia().obtener(ConfigKey.DOWNLOAD_DIR);
    }

    // --- LÓGICA DE ENVÍO (SENDER) ---
    public  void sendDirectory(File rootDir, String host, int port, String recipient) {
        String sessionId = PREFIX_REQUEST + new Random().nextInt(10000);
        AtomicInteger fileCount = new AtomicInteger(0);
        AtomicLong sizeCount = new AtomicLong(0);

        Logger.logInfo("[SENDER] 🔍 Analizando estructura del directorio local: " + rootDir.getAbsolutePath());
        analyzeDirectory(rootDir, fileCount, sizeCount);
        this.totalSize = sizeCount.get();
        this.totalBytesProcessed = 0;
        this.totalWireBytesTransmitted = 0;

        Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
        Logger.logInfo("│  [NIO OUTBOUND DIRECTORY] Inicializando Envío Estructural        │");
        Logger.logInfo("├──────────────────────────────────────────────────────────────────┤");
        Logger.logInfo(String.format("│  ├── ID Sesión:  %s", sessionId));
        Logger.logInfo(String.format("│  ├── Directorio: %-47s │", rootDir.getName()));
        Logger.logInfo(String.format("│  ├── Archivos:   %-47d │", fileCount.get()));
        Logger.logInfo(String.format("│  └── Vol. Total: %-47s │", formatSize(totalSize)));
        Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");

        try (SocketChannel channel = SocketChannel.open()) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024);
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            channel.configureBlocking(true);

            Logger.logInfo(String.format("[SENDER-TCP] Conectando canal síncrono al socket relay » %s:%d", host, port));
            channel.connect(new InetSocketAddress(host, port));

            if (channel.isConnected()) {
                Logger.logInfo("[SENDER-NIO] Canal conectado. Despachando metadatos estructurales de la raíz...");
                //ProtocolService.writeNIO(channel, new Mensaje(sessionId));
                ProtocolService.writeNIO(channel, new HandshakeMessage(sessionId,SocketPurpose.FILE_TRANSFER,""));
                ProtocolService.writeNIO(channel, new FileDirectoryCommunication(rootDir.getName(), fileCount.get(), recipient, totalSize));

                Logger.logInfo("[SENDER-NIO] Esperando autorización START_TRANSFER del nodo receptor...");
                FileHandshakeCommunication respuesta = waitForHandshakeNIO(channel);

                if (respuesta != null && respuesta.getAction() == FileHandshakeAction.START_TRANSFER) {
                    Logger.logInfo("[SENDER-HANDSHAKE] Transferencia autorizada. Registrando métricas en base de datos...");
                    long startTime = System.currentTimeMillis();
                    String idTransfe = transferenciaController.addTransference(
                            FileTransferState.SENDING.name(), host, host, rootDir.getName(), this, totalSize
                    );

                    Logger.logInfo("[SENDER-CORE] Iniciando iterador recursivo de archivos sobre el árbol...");
                    enviarRecursivoNIO(channel, rootDir, rootDir.getName(), idTransfe, recipient);

                    Logger.logInfo("[SENDER-NIO] Árbol completado. Despachando instrucción final TRANSFER_DONE.");
                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.TRANSFER_DONE));

                    mostrarEstadisticasFinales(System.currentTimeMillis() - startTime, totalSize, totalWireBytesTransmitted, true);
                } else {
                    Logger.logError("[SENDER-REJECT] El receptor rechazó la inicialización del directorio.");
                }
            }
        } catch (Exception e) {
            Logger.logError("❌ [SENDER-CRITICAL-ERROR] Excepción en flujo NIO Send: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void analyzeDirectory(File dir, AtomicInteger files, AtomicLong size) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isFile()) {
                files.incrementAndGet();
                size.addAndGet(f.length());
            } else {
                analyzeDirectory(f, files, size);
            }
        }
    }

    private void enviarRecursivoNIO(SocketChannel socket, File file, String rootName, String idTransfe, String recipient) throws Exception {
        File[] files = file.listFiles();
        if (files == null) return;

        for (File target : files) {
            String relativePath = target.getAbsolutePath().substring(target.getAbsolutePath().indexOf(rootName));
            boolean isDir = target.isDirectory();
            long lastModified = target.lastModified();

            FileDirectoryCommunication meta = new FileDirectoryCommunication(
                    target.getName(),
                    isDir ? 0 : target.length(),
                    isDir,
                    relativePath
            );
            meta.setLastModified(lastModified);
            meta.setRecipient(recipient);

            //Logger.logInfo(String.format("[SENDER-WALK] Evaluando nodo estructural: %s (%s)", relativePath, isDir ? "DIR" : "FILE"));
            ProtocolService.writeNIO(socket, meta);

            FileHandshakeCommunication ack = waitForHandshakeNIO(socket);
            BLOCK_SIZE=calcularTamanoBloqueOptimo(target.length());
            if (ack.getAction() == FileHandshakeAction.SKIP_FILE) {
                //Logger.logInfo(String.format(" │ ⏩ [OMITIDO] Nodo remoto reporta archivo idéntico: %s", relativePath));
                totalBytesProcessed += target.length();
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
                continue;
            }

            if (isDir) {
                //Logger.logInfo(String.format(" │ 📂 [DIRECTORIO] Descendiendo de nivel recursivo en: %s", relativePath));
                enviarRecursivoNIO(socket, target, rootName, idTransfe, recipient);
            } else if (ack.getAction() == FileHandshakeAction.PROCESS_DELTAS) {
                //Logger.logWarn(String.format(" │ ⚡ [MODO RSYNC] Mutación detectada en: %s. Descargando firmas...", relativePath));

                RsyncSignatures signatures = (RsyncSignatures) ProtocolService.readNIO(socket);
                //Logger.logInfo(String.format(" │  ├── Recibidas %d firmas remotas. Ejecutando rolling hash O(1)...", signatures.getSignatures().size()));

                procesarYEnviarDeltas(socket, target, signatures, idTransfe);

                //Logger.logInfo(" │  └── Esperando confirmación de escritura física en disco del receptor...");
                ProtocolService.readNIO(socket);
            } else {
                //Logger.logInfo(String.format(" │ 📥 [MODO TRADICIONAL] Archivo nuevo. Entrando en Zero-Copy: %s", relativePath));
                enviarArchivoCompletoNIO(socket, target, idTransfe);

                //Logger.logInfo(" │  └── Esperando confirmación de vaciado de buffer remoto...");
                ProtocolService.readNIO(socket);
            }
        }
    }

    private void enviarArchivoCompletoNIO(SocketChannel socket, File file, String idTransfe) throws Exception {
        try (FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long pos = 0, size = file.length(), chunk = 4 * 1024 * 1024;
            while (pos < size) {
                long transferred = fc.transferTo(pos, Math.min(size - pos, chunk), socket);
                if (transferred <= 0) { Thread.sleep(10); continue; }
                pos += transferred;
                totalBytesProcessed += transferred;
                totalWireBytesTransmitted += transferred;
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
            }
        }
    }

    private void procesarYEnviarDeltas(SocketChannel socket, File file, RsyncSignatures signatures, String idTransfe) throws Exception {
        Map<Long, List<BlockSignature>> adlerMap = new HashMap<>();
        for (BlockSignature sig : signatures.getSignatures()) {
            adlerMap.computeIfAbsent(sig.getAdler32(), k -> new ArrayList<>()).add(sig);
        }

        List<RsyncDeltaInstruction> instructions = new ArrayList<>();
        ByteArrayOutputStream literalBuffer = new ByteArrayOutputStream();

        Adler32 adler = new Adler32();
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        // Optimizacion O(1): Usamos FileChannel y un ByteBuffer de tamaño fijo
        try (FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long fileSize = fc.size();
            long i = 0;
            int matchedBlocks = 0;
            int literalBytes = 0;

            // Buffer reutilizable en la Stack para las operaciones de hashing
            byte[] blockWindow = new byte[BLOCK_SIZE];
            java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.wrap(blockWindow);

            while (i < fileSize) {
                long remainingBytes = fileSize - i;
                int currentBlockSize = (int) Math.min(BLOCK_SIZE, remainingBytes);

                // Leer la ventana actual desde el canal sin cargar el archivo completo
                byteBuffer.clear();
                byteBuffer.limit(currentBlockSize);
                fc.position(i);
                fc.read(byteBuffer);

                if (currentBlockSize < BLOCK_SIZE && remainingBytes == currentBlockSize) {
                    for (int j = 0; j < currentBlockSize; j++) {
                        literalBuffer.write(blockWindow[j]);
                        literalBytes++;
                    }
                    break;
                }

                adler.reset();
                adler.update(blockWindow, 0, currentBlockSize);
                long currentAdler = adler.getValue();

                boolean matchFound = false;
                if (adlerMap.containsKey(currentAdler)) {
                    md5.reset();
                    md5.update(blockWindow, 0, currentBlockSize);
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
                            matchedBlocks++;
                            break;
                        }
                    }
                }

                if (!matchFound) {
                    literalBuffer.write(blockWindow[0]); // Inyectar el byte del frente de la ventana
                    literalBytes++;
                    i++;
                }
            }

            if (literalBuffer.size() > 0) {
                instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
            }

            RsyncDeltaPackage deltaPackage = new RsyncDeltaPackage(instructions);

            // Estimar el tamaño en bytes del paquete de deltas serializado para medir ahorro de red
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(deltaPackage);
            }
            long deltaSerializedSize = baos.size();
            totalWireBytesTransmitted += deltaSerializedSize;

            Logger.logInfo(String.format(" │   ├── [ANALISIS DELTA] Coincidencias: %d bloques | Literales: %s", matchedBlocks, formatSize(literalBytes)));
            Logger.logInfo(String.format(" │   └── [REDUCCION] Tamaño lógico original: %s » Payload Delta inyectado a red: %s", formatSize(fileSize), formatSize(deltaSerializedSize)));

            ProtocolService.writeNIO(socket, deltaPackage);

            totalBytesProcessed += fileSize;
            transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
        }
    }

    // --- LÓGICA DE RECEPCIÓN (RECEPTOR) ---
    public void receiveDirectory(String host, int port, FileHandshakeCommunication handshake) {
        String sessionId = handshake.getSessionId();
        var info = handshake.getFileInfo();

        this.totalBytesProcessed = 0;
        this.totalWireBytesTransmitted = 0;

        Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
        Logger.logInfo("│  [NIO INBOUND DIRECTORY] Inicializando Receptor Estructural      │");
        Logger.logInfo("├──────────────────────────────────────────────────────────────────┤");
        Logger.logInfo(String.format("│  ├── ID Sesión:    %s", sessionId));
        Logger.logInfo(String.format("│  ├── Nodo Origen:  %s:%d", host, port));
        Logger.logInfo(String.format("│  ├── Raíz Destino: %-47s │", info.getName()));
        Logger.logInfo(String.format("│  └── Vol. Esperado:%-47s │", formatSize(info.getSize())));
        Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");

        try (SocketChannel channel = SocketChannel.open()) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024);
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            channel.configureBlocking(true);

            if (transferenciaController.notifyTranference(handshake)) {
                //Logger.logInfo(String.format("[RECEPTOR-TCP] Estableciendo conexión inversa hacia %s:%d...", host, port));
                channel.connect(new InetSocketAddress(host, port));

                if (channel.isConnected()) {
                    //Logger.logInfo("[RECEPTOR-NIO] Canal de datos abierto. Transmitiendo Tokens de Handshake...");
                    //ProtocolService.writeNIO(channel, new Mensaje(sessionId));
                    ProtocolService.writeNIO(channel, new HandshakeMessage(sessionId,SocketPurpose.FILE_TRANSFER,""));
                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));

                    if (confirmarInicioNIO(channel, sessionId)) {
                        //Logger.logInfo("[RECEPTOR-HANDSHAKE] Handshake mutuo verificado. Escuchando instrucciones de la máquina de estados...");
                        String idTransfe = transferenciaController.addTransference(
                                FileTransferState.RECEIVING.name(), info.getRecipient(), info.getRecipient(), info.getName(), this, info.getSize()
                        );

                        long startTime = System.currentTimeMillis();

                        while (running) {
                            Communication meta = ProtocolService.readNIO(channel);

                            if (meta instanceof FileDirectoryCommunication fileMeta) {
                                Path destPath = Paths.get(downloadDir, fileMeta.getRelativePath());
                                //Logger.logInfo(String.format("[RECEPTOR-STREAM] Procesando instrucción para: %s", fileMeta.getRelativePath()));

                                if (Files.exists(destPath)) {
                                    if (fileMeta.isDirectory()) {
                                        //Logger.logInfo(" │ 📂 [OMITIDO] El directorio local ya está instanciado.");
                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                        continue;
                                    } else {
                                        long localSize = Files.size(destPath);
                                        long localLastModified = Files.getLastModifiedTime(destPath).toMillis();

                                        if (localSize == fileMeta.getSize() && localLastModified >= fileMeta.getLastModified()) {
                                            //Logger.logInfo(" │ ⏩ [OMITIDO] Atributos binarios y timestamp idénticos.");
                                            totalBytesProcessed += fileMeta.getSize();
                                            ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                            continue;
                                        }
                                        BLOCK_SIZE=calcularTamanoBloqueOptimo(info.getSize());
                                        //Logger.logWarn(" │ ⚡ [DIFERENCIA DETECTADA] Activando motor de sincronización Rsync...");
                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                                        //Logger.logInfo(" │  ├── Generando mapa local de firmas Adler32/MD5...");
                                        RsyncSignatures signatures = generarFirmasLocales(destPath);

                                        //Logger.logInfo(String.format(" │  ├── Enviando %d bloques de firmas al emisor...", signatures.getSignatures().size()));
                                        ProtocolService.writeNIO(channel, signatures);

                                        //Logger.logInfo(" │  ├── Leyendo paquete de deltas encapsulado desde el canal...");
                                        RsyncDeltaPackage deltaPkg = (RsyncDeltaPackage) ProtocolService.readNIO(channel);

                                        // Medir el peso del delta en el receptor
                                        /*ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                                            oos.writeObject(deltaPkg);
                                        }
                                        totalWireBytesTransmitted += baos.size();/*

                                         */


                                        //Logger.logInfo(" │  ├── Reconstruyendo archivo binario aplicando instrucciones...");
                                        reconstruirArchivoRsync(destPath, deltaPkg);

                                        totalBytesProcessed += fileMeta.getSize();
                                        transferenciaController.updateProgressMetrics(FileTransferState.RECEIVING, idTransfe, totalBytesProcessed, info.getSize());

                                        //Logger.logInfo(" │  └── Sincronización exitosa del nodo de datos. Confirmando ACK de liberación...");
                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                        continue;
                                    }
                                }

                                if (fileMeta.isDirectory()) {
                                    //Logger.logInfo(String.format(" │ 📁 [VIRTUAL-DIR] Instanciando árbol local: %s", destPath.toAbsolutePath()));
                                    Files.createDirectories(destPath);
                                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                    continue;
                                }

                                //Logger.logInfo(String.format(" │ 📥 [NUEVO] Creando asignación limpia de disco para: %s", destPath.getFileName()));
                                if (destPath.getParent() != null) {
                                    Files.createDirectories(destPath.getParent());
                                }

                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                recibirArchivoCompletoNIO(channel, destPath, fileMeta.getSize(), idTransfe, info.getSize());
                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                            } else if (meta instanceof FileHandshakeCommunication h && h.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                                Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
                                Logger.logInfo("│  ✅ [COMPLETO] Nodo remoto envió señal TRANSFER_DONE.           │");
                                Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");
                                mostrarEstadisticasFinales(System.currentTimeMillis() - startTime, info.getSize(), totalWireBytesTransmitted, false);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.logError("❌ [RECEPTOR-CRITICAL-ERROR] Fallo en la máquina de estados recursiva: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private RsyncSignatures generarFirmasLocales(Path path) throws Exception {
        List<BlockSignature> list = new ArrayList<>();

        // Optimización O(1): Leer secuencialmente con FileChannel
        try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
            long totalBytes = fc.size();
            long index = 0;
            int blockIdx = 0;

            Adler32 adler = new Adler32();
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            byte[] buffer = new byte[BLOCK_SIZE];
            java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.wrap(buffer);

            while (index < totalBytes) {
                int length = (int) Math.min(BLOCK_SIZE, totalBytes - index);

                byteBuffer.clear();
                byteBuffer.limit(length);
                fc.read(byteBuffer);

                adler.reset();
                adler.update(buffer, 0, length);
                long adlerHash = adler.getValue();

                md5.reset();
                md5.update(buffer, 0, length);
                byte[] md5Hash = md5.digest();

                list.add(new BlockSignature(blockIdx++, adlerHash, md5Hash));
                index += length;
            }
        }
        return new RsyncSignatures(list);
    }

    private void reconstruirArchivoRsync(Path targetPath, RsyncDeltaPackage packageDeltas) throws Exception {
        Path tempFile = Paths.get(targetPath.toString() + ".tmp");

        // Optimización Extrema: Copia quirúrgica bloque a bloque sin tocar la Heap
        try (FileChannel fcOriginal = FileChannel.open(targetPath, StandardOpenOption.READ);
             FileChannel fcTarget = FileChannel.open(tempFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

            long currentTargetPosition = 0;

            for (RsyncDeltaInstruction inst : packageDeltas.getInstructions()) {
                if (inst.isLiteral()) {
                    // Es data nueva: Escribir directo desde los bytes literales del delta
                    byte[] literal = inst.getLiteralData();
                    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(literal);
                    while (buf.hasRemaining()) {
                        currentTargetPosition += fcTarget.write(buf, currentTargetPosition);
                    }
                } else {
                    // Es un MATCH: Hacemos transferencia Zero-Copy entre descriptores de archivos locales
                    int blockIdx = inst.getBlockIndex();
                    long startOffset = (long) blockIdx * BLOCK_SIZE;
                    long length = Math.min(BLOCK_SIZE, fcOriginal.size() - startOffset);

                    long written = 0;
                    while (written < length) {
                        long transferred = fcOriginal.transferTo(startOffset + written, length - written, fcTarget);
                        fcTarget.position(currentTargetPosition + transferred);
                        written += transferred;
                        currentTargetPosition += transferred;
                    }
                }
            }
        }
        // Reemplazo atómico en el FileSystem de Linux
        Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void recibirArchivoCompletoNIO(SocketChannel channel, Path dest, long size, String id, long total) throws Exception {
        try (FileChannel fileChannel = FileChannel.open(dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            long readTotal = 0, limit = 4 * 1024 * 1024;
            while (readTotal < size) {
                long read = fileChannel.transferFrom(channel, readTotal, Math.min(size - readTotal, limit));
                if (read <= 0) { Thread.sleep(1); if (read == -1) throw new IOException("Desconexión"); continue; }
                readTotal += read;
                totalBytesProcessed += read;
                totalWireBytesTransmitted += read;
                transferenciaController.updateProgressMetrics(FileTransferState.RECEIVING, id, totalBytesProcessed, total);
            }
            fileChannel.force(true);
        }
    }

    private boolean confirmarInicioNIO(SocketChannel channel, String sessionId) throws Exception {
        Communication comm = ProtocolService.readNIO(channel);
        return comm instanceof FileHandshakeCommunication f && f.getAction() == FileHandshakeAction.START_TRANSFER;
    }

    private FileHandshakeCommunication waitForHandshakeNIO(SocketChannel channel) throws Exception {
        while (running) {
            Communication comm = ProtocolService.readNIO(channel);
            if (comm instanceof FileHandshakeCommunication handshake) return handshake;
        }
        throw new IOException("Cerrado");
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.2f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    // =================================─────────────────
    // --- NUEVO CONSOLIDADO ESTATICO PARA DIRECTORIOS ---
    // =================================─────────────────
    private void mostrarEstadisticasFinales(long millis, long totalLogicalSize, long totalWireBytes, boolean isSender) {
        double segundos = millis / 1000.0;
        long bytesAhorrados = totalLogicalSize - totalWireBytes;
        if (bytesAhorrados < 0) bytesAhorrados = 0;

        double eficienciaAlgoritmo = (totalLogicalSize > 0) ? ((double) bytesAhorrados / totalLogicalSize) * 100 : 0.0;
        double throughputMbps = (segundos > 0) ? ((totalWireBytes * 8.0) / (1024.0 * 1024.0)) / segundos : 0.0;

        String modoTexto = isSender ? "OUTBOUND DIRECTORY SENDER" : "INBOUND DIRECTORY RECEIVER";

        Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
        Logger.logInfo(String.format("│ 🔥 [REPORTE CONSOLIDADO - %s]               │", modoTexto));
        Logger.logInfo("├──────────────────────────────────────────────────────────────────┤");
        Logger.logInfo(String.format("│  ├── Tiempo de Procesamiento: %-20s                │", String.format("%.3f seg", segundos)));
        Logger.logInfo(String.format("│  ├── Volumen Estructural:     %-20s                │", formatSize(totalLogicalSize)));
        Logger.logInfo(String.format("│  ├── Transferido Real (Red):  %-20s                │", formatSize(totalWireBytes)));
        Logger.logInfo(String.format("│  ├── Ancho de Banda Ahorrado: %-20s                │", formatSize(bytesAhorrados)));
        Logger.logInfo(String.format("│  ├── Eficiencia de Red Rsync: %-20s                │", String.format("%.2f%%", eficienciaAlgoritmo)));
        Logger.logInfo(String.format("│  └── Tasa Real de Inyección:  %-20s                │", String.format("%.2f Mbps", throughputMbps)));
        Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");
    }
    public  int calcularTamanoBloqueOptimo(long tamanoArchivo) {
        if (tamanoArchivo < 1024 * 1024) return 2048; // 2KB para archivos < 1MB

        // Cálculo basado en la raíz cuadrada
        int calculado = (int) Math.sqrt(tamanoArchivo);

        // Alinear a potencias de 2 para mejorar el rendimiento de lectura en disco (Buffer de NIO)
        int bloque = Integer.highestOneBit(calculado);

        // Acotar entre 4KB y 64KB (o 128KB según tu infraestructura física)
        return Math.max(4096, Math.min(bloque, 64 * 1024));
    }
    @Override public void stop() { running = false; }
    @Override public void cancel() { stop(); }
    @Override public void pause() {}
    @Override public void resume() {}
}