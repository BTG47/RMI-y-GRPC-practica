# Subasta distribuida con Java RMI

Proyecto académico para transformar una aplicación local de subastas Swing/MVC en un sistema distribuido cliente-servidor con Java RMI.

El módulo implementado en `rmi/` centraliza el estado y la lógica de negocio en un servidor, permite conectar varios clientes Swing independientes y notifica automáticamente los cambios de precio mediante callbacks RMI.

## Alcance

- Servidor centralizado con Java RMI y `rmiregistry`.
- Cliente gráfico Swing.
- Registro de usuarios y sesiones activas.
- Alta de productos y consulta del catálogo.
- Ofertas con resultados explícitos: aceptada, monto insuficiente o producto inexistente.
- Callbacks para actualizar precios sin polling.
- Manejo de ofertas concurrentes y callbacks fallidos.
- DTOs serializables e inmutables para la comunicación remota.
- Pruebas automáticas con Java puro, sin JUnit ni dependencias externas.

Este repositorio contiene el módulo RMI. La implementación de gRPC, TLS, autenticación, persistencia, cierre por fecha y adjudicación quedan fuera del alcance actual.

## Requisitos

- JDK 17 con `java` y `javac` disponibles en el `PATH`.
- Linux u otro sistema con Java RMI y Swing.
- No se requiere Maven, Gradle ni librerías externas.

## Estructura

```text
.
├── original/       Código original de referencia
├── rmi/
│   ├── src/        Código de producción
│   ├── test/       Pruebas Java puro
│   ├── bin/        Clases compiladas de producción
│   ├── bin-test/   Clases compiladas de las pruebas
│   ├── run/        Scripts de ejecución y pruebas de integración
│   └── docs/       Diagramas, evidencias, capturas y logs
├── .gitignore
└── README.md
```

La versión distribuida se desarrolla dentro de `rmi/`; los archivos de `original/` se conservan como referencia y no forman parte del arranque RMI.

## Arquitectura

El sistema se divide en dos partes:

- **Cliente:** `Principal`, `SubastaVista`, `SubastaControlador`, `ClienteCallbackImpl` y `CachePrecios`.
- **Servidor:** `ServidorMain`, `SubastaServidorImpl`, `SubastaModelo` y `Producto`.
- **Comunicación:** `SubastaInterface`, `ClienteCallbackInterface`, `InformacionProducto` e `InformacionOferta`.

`SubastaModelo` no conoce RMI. `SubastaServidorImpl` funciona como adaptador remoto y protege el modelo con un lock global. Las ofertas se confirman dentro del lock y las notificaciones se envían después mediante un pool fijo de cuatro hilos. Si un callback falla, se elimina esa sesión sin revertir la oferta aceptada.

Los DTOs cruzan la red como snapshots serializables. `Producto` permanece mutable y solo existe en el servidor.

## Compilación

Desde la raíz del proyecto:

```bash
cd rmi
javac -d bin src/*.java
javac -cp bin -d bin-test test/*.java
```

Los stubs RMI se generan dinámicamente con JDK 17; no se utiliza `rmic`.

## Ejecución manual

Se necesitan un registro RMI, el servidor y uno o más clientes.

### 1. Registro RMI

Debe iniciarse desde `rmi/bin` para que pueda encontrar las interfaces remotas:

```bash
cd rmi/bin
rmiregistry 1099
```

### 2. Servidor

En otra terminal:

```bash
cd rmi
java -cp bin ServidorMain
```

También se pueden indicar el puerto y el nombre del servicio:

```bash
java -cp bin ServidorMain 1099 Subasta
```

### 3. Cliente

En una o más terminales:

```bash
cd rmi
java -cp bin Principal
```

Para usar otra URL RMI:

```bash
java -cp bin Principal rmi://localhost:1099/Subasta
```

El cliente permite conectar y desconectar una sesión sin cerrar la ventana. Después de una desconexión se puede volver a conectar manualmente.

## Uso básico

1. Conectar con un nombre de usuario.
2. Obtener la lista de productos.
3. Publicar un producto con nombre y precio inicial.
4. Seleccionar un producto y realizar una oferta.
5. Observar la actualización automática del precio en los clientes conectados.
6. Desconectar o salir.

Reglas principales:

- Una oferta debe ser estrictamente mayor que el precio actual.
- El vendedor puede ofertar por su propio producto.
- No es obligatorio que el comprador esté registrado para ofertar.
- Solo puede existir una sesión activa por nombre de usuario.
- El estado autoritativo vive en el servidor.

## Pruebas

Las pruebas unitarias y de servidor se pueden ejecutar así:

```bash
cd rmi
for suite in PruebasDTOs PruebasModelo PruebasServidor PruebasCachePrecios; do
  java -cp bin:bin-test "$suite"
done
```

La integración con un registro y un servidor RMI reales se ejecuta con:

```bash
cd rmi
./run/pruebas.sh
```

El script compila, arranca los procesos necesarios, ejecuta `PruebasIntegracion` y limpia los procesos al terminar. También acepta puerto y nombre de servicio:

```bash
./run/pruebas.sh 1099 Subasta
```

Las suites verificadas y sus resultados están descritos en [rmi/docs/EVIDENCIAS.md](rmi/docs/EVIDENCIAS.md). Los logs se encuentran en `rmi/docs/logs/`.

## Documentación

- [README del módulo RMI](rmi/README.md): instalación, uso y problemas frecuentes.
- [Diagramas](rmi/docs/DIAGRAMAS.md): componentes, conexión, callbacks, concurrencia y fallos.
- [Evidencias](rmi/docs/EVIDENCIAS.md): pruebas automáticas, guion manual y capturas.
- `.partner/PROJECT.md`: objetivos y criterios de éxito del proyecto.
- `.partner/ARCHITECTURE.md`: arquitectura y decisiones técnicas implementadas.
- `.partner/DECISIONS.md`: registro de decisiones arquitectónicas.

## Limitaciones conocidas

- No hay persistencia: al reiniciar el servidor se pierden usuarios, productos y ofertas.
- No hay TLS ni autenticación.
- Las llamadas remotas del cliente se ejecutan en el EDT de Swing; una red lenta puede congelar temporalmente la interfaz.
- El cliente no se reconecta automáticamente.
- Los callbacks solo notifican producto y precio; un producto nuevo puede requerir volver a obtener el catálogo.
- No existe cierre automático, adjudicación ni historial completo de ofertas en la interfaz.

## Estado

El núcleo RMI, el modelo, los DTOs, el adaptador remoto, la caché y las pruebas de integración están implementados. La documentación técnica y las evidencias se mantienen en `rmi/docs/`; las capturas manuales de la interfaz se completan mediante el guion de pruebas correspondiente.
