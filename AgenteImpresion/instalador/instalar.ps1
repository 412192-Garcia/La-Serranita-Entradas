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
    $ip = Preguntar '  IP en la red del parque' $actual["impresora.$n.host"]
    $puerto = Preguntar '  Puerto' $(if ($actual["impresora.$n.puerto"]) { $actual["impresora.$n.puerto"] } else { '9100' })
    $impresoras += [pscustomobject]@{ Nombre = $nombre; Host = $ip; Puerto = $puerto }

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

# 1) Cada ticketera: conexion TCP al puerto RAW.
foreach ($imp in $impresoras) {
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
    Copy-Item -Path (Join-Path $origen 'desinstalar.bat') -Destination $destino -Force -ErrorAction SilentlyContinue
}

$lineas = @(
    '# Generado por el instalador. Para cambiar algo, volver a correr instalar.bat.'
    "backend.url=$backend"
    "agente.token=$token"
    "agente.nombre=$env:COMPUTERNAME"
)
for ($i = 0; $i -lt $impresoras.Count; $i++) {
    $lineas += "impresora.$($i + 1).nombre=$($impresoras[$i].Nombre)"
    $lineas += "impresora.$($i + 1).host=$($impresoras[$i].Host)"
    $lineas += "impresora.$($i + 1).puerto=$($impresoras[$i].Puerto)"
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
