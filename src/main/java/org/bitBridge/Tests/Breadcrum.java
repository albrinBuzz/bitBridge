package org.bitBridge.Tests;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Breadcrum {
    public static void main(String[] args) {
        // 1. Definimos la base (donde empieza tu carpeta compartida)
        Path raizDatos = Paths.get("/home/cris/baseDatos");

        // 2. Definimos dónde estamos "metidos" ahora mismo (el nodo actual)
        Path nodoActual = Paths.get("/home/cris/baseDatos/proyectos/java/bitBridge");

        // 3. Calculamos la ruta RELATIVA (el camino desde la raíz hasta el nodo actual)
        // Esto ignora "/home/cris/baseDatos" y se queda con el resto
        Path rutaRelativa = raizDatos.relativize(nodoActual);

        System.out.println("Ruta Base: " + raizDatos);
        System.out.println("Nodo Actual: " + nodoActual);
        System.out.println("Camino relativo: " + rutaRelativa);
        System.out.println("-------------------------------------------");

        // 4. Recorremos los fragmentos para crear el Breadcrumb
        // Empezamos con el String de la raíz para ir reconstruyendo paso a paso
        StringBuilder acumulado = new StringBuilder(raizDatos.toString());

        for (Path elemento : rutaRelativa) {
            // En cada vuelta del bucle, 'elemento' es una carpeta individual

            // Reconstruimos la ruta absoluta sumando el fragmento actual
            acumulado.append("/").append(elemento.toString());
            System.out.println(elemento.toString());

        }
        System.out.println(acumulado.toString());
    }
}
