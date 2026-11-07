import javax.swing.SwingUtilities;

public class Principal {

    private static final String URL_POR_DEFECTO = "rmi://localhost:1099/Subasta";

    public static void main(String[] args) {

        String url = args.length > 0 ? args[0] : URL_POR_DEFECTO;

        SwingUtilities.invokeLater(() -> {
            SubastaVista vista = new SubastaVista();
            SubastaControlador controlador = new SubastaControlador(vista, url);

            vista.asignarActionListener(controlador);
            vista.asignarListSelectionListener(controlador);
        });
    }
}
