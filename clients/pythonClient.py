import socket
import json
import struct
import threading
from enum import Enum

# 1. Definir los tipos de comunicación (Igual al Enum de Java)
class CommunicationType(str, Enum):
    MESSAGE = "MESSAGE"
    PRIVATE_MESSAGE = "PRIVATE_MESSAGE"
    SYSTEM_MESSAGE = "SYSTEM_MESSAGE"
    UPDATE = "UPDATE"
    DISCONNECT = "DISCONNECT"
    # ... agrega los demás según necesites

class BitBridgeClient:
    def __init__(self):
        self.socket = None
        self.host_name = socket.gethostname()
        self.running = False
        self.observers = [] # Tus NetObservers de Python

    def connect(self, address, port):
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((address, port))
            self.running = True

            # 1. Enviar Identificación Inicial (Igual que en Java)
            self.enviar_mensaje(self.host_name)

            # 2. Iniciar hilo de escucha (Equivalente a ReadMessages)
            threading.Thread(target=self._read_loop, daemon=True).start()
            print(f"Conectado a {address}:{port} como {self.host_name}")

        except Exception as e:
            print(f"Error de conexión: {e}")

    def enviar_comunicacion(self, comm_type, content_dict):
        """
        Empaqueta al estilo [INT: Longitud][UTF: Tipo][BYTES: JSON]
        """
        if not self.socket: return

        # Construir el objeto JSON (lo que en Java es la clase Communication)
        payload_dict = {
            "communicationType": comm_type,
            **content_dict # Mezcla el contenido con el tipo
        }

        json_data = json.dumps(payload_dict).encode('utf-8')
        type_data = comm_type.encode('utf-8')

        # [INT: Tamaño JSON] - 4 bytes
        header_length = struct.pack('>I', len(json_data))
        # [UTF: Tipo] - 2 bytes de longitud + texto (formato DataOutputStream de Java)
        header_type = struct.pack('>H', len(type_data)) + type_data

        try:
            self.socket.sendall(header_length + header_type + json_data)
        except Exception as e:
            print(f"Error enviando datos: {e}")
            self.disconnect()

    def enviar_mensaje(self, texto):
        self.enviar_comunicacion(CommunicationType.MESSAGE, {"contenido": texto})

    def _read_loop(self):
        """Hilo equivalente a ReadMessages en Java"""
        try:
            while self.running:
                # 1. Leer Prefijo de Longitud (4 bytes)
                raw_len = self.socket.recv(4)
                if not raw_len: break
                length = struct.unpack('>I', raw_len)[0]

                # 2. Leer Tipo (writeUTF: 2 bytes len + texto)
                raw_type_len = self.socket.recv(2)
                type_len = struct.unpack('>H', raw_type_len)[0]
                comm_type = self.socket.recv(type_len).decode('utf-8')

                # 3. Leer JSON Payload
                raw_json = self.socket.recv(length)
                json_obj = json.loads(raw_json.decode('utf-8'))

                # 4. Despachar mensaje (Equivalente a dispatcher.dispatch)
                self._dispatch(comm_type, json_obj)

        except Exception as e:
            print(f"Error en el hilo de lectura: {e}")
        finally:
            self.disconnect()

    def _dispatch(self, comm_type, data):
        """Maneja los mensajes recibidos"""
        if comm_type == CommunicationType.MESSAGE:
            contenido = data.get("contenido", "")
            print(f"\n[MENSAJE] {contenido}")
            self.notify_observers(contenido)

        elif comm_type == CommunicationType.UPDATE:
            # Aquí vendría la lista de nicks
            nicks = data.get("clientNicks", [])
            print(f"\n[SISTEMA] Usuarios conectados: {len(nicks)}")

    def add_observer(self, callback):
        self.observers.append(callback)

    def notify_observers(self, msg):
        for obs in self.observers:
            obs(msg)

    def disconnect(self):
        self.running = False
        if self.socket:
            self.socket.close()
            print("Desconectado de BitBridge.")

# --- EJEMPLO DE USO ---
if __name__ == "__main__":
    client = BitBridgeClient()

    # Simular un observador de la UI
    def on_new_message(msg):
        pass # Aquí actualizarías una interfaz en Python

    client.add_observer(on_new_message)
    client.connect("127.0.0.1", 8080)

    # Bucle para escribir en consola
    while True:
        txt = input("Escribe un mensaje (o 'exit'): ")
        if txt.lower() == 'exit':
            client.disconnect()
            break
        client.enviar_mensaje(txt)