import java.net.MalformedURLException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

public interface Transporte {

    SubastaInterface lookup()
            throws RemoteException, NotBoundException, MalformedURLException;

    ClienteCallbackImpl crearCallback(SubastaControlador controlador)
            throws RemoteException;
}
