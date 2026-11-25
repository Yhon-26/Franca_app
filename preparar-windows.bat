@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0preparar-windows.ps1"
if errorlevel 1 (
 echo ERROR: no se pudo preparar el wrapper de Gradle.
 pause
 exit /b 1
)
echo Puedes abrir la carpeta en Android Studio.
pause
