public class PruebasCachePrecios {

    public static void main(String[] args) {
        Aserciones.reinicia();
        pruebaGuardaMonotona();
        pruebaProductoNuevoYLimpieza();
        Aserciones.resumen("pruebas de la caché de precios");
    }

    private static void pruebaGuardaMonotona() {
        CachePrecios cache = new CachePrecios();

        Aserciones.assertTrue("la caché empieza vacía", !cache.contiene("Libro"));
        cache.aplica("Libro", 120.0f);
        Aserciones.assertEquals("guarda el primer precio", 120.0f, cache.get("Libro").floatValue());
        cache.aplica("Libro", 110.0f);
        Aserciones.assertEquals("una notificación vieja no baja el precio",
                120.0f, cache.get("Libro").floatValue());
        cache.aplica("Libro", 130.0f);
        Aserciones.assertEquals("una notificación nueva sube el precio",
                130.0f, cache.get("Libro").floatValue());
        Aserciones.assertTrue("la caché contiene el producto", cache.contiene("Libro"));
    }

    private static void pruebaProductoNuevoYLimpieza() {
        CachePrecios cache = new CachePrecios();
        cache.aplica("Libro", 100.0f);
        cache.aplica("Silla", 50.0f);

        Aserciones.assertEquals("acepta un producto nuevo", 50.0f, cache.get("Silla").floatValue());
        Aserciones.assertTrue("producto desconocido devuelve null", cache.get("Mesa") == null);

        cache.limpia();
        Aserciones.assertTrue("limpia deja la caché vacía",
                !cache.contiene("Libro") && !cache.contiene("Silla"));
    }
}
