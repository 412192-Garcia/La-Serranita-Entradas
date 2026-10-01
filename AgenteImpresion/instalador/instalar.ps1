# Instalador del agente de impresion de La Serranita.
#
# Lo lanza instalar.bat (que ya pide permisos de administrador). Pregunta los datos, los prueba,
# copia el agente a C:\AgenteImpresion y lo registra como tarea programada de Windows: arranca
# solo al prender la PC (sin que nadie inicie sesion) y, si se cae, Windows lo vuelve a levantar.
#
# Volver a correrlo sirve para cambiar la configuracion o actualizar a una version nueva.

$ErrorActionPreference = 'Stop'
$nombreTarea = 'Agente de impresion La Serranita'
$origen = $PSScriptRoot
$destino = 'C:\AgenteImpresion'
$archivoConfig = Join-Path $destino 'agente.properties'

function Preguntar($texto, $porDefecto) {
    $sufijo = if ($porDefecto) { " [$porDefecto]" } else { '' }
    while ($true) {
        $valor = Read-Host "$texto$sufijo"
        if ([string]::IsNullOrWhiteSpace($valor)) { $valor = $porDefecto }
        if (-not [string]::IsNullOrWhiteSpace($valor)) { return $valor.Trim() }
        Write-Host '  Este dato es obligatorio.' -ForegroundColor Yellow
    }
}

function LeerConfigExistente {
    $config = @{}
    if (Test-Path $archivoConfig) {
        foreach ($linea in Get-Content $archivoConfig -Encoding UTF8) {
            if ($linea -match '^\s*([^#][^=]*)=(.*)$') { $config[$Matches[1].Trim()] = $Matches[2].Trim() }
        }
    }
    return $config
}

Write-Host ''
Write-Host '=== Agente de impresion - La Serranita ===' -ForegroundColor Green
Write-Host 'Imprime las facturas de la boleteria en la ticketera. Enter deja el valor entre corchetes.'
Write-Host ''

# Si ya estaba instalado, se ofrecen los valores actuales: reinstalar = actualizar sin retipear.
$actual = LeerConfigExistente
$backend = Preguntar 'Direccion del backend (termina en /api)' $(if ($actual['backend.url']) { $actual['backend.url'] } else { 'https://' })
$backend = $backend.TrimEnd('/')
$token = Preguntar 'Token del agente (IMPRESION_AGENTE_TOKEN del servidor)' $actual['agente.token']
$nombreImpresora = Preguntar 'Nombre de la ticketera (el que se ve en la tablet)' $(if ($actual['impresora.1.nombre']) { $actual['impresora.1.nombre'] } else { 'Boleteria' })
$ipImpresora = Preguntar 'IP de la ticketera en la red del parque' $actual['impresora.1.host']
$puerto = Preguntar 'Puerto de la ticketera' $(if ($actual['impresora.1.puerto']) { $actual['impresora.1.puerto'] } else { '9100' })

Write-Host ''
Write-Host 'Probando conexiones...'

# 1) La ticketera: conexion TCP al puerto RAW.
$tcp = New-Object System.Net.Sockets.TcpClient
try {
    $intento = $tcp.BeginConnect($ipImpresora, [int]$puerto, $null, $null)
    if ($intento.AsyncWaitHandle.WaitOne(3000) -and $tcp.Connected) {
        Write-Host "  [OK] La ticketera responde en ${ipImpresora}:$puerto" -ForegroundColor Green
    } else {
        Write-Host "  [!] No responde la ticketera en ${ipImpresora}:$puerto. Revisar que este prendida, en la misma red y con esa IP." -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [!] No se pudo conectar a la ticketera: $($_.Exception.Message)" -ForegroundColor Yellow
} finally { $tcp.Close() }

# 2) El backend (el token se valida al arrancar el agente, mas abajo).
try {
    Invoke-WebRequest -Uri "$backend/ping" -UseBasicParsing -TimeoutSec 10 | Out-Null
    Write-Host "  [OK] El backend responde" -ForegroundColor Green
} catch {
    Write-Host "  [!] No se pudo contactar al backend en $backend ($($_.Exception.Message)). Revisar la direccion e internet." -ForegroundColor Yellow
}

Write-Host ''
$seguir = Read-Host 'Instalar con estos datos? (S/n)'
if ($seguir -and $seguir.Trim().ToLower() -eq 'n') { Write-Host 'Cancelado.'; exit 1 }

# Si ya estaba corriendo, se frena para poder pisar los archivos.
if (Get-ScheduledTask -TaskName $nombreTarea -ErrorAction SilentlyContinue) {
    Stop-ScheduledTask -TaskName $nombreTarea -ErrorAction SilentlyContinue
    Get-Process AgenteImpresion -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 1
}

New-Item -ItemType Directory -Force $destino | Out-Null
if ((Resolve-Path $origen).Path -ne (Resolve-Path $destino).Path) {
    Copy-Item -Path (Join-Path $origen 'AgenteImpresion\*') -Destination $destino -Recurse -Force
    Copy-Item -Path (Join-Path $origen 'desinstalar.bat') -Destination $destino -Force -ErrorAction SilentlyContinue
}

$contenido = @"
# Generado por el instalador. Para cambiar algo, volver a correr instalar.bat.
backend.url=$backend
agente.token=$token
agente.nombre=$env:COMPUTERNAME
impresora.1.nombre=$nombreImpresora
impresora.1.host=$ipImpresora
impresora.1.puerto=$puerto
"@
# UTF-8 sin BOM: el agente lo lee como UTF-8 (nombres con tilde).
[System.IO.File]::WriteAllText($archivoConfig, $contenido, (New-Object System.Text.UTF8Encoding $false))

$accion = New-ScheduledTaskAction -Execute (Join-Path $destino 'AgenteImpresion.exe') -WorkingDirectory $destino
$disparador = New-ScheduledTaskTrigger -AtStartup
$usuario = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$ajustes = New-ScheduledTaskSettingsSet `
    -RestartCount 9999 -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable
Register-ScheduledTask -TaskName $nombreTarea -Action $accion -Trigger $disparador -Principal $usuario -Settings $ajustes -Force | Out-Null
Start-ScheduledTask -TaskName $nombreTarea

# Se espera a ver en el log si conecto, para avisar aca mismo y no dejar al instalador adivinando.
Write-Host ''
Write-Host 'Arrancando el agente...'
$log = Join-Path $destino 'agente0.log'
$resultado = $null
for ($i = 0; $i -lt 20 -and -not $resultado; $i++) {
    Start-Sleep -Seconds 1
    if (Test-Path $log) {
        $texto = Get-Content $log -Raw -Encoding UTF8
        if ($texto -match 'Conectado al backend') { $resultado = 'ok' }
        elseif ($texto -match 'rechaz') { $resultado = 'token' }
    }
}
Write-Host ''
switch ($resultado) {
    'ok'    { Write-Host 'Listo: el agente esta conectado. En la tablet ya deberia habilitarse "Imprimir factura".' -ForegroundColor Green }
    'token' { Write-Host 'El backend rechazo el token. Volver a correr instalar.bat con el token correcto.' -ForegroundColor Red }
    default { Write-Host "Instalado, pero todavia no conecto. Revisar $log" -ForegroundColor Yellow }
}
Write-Host "Instalado en $destino. Arranca solo con Windows."
