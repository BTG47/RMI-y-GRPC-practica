import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;

public class ClienteCallbackImpl extends UnicastRemoteObject
        implements ClienteCallbackInterface {

    private static final long serialVersionUID = 1L;

    private final SubastaControlador controlador;

    public ClienteCallbackImpl(SubastaControlador controlador) throws RemoteException {
        this.controlador = controlador;
    }

    public ClienteCallbackImpl(SubastaControlador controlador,
                               RMIClientSocketFactory csf,
                               RMIServerSocketFactory ssf) throws RemoteException {
        super(0, csf, ssf);
        this.controlador = controlador;
    }

    @Override
    public void actualizaPrecio(String producto, float nuevoPrecio) {
        controlador.recibePrecio(producto, nuevoPrecio);
    }
}
