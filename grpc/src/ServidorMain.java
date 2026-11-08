import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public final class ServidorMain {

    private static final int PUERTO_POR_DEFECTO = 50051;

    private ServidorMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : PUERTO_POR_DEFECTO;
        if (puerto < 1 || puerto > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535");
        }

        SubastaServiceImpl servicio = new SubastaServiceImpl();
        Server servidor = ServerBuilder.forPort(puerto)
                .addService(servicio)
                .build()
                .start();
        CountDownLatch cierre = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.shutdown();
            servicio.close();
            cierre.countDown();
        }, "cierre-subasta-grpc"));
        System.out.println("Servidor gRPC de subastas escuchando en el puerto "
                + servidor.getPort());
        cierre.await();
    }
}
