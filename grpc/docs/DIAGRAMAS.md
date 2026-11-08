# Diagramas del módulo gRPC

Los diagramas describen el código final bajo `grpc/src/` y el contrato de
`grpc/proto/subasta.proto`.

## Componentes

```mermaid
flowchart LR
    subgraph Cliente Swing
        P[Principal]
        V[SubastaVista]
        C[SubastaControlador]
        CH[ManagedChannel]
        CACHE[Caché de precios]
        P --> V
        P --> C
        V -->|acciones/lista| C
        C -->|RPC unary async| CH
        C -->|StreamObserver| CH
        C --> CACHE
        C -->|actualización en EDT| V
    end

    subgraph Servidor gRPC
        S[ServerBuilder / SubastaServiceImpl]
        M[Mapa de sesiones y catálogo]
        L[Lock de estado]
        E[Emisor serial de eventos]
        S --> L
        L --> M
        S -->|snapshot ordenado| E
    end

    CH <-->|HTTP/2 + Protobuf| S
    E -->|ProductoActualizado stream| CH
```

No se envían DTO RMI por la red. `ProductoSubasta` es estado interno y se mapea a
mensajes generados por protobuf.

## Suscripción, publicación y actualización

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant V as SubastaVista / EDT
    participant C as SubastaControlador
    participant W as Executor de solicitudes
    participant S as SubastaServiceImpl
    participant E as Emisor serial

    U->>V: Conectar (nombre)
    V->>C: ActionEvent
    C->>W: agenda RegistrarUsuario
    W->>S: RegistrarUsuario
    S-->>W: registrado
    W->>S: SuscribirCambios (RPC server-streaming)
    S->>S: bajo lock registra stream y copia catálogo
    S->>E: encola snapshots iniciales
    E-->>C: ProductoActualizado*
    C->>V: SwingUtilities.invokeLater (EDT)
    W->>S: ConsultarCatalogo
    S-->>W: CatalogoResponse
    W->>V: renderiza catálogo en EDT
    U->>V: publicar producto
    V->>C: ActionEvent
    C->>W: agenda PublicarProducto
    W->>S: PublicarProducto
    S->>S: lock: valida y agrega producto
    S->>E: encola snapshot actualizado
    S-->>W: publicado
    E-->>C: ProductoActualizado*
    C->>V: incorpora producto/precio en EDT
```

El stream permanece abierto sin ocupar el hilo de Swing ni bloquear los botones. El
catálogo se envía al suscriptor en la misma sección crítica en que se registra el stream;
por tanto, cambios concurrentes quedan después de la instantánea inicial en la cola del
emisor.

## Ofertas concurrentes y broadcast

```mermaid
sequenceDiagram
    autonumber
    participant A as Cliente A
    participant B as Cliente B
    participant S as SubastaServiceImpl
    participant L as Lock de estado
    participant E as Emisor serial
    participant C1 as Stream cliente 1
    participant C2 as Stream cliente 2

    par Ofertas próximas
        A->>S: Ofertar(producto, 100)
    and
        B->>S: Ofertar(producto, 200)
    end
    S->>L: adquirir
    L->>L: compara con precio vigente y confirma atómicamente
    S->>E: encola solo si se aceptó
    S-->>A: resultado de negocio
    S->>L: siguiente oferta adquiere lock
    L->>L: vuelve a comprobar monto > precio vigente
    S->>E: encola snapshot confirmado
    S-->>B: resultado de negocio
    E-->>C1: ProductoActualizado en orden de confirmación
    E-->>C2: ProductoActualizado en orden de confirmación
```

El orden de llegada de las dos solicitudes no se presupone. Una oferta inferior a la
vigente se rechaza; la serialización de actualizaciones y eventos evita notificaciones
fuera de orden para una secuencia aceptada.

## Desconexión

```mermaid
sequenceDiagram
    actor U as Usuario
    participant C as SubastaControlador
    participant S as SubastaServiceImpl

    U->>C: Desconectar
    C->>S: DesconexionRequest
    S->>S: elimina sesión y suscriptor
    S-->>C: DesconexionResponse
    C->>C: cancela stream y limpia caché
    Note over S: Cancelación inesperada ejecuta onCancelHandler
    S->>S: elimina stream y libera nombre de usuario
```
