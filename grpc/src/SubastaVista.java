import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import javax.swing.event.ListSelectionListener;

public class SubastaVista {

    JFrame principal;
    JTextField usuario;
    JTextField producto;
    JTextField precioInicial;
    JTextField monto;
    DefaultComboBoxModel<String> productos;
    JLabel precioActual;
    JLabel estado;
    JList<String> lista;
    JButton conectar;
    JButton desconectar;
    JButton salir;
    JButton ponerALaVenta;
    JButton obtenerLista;
    JButton ofrecer;

    public SubastaVista() {
        JFrame.setDefaultLookAndFeelDecorated(true);
        Container panel;

        principal = new JFrame("Cliente Subasta gRPC");
        panel = principal.getContentPane();
        panel.setLayout(new GridLayout(0, 2));
        usuario = new JTextField();
        panel.add(new JLabel("Nombre del usuario"));
        panel.add(usuario);

        conectar = new JButton("Conectar");
        desconectar = new JButton("Desconectar");
        salir = new JButton("Salir");
        panel.add(conectar);
        panel.add(desconectar);
        panel.add(salir);
        panel.add(new JLabel());

        producto = new JTextField();
        precioInicial = new JTextField();
        panel.add(new JLabel("Producto a ofrecer"));
        panel.add(producto);
        panel.add(new JLabel("Precio inicial"));
        panel.add(precioInicial);

        ponerALaVenta = new JButton("Poner a la venta");
        panel.add(ponerALaVenta);
        panel.add(new JLabel());

        productos = new DefaultComboBoxModel<>();
        lista = new JList<>(productos);
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lista.setLayoutOrientation(JList.VERTICAL);
        JScrollPane listaScroller = new JScrollPane(lista);
        listaScroller.setPreferredSize(new Dimension(250, 80));
        obtenerLista = new JButton("Obtener lista");
        panel.add(obtenerLista);
        panel.add(listaScroller);

        precioActual = new JLabel();
        panel.add(new JLabel("Precio actual"));
        panel.add(precioActual);

        monto = new JTextField();
        ofrecer = new JButton("Ofrecer");
        panel.add(ofrecer);
        panel.add(monto);

        estado = new JLabel("Desconectado");
        panel.add(new JLabel("Estado"));
        panel.add(estado);

        principal.setSize(400, 480);
        principal.setLocationByPlatform(true);
        principal.setVisible(true);
        habilitaSesion(false);
    }

    public void asignarActionListener(ActionListener controlador) {
        conectar.addActionListener(controlador);
        desconectar.addActionListener(controlador);
        salir.addActionListener(controlador);
        ponerALaVenta.addActionListener(controlador);
        obtenerLista.addActionListener(controlador);
        ofrecer.addActionListener(controlador);
    }

    public void asignarListSelectionListener(ListSelectionListener controlador) {
        lista.addListSelectionListener(controlador);
    }

    public String getUsuario() {
        return usuario.getText();
    }

    public String getProducto() {
        return producto.getText();
    }

    public float getPrecioInicial() {
        try {
            return Float.parseFloat(precioInicial.getText());
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }

    public void reinicializaListaProductos() {
        productos.removeAllElements();
    }

    public boolean contieneProducto(String nombre) {
        return productos.getIndexOf(nombre) >= 0;
    }

    public void agregaProducto(String prod) {
        if (!contieneProducto(prod)) {
            productos.addElement(prod);
        }
    }

    public void desplegarPrecio(String precio) {
        precioActual.setText(precio);
    }

    public void setEstado(String mensaje) {
        estado.setText(mensaje);
    }

    public void habilitaSesion(boolean sesion) {
        conectar.setEnabled(!sesion);
        desconectar.setEnabled(sesion);
        salir.setEnabled(true);
        usuario.setEnabled(!sesion);
        producto.setEnabled(sesion);
        precioInicial.setEnabled(sesion);
        ponerALaVenta.setEnabled(sesion);
        obtenerLista.setEnabled(sesion);
        ofrecer.setEnabled(sesion);
        monto.setEnabled(sesion);
    }

    public float getMontoOfrecido() {
        try {
            return Float.parseFloat(monto.getText());
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }

    public String getProductoSeleccionado() {
        return lista.getSelectedValue();
    }
}
