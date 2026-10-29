package org.example.laserranitaentradas.model.entity;

public enum EstadoTrabajoImpresion {
    /** Esperando a que el agente de esa impresora esté conectado. */
    PENDIENTE,
    /** Mandado al agente sin confirmar. Se reenvía al reconectar. */
    ENVIADO,
    /** El agente confirmó que lo mandó a la impresora. */
    IMPRESO,
    /** Falló la impresora o venció. Se reimprime a mano. */
    ERROR
}
