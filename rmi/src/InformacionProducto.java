import java.io.Serializable;

public class InformacionProducto implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String vendedor;
    private final String producto;
    private final float precioInicial;
    private final float precioActual;

    public InformacionProducto(String vendedor, String producto,
                               float precioInicial, float precioActual) {
        this.vendedor = vendedor;
        this.producto = producto;
        this.precioInicial = precioInicial;
        this.precioActual = precioActual;
    }

    public String getVendedor() {
        return vendedor;
    }

    public String getNombreProducto() {
        return producto;
    }

    public float getPrecioInicial() {
        return precioInicial;
    }

    public float getPrecioActual() {
        return precioActual;
    }
}
