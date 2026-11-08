public class PruebaConexionTls {

    private static final String URL_POR_DEFECTO = "rmi://localhost:1100/Subasta";

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : URL_POR_DEFECTO;
        try {
            SubastaInterface subasta = FabricaTls.lookup(url, FabricaTls.cliente());
            int cantidad = subasta.obtieneCatalogo().size();
            System.out.println("CONECTADO: catálogo con " + cantidad + " producto(s)");
        } catch (Exception e) {
            System.out.println("RECHAZADO: " + e);
            System.exit(1);
        }
    }
}
