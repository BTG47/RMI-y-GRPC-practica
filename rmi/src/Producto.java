public class Producto {

    private final String vendedor;
    private final String nombre;
    private final float precioInicial;
    private float precioActual;

    public Producto(String vendedor, String nombre, float precioInicial) {
        this.vendedor = vendedor;
        this.nombre = nombre;
        this.precioInicial = precioInicial;
        this.precioActual = precioInicial;
    }

    public boolean actualizaPrecio(float monto) {
        if (monto > precioActual) {
            precioActual = monto;
            return true;
        }
        return false;
    }

    public String getVendedor() {
        return vendedor;
    }

    public String getNombre() {
        return nombre;
    }

    public float getPrecioInicial() {
        return precioInicial;
    }

    public float getPrecioActual() {
        return precioActual;
    }
}
