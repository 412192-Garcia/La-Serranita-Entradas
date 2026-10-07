#!/usr/bin/env bash
# Deploy al VPS desde tu PC, en un solo paso, con lo que está en origin/main:
#
#   1. Espera a que el CI de ese commit termine bien (si no, las imágenes :latest todavía son las
#      viejas y el "pull" no trae nada nuevo).
#   2. En el VPS: backup de la base, copia el docker-compose.yml (y observabilidad/) de ese mismo
#      commit — "docker compose pull" baja imágenes, nunca el compose —, pull, up -d y espera a que
#      el backend quede healthy.
#   3. Muestra el estado de los contenedores y /api/salud.
#
# Uso (Git Bash, desde cualquier carpeta del repo):
#   ./scripts/deploy.sh                 # pregunta antes de deployar
#   ./scripts/deploy.sh -y              # sin preguntar
#   ./scripts/deploy.sh --sin-esperar-ci
#
# La primera vez crea scripts/deploy.local para completar con los datos del VPS (no se sube al
# repo). Conviene entrar al VPS con clave SSH: con contraseña, la pide una sola vez por deploy.
# Si hubo un paso manual antes (un SQL, una variable nueva en el .env), hacelo ANTES de correr esto.
set -euo pipefail

# Nunca terminar en silencio: si algo falla se dice en qué línea, y la ventana espera un Enter
# antes de cerrarse (abierto con doble clic, se cerraba sin dejar leer nada).
PASO="arrancando"
trap 'codigo=$?; echo; echo "!! FALLÓ en: $PASO (línea $LINENO, código $codigo)"' ERR
trap 'codigo=$?; echo; [ "$codigo" = 0 ] || echo "Terminó con errores (código $codigo): revisá los mensajes de arriba."; [ -t 0 ] && read -r -p "Presioná Enter para cerrar..." _ || true' EXIT

cd "$(git rev-parse --show-toplevel)"
CONFIG="scripts/deploy.local"
REPO_API="https://api.github.com/repos/412192-Garcia/La-Serranita-Entradas"

SIN_PREGUNTAR=0
ESPERAR_CI=1
for arg in "$@"; do
  case "$arg" in
    -y|--si) SIN_PREGUNTAR=1 ;;
    --sin-esperar-ci) ESPERAR_CI=0 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Opción desconocida: $arg (ver --help)"; exit 1 ;;
  esac
done

if [ ! -f "$CONFIG" ]; then
  cat > "$CONFIG" <<'EOF'
# Datos del VPS para scripts/deploy.sh. Este archivo NO se sube al repo (.gitignore).
# Usuario y host SSH (ej. root@203.0.113.10 o deploy@mi-vps).
VPS=
# Carpeta del VPS donde están docker-compose.yml y .env.
VPS_RUTA=
# Puerto SSH, si no es el 22.
VPS_PUERTO=22
# Dominio público, para chequear https://DOMINIO/api/salud al final (vacío = no se chequea).
DOMINIO=
# "docker" si tu usuario está en el grupo docker; si no, "sudo docker".
DOCKER=docker
EOF
  echo "Creé $CONFIG: completalo con los datos del VPS y volvé a correr el script."
  exit 1
fi
# shellcheck disable=SC1090
source "$CONFIG"
: "${VPS:?Falta VPS en $CONFIG}" "${VPS_RUTA:?Falta VPS_RUTA en $CONFIG}"
VPS_PUERTO="${VPS_PUERTO:-22}"
DOCKER="${DOCKER:-docker}"
case "$VPS_RUTA" in
  /*) ;;
  *) echo "!! VPS_RUTA tiene que ser una ruta absoluta (empezar con /), ej. /docker/la-serranita-entradas. Corregilo en $CONFIG."; exit 1 ;;
esac
# El dominio solo: si se pegó la URL entera (https://dominio/api/salud), se le saca lo que sobra.
DOMINIO="${DOMINIO:-}"
DOMINIO="${DOMINIO#http://}"; DOMINIO="${DOMINIO#https://}"; DOMINIO="${DOMINIO%%/*}"

# ---------- Qué se deploya ----------

PASO="traer origin/main"
echo "==> Trayendo origin/main..."
git fetch -q origin main
SHA="$(git rev-parse origin/main)"
echo "    Commit a deployar: $(git log -1 --format='%h  %s  (%an, %ar)' "$SHA")"

# ---------- Esperar el CI ----------

if [ "$ESPERAR_CI" = 1 ]; then
  PASO="esperar el CI"
  echo "==> Esperando el CI de ${SHA:0:7} (publica las imágenes que va a bajar el VPS)..."
  inicio=$(date +%s)
  while :; do
    estado="$(curl -fsS "$REPO_API/actions/runs?head_sha=$SHA&per_page=20" 2>/dev/null | python -c '
import sys, json
runs = [r for r in json.load(sys.stdin).get("workflow_runs", []) if r.get("name") == "CI"]
if not runs: print("sin-run")
else:
    r = runs[0]
    print(r["status"] + ":" + (r.get("conclusion") or ""))
' 2>/dev/null || echo "error-api")"
    case "$estado" in
      completed:success) echo "    CI OK."; break ;;
      completed:*) echo "    El CI terminó mal ($estado). No se deploya: revisalo en GitHub > Actions."; exit 1 ;;
      error-api) echo "    No pude consultar GitHub (sin internet o límite de la API). Probá de nuevo o usá --sin-esperar-ci." ; exit 1 ;;
      *) ;;
    esac
    if [ $(( $(date +%s) - inicio )) -gt 1800 ]; then
      echo "    Pasaron 30 minutos y el CI no terminó ($estado). Cortado."; exit 1
    fi
    printf '    %s... (%ss)\r' "$estado" "$(( $(date +%s) - inicio ))"
    sleep 20
  done
fi

# ---------- Confirmar ----------

if [ "$SIN_PREGUNTAR" = 0 ]; then
  read -r -p "==> ¿Deployar ${SHA:0:7} en $VPS:$VPS_RUTA? [s/N] " respuesta
  case "$respuesta" in s|S|si|SI|sí|Sí) ;; *) echo "Cancelado."; exit 0 ;; esac
fi

# ---------- Deploy (una sola conexión SSH) ----------
# Por stdin viaja un tar con el docker-compose.yml y observabilidad/ de ESE commit (no de tu carpeta
# local, que puede tener cambios sin commitear); el resto corre en el VPS.

REMOTO=$(cat <<EOF
set -e
paso="entrar a la carpeta"
trap 'echo "!! [VPS] FALLÓ en: \$paso (código \$?)"' ERR
cd '$VPS_RUTA'
echo "==> [VPS] Backup de la base antes de tocar nada..."
$DOCKER compose exec -T backup backup.sh </dev/null || echo "    AVISO: no se pudo hacer el backup (¿el servicio backup no está corriendo?). Sigo."
paso="copiar docker-compose.yml"
echo "==> [VPS] Copiando docker-compose.yml (el anterior queda como docker-compose.yml.anterior)..."
cp -f docker-compose.yml docker-compose.yml.anterior 2>/dev/null || true
tar -xf -
paso="bajar las imágenes (docker compose pull)"
echo "==> [VPS] Bajando imágenes..."
$DOCKER compose pull
paso="recrear los contenedores (docker compose up -d)"
echo "==> [VPS] Levantando..."
$DOCKER compose up -d
paso="esperar al backend"
echo "==> [VPS] Esperando a que el backend quede healthy (hasta 3 min)..."
estado=""
for i in \$(seq 1 36); do
  estado=\$($DOCKER compose ps backend --format '{{.Health}}' 2>/dev/null || true)
  [ "\$estado" = "healthy" ] && break
  sleep 5
done
$DOCKER compose ps
if [ "\$estado" != "healthy" ]; then
  echo "    AVISO: el backend no quedó healthy (\$estado). Últimas líneas del log:"
  $DOCKER compose logs backend --tail 40
  exit 2
fi
echo "    Backend healthy."
EOF
)

PASO="deploy en el VPS (ssh)"
echo "==> Conectando a $VPS... (si pide contraseña, es la del usuario del VPS)"
set +e
trap - ERR  # el resultado del ssh se explica abajo, caso por caso
# autocrlf=false: si no, en Windows git archive pasa todo a CRLF y el VPS recibe algo distinto de lo que hay en main.
git -c core.autocrlf=false archive --format=tar "$SHA" docker-compose.yml observabilidad \
  | ssh -p "$VPS_PUERTO" "$VPS" "$REMOTO"
resultado=$?
set -e
case "$resultado" in
  0) ;;
  255) echo "!! No se pudo conectar o entrar por SSH a $VPS (contraseña, clave, IP o puerto). No se tocó nada en el VPS." ; exit 255 ;;
  2) echo "!! Los contenedores se levantaron pero el backend no quedó healthy: mirá el log de arriba." ; exit 2 ;;
  *) echo "!! El deploy falló en el VPS (código $resultado): el paso que falló está arriba, marcado con [VPS]." ; exit "$resultado" ;;
esac

# ---------- Verificar desde afuera ----------

if [ -n "${DOMINIO:-}" ]; then
  PASO="chequear /api/salud desde afuera"
  echo "==> Chequeando https://$DOMINIO/api/salud ..."
  if curl -fsS --max-time 15 "https://$DOMINIO/api/salud"; then
    echo
  else
    echo "    AVISO: no respondió bien desde afuera (Caddy, DNS o el backend)."; exit 3
  fi
fi

echo "==> Deploy de ${SHA:0:7} terminado."
echo "    Si entrás a la app y se ve vieja o se cuelga: Ctrl+Shift+R (caché del service worker)."
echo "    Para volver atrás el compose: ssh al VPS y 'cp docker-compose.yml.anterior docker-compose.yml && docker compose up -d'."
