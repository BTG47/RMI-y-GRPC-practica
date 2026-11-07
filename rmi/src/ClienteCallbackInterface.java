import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClienteCallbackInterface extends Remote {

    void actualizaPrecio(String producto, float nuevoPrecio)
            throws RemoteException;
}
