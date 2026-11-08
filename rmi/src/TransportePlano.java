import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public class TransportePlano implements Transporte {

    private final String url;

    public TransportePlano(String url) {
        this.url = url;
    }

    @Override
    public SubastaInterface lookup()
            throws RemoteException, NotBoundException, MalformedURLException {
        return (SubastaInterface) Naming.lookup(url);
    }

    @Override
    public ClienteCallbackImpl crearCallback(SubastaControlador controlador)
            throws RemoteException {
        return new ClienteCallbackImpl(controlador);
    }
}
