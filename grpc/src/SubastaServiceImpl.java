import grpc.generated.CatalogoResponse;
import grpc.generated.ConsultaCatalogoRequest;
import grpc.generated.DesconexionRequest;
import grpc.generated.DesconexionResponse;
import grpc.generated.OfertaRequest;
import grpc.generated.OfertaResponse;
import grpc.generated.Producto;
import grpc.generated.ProductoActualizado;
import grpc.generated.PublicarProductoRequest;
import grpc.generated.PublicarProductoResponse;
import grpc.generated.RegistroUsuarioRequest;
import grpc.generated.RegistroUsuarioResponse;
import grpc.generated.ResultadoOferta;
import grpc.generated.SubastaServiceGrpc;
import grpc.generated.SuscripcionRequest;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SubastaServiceImpl extends SubastaServiceGrpc.SubastaServiceImplBase
        implements AutoCloseable {

    private final Object lock = new Object();
    private final Map<String, ProductoSubasta> productos = new HashMap<>();
    private final Set<String> usuariosActivos = new HashSet<>();
    private final Map<String, ServerCallStreamObserver<ProductoActualizado>> suscriptores =
            new HashMap<>();
    private boolean cerrado;
    private final ExecutorService emisorEventos = Executors.newSingleThreadExecutor(r -> {
        Thread hilo = new Thread(r, "grpc-eventos-subasta");
        hilo.setDaemon(true);
        return hilo;
    });

    @Override
    public void registrarUsuario(RegistroUsuarioRequest request,
                                 StreamObserver<RegistroUsuarioResponse> observer) {
        String usuario = request.getUsuario().trim();
        if (usuario.isEmpty()) {
            respondeError(observer, Status.INVALID_ARGUMENT, "El usuario es obligatorio");
            return;
        }

        boolean registrado;
        synchronized (lock) {
            registrado = usuariosActivos.add(usuario);
        }
        observer.onNext(RegistroUsuarioResponse.newBuilder()
                .setRegistrado(registrado)
                .setMensaje(registrado ? "Sesión iniciada" : "Ese usuario ya tiene una sesión activa")
                .build());
        observer.onCompleted();
    }

    @Override
    public void publicarProducto(PublicarProductoRequest request,
                                StreamObserver<PublicarProductoResponse> observer) {
        String vendedor = request.getVendedor().trim();
        String nombre = request.getProducto().trim();
        float inicial = request.getPrecioInicial();
        if (vendedor.isEmpty() || nombre.isEmpty() || !esPrecioValido(inicial)) {
            respondeError(observer, Status.INVALID_ARGUMENT,
                    "Vendedor, producto y un precio inicial finito mayor que cero son obligatorios");
            return;
        }

        ProductoSubasta producto;
        synchronized (lock) {
            if (productos.containsKey(nombre)) {
                observer.onNext(PublicarProductoResponse.newBuilder()
                        .setPublicado(false)
                        .setMensaje("Ya existe un producto con ese nombre")
                        .build());
                observer.onCompleted();
                return;
            }
            producto = new ProductoSubasta(vendedor, nombre, inicial);
            productos.put(nombre, producto);
            emiteCambio(producto);
        }

        observer.onNext(PublicarProductoResponse.newBuilder()
                .setPublicado(true)
                .setMensaje("Producto publicado")
                .build());
        observer.onCompleted();
    }

    @Override
    public void consultarCatalogo(ConsultaCatalogoRequest request,
                                  StreamObserver<CatalogoResponse> observer) {
        CatalogoResponse.Builder catalogo = CatalogoResponse.newBuilder();
        synchronized (lock) {
            for (ProductoSubasta producto : productos.values()) {
                catalogo.addProductos(convierte(producto));
            }
        }
        observer.onNext(catalogo.build());
        observer.onCompleted();
    }

    @Override
    public void ofertar(OfertaRequest request, StreamObserver<OfertaResponse> observer) {
        String comprador = request.getComprador().trim();
        String nombre = request.getProducto().trim();
        float monto = request.getMonto();
        if (comprador.isEmpty() || nombre.isEmpty() || !esPrecioValido(monto)) {
            respondeError(observer, Status.INVALID_ARGUMENT,
                    "Comprador, producto y un monto finito mayor que cero son obligatorios");
            return;
        }

        OfertaResponse respuesta;
        synchronized (lock) {
            ProductoSubasta producto = productos.get(nombre);
            if (producto == null) {
                respuesta = OfertaResponse.newBuilder()
                        .setResultado(ResultadoOferta.OFERTA_PRODUCTO_NO_EXISTE)
                        .setMensaje("El producto no existe")
                        .build();
            } else if (!producto.actualizaPrecio(monto)) {
                respuesta = OfertaResponse.newBuilder()
                        .setResultado(ResultadoOferta.OFERTA_MONTO_INSUFICIENTE)
                        .setPrecioActual(producto.getPrecioActual())
                        .setMensaje("La oferta debe ser mayor que el precio actual")
                        .build();
            } else {
                respuesta = OfertaResponse.newBuilder()
                        .setResultado(ResultadoOferta.OFERTA_ACEPTADA)
                        .setPrecioActual(producto.getPrecioActual())
                        .setMensaje("Oferta aceptada")
                        .build();
                emiteCambio(producto);
            }
        }
        observer.onNext(respuesta);
        observer.onCompleted();
    }

    @Override
    public void suscribirCambios(SuscripcionRequest request,
                                 StreamObserver<ProductoActualizado> observer) {
        String usuario = request.getUsuario().trim();
        if (usuario.isEmpty()) {
            respondeError(observer, Status.INVALID_ARGUMENT, "El usuario es obligatorio");
            return;
        }
        if (!(observer instanceof ServerCallStreamObserver<?>)) {
            respondeError(observer, Status.INTERNAL, "El servidor requiere un stream gRPC");
            return;
        }

        @SuppressWarnings("unchecked")
        ServerCallStreamObserver<ProductoActualizado> stream =
                (ServerCallStreamObserver<ProductoActualizado>) observer;
        synchronized (lock) {
            if (!usuariosActivos.contains(usuario)) {
                respondeError(observer, Status.FAILED_PRECONDITION,
                        "Registra el usuario antes de suscribirte");
                return;
            }
            if (suscriptores.containsKey(usuario)) {
                respondeError(observer, Status.ALREADY_EXISTS,
                        "El usuario ya tiene una suscripción activa");
                return;
            }
            stream.setOnCancelHandler(() -> eliminaSuscriptor(usuario, stream, true));
            if (stream.isCancelled()) {
                usuariosActivos.remove(usuario);
                return;
            }
            suscriptores.put(usuario, stream);
            if (stream.isCancelled()) {
                eliminaSuscriptor(usuario, stream, true);
                return;
            }
            for (ProductoSubasta producto : productos.values()) {
                encolaEvento(usuario, stream, actualizacion(producto));
            }
        }
    }

    @Override
    public void desconectar(DesconexionRequest request,
                            StreamObserver<DesconexionResponse> observer) {
        String usuario = request.getUsuario().trim();
        if (usuario.isEmpty()) {
            respondeError(observer, Status.INVALID_ARGUMENT, "El usuario es obligatorio");
            return;
        }

        ServerCallStreamObserver<ProductoActualizado> stream;
        synchronized (lock) {
            usuariosActivos.remove(usuario);
            stream = suscriptores.remove(usuario);
        }
        if (stream != null) {
            emisorEventos.execute(() -> {
                if (!stream.isCancelled()) {
                    stream.onCompleted();
                }
            });
        }
        observer.onNext(DesconexionResponse.newBuilder().setDesconectado(true).build());
        observer.onCompleted();
    }

    private void emiteCambio(ProductoSubasta producto) {
        ProductoActualizado evento = actualizacion(producto);
        for (Map.Entry<String, ServerCallStreamObserver<ProductoActualizado>> destino
                : new ArrayList<>(suscriptores.entrySet())) {
            encolaEvento(destino.getKey(), destino.getValue(), evento);
        }
    }

    private void encolaEvento(String usuario,
                              ServerCallStreamObserver<ProductoActualizado> stream,
                              ProductoActualizado evento) {
        emisorEventos.execute(() -> {
            if (stream.isCancelled()) {
                return;
            }
            try {
                stream.onNext(evento);
            } catch (RuntimeException e) {
                System.err.println("Se cerró un stream de subasta: " + e.getMessage());
                eliminaSuscriptor(usuario, stream, true);
            }
        });
    }

    private void eliminaSuscriptor(String usuario,
                                   ServerCallStreamObserver<ProductoActualizado> stream,
                                   boolean liberaSesion) {
        synchronized (lock) {
            if (suscriptores.get(usuario) == stream) {
                suscriptores.remove(usuario);
                if (liberaSesion) {
                    usuariosActivos.remove(usuario);
                }
            }
        }
    }

    private static Producto convierte(ProductoSubasta producto) {
        return Producto.newBuilder()
                .setVendedor(producto.getVendedor())
                .setNombre(producto.getNombre())
                .setPrecioInicial(producto.getPrecioInicial())
                .setPrecioActual(producto.getPrecioActual())
                .build();
    }

    private static ProductoActualizado actualizacion(ProductoSubasta producto) {
        return ProductoActualizado.newBuilder()
                .setProducto(convierte(producto))
                .setVersion(producto.getVersion())
                .build();
    }

    private static boolean esPrecioValido(float precio) {
        return Float.isFinite(precio) && precio > 0;
    }

    private static void respondeError(StreamObserver<?> observer, Status status, String mensaje) {
        observer.onError(status.withDescription(mensaje).asRuntimeException());
    }

    @Override
    public void close() {
        List<ServerCallStreamObserver<ProductoActualizado>> streams;
        synchronized (lock) {
            if (cerrado) {
                return;
            }
            cerrado = true;
            streams = new ArrayList<>(suscriptores.values());
            suscriptores.clear();
            usuariosActivos.clear();
        }
        for (ServerCallStreamObserver<ProductoActualizado> stream : streams) {
            emisorEventos.execute(() -> {
                if (!stream.isCancelled()) {
                    stream.onCompleted();
                }
            });
        }
        emisorEventos.shutdown();
    }
}
