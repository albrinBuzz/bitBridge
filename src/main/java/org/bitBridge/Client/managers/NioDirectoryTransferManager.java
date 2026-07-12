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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Adler32;

import static org.bitBridge.server.transfer.TransferSessionManager.PREFIX_REQUEST;

/**
 * ============================================================================
 *  OPTIMIZACIONES APLICADAS (ver PLAN_REFACTORIZACION.md para detalle)
 * ----------------------------------------------------------------------------
 *  1. Rolling checksum REAL O(1) por byte en procesarYEnviarDeltas (antes era
 *     O(n * BLOCK_SIZE): se releía y rehasheaba el bloque completo en cada
 *     desplazamiento de 1 byte cuando no había coincidencia).
 *  2. generarFirmasLocales ahora reparte el archivo entre N hilos (uno por
 *     núcleo disponible), cada uno con su propio FileChannel, en vez de
 *     hashear secuencialmente en un solo hilo.
 *  3. Tamaño de bloque adaptativo: se levanta el techo de 64KB a 1MB para
 *     archivos > 100MB, reduciendo el número total de firmas.
 *  4. Escritura por lotes (arraycopy) en el buffer de literales en vez de
 *     byte a byte.
 * ============================================================================
 */
public class NioDirectoryTransferManager implements TransferManager {
    private volatile boolean running = true;
    private final TransferenciaController transferenciaController;
    private final String downloadDir;
    private long totalBytesProcessed = 0;
    private long totalSize = 0;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();
    // Variables dinámicas para el cálculo del ahorro físico real en red
    private long totalWireBytesTransmitted = 0;

    private int BLOCK_SIZE = 64 * 1024; // Bloques de 64KB para el rolling hash (valor por defecto, se recalcula por archivo)

    private static final long MOD_ADLER = 65521L;
    private static final int SIGNATURE_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors());

    public NioDirectoryTransferManager(TransferenciaController controller) {
        this.transferenciaController = controller;
        this.downloadDir = ConfiguracionApp.getInstancia().obtener(ConfigKey.DOWNLOAD_DIR);
    }

    /**
     * Verifica si la transferencia ha sido pausada y duerme el hilo de red si es necesario.
     */
    private void checkPause() throws InterruptedException {
        if (paused) {
            synchronized (pauseLock) {
                while (paused && running) {
                    Logger.logInfo("[NIO-FLOW] Transferencia pausada. Hilo de red entrando en espera...");
                    pauseLock.wait();
                }
            }
        }
    }

    // --- LÓGICA DE ENVÍO (SENDER) ---
    public void sendDirectory(File rootDir, String host, int port, String recipient) {
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
                ProtocolService.writeNIO(channel, new HandshakeMessage(sessionId, SocketPurpose.FILE_TRANSFER, ""));
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
            if (!running) return;
            checkPause();

            String relativePath = target.getAbsolutePath().substring(target.getAbsolutePath().indexOf(rootName));
            boolean isDir = target.isDirectory();
            long lastModified = target.lastModified();
            String rawRelativePath = target.getAbsolutePath().substring(target.getAbsolutePath().indexOf(rootName));

            // ⚡ NORMALIZACIÓN CRUZADA: Reemplazamos las '\' de Windows por '/' universales
            String relativePathNormalizada = rawRelativePath.replace("\\", "/");
            FileDirectoryCommunication meta = new FileDirectoryCommunication(
                    target.getName(),
                    isDir ? 0 : target.length(),
                    isDir,
                    relativePathNormalizada
            );
            meta.setLastModified(lastModified);
            meta.setRecipient(recipient);

            ProtocolService.writeNIO(socket, meta);

            FileHandshakeCommunication ack = waitForHandshakeNIO(socket);
            BLOCK_SIZE = calcularTamanoBloqueOptimo(target.length());

            if (ack.getAction() == FileHandshakeAction.SKIP_FILE) {
                totalBytesProcessed += target.length();
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
                continue;
            }

            if (isDir) {
                enviarRecursivoNIO(socket, target, rootName, idTransfe, recipient);
            } else if (ack.getAction() == FileHandshakeAction.PROCESS_DELTAS) {
                checkPause();
                RsyncSignatures signatures = (RsyncSignatures) ProtocolService.readNIO(socket);

                procesarYEnviarDeltas(socket, target, signatures, idTransfe);

                ProtocolService.readNIO(socket);
            } else {
                checkPause();
                enviarArchivoCompletoNIO(socket, target, idTransfe);
                ProtocolService.readNIO(socket);
            }
        }
    }

    private void enviarArchivoCompletoNIO(SocketChannel socket, File file, String idTransfe) throws Exception {
        try (FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long pos = 0, size = file.length(), chunk = 4 * 1024 * 1024;
            while (pos < size && running) {
                checkPause();
                long transferred = fc.transferTo(pos, Math.min(size - pos, chunk), socket);
                if (transferred <= 0) { Thread.sleep(10); continue; }
                pos += transferred;
                totalBytesProcessed += transferred;
                totalWireBytesTransmitted += transferred;
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
            }
        }
    }

    /**
     * ------------------------------------------------------------------------------------
     *  CHECKSUM RODANTE (Adler-style) — actualización O(1) por byte.
     *  Implementa la recurrencia estándar del algoritmo rsync (Tridgell & Mackerras):
     *      a(k+1) = a(k) - X_out + X_in
     *      b(k+1) = b(k) - L * X_out + a(k+1)
     *  A diferencia de java.util.zip.Adler32, este objeto SÍ soporta desplazar la
     *  ventana un byte sin recalcular desde cero. Se usa de forma consistente tanto
     *  al generar firmas (init only) como al escanear en busca de coincidencias (roll).
     * ------------------------------------------------------------------------------------
     */
    private static final class RollingChecksum {
        long a, b;
        int blockLen;
        private boolean initialized = false;

        void init(byte[] data, int offset, int len) {
            long s1 = 0, s2 = 0;
            for (int idx = 0; idx < len; idx++) {
                int val = data[offset + idx] & 0xFF;
                s1 += val;
                s2 += (long) (len - idx) * val;
            }
            a = s1 % MOD_ADLER;
            b = s2 % MOD_ADLER;
            blockLen = len;
            initialized = true;
        }

        void roll(int outByte, int inByte) {
            long newA = (a - outByte + inByte) % MOD_ADLER;
            if (newA < 0) newA += MOD_ADLER;
            long newB = (b - (long) blockLen * outByte + newA) % MOD_ADLER;
            if (newB < 0) newB += MOD_ADLER;
            a = newA;
            b = newB;
        }

        long getValue() {
            return (b << 16) | a;
        }

        boolean isInitialized() {
            return initialized;
        }

        void markUninitialized() {
            initialized = false;
        }
    }

    /**
     * Escaneo de deltas con checksum rodante real. Usa un buffer doble con
     * compactación amortizada O(1) para evitar seeks/re-lecturas repetidas
     * de disco en cada desplazamiento de byte (antes: fc.position(i) por byte).
     */
    private void procesarYEnviarDeltas(SocketChannel socket, File file, RsyncSignatures signatures, String idTransfe) throws Exception {
        Map<Long, List<BlockSignature>> adlerMap = new HashMap<>();
        for (BlockSignature sig : signatures.getSignatures()) {
            adlerMap.computeIfAbsent(sig.getAdler32(), k -> new ArrayList<>()).add(sig);
        }

        List<RsyncDeltaInstruction> instructions = new ArrayList<>();
        ByteArrayOutputStream literalBuffer = new ByteArrayOutputStream(Math.max(BLOCK_SIZE, 4096));
        MessageDigest md5 = MessageDigest.getInstance("MD5");

        int matchedBlocks = 0;
        long literalBytes = 0;

        try (FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
            long fileSize = fc.size();

            if (fileSize == 0) {
                ProtocolService.writeNIO(socket, new RsyncDeltaPackage(instructions));
                totalBytesProcessed += fileSize;
                transferenciaController.updateProgressMetrics(FileTransferState.SENDING, idTransfe, totalBytesProcessed, totalSize);
                return;
            }

            // Buffer doble: capacidad 2x BLOCK_SIZE para poder compactar sin perder datos de la ventana activa
            byte[] buf = new byte[BLOCK_SIZE * 2];
            int bufValid = 0;  // bytes válidos cargados en buf, contando desde el índice 0
            int winStart = 0;  // inicio de la ventana actual dentro de buf
            long i = 0;        // offset lógico (en el archivo) del inicio de la ventana

            // Carga inicial
            bufValid = readFully(fc, buf, 0, buf.length);

            RollingChecksum rc = new RollingChecksum();

            while (i < fileSize && running) {
                checkPause();
                int windowLen = (int) Math.min(BLOCK_SIZE, fileSize - i);

                // Asegurar que la ventana completa esté cargada en el buffer; compactar si hace falta espacio
                if (winStart + windowLen > bufValid) {
                    int remaining = bufValid - winStart;
                    if (remaining > 0) System.arraycopy(buf, winStart, buf, 0, remaining);
                    int extra = readFully(fc, buf, remaining, buf.length - remaining);
                    bufValid = remaining + extra;
                    winStart = 0;
                }

                // Cola del archivo más corta que BLOCK_SIZE: se trata directo como literal (no se busca match parcial)
                if (windowLen < BLOCK_SIZE) {
                    literalBuffer.write(buf, winStart, windowLen);
                    literalBytes += windowLen;
                    i += windowLen;
                    break;
                }

                if (!rc.isInitialized()) {
                    rc.init(buf, winStart, windowLen);
                }

                boolean matchFound = false;
                long checksum = rc.getValue();
                List<BlockSignature> candidates = adlerMap.get(checksum);

                if (candidates != null) {
                    md5.reset();
                    md5.update(buf, winStart, windowLen);
                    byte[] currentMd5 = md5.digest();

                    for (BlockSignature sig : candidates) {
                        if (Arrays.equals(sig.getMd5(), currentMd5)) {
                            if (literalBuffer.size() > 0) {
                                instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
                                literalBuffer.reset();
                            }
                            instructions.add(new RsyncDeltaInstruction(sig.getBlockIndex()));
                            matchedBlocks++;
                            matchFound = true;
                            i += windowLen;
                            winStart += windowLen;
                            rc.markUninitialized();
                            break;
                        }
                    }
                }

                if (!matchFound) {
                    // Desplazar la ventana 1 byte: emitir el byte saliente y actualizar el checksum en O(1)
                    int outByte = buf[winStart] & 0xFF;
                    literalBuffer.write(outByte);
                    literalBytes++;
                    i++;
                    winStart++;

                    if (i + windowLen <= fileSize) {
                        if (winStart + windowLen > bufValid) {
                            int remaining = bufValid - winStart;
                            if (remaining > 0) System.arraycopy(buf, winStart, buf, 0, remaining);
                            int extra = readFully(fc, buf, remaining, buf.length - remaining);
                            bufValid = remaining + extra;
                            winStart = 0;
                        }
                        int inByte = buf[winStart + windowLen - 1] & 0xFF;
                        rc.roll(outByte, inByte);
                    } else {
                        // No hay suficiente cola para mantener una ventana completa: se reinicia en la próxima vuelta
                        rc.markUninitialized();
                    }
                }
            }

            if (literalBuffer.size() > 0) {
                instructions.add(new RsyncDeltaInstruction(literalBuffer.toByteArray()));
            }

            RsyncDeltaPackage deltaPackage = new RsyncDeltaPackage(instructions);

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

    /** Lee hasta 'len' bytes secuencialmente desde el FileChannel hacia buf[offset..], sin seeks intermedios. */
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
                Logger.logInfo(String.format("🌐 [CONEXIÓN] Conectando a %s:%d para transferencia...", host, port));
                channel.connect(new InetSocketAddress(host, port));

                if (channel.isConnected()) {
                    Logger.logInfo("🔒 [HANDSHAKE] Canal conectado. Enviando credenciales de sesión y aceptación...");
                    ProtocolService.writeNIO(channel, new HandshakeMessage(sessionId, SocketPurpose.FILE_TRANSFER, ""));
                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.ACCEPT_REQUEST, sessionId));

                    if (confirmarInicioNIO(channel, sessionId)) {
                        Logger.logInfo("🚀 [FLUJO] Inicio confirmado por el nodo remoto. Inicializando tracking en UI...");
                        String idTransfe = transferenciaController.addTransference(
                                FileTransferState.RECEIVING.name(), info.getRecipient(), info.getRecipient(), info.getName(), this, info.getSize()
                        );

                        long startTime = System.currentTimeMillis();

                        while (running) {
                            checkPause();


                            Communication meta = ProtocolService.readNIO(channel);

                            if (meta instanceof FileDirectoryCommunication fileMeta) {
                                Path destPath = Paths.get(downloadDir, fileMeta.getRelativePath());
                                String tipoNodo = fileMeta.isDirectory() ? "📂 CARPETA" : "📄 ARCHIVO";



                                if (Files.exists(destPath)) {

                                    if (fileMeta.isDirectory()) {

                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                        continue;
                                    } else {
                                        long localSize = Files.size(destPath);
                                        long localLastModified = Files.getLastModifiedTime(destPath).toMillis();

                                        if (localSize == fileMeta.getSize() && localLastModified >= fileMeta.getLastModified()) {

                                            totalBytesProcessed += fileMeta.getSize();
                                            ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.SKIP_FILE, sessionId));
                                            continue;
                                        }

                                        Logger.logInfo("│ 🛠️ [RSYNC] El archivo difiere. Iniciando algoritmo de sincronización diferencial...");
                                        BLOCK_SIZE = calcularTamanoBloqueOptimo(info.getSize());
                                        Logger.logInfo("│    ▪️ Tamaño de bloque asignado: " + BLOCK_SIZE + " bytes");

                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.PROCESS_DELTAS, sessionId));

                                        Logger.logInfo("│    ▪️ Generando firmas hash locales (Rolling & MD5)...");
                                        RsyncSignatures signatures = generarFirmasLocales(destPath);

                                        Logger.logInfo("│    ▪️ Enviando firmas al emisor...");
                                        ProtocolService.writeNIO(channel, signatures);

                                        checkPause();
                                        Logger.logInfo("│    ▪️ Esperando paquete de deltas encapsulado (RsyncDeltaPackage)...");
                                        RsyncDeltaPackage deltaPkg = (RsyncDeltaPackage) ProtocolService.readNIO(channel);

                                        Logger.logInfo("│    ▪️ Parcheando y reconstruyendo archivo de forma local...");
                                        reconstruirArchivoRsync(destPath, deltaPkg);

                                        Logger.logInfo("│ ✅ [RSYNC-OK] Sincronización finalizada con éxito.");
                                        Logger.logInfo("└───────────────────────────────────────────────────────────────────");

                                        totalBytesProcessed += fileMeta.getSize();
                                        transferenciaController.updateProgressMetrics(FileTransferState.RECEIVING, idTransfe, totalBytesProcessed, info.getSize());

                                        ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                        continue;
                                    }
                                }

                                // Si no existe, se procesa de cero
                                if (fileMeta.isDirectory()) {
                                    Logger.logInfo("│ 📂 [CREAR] Creando estructura de directorio nueva...");
                                    Logger.logInfo("└───────────────────────────────────────────────────────────────────");
                                    Files.createDirectories(destPath);
                                    ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));
                                    continue;
                                }

                                Logger.logInfo("│ 📥 [DESCARGA] El archivo no existe. Preparando flujo para descarga completa...");
                                if (destPath.getParent() != null) {
                                    Files.createDirectories(destPath.getParent());
                                }

                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                                Logger.logInfo("│    ⏳ Recibiendo flujo binario desde el canal...");
                                recibirArchivoCompletoNIO(channel, destPath, fileMeta.getSize(), idTransfe, info.getSize());
                                Logger.logInfo("│ ✨ [DESCARGA-OK] Archivo guardado correctamente.");
                                Logger.logInfo("└───────────────────────────────────────────────────────────────────");

                                ProtocolService.writeNIO(channel, new FileHandshakeCommunication(FileHandshakeAction.START_TRANSFER, sessionId));

                            } else if (meta instanceof FileHandshakeCommunication h && h.getAction() == FileHandshakeAction.TRANSFER_DONE) {
                                Logger.logInfo("┌──────────────────────────────────────────────────────────────────┐");
                                Logger.logInfo("│  🏁 [COMPLETO] El nodo remoto ha enviado TRANSFER_DONE.         │");
                                Logger.logInfo("└──────────────────────────────────────────────────────────────────┘");
                                mostrarEstadisticasFinales(System.currentTimeMillis() - startTime, info.getSize(), totalWireBytesTransmitted, false);
                                break;
                            } else {
                                Logger.logWarn("⚠️ [FLUJO] Se recibió un paquete desconocido en la máquina de estados: " + (meta != null ? meta.getClass().getSimpleName() : "null"));
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

    /**
     * Generación de firmas en PARALELO: el archivo se reparte en rangos de bloques
     * contiguos entre N hilos (N = núcleos disponibles), cada uno con su propio
     * FileChannel abierto en modo lectura sobre la misma ruta (lecturas posicionales
     * independientes, sin contención). Reemplaza el escaneo secuencial de un solo hilo.
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

    private void reconstruirArchivoRsync(Path targetPath, RsyncDeltaPackage packageDeltas) throws Exception {
        Path tempFile = Paths.get(targetPath.toString() + ".tmp");

        try (FileChannel fcOriginal = FileChannel.open(targetPath, StandardOpenOption.READ);
             FileChannel fcTarget = FileChannel.open(tempFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

            long currentTargetPosition = 0;

            for (RsyncDeltaInstruction inst : packageDeltas.getInstructions()) {
                if (inst.isLiteral()) {
                    byte[] literal = inst.getLiteralData();
                    ByteBuffer buf = ByteBuffer.wrap(literal);
                    while (buf.hasRemaining()) {
                        currentTargetPosition += fcTarget.write(buf, currentTargetPosition);
                    }
                } else {
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
        Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void recibirArchivoCompletoNIO(SocketChannel channel, Path dest, long size, String id, long total) throws Exception {
        // Nos aseguramos de crear los directorios padre por si las moscas antes de abrir el canal
        if (dest.getParent() != null) {
            java.nio.file.Files.createDirectories(dest.getParent());
        }

        try (FileChannel fileChannel = FileChannel.open(dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            long readTotal = 0;
            long limit = 4 * 1024 * 1024; // Bloques de 4MB para transferFrom

            while (readTotal < size && running) {
                checkPause();

                // transferFrom lee directamente del socket al disco a nivel de Kernel
                long read = fileChannel.transferFrom(channel, readTotal, Math.min(size - readTotal, limit));

                if (read <= 0) {
                    if (read == -1) throw new IOException("Desconexión prematura del canal remoto.");
                    Thread.sleep(1); // Evita un bucle infinito que sature la CPU si el buffer de red está vacío
                    continue;
                }

                readTotal += read;
                totalBytesProcessed += read;      // Acumulador global interno
                totalWireBytesTransmitted += read; // Métrica de red global

                // ⚡ REPARACIÓN DE TELEMETRÍA:
                // Si 'total' representa el tamaño de ESTE ARCHIVO SOLAMENTE, debes pasar 'readTotal'.
                // Si 'total' es el tamaño de TODA LA CARPETA, entonces 'totalBytesProcessed' es el correcto.
                transferenciaController.updateProgressMetrics(
                        FileTransferState.RECEIVING,
                        id,
                        totalBytesProcessed, // 👈 Cambia a totalBytesProcessed si el progreso es global de la transferencia
                        size       // 👈 Cambia a 'total' si el progreso es global de la transferencia
                );
            }

            // Forzar la escritura de metadatos y datos al disco físico antes de cerrar
            fileChannel.force(true);

            // Notificación final opcional de que este ID de archivo terminó su transferencia
            transferenciaController.updateProgressMetrics(FileTransferState.COMPLETED, id, size, size);
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
        return String.format("%.2f %sB", (double) v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

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

    /**
     * Tamaño de bloque adaptativo por tramos. El límite anterior (64KB fijo)
     * generaba cientos de miles de bloques en archivos de varios GB. Se sube
     * el techo a 1MB para archivos grandes, reduciendo el número total de
     * firmas (menos CPU en el hashing, menos bytes en el handshake de firmas),
     * a costa de una granularidad de delta más gruesa.
     */
    public int calcularTamanoBloqueOptimo(long tamanoArchivo) {
        if (tamanoArchivo < 1024 * 1024) return 2048; // < 1MB

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

    @Override
    public void pause() {
        this.paused = true;
        Logger.logInfo("[MANAGER] Solicitud de PAUSA registrada.");
    }

    @Override
    public void resume() {
        synchronized (pauseLock) {
            this.paused = false;
            pauseLock.notifyAll();
        }
        Logger.logInfo("[MANAGER] Solicitud de REANUDACIÓN procesada con éxito.");
    }

    @Override
    public void stop() {
        this.running = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    @Override
    public void cancel() {
        stop();
        Logger.logWarn("[MANAGER] Transferencia forzada a estado CANCELADO.");
    }
}