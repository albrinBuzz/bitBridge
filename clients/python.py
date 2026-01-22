import socket
import json
import struct
import threading
import tkinter as tk
from tkinter import scrolledtext, messagebox

class BitBridgeGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("BitBridge Client - Python Edition")
        self.root.geometry("500x600")

        # Estado de conexión
        self.sock = None
        self.connected = False

        # --- Interfaz Gráfica ---
        # Configuración de conexión
        conn_frame = tk.Frame(root)
        conn_frame.pack(pady=10, fill=tk.X, padx=10)

        tk.Label(conn_frame, text="Nick:").pack(side=tk.LEFT)
        self.nick_entry = tk.Entry(conn_frame, width=15)
        self.nick_entry.insert(0, "PythonUser")
        self.nick_entry.pack(side=tk.LEFT, padx=5)

        self.btn_connect = tk.Button(conn_frame, text="Conectar", command=self.toggle_connection)
        self.btn_connect.pack(side=tk.LEFT, padx=5)

        # Área de Chat
        self.chat_area = scrolledtext.ScrolledText(root, state='disabled', wrap=tk.WORD)
        self.chat_area.pack(pady=10, padx=10, fill=tk.BOTH, expand=True)

        # Entrada de mensaje
        msg_frame = tk.Frame(root)
        msg_frame.pack(pady=10, fill=tk.X, padx=10)

        self.msg_entry = tk.Entry(msg_frame)
        self.msg_entry.bind("<Return>", lambda e: self.send_message())
        self.msg_entry.pack(side=tk.LEFT, fill=tk.X, expand=True)

        self.btn_send = tk.Button(msg_frame, text="Enviar", command=self.send_message, state='disabled')
        self.btn_send.pack(side=tk.RIGHT, padx=5)

    def log(self, message):
        """Escribe en la consola visual del chat."""
        self.chat_area.config(state='normal')
        self.chat_area.insert(tk.END, message + "\n")
        self.chat_area.config(state='disabled')
        self.chat_area.see(tk.END)

    def toggle_connection(self):
        if not self.connected:
            self.connect_to_server()
        else:
            self.disconnect()

    def connect_to_server(self):
        host = "127.0.0.1"
        port = 8080
        nick = self.nick_entry.get().strip()

        if not nick:
            messagebox.showwarning("Error", "Ingresa un Nick")
            return

        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.sock.connect((host, port))
            self.connected = True

            # Iniciar hilo de escucha
            threading.Thread(target=self.receive_loop, daemon=True).start()

            # Primer mensaje: Autenticación (según tu lógica handleAuthentication)
            self.send_protocol_message(nick, "MESSAGE")

            self.btn_connect.config(text="Desconectar")
            self.btn_send.config(state='normal')
            self.nick_entry.config(state='disabled')
            self.log(f"[*] Conectado a {host}:{port} como {nick}")

        except Exception as e:
            self.log(f"[!] Error de conexión: {e}")
            messagebox.showerror("Error", f"No se pudo conectar: {e}")

    def send_protocol_message(self, content, type_name):
        """Implementación exacta de ProtocolService.writeNIO"""
        try:
            # Construir el objeto JSON
            payload = {
                "contenido": content,
                "communicationType": type_name
            }
            json_str = json.dumps(payload)
            json_bytes = json_str.encode('utf-8')
            type_bytes = type_name.encode('utf-8')

            # Protocolo: [INT: jsonLen][SHORT: typeLen][BYTES: typeName][BYTES: JSON]
            # '>' indica Big Endian (Java default)
            header = struct.pack(">ih", len(json_bytes), len(type_bytes))

            packet = header + type_bytes + json_bytes
            self.sock.sendall(packet)
        except Exception as e:
            self.log(f"[!] Error al enviar: {e}")
            self.disconnect()

    def send_message(self):
        msg = self.msg_entry.get().strip()
        if msg and self.connected:
            self.send_protocol_message(msg, "MESSAGE")
            self.msg_entry.delete(0, tk.END)

    def receive_loop(self):
        """Hilo dedicado a recibir datos sin bloquear la GUI."""
        while self.connected:
            try:
                # 1. Leer Header (6 bytes)
                header_data = self.recv_all(6)
                if not header_data: break

                json_len, type_len = struct.unpack(">ih", header_data)

                # 2. Leer el resto (Tipo + JSON)
                body_data = self.recv_all(type_len + json_len)
                if not body_data: break

                # Extraer partes
                # type_str = body_data[:type_len].decode('utf-8') # Opcional usarlo
                json_part = body_data[type_len:].decode('utf-8')

                # Parsear JSON y mostrar
                data = json.loads(json_part)
                if "contenido" in data:
                    self.root.after(0, self.log, data["contenido"])
                elif "status" in data: # Caso MessageAck
                    self.root.after(0, self.log, f"[Servidor: {data['status']}]")

            except Exception as e:
                if self.connected:
                    self.root.after(0, self.log, f"[!] Conexión perdida: {e}")
                break

        self.root.after(0, self.disconnect)

    def recv_all(self, n):
        """Asegura leer exactamente N bytes (fundamental para protocolos binarios)."""
        data = bytearray()
        while len(data) < n:
            packet = self.sock.recv(n - len(data))
            if not packet: return None
            data.extend(packet)
        return data

    def disconnect(self):
        self.connected = False
        if self.sock:
            self.sock.close()
        self.btn_connect.config(text="Conectar")
        self.btn_send.config(state='disabled')
        self.nick_entry.config(state='normal')
        self.log("[*] Desconectado.")

if __name__ == "__main__":
    root = tk.Tk()
    app = BitBridgeGUI(root)
    root.mainloop()