#define MyAppName "Dhilip Home"
#define MyAppVersion "0.3.3"
#define MyAppExeName "DhilipHome.exe"

[Setup]
AppId={{5C1C1A1E-0E89-4D92-A3F2-DHILIPHOME031}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
DefaultDirName={autopf}\Dhilip Home
DefaultGroupName={#MyAppName}
OutputDir=..\release
OutputBaseFilename=DhilipHome-Setup-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin
UninstallDisplayName={#MyAppName}

[Files]
Source: "..\dist\DhilipHome.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent
