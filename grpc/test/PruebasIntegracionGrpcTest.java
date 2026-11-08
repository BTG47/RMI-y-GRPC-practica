import grpc.generated.ConsultaCatalogoRequest;
import grpc.generated.OfertaRequest;
import grpc.generated.ProductoActualizado;
import grpc.generated.PublicarProductoRequest;
import grpc.generated.RegistroUsuarioRequest;
import grpc.generated.ResultadoOferta;
import grpc.generated.SubastaServiceGrpc;
import grpc.generated.SuscripcionRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PruebasIntegracionGrpcTest {

    private Server servidor;
    private ManagedChannel canal;
    private SubastaServiceGrpc.SubastaServiceBlockingStub remoto;
    private SubastaServiceGrpc.SubastaServiceStub remotoAsincrono;
    private SubastaServiceImpl servicio;

    @BeforeEach
    void inicia() throws Exception {
        servicio = new SubastaServiceImpl();
        servidor = ServerBuilder.forPort(0).addService(servicio).build().start();
        canal = ManagedChannelBuilder.forAddress("localhost", servidor.getPort())
                .usePlaintext().build();
        remoto = SubastaServiceGrpc.newBlockingStub(canal);
        remotoAsincrono = SubastaServiceGrpc.newStub(canal);
    }

    @AfterEach
    void cierra() throws Exception {
        canal.shutdownNow();
        canal.awaitTermination(5, TimeUnit.SECONDS);
        servidor.shutdownNow();
        servidor.awaitTermination(5, TimeUnit.SECONDS);
        servicio.close();
    }

    @Test
    void ofertaValidaYEntregaCambiosEnVivoAClientesIndependientes() throws Exception {
        assertTrue(registra("Ana"));
        assertTrue(registra("Beto"));
        assertFalse(registra("Ana"));
        assertThrows(StatusRuntimeException.class, () ->
                remoto.publicarProducto(PublicarProductoRequest.newBuilder()
                        .setVendedor("Ana").setProducto(" ").setPrecioInicial(10).build()));

        ArrayBlockingQueue<ProductoActualizado> eventosAna = new ArrayBlockingQueue<>(20);
        ArrayBlockingQueue<ProductoActualizado> eventosBeto = new ArrayBlockingQueue<>(20);
        suscribe("Ana", eventosAna);
        suscribe("Beto", eventosBeto);

        assertTrue(remoto.publicarProducto(PublicarProductoRequest.newBuilder()
                .setVendedor("Ana").setProducto("Libro").setPrecioInicial(50).build())
                .getPublicado());
        assertFalse(remoto.publicarProducto(PublicarProductoRequest.newBuilder()
                .setVendedor("Beto").setProducto("Libro").setPrecioInicial(60).build())
                .getPublicado());
        assertEquals(50.0f, esperaEvento(eventosAna, "Libro").getProducto().getPrecioActual());
        assertEquals(50.0f, esperaEvento(eventosBeto, "Libro").getProducto().getPrecioActual());

        assertEquals(ResultadoOferta.OFERTA_PRODUCTO_NO_EXISTE,
                remoto.ofertar(oferta("Beto", "NoExiste", 75)).getResultado());
        var igual = remoto.ofertar(oferta("Beto", "Libro", 50));
        assertEquals(ResultadoOferta.OFERTA_MONTO_INSUFICIENTE, igual.getResultado());
        assertTrue(eventosAna.isEmpty());

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        try {
            Future<?> ofertaBaja = hilos.submit(() -> {
                espera(inicio);
                return remoto.ofertar(oferta("Ana", "Libro", 100));
            });
            Future<?> ofertaAlta = hilos.submit(() -> {
                espera(inicio);
                return remoto.ofertar(oferta("Beto", "Libro", 200));
            });
            inicio.countDown();
            ofertaBaja.get(5, TimeUnit.SECONDS);
            ofertaAlta.get(5, TimeUnit.SECONDS);
        } finally {
            hilos.shutdownNow();
        }

        assertEquals(200.0f, precioCatalogo("Libro"));
        ProductoActualizado actualizacion = esperaEvento(eventosAna, "Libro", 200);
        assertEquals(200.0f, actualizacion.getProducto().getPrecioActual());
        assertEquals(200.0f,
                esperaEvento(eventosBeto, "Libro", 200).getProducto().getPrecioActual());

        while (eventosAna.poll() != null) {
            // Descarta eventos intermedios de la oferta concurrente.
        }
        var menor = remoto.ofertar(oferta("Ana", "Libro", 199));
        assertEquals(ResultadoOferta.OFERTA_MONTO_INSUFICIENTE, menor.getResultado());
        assertEquals(200.0f, menor.getPrecioActual());
        assertNull(eventosAna.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void suscripcionRequiereRegistroYDesconexionLiberaElNombre() throws Exception {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class, () -> {
            var eventos = SubastaServiceGrpc.newBlockingStub(canal).suscribirCambios(
                    SuscripcionRequest.newBuilder().setUsuario("sin-registro").build());
            eventos.hasNext();
        });
        assertEquals(Status.Code.FAILED_PRECONDITION, error.getStatus().getCode());
        assertTrue(registra("Carla"));
        remoto.desconectar(grpc.generated.DesconexionRequest.newBuilder()
                .setUsuario("Carla").build());
        assertTrue(registra("Carla"));
    }

    @Test
    void cancelarElStreamLiberaLaSesion() throws Exception {
        assertTrue(registra("Diana"));
        AtomicReference<ClientCallStreamObserver<SuscripcionRequest>> stream =
                new AtomicReference<>();
        CountDownLatch listo = new CountDownLatch(1);
        remotoAsincrono.suscribirCambios(SuscripcionRequest.newBuilder()
                        .setUsuario("Diana").build(),
                new ClientResponseObserver<SuscripcionRequest, ProductoActualizado>() {
                    @Override
                    public void beforeStart(
                            ClientCallStreamObserver<SuscripcionRequest> requestStream) {
                        stream.set(requestStream);
                        listo.countDown();
                    }

                    @Override
                    public void onNext(ProductoActualizado value) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
        assertTrue(listo.await(2, TimeUnit.SECONDS));
        stream.get().cancel("fin de prueba", null);

        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        boolean reconectado = false;
        while (!reconectado && System.nanoTime() < limite) {
            reconectado = registra("Diana");
            if (!reconectado) {
                Thread.sleep(25);
            }
        }
        assertTrue(reconectado, "La cancelación del stream debe liberar el usuario");
    }

    private boolean registra(String usuario) {
        return remoto.registrarUsuario(RegistroUsuarioRequest.newBuilder()
                .setUsuario(usuario).build()).getRegistrado();
    }

    private void suscribe(String usuario, ArrayBlockingQueue<ProductoActualizado> eventos) {
        remotoAsincrono.suscribirCambios(SuscripcionRequest.newBuilder()
                .setUsuario(usuario).build(), new StreamObserver<>() {
            @Override
            public void onNext(ProductoActualizado value) {
                eventos.offer(value);
            }

            @Override
            public void onError(Throwable throwable) {
                eventos.offer(ProductoActualizado.getDefaultInstance());
            }

            @Override
            public void onCompleted() {
            }
        });
    }

    private static OfertaRequest oferta(String comprador, String producto, float monto) {
        return OfertaRequest.newBuilder().setComprador(comprador)
                .setProducto(producto).setMonto(monto).build();
    }

    private static ProductoActualizado esperaEvento(
            ArrayBlockingQueue<ProductoActualizado> eventos, String nombre) throws Exception {
        ProductoActualizado evento = eventos.poll(5, TimeUnit.SECONDS);
        assertNotNull(evento, "No se recibió el evento dentro del plazo");
        while (!evento.getProducto().getNombre().equals(nombre)) {
            evento = eventos.poll(5, TimeUnit.SECONDS);
            assertNotNull(evento, "No se recibió el evento para " + nombre);
        }
        return evento;
    }

    private static ProductoActualizado esperaEvento(
            ArrayBlockingQueue<ProductoActualizado> eventos, String nombre, float precio)
            throws Exception {
        ProductoActualizado evento = esperaEvento(eventos, nombre);
        while (evento.getProducto().getPrecioActual() != precio) {
            evento = esperaEvento(eventos, nombre);
        }
        return evento;
    }

    private float precioCatalogo(String nombre) {
        return remoto.consultarCatalogo(ConsultaCatalogoRequest.getDefaultInstance())
                .getProductosList().stream()
                .filter(producto -> producto.getNombre().equals(nombre))
                .findFirst().orElseThrow().getPrecioActual();
    }

    private static void espera(CountDownLatch inicio) {
        try {
            inicio.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la prueba concurrente", e);
        }
    }
}
