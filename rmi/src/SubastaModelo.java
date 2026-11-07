import java.util.Hashtable;
import java.util.Vector;

public class SubastaModelo {

    private final Hashtable<String, String> usuarios;
    private final Hashtable<String, Producto> productos;
    private final Hashtable<String, InformacionOferta> ofertas;

    public SubastaModelo() {
        usuarios = new Hashtable<>();
        productos = new Hashtable<>();
        ofertas = new Hashtable<>();
    }

    public boolean registraUsuario(String nombre) {
        if (!usuarios.containsKey(nombre)) {
            usuarios.put(nombre, nombre);
            return true;
        }
        return false;
    }

    public boolean agregaProductoALaVenta(String vendedor, String producto,
                                           float precioInicial) {
        if (!productos.containsKey(producto)) {
            productos.put(producto,
                    new Producto(vendedor, producto, precioInicial));
            return true;
        }
        return false;
    }

    public ResultadoOferta agregaOferta(String comprador, String producto,
                                        float monto) {
        Producto productoEnVenta = productos.get(producto);
        if (productoEnVenta == null) {
            return ResultadoOferta.PRODUCTO_NO_EXISTE;
        }

        if (!productoEnVenta.actualizaPrecio(monto)) {
            return ResultadoOferta.MONTO_INSUFICIENTE;
        }

        ofertas.put(producto + comprador,
                new InformacionOferta(comprador, producto, monto));
        return ResultadoOferta.ACEPTADA;
    }

    public Vector<InformacionProducto> obtieneCatalogo() {
        Vector<InformacionProducto> resultado = new Vector<>();
        for (Producto producto : productos.values()) {
            resultado.add(new InformacionProducto(
                    producto.getVendedor(),
                    producto.getNombre(),
                    producto.getPrecioInicial(),
                    producto.getPrecioActual()));
        }
        return resultado;
    }
}
