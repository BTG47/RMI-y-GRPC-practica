import grpc.generated.CatalogoResponse;
import grpc.generated.ConsultaCatalogoRequest;
import grpc.generated.DesconexionRequest;
import grpc.generated.OfertaRequest;
import grpc.generated.OfertaResponse;
import grpc.generated.Producto;
import grpc.generated.ProductoActualizado;
import grpc.generated.PublicarProductoRequest;
import grpc.generated.RegistroUsuarioRequest;
import grpc.generated.ResultadoOferta;
import grpc.generated.SubastaServiceGrpc;
import grpc.generated.SuscripcionRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;

import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class SubastaControlador implements ActionListener, ListSelectionListener {

    private final SubastaVista vista;
    private final ManagedChannel canal;
    private final SubastaServiceGrpc.SubastaServiceBlockingStub remoto;
    private final SubastaServiceGrpc.SubastaServiceStub remotoAsincrono;
    private final ExecutorService solicitudes = Executors.newSingleThreadExecutor(r -> {
        Thread hilo = new Thread(r, "solicitudes-grpc-subasta");
        hilo.setDaemon(true);
        return hilo;
    });
    private final ConcurrentHashMap<String, Float> precios = new ConcurrentHashMap<>();

    private volatile String usuarioConectado;
    private volatile ClientCallStreamObserver<SuscripcionRequest> suscripcion;
    private volatile boolean conectando;
    private volatile boolean cerrado;

    public SubastaControlador(SubastaVista vista, String host, int puerto) {
        this.vista = vista;
        this.canal = ManagedChannelBuilder.forAddress(host, puerto).usePlaintext().build();
        this.remoto = SubastaServiceGrpc.newBlockingStub(canal);
        this.remotoAsincrono = SubastaServiceGrpc.newStub(canal);
    }

    @Override
    public void actionPerformed(ActionEvent evento) {
        switch (evento.getActionCommand()) {
            case "Conectar" -> conectar();
            case "Desconectar" -> desconectar();
            case "Salir" -> salir();
            case "Poner a la venta" -> ponerALaVenta();
            case "Obtener lista" -> refrescaCatalogo();
            case "Ofrecer" -> ofrecer();
            default -> vista.setEstado("Acción desconocida: " + evento.getActionCommand());
        }
    }

    private void conectar() {
        String usuario = vista.getUsuario().trim();
        if (usuario.isEmpty()) {
            vista.setEstado("Escribe un nombre de usuario");
            return;
        }
        if (usuarioConectado != null || conectando || cerrado) {
            vista.setEstado("Ya hay una conexión activa o en proceso");
            return;
        }
        conectando = true;
        vista.setEstado("Conectando...");
        ejecuta("No se pudo conectar", () -> {
            var registro = remoto.registrarUsuario(RegistroUsuarioRequest.newBuilder()
                    .setUsuario(usuario).build());
            if (!registro.getRegistrado()) {
                conectando = false;
                SwingUtilities.invokeLater(() ->
                        vista.setEstado("Ese usuario ya tiene una sesión activa"));
                return;
            }
            usuarioConectado = usuario;
            precios.clear();
            iniciaSuscripcion(usuario);
            CatalogoResponse catalogo = remoto.consultarCatalogo(
                    ConsultaCatalogoRequest.getDefaultInstance());
            SwingUtilities.invokeLater(() -> {
                vista.habilitaSesion(true);
                pintaCatalogo(catalogo);
                vista.setEstado("Conectado como " + usuario
                        + " (" + catalogo.getProductosCount() + " producto(s))");
            });
            conectando = false;
        }, () -> {
            conectando = false;
            String sesion = usuarioConectado;
            usuarioConectado = null;
            cancelaSuscripcion();
            precios.clear();
            if (sesion != null) {
                try {
                    remoto.withDeadlineAfter(2, TimeUnit.SECONDS).desconectar(
                            DesconexionRequest.newBuilder().setUsuario(sesion).build());
                } catch (RuntimeException e) {
                    System.err.println("No se pudo limpiar la sesión incompleta: " + mensaje(e));
                }
            }
        });
    }

    private void iniciaSuscripcion(String usuario) {
        remotoAsincrono.suscribirCambios(SuscripcionRequest.newBuilder()
                .setUsuario(usuario).build(),
                new ClientResponseObserver<SuscripcionRequest, ProductoActualizado>() {
            @Override
            public void beforeStart(ClientCallStreamObserver<SuscripcionRequest> requestStream) {
                suscripcion = requestStream;
            }

            @Override
            public void onNext(ProductoActualizado evento) {
                recibeProducto(evento.getProducto());
            }

            @Override
            public void onError(Throwable error) {
                suscripcion = null;
                if (usuario.equals(usuarioConectado)) {
                    usuarioConectado = null;
                    precios.clear();
                    SwingUtilities.invokeLater(() -> {
                        vista.reinicializaListaProductos();
                        vista.desplegarPrecio("");
                        vista.habilitaSesion(false);
                        vista.setEstado("Se perdió la suscripción: " + mensaje(error));
                    });
                }
            }

            @Override
            public void onCompleted() {
                suscripcion = null;
            }
        });
    }

    private void desconectar() {
        String usuario = usuarioConectado;
        if (usuario == null) {
            vista.setEstado("No hay una sesión conectada");
            return;
        }
        vista.setEstado("Desconectando...");
        solicitudes.execute(() -> {
            boolean fallo = false;
            try {
                remoto.withDeadlineAfter(3, TimeUnit.SECONDS).desconectar(
                        DesconexionRequest.newBuilder().setUsuario(usuario).build());
            } catch (RuntimeException e) {
                fallo = true;
            } finally {
                cancelaSuscripcion();
                usuarioConectado = null;
                precios.clear();
                boolean error = fallo;
                SwingUtilities.invokeLater(() -> limpiaSesion(error));
            }
        });
    }

    public void salir() {
        if (cerrado) {
            return;
        }
        cerrado = true;
        String usuario = usuarioConectado;
        solicitudes.execute(() -> {
            if (usuario != null) {
                try {
                    remoto.withDeadlineAfter(2, TimeUnit.SECONDS).desconectar(
                            DesconexionRequest.newBuilder().setUsuario(usuario).build());
                } catch (RuntimeException e) {
                    System.err.println("No se pudo cerrar la sesión gRPC: " + mensaje(e));
                }
            }
            cancelaSuscripcion();
            canal.shutdownNow();
            solicitudes.shutdown();
            SwingUtilities.invokeLater(() -> vista.principal.dispose());
        });
    }

    private void ponerALaVenta() {
        String usuario = usuarioConectado;
        String nombre = vista.getProducto().trim();
        float inicial = vista.getPrecioInicial();
        if (usuario == null) {
            vista.setEstado("Conéctate primero");
            return;
        }
        if (nombre.isEmpty() || !esPrecioValido(inicial)) {
            vista.setEstado("Escribe el producto y un precio finito mayor que cero");
            return;
        }
        ejecuta("No se pudo publicar el producto", () -> {
            var respuesta = remoto.publicarProducto(PublicarProductoRequest.newBuilder()
                    .setVendedor(usuario).setProducto(nombre).setPrecioInicial(inicial).build());
            SwingUtilities.invokeLater(() -> vista.setEstado(respuesta.getPublicado()
                    ? "Producto publicado: " + nombre : respuesta.getMensaje()));
        });
    }

    private void ofrecer() {
        String usuario = usuarioConectado;
        String nombre = vista.getProductoSeleccionado();
        float monto = vista.getMontoOfrecido();
        if (usuario == null) {
            vista.setEstado("Conéctate primero");
            return;
        }
        if (nombre == null) {
            vista.setEstado("Selecciona un producto de la lista");
            return;
        }
        if (!esPrecioValido(monto)) {
            vista.setEstado("El monto ofrecido debe ser finito y mayor que cero");
            return;
        }
        ejecuta("No se pudo enviar la oferta", () -> {
            OfertaResponse respuesta = remoto.ofertar(OfertaRequest.newBuilder()
                    .setComprador(usuario).setProducto(nombre).setMonto(monto).build());
            SwingUtilities.invokeLater(() -> muestraResultadoOferta(nombre, monto, respuesta));
        });
    }

    private void muestraResultadoOferta(String nombre, float monto, OfertaResponse respuesta) {
        if (respuesta.getResultado() == ResultadoOferta.OFERTA_ACEPTADA) {
            aplicaPrecio(nombre, respuesta.getPrecioActual());
            vista.setEstado("Oferta aceptada por " + monto);
        } else if (respuesta.getResultado() == ResultadoOferta.OFERTA_MONTO_INSUFICIENTE) {
            aplicaPrecio(nombre, respuesta.getPrecioActual());
            vista.setEstado("La oferta debe ser mayor que " + respuesta.getPrecioActual());
        } else {
            vista.setEstado("El producto ya no existe; actualiza la lista");
        }
    }

    private void refrescaCatalogo() {
        if (usuarioConectado == null) {
            vista.setEstado("Conéctate primero");
            return;
        }
        ejecuta("No se pudo obtener el catálogo", () -> {
            CatalogoResponse catalogo = remoto.consultarCatalogo(
                    ConsultaCatalogoRequest.getDefaultInstance());
            SwingUtilities.invokeLater(() -> {
                pintaCatalogo(catalogo);
                vista.setEstado("Catálogo actualizado: "
                        + catalogo.getProductosCount() + " producto(s)");
            });
        });
    }

    private void pintaCatalogo(CatalogoResponse catalogo) {
        vista.reinicializaListaProductos();
        vista.desplegarPrecio("");
        for (Producto producto : catalogo.getProductosList()) {
            float precio = precios.merge(producto.getNombre(),
                    producto.getPrecioActual(), Math::max);
            vista.agregaProducto(producto.getNombre());
            if (vista.getProductoSeleccionado() != null
                    && vista.getProductoSeleccionado().equals(producto.getNombre())) {
                vista.desplegarPrecio(String.valueOf(precio));
            }
        }
    }

    private void recibeProducto(Producto producto) {
        precios.merge(producto.getNombre(), producto.getPrecioActual(), Math::max);
        SwingUtilities.invokeLater(() -> {
            if (usuarioConectado == null) {
                return;
            }
            vista.agregaProducto(producto.getNombre());
            if (producto.getNombre().equals(vista.getProductoSeleccionado())) {
                muestraPrecio(producto.getNombre());
            }
        });
    }

    private void aplicaPrecio(String nombre, float precio) {
        precios.merge(nombre, precio, Math::max);
        muestraPrecio(nombre);
    }

    private void muestraPrecio(String nombre) {
        if (nombre.equals(vista.getProductoSeleccionado())) {
            Float precio = precios.get(nombre);
            vista.desplegarPrecio(precio == null ? "" : String.valueOf(precio));
        }
    }

    private void limpiaSesion(boolean fallo) {
        usuarioConectado = null;
        vista.reinicializaListaProductos();
        vista.desplegarPrecio("");
        vista.habilitaSesion(false);
        vista.setEstado(fallo
                ? "Desconectado localmente; el servidor no respondió"
                : "Desconectado");
    }

    private void cancelaSuscripcion() {
        ClientCallStreamObserver<SuscripcionRequest> activa = suscripcion;
        suscripcion = null;
        if (activa != null) {
            activa.cancel("El usuario cerró la suscripción", null);
        }
    }

    private void ejecuta(String contexto, Runnable operacion) {
        ejecuta(contexto, operacion, null);
    }

    private void ejecuta(String contexto, Runnable operacion, Runnable alFallar) {
        solicitudes.execute(() -> {
            try {
                operacion.run();
            } catch (RuntimeException e) {
                if (alFallar != null) {
                    alFallar.run();
                }
                SwingUtilities.invokeLater(() ->
                        vista.setEstado(contexto + ": " + mensaje(e)));
            }
        });
    }

    private static boolean esPrecioValido(float precio) {
        return Float.isFinite(precio) && precio > 0;
    }

    private static String mensaje(Throwable error) {
        if (error instanceof io.grpc.StatusRuntimeException estado
                && estado.getStatus().getCode() == Status.Code.UNAVAILABLE) {
            return "servidor no disponible";
        }
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    @Override
    public void valueChanged(ListSelectionEvent evento) {
        if (evento.getValueIsAdjusting()) {
            return;
        }
        JList<?> lista = (JList<?>) evento.getSource();
        Object seleccionado = lista.getSelectedValue();
        if (seleccionado != null) {
            muestraPrecio(seleccionado.toString());
        }
    }
}
