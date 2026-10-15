# Arquitectura: Flujo de Datos y Persistencia (Data Flow)

Este documento detalla cómo viaja la información a través de las capas del sistema, el ciclo de vida de los estados en el cliente, la gestión de la concurrencia y las transacciones en el servidor.

---

## 1. Ciclo de Vida del Carrito en el Cliente

El carrito de venta **reside exclusivamente en la memoria RAM** de la terminal de caja ([`SalesUiState`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/features/sales/SalesUiState.kt)):

```mermaid
flowchart TD
    Scan["Lector USB o Búsqueda F2"] --> VM["SalesViewModel"]
    VM -->|"GET /api/v1/catalog/search?q=..."| API["ApiClient (Ktor Client)"]
    API -->|"HTTP JSON"| Server["Servidor Ktor (/api/v1/catalog)"]
    Server -->|"ProductDto + Presentaciones"| API
    API --> VM
    VM -->|"Agrega/Actualiza Línea"| State["_uiState (StateFlow en Memoria)"]
    State -->|"PricingRules.calculateLineItem()"| Totals["Subtotal, IVA y Total Provisional"]
    Totals -->|"Render Reactivo"| UI["SalesScreen (Compose)"]
```

### Principio de Aislamiento:
- Si el cliente agrega 10 productos al carrito, no se genera tráfico de escritura hacia la base de datos hasta el momento en que se presiona **Cobrar [F12]**.
- Si la conexión LAN parpadea mientras se escanean artículos, los productos capturados permanecen en memoria.

---

## 2. Máquina de Estados de Conexión LAN

Cada terminal de caja ejecuta un monitor en segundo plano ([`ServerConnection`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/core/ServerConnection.kt)) con sondeo de salud periódico (`GET /health`) y reconexión por WebSocket:

```mermaid
stateDiagram-v2
    [*] --> CONNECTED: Ping exitoso (HTTP 200)
    CONNECTED --> RECONNECTING: Falla de red / Timeout > 2s
    RECONNECTING --> CONNECTED: Ping exitoso restaurado
    RECONNECTING --> NO_SERVER: Fallos consecutivos > 3 intentos
    NO_SERVER --> CONNECTED: Reconexión exitosa
    
    note right of NO_SERVER
        - El botón de Cobro se bloquea (disabled)
        - Se muestra banner de alerta roja al cajero
        - El carrito permanece INTACTO en memoria
    end note
```

---

## 3. Flujo de Transacción Atómica de Cobro (`POST /api/v1/sales`)

Cuando el cajero confirma el pago, se desencadena una transacción ACID en el servidor autoritativo:

```mermaid
sequenceDiagram
    autonumber
    actor C as Cajero
    participant UI as SalesScreen
    participant VM as SalesViewModel
    participant API as ApiClient
    participant S as SalesService (Server)
    participant DB as PostgreSQL 16 (inTransaction)
    participant WS as WebSocket Hub

    C->>UI: Presiona Cobrar Efectivo [F12]
    UI->>VM: checkoutCash(receivedCents)
    VM->>API: completeSale(CreateSaleRequest, IdempotencyKey)
    API->>S: POST /api/v1/sales (Header: Idempotency-Key)
    
    rect rgb(240, 248, 255)
        Note over S,DB: Transacción Única (READ COMMITTED)
        S->>DB: 1. Validar sesión de caja abierta
        S->>DB: 2. Verificar idempotency_keys
        alt Si ya existe IdempotencyKey
            DB-->>S: Retornar respuesta previa almacenada (Commit rápido)
        else Nueva Venta
            S->>DB: 3. Validar precios vigentes y totales
            S->>DB: 4. Validar existencia si allow_negative_stock=false
            S->>DB: 5. Incrementar folio en counters (SELECT ... FOR UPDATE)
            S->>DB: 6. INSERT en sales
            S->>DB: 7. INSERT en sale_lines
            S->>DB: 8. INSERT stock_movements (descontar stock * factor)
            S->>DB: 9. INSERT cash_movements (efectivo ingresa a sesión)
            opt Si venta a Crédito / Fiado
                S->>DB: 10. Validar saldo + cargo <= límite
                S->>DB: 11. INSERT credit_movements (cargo a cuenta)
            end
            S->>DB: 12. INSERT idempotency_keys (guardar SaleResponse JSON)
            S->>DB: COMMIT
        end
    end
    
    S->>WS: Broadcast Evento ("SALE_COMPLETED", saleJson)
    S-->>API: 201 Created (SaleResponse)
    API-->>VM: Result.success(SaleResponse)
    VM->>UI: Vaciar carrito + Mostrar diálogo de cambio
    UI->>UI: Enviar a Impresora Térmica ESC/POS
    WS-->>UI: Actualización en vivo de existencias y reportes
```

---

## 4. Deducción de Inventario con Factor de Presentación

En tiendas de abarrotes, los productos se venden en diferentes presentaciones que comparten la misma existencia base. Por ejemplo:
- **Producto:** Huevo Blanco a Granel (Inventario medido en kilogramos: `unit = KG`).
- **Presentación 1:** Kilo completo (`factorMillis = 1000L`).
- **Presentación 2:** Cono de 30 huevos (~1.800 kg: `factorMillis = 1800L`).
- **Presentación 3:** Medio kilo (`factorMillis = 500L`).

La deducción en `stock_movements` se calcula canónicamente en enteros:
$$\text{BaseDeductionMillis} = \frac{\text{quantityMillis} \times \text{factorMillis}}{1000}$$

---

## 5. Eventos en Tiempo Real por WebSocket

El servidor expone el endpoint `/ws/events`. Las cajas conectadas reciben notificaciones inmediatas:
- `CATALOG_UPDATED`: Cuando el administrador modifica un precio o alta de producto.
- `SALE_COMPLETED`: Folio, cajero y monto de venta confirmada.
- `CASH_MOVEMENT`: Movimientos manuales o abonos recibidos en turno.
- `SESSION_CLOSED`: Notificación de corte de turno efectuado.
