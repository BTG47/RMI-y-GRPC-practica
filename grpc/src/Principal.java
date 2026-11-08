import javax.swing.SwingUtilities;

public final class Principal {

    private static final String HOST_POR_DEFECTO = "localhost";
    private static final int PUERTO_POR_DEFECTO = 50051;

    private Principal() {
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : HOST_POR_DEFECTO;
        int puerto = args.length > 1 ? Integer.parseInt(args[1]) : PUERTO_POR_DEFECTO;
        SwingUtilities.invokeLater(() -> {
            SubastaVista vista = new SubastaVista();
            SubastaControlador controlador = new SubastaControlador(vista, host, puerto);
            vista.asignarActionListener(controlador);
            vista.asignarListSelectionListener(controlador);
            vista.principal.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
            vista.principal.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent evento) {
                    controlador.salir();
                }
            });
        });
    }
}
