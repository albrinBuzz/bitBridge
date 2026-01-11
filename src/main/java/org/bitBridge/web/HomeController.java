package org.bitBridge.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.bitBridge.Client.core.Client;
import org.bitBridge.shared.CommunicationType;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.Mensaje;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;


@Controller
@RequestMapping("/")
public class HomeController {

    @GetMapping("")
    public RedirectView home(HttpServletRequest request, Model model) throws IOException, InterruptedException {
        // Log de la información básica
        Logger.logInfo("Dentro del controlador");

        // Obtener detalles del cliente
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String acceptLanguage = request.getHeader("Accept-Language");
        String referer = request.getHeader("Referer");
        String requestMethod = request.getMethod();
        String requestUri = request.getRequestURI();
        String host = request.getRemoteHost();
        String remoteAddr = request.getRemoteAddr();

        // Imprimir la información obtenida
        Logger.logInfo("IP del cliente: " + clientIp);
        Logger.logInfo("User-Agent: " + userAgent);
        Logger.logInfo("Idioma aceptado: " + acceptLanguage);
        Logger.logInfo("Referer: " + referer);
        Logger.logInfo("Método de solicitud: " + requestMethod);
        Logger.logInfo("URI solicitada: " + requestUri);
        Logger.logInfo("Host remoto: " + host);
        Logger.logInfo("Dirección remota: " + remoteAddr);

        // Obtener detalles de la sesión
        HttpSession session = request.getSession();
        String sessionId = session.getId();
        Logger.logInfo("ID de sesión: " + sessionId);
        System.out.println("");

        String serverAddress = "localhost";  // Dirección del servidor de sockets
        int serverPort = 8080;  // Puerto del servidor de sockets

        /*Client client=new Client();
        client.setConexion(serverAddress, serverPort);
        session.setAttribute("client", client);*/

        // Redirigir a la vista correspondiente
        return new RedirectView("home/index.xhtml");
    }

    // Método para obtener la IP del cliente
    private String getClientIp(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null) {
            clientIp = request.getRemoteAddr();
        }
        return clientIp;
    }

    @GetMapping("/sendMessage")
    public String sendMessage(@RequestParam("message") String message, HttpServletRequest request, Model model) {
        // Obtener la sesión HTTP
        HttpSession session = request.getSession();

        // Recuperar el cliente desde la sesión
        Client client = (Client) session.getAttribute("client");

        if (client != null) {
            try {
                // Crear el mensaje a enviar
                Mensaje mensaje = new Mensaje(message, CommunicationType.MESSAGE);

                // Enviar el mensaje al cliente a través del ObjectOutputStream
                client.enviarMensaje(message);

                // Agregar mensaje de éxito al modelo
                model.addAttribute("status", "Mensaje enviado exitosamente.");
            } catch (Exception e) {
                // Si ocurre algún error, mostrar un mensaje de error
                e.printStackTrace();
                model.addAttribute("status", "Error al enviar el mensaje.");
            }
        } else {
            model.addAttribute("status", "No se encontró un cliente en la sesión.");
        }

        // Redirigir o mostrar un mensaje en la vista
        return "messageStatus";  // Nombre de la vista donde mostrarás el estado del envío
    }

    /*@GetMapping("/baja")
    public ResponseEntity<byte[]> downloadFile() throws IOException {
        // Construir la ruta del archivo basado en el nombre proporcionado
        Logger.logInfo("bajando archivos");
        Path filePath = Paths.get("/home/cris/images.png");

        String filename=filePath.getFileName().toString();

        // Verificar si el archivo existe
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();  // Si no se encuentra el archivo, devolver 404
        }

        // Leer el archivo desde el sistema de archivos
        byte[] fileBytes = Files.readAllBytes(filePath);

        // Devolver el archivo con los encabezados adecuados para la descarga
        return ResponseEntity.ok()
                .header("Content-Type", Files.probeContentType(filePath))  // Tipo de archivo
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")  // Forzar la descarga
                .body(fileBytes);  // El contenido del archivo
    }*/
}
