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

### F. Importación y Exportación Masiva de Catálogo (`/api/v1/catalog`)

#### `GET /api/v1/catalog/export`
Genera y descarga el catálogo completo de productos en formato CSV compatible con Microsoft Excel y hojas de cálculo.
- **Convenciones y Formato:**
  - Codificación UTF-8 con BOM (`\uFEFF`) para apertura directa en Excel sin caracteres corruptos ni requerir asistentes de importación.
  - Especificación RFC 4180 con escape de comillas dobles y separador de comas.
  - Valores monetarios en pesos con dos decimales (`18.50`).
  - Encabezados: `Código de Barras,SKU,Nombre del Producto,Departamento,Unidad,Costo ($),Precio de Venta ($),Maneja Inventario,Stock Mínimo,Stock Máximo,Stock Actual,Activo`.
- **Respuesta 200 OK:** Flujo de texto `text/csv; charset=utf-8` con encabezado `Content-Disposition: attachment; filename="nexuspos_catalogo_YYYYMMDD_HHMMSS.csv"`.

#### `POST /api/v1/catalog/import`
Procesa un lote de productos para actualización masiva o alta inicial mediante upsert inteligente.
- **Reglas de Negocio:**
  - **Upsert por Identificador:** Busca coincidencias prioritariamente por `sku` o por `barcode`. Si el producto existe, actualiza sus nombres, precios, costos y existencias. Si no existe, genera el alta automática.
  - **Creación On-the-Fly de Departamentos:** Si el nombre del departamento especificado no existe, lo crea automáticamente en la base de datos.
  - **Tolerancia a Fallos:** Procesa cada fila de forma aislada. Filas con errores individuales (ej. precio negativo) se reportan en el detalle de resultados sin abortar la importación de los registros válidos.
  - **Sincronización:** Emite el evento WebSocket `catalog.changed` para que todas las cajas actualicen su caché local.
- **Cuerpo de Solicitud:**
  ```json
  {
    "rows": [
      {
        "sku": "SKU-SAB45",
        "barcode": "7501011115555",
        "name": "Sabritas Sal 45g",
        "departmentName": "Botanas",
        "baseUnit": "PZA",
        "costCents": 1200,
        "priceCents": 1800,
        "usesInventory": true,
        "minStockMillis": 5000,
        "maxStockMillis": 30000,
        "stockMillis": 24000,
        "isActive": true
      }
    ]
  }
  ```
- **Respuesta 200 OK:**
  ```json
  {
    "totalRows": 1,
    "createdCount": 1,
    "updatedCount": 0,
    "failedCount": 0,
    "results": [
      {
        "rowIndex": 0,
        "status": "CREATED",
        "message": "Producto creado exitosamente",
        "sku": "SKU-SAB45"
      }
    ]
  }
  ```

---

### F. Sesiones de Caja, Arqueo y Movimientos (`/api/v1/cash-sessions`)

#### `POST /api/v1/cash-sessions`
Abre un nuevo turno de caja en una terminal con su fondo inicial de apertura.
- Requiere el permiso `cash.open`. El `userId` corresponde al usuario autenticado.
- Si `commerce.requireOpeningFund` está desactivado, el servidor guarda `initialFundCents` en cero.
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
- Requiere el permiso `cash.view`.
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

#### `POST /api/v1/cash-sessions/{id}/movements/history`
Consulta hasta 30 movimientos de un turno abierto o cerrado. Requiere el PIN de un encargado activo en `pin`. Usa el cursor `next` de la respuesta como `before` para cargar la página siguiente. Las reversiones incluyen el vínculo al movimiento original y el encargado que las autorizó.

#### `POST /api/v1/cash-sessions/{id}/movements/{movementId}/reverse`
Requiere `pin` de un encargado activo y `reason` de hasta 256 caracteres. En un turno abierto registra un movimiento de tipo opuesto por el importe completo del original. El arqueo incluye los dos movimientos en sus importes brutos y aplica el efecto de ambos al saldo. Un reintento con el mismo encargado y motivo devuelve la reversión existente. Un nuevo intento de reversión después del cierre devuelve `409 Conflict`.

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
Consulta tickets de turnos abiertos. Si `sessionId` corresponde a un turno cerrado, devuelve `403 Forbidden`. Las consultas generales o por folio solo muestran turnos abiertos.
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
Obtiene el ticket y las partidas de una venta de turno abierto. Esta ruta y `GET /api/v1/sales/{id}/refunds` devuelven `403 Forbidden` para ventas de turnos cerrados.
- **Respuesta 200 OK:** Objeto `SaleResponse`.

#### `POST /api/v1/cash-sessions/history/{id}/sales`
Lista las ventas del turno cerrado. Requiere `pin` de un encargado activo en el cuerpo. Acepta `folio` para una búsqueda exacta y `beforeFolio` para cargar la página siguiente. Devuelve hasta 30 ventas y `nextBeforeFolio` cuando quedan resultados.

#### `POST /api/v1/cash-sessions/history/{id}/sales/{saleId}`
Consulta artículos, cobros y devoluciones de una venta del turno indicado con el mismo PIN. Incluye motivo y fecha de cancelación cuando se guardaron. Devuelve `404 Not Found` si la venta no corresponde al turno.

#### `POST /api/v1/sales`
Ejecuta la transacción atómica de cobro de una venta con soporte para pagos únicos o **cobro dividido / multi-pago (Split Payment)**.
- **Validaciones Atómicas:**
  - Requiere encabezado `Idempotency-Key` único.
  - Valida que la suma de `amountCents` de todas las formas de pago cubra el `totalCents` de la venta.
  - Descuenta existencias en inventario (`usesInventory = true`) registrando movimientos de Kardex tipo `SALE`.
  - **Efectivo (`CASH`):** Suma la porción cobrada en efectivo a `expected_cash_cents` de la sesión de caja activa. El cambio entregado se calcula exclusivamente sobre la porción en efectivo (`receivedCents - amountCents`).
  - **Tarjeta (`CARD`):** Registra el importe cobrado y el número de autorización bancaria / voucher en `reference`.
  - **Transferencia (`TRANSFER`):** Registra el importe y la clave de rastreo / folio SPEI en `reference`.
  - **Crédito Fiado (`CREDIT`):** Valida que el cliente exista, tenga crédito habilitado y $\text{Saldo Actual} + \text{Monto Fiado} \le \text{Límite de Crédito}$, generando el cargo correspondiente en el Kardex de crédito.
- **Cuerpo de Solicitud:**
  ```json
  {
    "cashSessionId": "sess-caja1-101",
    "cashierUserId": "usr-admin-1",
    "customerId": "cust-001",
    "subtotalCents": 30000,
    "taxCents": 0,
    "totalCents": 30000,
    "lines": [
      {
        "productId": "prod-1",
        "presentationId": "pres-1",
        "quantityMillis": 2000,
        "unitPriceCents": 15000,
        "taxRate": 0.0,
        "taxCents": 0,
        "totalCents": 30000
      }
    ],
    "payments": [
      {
        "method": "CASH",
        "amountCents": 10000,
        "receivedCents": 20000,
        "reference": ""
      },
      {
        "method": "CARD",
        "amountCents": 20000,
        "receivedCents": 20000,
        "reference": "AUTH-4812 Banorte"
      }
    ]
  }
  ```
- **Respuesta 201 Created:** Objeto `SaleResponse` con `payments` desglosados y número de folio consecutivo único.

#### `POST /api/v1/sales/{id}/cancel`
Cancela una venta de un turno abierto y revierte sus operaciones en una sola transacción. Una venta de un turno cerrado devuelve `409 Conflict`. Para devolver mercancía de otro turno se requiere un turno activo y el PIN de un encargado.
1. Revierte el inventario: reingresa las cantidades al stock de productos y registra movimientos en Kardex tipo `REFUND`.
2. Revierte crédito fiado: si la venta fue a crédito, descuenta la deuda del balance del cliente y registra movimiento de crédito tipo `REVERSAL`.
3. Revierte efectivo de caja: si la venta tuvo cobros en efectivo, descuenta el importe de `expected_cash_cents` de la sesión de caja para que el arqueo de caja [F6] cuadre al centavo.
4. Actualiza el estado de la venta a `"CANCELLED"` y guarda el motivo y la fecha.
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

#### `POST /api/v1/reports/sales-analysis`
Requiere el PIN de un encargado en `pin`, fechas inclusivas `fromDate` y `toDate` con formato AAAA-MM-DD, y `timeZone` con un identificador como `America/Mexico_City`. `groupBy` admite `DAY`, `PRODUCT` u `DEPARTMENT`.

Devuelve ventas, devoluciones, cancelaciones, costo y utilidad bruta sin IVA. Cada ajuste se incluye en su fecha de registro. Una devolución revierte el costo proporcional al reingreso a inventario. Las cancelaciones antiguas sin fecha se excluyen de la actividad del reporte. El campo `estimatedCostEvents` indica cuántas partidas usan un costo estimado a partir del catálogo actual o carecen de costo de catálogo.

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

### K. Tickets en Espera Persistentes (`/api/v1/parked-sales`)

#### `GET /api/v1/parked-sales`
Lista todas las ventas pausadas activas (`status = 'PARKED'`) en orden cronológico inverso. Permite consultar las colas de espera compartidas entre todas las terminales de la red local.
- **Respuesta 200 OK:**
  ```json
  [
    {
      "id": "parked-1713139200000",
      "customLabel": "Cliente camisa azul",
      "cashierUserId": "usr-admin-1",
      "cashierName": "Don Pepe",
      "registerId": "caja-1",
      "totalCents": 8500,
      "itemsCount": 3,
      "items": [
        {
          "productId": "prod-1",
          "presentationId": "pres-1",
          "productName": "Leche Entera 1L",
          "presentationName": "Pieza",
          "quantityMillis": 2000,
          "unitPriceCents": 2800,
          "totalCents": 5600,
          "isWeighed": false
        }
      ],
      "status": "PARKED",
      "createdAt": 1713139200000,
      "retrievedAt": null,
      "retrievedByUserId": null
    }
  ]
  ```

#### `POST /api/v1/parked-sales`
Pone en espera / suspende un ticket de venta activo en el servidor central.
- **Reglas de Negocio:**
  - Persiste el carrito completo en la tabla `parked_sales` con serialización JSON de artículos, cantidades, precios e indicadores de granel.
  - No descuenta inventario ni afecta el balance de caja hasta que el ticket sea reanudado y cobrado.
  - Difunde el evento WebSocket `parked_sale.created` a todas las terminales LAN conectadas.
- **Cuerpo de Solicitud:**
  ```json
  {
    "customLabel": "Doña Carmen",
    "cashierUserId": "usr-admin-1",
    "cashierName": "Don Pepe",
    "registerId": "caja-1",
    "items": [
      {
        "productId": "prod-1",
        "presentationId": "pres-1",
        "productName": "Arroz 1kg",
        "presentationName": "Bolsa",
        "quantityMillis": 1000,
        "unitPriceCents": 2200,
        "totalCents": 2200,
        "isWeighed": false
      }
    ]
  }
  ```
- **Respuesta 201 Created:** Objeto `ParkedSaleResponse`.

#### `POST /api/v1/parked-sales/{id}/retrieve`
Reanuda un ticket en espera para su cobro en mostrador.
- **Reglas de Negocio:**
  - Marca atómicamente el estado a `"RETRIEVED"`.
  - Si otra caja intenta reanudar simultáneamente el mismo ticket, la transacción previene el doble despacho devolviendo error de conflicto.
  - Difunde el evento WebSocket `parked_sale.retrieved` para que todas las cajas actualicen su contador de tickets pendientes.
- **Cuerpo de Solicitud:**
  ```json
  {
    "retrievedByUserId": "usr-cajero-2"
  }
  ```
- **Respuesta 200 OK:** Objeto `ParkedSaleResponse` con los artículos listos para inyectar en el carrito activo.

#### `POST /api/v1/parked-sales/{id}/discard`
Descarta permanentemente un ticket retenido que el cliente desistió de comprar.
- **Reglas de Negocio:**
  - Marca el estado a `"DISCARDED"`.
  - Excluye el registro de la lista de pendientes activos.
  - Difunde el evento WebSocket `parked_sale.discarded`.
- **Respuesta 200 OK:** Objeto `ParkedSaleResponse` con `status: "DISCARDED"`.

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
| `catalog.changed` | Notificación de importación o actualización masiva del catálogo | `{"action": "import"}` |
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
| `parked_sale.created` | Ticket puesto en espera en cola compartida | `ParkedSaleResponse` |
| `parked_sale.retrieved` | Ticket en espera recuperado para cobro | `ParkedSaleResponse` |
| `parked_sale.discarded` | Ticket en espera descartado | `ParkedSaleResponse` |
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
