# Subasta distribuida con Java RMI

Módulo RMI de la práctica: servidor centralizado, cliente Swing, DTOs serializables y
callbacks para avisar cambios de precio. No usa base de datos, dependencias externas ni
`rmic`; los stubs se generan dinámicamente con JDK 17.

## Requisitos

- JDK 17 (`javac` y `java` en el `PATH`).
- Linux (probado en Fedora).
- Todos los procesos en la misma máquina.
- No se necesita Maven, Gradle, JUnit ni ninguna librería externa.

## Estructura

```text
rmi/
  src/        fuentes de producción (paquete por defecto)
  test/       harness de pruebas en Java puro
  bin/        clases compiladas de producción
  bin-test/   clases compiladas de las pruebas
  run/        scripts de arranque y pruebas
  docs/       diagramas, evidencias y logs
```

Los seis `.java` originales de la raíz del repositorio no se modificaron; la
implementación RMI vive completa dentro de `rmi/`.

## Compilación

```bash
cd rmi
javac -d bin src/*.java
javac -cp bin -d bin-test test/*.java
```

## Arranque manual

Se necesitan tres procesos: registro, servidor y uno o más clientes.

### 1. Registro RMI

El registro debe poder cargar `SubastaInterface` y `ClienteCallbackInterface`. Por eso se
arranca **desde el directorio de clases**:

```bash
cd rmi/bin
rmiregistry 1099
```

Si se arranca desde otro directorio, `Naming.rebind` falla con
`ClassNotFoundException: SubastaInterface` (ver `docs/logs/registro-sin-clases.log`).

### 2. Servidor

En otra terminal, desde `rmi/`:

```bash
java -cp bin ServidorMain
```

Opcionalmente acepta puerto y nombre:

```bash
java -cp bin ServidorMain 1099 Subasta
```

El servidor imprime `Servidor de subastas publicado en rmi://localhost:1099/Subasta`.
Se cierra con `Ctrl+C`; el shutdown hook apaga el pool de notificaciones y desexporta el
objeto remoto.

### 3. Clientes

En una o más terminales, desde `rmi/`:

```bash
java -cp bin Principal
```

Opcionalmente acepta otra URL:

```bash
java -cp bin Principal rmi://localhost:1099/Subasta
```

## Uso del cliente

1. Escribe un nombre de usuario y pulsa **Conectar**.
2. Pulsa **Obtener lista** para ver el catálogo y los precios actuales.
3. Para publicar: escribe producto y precio inicial, y pulsa **Poner a la venta**.
4. Para ofertar: selecciona un producto de la lista, escribe el monto y pulsa **Ofrecer**.
5. **Desconectar** cierra la sesión sin cerrar la aplicación; se puede volver a conectar.
6. **Salir** intenta desconectar y termina el proceso.

Los callbacks actualizan el precio del producto seleccionado sin pulsar **Obtener lista**.
La etiqueta **Estado** muestra el resultado de cada operación y los errores de red.

## Reglas de negocio

- Una oferta solo se acepta si es **estrictamente mayor** que el precio actual.
- El vendedor puede ofertar por su propio producto.
- El servidor no exige que el comprador esté registrado para aceptar una oferta.
- Un nombre de usuario solo puede tener una sesión activa a la vez.
- No hay cierre por fecha ni adjudicación: es la versión simplificada del alcance.
- El estado autoritativo vive solo en el servidor; el cliente no tiene modelo de negocio
  local, solo una caché de precios para pintar la UI.

## Pruebas

### Suites automáticas

```bash
cd rmi
javac -d bin src/*.java
javac -cp bin -d bin-test test/*.java

java -cp bin:bin-test PruebasDTOs
java -cp bin:bin-test PruebasModelo
java -cp bin:bin-test PruebasServidor
java -cp bin:bin-test PruebasCachePrecios
```

### Integración con registro y servidor reales

```bash
cd rmi
./run/pruebas.sh
```

El script compila, arranca `rmiregistry` desde `bin` si el puerto está libre, arranca
`ServidorMain`, ejecuta `PruebasIntegracion` y limpia los procesos. Acepta puerto y nombre:

```bash
./run/pruebas.sh 1099 Subasta
```

Si el puerto ya está ocupado, reutiliza el registro existente y solo arranca el servidor.
Resultado esperado: `OK: pruebas de integración RMI (80 comprobaciones)`.

Los logs de la última ejecución están en `docs/logs/`.

## Concurrencia y fallos

- El adaptador remoto usa un **lock global**. Dentro del lock se ejecutan la comprobación y
  el commit de la oferta, y se copia la lista de callbacks; fuera del lock se notifica.
- Las notificaciones usan un pool fijo de 4 hilos, una tarea por callback. Una oferta no
  espera a que los clientes terminen de pintar.
- Si un callback lanza `RemoteException`, el servidor elimina esa sesión y sigue notificando
  a los demás. La oferta aceptada no se revierte.
- Si el cliente cae sin desconectarse, su sesión se libera cuando el servidor intenta
  notificarle y el callback falla. No hay heartbeat: si no ocurre ninguna oferta posterior,
  el nombre puede quedar bloqueado hasta reiniciar el servidor.
- El cliente no se reconecta automáticamente. Ante un fallo, muestra el error y permite
  reintentar **Conectar** manualmente.

## Limitaciones conocidas

- Sin TLS ni autenticación: es un extra opcional fuera del alcance actual.
- Sin persistencia: al reiniciar el servidor se pierden usuarios, productos y ofertas.
- Las llamadas remotas del cliente se hacen en el EDT (decisión aceptada); una red lenta
  puede congelar la UI mientras dura la llamada.
- El callback solo transporta `(producto, nuevoPrecio)`. Si llega un producto que el cliente
  no conoce, el precio se guarda en caché pero no se pinta una fila hasta el próximo
  **Obtener lista**.
- Sin cierre por fecha, adjudicación ni historial de ofertas en la UI.

## Problemas frecuentes

| Síntoma | Causa / solución |
|---|---|
| `ClassNotFoundException: SubastaInterface` al publicar | `rmiregistry` no ve las clases. Arráncalo desde `rmi/bin`. |
| `Connection refused` al conectar | El registro o el servidor no están corriendo, o el puerto no coincide. |
| `Ese usuario ya tiene una sesión activa` | Otra ventana usa ese nombre, o quedó una sesión de un cliente caído. Usa otro nombre o reinicia el servidor. |
| `No se pudo conectar: ...` | El servidor no está publicado; la ventana sigue abierta para reintentar. |
| `java.net.BindException: Address already in use` | Ya hay un registro en ese puerto; reutilízalo o usa otro. |
| El servidor no refleja cambios o hay dos servidores | Un `ServidorMain` anterior sigue vivo y conserva su objeto exportado; ciérralo antes de arrancar otro. `rebind` solo reemplaza el nombre en el registro. |
| El servidor no ve el puerto esperado | `ServidorMain [puerto] [nombre]` y `Principal [url]` deben coincidir. |
