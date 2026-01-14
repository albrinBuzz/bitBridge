package org.bitBridge.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfiguracionServidor {

    private static ConfiguracionServidor instancia;
    private Properties propiedades;
    private final String rutaArchivoConfig;

    private ConfiguracionServidor() {
        propiedades = new Properties();

        // 1. Definimos rutas basadas en el HOME del usuario actual
        String userHome = System.getProperty("user.home");
        // Usamos Paths para que sea agnóstico al separador (/ o \)
        Path pathConfig = Paths.get(userHome, ".config", "fileTalk", "config.properties");
        this.rutaArchivoConfig = pathConfig.toString();

        loadConfig();
    }

    // Singleton para acceder a la configuración desde cualquier parte
    public static synchronized ConfiguracionServidor getInstancia() {
        if (instancia == null) {
            instancia = new ConfiguracionServidor();
        }
        return instancia;
    }

    private void loadConfig() {
        File file = new File(rutaArchivoConfig);

        if (!file.exists()) {
            // Si no existe, creamos el archivo Y cargamos los valores por defecto en el objeto
            crearConfiguracionBase(file);
        } else {
            // Si ya existe, simplemente lo leemos
            try (FileInputStream entrada = new FileInputStream(file)) {
                propiedades.load(entrada);
            } catch (IOException e) {
                System.err.println("Error al cargar configuración: " + e.getMessage());
                // Fallback: cargar base en memoria aunque no se pueda leer el archivo
                crearConfiguracionBase(file);
            }
        }
    }

    private void crearConfiguracionBase(File file) {
        try {
            if (!file.exists()) {
                Files.createDirectories(file.toPath().getParent());
                file.createNewFile();
            }

            // Llenamos el objeto propiedades DIRECTAMENTE
            String userHome = System.getProperty("user.home");
            propiedades.setProperty("servidor.nombre", "Servidor_Principal");
            propiedades.setProperty("servidor.puerto", "8080");

            propiedades.setProperty("cliente.directorioConfig", file.getParent());

            // Carpeta Filetalk en el Home
            Path descargasDefault = Paths.get(userHome, "Filetalk");
            propiedades.setProperty("cliente.directorio_descargas", descargasDefault.toString());

            // Creamos la carpeta de descargas físicamente para que no falle DownloadBean
            Files.createDirectories(descargasDefault);

            propiedades.setProperty("red.direccion_ip", "192.168.1.100");
            propiedades.setProperty("red.puerto", "9090");
            propiedades.setProperty("cliente.puerto", "8080");

            // GUARDAR: Esto escribe lo que acabamos de setear
            guardarEnArchivo();

        } catch (IOException e) {
            // En lugar de detener la app, inicializamos con mínimos en memoria
            System.err.println("Advertencia: Usando configuración temporal en memoria.");
        }
    }

    public void guardarEnArchivo() {
        try (FileOutputStream output = new FileOutputStream(rutaArchivoConfig)) {
            propiedades.store(output, "Configuración del Servidor FileTalk");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String obtener(String clave) {
        return propiedades.getProperty(clave);
    }

    public String obtener(String clave, String valorPorDefecto) {
        return propiedades.getProperty(clave, valorPorDefecto);
    }

    public int obtenerInt(String clave, int valorDefecto) {
        try {
            return Integer.parseInt(propiedades.getProperty(clave, String.valueOf(valorDefecto)));
        } catch (NumberFormatException e) {
            return valorDefecto;
        }
    }

    public void setProperty(String clave, String nuevoNombre) {
        propiedades.setProperty(clave,nuevoNombre);
    }
}