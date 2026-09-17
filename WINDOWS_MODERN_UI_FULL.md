# Dhilip Home — Modern Windows Application

This version packages the existing DhilipHome server and a modern desktop UI into one Windows application.

The UI contains Home, Files, Media, Downloads, Cloud and Settings.
Files and media are intended to remain inside the application; the server API remains the source of truth.

The build creates:
- DhilipHome.exe — standalone GUI + in-process server
- DhilipHome-Setup-0.3.1.exe — normal Windows installer

No separate Python installation is required on the target machine.

NOTE: The UI is now connected to the existing API endpoints. Authentication credentials are required for protected endpoints.
