import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.CountDownLatch;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

public class ServidorMain {

    private static final int PUERTO_POR_DEFECTO = 1099;
    private static final String NOMBRE_POR_DEFECTO = "Subasta";

    public static void main(String[] args) {
        boolean tls = false;
        int indice = 0;
        if (args.length > 0 && "--tls".equals(args[0])) {
            tls = true;
            indice = 1;
        }

        int puerto = PUERTO_POR_DEFECTO;
        String nombre = NOMBRE_POR_DEFECTO;
        try {
            if (args.length > indice) {
                puerto = Integer.parseInt(args[indice]);
            }
            if (args.length > indice + 1) {
                nombre = args[indice + 1];
            }
        } catch (NumberFormatException e) {
            System.err.println("El puerto debe ser un número entero: " + args[indice]);
            System.exit(1);
            return;
        }

        if (!tls) {
            try {
                LocateRegistry.getRegistry("localhost", puerto).list();
            } catch (RemoteException e) {
                System.err.println("No se pudo contactar el registro RMI en el puerto " + puerto + ".");
                System.err.println("Arranca 'rmiregistry " + puerto
                        + "' desde el directorio de clases (rmi/bin) e inténtalo de nuevo.");
                System.exit(1);
                return;
            }
        }

        String url = "rmi://localhost:" + puerto + "/" + nombre;
        CountDownLatch cierre = new CountDownLatch(1);

        try {
            SubastaServidorImpl servidor;
            Registry registro = null;

            if (tls) {
                SslRMIClientSocketFactory csf = FabricaTls.cliente();
                SslRMIServerSocketFactory ssf = FabricaTls.servidor();
                registro = FabricaTls.registro(puerto, csf, ssf);
                servidor = new SubastaServidorImpl(new SubastaModelo(), csf, ssf);
            } else {
                servidor = new SubastaServidorImpl(new SubastaModelo());
            }

            SubastaServidorImpl servidorFinal = servidor;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                servidorFinal.cerrar();
                cierre.countDown();
            }, "cierre-subasta"));

            if (tls) {
                registro.rebind(nombre, servidor);
                System.out.println("Servidor TLS de subastas publicado en " + url);
            } else {
                Naming.rebind(url, servidor);
                System.out.println("Servidor de subastas publicado en " + url);
            }
            System.out.println("Pulsa Ctrl+C para cerrar.");
        } catch (Exception e) {
            System.err.println("No se pudo publicar el servidor en " + url + ": " + e);
            if (tls) {
                System.err.println("Comprueba que el puerto " + puerto
                        + " esté libre y que los keystores TLS existan.");
            }
            System.exit(1);
            return;
        }

        try {
            cierre.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
