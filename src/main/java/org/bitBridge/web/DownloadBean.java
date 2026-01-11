package org.bitBridge.web;


import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.PhaseId;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import org.bitBridge.server.ConfiguracionServidor;
import org.bitBridge.shared.Logger;
import org.primefaces.PrimeFaces;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Named
@ViewScoped
public class DownloadBean implements Serializable {

    private String uploadPath = "/home/cris/Descargas";


    private List<File> serverFiles;
    private ConfiguracionServidor config;
    private File currentDirectory;
    private File rootDirectory;

    private List<File> breadcrumb = new ArrayList<>();

    private Deque<File> backStack = new ArrayDeque<>();
    private Deque<File> forwardStack = new ArrayDeque<>();

    @PostConstruct
    public void init() throws IOException {
        config = ConfiguracionServidor.getInstancia();
        uploadPath = config.obtener("cliente.directorio_descargas");

        // Validación de seguridad para la ruta
        File dirBase = new File(uploadPath);
        if (!dirBase.exists()) {
            dirBase.mkdirs();
        }

        rootDirectory = dirBase;
        currentDirectory = dirBase;
        Logger.logInfo("Iniciando explorador en: " + uploadPath);

        loadFiles();

        // PASO 1: Construimos los datos del breadcrumb (sin intentar actualizar la UI)
        actualizarDatosBreadcrumb();
    }

    /**
     * Este método solo gestiona la LISTA de archivos del breadcrumb.
     * Es SEGURO llamarlo desde @PostConstruct.
     */
    private void actualizarDatosBreadcrumb() {
        breadcrumb.clear();
        File temp = currentDirectory;

        while (temp != null && !temp.getAbsolutePath().equals(rootDirectory.getAbsolutePath())) {
            breadcrumb.add(0, temp);
            temp = temp.getParentFile();
        }
        breadcrumb.add(0, rootDirectory);
    }

    private void archivosTotales(File archivo, AtomicInteger totalArchivos) throws IOException {
        File[] files = archivo.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    totalArchivos.incrementAndGet();
                    Logger.logInfo(file.getName());
                } else if (file.isDirectory()) {
                    Logger.logInfo(file.getAbsolutePath()+"-->");
                    archivosTotales(file, totalArchivos);
                }
            }
        }
    }

    // ===========================
    // Cargar lista de archivos
    // ===========================
    private void loadServerFiles() {
        serverFiles = new ArrayList<>();
        File dir = new File(uploadPath);

        if (dir.exists() && dir.isDirectory()) {
            serverFiles.addAll(Arrays.asList(Objects.requireNonNull(dir.listFiles())));
        }
    }

    // ===========================
    // Archivo de ejemplo fijo
    // ===========================
    public StreamedContent getFileExample() throws FileNotFoundException {
        String filePath = uploadPath + "ejemplo.pdf";
        File file = new File(filePath);

        InputStream stream = new FileInputStream(file);

        return DefaultStreamedContent.builder()
                .name(file.getName())
                .contentType("application/pdf")
                .stream(() -> stream)
                .build();
    }

    // ===========================
    // Descargar archivo dinámico
    // ===========================
    public StreamedContent downloadFile(File file) throws FileNotFoundException {

        //File file = new File(uploadPath +"/"+ fileName);

        InputStream stream = new FileInputStream(file);

        String contentType = guessContentType(file);

        return DefaultStreamedContent.builder()
                .name(file.getName())
                .contentType(contentType)
                .stream(() -> stream)
                .build();
    }

    public File createZipFromDirectory(File directory) throws IOException {
        // Nombre del archivo zip
        String zipFileName = directory.getName() + ".zip";
        File zipFile = new File(directory.getParent(), zipFileName);

        // Crear un archivo zip
        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Path dirPath = directory.toPath();
            Files.walk(dirPath).filter(path -> !Files.isDirectory(path)).forEach(path -> {
                try {
                    // Ruta relativa para que el archivo zip tenga la estructura correcta
                    Path relativePath = dirPath.relativize(path);
                    zipOut.putNextEntry(new ZipEntry(relativePath.toString()));

                    // Escribir el contenido del archivo en el ZIP
                    Files.copy(path, zipOut);
                    zipOut.closeEntry();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        return zipFile;
    }

    // Método para descargar el archivo zip
    public StreamedContent downloadZip(File directory) throws IOException {
        File zipFile = createZipFromDirectory(directory);

        // Crear un objeto StreamedContent para la descarga
        return DefaultStreamedContent.builder()
                .name(zipFile.getName())
                .contentType("application/zip")
                .stream(() -> {
                    try {
                        return new FileInputStream(zipFile);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .build();
    }

    public StreamedContent downloadDirectory(File file) throws FileNotFoundException {

        //File file = new File(uploadPath +"/"+ fileName);

        InputStream stream = new FileInputStream(file);

        String contentType = guessContentType(file);

        return DefaultStreamedContent.builder()
                .name(file.getName())
                .contentType(contentType)
                .stream(() -> stream)
                .build();
    }

    public boolean isImageFile(File file) {
        // Verifica si el archivo es una imagen según su extensión
        if (file!=null){
            if (file.isDirectory()){
                return false;
            }
            String fileName = file.getName().toLowerCase();

            return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png");
        }
        return false;
    }


    public String getFileUrl(File file) {
        // Si usas una ruta pública como http://localhost:8080/resources/files/
        return "/resources/files/" + file.getName();  // Ajusta esta URL según la ubicación de tus archivos en el servidor.
    }

    // Método para obtener el InputStream de una imagen
    /*public void getImageData(File file) {
        // Verifica que el archivo sea una imagen
        if (isImageFile(file)) {
            try (InputStream inputStream = new FileInputStream(file)) {
                // Configurar la respuesta HTTP para la imagen
                FacesContext context = FacesContext.getCurrentInstance();
                HttpServletResponse response = (HttpServletResponse) context.getExternalContext().getResponse();

                // Establecer encabezados de respuesta para la imagen
                response.setContentType(Files.probeContentType(file.toPath())); // Tipo MIME del archivo
                response.setContentLengthLong(file.length()); // Longitud del contenido
                response.setHeader("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");

                // Escribir el InputStream de la imagen en la respuesta
                try (OutputStream outputStream = response.getOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                context.responseComplete(); // Finaliza la respuesta

            } catch (IOException e) {
                e.printStackTrace(); // Manejo de errores, puedes agregar más detalles si es necesario
            }
        }
    }*/

    public StreamedContent getImage() {
        // Durante el renderizado de la página, JSF llama a este método
        // para generar la URL. En ese momento, devolvemos un objeto vacío.
        Logger.logInfo("hola");
        FacesContext context = FacesContext.getCurrentInstance();
        if (context.getCurrentPhaseId() == PhaseId.RENDER_RESPONSE) {
            return new DefaultStreamedContent();
        }

        // Cuando el navegador pide la imagen real usando la URL generada:
        String imageUrl = context.getExternalContext().getRequestParameterMap().get("imageUrl");
        File file=new File(imageUrl);


        if (isImageFile(file)) {
            Logger.logInfo("Es una imagen, creando StreamedContent...");
            try {
                InputStream stream = new FileInputStream(file);
                Logger.logInfo("Stream creado para: " + file.getName());

                return DefaultStreamedContent.builder()
                        .name(file.getName()) // Nombre del archivo
                        .contentType("image/" + getFileExtension(file)) // Tipo de contenido
                        .stream(() -> stream) // Stream de la imagen
                        .build();
            } catch (IOException e) {
                Logger.logInfo("Error al leer el archivo: " + file.getName()+" "+e.getMessage());
            }
        }
        Logger.logInfo("No es una imagen válida o es un directorio: " + file.getName());
        return null;
    }


    public InputStream getChartAsStream() {
        return getImage().getStream().get();
    }

    public byte[] getChartAsByteArray() throws IOException {
        InputStream is = getChartAsStream();
        byte[] array = new byte[is.available()];
        is.read(array);
        return array;
    }

    public String getFileExtension(File file) {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".jpg")) return "jpeg";
        if (fileName.endsWith(".jpeg")) return "jpeg";
        if (fileName.endsWith(".png")) return "png";
        return "octet-stream";  // Default content type
    }

    public boolean isVideoFile(File file) {
        if (file == null || file.isDirectory()) {
            return false;
        }

        String name = file.getName().toLowerCase();

        // Extensiones compatibles con la mayoría de navegadores modernos
        return name.endsWith(".mp4")  ||
                name.endsWith(".webm") ||
                name.endsWith(".ogg")  ||
                name.endsWith(".mov");
    }

    // Obtener lista de archivos en el directorio
    public List<File> getServerImges() {
        Path path = Paths.get(uploadPath);
        File[] files = path.toFile().listFiles();
        if (files != null) {
            return Arrays.asList(files);
        }
        return new ArrayList<>();
    }


    public void openDirectory(File dir){
        backStack.push(currentDirectory);
        forwardStack.clear();

        currentDirectory = dir;
        loadFiles();
        buildBreadcrumb(); // Aquí sí funciona porque es disparado por un clic
        PrimeFaces.current().ajax().update("formDownload");
    }

    public void goTo(File dir){
        backStack.push(currentDirectory);
        currentDirectory = dir;
        loadFiles();
        buildBreadcrumb(); // Seguro aquí
        PrimeFaces.current().ajax().update("formDownload");
    }



    public void goBack() {
        if (!backStack.isEmpty()) {
            forwardStack.push(currentDirectory);
            currentDirectory = backStack.pop();
            loadFiles();
            buildBreadcrumb();
        }
    }

    public void goForward() {
        if (!forwardStack.isEmpty()) {
            backStack.push(currentDirectory);
            currentDirectory = forwardStack.pop();
            loadFiles();
            buildBreadcrumb();
        }
    }

    public void goRoot() {
        backStack.push(currentDirectory);
        forwardStack.clear();

        currentDirectory = rootDirectory;
        loadFiles();
        buildBreadcrumb();
    }

    private void buildBreadcrumb() {
        actualizarDatosBreadcrumb();

        // Solo intentamos actualizar AJAX si FacesContext está disponible
        FacesContext context = FacesContext.getCurrentInstance();
        if (context != null && PrimeFaces.current() != null) {
            PrimeFaces.current().ajax().update("formBreadcrumb:breadcrumbPanel");
        }
    }



    private void loadFiles() {
            serverFiles = new ArrayList<>();
            serverFiles.addAll(Arrays.asList(Objects.requireNonNull(currentDirectory.listFiles())));

    }



    // Detectar tipo MIME
    private String guessContentType(File file) {
        try {
            return Files.probeContentType(file.toPath());
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    public List<File> getServerFiles() {
        return serverFiles;
    }

    public void setServerFiles(List<File> serverFiles) {
        this.serverFiles = serverFiles;
    }

    public String getUploadPath() {
        return uploadPath;
    }

    public boolean isCanGoBack() {
        return !backStack.isEmpty();
    }

    public boolean isCanGoForward() {
        return !forwardStack.isEmpty();
    }

    public List<File> getBreadcrumb() {
        return breadcrumb;
    }
}
