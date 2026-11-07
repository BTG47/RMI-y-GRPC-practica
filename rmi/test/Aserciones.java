public final class Aserciones {

    private static int fallos = 0;
    private static int comprobaciones = 0;

    private Aserciones() {
    }

    public static void reinicia() {
        fallos = 0;
        comprobaciones = 0;
    }

    public static void assertTrue(String nombre, boolean condicion) {
        comprobaciones++;
        if (!condicion) {
            fallos++;
            System.out.println("FALLO: " + nombre + " (se esperaba true)");
        }
    }

    public static void assertFalse(String nombre, boolean condicion) {
        comprobaciones++;
        if (condicion) {
            fallos++;
            System.out.println("FALLO: " + nombre + " (se esperaba false)");
        }
    }

    public static void assertEquals(String nombre, Object esperado, Object real) {
        comprobaciones++;
        if (esperado == null ? real != null : !esperado.equals(real)) {
            fallos++;
            System.out.println("FALLO: " + nombre + " (esperado " + esperado + ", real " + real + ")");
        }
    }

    public static void assertEquals(String nombre, float esperado, float real) {
        comprobaciones++;
        if (Float.compare(esperado, real) != 0) {
            fallos++;
            System.out.println("FALLO: " + nombre + " (esperado " + esperado + ", real " + real + ")");
        }
    }

    public static void assertEquals(String nombre, int esperado, int real) {
        comprobaciones++;
        if (esperado != real) {
            fallos++;
            System.out.println("FALLO: " + nombre + " (esperado " + esperado + ", real " + real + ")");
        }
    }

    public static void resumen(String suite) {
        System.out.println();
        if (fallos == 0) {
            System.out.println("OK: " + suite + " (" + comprobaciones + " comprobaciones)");
        } else {
            System.out.println("FALLOS: " + fallos + " de " + comprobaciones);
            System.exit(1);
        }
    }
}
