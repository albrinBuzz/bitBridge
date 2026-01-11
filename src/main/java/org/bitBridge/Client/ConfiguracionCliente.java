package org.bitBridge.Client;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfiguracionCliente {

    private Properties propiedades;
    private String ruta;

    public ConfiguracionCliente() {
        propiedades = new Properties();
        cargarConfiguracion();
    }

    private void cargarConfiguracion() {
        String home = System.getProperty("user.home");
        String separador = System.getProperty("file.separator");
        String rutaConfCliente = home + separador + ".config" + separador + "fileTalk" + separador + "cliente.properties";
        ruta = rutaConfCliente;

        File archivo = new File(rutaConfCliente);
        if (!archivo.exists()) {
            try {
                crearDirectorios(archivo.getAbsolutePath());
                archivo.createNewFile();
                establecerConfiguraciones();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try (FileInputStream entrada = new FileInputStream(ruta)) {
            propiedades.load(entrada);
            verificarDirectorioDescargas();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void crearDirectorios(String archivo) {
        try {
            Path path = Paths.get(archivo);
            Path directorioPadre = path.getParent();

            if (directorioPadre != null) {
                Files.createDirectories(directorioPadre);
            }
        } catch (IOException e) {
            System.out.println("Error al crear los directorios: " + e.getMessage());
        }
    }

    public String obtener(String clave) {
        return propiedades.getProperty(clave);
    }

    public int obtenerInt(String clave, int valorPorDefecto) {
        try {
            return Integer.parseInt(propiedades.getProperty(clave, String.valueOf(valorPorDefecto)));
        } catch (NumberFormatException e) {
            return valorPorDefecto;
        }
    }

    private void establecerConfiguraciones() {
        propiedades.setProperty("cliente.nombre", "Cliente_Principal");
        propiedades.setProperty("cliente.id", "CL-001");
        propiedades.setProperty("cliente.usuario_predeterminado", "usuario1");
        String home = System.getProperty("user.home");
        String separador = System.getProperty("file.separator");
        propiedades.setProperty("cliente.directorio_descargas", home+separador+"Filetalk"+separador);
        propiedades.setProperty("cliente.directorioConfig", home + separador + ".config" + separador + "fileTalk");
        propiedades.setProperty("cliente.servidor_ip", "192.168.1.100");
        propiedades.setProperty("cliente.servidor_puerto", "8080");
        propiedades.setProperty("cliente.tiempo_espera", "3000");
        propiedades.setProperty("cliente.intentos_reconexion", "5");
        propiedades.setProperty("cliente.habilitar_logs", "true");

        try (FileOutputStream salida = new FileOutputStream(ruta)) {
            propiedades.store(salida, "Configuración del Cliente");
            System.out.println("Archivo de configuración de cliente creado exitosamente.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void verificarDirectorioDescargas() {
        String rutaDescargas = obtener("cliente.directorio_descargas");
        if (rutaDescargas != null) {
            File dir = new File(rutaDescargas);
            if (!dir.exists()) {
                boolean creado = dir.mkdirs();
                if (creado) {
                    System.out.println("Directorio de descargas creado: " + rutaDescargas);
                } else {
                    System.err.println("No se pudo crear el directorio de descargas.");
                }
            }
        }
    }

    public String getRuta() {
        return ruta;
    }
}
