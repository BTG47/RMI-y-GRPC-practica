# Subasta distribuida con gRPC y Protocol Buffers

Este módulo independiente replica la semántica de `rmi/`. El servidor conserva usuarios activos, productos y precios; los clientes Swing solo mantienen una caché para presentar los snapshots recibidos por gRPC.

## Requisitos

- JDK 17 o posterior (`java` y `mvn` disponibles en `PATH`).
- Maven 3.8 o posterior.
- Acceso a Maven Central la primera vez para descargar dependencias y los ejecutables nativos de `protoc` y `protoc-gen-grpc-java`.
- Para abrir el cliente Swing, un entorno con escritorio gráfico.

No hace falta instalar `protoc` manualmente. El plugin detecta el sistema operativo, descarga la versión fijada de Protocol Buffers y genera los stubs Java durante la compilación.

## Estructura

```text
grpc/
  proto/subasta.proto       contrato protobuf
  src/                      servidor, modelo, cliente Swing y controlador
  test/                     pruebas de integración gRPC
  docs/DIAGRAMAS.md         diagramas de componentes y secuencias
  pom.xml                   dependencias y generación reproducible
  README-gRPC.md            instrucciones del módulo
```

Los archivos de `rmi/` y `original/` se conservan sin cambios.

## Compilación, generación y pruebas

Desde PowerShell, CMD, Linux o macOS:

```text
cd grpc
mvn clean test
```

El ciclo Maven genera `grpc.generated.*` desde `proto/subasta.proto`, compila servidor y cliente y ejecuta las pruebas de integración. Los archivos generados viven en `target/generated-sources/`; no se editan ni se entregan como fuentes manuales. Para compilar el artefacto sin ejecutar las pruebas:

```text
mvn package
```

Dependencias/versiones centrales: gRPC Java `1.68.1`, Protocol Buffers `3.25.5`, JUnit `5.11.3`. El código fuente y bytecode objetivo son Java 17.

## Arranque

Todos los comandos se ejecutan desde `grpc/`. El servidor escucha en el puerto `50051`por defecto; para otro puerto, agrega el número al comando.
 
### Servidor

```powershell
mvn.cmd exec:java '-Dexec.mainClass=ServidorMain'
```

Puerto alternativo en PowerShell:

```powershell
mvn.cmd exec:java '-Dexec.mainClass=ServidorMain' '-Dexec.args=50052'
```

En Linux o macOS:

```bash
mvn exec:java -Dexec.mainClass=ServidorMain -Dexec.args=50052
```

Detén el proceso con `Ctrl+C`.

### Clientes Swing

En cada terminal de cliente, ejecuta:

```powershell
mvn.cmd exec:java '-Dexec.mainClass=Principal'
```

Con host y puerto explícitos:

```powershell
mvn.cmd exec:java '-Dexec.mainClass=Principal' '-Dexec.args=localhost 50051'
```

En Linux o macOS:

```bash
mvn exec:java -Dexec.mainClass=Principal -Dexec.args="localhost 50051"
```

Abre el comando en dos terminales para probar dos clientes independientes. Conecta cada
ventana con un nombre distinto. Publicar un producto en una ventana y aceptar una oferten la otra actualiza automáticamente el catálogo/precio de ambas, sin pulsar «Obtener lista». Esta consulta manual sigue disponible para recargar el catálogo. También se puede ejecutar una demostración de consola como cliente independiente:

 ```powershell
mvn.cmd exec:java '-Dexec.mainClass=PrincipalServidor' '-Dexec.args=localhost 50051'
```

La demostración registra un usuario, abre el stream, publica un producto, oferta y espera el evento del nuevo precio.

## Contrato y reglas

| RPC | Semántica / resultado |
|---|---|
| `RegistrarUsuario` | Un nombre no vacío ocupa una única sesión activa; duplicado devuelve `registrado=false`. |
| `PublicarProducto` | Publica el primer producto de ese nombre; duplicado devuelve `publicado=false`. |
| `ConsultarCatalogo` | Devuelve snapshots inmutables del catálogo autoritativo. |
| `Ofertar` | Acepta solo `monto > precio_actual`; devuelve aceptada, monto insuficiente o producto inexistente. |
| `SuscribirCambios` | Stream servidor-cliente con snapshots iniciales y cada publicación/oferta aceptada. |
| `Desconectar` | Termina la suscripción y libera el nombre de usuario. Cancelar el stream inesperadamente también libera la sesión. |

`Producto.nombre` es la clave única; `vendedor` y `comprador` son nombres visibles. Los precios son `float` finitos mayores que cero. `ProductoActualizado` transporta el snapshot completo del producto y una versión creciente. gRPC retorna `INVALID_ARGUMENT` para campos vacíos o precios inválidos, `FAILED_PRECONDITION` si se intenta suscribir sin registrar usuario y `ALREADY_EXISTS` si ese usuario ya tiene un stream. Producto inexistente y monto insuficiente son resultados de negocio explícitos para mantener la semántica comparable con RMI.

Identificadores de campos protobuf:

| Mensaje | Número | Campo |
|---|---:|---|
| `RegistroUsuarioRequest` | 1 | `usuario` |
| `PublicarProductoRequest` | 1–3 | `vendedor`, `producto`, `precio_inicial` |
| `OfertaRequest` | 1–3 | `comprador`, `producto`, `monto` |
| `Producto` | 1–4 | `vendedor`, `nombre`, `precio_inicial`, `precio_actual` |
| `CatalogoResponse` | 1 | `productos` (lista de snapshots) |
| `ProductoActualizado` | 1–2 | `producto`, `version` |
| `SuscripcionRequest` / `DesconexionRequest` | 1 | `usuario` |

El resto de solicitudes/respuestas usa `mensaje` para texto explicativo, `registrado` o `publicado` para el resultado de alta y `resultado`/`precio_actual` para la respuesta de oferta. Un `UNAVAILABLE` de gRPC indica que el servidor o canal no está disponible.

El servidor acepta ofertas de compradores no registrados, permite al vendedor ofertar por su propio artículo y no tiene cierre por fecha ni adjudicación, igual que el modelo RMI. El registro de usuarios solo representa sesiones activas. La comprobación y actualización de precio ocurren atómicamente bajo un lock; un único emisor serializa eventos en el orden de aceptación/publicación.

## Pruebas y demostración manual

`mvn clean test` levanta un servidor gRPC local y dos suscriptores en las pruebas; valida campos inválidos, registro duplicado, actualizaciones de stream, ofertas concurrentes y rechazo de importes inferiores al precio vigente. Las pruebas no necesitan procesos manuales ni una GUI.

Para documentar la ejecución manual con tres procesos:

1. Compila con `mvn clean test`.
2. Inicia `ServidorMain` en la primera terminal.
3. Inicia dos `Principal` en terminales separadas y conecta usuarios distintos.
4. Publica desde una ventana y confirma que aparece en la otra; oferta desde el segundo cliente y confirma que el precio cambia en ambos sin refrescar.
5. Prueba una oferta igual o inferior al precio visible; debe rechazarse y no generar evento.

El diagrama de componentes y secuencia refleja el código implementado en [`docs/DIAGRAMAS.md`](docs/DIAGRAMAS.md).

## Notas de red

El canal usa `usePlaintext()` para el entorno local de la práctica; no habilita TLS ni autenticación. El estado no persiste al reiniciar el servidor. Se pueden usar varios clientes en procesos separados en la misma máquina; para probar entre máquinas, usa una dirección alcanzable en lugar de `localhost` y habilita el puerto elegido en el firewall.
