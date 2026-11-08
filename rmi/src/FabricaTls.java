import java.net.URI;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

public final class FabricaTls {

    private static final int PUERTO_POR_DEFECTO = 1099;

    private FabricaTls() {
    }

    public static SslRMIClientSocketFactory cliente() {
        return new SslRMIClientSocketFactory();
    }

    public static SslRMIServerSocketFactory servidor() {
        return new SslRMIServerSocketFactory(null, null, true);
    }

    public static Registry registro(int puerto, RMIClientSocketFactory csf,
                                    RMIServerSocketFactory ssf) throws RemoteException {
        return LocateRegistry.createRegistry(puerto, csf, ssf);
    }

    public static SubastaInterface lookup(String url, RMIClientSocketFactory csf)
            throws RemoteException, NotBoundException {
        Destino destino = parsea(url);
        Registry registro = LocateRegistry.getRegistry(
                destino.host(), destino.puerto(), csf);
        return (SubastaInterface) registro.lookup(destino.nombre());
    }

    public static Destino parsea(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL RMI inválida: " + url, e);
        }
        if (!"rmi".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("El URL debe usar el esquema rmi://: " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("El URL no tiene host: " + url);
        }
        int puerto = uri.getPort() == -1 ? PUERTO_POR_DEFECTO : uri.getPort();
        String ruta = uri.getPath();
        if (ruta == null || ruta.length() <= 1) {
            throw new IllegalArgumentException("El URL no tiene nombre de servicio: " + url);
        }
        return new Destino(host, puerto, ruta.substring(1));
    }

    public record Destino(String host, int puerto, String nombre) {
    }
}
