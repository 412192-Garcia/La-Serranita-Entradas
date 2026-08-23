/**
 * Cartel de último recurso, fuera del árbol de componentes de Angular (se cuelga directo de
 * `document.body` con estilos inline, sin depender de las variables CSS del tema ni de que
 * Angular esté renderizando bien): lo usan el ErrorHandler global y el interceptor de auth,
 * dos lugares donde el estado de la app puede estar roto o a punto de navegar, así que no
 * conviene depender de un componente Angular para mostrar el aviso.
 */

const ID_CONTENEDOR = 'aviso-global-contenedor';
const DURACION_AUTODESCARTE_MS = 6000;

export interface AccionAviso {
  etiqueta: string;
  onClick: () => void;
}

/** Sin accion, el aviso se descarta solo a los pocos segundos; con accion, el usuario lo cierra
 * al tocar el botón (ej. "Recargar"). Si el mismo mensaje ya está en pantalla, no lo duplica —
 * evita empapelar la pantalla si el error que lo dispara se repite en bucle. */
export function mostrarAvisoGlobal(mensaje: string, accion?: AccionAviso): void {
  try {
    const contenedor = obtenerOCrearContenedor();
    if (contenedor.querySelector(`[data-mensaje="${CSS.escape(mensaje)}"]`)) return;

    const aviso = document.createElement('div');
    aviso.dataset['mensaje'] = mensaje;
    aviso.style.cssText =
      'pointer-events:auto;background:#3a1414;color:#fff;border:1px solid #7a2020;' +
      'border-radius:8px;padding:10px 14px;font:14px/1.4 system-ui,sans-serif;' +
      'box-shadow:0 4px 16px rgba(0,0,0,.3);max-width:90vw;display:flex;gap:12px;align-items:center;';

    const texto = document.createElement('span');
    texto.textContent = mensaje;
    aviso.appendChild(texto);

    if (accion) {
      const boton = document.createElement('button');
      boton.type = 'button';
      boton.textContent = accion.etiqueta;
      boton.style.cssText =
        'background:#fff;color:#3a1414;border:none;border-radius:6px;padding:6px 10px;' +
        'font-weight:700;cursor:pointer;flex-shrink:0;font:inherit;';
      boton.addEventListener('click', () => {
        aviso.remove();
        accion.onClick();
      });
      aviso.appendChild(boton);
    }

    contenedor.appendChild(aviso);
    if (!accion) {
      setTimeout(() => aviso.remove(), DURACION_AUTODESCARTE_MS);
    }
  } catch {
    // Si ni este último recurso funciona, no hay nada más que hacer acá — que no vuelva a
    // romper la app es lo único que importa en este punto.
  }
}

function obtenerOCrearContenedor(): HTMLElement {
  let contenedor = document.getElementById(ID_CONTENEDOR);
  if (!contenedor) {
    contenedor = document.createElement('div');
    contenedor.id = ID_CONTENEDOR;
    contenedor.style.cssText =
      'position:fixed;bottom:16px;left:50%;transform:translateX(-50%);z-index:99999;' +
      'display:flex;flex-direction:column;gap:8px;align-items:center;pointer-events:none;';
    document.body.appendChild(contenedor);
  }
  return contenedor;
}
