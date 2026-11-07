import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.concurrent.CountDownLatch;

public class ServidorMain {

    private static final int PUERTO_POR_DEFECTO = 1099;
    private static final String NOMBRE_POR_DEFECTO = "Subasta";

    public static void main(String[] args) {
        int puerto = PUERTO_POR_DEFECTO;
        String nombre = NOMBRE_POR_DEFECTO;
        try {
            if (args.length > 0) {
                puerto = Integer.parseInt(args[0]);
            }
            if (args.length > 1) {
                nombre = args[1];
            }
        } catch (NumberFormatException e) {
            System.err.println("El puerto debe ser un número entero: " + args[0]);
            System.exit(1);
        }

        try {
            LocateRegistry.getRegistry("localhost", puerto).list();
        } catch (RemoteException e) {
            System.err.println("No se pudo contactar el registro RMI en el puerto " + puerto + ".");
            System.err.println("Arranca 'rmiregistry " + puerto
                    + "' desde el directorio de clases (rmi/bin) e inténtalo de nuevo.");
            System.exit(1);
        }

        String url = "rmi://localhost:" + puerto + "/" + nombre;
        CountDownLatch cierre = new CountDownLatch(1);

        try {
            SubastaServidorImpl servidor = new SubastaServidorImpl(new SubastaModelo());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                servidor.cerrar();
                cierre.countDown();
            }, "cierre-subasta"));

            Naming.rebind(url, servidor);
            System.out.println("Servidor de subastas publicado en " + url);
            System.out.println("Pulsa Ctrl+C para cerrar.");
        } catch (Exception e) {
            System.err.println("No se pudo publicar el servidor en " + url + ": " + e);
            System.exit(1);
        }

        try {
            cierre.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
