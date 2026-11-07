import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;

import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

public class SubastaControlador implements ActionListener, ListSelectionListener {

    private final SubastaVista vista;
    private final String url;
    private final CachePrecios precios = new CachePrecios();

    private SubastaInterface subasta;
    private ClienteCallbackImpl callback;
    private volatile String usuarioConectado;

    public SubastaControlador(SubastaVista vista, String url) {
        this.vista = vista;
        this.url = url;
    }

    @Override
    public void actionPerformed(ActionEvent evento) {
        String comando = evento.getActionCommand();
        System.out.println("<<" + comando + ">>");

        if (comando.equals("Conectar")) {
            conectar();
        } else if (comando.equals("Desconectar")) {
            desconectar();
        } else if (comando.equals("Salir")) {
            salir();
        } else if (comando.equals("Poner a la venta")) {
            ponerALaVenta();
        } else if (comando.equals("Obtener lista")) {
            int cantidad = refrescaCatalogo();
            if (cantidad >= 0) {
                vista.setEstado("Catálogo actualizado: " + cantidad + " producto(s)");
            }
        } else if (comando.equals("Ofrecer")) {
            ofrecer();
        }
    }

    private void conectar() {
        String usuario = vista.getUsuario().trim();
        if (usuario.isEmpty()) {
            vista.setEstado("Escribe un nombre de usuario");
            return;
        }
        if (usuarioConectado != null) {
            vista.setEstado("Ya estás conectado como " + usuarioConectado);
            return;
        }

        try {
            if (subasta == null) {
                subasta = (SubastaInterface) Naming.lookup(url);
            }
            if (callback == null) {
                callback = new ClienteCallbackImpl(this);
            }
            if (!subasta.conecta(usuario, callback)) {
                vista.setEstado("Ese usuario ya tiene una sesión activa");
                return;
            }

            usuarioConectado = usuario;
            precios.limpia();
            vista.habilitaSesion(true);
            int cantidad = refrescaCatalogo();
            if (cantidad >= 0) {
                vista.setEstado("Conectado como " + usuario
                        + " (" + cantidad + " producto(s))");
            }
        } catch (NotBoundException | MalformedURLException e) {
            subasta = null;
            vista.setEstado("No se pudo conectar: " + e.getMessage());
        } catch (RemoteException e) {
            subasta = null;
            vista.setEstado("No se pudo conectar: " + e.getMessage());
        }
    }

    private void desconectar() {
        boolean fallo = false;
        if (usuarioConectado != null && subasta != null) {
            try {
                subasta.desconecta(usuarioConectado);
            } catch (RemoteException e) {
                fallo = true;
            }
        }

        usuarioConectado = null;
        subasta = null;
        precios.limpia();
        vista.reinicializaListaProductos();
        vista.desplegarPrecio("");
        vista.habilitaSesion(false);
        vista.setEstado(fallo
                ? "Desconectado localmente; el servidor no respondió"
                : "Desconectado");
    }

    private void salir() {
        if (usuarioConectado != null && subasta != null) {
            try {
                subasta.desconecta(usuarioConectado);
            } catch (RemoteException e) {
                // Se cierra igual: no hay reconexión automática.
            }
        }
        if (callback != null) {
            try {
                UnicastRemoteObject.unexportObject(callback, true);
            } catch (Exception e) {
                // El callback ya no estaba exportado.
            }
        }
        System.exit(1);
    }

    private void ponerALaVenta() {
        if (!haySesion()) {
            return;
        }

        String producto = vista.getProducto().trim();
        float precioInicial = vista.getPrecioInicial();
        if (producto.isEmpty()) {
            vista.setEstado("Escribe el nombre del producto");
            return;
        }
        if (precioInicial <= 0) {
            vista.setEstado("El precio inicial debe ser mayor que cero");
            return;
        }

        try {
            if (subasta.agregaProductoALaVenta(usuarioConectado, producto, precioInicial)) {
                precios.aplica(producto, precioInicial);
                int cantidad = refrescaCatalogo();
                if (cantidad >= 0) {
                    vista.setEstado("Producto publicado: " + producto);
                }
            } else {
                vista.setEstado("Ya existe un producto con ese nombre");
            }
        } catch (RemoteException e) {
            vista.setEstado("No se pudo publicar el producto: " + e.getMessage());
        }
    }

    private void ofrecer() {
        if (!haySesion()) {
            return;
        }

        String producto = vista.getProductoSeleccionado();
        if (producto == null) {
            vista.setEstado("Selecciona un producto de la lista");
            return;
        }
        float monto = vista.getMontoOfrecido();
        if (monto <= 0) {
            vista.setEstado("El monto ofrecido debe ser mayor que cero");
            return;
        }

        try {
            ResultadoOferta resultado = subasta.agregaOferta(usuarioConectado, producto, monto);
            if (resultado == ResultadoOferta.ACEPTADA) {
                precios.aplica(producto, monto);
                muestraPrecio(producto);
                vista.setEstado("Oferta aceptada por " + monto);
            } else if (resultado == ResultadoOferta.MONTO_INSUFICIENTE) {
                Float actual = precios.get(producto);
                vista.setEstado("La oferta debe ser mayor"
                        + (actual == null ? "" : " que " + actual));
            } else {
                vista.setEstado("El producto ya no existe; actualiza la lista");
            }
        } catch (RemoteException e) {
            vista.setEstado("No se pudo enviar la oferta: " + e.getMessage());
        }
    }

    private int refrescaCatalogo() {
        if (!haySesion()) {
            return -1;
        }

        try {
            Vector<InformacionProducto> catalogo = subasta.obtieneCatalogo();
            vista.reinicializaListaProductos();
            vista.desplegarPrecio("");
            for (InformacionProducto producto : catalogo) {
                precios.aplica(producto.getNombreProducto(), producto.getPrecioActual());
                vista.agregaProducto(producto.getNombreProducto());
            }
            return catalogo.size();
        } catch (RemoteException e) {
            vista.setEstado("No se pudo obtener el catálogo: " + e.getMessage());
            return -1;
        }
    }

    private boolean haySesion() {
        if (usuarioConectado == null || subasta == null) {
            vista.setEstado("Conéctate primero");
            return false;
        }
        return true;
    }

    private void muestraPrecio(String producto) {
        if (!producto.equals(vista.getProductoSeleccionado())) {
            return;
        }
        Float precio = precios.get(producto);
        if (precio != null) {
            vista.desplegarPrecio(String.valueOf(precio));
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent evento) {
        if (evento.getValueIsAdjusting()) {
            return;
        }

        JList<?> lista = (JList<?>) evento.getSource();
        Object seleccionado = lista.getSelectedValue();
        if (seleccionado == null) {
            return;
        }

        String producto = seleccionado.toString();
        Float precio = precios.get(producto);
        vista.desplegarPrecio(precio == null ? "" : String.valueOf(precio));
    }

    public void recibePrecio(String producto, float nuevoPrecio) {
        if (usuarioConectado == null) {
            return;
        }

        precios.aplica(producto, nuevoPrecio);
        SwingUtilities.invokeLater(() -> {
            if (usuarioConectado == null) {
                return;
            }
            if (producto.equals(vista.getProductoSeleccionado())) {
                muestraPrecio(producto);
            }
        });
    }
}
