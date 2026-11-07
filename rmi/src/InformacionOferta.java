import java.io.Serializable;

public class InformacionOferta implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String comprador;
    private final String producto;
    private final float monto;

    public InformacionOferta(String comprador, String producto, float monto) {
        this.comprador = comprador;
        this.producto = producto;
        this.monto = monto;
    }

    public String getComprador() {
        return comprador;
    }

    public String getProducto() {
        return producto;
    }

    public float getMonto() {
        return monto;
    }
}
