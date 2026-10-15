# Contratos de API: Endpoints REST y WebSocket

Este documento detalla la especificación formal de la API HTTP/JSON versión 1 (`/api/v1`) y el canal de eventos WebSocket en tiempo real de NexusPOS.

---

## 1. Convenciones y Encabezados Globales

- **Formato:** JSON (`application/json`) con codificación UTF-8.
- **Valores Monetarios:** Siempre en centavos enteros (`Long`). Ejemplo: `$18.50` = `1850`.
- **Cantidades:** Siempre en milésimas de unidad (`Long`). Ejemplo: `1 pza` = `1000`, `0.250 kg` = `250`.
- **Encabezados Específicos:**
  - `Idempotency-Key`: UUID único generado por el cliente para operaciones de escritura críticas (`POST /api/v1/sales`).
  - `X-Device-Token`: Token SHA-256 de la terminal emparejada.

---

## 2. Catálogo de Endpoints

### A. Diagnóstico y Sistema

#### `GET /health`
Verifica la disponibilidad del servidor y conexión con PostgreSQL.
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
    "ticketHeader": "¡Gracias por su compra!",
    "ticketFooter": "Conserve su ticket para aclaraciones.",
    "allowNegativeStock": true,
    "currencySymbol": "$"
  }
  ```

#### `PUT /api/v1/system/settings`
Actualiza la configuración (requiere rol `ADMIN`).

---

### B. Autenticación y Autorización

#### `POST /api/v1/auth/login`
Autenticación inicial de cajero por PIN de 4 dígitos.
- **Cuerpo de Solicitud:**
  ```json
  {
    "pin": "1234",
    "registerId": "caja-1"
  }
  ```
- **Respuesta 200 OK:**
  ```json
  {
    "userId": "usr-admin-1",
    "name": "Don Pepe",
    "role": "ADMIN",
    "token": "sess-token-abc"
  }
  ```
- **Error 401 Unauthorized:** PIN incorrecto o usuario inactivo.

#### `POST /api/v1/auth/authorize-supervisor`
Elevación temporal de privilegios para autorizar acciones sensibles en caja.
- **Cuerpo:** `{"pin": "9999", "reason": "OVERDRAW_CREDIT"}`
- **Respuesta 200 OK:** `{"authorized": true, "supervisorName": "Encargado Juan"}`

---

### C. Catálogo de Productos y Búsqueda

#### `GET /api/v1/catalog/search?q={query}`
Búsqueda ultrarrápida por código de barras exacto o texto parcial en nombre.
- **Respuesta 200 OK:**
  ```json
  [
    {
      "id": "prod-coca-600",
      "name": "Coca-Cola Original 600ml",
      "unit": "PIECE",
      "taxRate": 0.16,
      "stockMillis": 24000,
      "presentations": [
        {
          "id": "pres-coca-600",
          "barcode": "7501055310884",
          "name": "Coca-Cola 600ml PZA",
          "priceCents": 1900,
          "factorMillis": 1000
        }
      ]
    }
  ]
  ```

---

### D. Sesiones de Caja y Arqueo

#### `GET /api/v1/cash/sessions/active?registerId={id}`
Obtiene el turno abierto actual de la terminal.

#### `POST /api/v1/cash/sessions/open`
Abre un nuevo turno de caja con fondo inicial.
- **Cuerpo:**
  ```json
  {
    "registerId": "caja-1",
    "userId": "usr-admin-1",
    "initialFundCents": 50000
  }
  ```

#### `POST /api/v1/cash/sessions/{id}/movements`
Registra una entrada o salida manual de efectivo.
- **Cuerpo:**
  ```json
  {
    "type": "CASH_OUT",
    "amountCents": 15000,
    "concept": "Pago a repartidor de Pan Dulce",
    "userId": "usr-admin-1"
  }
  ```

#### `POST /api/v1/cash/sessions/{id}/close`
Cierra el turno capturando el efectivo contado y calculando la diferencia según `RN-CAJ-04`.
- **Cuerpo:**
  ```json
  {
    "countedCashCents": 400500,
    "notes": "Turno matutino completo"
  }
  ```
- **Respuesta 200 OK:** Objeto `CashSessionSummary` con `expectedCashCents`, `countedCashCents` y `differenceCents`.

---

### E. Ventas y Cobro

#### `POST /api/v1/sales`
Ejecuta la transacción atómica de venta. Requiere cabecera `Idempotency-Key`.
- **Cuerpo de Solicitud:**
  ```json
  {
    "idempotencyKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "cashSessionId": "sess-caja1-101",
    "cashierUserId": "usr-admin-1",
    "customerId": null,
    "expectedTotalCents": 3500,
    "lines": [
      {
        "presentationId": "pres-coca-600",
        "description": "Coca-Cola 600ml",
        "quantityMillis": 1000,
        "expectedUnitPriceCents": 1800,
        "expectedTotalCents": 1800,
        "taxRate": 0.16
      },
      {
        "presentationId": "pres-huevo-granel",
        "description": "Huevo Blanco a Granel",
        "quantityMillis": 500,
        "expectedUnitPriceCents": 3400,
        "expectedTotalCents": 1700,
        "taxRate": 0.0
      }
    ],
    "payments": [
      {
        "method": "CASH",
        "amountCents": 3500,
        "receivedCents": 5000
      }
    ]
  }
  ```
- **Respuesta 201 Created:**
  ```json
  {
    "id": "sale-uuid-789",
    "folio": 101,
    "cashSessionId": "sess-caja1-101",
    "cashierName": "Don Pepe",
    "status": "COMPLETED",
    "subtotalCents": 3500,
    "taxCents": 0,
    "totalCents": 3500,
    "createdAt": 1713139200000,
    "lines": [...],
    "payments": [
      {
        "id": "pay-1",
        "method": "CASH",
        "amountCents": 3500,
        "receivedCents": 5000,
        "changeCents": 1500
      }
    ]
  }
  ```

---

### F. Crédito y Fiado

#### `GET /api/v1/credit/customers?q={query}`
Busca clientes por nombre o teléfono, retornando su límite y saldo actual.

#### `POST /api/v1/credit/customers/{id}/payments`
Registra un abono en efectivo a la cuenta de crédito.
- **Cuerpo:**
  ```json
  {
    "cashSessionId": "sess-caja1-101",
    "cashierUserId": "usr-admin-1",
    "amountCents": 20000,
    "concept": "Abono semanal Don Juan"
  }
  ```

---

## 3. Canal de Eventos en Tiempo Real (WebSocket)

- **URL:** `ws://<server-ip>:8080/ws/events`
- **Mensaje de Evento (JSON):**
  ```json
  {
    "type": "SALE_COMPLETED",
    "timestamp": 1713139200000,
    "payload": "{\"folio\": 101, \"totalCents\": 3500, \"cashier\": \"Don Pepe\"}"
  }
  ```

---

## 4. Estructura de Errores Tipados (`ApiError`)

Toda respuesta con código de error HTTP (4xx / 5xx) devuelve un objeto uniforme:
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
