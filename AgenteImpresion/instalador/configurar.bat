@echo off
rem Cambia la configuracion del agente ya instalado (token, IPs, ticketeras, red o USB) sin
rem reinstalar. Ofrece los valores actuales: Enter deja cada uno como esta.
net session >nul 2>&1
if %errorlevel% neq 0 (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"
echo.
pause
