import java.util.concurrent.ConcurrentHashMap;

public class CachePrecios {

    private final ConcurrentHashMap<String, Float> precios = new ConcurrentHashMap<>();

    public void aplica(String producto, float precio) {
        precios.merge(producto, precio, Math::max);
    }

    public Float get(String producto) {
        return precios.get(producto);
    }

    public boolean contiene(String producto) {
        return precios.containsKey(producto);
    }

    public void limpia() {
        precios.clear();
    }
}
