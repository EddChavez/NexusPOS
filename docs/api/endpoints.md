# Contratos de API: Endpoints REST y WebSocket

Este documento detalla la especificación formal y exhaustiva de la API HTTP/JSON versión 1 (`/api/v1`) y el canal de eventos WebSocket en tiempo real de NexusPOS.

---

## 1. Convenciones y Encabezados Globales

- **Formato:** JSON (`application/json`) con codificación UTF-8.
- **Valores Monetarios:** Siempre en centavos enteros (`Long`). Ejemplo: `$18.50` = `1850`, `$500.00` = `50000`.
- **Cantidades:** Siempre en milésimas de unidad (`Long`). Ejemplo: `1 pza` = `1000`, `0.250 kg` = `250`.
- **Encabezados Específicos:**
  - `Idempotency-Key`: UUID único generado por el cliente para operaciones de escritura críticas (`POST /api/v1/sales`).
  - `X-Device-Token`: Token SHA-256 de la terminal emparejada.

---

## 2. Catálogo de Endpoints

### A. Diagnóstico y Sistema

#### `GET /health`
Verifica la disponibilidad del servidor y conexión con la base de datos PostgreSQL.
- **Respuesta 200 OK:**
  ```json
  {
    "status": "UP"
  }
  ```

#### `GET /api/v1/system/info`
Obtiene metadatos del servidor para verificar compatibilidad de versiones de cliente.
- **Respuesta 200 OK:**
  ```json
  {
    "appVersion": "2.0.0",
    "apiVersion": "v1",
    "serverTime": 1713139200000,
    "dbEngine": "PostgreSQL 16",
    "connectedDevices": 2
  }
  ```

#### `GET /api/v1/system/settings`
Obtiene las políticas y configuración actual de la tienda.
- **Respuesta 200 OK:**
  ```json
  {
    "storeName": "Abarrotes Don Pepe",
    "storeAddress": "Av. Hidalgo 123, Centro",
    "storePhone": "555-987-6543",
    "storeTaxId": "XAXX010101000",
    "ticketHeader": "¡Gracias por su compra!",
    "ticketFooter": "Conserve su ticket para aclaraciones.",
    "allowNegativeStock": true,
    "currencySymbol": "$"
  }
  ```

#### `PUT /api/v1/system/settings`
Actualiza la configuración y políticas de la tienda (nombre, dirección, teléfono, RFC, leyenda del ticket, política de inventario negativo y símbolo de moneda).
- **Cuerpo de Solicitud y Respuesta 200 OK:** Mismo esquema que `GET /api/v1/system/settings`.

---

### B. Gestión de Usuarios y Roles (RBAC)

#### `GET /api/v1/users`
Lista todos los usuarios del sistema.
- **Query Params:**
  - `includeInactive` (`Boolean`, opcional, default: `false`): Si es `true`, incluye usuarios dados de baja lógica.
- **Respuesta 200 OK:**
  ```json
  [
    { "id": "usr-a1b2c3", "name": "Administrador", "role": "ADMIN", "isActive": true },
    { "id": "usr-d4e5f6", "name": "Don Pepe Cajero", "role": "CASHIER", "isActive": true }
  ]
  ```

#### `POST /api/v1/users`
Crea un nuevo usuario con PIN numérico de 4 dígitos (almacenado con hash seguro BCrypt).
- **Cuerpo:**
  ```json
  {
    "name": "María García",
    "role": "SUPERVISOR",
    "pin": "5678"
  }
  ```
- **Respuesta 201 Created:** Objeto `UserDto` del usuario creado.
- **Errores:**
  - `400 Bad Request`: Formato de PIN inválido (debe cumplir `^\d{4}$`).
  - `409 Conflict`: `PIN_ALREADY_IN_USE` (El PIN ya pertenece a otro usuario activo).

#### `GET /api/v1/users/{id}`
Obtiene un usuario por su identificador único.
- **Respuesta 200 OK:** Objeto `UserDto`.
- **404 Not Found:** Usuario no encontrado.

#### `PUT /api/v1/users/{id}`
Actualiza el nombre, rol y/o PIN de un usuario. Si `pin` se omite o es `null`, se mantiene el PIN anterior.
- **Cuerpo:**
  ```json
  {
    "name": "María G. Encargada",
    "role": "SUPERVISOR",
    "pin": null
  }
  ```
- **Respuesta 200 OK:** Objeto `UserDto` actualizado.
- **422 Unprocessable Entity:** `LAST_ADMIN_CANNOT_BE_DEMOTED` (no se puede degradar al único administrador del sistema).

#### `DELETE /api/v1/users/{id}`
Realiza la baja lógica (`isActive = false`).
- **Respuesta 200 OK:** `UserDto` con `isActive: false`.
- **422 Unprocessable Entity:** `LAST_ADMIN_CANNOT_BE_DEACTIVATED` (no se puede desactivar al único administrador del sistema).

#### `POST /api/v1/users/{id}/reactivate`
Reactiva un usuario desactivado previamente.
- **Respuesta 200 OK:** `UserDto` con `isActive: true`.

---

### C. Autenticación, Autorización y Emparejamiento de Dispositivos

#### `POST /api/v1/auth/login`
Autenticación inicial de cajero mediante teclado numérico o PIN de 4 dígitos.
- **Cuerpo de Solicitud:**
  ```json
  {
    "pin": "1234",
    "deviceToken": "dev-token-caja-01"
  }
  ```
- **Respuesta 200 OK:**
  ```json
  {
    "token": "sess-jwt-token-xyz",
    "userId": "usr-admin-1",
    "name": "Don Pepe",
    "role": "ADMIN",
    "permissions": ["ACCESS_SETTINGS", "ADJUST_INVENTORY", "CANCEL_SALE", "OVERRIDE_CREDIT"]
  }
  ```
- **Error 401 Unauthorized:** PIN incorrecto o usuario desactivado.

#### `POST /api/v1/auth/authorize` y `POST /api/v1/auth/authorize-supervisor`
Elevación puntual de privilegios al vuelo. Permite autorizar acciones protegidas (`CANCEL_SALE`, `ADJUST_INVENTORY`, `ACCESS_SETTINGS`, `OVERRIDE_CREDIT`, `CASH_OUT`, `DISCOUNT`) ingresando el PIN de un usuario con rol `ADMIN` o `SUPERVISOR` sin cerrar la sesión del cajero en mostrador.
- **Cuerpo:**
  ```json
  {
    "pin": "1234",
    "action": "CANCEL_SALE"
  }
  ```
- **Respuesta 200 OK:**
  ```json
  {
    "authorized": true,
    "supervisorId": "usr-admin-1",
    "supervisorName": "Don Pepe (Administrador)"
  }
  ```
- **Error 401 Unauthorized:** PIN incorrecto o usuario sin rol supervisor/admin.

#### `POST /api/v1/pairing/codes`
Genera un código alfanumérico temporal de 6 caracteres para vincular terminales de punto de venta secundarias en la red local.
- **Respuesta 201 Created:**
  ```json
  {
    "code": "NEX782",
    "expiresAt": 1713142800000
  }
  ```

#### `POST /api/v1/pairing/claim`
Reclama un código de emparejamiento desde una terminal secundaria e intercambia credenciales por un `deviceToken` permanente.
- **Cuerpo:**
  ```json
  {
    "code": "NEX782",
    "deviceName": "Caja Secundaria Pasillo 2",
    "deviceType": "CASHIER_DESKTOP"
  }
  ```
- **Respuesta 201 Created:**
  ```json
  {
    "deviceToken": "dev-tok-99a8b7c6d5e4",
    "deviceId": "dev-caja-02"
  }
  ```

---

### D. Catálogo de Departamentos

#### `GET /api/v1/departments`
Lista todos los departamentos registrados en la tienda.
- **Respuesta 200 OK:**
  ```json
  [
    { "id": "dept-1", "name": "Abarrotes" },
    { "id": "dept-2", "name": "Bebidas y Refrescos" }
  ]
  ```

#### `POST /api/v1/departments`
Crea un nuevo departamento.
- **Cuerpo:** `{"name": "Lácteos y Quesos"}`
- **Respuesta 201 Created:** `{"id": "dept-3", "name": "Lácteos y Quesos"}`

#### `PUT /api/v1/departments/{id}`
Modifica el nombre de un departamento.
- **Cuerpo:** `{"name": "Lácteos y Salchichonería"}`
- **Respuesta 200 OK:** `{"id": "dept-3", "name": "Lácteos y Salchichonería"}`

#### `DELETE /api/v1/departments/{id}`
Elimina un departamento. Los productos vinculados se reasignan automáticamente al departamento general (`null`).
- **Respuesta 200 OK:** `{"status": "deleted", "id": "dept-3"}`

---

### E. Catálogo de Productos y Presentaciones

#### `GET /api/v1/products`
Búsqueda y listado de productos con filtros combinables.
- **Query Params:**
  - `barcode`: Búsqueda por código de barras exacto de cualquier presentación.
  - `query`: Búsqueda parcial de texto por nombre de producto o SKU.
  - `departmentId`: Identificador del departamento.
  - `includeInactive`: `true` para incluir productos dados de baja lógica.
- **Respuesta 200 OK:**
  ```json
  [
    {
      "id": "prod-coca-600",
      "sku": "SKU-COCA600",
      "name": "Coca-Cola Original 600ml",
      "departmentId": "dept-2",
      "baseUnit": "PZA",
      "costCents": 1400,
      "taxRate": 0.16,
      "usesInventory": true,
      "minStockMillis": 10000,
      "maxStockMillis": 50000,
      "isActive": true,
      "stockMillis": 24000,
      "presentations": [
        {
          "id": "pres-coca-600",
          "barcode": "7501055310884",
          "name": "Pieza",
          "priceCents": 1900,
          "factorMillis": 1000
        }
      ]
    }
  ]
  ```

#### `GET /api/v1/products/{id}`
Obtiene el detalle completo de un producto por ID.

#### `POST /api/v1/products`
Crea un producto nuevo en el catálogo junto con sus presentaciones y precios. Si el `sku` se envía en blanco, el servidor genera uno automáticamente.
- **Cuerpo:**
  ```json
  {
    "sku": "",
    "name": "Sabritas Sal 45g",
    "departmentId": "dept-1",
    "baseUnit": "PZA",
    "costCents": 1200,
    "taxRate": 0.08,
    "usesInventory": true,
    "minStockMillis": 5000,
    "maxStockMillis": 30000,
    "isActive": true,
    "presentations": [
      {
        "barcode": "7501011115555",
        "name": "Bolsa 45g",
        "factorMillis": 1000,
        "priceCents": 1800
      }
    ]
  }
  ```
- **Respuesta 201 Created:** Objeto `ProductDto`.

#### `PUT /api/v1/products/{id}`
Actualiza los datos maestros y presentaciones de un producto existente.

#### `DELETE /api/v1/products/{id}`
Baja lógica (*soft-delete*): marca `isActive = false` evitando su selección en el punto de cobro sin romper la integridad referencial en ventas anteriores.

#### `POST /api/v1/products/{id}/reactivate`
Reactiva un producto previamente dado de baja lógica.

---

### F. Sesiones de Caja, Arqueo y Movimientos (`/api/v1/cash-sessions`)

#### `POST /api/v1/cash-sessions`
Abre un nuevo turno de caja en una terminal con su fondo inicial de apertura.
- **Cuerpo:**
  ```json
  {
    "registerId": "caja-1",
    "userId": "usr-admin-1",
    "initialFundCents": 50000
  }
  ```
- **Respuesta 201 Created:** Objeto `CashSessionSummary` con estado `"OPEN"`.

#### `GET /api/v1/cash-sessions/current?registerId={id}`
Obtiene la sesión de caja activa de la terminal especificada.
- **Respuesta 200 OK:**
  ```json
  {
    "sessionId": "sess-caja1-101",
    "registerId": "caja-1",
    "registerName": "Caja Principal",
    "cashierId": "usr-admin-1",
    "cashierName": "Don Pepe",
    "openedAt": 1713135600000,
    "closedAt": null,
    "initialFundCents": 50000,
    "salesCashCents": 125000,
    "creditPaymentsCashCents": 20000,
    "cashInCents": 0,
    "cashOutCents": 15000,
    "refundsCashCents": 0,
    "expectedCashCents": 180000,
    "countedCashCents": null,
    "differenceCents": null,
    "status": "OPEN"
  }
  ```
- **Error 404 Not Found:** `NO_OPEN_SESSION` (La caja no tiene ningún turno abierto actualmente).

#### `GET /api/v1/cash-sessions/{id}/summary`
Obtiene el resumen financiero consolidado en tiempo real de una sesión de caja específica para la generación del **Corte X (Arqueo Parcial)**.
- **Respuesta 200 OK:** Objeto `CashSessionSummary`.

#### `POST /api/v1/cash-sessions/{id}/movements`
Registra un movimiento manual de efectivo en la caja (entrada de cambio o retiro de proveedor/gastos).
- **Cuerpo:**
  ```json
  {
    "type": "CASH_OUT",
    "amountCents": 15000,
    "concept": "Pago a repartidor de Pan Dulce",
    "userId": "usr-admin-1",
    "authorizedByUserId": null
  }
  ```
- **Respuesta 201 Created:** `{"status": "OK"}`.

#### `POST /api/v1/cash-sessions/{id}/close`
Cierra oficialmente el turno de caja (**Corte Z**). Registra el efectivo contado físicamente por el cajero, calcula la diferencia de arqueo (`countedCashCents - expectedCashCents`) y marca el turno como `"CLOSED"`.
- **Cuerpo:**
  ```json
  {
    "countedCashCents": 180500,
    "notes": "Turno finalizado sin incidentes. Sobrante de $5.00."
  }
  ```
- **Respuesta 200 OK:** Objeto `CashSessionSummary` cerrado con `differenceCents: 500`.

---

### G. Clientes y Crédito Fiado (`/api/v1/customers`)

#### `GET /api/v1/customers`
Busca clientes por nombre, teléfono o RFC.
- **Query Params:**
  - `query` (`String`, opcional): Término de búsqueda.
  - `includeInactive` (`Boolean`, opcional, default: `false`).
- **Respuesta 200 OK:**
  ```json
  [
    {
      "id": "cust-001",
      "folio": "CLI-001",
      "name": "Doña Carmen López",
      "phone": "555-123-4567",
      "email": "carmen@example.com",
      "rfc": "XAXX010101000",
      "address": "Calle Rosales #45",
      "notes": "Vecina de la esquina",
      "creditEnabled": true,
      "hasCreditLimit": true,
      "creditLimitCents": 50000,
      "currentBalanceCents": 12000,
      "availableCreditCents": 38000,
      "isActive": true,
      "createdAt": 1713135600000,
      "updatedAt": 1713135600000
    }
  ]
  ```

#### `GET /api/v1/customers/{id}`
Obtiene la ficha técnica completa del cliente por su ID.

#### `POST /api/v1/customers`
Registra un nuevo cliente con parámetros de línea de crédito.
- **Cuerpo:**
  ```json
  {
    "name": "Don Alberto Gómez",
    "phone": "555-999-8888",
    "email": "",
    "rfc": "",
    "address": "Av. Principal #10",
    "notes": "Taller mecánico",
    "creditEnabled": true,
    "hasCreditLimit": true,
    "creditLimitCents": 100000
  }
  ```
- **Respuesta 201 Created:** Objeto `CustomerDto`.

#### `PUT /api/v1/customers/{id}`
Actualiza los datos del cliente, habilitación de fiado y límite crediticio.
- **Cuerpo:** Objeto `UpdateCustomerRequest`.
- **Respuesta 200 OK:** Objeto `CustomerDto` actualizado.

#### `DELETE /api/v1/customers/{id}`
Baja lógica del cliente (`isActive = false`).

#### `GET /api/v1/customers/{id}/statement`
Obtiene el **Estado de Cuenta** consolidado del cliente con saldo actual, total de compras cargadas al fiado, total de abonos realizados y el desglose cronológico de movimientos.
- **Respuesta 200 OK:**
  ```json
  {
    "customer": { ... },
    "movements": [ ... ],
    "totalChargesCents": 85000,
    "totalPaymentsCents": 55000
  }
  ```

#### `POST /api/v1/customers/{id}/credit/payments`
Registra un abono en efectivo a la cuenta fiada del cliente. Impacta atómicamente el saldo del cliente y el efectivo esperado de la sesión de caja activa.
- **Cuerpo:**
  ```json
  {
    "customerId": "cust-001",
    "cashSessionId": "sess-caja1-101",
    "cashierUserId": "usr-admin-1",
    "amountCents": 20000,
    "paymentMethod": "CASH",
    "notes": "Abono quincenal",
    "receiptFolio": "ABN-104"
  }
  ```
- **Respuesta 201 Created:** Objeto `CreditMovementDto`.

#### `GET /api/v1/customers/{id}/credit/movements`
Retorna el historial de movimientos de crédito (`CHARGE`, `PAYMENT`, `REVERSAL`, `ADJUSTMENT`) del cliente.

---

### H. Ventas, Historial del Turno y Cancelaciones (`/api/v1/sales`)

#### `GET /api/v1/sales`
Consulta el historial de tickets de venta con filtros avanzados para el módulo de ventas del turno y auditoría.
- **Query Params:**
  - `sessionId`: Filtra las ventas correspondientes a una sesión de caja determinada.
  - `folio`: Búsqueda por número de folio exacto.
  - `status`: Filtra por estado (`COMPLETED`, `CANCELLED`, `REFUNDED`).
  - `startDate`: Timestamp inicial en milisegundos (epoch ms).
  - `endDate`: Timestamp final en milisegundos (epoch ms).
  - `limit`: Límite de resultados a retornar (por defecto `100`).
- **Respuesta 200 OK:**
  ```json
  [
    {
      "id": "sale-001",
      "folio": 1001,
      "cashSessionId": "sess-caja1-101",
      "cashierName": "Don Pepe",
      "customerName": "Público en General",
      "customerRemainingCreditCents": null,
      "status": "COMPLETED",
      "subtotalCents": 4800,
      "taxCents": 0,
      "totalCents": 4800,
      "createdAt": 1713139200000,
      "lines": [
        {
          "id": "line-1",
          "productId": "prod-1",
          "presentationId": "pres-1",
          "description": "Refresco Cola 600ml",
          "quantityMillis": 2000,
          "unitPriceCents": 1800,
          "taxRate": 0.0,
          "taxCents": 0,
          "totalCents": 3600
        }
      ],
      "payments": [
        {
          "id": "pay-1",
          "method": "CASH",
          "amountCents": 4800,
          "receivedCents": 5000,
          "changeCents": 200,
          "reference": ""
        }
      ]
    }
  ]
  ```

#### `GET /api/v1/sales/{id}`
Obtiene el ticket y partidas completas de una venta individual por su identificador único.
- **Respuesta 200 OK:** Objeto `SaleResponse`.

#### `POST /api/v1/sales`
Ejecuta la transacción atómica de cobro de una venta.
- **Validaciones Atómicas:**
  - Requiere encabezado `Idempotency-Key` único.
  - Descuenta existencias en inventario (`usesInventory = true`).
  - Si incluye pago en `"CASH"`, suma el monto cobrado al efectivo esperado en la sesión de caja.
  - Si incluye pago en `"CREDIT"`, valida que el cliente tenga crédito activo y no exceda su límite, creando el cargo correspondiente en el Kardex de crédito.
- **Cuerpo:** Objeto `CreateSaleRequest`.
- **Respuesta 201 Created:** Objeto `SaleResponse`.

#### `POST /api/v1/sales/{id}/cancel`
Cancela atómicamente una venta realizada, revirtiendo todas sus operaciones asociadas en una sola transacción:
1. Revierte el inventario: reingresa las cantidades al stock de productos y registra movimientos en Kardex tipo `REFUND`.
2. Revierte crédito fiado: si la venta fue a crédito, descuenta la deuda del balance del cliente y registra movimiento de crédito tipo `REVERSAL`.
3. Revierte efectivo de caja: si la venta tuvo cobros en efectivo, descuenta el importe de `expected_cash_cents` de la sesión de caja para que el arqueo de caja [F6] cuadre al centavo.
4. Actualiza el estado de la venta a `"CANCELLED"`.
- **Cuerpo de Solicitud:**
  ```json
  {
    "reason": "Error de captura de cajero",
    "authorizedByUserId": "usr-admin-1"
  }
  ```
- **Respuesta 200 OK:** Objeto `SaleResponse` actualizado con `status: "CANCELLED"`.

---

### I. Reportes Financieros Consolidados (`/api/v1/reports`)

#### `GET /api/v1/reports/daily`
Genera el reporte financiero consolidado del día o de una sesión específica, calculando ventas por método de pago, cobranza de fiados, costo de reposición de mercancía (COGS), ganancia bruta estimada y margen de rentabilidad.
- **Query Params:**
  - `date` (`Long`, opcional, default: día actual): Timestamp epoch ms de la fecha a consultar.
  - `sessionId` (`String`, opcional): Si se provee, acota el reporte a una sesión de caja determinada.
- **Respuesta 200 OK:**
  ```json
  {
    "dateEpochMs": 1713139200000,
    "totalSalesCents": 1250000,
    "salesCashCents": 950000,
    "salesCreditCents": 300000,
    "salesOtherCents": 0,
    "totalTicketsCount": 42,
    "completedTicketsCount": 40,
    "cancelledTicketsCount": 2,
    "cancelledAmountCents": 45000,
    "creditPaymentsCashCents": 80000,
    "cashInCents": 0,
    "cashOutCents": 15000,
    "totalCostCents": 750000,
    "estimatedGrossProfitCents": 500000,
    "profitMarginPercent": 40.0,
    "activeSessionsCount": 1
  }
  ```

---

### J. Inventario, Kardex y Ajustes (`/api/v1/inventory`)

#### `GET /api/v1/inventory/summary`
Retorna el resumen consolidado de existencias, productos con bajo stock y valuación monetaria global al costo y a precio de venta.
- **Respuesta 200 OK:**
  ```json
  {
    "totalProducts": 142,
    "lowStockCount": 8,
    "totalValuationCostCents": 18542000,
    "totalValuationSaleCents": 26895000,
    "items": [
      {
        "id": "prod-coca-600",
        "sku": "SKU-COCA600",
        "name": "Coca-Cola Original 600ml",
        "departmentId": "dept-2",
        "departmentName": "Bebidas",
        "baseUnit": "PZA",
        "costCents": 1400,
        "priceCents": 1900,
        "stockMillis": 24000,
        "minStockMillis": 10000,
        "maxStockMillis": 50000,
        "isLowStock": false
      }
    ]
  }
  ```

#### `GET /api/v1/inventory/movements`
Obtiene el historial de auditoría de movimientos del Kardex con soporte para filtros por producto, tipo de movimiento y límite.
- **Query Params:**
  - `productId`: Filtra los movimientos de un artículo específico.
  - `type`: `PURCHASE`, `SALE`, `ADJUSTMENT` o `RETURN`.
  - `limit`: Cantidad máxima de registros a devolver (por defecto `100`).
- **Respuesta 200 OK:**
  ```json
  [
    {
      "id": "mov-001",
      "productId": "prod-coca-600",
      "productName": "Coca-Cola Original 600ml",
      "type": "PURCHASE",
      "quantityMillis": 24000,
      "previousStockMillis": 0,
      "resultingStockMillis": 24000,
      "costCents": 1400,
      "reference": "FAC-PROV-9821",
      "notes": "Recepción de pedido semanal",
      "createdAt": 1713139200000
    }
  ]
  ```

#### `POST /api/v1/inventory/movements`
Registra un nuevo movimiento manual en el Kardex (compra, ajuste por merma, conteo físico o corrección).
- **Cuerpo:**
  ```json
  {
    "productId": "prod-coca-600",
    "type": "ADJUSTMENT",
    "quantityMillis": -2000,
    "costCents": 1400,
    "reference": "MERMA-01",
    "notes": "Botellas rotas en acomodo"
  }
  ```
- **Respuesta 201 Created:** Objeto `StockMovementDto`.

---

## 3. Canal de Eventos en Tiempo Real (WebSocket)

- **URL:** `ws://<server-ip>:8080/ws/events`
- **Estructura del Mensaje (JSON):**
  ```json
  {
    "type": "sale.completed",
    "timestamp": 1713139200000,
    "payload": "{ ... }"
  }
  ```

### Catálogo de Eventos Transmitidos:
| Evento | Descripción | Payload |
| :--- | :--- | :--- |
| `product.created` | Alta de nuevo artículo en el catálogo | `ProductDto` |
| `product.updated` | Modificación de datos maestros o presentaciones | `ProductDto` |
| `product.deactivated` | Baja lógica de producto | `ProductDto` |
| `product.reactivated` | Reactivación de producto | `ProductDto` |
| `department.created` | Creación de nuevo departamento | `DepartmentDto` |
| `department.updated` | Renombrado de departamento | `DepartmentDto` |
| `department.deleted` | Eliminación de departamento | `{"id": "dept-id"}` |
| `inventory.movement.created` | Registro de movimiento en Kardex | `StockMovementDto` |
| `inventory.updated` | Notificación de cambio de existencias | `{"productId": "..."}` |
| `cash.session.opened` | Apertura de turno de caja | `CashSessionSummary` |
| `cash.session.closed` | Cierre oficial de turno (Corte Z) | `CashSessionSummary` |
| `sale.completed` | Venta finalizada con éxito | `SaleResponse` |
| `sale.cancelled` | Cancelación de venta y reversión | `SaleResponse` |
| `customer.created` | Alta de cliente | `CustomerDto` |
| `customer.updated` | Edición de cliente o crédito | `CustomerDto` |
| `customer.deactivated` | Baja lógica de cliente | `CustomerDto` |
| `credit.payment` | Registro de abono a cuenta fiada | `CreditMovementDto` |

---

## 4. Estructura de Errores Tipados (`ApiError`)

Toda respuesta con código de error HTTP (4xx / 5xx) devuelve un objeto uniforme con código semántico:
```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "Existencias insuficientes para el producto Coca-Cola 600ml. Disponibles: 0",
  "details": {
    "productId": "prod-coca-600",
    "availableMillis": 0,
    "requestedMillis": 1000
  }
}
```

### Códigos de Error Frecuentes:
- `VALIDATION_ERROR` (400): Campos requeridos ausentes o tipos de datos inválidos.
- `INVALID_PIN` (401): PIN de 4 dígitos incorrecto o usuario inactivo.
- `UNAUTHORIZED` (403): Permisos insuficientes para realizar la operación solicitada.
- `NOT_FOUND` (404): Entidad o recurso inexistente en la base de datos.
- `NO_OPEN_SESSION` (404): La caja indicada no tiene ningún turno abierto.
- `PIN_ALREADY_IN_USE` (409): El PIN ya está asignado a otro usuario activo.
- `INSUFFICIENT_STOCK` (422): Existencias insuficientes bajo política de no permitir inventario negativo.
- `CREDIT_LIMIT_EXCEEDED` (422): La venta excede el límite de crédito disponible del cliente.
- `LAST_ADMIN_CANNOT_BE_DEMOTED` (422): Regla de seguridad para evitar dejar el sistema sin administradores.
- `INTERNAL_ERROR` (500): Excepción no controlada en el servidor central.
