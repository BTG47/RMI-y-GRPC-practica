import java.rmi.server.UnicastRemoteObject;

public class PruebasTls {

    public static void main(String[] args) throws Exception {
        Aserciones.reinicia();
        pruebaFabrica();
        pruebaUrl();
        pruebaTransportes();
        Aserciones.resumen("pruebas TLS");
    }

    private static void pruebaFabrica() {
        Aserciones.assertTrue("FabricaTls.cliente no es nulo",
                FabricaTls.cliente() != null);
        Aserciones.assertTrue("FabricaTls.servidor no es nulo",
                FabricaTls.servidor() != null);
        Aserciones.assertTrue("el servidor SSL exige certificado de cliente",
                FabricaTls.servidor().getNeedClientAuth());
    }

    private static void pruebaUrl() {
        FabricaTls.Destino destino = FabricaTls.parsea("rmi://localhost:1100/Subasta");
        Aserciones.assertEquals("host del URL TLS", "localhost", destino.host());
        Aserciones.assertEquals("puerto del URL TLS", 1100, destino.puerto());
        Aserciones.assertEquals("nombre del URL TLS", "Subasta", destino.nombre());

        FabricaTls.Destino porDefecto = FabricaTls.parsea("rmi://localhost/Subasta");
        Aserciones.assertEquals("puerto por defecto", 1099, porDefecto.puerto());

        boolean fallo = false;
        try {
            FabricaTls.parsea("http://localhost:1100/Subasta");
        } catch (IllegalArgumentException e) {
            fallo = true;
        }
        Aserciones.assertTrue("rechaza un esquema que no es rmi", fallo);
    }

    private static void pruebaTransportes() throws Exception {
        Transporte plano = new TransportePlano("rmi://localhost:1099/Subasta");
        ClienteCallbackImpl callbackPlano = plano.crearCallback(null);
        Aserciones.assertTrue("TransportePlano crea el callback", callbackPlano != null);
        UnicastRemoteObject.unexportObject(callbackPlano, true);

        Transporte tls = new TransporteTls("rmi://localhost:1100/Subasta");
        ClienteCallbackImpl callbackTls = tls.crearCallback(null);
        Aserciones.assertTrue("TransporteTls crea el callback", callbackTls != null);
        UnicastRemoteObject.unexportObject(callbackTls, true);
    }
}
