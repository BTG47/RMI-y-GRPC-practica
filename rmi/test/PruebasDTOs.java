import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class PruebasDTOs {

    public static void main(String[] args) throws Exception {
        Aserciones.reinicia();
        pruebaProducto();
        pruebaSerializacionProducto();
        pruebaSerializacionOferta();
        pruebaEnum();
        Aserciones.resumen("pruebas de DTOs");
    }

    private static void pruebaProducto() {
        Producto p = new Producto("Ana", "Libro", 100.0f);

        Aserciones.assertEquals("precio inicial", 100.0f, p.getPrecioActual());
        Aserciones.assertFalse("una oferta igual no sube el precio", p.actualizaPrecio(100.0f));
        Aserciones.assertEquals("el precio no cambia con una oferta igual", 100.0f, p.getPrecioActual());
        Aserciones.assertTrue("una oferta mayor sube el precio", p.actualizaPrecio(101.0f));
        Aserciones.assertEquals("el precio sube", 101.0f, p.getPrecioActual());
        Aserciones.assertEquals("vendedor", "Ana", p.getVendedor());
        Aserciones.assertEquals("nombre", "Libro", p.getNombre());
        Aserciones.assertEquals("precio inicial conservado", 100.0f, p.getPrecioInicial());
    }

    private static void pruebaSerializacionProducto() throws Exception {
        InformacionProducto original = new InformacionProducto("Ana", "Libro", 100.0f, 120.0f);
        InformacionProducto copia = (InformacionProducto) serializaYDeserializa(original);

        Aserciones.assertEquals("vendedor serializado", original.getVendedor(), copia.getVendedor());
        Aserciones.assertEquals("producto serializado", original.getNombreProducto(), copia.getNombreProducto());
        Aserciones.assertEquals("precio inicial serializado", original.getPrecioInicial(), copia.getPrecioInicial());
        Aserciones.assertEquals("precio actual serializado", original.getPrecioActual(), copia.getPrecioActual());
    }

    private static void pruebaSerializacionOferta() throws Exception {
        InformacionOferta original = new InformacionOferta("Beto", "Libro", 120.0f);
        InformacionOferta copia = (InformacionOferta) serializaYDeserializa(original);

        Aserciones.assertEquals("comprador serializado", original.getComprador(), copia.getComprador());
        Aserciones.assertEquals("producto de oferta serializado", original.getProducto(), copia.getProducto());
        Aserciones.assertEquals("monto serializado", original.getMonto(), copia.getMonto());
    }

    private static void pruebaEnum() {
        Aserciones.assertEquals("número de resultados", 3, ResultadoOferta.values().length);
        Aserciones.assertEquals("ACEPTADA", ResultadoOferta.ACEPTADA, ResultadoOferta.valueOf("ACEPTADA"));
        Aserciones.assertEquals("MONTO_INSUFICIENTE", ResultadoOferta.MONTO_INSUFICIENTE,
                ResultadoOferta.valueOf("MONTO_INSUFICIENTE"));
        Aserciones.assertEquals("PRODUCTO_NO_EXISTE", ResultadoOferta.PRODUCTO_NO_EXISTE,
                ResultadoOferta.valueOf("PRODUCTO_NO_EXISTE"));
    }

    private static Object serializaYDeserializa(Object original) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(original);
        }
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return entrada.readObject();
        }
    }
}
