package org.example.laserranitaentradas.monitoreo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lo que un servicio le suma al registro de auditoría de la request en curso: a qué se refiere
 * ("#261005-3", "General") y el antes → después de lo que cambió. El AuditoriaFilter lo abre al
 * entrar la request y lo junta con el resto al terminar. Fuera de una request (procesos en segundo
 * plano) no hace nada.
 */
public final class AuditoriaContexto {

    private static final ThreadLocal<Datos> ACTUAL = new ThreadLocal<>();

    private AuditoriaContexto() {}

    static final class Datos {
        String referencia;
        final List<String> lineas = new ArrayList<>();
    }

    static void iniciar() {
        ACTUAL.set(new Datos());
    }

    /** Lo juntado hasta ahora, y deja el hilo limpio. */
    static Datos tomar() {
        Datos d = ACTUAL.get();
        ACTUAL.remove();
        return d;
    }

    /** A qué se refiere la acción, para la descripción: "#261005-3", "Factura B 0011-00000007". */
    public static void referencia(String referencia) {
        Datos d = ACTUAL.get();
        if (d != null && referencia != null) d.referencia = referencia;
    }

    /** "Precio: $30.000 → $34.300". Si no cambió, no anota nada. */
    public static void cambio(String campo, Object antes, Object despues) {
        Datos d = ACTUAL.get();
        if (d == null || iguales(antes, despues)) return;
        d.lineas.add(campo + ": " + texto(antes) + " → " + texto(despues));
    }

    public static void detalle(String linea) {
        Datos d = ACTUAL.get();
        if (d != null && linea != null && !linea.isBlank()) d.lineas.add(linea);
    }

    private static boolean iguales(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y) == 0;
        return Objects.equals(a, b);
    }

    private static String texto(Object valor) {
        if (valor == null) return "(vacío)";
        if (valor instanceof BigDecimal b) return "$" + b.stripTrailingZeros().toPlainString();
        if (valor instanceof Boolean b) return b ? "sí" : "no";
        return valor.toString();
    }
}
