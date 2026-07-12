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
import java.util.concurrent.*;
import java.util.zip.Adler32;

import static org.bitBridge.server.transfer.TransferSessionManager.PREFIX_REQUEST;

/**
 * ============================================================================
 *  OPTIMIZACIONES APLICADAS (ver PLAN_REFACTORIZACION.md para detalle)
 * ----------------------------------------------------------------------------
 *  1. calcularDeltasLocales: la FÓRMULA del rolling checksum ya era correcta
 *     en el original (recurrencia O(1) de Adler32 estándar). El problema era
 *     la E/S: por cada byte sin match se hacían 2 lecturas de disco
 *     (fileChannel.read posicional) + asignación de un ByteBuffer nuevo.
 *     Ahora se lee el archivo en bloques grandes hacia un buffer en memoria
 *     (con compactación amortizada O(1)) y el byte saliente/entrante se lee
 *     directo de ese buffer — cero syscalls por byte. La matemática del
 *     checksum NO se tocó, se preservó exactamente para no arriesgar
 *     regresiones de corrección.
 *  2. El chequeo MD5 en un hit de Adler32 y el recálculo del checksum tras un
 *     match también leen del buffer en memoria en vez de volver a golpear
 *     disco.
 *  3. generarFirmasLocales: paralelizado entre N hilos (uno por núcleo),
 *     cada uno con su propio FileChannel sobre una región distinta del
 *     archivo — igual que en la versión de directorio.
 *  4. reconstruirArchivoRsync: los bloques reutilizados (match) ahora se
 *     copian con FileChannel.transferTo() (zero-copy a nivel de kernel) en
 *     vez de pasar por un buffer intermedio en el heap de Java.
 * ============================================================================
 */
public class FileTransferManager implements TransferManager {
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();

    private final TransferenciaController transferenciaController;
    private final String downloadDir;

    private int BLOCK_SIZE = 64 * 1024;
    private int TRANSFER_CHUNK_SIZE = 4 * 1024 * 1024;

    private static final int MOD_ADLER = 65521;
    private static final int SIGNATURE_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors());

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
        BLOCK_SIZE = calcularTamanoBloqueOptimo(totalSize);
        Logger.logInfo("Tamaño del bloque: " + formatSize(BLOCK_SIZE));

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

                ProtocolService.writeNIO(channel, new HandshakeMessage(sessionId, SocketPurpose.FILE_TRANSFER, ""));

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
                    } else if (ack.getAction() == FileHandshakeAction.PROCESS_DELTAS) {
                        rsyncMode = true;
                        Logger.logWarn("┌──────────────────────────────────────────────────────────────────┐");
                        Logger.logWarn("│  ⚡ [MODO RSYNC] Archivo modificado detectado. Iniciando Delta.  │");
                        Logger.logWarn("└──────────────────────────────────────────────────────────────────┘");

                        Logger.logInfo("[NIO-READ] Descargando mapa de firmas Adler32/MD5 del receptor remoto...");
                        RsyncSignatures signatures = (RsyncSignatures) ProtocolService.readNIO(channel);

                        Logger.logInfo(String.format("[RSYNC-CORE] Procesando %d firmas con algoritmo de ventana deslizante...", signatures.getSignatures().size()));

                        RsyncDeltaPackage deltaPackage = calcularDeltasLocales(targetFile, signatures);

                        bytesEnviadosRed = deltaPackage.getInstructions().stream()
                                .mapToLong(inst -> inst.isLiteral() ? inst.getLiteralData().length : 4)
                                .sum();

                        Logger.logInfo("[NIO-WRITE] Despachando paquete de deltas optimizado hacia la red...: " + formatSize(bytesEnviadosRed));

                        ProtocolService.writeNIO(channel, deltaPackage);
                        transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalSize, totalSize);

                        Logger.logInfo("[NIO-READ] Esperando ACK final de sincronización del Server Relay...");
                        ProtocolService.readNIO(channel);
                    } else {
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

    /**
     * Lee hasta 'len' bytes secuencialmente desde el FileChannel hacia buf[offset..],
     * agrupando varias lecturas del kernel en una sola pasada de buffer en memoria
     * (en vez de una syscall posicional por cada byte, como hacía el código original).
     */
    private int readFully(FileChannel fc, byte[] buf, int offset, int len) throws IOException {
        if (len <= 0) return 0;
        ByteBuffer bb = ByteBuffer.wrap(buf, offset, len);
        int total = 0;
        while (bb.hasRemaining()) {
            int r = fc.read(bb);
            if (r == -1) break;
            total += r;
        }
        return total;
    }

    private RsyncDeltaPackage calcularDeltasLocales(File file, RsyncSignatures remoteSignatures) throws Exception {
        long startTime = System.currentTimeMillis();
        Logger.logInfo(String.format("[RSYNC-DIAG] 🔍 Iniciando análisis local para: %s (%s)", file.getName(), formatSize(file.length())));

        long totalFileSize = file.length();
        int expectedBlocks = remoteSignatures.getSignatures().size();
        Logger.logInfo("[RSYNC-DIAG] 📥 Firmas remotas recibidas: " + expectedBlocks + " bloques.");

        Map<Long, List<BlockSignature>> adlerMap = new HashMap<>((int) (expectedBlocks / 0.75f) + 1);
        for (BlockSignature sig : remoteSignatures.getSignatures()) {
            adlerMap.computeIfAbsent(sig.getAdler32(), k -> new ArrayList<>(2)).add(sig);
        }

        List<RsyncDeltaInstruction> instructions = new ArrayList<>();
        ByteArrayOutputStream literalBuffer = new ByteArrayOutputStream(Math.max(BLOCK_SIZE, 4096));
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        int matchedBlocks = 0;
        long literalBytesCount = 0;
        int adlerCollisions = 0;
        int totalAdlerHits = 0;
        long totalRollingSteps = 0;
        int ultimoProgresoReportado = -1;

        try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {

            if (totalFileSize == 0) {
                return new RsyncDeltaPackage(instructions);
            }

            if (totalFileSize < BLOCK_SIZE) {
                // Archivo más chico que un bloque: no hay ventana que evaluar, todo es literal.
                byte[] data = new byte[(int) totalFileSize];
                readFully(fileChannel, data, 0, data.length);
                instructions.add(new RsyncDeltaInstruction(data));
                literalBytesCount = totalFileSize;
            } else {
                // Buffer doble (2x BLOCK_SIZE) con compactación amortizada O(1): evita
                // los seeks/reads posicionales de 1 byte que hacía la versión original.
                byte[] buf = new byte[BLOCK_SIZE * 2];
                int bufValid = readFully(fileChannel, buf, 0, buf.length);
                int winStart = 0;
                long i = 0; // offset lógico (en el archivo) del inicio de la ventana

                int s1 = 0, s2 = 0;
                boolean windowInit = false;

                while (i <= totalFileSize - BLOCK_SIZE) {

                    // Asegurar que la ventana completa esté cargada en memoria
                    if (winStart + BLOCK_SIZE > bufValid) {
                        int remaining = bufValid - winStart;
                        if (remaining > 0) System.arraycopy(buf, winStart, buf, 0, remaining);
                        int extra = readFully(fileChannel, buf, remaining, buf.length - remaining);
                        bufValid = remaining + extra;
                        winStart = 0;
                    }

                    if (!windowInit) {
                        s1 = 1;
                        s2 = 0;
                        for (int j = 0; j < BLOCK_SIZE; j++) {
                            int v = buf[winStart + j] & 0xFF;
                            s1 = (s1 + v) % MOD_ADLER;
                            s2 = (s2 + s1) % MOD_ADLER;
                        }
                        windowInit = true;
                    }

                    long currentAdler = ((long) s2 << 16) | s1;

                    int progresoActual = (int) (((double) i / totalFileSize) * 100);
                    if (progresoActual % 10 == 0 && progresoActual != ultimoProgresoReportado) {
                        Logger.logInfo(String.format("   ↳ [PROGRESO %d%%] Puntero i: %d/%d bytes. Ints. acumuladas: %d | Matches: %d | Literales: %s",
                                progresoActual, i, totalFileSize, instructions.size(), matchedBlocks, formatSize(literalBytesCount)));
                        ultimoProgresoReportado = progresoActual;
                    }

                    boolean matchFound = false;
                    List<BlockSignature> candidates = adlerMap.get(currentAdler);

                    if (candidates != null) {
                        totalAdlerHits++;

                        // MD5 directo desde el buffer en memoria (antes: releía el bloque del disco)
                        md5.reset();
                        md5.update(buf, winStart, BLOCK_SIZE);
                        byte[] currentMd5 = md5.digest();

                        boolean cryptographicMatch = false;
                        for (BlockSignature sig : candidates) {
                            if (Arrays.equals(sig.getMd5(), currentMd5)) {
                                if (literalBuffer.size() > 0) {
                                    instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
                                    literalBuffer.reset();
                                }
                                instructions.add(new RsyncDeltaInstruction(sig.getBlockIndex()));

                                i += BLOCK_SIZE;
                                winStart += BLOCK_SIZE;
                                matchFound = true;
                                cryptographicMatch = true;
                                matchedBlocks++;
                                windowInit = false; // el próximo bloque necesita checksum inicial fresco
                                break;
                            }
                        }
                        if (!cryptographicMatch) adlerCollisions++;
                    }

                    if (!matchFound) {
                        totalRollingSteps++;

                        // Desplazar la ventana 1 byte usando datos YA cargados en memoria
                        int byteSaliente = buf[winStart] & 0xFF;
                        literalBuffer.write(byteSaliente);
                        literalBytesCount++;
                        i++;
                        winStart++;

                        if (i <= totalFileSize - BLOCK_SIZE) {
                            if (winStart + BLOCK_SIZE > bufValid) {
                                int remaining = bufValid - winStart;
                                if (remaining > 0) System.arraycopy(buf, winStart, buf, 0, remaining);
                                int extra = readFully(fileChannel, buf, remaining, buf.length - remaining);
                                bufValid = remaining + extra;
                                winStart = 0;
                            }
                            int byteEntrante = buf[winStart + BLOCK_SIZE - 1] & 0xFF;

                            // --- Misma fórmula matemática que el código original (sin modificar) ---
                            s1 = (s1 - byteSaliente + byteEntrante) % MOD_ADLER;
                            if (s1 < 0) s1 += MOD_ADLER;

                            s2 = (s2 - (BLOCK_SIZE * byteSaliente) + s1 - 1) % MOD_ADLER;
                            if (s2 < 0) s2 += MOD_ADLER;
                        } else {
                            windowInit = false;
                        }
                    }
                }

                // Remanente final (< BLOCK_SIZE): parte ya está en memoria, el resto se lee del disco
                long literalStart = i;
                long finalLiteralLength = totalFileSize - literalStart;
                if (finalLiteralLength > 0) {
                    int inMemory = bufValid - winStart;
                    if (inMemory > 0) {
                        literalBuffer.write(buf, winStart, inMemory);
                    }
                    long remainingToRead = finalLiteralLength - inMemory;
                    if (remainingToRead > 0) {
                        byte[] tail = new byte[(int) remainingToRead];
                        ByteBuffer tb = ByteBuffer.wrap(tail);
                        long pos = literalStart + inMemory;
                        while (tb.hasRemaining()) {
                            int r = fileChannel.read(tb, pos);
                            if (r == -1) break;
                            pos += r;
                        }
                        literalBuffer.write(tail);
                    }
                    literalBytesCount += finalLiteralLength;
                }
            }

            if (literalBuffer.size() > 0) {
                instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
        Logger.logInfo("│ 🛠️  [INFORME DE REDUNDANCIA RSYNC - BITBRIDGE]                   │");
        Logger.logInfo("├──────────────────────────────────────────────────────────────────┤");
        Logger.logInfo("Archivo "+file.getName());
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
                if (!running) throw new IOException("Transferencia abortada por el usuario.");

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
        BLOCK_SIZE = calcularTamanoBloqueOptimo(info.getSize());

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
                                    transferenciaController.updateProgressMetrics(
                                            FileTransferState.RECEIVING,
                                            idTransfe,
                                            localSize,
                                            fileMeta.getSize()
                                    );

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

                                /*ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ObjectOutputStream oos = new ObjectOutputStream(baos);
                                oos.writeObject(deltaPkg);
                                oos.flush();
                                bytesRecibidosRed = baos.size();*/

                                Logger.logInfo("[DISK-IO] Reconstruyendo archivo binario local aplicando deltas literales...");
                                reconstruirArchivoRsync(destPath, deltaPkg, idTransfe, info.getSize());

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

    /**
     * Generación de firmas en PARALELO: el archivo se reparte entre N hilos
     * (N = núcleos disponibles), cada uno con su propio FileChannel abierto
     * sobre un rango de bloques distinto, sin contención entre ellos.
     * Reemplaza el escaneo secuencial de un solo hilo del código original.
     */
    private RsyncSignatures generarFirmasLocales(Path path) throws Exception {
        long totalBytes = Files.size(path);
        if (totalBytes == 0) {
            return new RsyncSignatures(new ArrayList<>());
        }

        int numBlocks = (int) ((totalBytes + BLOCK_SIZE - 1) / BLOCK_SIZE);
        BlockSignature[] results = new BlockSignature[numBlocks];

        int threads = Math.min(SIGNATURE_THREADS, Math.max(1, numBlocks));
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            int blocksPerWorker = (int) Math.ceil((double) numBlocks / threads);
            List<Future<?>> futures = new ArrayList<>();

            for (int w = 0; w < threads; w++) {
                final int startBlock = w * blocksPerWorker;
                final int endBlock = Math.min(numBlocks, startBlock + blocksPerWorker);
                if (startBlock >= endBlock) continue;

                futures.add(pool.submit(() -> {
                    try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
                        Adler32 adler = new Adler32();
                        MessageDigest md5 = MessageDigest.getInstance("MD5");
                        byte[] buffer = new byte[BLOCK_SIZE];

                        for (int b = startBlock; b < endBlock; b++) {
                            long offset = (long) b * BLOCK_SIZE;
                            int len = (int) Math.min(BLOCK_SIZE, totalBytes - offset);

                            ByteBuffer bb = ByteBuffer.wrap(buffer, 0, len);
                            long pos = offset;
                            while (bb.hasRemaining()) {
                                int r = fc.read(bb, pos);
                                if (r == -1) break;
                                pos += r;
                            }

                            adler.reset();
                            adler.update(buffer, 0, len);

                            md5.reset();
                            md5.update(buffer, 0, len);

                            results[b] = new BlockSignature(b, adler.getValue(), md5.digest());
                        }
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }));
            }

            for (Future<?> f : futures) f.get();
        } finally {
            pool.shutdown();
        }

        return new RsyncSignatures(Arrays.asList(results));
    }

    private void reconstruirArchivoRsync(Path targetPath, RsyncDeltaPackage packageDeltas, String idTrans, long expectedTotalSize) throws Exception {
        Path tempFile = Paths.get(targetPath.toString() + ".tmp");
        long bytesProcesados = 0;

        long originalLength = Files.size(targetPath);

        try (FileChannel srcChannel = FileChannel.open(targetPath, StandardOpenOption.READ);
             FileChannel destChannel = FileChannel.open(tempFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

            long currentTargetPosition = 0;

            for (RsyncDeltaInstruction inst : packageDeltas.getInstructions()) {

                checkPaused();
                if (!running) throw new IOException("Reconstrucción delta cancelada de forma atómica.");

                if (inst.isLiteral()) {
                    byte[] literalData = inst.getLiteralData();
                    ByteBuffer literalBuffer = ByteBuffer.wrap(literalData);

                    while (literalBuffer.hasRemaining()) {
                        currentTargetPosition += destChannel.write(literalBuffer, currentTargetPosition);
                    }
                    bytesProcesados += literalData.length;
                } else {
                    // Bloque reutilizado: copia zero-copy a nivel de kernel entre los
                    // dos FileChannel, sin pasar por un buffer intermedio en el heap.
                    int blockIdx = inst.getBlockIndex();
                    long startOffset = (long) blockIdx * BLOCK_SIZE;
                    long length = Math.min((long) BLOCK_SIZE, originalLength - startOffset);

                    long written = 0;
                    while (written < length) {
                        long transferred = srcChannel.transferTo(startOffset + written, length - written, destChannel);
                        if (transferred <= 0) break;
                        destChannel.position(currentTargetPosition + transferred);
                        written += transferred;
                        currentTargetPosition += transferred;
                    }
                    bytesProcesados += length;
                }

                transferenciaController.updateProgressMetrics(
                        FileTransferState.RECEIVING,
                        idTrans,
                        bytesProcesados,
                        expectedTotalSize
                );


            }

            destChannel.force(true);
        }

        Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void recibirArchivoCompletoNIO(SocketChannel channel, Path dest, long size, String idTrans) throws Exception {
        try (FileChannel fileChannel = FileChannel.open(dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            long readTotal = 0;
            while (readTotal < size) {
                checkPaused();
                if (!running) throw new IOException("Descarga abortada por el usuario.");

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
        if (paused) {
            synchronized (pauseLock) {
                while (paused && running) {
                    Logger.logInfo("💤 [NIO-THREAD] Hilo de transferencia durmiendo (Pausado)...");
                    pauseLock.wait();
                }
            }
        }
    }

    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.2f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    private void mostrarEstadisticasFinales(long millis, long totalLogicalSize, long actualWireBytes, boolean rsyncMode, boolean isSender) {
        double segundos = millis / 1000.0;
        long bytesAhorrados = totalLogicalSize - actualWireBytes;
        if (bytesAhorrados < 0) bytesAhorrados = 0;

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

    /**
     * Tamaño de bloque adaptativo por tramos (igual que en la versión de
     * directorio): el techo fijo de 64KB generaba demasiados bloques para
     * archivos de varios GB. Se sube a 1MB para archivos > 100MB.
     */
    public int calcularTamanoBloqueOptimo(long tamanoArchivo) {
        if (tamanoArchivo < 1024 * 1024) return 2048; // 2KB para archivos < 1MB

        if (tamanoArchivo < 100L * 1024 * 1024) { // 1MB - 100MB
            int calculado = (int) Math.sqrt(tamanoArchivo);
            int bloque = Integer.highestOneBit(calculado);
            return Math.max(4096, Math.min(bloque, 64 * 1024));
        }

        // Archivos grandes (> 100MB): bloques más grandes, hasta 1MB
        int calculado = (int) Math.sqrt(tamanoArchivo);
        int bloque = Integer.highestOneBit(calculado);
        return Math.max(64 * 1024, Math.min(bloque, 1024 * 1024));
    }

    public void stop() {
        this.paused = true;
        Logger.logWarn("⏸️ [TRANSFER-CONTROL] Solicitud de PAUSA activada.");
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        synchronized (pauseLock) {
            this.paused = false;
            pauseLock.notifyAll();
        }
        Logger.logInfo("▶️ [TRANSFER-CONTROL] Solicitud de REANUDACIÓN activada.");
    }

    @Override
    public void cancel() {
        this.running = false;
        this.paused = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        Logger.logError("❌ [TRANSFER-CONTROL] Solicitud de CANCELACIÓN activada.");
    }
}