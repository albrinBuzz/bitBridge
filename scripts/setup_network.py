import os
import platform
import subprocess
import sys

def mostrar_mensaje_nativo(titulo, mensaje, tipo="info"):
    sistema = platform.system().lower()

    if sistema == "linux":
        # Intentamos con zenity (estándar en GNOME/Fedora) o kdialog (KDE)
        if subprocess.run(["which", "zenity"], capture_output=True).returncode == 0:
            subprocess.run(["zenity", f"--{tipo}", "--title", titulo, "--text", mensaje, "--width=300"])
        elif subprocess.run(["which", "kdialog"], capture_output=True).returncode == 0:
            subprocess.run(["kdialog", "--title", titulo, "--msgbox", mensaje])

    elif sistema == "windows":
        # Usamos un pequeño comando de PowerShell para mostrar una caja nativa de Windows
        icono = "Information" if tipo == "info" else "Error"
        ps_cmd = f"Add-Type -AssemblyName PresentationFramework; [System.Windows.MessageBox]::Show('{mensaje}', '{titulo}', 'OK', '{icono}')"
        subprocess.run(["powershell", "-Command", ps_cmd])

def run_config_red():
    sistema = platform.system().lower()

    try:
        if sistema == "linux":
            # pkexec lanza el diálogo de contraseña NATIVO de Linux
            cmd = (
                "firewall-cmd --add-port=8080-8085/tcp --add-port=45000-45100/tcp "
                "--add-service=mdns --permanent && firewall-cmd --reload"
            )
            subprocess.run(["pkexec", "sh", "-c", cmd], check=True)
            mostrar_mensaje_nativo("BitBridge", "Configuración de red completada con éxito.")

        elif sistema == "windows":
            import ctypes
            if ctypes.windll.shell32.IsUserAnAdmin():
                cmd = 'netsh advfirewall firewall add rule name="BitBridge_In" dir=in action=allow protocol=TCP localport=8080-8085,45000-45100'
                subprocess.run(["cmd.exe", "/c", cmd], check=True)
                mostrar_mensaje_nativo("BitBridge", "Reglas de firewall añadidas correctamente.")
            else:
                # El "runas" lanza el escudo NATIVO de Windows (UAC)
                ctypes.windll.shell32.ShellExecuteW(None, "runas", sys.executable, __file__, None, 1)

    except subprocess.CalledProcessError:
        # Si el usuario cancela el diálogo de contraseña
        mostrar_mensaje_nativo("BitBridge", "La operación fue cancelada o denegada.", "error")
    except Exception as e:
        mostrar_mensaje_nativo("BitBridge Error", str(e), "error")

if __name__ == "__main__":
    run_config_red()