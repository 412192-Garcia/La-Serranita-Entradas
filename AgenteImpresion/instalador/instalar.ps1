# Instalador y configurador del agente de impresion de La Serranita.
#
# - instalar.bat (desde el zip): pregunta los datos, los prueba, copia el agente a
#   C:\AgenteImpresion y lo registra como tarea programada de Windows (arranca solo al prender
#   la PC, sin que nadie inicie sesion, y si se cae Windows lo vuelve a levantar). Correrlo con
#   una version nueva = actualizar.
# - configurar.bat (en C:\AgenteImpresion): lo mismo pero sin copiar nada. Ofrece los valores
#   actuales (Enter los deja), guarda y reinicia el agente. Para cambiar el token, una IP, pasar
#   una ticketera de red a USB, agregar o sacar una.

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
            # .properties escapa la barra invertida: se deshace para mostrar y reescribir el valor real.
            if ($linea -match '^\s*([^#][^=]*)=(.*)$') { $config[$Matches[1].Trim()] = $Matches[2].Trim().Replace('\\', '\') }
        }
    }
    return $config
}

$soloConfigurar = (Test-Path (Join-Path $origen 'AgenteImpresion.exe')) -and -not (Test-Path (Join-Path $origen 'AgenteImpresion'))
Write-Host ''
if ($soloConfigurar) {
    Write-Host '=== Configuracion del agente de impresion - La Serranita ===' -ForegroundColor Green
} else {
    Write-Host '=== Agente de impresion - La Serranita ===' -ForegroundColor Green
}
Write-Host 'Imprime las facturas de la boleteria en la ticketera. Enter deja el valor entre corchetes.'
Write-Host ''

# Si ya estaba instalado, se ofrecen los valores actuales: reinstalar = actualizar sin retipear.
$actual = LeerConfigExistente
$backend = Preguntar 'Direccion del backend (termina en /api)' $(if ($actual['backend.url']) { $actual['backend.url'] } else { 'https://' })
$backend = $backend.TrimEnd('/')

# Token: es una clave que se inventa una vez y va igual en el .env del servidor y aca. Vacio =
# se genera una al azar. Una corta ("hola") se adivina enseguida y cualquiera podria hacerse
# pasar por este agente, por eso se exigen al menos 16 caracteres.
$tokenGenerado = $false
while ($true) {
    $sufijo = if ($actual['agente.token']) { ' [Enter = dejar el actual]' } else { ' [Enter = generar uno nuevo]' }
    $token = Read-Host "Token del agente (IMPRESION_AGENTE_TOKEN del servidor)$sufijo"
    if ([string]::IsNullOrWhiteSpace($token)) {
        if ($actual['agente.token']) { $token = $actual['agente.token']; break }
        $bytes = New-Object byte[] 32
        [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
        $token = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
        $tokenGenerado = $true
        break
    }
    $token = $token.Trim()
    if ($token.Length -ge 16) { break }
    Write-Host '  Muy corto: usa al menos 16 caracteres, o Enter para generar uno.' -ForegroundColor Yellow
}
if ($tokenGenerado) {
    Write-Host ''
    Write-Host '  Token generado. Copialo en el .env del servidor y reinicia el backend:' -ForegroundColor Cyan
    Write-Host ''
    Write-Host "    IMPRESION_AGENTE_TOKEN=$token" -ForegroundColor White
    Write-Host ''
    try { Set-Clipboard -Value "IMPRESION_AGENTE_TOKEN=$token"; Write-Host '  (ya quedo copiado al portapapeles)' } catch {}
    Write-Host ''
}

# Ticketeras: una o varias, todas manejadas por este mismo agente. En la tablet aparece un
# selector para elegir en cual imprime cada una.
$impresoras = @()
$n = 1
while ($true) {
    Write-Host ''
    Write-Host "Ticketera $n" -ForegroundColor Green
    $nombrePorDefecto = if ($actual["impresora.$n.nombre"]) { $actual["impresora.$n.nombre"] } elseif ($n -eq 1) { 'Boleteria' } else { "Boleteria $n" }
    while ($true) {
        $nombre = Preguntar '  Nombre (el que se ve en la tablet)' $nombrePorDefecto
        if ($nombre -match '[;,]') { Write-Host '  El nombre no puede tener ; ni ,' -ForegroundColor Yellow; continue }
        if ($impresoras | Where-Object { $_.Nombre -eq $nombre }) { Write-Host '  Ya hay una ticketera con ese nombre.' -ForegroundColor Yellow; continue }
        break
    }

    # Como esta conectada: por red (IP) o instalada en Windows (USB, o compartida desde otra PC).
    $tipoActual = if ($actual["impresora.$n.tipo"] -eq 'windows') { '2' } else { '1' }
    Write-Host '  Conexion:  1) Red (tiene IP propia)   2) USB / instalada en Windows'
    while ($true) {
        $tipo = Preguntar '  Opcion' $tipoActual
        if ($tipo -in @('1', '2')) { break }
        Write-Host '  Elegi 1 o 2.' -ForegroundColor Yellow
    }

    if ($tipo -eq '1') {
        $ip = Preguntar '  IP en la red del parque' $actual["impresora.$n.host"]
        $puerto = Preguntar '  Puerto' $(if ($actual["impresora.$n.puerto"]) { $actual["impresora.$n.puerto"] } else { '9100' })
        $impresoras += [pscustomobject]@{ Nombre = $nombre; Tipo = 'red'; Host = $ip; Puerto = $puerto; Windows = $null }
    } else {
        # Las impresoras que Windows tiene instaladas: se elige por numero para no tipear el nombre.
        $instaladas = @(Get-Printer -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name | Sort-Object)
        if ($instaladas.Count -eq 0) {
            Write-Host '  Windows no tiene impresoras instaladas. Instala la ticketera (con su driver o con "Generic / Text Only") y volve a correr esto.' -ForegroundColor Red
            exit 1
        }
        for ($i = 0; $i -lt $instaladas.Count; $i++) { Write-Host ("    {0}) {1}" -f ($i + 1), $instaladas[$i]) }
        $porDefecto = $null
        if ($actual["impresora.$n.windows"]) {
            $pos = [array]::IndexOf($instaladas, $actual["impresora.$n.windows"])
            if ($pos -ge 0) { $porDefecto = [string]($pos + 1) }
        }
        while ($true) {
            $eleccion = Preguntar '  Numero de la impresora' $porDefecto
            if ($eleccion -match '^\d+$' -and [int]$eleccion -ge 1 -and [int]$eleccion -le $instaladas.Count) { break }
            Write-Host "  Elegi un numero entre 1 y $($instaladas.Count)." -ForegroundColor Yellow
        }
        $impresoras += [pscustomobject]@{ Nombre = $nombre; Tipo = 'windows'; Host = $null; Puerto = $null; Windows = $instaladas[[int]$eleccion - 1] }
    }

    # Si ya habia una siguiente configurada, se ofrece "s" por defecto para no perderla.
    $hayOtra = [bool]$actual["impresora.$($n + 1).nombre"]
    $pregunta = if ($hayOtra) { 'Agregar otra ticketera? (S/n)' } else { 'Agregar otra ticketera? (s/N)' }
    $resp = Read-Host $pregunta
    $otra = if ([string]::IsNullOrWhiteSpace($resp)) { $hayOtra } else { $resp.Trim().ToLower() -eq 's' }
    if (-not $otra) { break }
    $n++
}

Write-Host ''
Write-Host 'Probando conexiones...'

# 1) Cada ticketera: las de red, conexion TCP al puerto RAW; las de Windows, que la impresora
# exista y no este con error.
foreach ($imp in $impresoras) {
    if ($imp.Tipo -eq 'windows') {
        $impWin = Get-Printer -Name $imp.Windows -ErrorAction SilentlyContinue
        if (-not $impWin) {
            Write-Host "  [!] Windows no encuentra la impresora '$($imp.Windows)'." -ForegroundColor Yellow
        } elseif ("$($impWin.PrinterStatus)" -notin @('Normal', '0', 'Idle')) {
            Write-Host "  [!] '$($imp.Nombre)' ($($imp.Windows)) esta en estado '$($impWin.PrinterStatus)'. Revisar que este prendida y conectada." -ForegroundColor Yellow
        } else {
            Write-Host "  [OK] '$($imp.Nombre)' instalada en Windows como '$($imp.Windows)'" -ForegroundColor Green
        }
        continue
    }
    $tcp = New-Object System.Net.Sockets.TcpClient
    try {
        $intento = $tcp.BeginConnect($imp.Host, [int]$imp.Puerto, $null, $null)
        if ($intento.AsyncWaitHandle.WaitOne(3000) -and $tcp.Connected) {
            Write-Host "  [OK] '$($imp.Nombre)' responde en $($imp.Host):$($imp.Puerto)" -ForegroundColor Green
        } else {
            Write-Host "  [!] '$($imp.Nombre)' no responde en $($imp.Host):$($imp.Puerto). Revisar que este prendida, en la misma red y con esa IP." -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  [!] No se pudo conectar a '$($imp.Nombre)': $($_.Exception.Message)" -ForegroundColor Yellow
    } finally { $tcp.Close() }
}

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
    foreach ($archivo in @('desinstalar.bat', 'configurar.bat', 'instalar.ps1')) {
        Copy-Item -Path (Join-Path $origen $archivo) -Destination $destino -Force -ErrorAction SilentlyContinue
    }
}

$lineas = @(
    '# Generado por el instalador. Para cambiar algo, volver a correr instalar.bat.'
    "backend.url=$backend"
    "agente.token=$token"
    "agente.nombre=$env:COMPUTERNAME"
)
for ($i = 0; $i -lt $impresoras.Count; $i++) {
    $k = $i + 1
    $lineas += "impresora.$k.nombre=$($impresoras[$i].Nombre)"
    $lineas += "impresora.$k.tipo=$($impresoras[$i].Tipo)"
    if ($impresoras[$i].Tipo -eq 'windows') {
        # En .properties la barra invertida escapa: una impresora compartida (\\PC\Ticketera) la necesita doble.
        $lineas += "impresora.$k.windows=$($impresoras[$i].Windows.Replace('\', '\\'))"
    } else {
        $lineas += "impresora.$k.host=$($impresoras[$i].Host)"
        $lineas += "impresora.$k.puerto=$($impresoras[$i].Puerto)"
    }
}
$contenido = ($lineas -join "`r`n") + "`r`n"
# UTF-8 sin BOM: el agente lo lee como UTF-8 (nombres con tilde).
[System.IO.File]::WriteAllText($archivoConfig, $contenido, (New-Object System.Text.UTF8Encoding $false))

# El log se va acumulando: se anota hasta donde llegaba, para mirar despues solo lo que escriba
# esta instalacion (si no, un "Conectado" viejo daria un OK falso).
$log = Join-Path $destino 'agente0.log'
$lineasPrevias = if (Test-Path $log) { (Get-Content $log -Encoding UTF8).Count } else { 0 }

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
$resultado = $null
for ($i = 0; $i -lt 20 -and -not $resultado; $i++) {
    Start-Sleep -Seconds 1
    if (Test-Path $log) {
        $nuevas = (Get-Content $log -Encoding UTF8 | Select-Object -Skip $lineasPrevias) -join "`n"
        if ($nuevas -match 'Conectado al backend') { $resultado = 'ok' }
        elseif ($nuevas -match 'rechaz') { $resultado = 'token' }
    }
}
Write-Host ''
switch ($resultado) {
    'ok'    { Write-Host 'Listo: el agente esta conectado. En la tablet ya deberia habilitarse "Imprimir factura".' -ForegroundColor Green }
    'token' {
        if ($tokenGenerado) {
            Write-Host 'Instalado. Falta poner el token generado en el .env del servidor y reiniciar el backend: el agente se conecta solo apenas coincidan.' -ForegroundColor Yellow
        } else {
            Write-Host 'El backend rechazo el token. Volver a correr instalar.bat con el mismo token que tiene el servidor.' -ForegroundColor Red
        }
    }
    default { Write-Host "Instalado, pero todavia no conecto. Revisar $log" -ForegroundColor Yellow }
}
Write-Host "Instalado en $destino. Arranca solo con Windows."
Write-Host "Para cambiar algo mas adelante: $destino\configurar.bat"
