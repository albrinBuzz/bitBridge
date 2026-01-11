#define MyAppName "FileTalk Suite"
#define MyAppVersion "1.0"
#define MyAppPublisher "FileTalk Project"

[Setup]
AppId={{A58B5142-F3FF-4CA5-B0F0-9AE39FD15272}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
OutputDir=target\installer
OutputBaseFilename=FileTalk_Full_Setup
Compression=lzma
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon_server"; Description: "Crear acceso directo del SERVIDOR (QR)"; GroupDescription: "{cm:AdditionalIcons}"
Name: "desktopicon_client"; Description: "Crear acceso directo del CLIENTE (Desktop)"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; Copiamos ambos JARs
Source: "target\FileTalkServer.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\filetalk-client-jar-with-dependencies.jar"; DestDir: "{app}"; Flags: ignoreversion
; Copiamos el JRE portable para que no necesiten instalar Java
Source: "jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
; Acceso directo para el SERVIDOR
Name: "{group}\FileTalk Server"; Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\FileTalkServer.jar"""; IconFilename: "{app}\server_icon.ico"
Name: "{autodesktop}\FileTalk Server"; Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\FileTalkServer.jar"""; Tasks: desktopicon_server

; Acceso directo para el CLIENTE Desktop (JavaFX)
Name: "{group}\FileTalk Desktop"; Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\filetalk-client-jar-with-dependencies.jar"""; IconFilename: "{app}\client_icon.ico"
Name: "{autodesktop}\FileTalk Desktop"; Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\filetalk-client-jar-with-dependencies.jar"""; Tasks: desktopicon_client

[Run]
; Al finalizar, por defecto sugerimos abrir el servidor
Filename: "{app}\jre\bin\javaw.exe"; Parameters: "-jar ""{app}\FileTalkServer.jar"""; Description: "Iniciar Servidor FileTalk ahora"; Flags: nowait postinstall skipifsilent