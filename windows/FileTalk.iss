#define MyAppName "FileTalk"
#define MyAppVersion "1.0"
#define MyAppPublisher "FileTalk Project"
#define MyAppExeName "jre\bin\javaw.exe"
#define MyJarName "FileTalk-1.0-SNAPSHOT-fullserver.jar"

[Setup]
AppId={{B2D1F9E3-4D9A-4E1B-9A7C-C0E5A1D2F3B4}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
; CAMBIO CLAVE: Se instala en AppData del usuario, no en Program Files
DefaultDirName={userappdata}\{#MyAppName}
DisableProgramGroupPage=yes
; CAMBIO CLAVE: No solicita privilegios administrativos
PrivilegesRequired=lowest
OutputDir=C:\Users\roman\Desktop\salidafileTalks\installer
OutputBaseFilename=FileTalkUserSetup
Compression=lzma
SolidCompression=yes
WizardStyle=modern

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "C:\Users\roman\Desktop\salidafileTalks\{#MyJarName}"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\Users\roman\Desktop\salidafileTalks\jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{userprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; \
    Parameters: "-Djoinfaces.jsf.classes-scan-packages=org.primefaces,jakarta.faces,com.sun.faces -jar ""{app}\{#MyJarName}"""

Name: "{userdesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; \
    Parameters: "-Djoinfaces.jsf.classes-scan-packages=org.primefaces,jakarta.faces,com.sun.faces -jar ""{app}\{#MyJarName}"""; \
    Tasks: desktopicon

[Run]
; NOTA: Las reglas de firewall suelen requerir admin. Si el usuario no es admin,
; estas líneas podrían fallar silenciosamente. El usuario deberá dar permiso al abrir la app.
Filename: "{app}\{#MyAppExeName}"; \
    Parameters: "-Djoinfaces.jsf.classes-scan-packages=org.primefaces,jakarta.faces,com.sun.faces -jar ""{app}\{#MyJarName}"""; \
    Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; \
    Flags: nowait postinstall skipifsilent