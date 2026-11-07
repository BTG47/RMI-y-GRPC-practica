import java.util.Vector;

public class PruebasModelo {

    public static void main(String[] args) {
        Aserciones.reinicia();
        pruebaRegistro();
        pruebaProductos();
        pruebaOfertas();
        pruebaCatalogoCopiaYSnapshot();
        Aserciones.resumen("pruebas del modelo");
    }

    private static void pruebaRegistro() {
        SubastaModelo modelo = new SubastaModelo();

        Aserciones.assertTrue("registra a Ana", modelo.registraUsuario("Ana"));
        Aserciones.assertFalse("rechaza a Ana duplicada", modelo.registraUsuario("Ana"));
    }

    private static void pruebaProductos() {
        SubastaModelo modelo = new SubastaModelo();

        Aserciones.assertTrue("pone Libro a la venta",
                modelo.agregaProductoALaVenta("Ana", "Libro", 100.0f));
        Aserciones.assertFalse("rechaza Libro duplicado",
                modelo.agregaProductoALaVenta("Beto", "Libro", 90.0f));
    }

    private static void pruebaOfertas() {
        SubastaModelo modelo = new SubastaModelo();
        modelo.agregaProductoALaVenta("Ana", "Libro", 100.0f);

        Aserciones.assertEquals("oferta igual rechazada",
                ResultadoOferta.MONTO_INSUFICIENTE,
                modelo.agregaOferta("Beto", "Libro", 100.0f));
        Aserciones.assertEquals("oferta menor rechazada",
                ResultadoOferta.MONTO_INSUFICIENTE,
                modelo.agregaOferta("Beto", "Libro", 99.0f));
        Aserciones.assertEquals("oferta mayor aceptada",
                ResultadoOferta.ACEPTADA,
                modelo.agregaOferta("Beto", "Libro", 120.0f));
        Aserciones.assertEquals("precio actualizado", 120.0f,
                busca(modelo.obtieneCatalogo(), "Libro").getPrecioActual());
        Aserciones.assertEquals("el vendedor puede ofertar",
                ResultadoOferta.ACEPTADA,
                modelo.agregaOferta("Ana", "Libro", 130.0f));
        Aserciones.assertEquals("un comprador no registrado puede ofertar",
                ResultadoOferta.ACEPTADA,
                modelo.agregaOferta("Carla", "Libro", 140.0f));
        Aserciones.assertEquals("producto inexistente rechazado",
                ResultadoOferta.PRODUCTO_NO_EXISTE,
                modelo.agregaOferta("Beto", "Silla", 50.0f));
    }

    private static void pruebaCatalogoCopiaYSnapshot() {
        SubastaModelo modelo = new SubastaModelo();
        modelo.agregaProductoALaVenta("Ana", "Libro", 100.0f);
        modelo.agregaOferta("Beto", "Libro", 110.0f);

        Vector<InformacionProducto> catalogo = modelo.obtieneCatalogo();
        InformacionProducto libro = busca(catalogo, "Libro");
        Aserciones.assertTrue("Libro está en el catálogo", libro != null);
        if (libro == null) {
            return;
        }
        Aserciones.assertEquals("vendedor en el DTO", "Ana", libro.getVendedor());
        Aserciones.assertEquals("precio inicial en el DTO", 100.0f, libro.getPrecioInicial());
        Aserciones.assertEquals("precio actual en el DTO", 110.0f, libro.getPrecioActual());

        catalogo.clear();
        Aserciones.assertTrue("el catálogo devuelto es una copia",
                busca(modelo.obtieneCatalogo(), "Libro") != null);

        Vector<InformacionProducto> snapshot = modelo.obtieneCatalogo();
        modelo.agregaOferta("Carla", "Libro", 120.0f);
        Aserciones.assertEquals("el snapshot no cambia", 110.0f,
                busca(snapshot, "Libro").getPrecioActual());
        Aserciones.assertEquals("el modelo sí cambia", 120.0f,
                busca(modelo.obtieneCatalogo(), "Libro").getPrecioActual());
    }

    private static InformacionProducto busca(Vector<InformacionProducto> catalogo,
                                             String nombre) {
        for (InformacionProducto producto : catalogo) {
            if (producto.getNombreProducto().equals(nombre)) {
                return producto;
            }
        }
        return null;
    }
}
