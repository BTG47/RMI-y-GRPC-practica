import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import grpc.generated.ConsultaCatalogoRequest;
import grpc.generated.OfertaRequest;
import grpc.generated.PublicarProductoRequest;
import grpc.generated.RegistroUsuarioRequest;
import grpc.generated.SubastaServiceGrpc;
import grpc.generated.SuscripcionRequest;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;

public final class PrincipalServidor {

    private PrincipalServidor() {
    }

    public static void main(String[] args) throws InterruptedException {
        String host = args.length > 0 ? args[0] : "localhost";
        int puerto = args.length > 1 ? Integer.parseInt(args[1]) : 50051;
        String base = "demo-" + System.nanoTime();
        ManagedChannel canal = ManagedChannelBuilder.forAddress(host, puerto)
                .usePlaintext()
                .build();
        SubastaServiceGrpc.SubastaServiceBlockingStub remoto =
                SubastaServiceGrpc.newBlockingStub(canal);
        SubastaServiceGrpc.SubastaServiceStub remotoAsincrono =
                SubastaServiceGrpc.newStub(canal);
        CountDownLatch actualizado = new CountDownLatch(1);
        boolean registrado = false;
        try {
            if (!remoto.registrarUsuario(RegistroUsuarioRequest.newBuilder()
                    .setUsuario(base).build()).getRegistrado()) {
                throw new IllegalStateException("No se pudo registrar la sesión de demostración");
            }
            registrado = true;
            String nombreProducto = base + "-libro";
            remotoAsincrono.suscribirCambios(SuscripcionRequest.newBuilder()
                            .setUsuario(base).build(),
                    new StreamObserver<>() {
                        @Override
                        public void onNext(grpc.generated.ProductoActualizado evento) {
                            System.out.println("EVENTO: " + evento.getProducto().getNombre()
                                    + " = " + evento.getProducto().getPrecioActual()
                                    + " (v" + evento.getVersion() + ")");
                            if (evento.getProducto().getNombre().equals(nombreProducto)
                                    && evento.getProducto().getPrecioActual() == 125.0f) {
                                actualizado.countDown();
                            }
                        }

                        @Override
                        public void onError(Throwable error) {
                            System.err.println("Stream finalizado: " + error.getMessage());
                            actualizado.countDown();
                        }

                        @Override
                        public void onCompleted() {
                            actualizado.countDown();
                        }
                    });
            remoto.publicarProducto(PublicarProductoRequest.newBuilder()
                    .setVendedor(base)
                    .setProducto(nombreProducto)
                    .setPrecioInicial(100)
                    .build());
            remoto.ofertar(OfertaRequest.newBuilder()
                    .setComprador(base)
                    .setProducto(nombreProducto)
                    .setMonto(125)
                    .build());
            remoto.consultarCatalogo(ConsultaCatalogoRequest.getDefaultInstance())
                    .getProductosList().forEach(producto ->
                            System.out.println(producto.getNombre() + ": "
                                    + producto.getPrecioActual()));
            if (!actualizado.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("No se recibió el evento de actualización");
            }
        } finally {
            if (registrado) {
                try {
                    remoto.withDeadlineAfter(2, java.util.concurrent.TimeUnit.SECONDS)
                            .desconectar(grpc.generated.DesconexionRequest.newBuilder()
                                    .setUsuario(base).build());
                } catch (RuntimeException e) {
                    System.err.println("No se pudo cerrar la sesión de demostración: "
                            + e.getMessage());
                }
            }
            canal.shutdown();
            if (!canal.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                canal.shutdownNow();
            }
        }
    }
}
