# Registra el agente de impresión como tarea programada de Windows: arranca solo al prender la
# PC (sin que nadie inicie sesión) y, si se cae, Windows lo vuelve a levantar cada minuto.
#
# Uso (PowerShell como administrador, parado en la carpeta del agente):
#   powershell -ExecutionPolicy Bypass -File .\instalar-tarea.ps1
#
# Para sacarlo:  Unregister-ScheduledTask -TaskName "Agente de impresion La Serranita" -Confirm:$false

$ErrorActionPreference = 'Stop'
$nombreTarea = 'Agente de impresion La Serranita'
$carpeta = $PSScriptRoot

if (-not (Test-Path (Join-Path $carpeta 'agente.properties'))) {
    throw "Falta agente.properties en $carpeta (copiar agente.properties.example y completarlo)."
}

# Empaquetado con jpackage trae su propio Java (AgenteImpresion.exe). Si no, se usa el .jar con
# el Java instalado.
$exe = Join-Path $carpeta 'AgenteImpresion.exe'
if (Test-Path $exe) {
    $accion = New-ScheduledTaskAction -Execute $exe -WorkingDirectory $carpeta
} else {
    $jar = Join-Path $carpeta 'agente-impresion.jar'
    if (-not (Test-Path $jar)) { throw "No encuentro AgenteImpresion.exe ni agente-impresion.jar en $carpeta" }
    $java = (Get-Command javaw.exe -ErrorAction Stop).Source
    $accion = New-ScheduledTaskAction -Execute $java -Argument "-jar `"$jar`" `"$(Join-Path $carpeta 'agente.properties')`"" -WorkingDirectory $carpeta
}

$disparador = New-ScheduledTaskTrigger -AtStartup
# Corre como SYSTEM: no depende de que alguien inicie sesión en la PC.
$usuario = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$ajustes = New-ScheduledTaskSettingsSet `
    -RestartCount 9999 -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

Register-ScheduledTask -TaskName $nombreTarea -Action $accion -Trigger $disparador -Principal $usuario -Settings $ajustes -Force | Out-Null
Start-ScheduledTask -TaskName $nombreTarea
Write-Host "Listo: '$nombreTarea' instalada y arrancada. Log en $carpeta\agente0.log"
