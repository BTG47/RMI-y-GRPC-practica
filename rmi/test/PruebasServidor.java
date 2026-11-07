import java.rmi.RemoteException;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PruebasServidor {

    public static void main(String[] args) throws Exception {
        Aserciones.reinicia();
        pruebaSesionUnica();
        pruebaNotificacionDeOfertaAceptada();
        pruebaOfertaRechazadaNoNotifica();
        pruebaNotificacionDiferida();
        pruebaCallbackMuerto();
        pruebaConcurrencia();
        Aserciones.resumen("pruebas del servidor");
    }

    private static void pruebaSesionUnica() throws Exception {
        SubastaServidorImpl servidor = nuevoServidor();
        try {
            CallbackFalso callback = new CallbackFalso();

            Aserciones.assertTrue("conecta a Ana", servidor.conecta("Ana", callback));
            Aserciones.assertFalse("rechaza una segunda sesión de Ana",
                    servidor.conecta("Ana", new CallbackFalso()));

            servidor.desconecta("Ana");
            Aserciones.assertTrue("Ana puede reconectar tras desconectar",
                    servidor.conecta("Ana", callback));
            Aserciones.assertTrue("desconectar a alguien sin sesión no falla",
                    desconectaSinError(servidor, "Nadie"));
        } finally {
            servidor.cerrar();
        }
    }

    private static void pruebaNotificacionDeOfertaAceptada() throws Exception {
        SubastaServidorImpl servidor = nuevoServidor();
        try {
            CallbackFalso callback = new CallbackFalso();
            servidor.conecta("Ana", callback);
            servidor.agregaProductoALaVenta("Ana", "Libro", 100.0f);

            Aserciones.assertEquals("oferta aceptada", ResultadoOferta.ACEPTADA,
                    servidor.agregaOferta("Beto", "Libro", 120.0f));
            Aserciones.assertEquals("el callback recibe producto y precio",
                    "Libro=120.0", callback.eventos.poll(2, TimeUnit.SECONDS));
        } finally {
            servidor.cerrar();
        }
    }

    private static void pruebaOfertaRechazadaNoNotifica() throws Exception {
        SubastaServidorImpl servidor = nuevoServidor();
        try {
            CallbackFalso callback = new CallbackFalso();
            servidor.conecta("Ana", callback);
            servidor.agregaProductoALaVenta("Ana", "Libro", 100.0f);

            Aserciones.assertEquals("oferta igual rechazada",
                    ResultadoOferta.MONTO_INSUFICIENTE,
                    servidor.agregaOferta("Beto", "Libro", 100.0f));
            Aserciones.assertTrue("no hay notificación de una oferta rechazada",
                    callback.eventos.poll(400, TimeUnit.MILLISECONDS) == null);
        } finally {
            servidor.cerrar();
        }
    }

    private static void pruebaNotificacionDiferida() throws Exception {
        SubastaServidorImpl servidor = nuevoServidor();
        CallbackFalso lento = new CallbackFalso();
        lento.bloqueo = new CountDownLatch(1);
        try {
            servidor.conecta("Ana", lento);
            servidor.agregaProductoALaVenta("Ana", "Libro", 100.0f);

            CompletableFuture<ResultadoOferta> oferta = CompletableFuture.supplyAsync(() -> {
                try {
                    return servidor.agregaOferta("Beto", "Libro", 120.0f);
                } catch (RemoteException e) {
                    throw new CompletionException(e);
                }
            });

            Aserciones.assertEquals("la oferta vuelve sin esperar al callback",
                    ResultadoOferta.ACEPTADA, oferta.get(5, TimeUnit.SECONDS));
            Aserciones.assertTrue("el callback llegó a ejecutarse",
                    lento.eventos.poll(2, TimeUnit.SECONDS) != null);
            Aserciones.assertEquals("el callback sigue bloqueado", 1L, lento.bloqueo.getCount());
        } finally {
            lento.bloqueo.countDown();
            servidor.cerrar();
        }
    }

    private static void pruebaCallbackMuerto() throws Exception {
        SubastaServidorImpl servidor = nuevoServidor();
        try {
            CallbackFalso muerto = new CallbackFalso();
            muerto.lanzaError = true;
            CallbackFalso vivo = new CallbackFalso();
            CallbackFalso anaNueva = new CallbackFalso();

            servidor.conecta("Ana", muerto);
            servidor.conecta("Beto", vivo);
            servidor.agregaProductoALaVenta("Ana", "Libro", 100.0f);

            Aserciones.assertEquals("oferta aceptada", ResultadoOferta.ACEPTADA,
                    servidor.agregaOferta("Beto", "Libro", 120.0f));
            Aserciones.assertTrue("el cliente vivo recibe la notificación",
                    vivo.eventos.poll(2, TimeUnit.SECONDS) != null);

            Aserciones.assertTrue("el callback muerto se elimina y Ana puede reconectar",
                    reconectaEnUnRato(servidor, "Ana", anaNueva));

            Aserciones.assertEquals("la nueva sesión de Ana recibe la oferta",
                    ResultadoOferta.ACEPTADA,
                    servidor.agregaOferta("Ana", "Libro", 150.0f));
            Aserciones.assertTrue("Ana recibe la notificación",
                    anaNueva.eventos.poll(2, TimeUnit.SECONDS) != null);
            Thread.sleep(200);
            Aserciones.assertEquals("el callback muerto ya no recibe nada",
                    1, muerto.eventos.size());
        } finally {
            servidor.cerrar();
        }
    }

    private static void pruebaConcurrencia() throws Exception {
        SubastaServidorImpl servidor = nuevoServidor();
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 50; i++) {
                servidor.agregaProductoALaVenta("Ana", "Producto" + i, 50.0f);
            }

            Future<?> ofertaBaja = hilos.submit(() -> ofertaATodos(servidor, 100.0f));
            Future<?> ofertaAlta = hilos.submit(() -> ofertaATodos(servidor, 200.0f));
            ofertaBaja.get(10, TimeUnit.SECONDS);
            ofertaAlta.get(10, TimeUnit.SECONDS);

            Vector<InformacionProducto> catalogo = servidor.obtieneCatalogo();
            Aserciones.assertEquals("hay 50 productos", 50, catalogo.size());
            for (InformacionProducto producto : catalogo) {
                Aserciones.assertEquals("precio final de " + producto.getNombreProducto(),
                        200.0f, producto.getPrecioActual());
            }
        } finally {
            hilos.shutdownNow();
            servidor.cerrar();
        }
    }

    private static void ofertaATodos(SubastaServidorImpl servidor, float monto) {
        for (int i = 0; i < 50; i++) {
            try {
                servidor.agregaOferta("Beto", "Producto" + i, monto);
            } catch (RemoteException e) {
                throw new CompletionException(e);
            }
        }
    }

    private static boolean reconectaEnUnRato(SubastaServidorImpl servidor,
                                             String nombre,
                                             CallbackFalso callback) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            if (servidor.conecta(nombre, callback)) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private static boolean desconectaSinError(SubastaServidorImpl servidor, String nombre) {
        try {
            servidor.desconecta(nombre);
            return true;
        } catch (RemoteException e) {
            return false;
        }
    }

    private static SubastaServidorImpl nuevoServidor() throws RemoteException {
        return new SubastaServidorImpl(new SubastaModelo());
    }

    private static class CallbackFalso implements ClienteCallbackInterface {

        final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();
        volatile CountDownLatch bloqueo;
        volatile boolean lanzaError;

        @Override
        public void actualizaPrecio(String producto, float nuevoPrecio) throws RemoteException {
            eventos.offer(producto + "=" + nuevoPrecio);
            if (lanzaError) {
                throw new RemoteException("fallo simulado");
            }
            CountDownLatch actual = bloqueo;
            if (actual != null) {
                try {
                    actual.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
