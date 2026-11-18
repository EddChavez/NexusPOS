# Funcionalidad: Tickets en Espera Persistentes (Parked Sales)

Este documento detalla la especificación técnica y funcional del mecanismo de retención y recuperación de ventas en espera en **NexusPOS**, diseñado para mantener la agilidad en mostrador y evitar cuellos de botella en la fila de cobro.

---

## 1. Objetivo

Permitir a los cajeros suspender temporalmente un ticket de venta en curso cuando un cliente olvida un artículo, necesita verificar precios o busca cambio, liberando la terminal de forma instantánea para atender al siguiente cliente en la fila, con persistencia centralizada en base de datos y sincronización en tiempo real entre todas las cajas de la tienda.

---

## 2. Alcance

### Incluye:
- **Pausa Inmediata de Venta:** Atajo de teclado `[F6]` en la barra de captura o botón visible en el panel de control.
- **Etiquetado Rápido:** Diálogo modal [`ParkSalePromptDialog.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/features/sales/ParkSalePromptDialog.kt) con campo de texto libre enfocado por defecto (ej. *"Señor del coche rojo"*, *"Doña Carmen"*, *"Mostrador 2"*).
- **Persistencia Autoritativa en Servidor:** Almacenamiento íntegro de partidas, presentaciones, importes e indicadores de granel en la tabla `parked_sales` de PostgreSQL / H2.
- **Sincronización LAN en Tiempo Real:** Notificación por WebSockets (`parked_sale.created`, `parked_sale.retrieved`, `parked_sale.discarded`) a todas las terminales de caja conectadas.
- **Indicador y Badge Dinámico:** Contador visible `Pend (N)` en el panel inferior [`BottomCheckoutPanel.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/features/sales/BottomCheckoutPanel.kt) y en la barra de captura.
- **Diálogo de Gestión y Recuperación:** [`ParkedSalesListDialog.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/features/sales/ParkedSalesListDialog.kt) con lista dividida, tiempo transcurrido (ej. *"Hace 3 min"*), desglose de partidas y botones de acción.
- **Reanudación No Destructiva:** Si el cajero ya tiene productos en el ticket actual al recuperar una venta en espera, el sistema genera automáticamente una nueva pestaña de ticket sin sobreescribir la venta en curso.
- **Descarte de Venta:** Opción para dar de baja tickets retenidos que ya no serán cobrados.

### Queda fuera:
- Expiración o purga automática por tiempo límite (se gestiona manualmente para evitar pérdidas de captura).
- Facturación anticipada de tickets en espera.

---

## 3. Actores y Precondiciones

- **Actores:** Cajero, Encargado, Administrador.
- **Precondiciones:**
  1. La terminal debe estar conectada al servidor autoritativo (`ServerConnectionState.CONNECTED`).
  2. Debe existir una sesión de caja activa abierta en la terminal.
  3. El carrito actual debe tener al menos un artículo registrado para poder ponerse en espera.

---

## 4. Flujo Funcional

```mermaid
sequenceDiagram
    actor C as Cajero
    participant POS1 as Caja 1 (Terminal)
    participant S as Servidor Central (PostgreSQL)
    participant WS as Canal WebSocket LAN
    participant POS2 as Caja 2 (Terminal Secundaria)

    C->>POS1: Presiona [F6] "Poner en Espera"
    POS1->>POS1: Abre modal ParkSalePromptDialog
    C->>POS1: Escribe "Cliente de playera azul" + [Enter]
    POS1->>S: POST /api/v1/parked-sales (itemsJson, label, total)
    S->>S: Inserta en tabla parked_sales (status='PARKED')
    S->>WS: Broadcast event: parked_sale.created
    WS-->>POS1: Actualiza contador Pend (1)
    WS-->>POS2: Actualiza contador Pend (1) en tiempo real
    POS1->>POS1: Limpia carrito local; lista para atender siguiente cliente

    Note over POS2: Minutos después en Caja 2...
    actor C2 as Cajero 2
    C2->>POS2: Clic en badge "Pend (1)" o atajo de gestión
    POS2->>S: GET /api/v1/parked-sales
    S-->>POS2: 200 OK (Lista de tickets en espera)
    C2->>POS2: Selecciona ticket y presiona [Enter] Reanudar
    POS2->>S: POST /api/v1/parked-sales/{id}/retrieve
    S->>S: UPDATE parked_sales SET status='RETRIEVED'
    S->>WS: Broadcast event: parked_sale.retrieved
    WS-->>POS1: Contador decrementa a 0
    WS-->>POS2: Contador decrementa a 0
    POS2->>POS2: Restaura productos en pantalla y procede al cobro [F12]
```

---

## 5. Reglas de Negocio

- **RN-PRK-01 (Sin Afectación Prematura de Stock ni Caja):** Poner un ticket en espera **no descuenta inventario** en el Kardex ni altera los saldos de la sesión de caja. Los movimientos contables ocurren exclusivamente cuando el ticket es cobrado definitivamente vía `POST /api/v1/sales`.
- **RN-PRK-02 (Carrito No Vacío Obligatorio):** No se permite aparcar una venta vacía con 0 artículos. Si el operador lo intenta, el sistema muestra advertencia: *"El carrito está vacío. Agregue productos antes de poner en espera."*
- **RN-PRK-03 (Prevención de Doble Cobro en Red):** La operación de recuperación (`POST /api/v1/parked-sales/{id}/retrieve`) es atómica en base de datos. Si dos terminales en la red local intentan reanudar el mismo ticket simultáneamente, la primera tiene éxito y la segunda recibe error de conflicto (`409 ALREADY_RETRIEVED`), garantizando que un ticket nunca sea cobrado dos veces.
- **RN-PRK-04 (Protección de Partidas Activas en Mostrador):** Al reanudar un ticket retenido:
  - Si el ticket actual de la terminal está vacío: se inyectan las partidas directamente en él.
  - Si el ticket actual ya contiene artículos escaneados: el sistema abre automáticamente una nueva pestaña de ticket en paralelo ([`cycleNextTicket()`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/features/sales/SalesViewModel.kt)) y carga el ticket retenido allí, preservando los artículos ya escaneados por el cajero.
- **RN-PRK-05 (Descarte Auditable):** Al descartar un ticket retenido, el estado cambia a `DISCARDED` con registro del usuario responsable en la base de datos para trazabilidad y prevención de mermas.

---

## 6. Diseño Técnico

- **Migración de Base de Datos:** [`V3__parked_sales.sql`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/server/src/main/resources/db/migration/V3__parked_sales.sql).
- **Contratos DTO:** [`ParkedSaleDtos.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/shared/contracts/src/commonMain/kotlin/mx/nexuspos/contracts/sales/ParkedSaleDtos.kt) (`ParkSaleRequest`, `ParkedSaleResponse`, `ParkedSaleItemDto`, `RetrieveParkedSaleRequest`).
- **Servicio Backend:** [`ParkedSalesService.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/server/src/main/kotlin/mx/nexuspos/server/modules/sales/ParkedSalesService.kt).
- **Rutas HTTP:** [`Application.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/server/src/main/kotlin/mx/nexuspos/server/Application.kt) (`GET/POST /api/v1/parked-sales`, `POST /api/v1/parked-sales/{id}/retrieve`, `POST /api/v1/parked-sales/{id}/discard`).
- **Gestión de Estado UI:** [`SalesViewModel.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/features/sales/SalesViewModel.kt) (`parkedSalesCount`, `loadParkedSales()`, `confirmParkCurrentSale()`, `retrieveParkedSale()`, `discardParkedSale()`).
- **Componentes Compose Desktop:**
  - Diálogo de entrada de etiqueta: [`ParkSalePromptDialog.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/features/sales/ParkSalePromptDialog.kt).
  - Diálogo maestro de tickets en espera: [`ParkedSalesListDialog.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/features/sales/ParkedSalesListDialog.kt).
  - Indicadores con badge: [`BottomCheckoutPanel.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/features/sales/BottomCheckoutPanel.kt) y [`CaptureBar.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/app/src/commonMain/kotlin/mx/nexuspos/client/features/sales/CaptureBar.kt).

---

## 7. Errores y Casos Límite

| Escenario | Comportamiento del Sistema |
| :--- | :--- |
| **Intento de aparcar carrito sin productos** | Bloqueo client-side inmediato con mensaje de error en barra de notificación. |
| **Pérdida de conexión con el servidor central** | El botón de aparcado se bloquea temporalmente para evitar carritos perdidos en memoria volátil; la venta permanece segura en la terminal. |
| **Colisión concurrente entre dos cajas** | Transacción segura en PostgreSQL; la segunda terminal recibe `409` y actualiza su lista de tickets pendientes automáticamente vía WebSocket. |
| **Reanudación con carrito activo lleno** | Creación automática de pestaña adicional; ninguna partida es eliminada ni mezclada accidentalmente. |

---

## 8. Pruebas Automatizadas

- **Pruebas de Backend e Integración:** [`ParkedSalesModuleTest.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/server/src/test/kotlin/mx/nexuspos/server/ParkedSalesModuleTest.kt).
  - `testParkAndRetrieveSale`: Ciclo completo de creación, listado y recuperación.
  - `testDiscardParkedSale`: Descarte y exclusión de listas activas.
  - `testCannotParkEmptyItems`: Validación de rechazo ante carritos vacíos.
- **Pruebas de Frontend Desktop:** [`ParkedSalesFeatureTest.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/desktop/src/test/kotlin/mx/nexuspos/desktop/sales/ParkedSalesFeatureTest.kt).
  - `testParkCurrentSaleSuccess`: Aparcado y reinicio de pantalla.
  - `testRetrieveParkedSaleIntoEmptyCart`: Inyección directa en ticket vacío.
  - `testRetrieveParkedSaleWhenCartHasItemsCreatesNewTicket`: Apertura de nueva pestaña sin sobreescritura.
