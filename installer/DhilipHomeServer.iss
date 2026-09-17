#define MyAppName "Dhilip Home Server"
#define MyAppVersion "0.3.1"
#define MyAppPublisher "Dhilip Home"
#define MyAppExeName "DhilipHomeServer.exe"

[Setup]
AppId={{D4B3A8C1-8A2E-4C90-9A71-DHILIPHOME031}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\Dhilip Home Server
DefaultGroupName={#MyAppName}
OutputDir=..\release
OutputBaseFilename=DhilipHomeServer-Setup-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
UninstallDisplayName={#MyAppName}

[Files]
Source: "..\dhilip-home-server-main\dist\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent
