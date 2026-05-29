package org.bitBridge.shared.core.comunication;

import org.bitBridge.shared.Logger;
import org.bitBridge.utils.HashUtil;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NodoDirectorio {

    private final String nombre;
    private String rutaString;
    private transient Path rutaCompleta;
    private final boolean esDirectorio;
    private long tamaño;
    private final List<NodoDirectorio> hijos;
    private boolean cargado;
    private String hash; // <-- NUEVO: Para guardar el Checksum SHA-256/MD5 del archivo

    // Constructor actualizado
    public NodoDirectorio(Path ruta) {
        this.nombre = ruta.getFileName() != null ? ruta.getFileName().toString() : ruta.toString();
        this.rutaCompleta = ruta;
        this.rutaString = ruta.toAbsolutePath().toString();
        this.esDirectorio = Files.isDirectory(ruta);
        this.hijos = new ArrayList<>();
        this.cargado = !esDirectorio;

        try {
            this.tamaño = esDirectorio ? 0 : Files.size(ruta);

            // Generación opcional perezosa o directa si el archivo es pequeño
            this.hash = esDirectorio ? null : "PENDIENTE";
            setHash();
        } catch (IOException e) {
            this.tamaño = 0;
        }
    }
    /**
     * MÉTODO MAESTRO: Escanea recursivamente todo el sistema de archivos
     * y genera el árbol completo de objetos.
     */
    public static NodoDirectorio escanear(Path rutaRaiz) throws IOException {
        NodoDirectorio nodo = new NodoDirectorio(rutaRaiz);

        if (nodo.esDirectorio) {
            // Usamos un DirectoryStream para no saturar la memoria
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(rutaRaiz)) {
                for (Path entrada : stream) {
                    // Llamada recursiva: Cada hijo crea su propio sub-árbol
                    nodo.hijos.add(escanear(entrada));
                }
            }
        }
        return nodo;
    }

    /**
     * Obtiene los hijos del nodo. Si es un directorio y no ha sido cargado,
     * realiza el escaneado en ese momento.
     */
    public List<NodoDirectorio> getHijos() {
        /*if (esDirectorio && !cargado) {
            cargarContenido();
        }*/

        List<NodoDirectorio> hijos=new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rutaCompleta)) {
            for (Path entrada : stream) {
                // Creamos los nodos hijos pero NO los escaneamos aún (Lazy)
                hijos.add(new NodoDirectorio(entrada));
            }
            cargado = true;
            //System.out.println("DEBUG: Cargado nivel de " + nombre);
        } catch (IOException e) {
            System.err.println("Error cargando: " + rutaCompleta);
        }

        return hijos;
    }

    public List<NodoDirectorio> getHijosRed() {
        /*if (esDirectorio && !cargado) {
            cargarContenido();
        }*/

        return hijos;

    }


    /**
     * Escanea solo el nivel inmediato de este directorio.
     */
    public synchronized void cargarContenido() {
        if (cargado) return; // Doble verificación por seguridad de hilos

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rutaCompleta)) {
            hijos.clear();
            for (Path entrada : stream) {
                // Creamos los nodos hijos pero NO los escaneamos aún (Lazy)
                hijos.add(new NodoDirectorio(entrada));
            }
            cargado = true;
            //System.out.println("DEBUG: Cargado nivel de " + nombre);
        } catch (IOException e) {
            System.err.println("Error cargando: " + rutaCompleta);
        }
    }


    // --- MÉTODOS DE UTILIDAD ---

    public long getTamañoTotal() {
        if (!esDirectorio) return tamaño;
        return hijos.stream().mapToLong(NodoDirectorio::getTamañoTotal).sum();
    }

    /**
     * Convierte el tamaño en bytes a una cadena legible (ej: 1.5 GB).
     * @return String formateado con la unidad correspondiente.
     */
    public String getTamañoFormateado() {
        long bytes = getTamañoTotal();

        if (bytes < 1024) return bytes + " B";

        // Calculamos el exponente (0 para B, 1 para KB, 2 para MB...)
        int exp = (int) (Math.log(bytes) / Math.log(1024));

        // Definimos los prefijos de las unidades
        String unidades = "KMGTPE";
        char prefijo = unidades.charAt(exp - 1);

        // Calculamos el valor final y formateamos a 2 decimales
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), prefijo);
    }

    /**
     * Imprime el árbol en consola con indentación (para depuración)
     */
    public void imprimirArbol(String prefijo) {
        System.out.println(prefijo + (esDirectorio ? "📁 " : "📄 ") + nombre +
                (!esDirectorio ? " [" + tamaño + " bytes]" : ""));
        for (NodoDirectorio hijo : hijos) {
            hijo.imprimirArbol(prefijo + "  ");
        }
    }

    /**
     * Busca archivos o carpetas cuyo nombre contenga la secuencia especificada.
     * @param termino El nombre o parte del nombre a buscar.
     * @return Una lista con todos los nodos que coinciden con la búsqueda.
     */
    public List<NodoDirectorio> buscar(String termino) {
        List<NodoDirectorio> resultados = new ArrayList<>();
        buscarRecursivo(this, termino.toLowerCase(), resultados);
        return resultados;
    }



    private void buscarRecursivo(NodoDirectorio nodoActual, String termino, List<NodoDirectorio> resultados) {
        // Comprobar si el nodo actual coincide (ignorando mayúsculas/minúsculas)
        if (nodoActual.getNombre().toLowerCase().contains(termino)) {
            resultados.add(nodoActual);

        }

        // Si es un directorio, buscar dentro de sus hijos
        if (nodoActual.esDirectorio()) {
            for (NodoDirectorio hijo : nodoActual.getHijos()) {
                buscarRecursivo(hijo, termino, resultados);
            }
        }
    }


    public NodoDirectorio buscar(String nombre,NodoDirectorio nodoActual){
        for (NodoDirectorio hijo : nodoActual.hijos) {

            if (hijo.nombre.endsWith(nombre)) return hijo;
        }
        return null;
    }

    public NodoDirectorio ir(String termino) {
        List<NodoDirectorio> resultados = new ArrayList<>();

        NodoDirectorio nodo=buscar(termino,this);

        Logger.logInfo(nodo.getNombre());

        //irRecursivo(nodo, resultados);
        return nodo;
    }

    private void irRecursivo(NodoDirectorio nodoActual, List<NodoDirectorio> resultados) {
        // Comprobar si el nodo actual coincide (ignorando mayúsculas/minúsculas)
        /*if (nodoActual.getNombre().toLowerCase().contains(termino)) {
            resultados.add(nodoActual);
        }*/
        Logger.logInfo(nodoActual.getNombre());
        resultados.add(nodoActual);
        for (NodoDirectorio hijo : nodoActual.hijos) {
            //Logger.logInfo(hijo.getNombre());
            if (hijo.esDirectorio){
                irRecursivo(hijo, resultados);
            }else {
                resultados.add(hijo);
            }
        }

    }





    /**
     * Estima el peso en RAM (Heap) de este nodo y todos sus descendientes.
     * Nota: Es una aproximación basada en arquitecturas de 64 bits.
     */
    public long getPesoEstimadoRAM() {
        long peso = 32; // Overhead base del objeto NodoDirectorio (Header + referencias)

        // Peso del String 'nombre' (aprox 24 bytes + (longitud * 2))
        if (nombre != null) {
            peso += 24 + (nombre.length() * 2L);
        }

        // Peso de la ruta (Path suele ser un objeto complejo, estimamos 64 bytes)
        peso += 64;

        // Peso de la lista 'hijos' (Overhead de ArrayList + referencias internas)
        peso += 24 + (hijos.size() * 8L);

        // Sumar el peso de cada hijo recursivamente
        for (NodoDirectorio hijo : hijos) {
            peso += hijo.getPesoEstimadoRAM();
        }

        return peso;
    }

    /**
     * Filtra los archivos por una lista de extensiones (ej: "jpg", "pdf").
     * @param extensiones Varargs de extensiones sin el punto.
     * @return Lista de nodos que coinciden.
     */
    public List<NodoDirectorio> filtrarPorExtension(String... extensiones) {
        List<NodoDirectorio> resultados = new ArrayList<>();
        for (NodoDirectorio hijo : getHijos()) {
            if (!hijo.esDirectorio()) {
                for (String ext : extensiones) {
                    if (hijo.nombre.toLowerCase().endsWith("." + ext.toLowerCase())) {
                        resultados.add(hijo);
                        break;
                    }
                }
            } else {
                resultados.addAll(hijo.filtrarPorExtension(extensiones));
            }
        }
        return resultados;
    }

    /**
     * Cuenta cuántos archivos hay en total dentro de este nodo (recursivo).
     */
    public long contarArchivosTotales() {
        if (!esDirectorio) return 1;
        return getHijos().stream().mapToLong(NodoDirectorio::contarArchivosTotales).sum();
    }

    /**
     * Devuelve el peso en RAM formateado para humanos.
     */
    public String getPesoRAMFormateado() {
        long bytes = getPesoEstimadoRAM();
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        return String.format("%.2f %cB", bytes / Math.pow(1024, exp), "KMGTPE".charAt(exp - 1));
    }
    /**
     * Obtiene la extensión del archivo (ej: .txt).
     */
    public String getExtension() {
        if (esDirectorio) return "Carpeta";
        String nombre = getNombre();
        int lastDot = nombre.lastIndexOf('.');
        return (lastDot > 0) ? nombre.substring(lastDot).toUpperCase() : "Archivo";
    }



    /**
     * Calcula el Hash MD5 para verificar integridad (solo para archivos).
     * ¡Cuidado! Usar solo si es necesario, consume CPU en archivos grandes.
     */
    public String getHashIntegridad() {
        /*if (esDirectorio) return "-";
        try (java.io.InputStream is = Files.newInputStream(rutaCompleta)) {
            return org.apache.commons.codec.digest.DigestUtils.md5Hex(is);
        } catch (Exception e) {
            return "N/A";
        }*/
        return "Yes";
    }

    /**
     * Devuelve el tamaño base en bytes (necesario para la comparación rápida en la UI).
     */
    public long getTamaño() {
        return this.tamaño;
    }

    /**
     * Obtiene la fecha de última modificación real del sistema de archivos.
     */
    public String getFechaModificacion() {
        if (rutaCompleta == null || !Files.exists(rutaCompleta)) return "Desconocido";
        try {
            java.nio.file.attribute.FileTime time = Files.getLastModifiedTime(rutaCompleta);
            java.time.LocalDateTime ldt = java.time.LocalDateTime.ofInstant(
                    time.toInstant(), java.time.ZoneId.systemDefault());
            return ldt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-8HH:mm"));
        } catch (IOException e) {
            return "Error";
        }
    }

    /**
     * Getter y Setter para el Hash que viaja a través de Netty.
     */
    public String getHash() {
        // Si el hash local no se ha calculado y es un archivo local, lo calculamos bajo demanda
        if (!esDirectorio && ("PENDIENTE".equals(hash) || hash == null) && rutaCompleta != null && Files.exists(rutaCompleta)) {
            this.hash = calcularHashLocal();
        }
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
    public void setHash(){
        this.hash =    calcularHashLocal();
    }

    /**
     * Calcula el hash local usando un flujo seguro para no saturar memoria.
     */
    private String calcularHashLocal() {
        try (java.io.InputStream is = Files.newInputStream(rutaCompleta)) {
            // Puedes usar tu HashUtil.getFileChecksum(rutaCompleta.toFile()) aquí mismo:
            return HashUtil.getFileChecksum(rutaCompleta.toFile());
        } catch (Exception e) {
            return null;
        }
    }

    // Getters
    public String getNombre() { return nombre; }
    //public List<NodoDirectorio> getHijos() { return hijos; }
    public boolean esDirectorio() { return esDirectorio; }
    public Path getRutaCompleta() { return rutaCompleta; }

    public String getRutaString() {
        return rutaString;
    }

    public void setRutaString(String rutaString) {
        this.rutaString = rutaString;
    }

    public static void main(String[] args) throws IOException {

        Path rutaBase=Path.of("/home/cris/");


        NodoDirectorio raiz = new NodoDirectorio(rutaBase);

        // En este punto, raiz.hijos está vacío.
        // Al llamar a getHijos(), se disparará la carga del primer nivel:
        List<NodoDirectorio> primerNivel = raiz.getHijos();

        /*for (NodoDirectorio nodo : primerNivel) {
            System.out.println(nodo.getNombre());
            if (nodo.esDirectorio()) {

                // Los hijos de estas carpetas aún NO están en RAM.
                // Solo se cargarán si haces nodo.getHijos()
            }
        }*/

        // Escaneamos TODO el directorio y sus subcarpetas
        NodoDirectorio miArbol = NodoDirectorio.escanear(rutaBase);
        String query = "pdf"; // Buscamos todos los PDFs
        List<NodoDirectorio> encontrados = miArbol.buscar(query);

        List<NodoDirectorio> peliculas = miArbol.filtrarPorExtension("mp4", "mkv");
        //List<NodoDirectorio> pesados = miArbol.obtenerArchivosPesados(1024 * 1024 * 500); // +500MB

        // Lo mostramos formateado
        //miArbol.imprimirArbol("");

        System.out.println("Archivos totales: " + miArbol.contarArchivosTotales());
        System.out.println("Videos detectados: " + peliculas.size());

        for (NodoDirectorio pelicula : peliculas) {
            System.out.println(pelicula.getNombre());
        }

        System.out.println("Se encontraron " + encontrados.size() + " coincidencias:");
        /*for (NodoDirectorio n : encontrados) {
            String tipo = n.esDirectorio() ? "[DIR]" : "[FILE]";
            System.out.println(tipo + " " + n.getNombre() + " -> Ruta: " + n.getRutaCompleta());
        }*/

        System.out.println("Peso total del repositorio: " + miArbol.getTamañoFormateado() + " bytes");
        System.out.println("Peso total En Ram: " + miArbol.getPesoRAMFormateado() + " bytes");

    }
}
