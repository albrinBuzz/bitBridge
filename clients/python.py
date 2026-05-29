import sys
import asyncio
import struct
import json
import psutil
from enum import Enum
from PySide6.QtWidgets import (QApplication, QMainWindow, QWidget, QVBoxLayout,
                             QHBoxLayout, QLabel, QTableWidget, QTableWidgetItem,
                             QHeaderView, QFrame, QLineEdit, QPushButton)
from PySide6.QtCore import Qt, QTimer
from PySide6.QtGui import QColor

# --- MOTOR DE RED (Igual, pero sin dependencias externas) ---
class NioClientEngine:
    def __init__(self, dispatcher):
        self.dispatcher = dispatcher
        self.reader = None
        self.writer = None
        self.is_running = False

    async def connect(self, host, port):
        try:
            self.reader, self.writer = await asyncio.open_connection(host, port)
            self.is_running = True
            asyncio.create_task(self._read_loop())
            return True
        except Exception as e:
            print(f"Error de conexión: {e}")
            return False

    async def _read_loop(self):
        try:
            while self.is_running:
                header = await self.reader.readexactly(8)
                json_size, type_size = struct.unpack('>ii', header)
                payload = await self.reader.readexactly(type_size + json_size)
                type_name = payload[:type_size].decode('utf-8').strip()
                json_data = payload[type_size:].decode('utf-8')
                data_dict = json.loads(json_data)
                await self.dispatcher.dispatch(type_name, data_dict)
        except Exception as e:
            print(f"Desconectado: {e}")
            self.is_running = False

    async def send(self, comm_type, data):
        if not self.writer or self.writer.is_closing(): return
        json_bytes = json.dumps(data).encode('utf-8')
        type_bytes = comm_type.encode('utf-8')
        header = struct.pack('>ii', len(json_bytes), len(type_bytes))
        self.writer.write(header + type_bytes + json_bytes)
        await self.writer.drain()

# --- INTERFAZ UI ---
class BitBridgeFancyClient(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("BitBridge Terminal - Pro")
        self.resize(1100, 700)
        self.engine = NioClientEngine(self)
        self.host_name = f"PyNode_{psutil.os.getpid()}"

        # Estilo Neón
        self.setStyleSheet("""
            QMainWindow { background-color: #05070a; }
            QWidget { color: #00d1ff; font-family: 'Consolas', monospace; }
            QFrame#Panel { background-color: #0d1117; border: 1px solid #1a1e26; border-radius: 10px; }
            QLineEdit { background: #161b22; border: 1px solid #30363d; padding: 8px; color: white; border-radius: 5px; }
            QPushButton { background: #00d1ff; color: #05070a; font-weight: bold; border-radius: 5px; padding: 10px; }
            QPushButton:hover { background: #00ff88; }
            QTableWidget { background: transparent; border: none; alternate-background-color: #0d1117; }
        """)

        # Layout
        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)

        # Header y Status
        header = QHBoxLayout()
        self.lbl_status = QLabel("OFFLINE")
        self.lbl_status.setStyleSheet("color: #ff3e3e;")
        header.addWidget(QLabel("BITBRIDGE NODE // SYSTEM_READY"))
        header.addStretch()
        header.addWidget(self.lbl_status)
        layout.addLayout(header)

        # Conexión
        self.input_ip = QLineEdit("127.0.0.1")
        self.input_port = QLineEdit("8080")
        self.btn_connect = QPushButton("CONNECT")
        self.btn_connect.clicked.connect(self.handle_connect_click) # Llamada normal

        conn_box = QHBoxLayout()
        conn_box.addWidget(self.input_ip); conn_box.addWidget(self.input_port); conn_box.addWidget(self.btn_connect)
        layout.addLayout(conn_box)

        # Chat Log
        self.log_table = QTableWidget(0, 1)
        self.log_table.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.log_table.horizontalHeader().setVisible(False)
        layout.addWidget(self.log_table)

        # Input Mensaje
        msg_layout = QHBoxLayout()
        self.input_msg = QLineEdit()
        self.btn_send = QPushButton("SEND")
        self.btn_send.clicked.connect(self.handle_send_click)
        msg_layout.addWidget(self.input_msg); msg_layout.addWidget(self.btn_send)
        layout.addLayout(msg_layout)

    # --- PUENTES ASÍNCRONOS ---
    def handle_connect_click(self):
        # Envolvemos la corrutina en una tarea para evitar el conflicto de runtime
        asyncio.create_task(self.start_connection())

    def handle_send_click(self):
        asyncio.create_task(self.send_message())

    async def start_connection(self):
        ip = self.input_ip.text()
        port = int(self.input_port.text())
        if await self.engine.connect(ip, port):
            self.lbl_status.setText("ONLINE")
            self.lbl_status.setStyleSheet("color: #00ff88;")
            saludo = {"hostName": self.host_name, "contenido": "Python Link Established"}
            await self.engine.send("MESSAGE", saludo)

    async def send_message(self):
        txt = self.input_msg.text()
        if txt and self.engine.is_running:
            await self.engine.send("MESSAGE", {"contenido": txt})
            self.add_log(f"YO: {txt}", "#00d1ff")
            self.input_msg.clear()

    async def dispatch(self, type_name, data):
        if type_name == "MESSAGE":
            self.add_log(f"REMOTE: {data.get('contenido')}", "#00ff88")
        elif type_name == "UPDATE":
            self.add_log(f"SISTEMA: Lista de hosts actualizada", "#ffaa00")

    def add_log(self, text, color):
        row = self.log_table.rowCount()
        self.log_table.insertRow(row)
        item = QTableWidgetItem(text)
        item.setForeground(QColor(color))
        self.log_table.setItem(row, 0, item)
        self.log_table.scrollToBottom()

# --- BUCLE DE EVENTOS HÍBRIDO (SIN QASYNC) ---
async def run_app():
    app = QApplication.instance() or QApplication(sys.argv)
    window = BitBridgeFancyClient()
    window.show()

    while True:
        app.processEvents() # Procesa eventos de Qt
        await asyncio.sleep(0.01) # Cede el control a asyncio

if __name__ == "__main__":
    try:
        asyncio.run(run_app())
    except KeyboardInterrupt:
        pass