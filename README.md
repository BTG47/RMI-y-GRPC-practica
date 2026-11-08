# Subasta distribuida con Java RMI y gRPC

Proyecto académico para transformar una aplicación local de subastas Swing/MVC en
dos sistemas distribuidos cliente-servidor independientes: uno con **Java RMI** y
otro con **gRPC + Protocol Buffers**.

Los módulos `rmi/` y `grpc/` centralizan el estado y la lógica de negocio en un
servidor y permiten conectar varios clientes Swing. RMI notifica los cambios de
precio mediante callbacks; gRPC usa un flujo servidor-cliente de eventos.

## Alcance

- `rmi/`: registro RMI, servidor centralizado, cliente Swing, DTOs serializables,
  callbacks de precio, concurrencia y pruebas en Java puro.
- `grpc/`: servidor gRPC, cliente Swing, contrato Protocol Buffers, stream de
  eventos y pruebas Maven/JUnit.
- Ambas implementaciones permiten registrar sesiones, publicar productos,
  consultar el catálogo y ofertar con resultado explícito.
- Ambas protegen las ofertas concurrentes y rechazan montos que no superen el
  precio vigente.
- `rmi/` incluye además un **perfil TLS opcional** con TLS/mTLS en registro,
  servicio y callbacks (ver [rmi/README.md](rmi/README.md) y
  [rmi/docs/SEGURIDAD.md](rmi/docs/SEGURIDAD.md)).
- Fuera del alcance: persistencia, autenticación de usuarios de la subasta,
  cierre por fecha, adjudicación e historial completo. El perfil TLS de gRPC no
  está implementado; su canal local usa `usePlaintext()`.

## Requisitos

- JDK 17 o posterior con `java` y `javac` disponibles en el `PATH`.
- Linux u otro sistema con Java y Swing.
- Para `rmi/`: no se requieren Maven, Gradle, JUnit ni librerías externas.
- Para `grpc/`: Maven 3.8 o posterior y acceso inicial a Maven Central para
  descargar dependencias y los ejecutables de `protoc`. Ver
  [grpc/README-gRPC.md](grpc/README-gRPC.md).

## Estructura

```text
.
├── original/       Código original de referencia
├── rmi/            Implementación Java RMI
│   ├── src/        Servidor, cliente Swing, modelo, callbacks y DTOs
│   ├── test/       Pruebas en Java puro y de integración
│   ├── run/        Scripts de arranque, pruebas y TLS
│   ├── tls/        Material TLS de demo (no se versiona)
│   └── docs/       Diagramas, evidencias, seguridad y logs
├── grpc/           Implementación gRPC
│   ├── proto/      Contrato Protocol Buffers
│   ├── src/        Servidor, cliente Swing y controlador
│   ├── test/       Pruebas de integración JUnit
│   ├── docs/       Diagramas y capturas
│   └── pom.xml     Compilación y generación de stubs
├── .gitignore
└── README.md
```

`original/` se conserva como referencia y no forma parte del arranque de ninguno
de los dos módulos. RMI y gRPC se compilan y ejecutan de forma independiente.

## Arquitectura

### RMI

- **Cliente:** `Principal`, `SubastaVista`, `SubastaControlador`,
  `ClienteCallbackImpl` y `CachePrecios`.
- **Servidor:** `ServidorMain`, `SubastaServidorImpl`, `SubastaModelo` y
  `Producto`.
- **Comunicación:** `SubastaInterface`, `ClienteCallbackInterface`,
  `InformacionProducto` e `InformacionOferta`.

`SubastaModelo` no conoce RMI. `SubastaServidorImpl` es el adaptador remoto y
protege el modelo con un lock global. Las ofertas se confirman dentro del lock y
las notificaciones se envían después mediante un pool fijo de cuatro hilos. Si
un callback falla, se elimina esa sesión sin revertir la oferta aceptada.

Detalle completo en [rmi/README.md](rmi/README.md) y
[rmi/docs/DIAGRAMAS.md](rmi/docs/DIAGRAMAS.md).

### gRPC

`SubastaServiceImpl` centraliza usuarios activos, catálogo y suscriptores. El
controlador Swing usa RPC unary para registrar, publicar, consultar y ofertar, y
un RPC server-streaming (`SuscribirCambios`) para recibir snapshots completos de
productos. Las llamadas unary se ejecutan fuera del EDT y los eventos se aplican
a la vista en el hilo de Swing. La comprobación y actualización de precio ocurren
atómicamente bajo lock; un emisor de un solo hilo serializa los eventos.

Detalle completo en [grpc/README-gRPC.md](grpc/README-gRPC.md),
[grpc/proto/subasta.proto](grpc/proto/subasta.proto) y
[grpc/docs/DIAGRAMAS.md](grpc/docs/DIAGRAMAS.md).

## Compilación

### RMI (perfil plano)

Desde la raíz del proyecto:

```bash
cd rmi
javac -d bin src/*.java
javac -cp bin -d bin-test test/*.java
```

Los stubs RMI se generan dinámicamente con JDK 17; no se utiliza `rmic`.

### gRPC

Desde `grpc/`:

```bash
mvn clean test
```

Maven genera las clases `grpc.generated.*` desde `proto/subasta.proto`, compila
servidor y cliente y ejecuta las pruebas. Para empaquetar sin ejecutar pruebas:

```bash
mvn package
```

## Ejecución manual

### RMI — perfil plano

Se necesitan un registro RMI, el servidor y uno o más clientes.

```bash
# 1. Registro (debe arrancar desde rmi/bin para ver las interfaces)
cd rmi/bin
rmiregistry 1099

# 2. Servidor, en otra terminal
cd rmi
java -cp bin ServidorMain

# 3. Cliente, en una o más terminales
java -cp bin Principal
```

`ServidorMain` y `Principal` aceptan puerto, nombre y URL opcionales:

```bash
java -cp bin ServidorMain 1099 Subasta
java -cp bin Principal rmi://localhost:1099/Subasta
```

El cliente permite conectar y desconectar una sesión sin cerrar la ventana.

### RMI — perfil TLS opcional

El perfil TLS es adicional y no reemplaza al plano. Genera el material de demo y
ejecuta las pruebas:

```bash
cd rmi
./run/genera-keystores.sh
./run/pruebas-tls.sh
```

El arranque manual con `--tls`, los keystores y los límites de mTLS están en
[rmi/README.md](rmi/README.md#perfil-tls-opcional) y
[rmi/docs/SEGURIDAD.md](rmi/docs/SEGURIDAD.md).

### gRPC

Desde `grpc/`, en terminales separadas:

```bash
mvn exec:java -Dexec.mainClass=ServidorMain
mvn exec:java -Dexec.mainClass=Principal
```

El servidor escucha en `localhost:50051` por defecto. Para otro puerto:

```bash
mvn exec:java -Dexec.mainClass=ServidorMain -Dexec.args=50052
mvn exec:java -Dexec.mainClass=Principal -Dexec.args="localhost 50052"
```

La sintaxis para PowerShell está documentada en
[grpc/README-gRPC.md](grpc/README-gRPC.md). También hay una demostración de
consola con `PrincipalServidor`.

## Uso básico

1. Conectar con un nombre de usuario.
2. Obtener la lista de productos.
3. Publicar un producto con nombre y precio inicial.
4. Seleccionar un producto y realizar una oferta.
5. Observar la actualización automática del precio en los clientes conectados.
6. Desconectar o salir.

Reglas principales, comunes a RMI y gRPC:

- Una oferta debe ser estrictamente mayor que el precio actual.
- El vendedor puede ofertar por su propio producto.
- No es obligatorio que el comprador esté registrado para ofertar.
- Solo puede existir una sesión activa por nombre de usuario.
- El estado autoritativo vive en el servidor.

## Pruebas

### RMI

Suites unitarias y de servidor:

```bash
cd rmi
for suite in PruebasDTOs PruebasModelo PruebasServidor PruebasCachePrecios; do
  java -cp bin:bin-test "$suite"
done
```

Integración con registro y servidor reales:

```bash
cd rmi
./run/pruebas.sh
```

Pruebas TLS positivas y negativas:

```bash
cd rmi
./run/pruebas-tls.sh
./run/pruebas-tls-negativas.sh
```

### gRPC

```bash
cd grpc
mvn clean test
```

El ciclo levanta un servidor local en un puerto efímero y dos suscriptores;
valida registro duplicado, campos inválidos, actualizaciones por stream, ofertas
concurrentes y rechazo de importes no superiores al precio vigente.

### Resultados registrados

| Perfil | Evidencia | Resultado |
|---|---|---|
| RMI DTOs | `rmi/docs/logs/PruebasDTOs.log` | 19 comprobaciones |
| RMI modelo | `rmi/docs/logs/PruebasModelo.log` | 18 comprobaciones |
| RMI servidor | `rmi/docs/logs/PruebasServidor.log` | 68 comprobaciones |
| RMI caché | `rmi/docs/logs/PruebasCachePrecios.log` | 8 comprobaciones |
| RMI integración | `rmi/docs/logs/integracion.log` | 80 comprobaciones |
| RMI TLS unitario | `rmi/docs/logs/tls/pruebas-tls-unit.log` | 10 comprobaciones |
| RMI TLS integración | `rmi/docs/logs/tls/integracion-tls.log` | 80 comprobaciones |
| RMI TLS negativas | `rmi/docs/logs/tls/negativa-*.log` | 3/3 rechazos |
| gRPC integración | `grpc/target/surefire-reports/` | 3 pruebas JUnit, 0 fallos |

El detalle de las pruebas RMI y su guion manual está en
[rmi/docs/EVIDENCIAS.md](rmi/docs/EVIDENCIAS.md). Las capturas del cliente gRPC
están en `grpc/docs/Capturas/`.

## Documentación

- [README del módulo RMI](rmi/README.md): instalación, uso, TLS y problemas
  frecuentes.
- [Diagramas RMI](rmi/docs/DIAGRAMAS.md): componentes, conexión, callbacks,
  concurrencia, fallos y TLS.
- [Evidencias RMI](rmi/docs/EVIDENCIAS.md): pruebas automáticas, guion manual y
  capturas.
- [Seguridad TLS](rmi/docs/SEGURIDAD.md): qué protege, qué no y cómo se comprueba.
- [README del módulo gRPC](grpc/README-gRPC.md): contrato, arranque y reglas.
- [Diagramas gRPC](grpc/docs/DIAGRAMAS.md): componentes, suscripción, concurrencia
  y desconexión.
- `.partner/PROJECT.md`, `.partner/ARCHITECTURE.md` y `.partner/DECISIONS.md`:
  objetivos, arquitectura y decisiones del proyecto.

## Limitaciones conocidas

- No hay persistencia: al reiniciar el servidor se pierden usuarios, productos y
  ofertas.
- El perfil **plano** de RMI no tiene TLS ni autenticación. El perfil TLS
  opcional usa mTLS, que autentica procesos, no usuarios de la subasta.
- El canal gRPC es `usePlaintext()`: no tiene TLS ni autenticación en esta
  entrega.
- El cliente gRPC ejecuta las llamadas unary fuera del EDT y recibe los eventos
  por stream; el cliente RMI ejecuta las llamadas remotas en el EDT y no se
  reconecta automáticamente.
- El callback RMI solo transporta producto y precio; un producto nuevo puede
  requerir volver a obtener el catálogo.
- No existe cierre automático, adjudicación ni historial completo de ofertas en
  la interfaz.
- En gRPC, un usuario registrado que nunca llega a abrir el stream queda ocupado
  hasta que se desconecta o se reinicia el servidor.

## Estado

El núcleo RMI, el modelo, los DTOs, el adaptador remoto, la caché, la integración,
el perfil TLS opcional y el módulo gRPC están implementados. Las pruebas
automáticas de RMI y TLS están en verde; el reporte de Surefire incluido para
gRPC registra 3 pruebas sin fallos. La demostración manual de la interfaz gRPC se
completa siguiendo [grpc/README-gRPC.md](grpc/README-gRPC.md).
