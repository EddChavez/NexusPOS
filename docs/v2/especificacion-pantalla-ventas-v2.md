# Especificación Funcional, Arquitectura y Reglas de Negocio: Pantalla de Ventas V2 (Material Design 3)

**Documento:** NexusPOS-DOC-V2-SALES  
**Versión:** 2.0.0-DRAFT  
**Estado:** Aprobado para Implementación  
**Audiencia:** Desarrolladores, QA, Arquitectos de Software y Diseñadores de Producto  

---

## 1. Arquitectura Visual y Wireframes por Estado

### A. Vista Principal (Distribución Base y Pestañas Multi-Ticket)
```text
+-----+-----------------------------------------------+------------------------+
| NAV | [ Pedido María (x) ] [ Mesa 4 (x) ] [ + ]     | [ TARJETA PRODUCTO ]   |
|     +-----------------------------------------------+ Carne Molida De Res    |
| [V] | [ 🔍 Buscar por nombre, código o categoría ]  | $160 / Kg [Precio ▾]   |
|     +-----------------------------------------------+------------------------+
| [C] | LISTA DE PRODUCTOS EN TICKET                  | ACCIONES RÁPIDAS       |
|     | +-------------------------------------------+ | +----------+---------+ |
| [P] | | [img] Carne Molida  [-] 1.500 [+] $240  ⋮ | | | Venta    | Verif.  | |
|     | | [img] Paquete Desayuno [-] 1  [+]  $85  ⋮ | | | Rápida   | Precios | |
| [I] | | [img] Producto C       [-] 1  [+]  $45  ⋮ | | +----------+---------+ |
|     | |                                           | | | Entrada  | Salida  | |
| [R] | |                                           | | | Efectivo | Efectivo| |
|     | |                                           | | +----------+---------+ |
|     | |                                           | | | Asignar  | Desc.   | |
|     | |                                           | | | Cliente  | Global  | |
|     | |                                           | | | (Juan P.)|         | |
|     | |                                           | | +----------+---------+ |
|     | |                                           | | | WhatsApp | Abrir   | |
| [⚙] | +-------------------------------------------+ | |          | Cajón   | |
| [U] | 3 artículos / 3.500 unid. [ 📝 ] [ COBRAR $ ] | +----------+---------+ |
+-----+-----------------------------------------------+------------------------+
| [Toast]: ✓ Cajón de dinero abierto (Registrado en bitácora - Cajero: J.V.)   |
| BARRA ESTADO: Cajero activo  |  ● Conectado         |  Fecha · Hora          |
+------------------------------------------------------------------------------+
```

### B. Distribución Proporcional de Pantalla
* **Navegación Lateral Izquierda (Navigation Rail M3):** Ancho fijo de 72 dp.
* **Área Central de Captura y Venta:** Ancho dinámico `weight(1f)`.
  * Cabecera: Fila de pestañas multi-ticket (altura 48 dp).
  * Omnibox: Barra flotante de búsqueda y escaneo continuo (altura 56 dp).
  * Carrito de Compras: Virtualizado con `LazyColumn` (altura adaptable).
  * Resumen y Cierre: Barra inferior de cobro y notas (altura 64 dp).
* **Panel Lateral Derecho de Contexto y Acciones:** Ancho fijo de 340 dp.
  * Tarjeta de Producto en Foco: Altura aproximada de 140 dp.
  * Cuadrícula de Acciones Rápidas: 2 columnas × 4 filas de botones de alto impacto.
* **Pie de Telemetría (System Status Bar M3):** Altura fija de 28 dp.
* **Capa Flotante de Notificaciones (Audit Toast):** Superpuesta en la base inferior izquierda/centro.

---

## 2. Especificación Funcional por Módulo y Componente

### 2.1. Navegación Principal (Navigation Rail)
* **Comportamiento:** Permite cambiar instantáneamente entre los módulos del sistema manteniendo intacta la memoria de las ventas en curso.
* **Atajos Globales de Función:**
  * `[F1]` Ventas (vista por defecto)
  * `[F2]` Clientes
  * `[F3]` Productos
  * `[F4]` Inventario
  * `[F5]` Compras / Proveedores *(Enlace a backlog en mejoras pendientes)*
  * `[F6]` Cortes de Caja y Reportes
* **Perfil de Usuario Activo:** Muestra el avatar del cajero con badge de turno abierto, rol asignado (`CASHIER` / `SUPERVISOR`) y opción de bloqueo rápido de pantalla (`Ctrl+L`).

---

### 2.2. Gestión Multi-Ticket y Función PLUS de Ventas en Espera en Red (Parked Sales)
* **Pestañas Locales en Memoria:**
  * Soporte para múltiples tickets abiertos en paralelo (ej. "Pedido María", "Mesa 4", "Ticket 3").
  * Doble clic sobre la pestaña abre diálogo rápido para **renombrar el ticket**.
  * Botón `+` (`Ctrl+T`): Genera una nueva pestaña en blanco asignando un identificador único en memoria.
  * Botón `✕` (`Ctrl+W`): Cierra la pestaña. Si tiene productos cargados, despliega diálogo de confirmación.
* **Función PLUS: Ventas Archivadas en Red (Parked Sales / Venta en Espera Centralizada):**
  * Permite **archivar un ticket completo en la base de datos del servidor LAN** (`Ctrl+E`), liberando la terminal local.
  * Indicador visual en la barra de tickets con contador de ventas en espera en la red (ej. `[ 📥 2 en espera ]`).
  * Cualquier terminal de la red local puede listar y desarchivar el ticket para continuar con el cobro en caja principal.

---

### 2.3. Buscador Omnibox con Autocompletado Predictivo
* **Captura de Entrada Continua:**
  * El cursor regresa automáticamente al Omnibox tras cualquier acción operativa.
  * Discriminación de hardware: Diferencia la lectura de un escáner láser de código de barras (entrada ultra-rápida finalizada en `Enter`) de la escritura manual del cajero.
* **Menú Flotante Predictivo (M3 Overlay):**
  * Al escribir al menos 2 caracteres, despliega una lista flotante de coincidencias mostrando:
    1. **Miniatura:** Foto del producto o contenedor tonal M3 con icono de categoría e iniciales.
    2. **Nombre y Código:** Nombre comercial y código de barras/SKU.
    3. **Badge de Existencia:** Semáforo de stock (Verde: disponible, Amarillo: stock bajo $\le$ mínimo, Rojo: agotado o negativo).
    4. **Categoría y Precio:** Departamento y precio unitario en centavos formateados.
* **Navegación sin Mouse:** Flechas `↑` y `↓` para resaltar elementos del menú flotante, y tecla `Enter` para agregar de inmediato al ticket activo.

/art---

### 2.4. Carrito de Compras en Ticket Activo
* **Lista de Partidas:**
  * Foto en miniatura (48×48 dp) con bordes redondeados (8 dp).
  * Nombre, presentación y código del artículo.
  * Selector interactivo de cantidad:
    * Botón `[-]`: Decrementa en 1 unidad (o fraccionado si es granel).
    * Campo editable numérico: Permite teclear directamente la cantidad exacta (ej. `1.500`, `3`, `0.750`).
    * Botón `[+]`: Incrementa en 1 unidad.
  * Precio unitario e importe total de la línea.
  * Menú Contextual (`⋮`):
    * `Eliminar artículo` (`Del`).
    * `Aplicar descuento a partida` (porcentaje o monto en centavos).
    * `Modificar precio unitario` (sujeto a validación de supervisor).

---

### 2.5. Tarjeta de Producto Seleccionado y Gestión de Precios
* **Ubicación:** Panel superior derecho.
* **Detalle del Producto en Foco:** Muestra la ficha del último producto escaneado o seleccionado en el carrito:
  * Fotografía o avatar del producto.
  * Nombre completo y unidad de medida (ej. `Carne Molida De Res - $160 / Kg`).
  * Existencia disponible en almacén/piso de venta.
* **Selector de Escalas de Precio `[Precio ▾]`:**
  * Desplegable M3 para conmutar la lista de precios asignada al producto en esa partida:
    * `Precio 1 (Menudeo)`
    * `Precio 2 (Mayoreo)`
    * `Precio 3 (Especial / Empleado)`
* **Acceso a Catálogo:** Botón para consultar o editar la ficha técnica del producto sin abandonar la pantalla de ventas.

---

### 2.6. Cuadrícula de Acciones Rápidas (8 Operaciones Clave con Insignias SO)
Matriz táctil y operable por teclado (2 columnas × 4 filas) con botones M3 de alta visibilidad e insignias dinámicas `<kbd>` adaptadas al sistema operativo:
1. **Venta Rápida [`F1` o `Ctrl+R` en Win \| `Cmd+R` en Mac]:** Despliega selector de productos genéricos sin código (tortillas, panadería, hielo, recargas).
2. **Verificador de Precios [`F9` en Win \| `Cmd+F9` en Mac]:** Modal flotante para escanear artículos y consultar precio/stock sin agregarlos al ticket.
3. **Entrada de Efectivo [`F7` en Win \| `Cmd+7` en Mac]:** Captura de fondo de caja, aportaciones de cambio o ingresos extraordinarios.
4. **Salida de Efectivo [`F8` en Win \| `Cmd+8` en Mac]:** Retiros de efectivo para pago a repartidores, proveedores locales o gastos de operación.
5. **Asignar Cliente [`F11` o `Ctrl+L` en Win \| `Cmd+L` en Mac]:** Búsqueda de cliente en cartera. Al seleccionarse, el botón se actualiza visualmente mostrando el nombre (ej. `Cliente: Juan P.`) y su saldo disponible de crédito y monedero electrónico.
6. **Descuento Global [`F10` o `Ctrl+D` en Win \| `Cmd+D` en Mac]:** Aplica porcentaje o monto fijo de descuento al total del ticket.
7. **WhatsApp [`Alt+W` en Win \| `Option+W` en Mac]:** Despliega el diálogo de envío digital del último ticket o ticket en curso vía WhatsApp Web / API.
8. **Abrir Cajón [`Ctrl+K` o `Alt+C` en Win \| `Cmd+K` en Mac]:** Emisión de pulso de apertura manual al cajón de dinero con registro obligatorio de auditoría.

---

### 2.7. Cierre de Venta, Cobro y Telemetría
* **Resumen de Volumen:** Contador visible de artículos distintos y unidades acumuladas (ej. `3 artículos / 3.500 unid.`).
* **Nota al Ticket `[ 📝 ]`:** Diálogo rápido para añadir observaciones impresas en el ticket (ej. *"Entrega a domicilio"*, *"Mesa 4 exterior"*).
* **Botón Principal Cobrar [`F12`]:**
  * Botón masivo M3 con fondo primario de alto contraste.
  * Muestra el total a pagar con tipografía destacada: `[F12] COBRAR $370.00`.
  * Abre la pasarela de pagos multidivisa (Efectivo, Tarjeta de Crédito/Débito, Crédito en Tienda / Fiado, Vales).
* **Barra de Estado Inferior (Status Bar):**
  * Nombre del cajero en turno (ej. `Cajero: Juan V.`).
  * Estado de conexión LAN: `● Conectado` (verde) o `⚠️ Modo Contingencia Offline` (ámbar con contador de ventas pendientes).
  * Fecha y hora del sistema en tiempo real.
* **Toast de Auditoría Flotante:** Notificación en pantalla ante eventos sensibles:
  `✓ Cajón de dinero abierto (Registrado en bitácora - Cajero: J.V.)`.

---

## 3. Reglas de Negocio Críticas

### 3.1. Apertura Manual y Auditoría de Cajón de Dinero
* **Integración de Hardware:** El cajón de dinero conectado mediante cable RJ11 a la impresora térmica ESC/POS se activa mediante el pulso estándar `ESC p 0 25 250` (0x1B, 0x70, 0x00, 0x19, 0xFA).
* **Registro de Auditoría Obligatorio:**
  * Toda apertura manual por botón o atajo `Ctrl+B` sin que exista una venta en efectivo debe registrarse en la bitácora de auditoría:
    `{ timestamp, registerId, cashierUserId, cashierName, reason: "MANUAL_OPEN_DRAWER", balanceInDrawer }`.
  * Si la terminal está en red, se envía el evento en tiempo real al servidor; si está offline, se encola en la bitácora local de contingencia.
* **Feedback Visual:** Despliega inmediatamente un Toast confirmando la apertura y la auditoría.

### 3.2. Control de Inventario Negativo
* **Modos de Operación Configurables:**
  1. *Permisivo con Advertencia (Abarrotes alta rotación):* Permite agregar el producto aunque no haya stock en sistema, marcando el badge en rojo y registrando la partida con bandera de inventario negativo.
  2. *Estricto con Bloqueo:* Si `currentStockMillis <= 0` y `usesInventory == true`, el sistema bloquea la venta e indica *"Existencias agotadas"*.
  3. *Autorización de Supervisor:* Permite vender producto sin stock únicamente si un supervisor ingresa su PIN.

### 3.3. Venta a Granel y Productos Pesados (Báscula RS-232)
* Los productos con unidad base `KG` se comunican con el controlador de báscula serial.
* Si el peso detectado es estable (`isStableWeight`), se carga automáticamente en la cantidad de la partida.
* Si la báscula está desconectada o el peso fluctúa, se abre el diálogo de captura manual donde el cajero puede introducir el peso en gramos/kilogramos o el importe en dinero deseado por el cliente (calculando la cantidad exacta).

### 3.4. Venta de Kits, Paquetes y Combos
* Un producto clasificado como "Kit / Paquete" se visualiza en el ticket como una línea única comercial (ej. `Paquete Desayuno - $85.00`).
* En el backend y en la deducción de inventario, el sistema descuenta los insumos hijos asociados al paquete según su receta o lista de materiales (BOM).

### 3.5. Gestión de Precios y Modificaciones en Línea
* El cajero puede conmutar entre las escalas de precios autorizadas (`Menudeo`, `Mayoreo`, `Especial`) desde la tarjeta lateral.
* Cualquier descuento manual o cambio de precio por debajo del costo promedio ponderado (CPP) queda bloqueado por defecto y exige elevación de privilegios de supervisor vía PIN.

### 3.6. Crédito a Clientes (Fiado)
* Al asignar un cliente con `[F4]`, el sistema valida:
  1. Si el cliente tiene habilitada la línea de crédito (`creditAllowed == true`).
  2. Límite de crédito disponible vs Total del ticket actual.
  3. Existencia de facturas o saldos vencidos.
* Si el monto excede el saldo permitido, el botón de crédito en el modal de cobro se deshabilita, permitiendo únicamente autorización manual por supervisor.

---

### 3.7. Funcionalidades de Alto Valor Rescatadas de V1 (100% Preservadas)
1. **Reimpresión de Último Ticket:** Emisión de duplicado térmico inmediato si se termina el rollo de papel o el cliente lo solicita.
2. **Historial de Ventas del Turno y Devoluciones:** Consulta de tickets emitidos durante la sesión actual con soporte para devoluciones parciales/totales de partidas e reingreso condicional a inventario.
3. **Cancelación Formal de Venta:** Cancelación de folio auditada con captura obligatoria de motivo (`CancelSaleReasonDialog`) y validación de supervisor.
4. **Modo Contingencia Offline LAN:** Continuidad operativa total sin servidor central: emisión con folios `OFF-XXXX`, resguardo en cola local y sincronización asíncrona mediante `OfflineSyncDialog`.
5. **Monedero Electrónico y Fidelización:** Visualización del saldo de cashback acumulado por el cliente y opción de pago con monedero en la pasarela de cobro.
6. **Acción Digital WhatsApp Post-Venta:** Disparo de notificación/banner post-cobro para enviar el ticket por WhatsApp con un solo clic.
7. **Re-pesaje Dinámico de Partida RS-232:** Botón de báscula `[⚖]` en el stepper del carrito para refrescar la lectura de la báscula serial sin eliminar la partida.

---

## 4. Matriz de Control de Permisos (RBAC y PIN de Supervisor)

| Operación en Pantalla de Ventas | Permiso Cajero (`CASHIER`) | Requiere Supervisor (`SUPERVISOR`) |
| :--- | :---: | :---: |
| Escanear y añadir partidas | ✅ Permitido | — |
| Modificar cantidades en ticket | ✅ Permitido | — |
| Eliminar producto del ticket activo | ✅ Permitido | ⚠️ Configurable en ajustes |
| Cancelar ticket completo con partidas | ❌ Bloqueado | ✅ Requiere PIN de Supervisor |
| Aplicar descuento hasta límite (ej. 10%) | ✅ Permitido | — |
| Aplicar descuento superior al límite | ❌ Bloqueado | ✅ Requiere PIN de Supervisor |
| Modificar precio unitario por debajo del costo | ❌ Bloqueado | ✅ Requiere PIN de Supervisor |
| Abrir cajón manualmente (`Ctrl+K` / `Cmd+K`) | ✅ Con Auditoría | ⚠️ Notificación en bitácora |
| Autorizar venta con inventario negativo | ❌ Bloqueado | ✅ Requiere PIN de Supervisor |
| Autorizar crédito excedido de cliente | ❌ Bloqueado | ✅ Requiere PIN de Supervisor |

---

## 5. Integración de Hardware Periférico

| Periférico | Protocolo / Interfaz | Comportamiento en Ventas V2 |
| :--- | :--- | :--- |
| **Lector Láser de Código de Barras** | USB Keyboard Wedge | Interceptación de ráfagas en Omnibox sin perder foco; soporte para GS1-128, EAN-13, UPC-A y Code 128. |
| **Báscula Digital** | Serial RS-232 / USB Virtual COM | Detección de peso estable en tiempo real, protocolo Torrey/Rhino, tara automática y captura a 3 decimales. |
| **Impresora Térmica de Tickets** | USB / Red LAN TCP (ESC/POS) | Impresión de tickets de 58 mm y 80 mm con corte automático, código de barras Code 128 al pie y mensaje personalizado. |
| **Cajón de Dinero** | RJ11 conectado a impresora | Disparo de pulso electromagnético (`ESC p 0 25 250`) en cobro en efectivo y en apertura manual auditada. |

---

## 6. Gestor Global de Atajos Multiplataforma (Windows / macOS) y Matriz de Prioridad

### 6.1. Mapeo Oficial de Atajos de Teclado
| Categoría | Acción Operativa | Windows / Linux | macOS |
| :--- | :--- | :--- | :--- |
| **Ventas y Cobro** | Cambiar de venta en progreso (pestañas) | `F5` | `Cmd + 5` |
| **Ventas y Cobro** | Cobrar venta actual (abrir modal cobro) | `F12` | `Cmd + Enter` o `F12` |
| **Ventas y Cobro** | Venta rápida (producto fuera de catálogo) | `F1` o `Ctrl + R` | `Cmd + R` |
| **Ventas y Cobro** | Verificador de precios | `F9` | `Cmd + F9` |
| **Buscador y Ticket** | Enfocar el buscador de productos | `F2` o `Ctrl + F` | `Cmd + F` |
| **Buscador y Ticket** | Navegar resultados del buscador | `↑` / `↓` | `↑` / `↓` |
| **Buscador y Ticket** | Agregar producto seleccionado | `Enter` | `Enter` |
| **Buscador y Ticket** | Cerrar desplegable o cancelar modal | `Esc` | `Esc` |
| **Buscador y Ticket** | Incrementar / reducir cantidad del ítem | `+` / `-` | `+` / `-` |
| **Buscador y Ticket** | Eliminar artículo seleccionado | `Delete` / `Supr` | `Backspace` / `Delete` |
| **Clientes y Caja** | Asignar cliente a la venta | `F11` o `Ctrl + L` | `Cmd + L` |
| **Clientes y Caja** | Aplicar descuento global | `F10` o `Ctrl + D` | `Cmd + D` |
| **Clientes y Caja** | Entrada de efectivo a caja | `F7` | `Cmd + 7` |
| **Clientes y Caja** | Salida / retiro de efectivo | `F8` | `Cmd + 8` |
| **Clientes y Caja** | Abrir cajón de dinero manualmente | `Ctrl + K` o `Alt + C` | `Cmd + K` |

### 6.2. Matriz de Prioridad de Eventos de Teclado
1. **Nivel 1 (Prioridad Máxima - Modales Activos):**
   - Si un modal está abierto (Cobro, Supervisor, Verificador, Cliente): `Esc` cierra el modal, `Tab` cicla inputs internos y `Enter` confirma. Todos los demás atajos globales de fondo se bloquean para evitar acciones secundarias invisibles.
2. **Nivel 2 (Desplegable Omnibox Activo):**
   - Si el dropdown de autocompletado está expandido: `↑` y `↓` navegan exclusivamente los resultados, `Enter` agrega la partida seleccionada y `Esc` repliega el menú devolviendo el foco al texto del Omnibox.
3. **Nivel 3 (Input de Texto en Foco):**
   - Si el cursor reside dentro de un campo de texto (Omnibox o celda de cantidad): Los atajos de carácter único sin modificador (`+`, `-`, `Delete`) se inhiben para permitir la captura literal de cadenas (ej. escribir `coca-cola` o `1+1`). Los atajos de teclas de función (`F1`–`F12`) y modificadores (`Ctrl` / `Cmd`) se procesan con normalidad.
4. **Nivel 4 (Parrilla del Carrito / Vista General):**
   - Sin campos de texto activos: `+` incrementa la partida enfocada, `-` decrementa, `Delete` elimina la partida y los atajos de caja ejecutan sus acciones inmediatas.

