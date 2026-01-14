#define MyAppName "BitBridge"
#define MyAppVersion "1.0"
#define MyAppPublisher "BitBridge P2P"
#define MyAppURL "https://tuweb.com"
#define MyAppExeName "BitBridge.exe"

[Setup]
; --- INFORMACIÓN BÁSICA ---
AppId={{B2D1F9E3-4D9A-4E1B-9A7C-C0E5A1D2F3B4}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
DefaultDirName={userappdata}\{#MyAppName}
DefaultGroupName={#MyAppName}
PrivilegesRequired=lowest

; --- ESTÉTICA FANCY ---
; Necesitas crear/tener estas imágenes en la carpeta de origen
WizardStyle=modern
; Imagen lateral (164x314 px)
WizardImageFile=C:\Users\roman\Desktop\salidafileTalks\installer_sidebar.bmp
; Logo pequeño arriba a la derecha (55x55 px)
WizardSmallImageFile=C:\Users\roman\Desktop\salidafileTalks\installer_logo.bmp
; Icono del instalador (.ico)
SetupIconFile=C:\Users\roman\Desktop\salidafileTalks\app_icon.ico

; --- SALIDA ---
OutputDir=C:\Users\roman\Desktop\salidafileTalks\installer
OutputBaseFilename=BitBridgeSetup_v{#MyAppVersion}
Compression=lzma2/ultra64
SolidCompression=yes

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "firewall"; Description: "Agregar regla al Firewall de Windows (Recomendado)"; GroupDescription: "Seguridad:";

[Files]
Source: "C:\Users\roman\Desktop\salidafileTalks\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\Users\roman\Desktop\salidafileTalks\*.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\Users\roman\Desktop\salidafileTalks\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{userdesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
; --- FIREWALL: Crucial para que el P2P no falle ---
Filename: "netsh"; Parameters: "advfirewall firewall add rule name=""FileTalk"" dir=in action=allow program=""{app}\{#MyAppExeName}"" enable=yes"; \
    Flags: runhidden; Tasks: firewall

; Lanzar al finalizar
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; Limpiar firewall al desinstalar
Filename: "netsh"; Parameters: "advfirewall firewall delete rule name=""FileTalk"""; Flags: runhidden

[Code]
// --- EFECTO FANCY: Saludo al iniciar ---
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    MsgBox('¡FileTalk se ha instalado con éxito!' #13#10 #13#10 'Ya puedes empezar a enviar archivos de forma segura.', mbInformation, MB_OK);
  end;
end;