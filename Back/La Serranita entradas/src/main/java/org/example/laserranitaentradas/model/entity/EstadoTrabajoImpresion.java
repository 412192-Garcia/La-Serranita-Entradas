package org.example.laserranitaentradas.model.entity;

public enum EstadoTrabajoImpresion {
    /** Esperando a que el agente de esa impresora esté conectado. */
    PENDIENTE,
    /** Se le mandó al agente y todavía no confirmó. Si se corta la conexión, se le vuelve a
     * mandar al reconectar (el agente descarta los que ya imprimió). */
    ENVIADO,
    /** El agente confirmó que lo mandó a la impresora. */
    IMPRESO,
    /** La impresora falló, o pasó demasiado tiempo sin poder imprimirse (el cliente ya se fue:
     * imprimirlo horas después no le sirve a nadie; se reimprime a mano si hace falta). */
    ERROR
}
