import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PruebasIntegracion {

    private static final String URL_POR_DEFECTO = "rmi://localhost:1099/Subasta";

    private static RMIClientSocketFactory clienteTls;
    private static RMIServerSocketFactory servidorTls;

    public static void main(String[] args) {
        boolean tls = false;
        String url = URL_POR_DEFECTO;
        for (String arg : args) {
            if ("--tls".equals(arg)) {
                tls = true;
            } else {
                url = arg;
            }
        }

        if (tls) {
            clienteTls = FabricaTls.cliente();
            servidorTls = FabricaTls.servidor();
        }

        SubastaInterface subasta;
        try {
            subasta = tls
                    ? FabricaTls.lookup(url, clienteTls)
                    : (SubastaInterface) Naming.lookup(url);
        } catch (Exception e) {
            System.out.println("No se pudo conectar al servidor en " + url + ": " + e);
            System.out.println("Arranca 'rmiregistry' y 'ServidorMain' antes de ejecutar esta prueba.");
            System.exit(1);
            return;
        }

        Aserciones.reinicia();
        String base = "integracion-" + System.nanoTime();
        CallbackRemoto ana = null;
        CallbackRemoto beto = null;
        CallbackRemoto muerto = null;
        CallbackRemoto reemplazo = null;

        try {
            ana = creaCallback(false);
            beto = creaCallback(false);
            muerto = creaCallback(true);
            reemplazo = creaCallback(false);

            pruebaConexion(subasta, base, ana, beto);
            String producto = pruebaCatalogoYOfertas(subasta, base, ana, beto);
            pruebaConcurrencia(subasta, base);
            pruebaCallbackMuerto(subasta, base, producto, ana, beto, muerto, reemplazo);
            pruebaDesconexionYReconexion(subasta, base, ana);
        } catch (Exception e) {
            System.out.println("FALLO inesperado: " + e);
            e.printStackTrace();
            Aserciones.assertTrue("la prueba terminó sin excepción", false);
        } finally {
            cierraSesion(subasta, base);
            desexporta(ana);
            desexporta(beto);
            desexporta(muerto);
            desexporta(reemplazo);
        }

        Aserciones.resumen("pruebas de integración RMI");
    }

    private static void pruebaConexion(SubastaInterface subasta, String base,
                                       CallbackRemoto ana, CallbackRemoto beto)
            throws Exception {
        String nombreAna = base + "-Ana";
        Aserciones.assertTrue("conecta a Ana", subasta.conecta(nombreAna, ana));
        Aserciones.assertFalse("rechaza una segunda sesión de Ana",
                subasta.conecta(nombreAna, beto));
        Aserciones.assertTrue("conecta a Beto", subasta.conecta(base + "-Beto", beto));
    }

    private static String pruebaCatalogoYOfertas(SubastaInterface subasta, String base,
                                                 CallbackRemoto ana, CallbackRemoto beto)
            throws Exception {
        String producto = base + "-Libro";
        Aserciones.assertTrue("pone el producto a la venta",
                subasta.agregaProductoALaVenta(base + "-Ana", producto, 100.0f));
        Aserciones.assertFalse("rechaza un producto duplicado",
                subasta.agregaProductoALaVenta(base + "-Beto", producto, 90.0f));

        Aserciones.assertEquals("oferta igual rechazada", ResultadoOferta.MONTO_INSUFICIENTE,
                subasta.agregaOferta(base + "-Beto", producto, 100.0f));
        Aserciones.assertTrue("no hay notificación de la oferta rechazada",
                ana.eventos.poll(400, TimeUnit.MILLISECONDS) == null);

        Aserciones.assertEquals("oferta mayor aceptada", ResultadoOferta.ACEPTADA,
                subasta.agregaOferta(base + "-Beto", producto, 120.0f));
        Aserciones.assertEquals("Ana recibe la notificación", producto + "=120.0",
                ana.eventos.poll(2, TimeUnit.SECONDS));
        Aserciones.assertEquals("Beto recibe la notificación", producto + "=120.0",
                beto.eventos.poll(2, TimeUnit.SECONDS));

        Vector<InformacionProducto> catalogo = subasta.obtieneCatalogo();
        InformacionProducto encontrado = busca(catalogo, producto);
        Aserciones.assertTrue("el producto está en el catálogo", encontrado != null);
        if (encontrado != null) {
            Aserciones.assertEquals("vendedor en el catálogo", base + "-Ana",
                    encontrado.getVendedor());
            Aserciones.assertEquals("precio actual en el catálogo", 120.0f,
                    encontrado.getPrecioActual());
        }
        return producto;
    }

    private static void pruebaConcurrencia(SubastaInterface subasta, String base)
            throws Exception {
        int cantidad = 20;
        for (int i = 0; i < cantidad; i++) {
            Aserciones.assertTrue("producto concurrente " + i + " creado",
                    subasta.agregaProductoALaVenta(base + "-Ana", base + "-P" + i, 50.0f));
        }

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            Future<?> baja = hilos.submit(() -> ofertaATodos(subasta, base, cantidad, 100.0f));
            Future<?> alta = hilos.submit(() -> ofertaATodos(subasta, base, cantidad, 200.0f));
            baja.get(15, TimeUnit.SECONDS);
            alta.get(15, TimeUnit.SECONDS);
        } finally {
            hilos.shutdownNow();
        }

        Vector<InformacionProducto> catalogo = subasta.obtieneCatalogo();
        for (int i = 0; i < cantidad; i++) {
            InformacionProducto producto = busca(catalogo, base + "-P" + i);
            Aserciones.assertTrue("producto concurrente " + i + " en el catálogo",
                    producto != null);
            if (producto != null) {
                Aserciones.assertEquals("precio final concurrente " + i,
                        200.0f, producto.getPrecioActual());
            }
        }
    }

    private static void pruebaCallbackMuerto(SubastaInterface subasta, String base,
                                              String producto, CallbackRemoto ana,
                                              CallbackRemoto beto, CallbackRemoto muerto,
                                              CallbackRemoto reemplazo) throws Exception {
        String nombreMuerto = base + "-Muerto";
        Aserciones.assertTrue("conecta al cliente que fallará",
                subasta.conecta(nombreMuerto, muerto));

        Aserciones.assertEquals("oferta aceptada con cliente muerto conectado",
                ResultadoOferta.ACEPTADA,
                subasta.agregaOferta(base + "-Beto", producto, 150.0f));
        Aserciones.assertTrue("Ana recibe la oferta con el cliente muerto presente",
                esperaEvento(ana.eventos, producto + "=150.0", 3000));
        Aserciones.assertTrue("Beto recibe la oferta con el cliente muerto presente",
                esperaEvento(beto.eventos, producto + "=150.0", 3000));

        Aserciones.assertTrue("el callback muerto se elimina y el usuario puede reconectar",
                reconectaEnUnRato(subasta, nombreMuerto, reemplazo));
        subasta.desconecta(nombreMuerto);
    }

    private static void pruebaDesconexionYReconexion(SubastaInterface subasta, String base,
                                                      CallbackRemoto ana) throws Exception {
        String nombreAna = base + "-Ana";
        subasta.desconecta(nombreAna);
        Aserciones.assertTrue("Ana puede reconectar tras desconectarse",
                subasta.conecta(nombreAna, ana));
        subasta.desconecta(nombreAna);
        subasta.desconecta(nombreAna);
        Aserciones.assertTrue("desconectar dos veces no rompe el servidor",
                subasta.conecta(nombreAna, ana));
    }

    private static void ofertaATodos(SubastaInterface subasta, String base,
                                     int cantidad, float monto) {
        for (int i = 0; i < cantidad; i++) {
            try {
                subasta.agregaOferta(base + "-Beto", base + "-P" + i, monto);
            } catch (RemoteException e) {
                throw new CompletionException(e);
            }
        }
    }

    private static boolean reconectaEnUnRato(SubastaInterface subasta, String nombre,
                                              CallbackRemoto callback) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < limite) {
            if (subasta.conecta(nombre, callback)) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    private static boolean esperaEvento(BlockingQueue<String> eventos, String esperado,
                                        long milisegundos) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milisegundos);
        while (true) {
            long restante = TimeUnit.NANOSECONDS.toMillis(limite - System.nanoTime());
            if (restante <= 0) {
                return false;
            }
            String evento = eventos.poll(restante, TimeUnit.MILLISECONDS);
            if (evento == null) {
                return false;
            }
            if (evento.equals(esperado)) {
                return true;
            }
        }
    }

    private static InformacionProducto busca(Vector<InformacionProducto> catalogo,
                                             String nombre) {
        for (InformacionProducto producto : catalogo) {
            if (producto.getNombreProducto().equals(nombre)) {
                return producto;
            }
        }
        return null;
    }

    private static void cierraSesion(SubastaInterface subasta, String base) {
        for (String sufijo : new String[]{"-Ana", "-Beto", "-Muerto"}) {
            try {
                subasta.desconecta(base + sufijo);
            } catch (RemoteException e) {
                System.out.println("Aviso: no se pudo desconectar " + base + sufijo + ": " + e);
            }
        }
    }

    private static CallbackRemoto creaCallback(boolean falla) throws RemoteException {
        if (clienteTls != null) {
            return new CallbackRemoto(falla, clienteTls, servidorTls);
        }
        return new CallbackRemoto(falla);
    }

    private static void desexporta(CallbackRemoto callback) {
        if (callback != null) {
            try {
                UnicastRemoteObject.unexportObject(callback, true);
            } catch (Exception e) {
                // Ya no estaba exportado.
            }
        }
    }

    private static class CallbackRemoto extends UnicastRemoteObject
            implements ClienteCallbackInterface {

        private static final long serialVersionUID = 1L;

        final BlockingQueue<String> eventos = new LinkedBlockingQueue<>();
        private final boolean falla;

        CallbackRemoto(boolean falla) throws RemoteException {
            this.falla = falla;
        }

        CallbackRemoto(boolean falla, RMIClientSocketFactory csf,
                       RMIServerSocketFactory ssf) throws RemoteException {
            super(0, csf, ssf);
            this.falla = falla;
        }

        @Override
        public void actualizaPrecio(String producto, float nuevoPrecio) throws RemoteException {
            eventos.offer(producto + "=" + nuevoPrecio);
            if (falla) {
                throw new RemoteException("fallo simulado");
            }
        }
    }
}
