@echo off
rem Saca el agente de impresion: frena la tarea programada y la borra.
net session >nul 2>&1
if %errorlevel% neq 0 (
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell -NoProfile -Command "Stop-ScheduledTask -TaskName 'Agente de impresion La Serranita' -ErrorAction SilentlyContinue; Unregister-ScheduledTask -TaskName 'Agente de impresion La Serranita' -Confirm:$false -ErrorAction SilentlyContinue; Get-Process AgenteImpresion -ErrorAction SilentlyContinue | Stop-Process -Force"
echo Agente desinstalado. Se puede borrar la carpeta C:\AgenteImpresion.
pause
