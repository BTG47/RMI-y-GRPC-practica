import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;

public class TransporteTls implements Transporte {

    private final String url;
    private final SslRMIClientSocketFactory cliente;
    private final SslRMIServerSocketFactory servidor;

    public TransporteTls(String url) {
        this(url, FabricaTls.cliente(), FabricaTls.servidor());
    }

    public TransporteTls(String url, SslRMIClientSocketFactory cliente,
                         SslRMIServerSocketFactory servidor) {
        this.url = url;
        this.cliente = cliente;
        this.servidor = servidor;
    }

    @Override
    public SubastaInterface lookup() throws RemoteException, NotBoundException {
        return FabricaTls.lookup(url, cliente);
    }

    @Override
    public ClienteCallbackImpl crearCallback(SubastaControlador controlador)
            throws RemoteException {
        return new ClienteCallbackImpl(controlador, cliente, servidor);
    }
}
