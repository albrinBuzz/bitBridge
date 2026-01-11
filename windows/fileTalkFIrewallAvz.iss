#define MyAppName "FileTalk"
#define MyAppVersion "1.0"
#define MyAppPublisher "FileTalk P2P"
#define MyAppURL "https://tuweb.com"
#define MyAppExeName "FileTalk.exe"

[Setup]
AppId={{B2D1F9E3-4D9A-4E1B-9A7C-C0E5A1D2F3B4}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
DefaultDirName={userappdata}\{#MyAppName}
DefaultGroupName={#MyAppName}
PrivilegesRequired=admin
; ^ Nota: Se requiere 'admin' para modificar reglas de Firewall de forma avanzada
OutputDir=C:\Users\roman\Desktop\salidafileTalks\installer
OutputBaseFilename=FileTalkSetup_v{#MyAppVersion}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern

; --- ESTÉTICA ---
; Descomenta solo si los archivos existen para evitar errores de compilación
; WizardImageFile=C:\Users\roman\Desktop\salidafileTalks\installer_sidebar.bmp
; WizardSmallImageFile=C:\Users\roman\Desktop\salidafileTalks\installer_logo.bmp
; SetupIconFile=C:\Users\roman\Desktop\salidafileTalks\app_icon.ico

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "firewall_pro"; Description: "Configuración Avanzada de Red (Recomendado para P2P)"; GroupDescription: "Seguridad y Conectividad:";

[Files]
Source: "C:\Users\roman\Desktop\salidafileTalks\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\Users\roman\Desktop\salidafileTalks\*.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\Users\roman\Desktop\salidafileTalks\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{userdesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
; --- OPTIMIZACIÓN AVANZADA DEL FIREWALL ---
; 1. Regla para TCP (Transferencia de archivos y Control)
Filename: "netsh"; \
    Parameters: "advfirewall firewall add rule name=""FileTalk (TCP-In)"" dir=in action=allow protocol=TCP program=""{app}\{#MyAppExeName}"" profile=private,public enable=yes description=""Permite transferencia de datos P2P vía TCP"""; \
    Flags: runhidden; Tasks: firewall_pro

; 2. Regla para UDP (Descubrimiento de nodos y streaming)
Filename: "netsh"; \
    Parameters: "advfirewall firewall add rule name=""FileTalk (UDP-In)"" dir=in action=allow protocol=UDP program=""{app}\{#MyAppExeName}"" profile=private,public enable=yes description=""Permite descubrimiento de nodos vía UDP"""; \
    Flags: runhidden; Tasks: firewall_pro

; Lanzar al finalizar
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; Limpieza total al desinstalar
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""FileTalk (TCP-In)"""; Flags: runhidden
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""FileTalk (UDP-In)"""; Flags: runhidden

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    if IsTaskSelected('firewall_pro') then
      MsgBox('Configuración de red completada.' #13#10 #13#10 'Se han habilitado los protocolos TCP y UDP para FileTalk.', mbInformation, MB_OK)
    else
      MsgBox('Instalación terminada.' #13#10 #13#10 'Recuerde dar permisos manualmente si el sistema lo solicita.', mbInformation, MB_OK);
  end;
end;