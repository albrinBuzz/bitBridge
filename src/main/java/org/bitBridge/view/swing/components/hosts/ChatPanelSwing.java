package org.bitBridge.view.swing.components.hosts;


import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.NetObserver;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.ServerStatusConnection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;

public class ChatPanelSwing extends JPanel implements NetObserver {

    private JTextArea chatArea;
    private JTextField inputChat;
    private JButton sendChatBtn;
    private JLabel usuariosChatLabel;
    private final Client client;

    // Colores para el estilo moderno
    private final Color COLOR_FONDO = new Color(45, 52, 54);
    private final Color COLOR_ACCENTO = new Color(0, 191, 255);
    private final Color COLOR_TEXTO = new Color(220, 221, 225);

    public ChatPanelSwing(Client client) {
        this.client = client;
        initGUI();
    }

    private void initGUI() {
        setLayout(new BorderLayout(5, 5));
        setBackground(COLOR_FONDO);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 63, 65), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        // --- NORTE: Títulos ---
        JPanel headerPanel = new JPanel(new GridLayout(2, 1));
        headerPanel.setOpaque(false);

        JLabel chatLabel = new JLabel("💬 Chat y coordinación de transferencias");
        chatLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        chatLabel.setForeground(COLOR_ACCENTO);

        usuariosChatLabel = new JLabel("[Usuarios en chat: Esperando lista...]");
        usuariosChatLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        usuariosChatLabel.setForeground(Color.GRAY);

        headerPanel.add(chatLabel);
        headerPanel.add(usuariosChatLabel);
        add(headerPanel, BorderLayout.NORTH);

        // --- CENTRO: Área de Mensajes ---
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(new Color(30, 30, 30));
        chatArea.setForeground(COLOR_TEXTO);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        chatArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(60, 63, 65)));
        add(scrollPane, BorderLayout.CENTER);

        // --- SUR: Entrada de texto ---
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setOpaque(false);
        inputPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

        inputChat = new JTextField();
        inputChat.setBackground(new Color(60, 63, 65));
        inputChat.setForeground(Color.WHITE);
        inputChat.setCaretColor(Color.WHITE);

        // Enviar mensaje al presionar ENTER
        inputChat.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }
        });

        sendChatBtn = new JButton("Enviar");
        sendChatBtn.setBackground(COLOR_ACCENTO);
        sendChatBtn.setForeground(Color.WHITE);
        sendChatBtn.addActionListener(e -> sendMessage());

        inputPanel.add(inputChat, BorderLayout.CENTER);
        inputPanel.add(sendChatBtn, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);
    }

    public void sendMessage() {
        String message = inputChat.getText().trim();
        if (!message.isEmpty()) {
            try {
                // Agregar localmente
                appendMessage("Tú: " + message, true);
                // Enviar al servidor
                client.enviarMensaje(message);
                inputChat.setText("");
            } catch (IOException e) {
                Logger.logError("Error enviando mensaje: " + e.getMessage());
                appendMessage("⚠️ Error: No se pudo enviar el mensaje.", false);
            }
        }
    }

    private void appendMessage(String message, boolean isSent) {
        // En Swing, si estamos en un hilo externo (NetObserver), usamos invokeLater
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            // Auto-scroll al final
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    // --- IMPLEMENTACIÓN NETOBSERVER ---

    @Override
    public void onMessageReceived(String message) {
        appendMessage(message, false);
    }

    @Override
    public void onHostListUpdated(List<ClientInfo> hosts) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hosts.size(); i++) {
                sb.append(hosts.get(i).getNick());
                if (i < hosts.size() - 1) sb.append(", ");
            }
            usuariosChatLabel.setText("[Usuarios en chat: " + sb.toString() + "]");
        });
    }

    @Override
    public void onStatusChanged(ServerStatusConnection statusConnection) {
        // Manejar cambios de estado si es necesario
    }
}