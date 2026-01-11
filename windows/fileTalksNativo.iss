#define MyAppName "FileTalk"
#define MyAppVersion "1.0"
#define MyAppExeName "FileTalk.exe"

[Setup]
AppId={{B2D1F9E3-4D9A-4E1B-9A7C-C0E5A1D2F3B4}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
DefaultDirName={userappdata}\{#MyAppName}
PrivilegesRequired=lowest
OutputDir=C:\Users\roman\Desktop\salidafileTalks\installer
OutputBaseFilename=FileTalkSetup
Compression=lzma
SolidCompression=yes
WizardStyle=modern

; --- NUEVA SECCIÓN PARA TAREAS ADICIONALES ---
[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "C:\Users\roman\Desktop\salidafileTalks\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\Users\roman\Desktop\salidafileTalks\*.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\Users\roman\Desktop\salidafileTalks\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
; Acceso directo en el Menú Inicio
Name: "{userprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"

; --- NUEVO ACCESO DIRECTO EN EL ESCRITORIO ---
Name: "{userdesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent