package com.mutuals.moment.entity;

public enum MomentStatus {
    /** Corre el reloj: falta la foto de uno o de los dos. */
    OPEN,
    /** Ambos subieron su foto a tiempo y la racha del día quedó confirmada. */
    COMPLETED,
    /** Se acabó la ventana sin que ambos subieran su foto. */
    EXPIRED
}
