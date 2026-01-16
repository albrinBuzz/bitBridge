package org.bitBridge.view.swing.components.hosts;

import com.formdev.flatlaf.FlatClientProperties;
import org.bitBridge.Client.ClientInfo;
import org.bitBridge.Client.core.Client;
import org.bitBridge.Observers.GenericCountListener;
import org.bitBridge.Observers.NetObserver;
import org.bitBridge.shared.Logger;
import org.bitBridge.shared.ServerStatusConnection;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ChatPanel extends JPanel implements NetObserver {
    private static final Color BG_DARKER = new Color(25, 25, 25);
    private static final Color NICOTINE_ORANGE = new Color(255, 165, 0);

    private JList<String> userList;
    private DefaultListModel<String> listModel;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendBtn;

    private final Client client;
    private int unreadCount = 0;
    private GenericCountListener<ChatPanel> countListener;

    public ChatPanel(Client client) {
        this.client = client;
        // Registro del observador para recibir datos reales
        this.client.addObserver(this);

        setLayout(new BorderLayout());
        setBackground(BG_DARKER);
        initComponents();
    }

    private void initComponents() {
        // --- LISTA DE USUARIOS (Izquierda) ---
        listModel = new DefaultListModel<>();
        // El contenido inicial se llenará vía onHostListUpdated

        userList = new JList<>(listModel);
        userList.setBackground(BG_DARKER);
        userList.setForeground(new Color(180, 180, 180));
        userList.setBorder(new EmptyBorder(10, 10, 10, 10));
        userList.setSelectionBackground(new Color(45, 52, 54));

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(200, 0));
        userScroll.setBorder(null);

        // --- ÁREA DE MENSAJES (Derecha) ---
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(new Color(30, 30, 30));
        chatArea.setForeground(new Color(200, 200, 200));
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        chatArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(null);

        // --- CAMPO DE ENTRADA (Abajo) ---
        inputField = new JTextField();
        inputField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Escribe un mensaje para la red...");

        sendBtn = new JButton("Enviar");
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendBtn.putClientProperty(FlatClientProperties.STYLE, "background: " + String.format("#%02x%02x%02x",
                NICOTINE_ORANGE.getRed(), NICOTINE_ORANGE.getGreen(), NICOTINE_ORANGE.getBlue()) + "; foreground: #000000");

        // Acciones de envío
        inputField.addActionListener(e -> sendMessage());
        sendBtn.addActionListener(e -> sendMessage());

        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        inputPanel.setOpaque(false);
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);

        // --- ENSAMBLADO ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setOpaque(false);
        rightPanel.add(chatScroll, BorderLayout.CENTER);
        rightPanel.add(inputPanel, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, userScroll, rightPanel);
        splitPane.setDividerLocation(200);
        splitPane.setDividerSize(1);
        splitPane.setBorder(null);
        splitPane.setBackground(BG_DARKER);

        add(splitPane, BorderLayout.CENTER);
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty()) {
            try {
                // Enviamos al core del cliente
                client.enviarMensaje(text);
                // Si tu servidor no hace "echo" de tus propios mensajes, descomenta la sig. línea:
                 appendMessage("Tú", text, true);
                inputField.setText("");
            } catch (IOException e) {
                Logger.logError("Error al enviar mensaje: " + e.getMessage());
                appendMessage("SISTEMA", "No se pudo enviar el mensaje. Verifica la conexión.", true);
            }
        }
    }

    public void appendMessage(String user, String msg, boolean isOwnMessage) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            chatArea.append(String.format(" [%s] %s: %s\n", time, user, msg));
            chatArea.setCaretPosition(chatArea.getDocument().getLength());

            // Si no es nuestro, incrementamos contador para la pestaña
            if (!isOwnMessage) {
                unreadCount++;
                notifyCounter();
            }
        });
    }

    // --- IMPLEMENTACIÓN NETOBSERVER ---

    @Override
    public void onMessageReceived(String rawMessage) {
        // Asumiendo que rawMessage viene como "Nick: contenido"
        appendMessage("Red", rawMessage, false);
    }

    @Override
    public void onHostListUpdated(List<ClientInfo> hosts) {
        Logger.logInfo(hosts.toString());
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (ClientInfo info : hosts) {
                //String statusPrefix = info.() ? "● " : "○ ";
                listModel.addElement("● " + info.getNick());
            }
        });
    }

    @Override
    public void onStatusChanged(ServerStatusConnection status) {
        SwingUtilities.invokeLater(() -> {
            boolean connected = (status == ServerStatusConnection.CONNECTED);
            sendBtn.setEnabled(connected);
            inputField.setEditable(connected);
            appendMessage("SISTEMA", "Estado de conexión: " + status, true);
        });
    }

    // --- GESTIÓN DE CONTADORES ---

    public void setCountListener(GenericCountListener<ChatPanel> listener) {
        this.countListener = listener;
    }

    public void resetUnreadCount() {
        this.unreadCount = 0;
        notifyCounter();
    }

    private void notifyCounter() {
        if (countListener != null) {
            countListener.onCountChanged(this, unreadCount);
        }
    }
}