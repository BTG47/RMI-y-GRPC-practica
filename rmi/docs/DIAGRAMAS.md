# Diagramas del módulo RMI

Los diagramas describen el código implementado en `rmi/src/`. No incluyen TLS ni
componentes que no existan en esta entrega.

## Componentes

```mermaid
flowchart LR
    subgraph Cliente
        V[SubastaVista<br/>Swing]
        C[SubastaControlador]
        CB[ClienteCallbackImpl]
        CP[CachePrecios]
        P[Principal]
    end

    subgraph Servidor
        S[SubastaServidorImpl]
        M[SubastaModelo]
        PR[Producto]
        EX[Pool de 4 hilos]
    end

    R[(rmiregistry<br/>puerto 1099)]

    P --> V
    P --> C
    V -->|ActionEvent| C
    C -->|lookup / conecta / agregaOferta<br/>obtieneCatalogo / desconecta| S
    S --> M
    M --> PR
    S -->|copia callbacks y encola tareas| EX
    EX -->|actualizaPrecio producto, precio| CB
    CB -->|recibePrecio| C
    C --> CP
    C -->|setEstado / lista / precio| V
    S -.->|Naming.rebind| R
    C -.->|Naming.lookup| R
```

`SubastaModelo` no conoce RMI ni `RemoteException`. `Producto` es mutable y nunca cruza la
red; `InformacionProducto` e `InformacionOferta` son los DTO inmutables que sí se serializan.

## Conexión de un cliente

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant V as SubastaVista
    participant C as SubastaControlador
    participant R as rmiregistry
    participant S as SubastaServidorImpl

    U->>V: escribe nombre y pulsa Conectar
    V->>C: ActionEvent "Conectar"
    C->>R: Naming.lookup(url)
    R-->>C: stub SubastaInterface
    C->>C: crea ClienteCallbackImpl(this)
    C->>S: conecta(nombre, callback)
    S->>S: lock: ¿callbacks contiene el nombre?
    alt nombre libre
        S->>S: modelo.registraUsuario(nombre), guarda callback
        S-->>C: true
        C->>S: obtieneCatalogo()
        S-->>C: Vector<InformacionProducto>
        C->>C: precios.limpia(), aplica cada precio
        C->>V: setEstado + lista + habilitaSesion(true)
    else nombre duplicado
        S-->>C: false
        C->>V: "Ese usuario ya tiene una sesión activa"
    else error de red
        S--xC: RemoteException
        C->>V: "No se pudo conectar: ..."
    end
```

El callback se crea una sola vez y se reutiliza si el usuario se desconecta y vuelve a
conectar en la misma ventana.

## Oferta con notificación diferida

```mermaid
sequenceDiagram
    autonumber
    actor A as Cliente A (EDT)
    participant S as SubastaServidorImpl
    participant M as SubastaModelo
    participant E as Pool de 4 hilos
    participant B as Cliente B (callback)
    participant C as Cliente C (callback)

    A->>S: agregaOferta(usuario, producto, monto)
    S->>S: lock global
    S->>M: agregaOferta(...)
    M->>M: ¿producto existe? ¿monto > precioActual?
    alt ACEPTADA
        M->>M: actualiza precio y registra oferta
        M-->>S: ACEPTADA
        S->>S: copia la lista de callbacks
        S-->>A: ACEPTADA (sin esperar a los clientes)
        S->>E: una tarea por callback
        E->>B: actualizaPrecio(producto, monto)
        E->>C: actualizaPrecio(producto, monto)
        B->>B: caché merge(max) + SwingUtilities.invokeLater
        C->>C: caché merge(max) + SwingUtilities.invokeLater
    else MONTO_INSUFICIENTE
        M-->>S: MONTO_INSUFICIENTE
        S-->>A: MONTO_INSUFICIENTE (no notifica)
    else PRODUCTO_NO_EXISTE
        M-->>S: PRODUCTO_NO_EXISTE
        S-->>A: PRODUCTO_NO_EXISTE (no notifica)
    end
```

El lock se libera antes de encolar las notificaciones. Un callback lento no bloquea la
oferta ni a los demás clientes; un callback que falla se elimina de `callbacks` sin revertir
el cambio de precio.

## Concurrencia de dos ofertas

```mermaid
sequenceDiagram
    autonumber
    participant A as Hilo oferta 100
    participant B as Hilo oferta 200
    participant S as SubastaServidorImpl

    A->>S: agregaOferta(..., 100)
    S->>S: adquiere lock
    S->>S: modelo: precio 50 → 100, ACEPTADA
    S->>S: libera lock
    S-->>A: ACEPTADA
    B->>S: agregaOferta(..., 200)
    S->>S: adquiere lock
    S->>S: modelo: precio 100 → 200, ACEPTADA
    S->>S: libera lock
    S-->>B: ACEPTADA
```

Con el lock global, la comprobación y la actualización del precio son atómicas. Si el orden
fuera el inverso, la oferta de 100 se rechazaría y el precio final seguiría siendo 200.

## Desconexión y fallo de callback

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuario
    participant C as SubastaControlador
    participant S as SubastaServidorImpl
    participant E as Pool de notificaciones

    U->>C: Desconectar
    C->>S: desconecta(nombre)
    S->>S: lock: elimina callback
    S-->>C: void
    C->>C: limpia usuario, stub y caché
    C->>C: habilitaSesion(false)
    Note over C: la app sigue abierta

    Note over S,E: Si el cliente muere sin desconectar
    E->>S: tarea de notificación
    S->>S: callback.actualizaPrecio lanza RemoteException
    S->>S: elimina solo ese callback
    S-->>E: la tarea termina
    Note over S: los demás callbacks siguen recibiendo
```
