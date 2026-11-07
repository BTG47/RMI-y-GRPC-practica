import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SubastaServidorImpl extends UnicastRemoteObject implements SubastaInterface {

    private static final long serialVersionUID = 1L;

    private final SubastaModelo modelo;
    private final Object lock = new Object();
    private final Map<String, ClienteCallbackInterface> callbacks = new HashMap<>();
    private final ExecutorService notificador = Executors.newFixedThreadPool(4);
    private volatile boolean cerrado = false;

    public SubastaServidorImpl(SubastaModelo modelo) throws RemoteException {
        super();
        this.modelo = modelo;
    }

    @Override
    public boolean conecta(String nombre, ClienteCallbackInterface callback)
            throws RemoteException {
        synchronized (lock) {
            if (cerrado || callbacks.containsKey(nombre)) {
                return false;
            }
            modelo.registraUsuario(nombre);
            callbacks.put(nombre, callback);
            return true;
        }
    }

    @Override
    public boolean agregaProductoALaVenta(String vendedor, String producto,
                                           float precioInicial) throws RemoteException {
        synchronized (lock) {
            return modelo.agregaProductoALaVenta(vendedor, producto, precioInicial);
        }
    }

    @Override
    public ResultadoOferta agregaOferta(String comprador, String producto,
                                        float monto) throws RemoteException {
        List<ClienteCallbackInterface> destinatarios;
        synchronized (lock) {
            ResultadoOferta resultado = modelo.agregaOferta(comprador, producto, monto);
            if (resultado != ResultadoOferta.ACEPTADA) {
                return resultado;
            }
            destinatarios = new ArrayList<>(callbacks.values());
        }

        for (ClienteCallbackInterface callback : destinatarios) {
            notificador.execute(() -> notifica(callback, producto, monto));
        }
        return ResultadoOferta.ACEPTADA;
    }

    @Override
    public Vector<InformacionProducto> obtieneCatalogo() throws RemoteException {
        synchronized (lock) {
            return modelo.obtieneCatalogo();
        }
    }

    @Override
    public void desconecta(String nombre) throws RemoteException {
        synchronized (lock) {
            callbacks.remove(nombre);
        }
    }

    private void notifica(ClienteCallbackInterface callback, String producto,
                          float nuevoPrecio) {
        try {
            callback.actualizaPrecio(producto, nuevoPrecio);
        } catch (RemoteException | RuntimeException e) {
            eliminaCallback(callback);
        }
    }

    private void eliminaCallback(ClienteCallbackInterface callback) {
        synchronized (lock) {
            callbacks.entrySet().removeIf(entrada -> entrada.getValue() == callback);
        }
    }

    public void cerrar() {
        synchronized (lock) {
            if (cerrado) {
                return;
            }
            cerrado = true;
            callbacks.clear();
        }

        notificador.shutdown();
        try {
            if (!notificador.awaitTermination(5, TimeUnit.SECONDS)) {
                notificador.shutdownNow();
            }
        } catch (InterruptedException e) {
            notificador.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (Exception e) {
            // El objeto ya no estaba exportado: cerrar debe ser idempotente.
        }
    }
}
