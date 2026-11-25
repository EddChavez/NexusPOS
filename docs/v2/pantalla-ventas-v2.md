# Pantalla de Ventas V2 — NexusPOS

**Documento:** NexusPOS-DOC-V2-SALES-FEATURE  
**Versión:** 2.0.0 (Compose Multiplatform Desktop)  
**Estado:** Producción / Estable  
**Estándar UI:** Material Design 3 Puro (Material You)  
**Compatibilidad:** 100% retrocompatible con el modelo de datos existente y convivencia pacífica con V1 (`salesUiVersion = "V2"` / `"V1"`).

---

## 1. Visión General del Feature

La **Pantalla Principal de Ventas V2** es una reingeniería completa de la interfaz de punto de venta (POS) para comercios minoristas y tiendas de abarrotes de alto rendimiento, diseñada para operar a la velocidad del cajero mediante el teclado sin necesidad obligatoria del ratón.

Esta versión implementa una arquitectura visual en 3 columnas ergonómicas, estandarizada estrictamente bajo las especificaciones oficiales de **Material Design 3 (M3)** mediante Jetpack Compose Multiplatform, recuperando e integrando las **7 funcionalidades clave de la versión V1** bajo un diseño limpio, moderno y accesible.

---

## 2. Arquitectura Visual (3 Columnas M3)

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
| [U] | 3 artículos / 3.500 unid. [ 📝 ] [ 🖨️ ] [ COBRAR $ ]                  |
+-----+-----------------------------------------------+------------------------+
| [Toast]: ✓ Cajón de dinero abierto (Registrado en bitácora - Cajero: J.V.)   |
| BARRA ESTADO: Nexus Lan Server  |  ● Conectado      | [🔄 N offline] | Reloj |
+------------------------------------------------------------------------------+
```

### Distribución de Espacios:
1. **Columna izquierda.** La barra Material 3 de 64 dp muestra solo iconos, con el nombre de cada acción al pasar el cursor. Los módulos de operación, incluida **Transacciones**, están arriba. **Ajustes** y **Bloquear** quedan al pie, debajo de un separador.
2. **Columna Central (Flexible, `weight(1f)`):** 
   - Barra superior de pestañas multi-ticket con `FilterChip`, botón de nuevo ticket `FilledTonalIconButton` y badge de ventas en red `AssistChip`.
   - Buscador Omnibox con autocompletado flotante y compatibilidad con escáner láser.
   - Carrito virtualizado (`LazyColumn`) con tarjetas `OutlinedCard`, steppers de cantidad, pesaje serial RS-232 y menú contextual.
   - Franja inferior de cobro con botón masivo `[COBRAR F12 / ⌘↵]`, notas `[📝]` y reimpresión rápida `[🖨️]`.
3. **Columna Derecha (340 dp):**
   - Tarjeta `ElevatedCard` del producto en contexto con selector de tarifas `[Precio ▾]` (Público, Mayoreo, Especial) y consulta de catálogo.
   - Cuadrícula 2×4 de Acciones Rápidas con insignias visuales dinámicas `<kbd>`.
4. **Pie de Telemetría (28 dp):** Estado de servidor LAN, badge reactivo de contingencia offline `[🔄 N offline]`, estado de báscula y reloj en tiempo real.

---

## 3. Lista Detallada de Características Funcionales

### 3.1. Gestión de Ventas Multi-Ticket
- **Pestañas Paralelas:** Apertura simultánea de ventas (`TicketState`) sin perder las partidas de las otras ventas.
- **Renombrado Personalizado:** Doble clic o atajo para nombrar la pestaña (ej. "Pedido María", "Mesa 4", "Cliente Mostrador").
- **Observaciones del Ticket:** Botón `[📝 Nota]` para registrar instrucciones de entrega, dirección o notas de preparación.
- **Sincronización:** Persistencia de notas, clientes y totales independientes por cada pestaña.

### 3.2. Buscador Omnibox & Escaneo Continuo
- **Lector Láser Continuo:** Detección automática de códigos de barras (EAN-13, CODE-128, UPC) desde escáner USB/Bluetooth con adición directa al carrito en $<50$ ms.
- **Autocompletado Predictivo:** Menú flotante al escribir más de 2 caracteres que muestra miniatura, nombre, código, stock disponible (badge) y precio unitario.
- **Navegación por Teclado:** Selección con flechas `↑` `↓` y adición al ticket con `Enter`.

### 3.3. Carrito de Compras Ergonómico
- **Miniaturas Visuales:** Generación automática de avatares con colores y pictogramas por categoría (Carnicería, Bebidas, Lácteos, Granel, etc.).
- **Stepper de Cantidad Inteligente:**
  - Botones `-` y `+` con incrementos de $1.000$ unidad para piezas o $0.100$ kg para productos a granel.
  - Edición directa de cantidad con validación numérica decimal.
- **Acceso Directo a Báscula RS-232:** Botón `[⚖]` para re-pesar la línea directamente desde la báscula serial.
- **Menú Contextual `[⋮]`:** Acciones rápidas para eliminar artículo (`Supr`), aplicar descuento por línea o editar precio unitario.

### 3.4. Tarjeta de Contexto y Selector de Escalas de Precio
- **Foco Automático:** Muestra en tiempo real el último producto escaneado o seleccionado en el carrito.
- **Selector de Precio `[Precio ▾]`:** Permite conmutar la partida entre:
  - *Precio 1 (Público general).*
  - *Precio 2 (Mayoreo / 10% de descuento).*
  - *Precio 3 (Especial / 20% de descuento).*
- **Consulta de Ficha:** Acceso directo a la ficha del producto en catálogo para consultar existencias, proveedores y margen comercial.

### 3.5. Cuadrícula de 8 Acciones Rápidas
| Botón | Atajo Windows | Atajo macOS | Función Operativa |
| :--- | :--- | :--- | :--- |
| **Venta Rápida** | `F1` / `Ctrl+R` | `Cmd+R` | Agrega un producto eventual o fuera de catálogo |
| **Verif. Precios** | `F9` | `Cmd+F9` | Modal para consultar precio y stock sin alterar el ticket |
| **Entrada Efectivo** | `F7` | `Cmd+7` | Registro de ingreso de efectivo a la caja |
| **Salida Efectivo** | `F8` | `Cmd+8` | Registro de retiro/gasto de efectivo de la caja |
| **Asignar Cliente** | `F11` / `Ctrl+L` | `Cmd+L` | Búsqueda y vinculación de cliente con saldo de crédito y monedero |
| **Desc. Global** | `F10` | `Cmd+D` | Aplica descuento porcentual o monto fijo a todo el ticket |
| **WhatsApp** | `Alt+W` | `⌥W` | Envío digital del comprobante de venta por WhatsApp |
| **Abrir Cajón** | `Ctrl+K` / `Alt+C` | `Cmd+K` | Apertura manual del cajón con registro de auditoría |

---

## 4. Recuperación Exclusiva de las 7 Funcionalidades V1

### 4.1. Reimpresión de Último Ticket (`[🖨️]`)
- Ubicado en la barra de cobro junto a `[📝 Nota]`.
- Atajo de teclado directo: `Ctrl+P` / `Cmd+P` o acceso en barra.
- Abre la vista previa del recibo con opción de re-emisión física en impresora térmica ESC/POS.

### 4.2. Ventas Archivadas en Red (Parked Sales)
- Badge interactivo `[📥 N en espera]` ubicado junto al botón `[+]` de pestañas.
- Visible dinámicamente cuando existen ventas pausadas en la base de datos central PostgreSQL.
- Permite pausar una venta en cualquier caja de la red local y reanudarla en otra terminal.

### 4.3. Re-pesaje mediante Báscula RS-232
- Icono `[⚖]` integrado en cada línea de producto a granel dentro del carrito.
- Lectura en tiempo real del peso estable desde el puerto serial (protocolo Torrey/Rhino).
- No destructivo: si la báscula está en cero o inestable, no altera la cantidad previa capturada.

### 4.4. Monedero Electrónico y Crédito de Cliente
- El botón **Asignar Cliente** muestra directamente el nombre y el saldo real del monedero:
  `Juan Pérez · 💰 $350.00 Mon.`
- En el flujo de cobro dividido (Split Payment), se valida el límite de crédito disponible (`availableCreditCents`) y el saldo acumulado de lealtad para pago directo.

### 4.5. Cancelación Formal de Ticket y Auditoría
- Al pulsar `(x)` en una pestaña con partidas, se dispara el modal de confirmación formal `CancelTicketPromptDialog`.
- Exige la selección de un motivo de cancelación (error de captura, cliente desistió, falta de fondos, etc.).
- Si el cajero activo tiene rol restringido (`CASHIER`), exige elevación de privilegios mediante PIN de supervisor.
- Genera un toast de auditoría operativo en pantalla: `✓ Venta cancelada (Auditado - Motivo: ...)`.

### 4.6. Modo Contingencia Offline Reactivo
- En caso de caída del servidor central LAN, la aplicación entra automáticamente en modo offline.
- El pie de estado (`SystemFooter`) muestra de forma reactiva el chip `[🔄 N offline]` **exclusivamente cuando existen ventas pendientes de sincronizar ($N > 0$)**.
- Las ventas offline se numeran temporalmente como `OFF-XXXX` y se sincronizan atómicamente al restaurarse la red.

### 4.7. Transacciones

- Abre **Transacciones** desde la barra lateral o con `Ctrl+H` / `Cmd+H` desde Ventas V2.
- La barra de consulta reúne la búsqueda por folio, monto o contenido, el periodo y la caja. La vista abre en **Hoy**. Los filtros de tipo permanecen visibles y muestran ventas, entradas, salidas, fondo de caja y abonos de crédito según los permisos del usuario.
- Selecciona una venta para consultar su ticket y, según los permisos y el estado del turno, reimprimirlo, compartirlo por WhatsApp, devolver artículos o cancelar la venta.
- La consulta de operaciones de otros usuarios y de turnos cerrados requiere sus permisos respectivos.

---

## 5. Gestor Global de Atajos de Teclado Multiplataforma

El sistema cuenta con un motor de eventos de teclado de 4 capas de prioridad gestionado por `PosShortcutManager`:
1. **Capa 1 (Modales y Diálogos Activos):** `Esc` descarta el modal, `Enter` confirma la acción principal.
2. **Capa 2 (Buscador Omnibox en Foco):** `Enter` agrega el producto sugerido, `↑`/`↓` navega los resultados.
3. **Capa 3 (Navegación del Carrito):** `+`/`-` modifican cantidades de la línea activa, `Supr`/`Backspace` eliminan la partida.
4. **Capa 4 (Acciones Globales):** `F12`/`Cmd+Enter` abre el cobro, `F1`-`F11` ejecutan acciones rápidas.

### Detección Dinámica de SO y Componente `<kbd>`
El componente `ShortcutBadge` detecta en tiempo de ejecución el sistema operativo anfitrión (`HostOsDetector`):
- En **Windows / Linux**: Renderiza teclas de función `F12`, `F5`, `Ctrl+...`.
- En **macOS**: Renderiza símbolos estándar `⌘↵`, `⌘5`, `⌘L`, `⌥W`.

---

## 6. Estandarización Material Design 3 Puro

Todos los componentes de la interfaz utilizan exclusivamente la biblioteca oficial de Compose Material 3 (`androidx.compose.material3`):
- **Contenedores y Paneles:** `NavigationRail`, `ElevatedCard`, `OutlinedCard`.
- **Botones e Iconos:** `FilledTonalButton`, `FilledTonalIconButton`, `IconButton`.
- **Chips y Badges:** `FilterChip`, `AssistChip`.
- **Modales:** `AlertDialog` con slots estándar (`icon`, `title`, `text`, `confirmButton`, `dismissButton`).
- **Mensajería:** `Snackbar` y `SnackbarHost` con tokens de color de `MaterialTheme.colorScheme`.

---

## 7. Verificación y Pruebas Unitarias

La suite de pruebas en `SalesV2FeaturesTest.kt` valida al 100%:
1. `testMultiTicketNoteAndRename`: Persistencia de notas y títulos al alternar pestañas.
2. `testUpdateCartLinePriceTier`: Recálculo aritmético de totales al aplicar escalas de precio.
3. `testRecordManualDrawerOpenGeneratesAuditToast`: Emisión de eventos de auditoría al abrir cajón.
4. `testPosActionShortcutLabelsByOs`: Adaptación de atajos entre Windows y macOS.
5. `testCancelTicketWithItemsRequiresFormalReason`: Flujo de cancelación formal con partidas.
6. `testReweighCartLineWithScaleReading`: Actualización no destructiva desde báscula RS-232.
7. `testCustomerSelectionWithLoyaltyBalance`: Vinculación de cliente y exposición de saldo de monedero.
