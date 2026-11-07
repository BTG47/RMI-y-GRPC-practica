import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ClienteCallbackImpl extends UnicastRemoteObject
        implements ClienteCallbackInterface {

    private static final long serialVersionUID = 1L;

    private final SubastaControlador controlador;

    public ClienteCallbackImpl(SubastaControlador controlador) throws RemoteException {
        this.controlador = controlador;
    }

    @Override
    public void actualizaPrecio(String producto, float nuevoPrecio) {
        controlador.recibePrecio(producto, nuevoPrecio);
    }
}
