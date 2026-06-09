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
import org.bitBridge.shared.network.NetworkConfig;
import org.bitBridge.shared.network.ProtocolService;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.Adler32;

import static org.bitBridge.server.transfer.TransferSessionManager.PREFIX_REQUEST;

public class FileTransferManager implements TransferManager {
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();

    private final TransferenciaController transferenciaController;
    private final String downloadDir;

    private int BLOCK_SIZE = 64 * 1024;
    private int TRANSFER_CHUNK_SIZE = 4 * 1024 * 1024;

    public FileTransferManager(TransferenciaController transferenciaController) {
        this.transferenciaController = transferenciaController;
        this.downloadDir = ConfiguracionApp.getInstancia().obtener(ConfigKey.DOWNLOAD_DIR);
    }

    // ==========================================
    // --- LÓGICA DE ENVÍO (SENDER INDIVIDUAL) ---
    // ==========================================

    public void sendFile(FileDirectoryCommunication com, File targetFile, String host, int port) {
        if (targetFile == null || !targetFile.isFile()) {
            Logger.logError("❌ [SENDER-ERROR] El archivo proporcionado no es válido o es un directorio.");
            return;
        }

        String sessionId = PREFIX_REQUEST + new Random().nextInt(10000);
        var totalSize = targetFile.length();
        var totalBytesProcessed = 0;

        Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
        Logger.logInfo("│  [NIO OUTBOUND SENDER] Inicializando Sesión de Transferencia     │");
        Logger.logInfo("├──────────────────────────────────────────────────────────────────┤");
        Logger.logInfo(String.format("│  ├── ID Asignado: %s", sessionId));
        Logger.logInfo(String.format("│  ├── Archivo:    %-47s │", targetFile.getName()));
        Logger.logInfo(String.format("│  └── Volumen:    %s ", formatSize(totalSize)));
        Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");
        BLOCK_SIZE=calcularTamanoBloqueOptimo(totalSize);
        Logger.logInfo("Tamaño del bloque: "+formatSize(BLOCK_SIZE));

        boolean rsyncMode = false;
        long bytesEnviadosRed = 0;

        try (SocketChannel channel = SocketChannel.open()) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024);
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            channel.configureBlocking(true);

            Logger.logInfo(String.format("[TCP-SOCKET] Abriendo canal asíncrono hacia el host » %s:%d", host, port));
            channel.connect(new InetSocketAddress(host, port));

            if (channel.isConnected()) {
                Logger.logInfo("[TCP-SOCKET] Conexión establecida. Iniciando Handshake Fase 1 (Autenticación)...");

                //ProtocolService.writeNIO(channel, new Mensaje(sessionId));
                ProtocolService.writeNIO(channel, new HandshakeMessage(sessionId,SocketPurpose.FILE_TRANSFER,""));

                FileDirectoryCommunication handshakeMeta = new FileDirectoryCommunication(
                        targetFile.getName(), totalSize, false, targetFile.getName()
                );
                handshakeMeta.setRecipient(com.getRecipient());

                Logger.logInfo("[NIO-WRITE] Despachando handshakeMeta inicial al Server Relay...");
                ProtocolService.writeNIO(channel, handshakeMeta);

                Logger.logInfo("[NIO-READ] Esperando respuesta de autorización (START_TRANSFER)...");
                FileHandshakeCommunication respuesta = waitForHandshakeNIO(channel);

                if (respuesta != null && respuesta.getAction() == FileHandshakeAction.START_TRANSFER) {
                    Logger.logInfo("[HANDSHAKE] Fase 1 CONFIRMADA por el nodo remoto. Registrando métricas...");

                    long startTime = System.currentTimeMillis();
                    String idTransfe = transferenciaController.addTransference(
                            FileTransferState.SENDING.name(), host, host, targetFile.getName(), this, totalSize
                    );

                    String relativePath = targetFile.getName();
                    long lastModified = targetFile.lastModified();

                    FileDirectoryCommunication meta = new FileDirectoryCommunication(
                            targetFile.getName(), totalSize, false, relativePath
                    );
                    meta.setLastModified(lastModified);
                    meta.setRecipient(com.getRecipient());

                    Logger.logInfo(String.format("[NIO-WRITE] Enviando metadatos específicos del payload local (Timestamp: %d)...", lastModified));
                    ProtocolService.writeNIO(channel, meta);

                    Logger.logInfo("[NIO-READ] Esperando resolución del Receptor (Evaluación de redundancia)...");
                    FileHandshakeCommunication ack = waitForHandshakeNIO(channel);

                    if (ack.getAction() == FileHandshakeAction.SKIP_FILE) {
                        Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
                        Logger.logInfo(String.format("│  ⏩ [OMITIDO] Archivo idéntico en destino: %-21s │", relativePath));
                        Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");
                        totalBytesProcessed += (int) totalSize;
                        transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
                        bytesEnviadosRed = 0;
                    }
                    else if (ack.getAction() == FileHandshakeAction.PROCESS_DELTAS) {
                        rsyncMode = true;
                        Logger.logWarn("┌──────────────────────────────────────────────────────────────────┐");
                        Logger.logWarn("│  ⚡ [MODO RSYNC] Archivo modificado detectado. Iniciando Delta.  │");
                        Logger.logWarn("└──────────────────────────────────────────────────────────────────┘");

                        Logger.logInfo("[NIO-READ] Descargando mapa de firmas Adler32/MD5 del receptor remoto...");
                        RsyncSignatures signatures = (RsyncSignatures) ProtocolService.readNIO(channel);

                        Logger.logInfo(String.format("[RSYNC-CORE] Procesando %d firmas con algoritmo de ventana deslizante...", signatures.getSignatures().size()));

                        RsyncDeltaPackage deltaPackage = calcularDeltasLocales(targetFile, signatures);

                        bytesEnviadosRed = deltaPackage.getInstructions().stream()
                                .mapToLong(inst -> inst.isLiteral() ? inst.getLiteralData().length : 4) // 4 bytes por índice de bloque coincidente
                                .sum();

                        Logger.logInfo("[NIO-WRITE] Despachando paquete de deltas optimizado hacia la red...: "+formatSize(bytesEnviadosRed));

                        ProtocolService.writeNIO(channel, deltaPackage);
                        transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalSize, totalSize);

                        Logger.logInfo("[NIO-READ] Esperando ACK final de sincronización del Server Relay...");
                        ProtocolService.readNIO(channel);
                    }
                    else {
                        Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
                        Logger.logInfo("│  📥 [MODO TRADICIONAL] Archivo nuevo. Activando Zero-Copy Pipe. │");
                        Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");

                        Logger.logInfo("[KERNEL-IO] Ejecutando FileChannel.transferTo() hacia el SocketChannel...");
                        enviarArchivoCompletoNIO(channel, targetFile, idTransfe);
                        bytesEnviadosRed = totalSize;

                        Logger.logInfo("[NIO-READ] Esperando confirmación de escritura física (Disk Flush) del receptor...");
                        ProtocolService.readNIO(channel);
                    }

                    Logger.logInfo("[NIO-WRITE] Transmisión de instrucciones finalizada. Despachando TRANSFER_DONE.");
                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.TRANSFER_DONE));

                    // Llamada con métricas detalladas de red
                    mostrarEstadisticasFinales(System.currentTimeMillis() - startTime, totalSize, bytesEnviadosRed, rsyncMode, true);
                } else {
                    Logger.logError("[HANDSHAKE-REJECT] El nodo remoto rechazó o canceló la inicialización de la transferencia.");
                }
            }
        } catch (Exception e) {
            Logger.logError("❌ [CRITICAL-ERROR] Excepción I/O en la máquina de estados del Emisor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void procesarYEnviarDeltas(SocketChannel socket, File file, RsyncSignatures signatures, String idTransfe) throws Exception {
        RsyncDeltaPackage deltaPackage = calcularDeltasLocales(file, signatures);
        ProtocolService.writeNIO(socket, deltaPackage);
        transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, file.length(), file.length());
    }


    private RsyncDeltaPackage calcularDeltasLocales(File file, RsyncSignatures remoteSignatures) throws Exception {
        long startTime = System.currentTimeMillis();
        Logger.logInfo(String.format("[RSYNC-DIAG] 🔍 Iniciando análisis local para: %s (%s)", file.getName(), formatSize(file.length())));

        long totalFileSize = file.length();
        int expectedBlocks = remoteSignatures.getSignatures().size();
        Logger.logInfo("[RSYNC-DIAG] 📥 Firmas remotas recibidas: " + expectedBlocks + " bloques.");

        // Optimización de carga para el mapa de firmas
        Map<Long, List<BlockSignature>> adlerMap = new HashMap<>((int) (expectedBlocks / 0.75f) + 1);
        for (BlockSignature sig : remoteSignatures.getSignatures()) {
            adlerMap.computeIfAbsent(sig.getAdler32(), k -> new ArrayList<>(2)).add(sig);
        }

        List<RsyncDeltaInstruction> instructions = new ArrayList<>();
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        // --- VARIABLES DE CONTROL DE VENTANA DESLIZANTE ---
        long i = 0; // Puntero global en el archivo
        long literalStart = 0;
        long literalBytesCount = 0;
        int matchedBlocks = 0;
        final int MOD_ADLER = 65521;

        int adlerCollisions = 0;
        int totalAdlerHits = 0;
        int totalRollingSteps = 0;
        int ultimoProgresoReportado = -1;

        // Abrimos el archivo mediante canales NIO
        try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {

            // Un solo buffer auxiliar para calcular el MD5 de un bloque en memoria Heap sin reasignaciones
            byte[] blockBuffer = new byte[BLOCK_SIZE];
            ByteBuffer blockByteBuffer = ByteBuffer.wrap(blockBuffer);

            // --- INICIALIZACIÓN DEL PRIMER BLOQUE ---
            int s1 = 1;
            int s2 = 0;

            if (totalFileSize >= BLOCK_SIZE) {
                blockByteBuffer.clear();
                fileChannel.read(blockByteBuffer, 0);
                for (int j = 0; j < BLOCK_SIZE; j++) {
                    s1 = (s1 + (blockBuffer[j] & 0xFF)) % MOD_ADLER;
                    s2 = (s2 + s1) % MOD_ADLER;
                }
            }
            long currentAdler = ((long) s2 << 16) | s1;

            // --- ciclo principal de la ventana DESLIZANTE ---
            while (i <= totalFileSize - BLOCK_SIZE) {

                // Muestreo de progreso
                int progresoActual = (int) (((double) i / totalFileSize) * 100);
                if (progresoActual % 10 == 0 && progresoActual != ultimoProgresoReportado) {
                    Logger.logInfo(String.format("   ↳ [PROGRESO %d%%] Puntero i: %d/%d bytes. Ints. acumuladas: %d | Matches: %d | Literales: %s",
                            progresoActual, i, totalFileSize, instructions.size(), matchedBlocks, formatSize(literalBytesCount)));
                    ultimoProgresoReportado = progresoActual;
                }

                boolean matchFound = false;

                if (adlerMap.containsKey(currentAdler)) {
                    totalAdlerHits++;

                    // Leer el bloque actual en el offset 'i' para verificar criptográficamente con MD5
                    blockByteBuffer.clear();
                    fileChannel.read(blockByteBuffer, i);

                    md5.reset();
                    md5.update(blockBuffer, 0, BLOCK_SIZE);
                    byte[] currentMd5 = md5.digest();

                    boolean cryptographicMatch = false;
                    for (BlockSignature sig : adlerMap.get(currentAdler)) {
                        if (Arrays.equals(sig.getMd5(), currentMd5)) {

                            // Procesar el bloque literal acumulado antes de este match
                            long literalLength = i - literalStart;
                            if (literalLength > 0) {
                                byte[] literalData = new byte[(int) literalLength];
                                ByteBuffer litBuffer = ByteBuffer.wrap(literalData);
                                fileChannel.read(litBuffer, literalStart);

                                instructions.add(new RsyncDeltaInstruction(literalData));
                                literalBytesCount += literalLength;
                            }

                            // Añadir instrucción del bloque coincidente indexado
                            instructions.add(new RsyncDeltaInstruction(sig.getBlockIndex()));

                            i += BLOCK_SIZE;
                            literalStart = i;
                            matchFound = true;
                            cryptographicMatch = true;
                            matchedBlocks++;
                            break;
                        }
                    }

                    if (!cryptographicMatch) {
                        adlerCollisions++;
                    }
                }

                if (matchFound) {
                    // Si hubo Match, recalculamos el Adler inicial para la nueva posición de la ventana
                    if (i <= totalFileSize - BLOCK_SIZE) {
                        blockByteBuffer.clear();
                        fileChannel.read(blockByteBuffer, i);
                        s1 = 1; s2 = 0;
                        for (int j = 0; j < BLOCK_SIZE; j++) {
                            s1 = (s1 + (blockBuffer[j] & 0xFF)) % MOD_ADLER;
                            s2 = (s2 + s1) % MOD_ADLER;
                        }
                        currentAdler = ((long) s2 << 16) | s1;
                    }
                } else {
                    totalRollingSteps++;
                    if (i + BLOCK_SIZE < totalFileSize) {
                        // Ventana Deslizante O(1) leyendo directo del canal de forma posicional rápida
                        ByteBuffer bytesAux = ByteBuffer.allocate(2);

                        fileChannel.read(bytesAux, i); // Lee byteSaliente
                        int byteSaliente = bytesAux.get(0) & 0xFF;

                        bytesAux.clear();
                        fileChannel.read(bytesAux, i + BLOCK_SIZE); // Lee byteEntrante
                        int byteEntrante = bytesAux.get(0) & 0xFF;

                        s1 = (s1 - byteSaliente + byteEntrante) % MOD_ADLER;
                        if (s1 < 0) s1 += MOD_ADLER;

                        s2 = (s2 - (BLOCK_SIZE * byteSaliente) + s1 - 1) % MOD_ADLER;
                        if (s2 < 0) s2 += MOD_ADLER;

                        currentAdler = ((long) s2 << 16) | s1;
                        i++;
                    } else {
                        i++;
                    }
                }
            }

            // Manejar remanente literal al final del archivo
            long finalLiteralLength = totalFileSize - literalStart;
            if (finalLiteralLength > 0) {
                byte[] literalData = new byte[(int) finalLiteralLength];
                ByteBuffer finalLitBuffer = ByteBuffer.wrap(literalData);
                fileChannel.read(finalLitBuffer, literalStart);
                instructions.add(new RsyncDeltaInstruction(literalData));
                literalBytesCount += finalLiteralLength;
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // --- REPORTE DE METADATOS FORENSE ---
        Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
        Logger.logInfo("│ 🛠️  [INFORME DE REDUNDANCIA RSYNC - BITBRIDGE]                   │");
        Logger.logInfo("├──────────────────────────────────────────────────────────────────┤");
        Logger.logInfo(String.format("│  ├── Tiempo en CPU:           %s", String.format("%-38s │", duration + " ms")));
        Logger.logInfo(String.format("│  ├── Instrucciones Totales:   %-38d │", instructions.size()));
        Logger.logInfo(String.format("│  ├── Bloques Coincidentes:   %-38d │", matchedBlocks));
        Logger.logInfo(String.format("│  ├── Tamaño de Payload Literal:%s", String.format("%-38s │", formatSize(literalBytesCount))));
        Logger.logInfo(String.format("│  ├── Evaluaciones Adler32:    %-38d │", totalAdlerHits));
        Logger.logInfo(String.format("│  ├── Desplazamientos O(1):    %-38d │", totalRollingSteps));
        Logger.logInfo(String.format("│  └── Colisiones Falsas Adler: %-38d │", adlerCollisions));
        Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");

        return new RsyncDeltaPackage(instructions);
    }

    private void enviarArchivoCompletoNIO(SocketChannel socketChannel, File file, String idTransfe) throws Exception {
        try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long position = 0;
            long size = file.length();
            while (position < size && running) {
                checkPaused();
                long transferred = fileChannel.transferTo(position, Math.min(TRANSFER_CHUNK_SIZE, size - position), socketChannel);
                if (transferred <= 0) {
                    Thread.sleep(10);
                    continue;
                }
                position += transferred;
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, position, size);
            }
        }
    }

    // ===============================================
    // --- LÓGICA DE RECEPCIÓN (RECEPTOR INDIVIDUAL) ---
    // ===============================================

    public void receiveFile(String host, int port, FileHandshakeCommunication handshake) {
        String sessionId = handshake.getSessionId();
        var info = handshake.getFileInfo();

        Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
        Logger.logInfo("│  [NIO RECEIVER] Inicializando Conexión Inbound de Archivo        │");
        Logger.logInfo("├──────────────────────────────────────────────────────────────────┤");
        Logger.logInfo(String.format("│  ↳ ID Sesión:  %s", sessionId));
        Logger.logInfo(String.format("│  ↳ Objetivo:   %-50s │", info.getName()));
        Logger.logInfo(String.format("│  ↳ Tamaño:     %s", formatSize(info.getSize())));
        Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");
        BLOCK_SIZE=calcularTamanoBloqueOptimo(info.getSize());

        boolean rsyncMode = false;
        long bytesRecibidosRed = 0;
        long startTime = 0;

        try (SocketChannel channel = SocketChannel.open()) {
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_SNDBUF, 4 * 1024 * 1024);
            channel.setOption(StandardSocketOptions.SO_RCVBUF, 4 * 1024 * 1024);
            channel.configureBlocking(true);

            if (transferenciaController.notifyTranference(handshake)) {
                Logger.logInfo(String.format("[TCP-SOCKET] Conectando a nodo remoto en %s:%d ...", host, port));
                channel.connect(new InetSocketAddress(host, port));

                if (channel.isConnected()) {
                    Logger.logInfo("[TCP-SOCKET] SocketChannel conectado. Despachando Handshake inicial...");

                    ProtocolService.writeNIO(channel, new HandshakeMessage(sessionId, SocketPurpose.FILE_TRANSFER, ""));

                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));

                    if (confirmarInicioNIO(channel, sessionId)) {
                        Logger.logInfo("[HANDSHAKE] Handshake inicial de bitBridge CONFIRMADO por el emisor.");

                        String idTransfe = transferenciaController.addTransference(
                                FileTransferState.RECEIVING.name(), info.getRecipient(), info.getRecipient(), info.getName(), this, info.getSize()
                        );

                        Logger.logInfo("[NIO-READ] Esperando metadatos lógicos (FileDirectoryCommunication)...");
                        Communication meta = ProtocolService.readNIO(channel);

                        if (meta instanceof FileDirectoryCommunication fileMeta) {
                            Path destPath = Paths.get(downloadDir, fileMeta.getRelativePath());
                            Logger.logInfo(String.format("[PATH-RESOLVER] Ruta local destino establecida: %s", destPath.toAbsolutePath()));

                            startTime = System.currentTimeMillis();

                            if (Files.exists(destPath)) {
                                long localSize = Files.size(destPath);
                                long localLastModified = Files.getLastModifiedTime(destPath).toMillis();

                                Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
                                Logger.logInfo("│  [EVALUACIÓN DE ESTADO LOCAL] Archivo preexistente detectado    │");
                                Logger.logInfo("├──────────────────────────────────────────────────────────────────┤");
                                Logger.logInfo(String.format("│  ├── Tamaño Local:  %10d B  |  Remoto: %10d B     │", localSize, fileMeta.getSize()));
                                Logger.logInfo(String.format("│  └── Modificado:    %10d ms |  Remoto: %10d ms    │", localLastModified, fileMeta.getLastModified()));
                                Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");

                                if (localSize == fileMeta.getSize() && localLastModified >= fileMeta.getLastModified()) {
                                    Logger.logInfo("│  ⏩ [OMISIÓN] El archivo local es idéntico al remoto. Saltando...│");
                                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                    return;
                                }

                                rsyncMode = true;
                                Logger.logInfo("⚡ [MODO RSYNC] Se detectaron diferencias. Iniciando sincronización diferencial...");
                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                                Logger.logInfo("[RSYNC-SIGN] Generando firmas Adler32 y MD5 del archivo local desactualizado...");
                                RsyncSignatures signatures = generarFirmasLocales(destPath);

                                Logger.logInfo(String.format("[NIO-WRITE] Enviando %d firmas estructurales al emisor...", signatures.getSignatures().size()));
                                ProtocolService.writeNIO(channel, signatures);

                                Logger.logInfo("[NIO-READ] Esperando paquete de deltas encapsulado (RsyncDeltaPackage)...");
                                Communication deltaComm = ProtocolService.readNIO(channel);
                                if (!(deltaComm instanceof RsyncDeltaPackage deltaPkg)) {
                                    throw new IOException("Se esperaba un RsyncDeltaPackage, pero se recibió un paquete inválido.");
                                }
                                Logger.logInfo("[NIO-READ] paquete de deltas recibido (RsyncDeltaPackage)...");

                                // Estimar volumen físico mutado recibido en la estructura
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ObjectOutputStream oos = new ObjectOutputStream(baos);
                                oos.writeObject(deltaPkg);
                                oos.flush();
                                bytesRecibidosRed = baos.size();

                                Logger.logInfo("[DISK-IO] Reconstruyendo archivo binario local aplicando deltas literales...");
                                reconstruirArchivoRsync(destPath, deltaPkg,idTransfe,info.getSize());

                                Logger.logInfo("[HANDSHAKE] Notificando finalización y reconstrucción exitosa de deltas (Fase 3)...");
                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));

                                Logger.logInfo("[HANDSHAKE] Notificando finalización de bloque de deltas.");
                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                            } else {
                                Logger.logInfo("📥 [MODO TRADICIONAL] Archivo nuevo. Activando descarga limpia...");
                                if (destPath.getParent() != null) {
                                    Files.createDirectories(destPath.getParent());
                                    Logger.logInfo(String.format("[DISK-IO] Directorios estructurales creados: %s", destPath.getParent()));
                                }

                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                                Logger.logInfo(String.format("[NIO-STREAM] Leyendo carga binaria completa (%d bytes)...", fileMeta.getSize()));
                                recibirArchivoCompletoNIO(channel, destPath, fileMeta.getSize(), idTransfe);
                                bytesRecibidosRed = fileMeta.getSize();

                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                            }
                        }

                        Logger.logInfo("[NIO-READ] Esperando confirmación de cierre atómico (TRANSFER_DONE)...");
                        Communication finalAck = ProtocolService.readNIO(channel);
                        if (finalAck instanceof FileHandshakeCommunication h && h.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                            Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
                            Logger.logInfo("│  ✅ [COMPLETO] Flujo de Red Cerrado de Forma Segura             │");
                            Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");
                        }

                        long duration = System.currentTimeMillis() - startTime;
                        mostrarEstadisticasFinales(duration, info.getSize(), bytesRecibidosRed, rsyncMode, false);
                    }
                }
            }
        } catch (Exception e) {
            Logger.logError("❌ [CRITICAL-ERROR] Fallo en la máquina de estados de FileTransferManager: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private RsyncSignatures generarFirmasLocales(Path path) throws Exception {
        List<BlockSignature> list = new ArrayList<>();

        // Obtener el tamaño del archivo usando tipos long para soportar archivos > 2GB
        long totalBytes = Files.size(path);
        long index = 0;
        int blockIdx = 0;

        // Instanciamos los motores de hashing
        Adler32 adler = new Adler32();
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        // Reservamos un único buffer en memoria Heap del tamaño de un bloque
        byte[] buffer = new byte[BLOCK_SIZE];
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        // Abrimos el canal de lectura NIO de forma eficiente
        try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ)) {

            while (index < totalBytes) {
                // Calcular el tamaño del bloque actual (el último bloque puede ser más pequeño)
                int length = (int) Math.min((long) BLOCK_SIZE, totalBytes - index);

                // Preparar el buffer para la lectura exacta de este bloque
                byteBuffer.clear();
                byteBuffer.limit(length);

                // Leer secuencialmente desde el disco al buffer
                while (byteBuffer.hasRemaining()) {
                    if (fileChannel.read(byteBuffer, index) == -1) {
                        break;
                    }
                }

                // 1. Calcular Adler32 sobre el arreglo de bytes del buffer
                adler.reset();
                adler.update(buffer, 0, length);
                long adlerHash = adler.getValue();

                // 2. Calcular MD5 sobre el mismo fragmento
                md5.reset();
                md5.update(buffer, 0, length);
                byte[] md5Hash = md5.digest();

                // Agregar la firma indexada a la lista
                list.add(new BlockSignature(blockIdx++, adlerHash, md5Hash));

                // Avanzar el puntero global usando aritmética long
                index += length;
            }
        }

        return new RsyncSignatures(list);
    }

    private void reconstruirArchivoRsync(Path targetPath, RsyncDeltaPackage packageDeltas, String idTrans, long expectedTotalSize) throws Exception {
        Path tempFile = Paths.get(targetPath.toString() + ".tmp");
        long bytesProcesados = 0;

        // Obtener el tamaño original del archivo para los límites de los bloques
        long originalLength = Files.size(targetPath);

        // Reutilizamos un único buffer en memoria Heap para copiar los bloques del archivo original
        ByteBuffer blockBuffer = ByteBuffer.allocate(BLOCK_SIZE);

        // Abrimos el archivo original para lectura y el archivo temporal para escritura usando canales NIO
        try (FileChannel srcChannel = FileChannel.open(targetPath, StandardOpenOption.READ);
             FileChannel destChannel = FileChannel.open(tempFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

            for (RsyncDeltaInstruction inst : packageDeltas.getInstructions()) {
                if (inst.isLiteral()) {
                    // 1. Escribir datos literales (nuevos)
                    byte[] literalData = inst.getLiteralData();
                    ByteBuffer literalBuffer = ByteBuffer.wrap(literalData);

                    while (literalBuffer.hasRemaining()) {
                        destChannel.write(literalBuffer);
                    }
                    bytesProcesados += literalData.length;
                } else {
                    // 2. Reutilizar bloque del archivo original
                    int blockIdx = inst.getBlockIndex();
                    long startOffset = (long) blockIdx * BLOCK_SIZE; // Casteo a long para evitar desbordamiento int
                    long length = Math.min((long) BLOCK_SIZE, originalLength - startOffset);

                    // Limpiar el buffer para la nueva lectura
                    blockBuffer.clear();
                    blockBuffer.limit((int) length);

                    // Leer el fragmento específico del archivo base sin cargarlo entero en RAM
                    long currentOffset = startOffset;
                    while (blockBuffer.hasRemaining()) {
                        int bytesRead = srcChannel.read(blockBuffer, currentOffset);
                        if (bytesRead == -1) break;
                        currentOffset += bytesRead;
                    }

                    // Voltear el buffer para prepararlo para la escritura
                    blockBuffer.flip();

                    while (blockBuffer.hasRemaining()) {
                        destChannel.write(blockBuffer);
                    }
                    bytesProcesados += length;
                }

                // Notificar el progreso al controlador por cada instrucción procesada
                transferenciaController.updateProgressMetrics(
                        FileTransferState.RECEIVING,
                        idTrans,
                        bytesProcesados,
                        expectedTotalSize
                );
            }

            // Asegurar que los datos físicos se escriban en el almacenamiento antes de cerrar
            destChannel.force(true);
        }

        // Reemplazo atómico del archivo original por el nuevo reconstruido
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

    private boolean confirmarInicioNIO(SocketChannel channel, String sessionId) throws Exception {
        Communication comm = ProtocolService.readNIO(channel);
        return comm instanceof FileHandshakeCommunication f && f.getAction() == FileHandshakeAction.START_TRANSFER;
    }

    private FileHandshakeCommunication waitForHandshakeNIO(SocketChannel channel) throws Exception {
        while (running) {
            Communication comm = ProtocolService.readNIO(channel);
            if (comm instanceof FileHandshakeCommunication handshake) {
                return handshake;
            }
        }
        throw new IOException("Se cerró la interfaz de lectura.");
    }

    private void checkPaused() throws InterruptedException {
        synchronized (pauseLock) {
            while (paused) pauseLock.wait();
        }
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.2f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    // ==========================================
    // --- NUEVA SALIDA CONSOLIDADA DETALLADA ---
    // ==========================================
    private void mostrarEstadisticasFinales(long millis, long totalLogicalSize, long actualWireBytes, boolean rsyncMode, boolean isSender) {
        double segundos = millis / 1000.0;
        long bytesAhorrados = totalLogicalSize - actualWireBytes;
        if (bytesAhorrados < 0) bytesAhorrados = 0; // Prevenir deltas ligeramente mayores por overhead estructural

        double porcentajeEficiencia = (totalLogicalSize > 0) ? ((double) bytesAhorrados / totalLogicalSize) * 100 : 0.0;
        double throughputMbps = (segundos > 0) ? ((actualWireBytes * 8.0) / (1024.0 * 1024.0)) / segundos : 0.0;

        String rol = isSender ? "OUTBOUND SENDER" : "INBOUND RECEIVER";
        String tagModo = rsyncMode ? "MUTADO [RSYNC-TUNNEL]" : "NUEVO [ZERO-COPY]";

        Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
        Logger.logInfo(String.format("│ 🔥 [METRICAS FINALES - %s]                        │", rol));
        Logger.logInfo("├──────────────────────────────────────────────────────────────────┤");
        Logger.logInfo(String.format("│  ├── Estrategia Aplicada:  %s", String.format("%-38s │", tagModo)));
        Logger.logInfo(String.format("│  ├── Tiempo de Ejecución:  %-20s                  │", String.format("%.3f seg", segundos)));
        Logger.logInfo(String.format("│  ├── Tamaño Lógico (File): %-20s                  │", formatSize(totalLogicalSize)));
        Logger.logInfo(String.format("│  ├── Transferido Real Físico: %-20s               │", formatSize(actualWireBytes)));

        if (rsyncMode) {
            Logger.logInfo(String.format("│  ├── Tráfico de Red Ahorrado: %-20s               │", formatSize(bytesAhorrados)));
            Logger.logInfo(String.format("│  └── Eficiencia del Algoritmo: %-20s              │", String.format("%.2f%%", porcentajeEficiencia)));
        } else {
            Logger.logInfo(String.format("│  └── Tasa de Transferencia: %-20s                 │", String.format("%.2f Mbps", throughputMbps)));
        }
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

    public void stop() { running = false; resume(); }
    public void pause() { paused = true; }
    public void resume() { synchronized (pauseLock) { paused = false; pauseLock.notifyAll(); } }
    @Override public void cancel() { stop(); }
}