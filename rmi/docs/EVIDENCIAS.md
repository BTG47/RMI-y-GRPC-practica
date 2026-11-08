# Evidencias de las siete pruebas

Fecha de la última ejecución automática: **2026-11-07** (perfil plano y perfil TLS).

El harness cubre la lógica distribuida (servidor, callbacks, concurrencia y fallos). Las
pruebas que dependen de la interfaz Swing se documentan con capturas manuales en
`rmi/docs/capturas/`. No se afirma que una captura exista hasta que esté guardada allí.

## Resumen

| # | Prueba | Evidencia automática | Evidencia manual |
|---|---|---|---|
| 1 | Conexión | `PruebasIntegracion`: conexión, nombre duplicado, desconexión y reconexión; `PruebasServidor`: sesión única | Pendiente: dos ventanas con el mismo nombre |
| 2 | Productos | `PruebasIntegracion`: alta, duplicado y catálogo; `PruebasModelo`: reglas de alta | Pendiente: producto visible en dos clientes |
| 3 | Ofertas | `PruebasIntegracion`: oferta igual rechazada, oferta mayor aceptada, precio en catálogo; `PruebasModelo`: vendedor y no registrado | Pendiente: precio actualizado en la UI |
| 4 | Múltiples clientes | `PruebasIntegracion`: tres sesiones y catálogo compartido | Pendiente: tres ventanas sobre el mismo servidor |
| 5 | Callbacks | `PruebasIntegracion`: dos callbacks reciben el nuevo precio sin consultar el catálogo; `PruebasServidor`: notificación diferida | Pendiente: actualización visible sin pulsar Obtener lista |
| 6 | Concurrencia | `PruebasServidor`: 50 productos, ofertas 100/200 en paralelo, gana 200; `PruebasIntegracion`: 20 productos por RMI | Opcional: dos ventanas ofertando a la vez |
| 7 | Fallos | `PruebasServidor`: callback que lanza `RemoteException` se elimina y los demás siguen; `PruebasIntegracion`: reconexión tras callback muerto | Pendiente: matar un cliente, reconectar y apagar el servidor |

## Logs automáticos

| Archivo | Suite | Resultado |
|---|---|---|
| `logs/PruebasDTOs.log` | DTOs y serialización | `OK: pruebas de DTOs (19 comprobaciones)` |
| `logs/PruebasModelo.log` | Modelo puro | `OK: pruebas del modelo (18 comprobaciones)` |
| `logs/PruebasServidor.log` | Adaptador remoto | `OK: pruebas del servidor (68 comprobaciones)` |
| `logs/PruebasCachePrecios.log` | Caché de precios | `OK: pruebas de la caché de precios (8 comprobaciones)` |
| `logs/integracion.log` | Registro + servidor + cliente remoto | `OK: pruebas de integración RMI (80 comprobaciones)` |
| `logs/registro-sin-clases.log` | Caso negativo del registro | `ClassNotFoundException: SubastaInterface` |

### Reproducir

```bash
cd rmi
javac -d bin src/*.java
javac -cp bin -d bin-test test/*.java

for suite in PruebasDTOs PruebasModelo PruebasServidor PruebasCachePrecios; do
  java -cp bin:bin-test "$suite"
done

./run/pruebas.sh
```

El caso negativo del registro se reproduce arrancando `rmiregistry` en un directorio vacío
y publicando el servidor contra ese puerto:

```bash
mkdir -p /tmp/registro-vacio && cd /tmp/registro-vacio
rmiregistry 11100 &
cd -
java -cp rmi/bin ServidorMain 11100
# Esperado: ClassNotFoundException: SubastaInterface
```

## Perfil TLS (extra opcional)

El perfil TLS no reemplaza al plano: se ejecuta con `--tls` y un registro SSL
embebido en el puerto `1100`. Cubre registro, servicio y callbacks con TLS/mTLS.

### Logs automáticos TLS

| Archivo | Suite | Resultado |
|---|---|---|
| `logs/tls/pruebas-tls-unit.log` | Fábricas SSL, URL y transportes | `OK: pruebas TLS (10 comprobaciones)` |
| `logs/tls/integracion-tls.log` | Registro + servicio + callbacks TLS | `OK: pruebas de integración RMI (80 comprobaciones)` |
| `logs/tls/servidor-tls.log` | Servidor TLS con registro embebido | publicado en `rmi://localhost:1100/Subasta` |
| `logs/tls/servidor-tls-negativas.log` | Servidor aislado de las negativas | publicado en `rmi://localhost:1101/Subasta` |

### Pruebas negativas TLS

Las tres deben ser **rechazadas** en el handshake, no aceptadas ni fallar por
otra causa:

| Caso | Log | Señal observada |
|---|---|---|
| Cliente sin certificado | `logs/tls/negativa-sin-certificado-cliente.log` | `certificate_required` |
| Certificado firmado por otra CA | `logs/tls/negativa-certificado-de-otra-ca.log` | `certificate_required`, o cierre de conexión (`Tubería rota` / `Connection reset`) |
| Hostname fuera del SAN (`127.0.0.2`) | `logs/tls/negativa-hostname-incorrecto.log` | `No subject alternative names matching IP address 127.0.0.2 found` |

En el caso de otra CA, el servidor puede cerrar el socket antes de que el
cliente lea la alerta TLS. El script acepta ese cierre como rechazo solo si el
servidor sigue vivo, y después comprueba con un cliente legítimo que el servicio
sigue usable (`logs/tls/negativas-cliente-valido.log`).

Reproducir:

```bash
cd rmi
./run/pruebas-tls.sh
./run/pruebas-tls-negativas.sh
```

La guía para ejecutar T1 y revisar T2 está en `docs/TAREAS-TLS.md`. El modelo de
seguridad, los límites y la matriz keystore/truststore están en
`docs/SEGURIDAD.md`.

## Guion manual de las siete pruebas

Preparación común:

```bash
cd rmi
javac -d bin src/*.java
cd bin && rmiregistry 1099 &   # debe arrancar desde rmi/bin
cd ..
java -cp bin ServidorMain
```

En otras terminales, dos o tres clientes:

```bash
java -cp bin Principal
```

Guardar cada captura en `docs/capturas/` con el nombre indicado.

### 1. Conexión

1. Cliente A: nombre `Ana`, **Conectar**. Captura `01-conexion-ana.png`.
2. Cliente B: nombre `Ana`, **Conectar**. Debe mostrar
   `Ese usuario ya tiene una sesión activa`. Captura `01-conexion-duplicada.png`.
3. Cliente A: **Desconectar** y luego **Conectar** otra vez con `Ana`. Debe funcionar.
   Captura `01-reconexion.png`.

### 2. Productos

1. Cliente A conectado como `Ana` publica `Libro` con precio inicial `100`.
2. Cliente B conectado como `Beto` pulsa **Obtener lista**: debe ver `Libro`.
3. Cliente A intenta publicar `Libro` otra vez: debe rechazarse por nombre duplicado.
4. Captura `02-productos.png`.

### 3. Ofertas

1. Cliente B selecciona `Libro` y ofrece `100`. La UI debe indicar que la oferta debe ser
   mayor; el precio no cambia.
2. Cliente B ofrece `120`. Debe aceptarse y el precio actual debe mostrarse como `120.0`.
3. Cliente A (vendedor) ofrece `130`. Debe aceptarse.
4. Captura `03-ofertas.png`.

### 4. Múltiples clientes

1. Abrir un tercer cliente como `Carla` y conectar.
2. En las tres ventanas, **Obtener lista**: todas deben ver el mismo catálogo.
3. Captura `04-tres-clientes.png` mostrando las tres ventanas.

### 5. Callbacks

1. En las tres ventanas, **Obtener lista** y seleccionar el mismo producto `Libro`.
2. En la ventana de Beto, ofrecer `150`.
3. Ana y Carla deben actualizar el precio sin pulsar **Obtener lista**.
4. Captura `05-callbacks.png` con las tres ventanas y el precio `150.0`.

### 6. Concurrencia (opcional en la UI)

La prueba principal es automática (`PruebasServidor` e `PruebasIntegracion`). Para una
demostración visual, dos clientes ofrecen casi al mismo tiempo montos `200` y `300`; el
precio final debe ser `300` y no debe perderse ninguna actualización. Captura
`06-concurrencia.png` si se realiza.

### 7. Fallos

1. Con Ana, Beto y Carla conectados, cerrar abruptamente la ventana de Carla con
   `kill -9 <pid>` (no usar **Salir**).
2. Beto ofrece un monto mayor. El servidor no debe caerse y Ana/Beto deben seguir
   actualizando. Captura `07-cliente-caido.png`.
3. Carla vuelve a abrir el cliente, intenta conectar. Si la sesión muerta todavía no se
   liberó, usar otro nombre o provocar otra oferta; el callback muerto se elimina en el
   primer intento de notificación. Captura `07-reconexion.png`.
4. Apagar `ServidorMain` con `Ctrl+C` y pulsar **Obtener lista** en un cliente. Debe
   mostrarse un error de red y la ventana debe seguir abierta. Captura `07-servidor-caido.png`.

## Comprobación de los originales

Los seis archivos de la raíz se compararon con `SUBASTA(1).zip`:

```text
INTACTO InformacionOferta.java
INTACTO InformacionProducto.java
INTACTO Principal.java
INTACTO SubastaControlador.java
INTACTO SubastaModelo.java
INTACTO SubastaVista.java
```

## Qué falta para cerrar M006

- Guardar las capturas manuales indicadas en `rmi/docs/capturas/`.
- Revisar que la UI muestre los mensajes descritos.
- No se requiere benchmark: queda fuera del alcance de esta entrega.
