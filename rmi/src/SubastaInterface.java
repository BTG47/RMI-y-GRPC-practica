import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Vector;

public interface SubastaInterface extends Remote {

    boolean conecta(String nombre, ClienteCallbackInterface callback)
            throws RemoteException;

    boolean agregaProductoALaVenta(String vendedor, String producto,
                                   float precioInicial) throws RemoteException;

    ResultadoOferta agregaOferta(String comprador, String producto,
                                 float monto) throws RemoteException;

    Vector<InformacionProducto> obtieneCatalogo() throws RemoteException;

    void desconecta(String nombre) throws RemoteException;
}
