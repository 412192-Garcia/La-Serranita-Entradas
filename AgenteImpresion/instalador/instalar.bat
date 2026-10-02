@echo off
rem Instala o actualiza el agente de impresion. Doble clic y contestar las preguntas.
net session >nul 2>&1
if %errorlevel% neq 0 (
  rem Sin permisos de administrador: se vuelve a lanzar pidiendolos.
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"
echo.
pause
