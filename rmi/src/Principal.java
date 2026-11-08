import javax.swing.SwingUtilities;

public class Principal {

    private static final String URL_POR_DEFECTO = "rmi://localhost:1099/Subasta";

    public static void main(String[] args) {

        boolean tls = false;
        int indice = 0;
        if (args.length > 0 && "--tls".equals(args[0])) {
            tls = true;
            indice = 1;
        }
        String url = args.length > indice ? args[indice] : URL_POR_DEFECTO;
        Transporte transporte = tls
                ? new TransporteTls(url)
                : new TransportePlano(url);

        SwingUtilities.invokeLater(() -> {
            SubastaVista vista = new SubastaVista();
            SubastaControlador controlador = new SubastaControlador(vista, transporte);

            vista.asignarActionListener(controlador);
            vista.asignarListSelectionListener(controlador);
        });
    }
}
