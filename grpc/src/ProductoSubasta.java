final class ProductoSubasta {

    private final String vendedor;
    private final String nombre;
    private final float precioInicial;
    private float precioActual;
    private long version;

    ProductoSubasta(String vendedor, String nombre, float precioInicial) {
        this.vendedor = vendedor;
        this.nombre = nombre;
        this.precioInicial = precioInicial;
        this.precioActual = precioInicial;
        this.version = 1;
    }

    boolean actualizaPrecio(float monto) {
        if (monto <= precioActual) {
            return false;
        }
        precioActual = monto;
        version++;
        return true;
    }

    String getVendedor() {
        return vendedor;
    }

    String getNombre() {
        return nombre;
    }

    float getPrecioInicial() {
        return precioInicial;
    }

    float getPrecioActual() {
        return precioActual;
    }

    long getVersion() {
        return version;
    }
}
