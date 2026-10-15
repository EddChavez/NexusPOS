# Registro de Decisiones de Arquitectura (ADR)

Este documento recopila las decisiones arquitectónicas fundamentales adoptadas en NexusPOS, su contexto, las alternativas evaluadas y sus justificaciones técnicas.

---

## ADR-001: Monolito Modular con Ktor y Kotlin Multiplatform

- **Estado:** Aceptado
- **Contexto:** Se requiere desarrollar un sistema POS robusto que soporte lógica compartida entre el servidor local, terminales de escritorio y futuras terminales móviles Android, sin complejidad operativa excesiva.
- **Decisión:** Implementar un backend como monolito modular sobre Ktor 3.1.0 (Kotlin/JVM) y módulos compartidos mediante Kotlin Multiplatform (KMP).
- **Justificación:**
  - Ktor provee un servidor asíncrono, ultraligero y con bajo consumo de memoria RAM (ideal para ejecutarse en la misma PC que atiende caja).
  - KMP permite compartir contratos, DTOs de serialización y modelos matemáticos de dinero entre servidor y clientes sin duplicar código ni crear inconsistencias entre lenguajes.
- **Consecuencias:** No se requiere infraestructura de microservicios ni herramientas de orquestación (Kubernetes). El despliegue es un único proceso JVM.

---

## ADR-002: Pivote a LAN-First con Servidor Autoritativo (vs. SQLite Offline-First Distribuido)

- **Estado:** Aceptado (Sustituye al prototipo v1)
- **Contexto:** El diseño inicial contemplaba una base de datos SQLite en cada caja con un motor de sincronización distribuida (`SyncEngine`) por eventos. En un entorno de abarrotes de una sola tienda física, esto provocaba:
  1. Conflictos de concurrencia al vender las últimas piezas de un producto entre dos cajas.
  2. Complejidad innecesaria en la conciliación de saldos de fiado/crédito.
  3. Desfases en el arqueo de efectivo y cortes de turno entre terminales.
- **Decisión:** Adoptar una arquitectura **LAN-First con Servidor Autoritativo**. Las cajas no replican base de datos local ni eventos; mantienen el carrito de venta actual en memoria RAM y consumen la API centralizada. Si el servidor no responde en la LAN, la terminal pasa al estado `Sin servidor` y **bloquea el cobro**, manteniendo los productos del carrito a salvo en pantalla.
- **Justificación:**
  - Garantiza **consistencia inmediata (ACID)** de inventario, crédito y folios fiscales/internos.
  - Elimina de raíz algoritmos de resolución de conflictos y replicación eventual en red local.
- **Consecuencias:** Si la PC principal o la red cableada caen, las cajas no pueden cobrar hasta que el servidor esté activo. Esto se mitiga con respaldo de energía (UPS) y el aislamiento del proceso del servidor como servicio de sistema.

---

## ADR-003: Persistencia Central Directa en PostgreSQL 16 con HikariCP y Flyway

- **Estado:** Aceptado
- **Contexto:** Se evaluó si convenía iniciar con SQLite central en el servidor y migrar a PostgreSQL en fases posteriores.
- **Decisión:** Implementar directamente **PostgreSQL 16**, gestionado mediante un contenedor local (`docker-compose.yml`), con pool de conexiones de alto rendimiento **HikariCP** y control de esquema versionado mediante **Flyway**.
- **Justificación:**
  - PostgreSQL soporta bloqueos a nivel de fila (`SELECT ... FOR UPDATE`), transacciones concurrentes reales bajo nivel de aislamiento `READ COMMITTED` y foliadores numéricos seguros (`counters`).
  - SQLite en concurrencia multicaja con escrituras intensivas puede presentar bloqueos de archivo (`database is locked`).
  - Flyway ejecuta las migraciones DDL/DML automáticamente al arrancar el servidor, garantizando que todos los ambientes estén sincronizados.
- **Consecuencias:** Requiere la presencia de PostgreSQL (vía Docker o servicio nativo) en la PC principal de la tienda.

---

## ADR-004: Precisión Financiera con Value Classes `Money` y `Quantity`

- **Estado:** Aceptado
- **Contexto:** El uso del tipo estándar `Double` (punto flotante IEEE 754) en sistemas de cobro introduce errores residuales binarios (`0.1 + 0.2 = 0.30000000000000004`). Tras miles de transacciones diarias, estos residuos descuadran centavos en cortes de caja, auditorías e impuestos.
- **Decisión:** Modelar el dinero y las cantidades exclusivamente mediante `@JvmInline value class`:
  - [`Money(val cents: Long)`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/shared/core/src/commonMain/kotlin/mx/nexuspos/core/Money.kt): Dinero almacenado en centavos enteros.
  - [`Quantity(val millis: Long)`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/shared/core/src/commonMain/kotlin/mx/nexuspos/core/Quantity.kt): Cantidades almacenadas en milésimas (`1000L = 1 pza`, `250L = 0.250 kg / 250 g`).
  - [`PricingRules`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/shared/core/src/commonMain/kotlin/mx/nexuspos/core/PricingRules.kt): Cálculos con redondeo canónico *Round Half-Up* al centavo más próximo.
- **Justificación:**
  - En tiempo de compilación provee tipado fuerte, evitando sumar cantidades con dinero accidentalmente.
  - En tiempo de ejecución se compila a un primitivo nativo `long`, con **cero sobrecarga de memoria** (sin instanciación de objetos).
  - Matemáticamente exacto, sin pérdida de centavos.

---

## ADR-005: Seguridad con Hashing BCrypt para PINs y SHA-256 para Dispositivos

- **Estado:** Aceptado
- **Contexto:** Los cajeros y supervisores utilizan PINs numéricos de 4 dígitos para agilidad en caja. Un PIN de 4 dígitos tiene solo 10,000 combinaciones posibles (`0000` a `9999`), haciéndolo extremadamente vulnerable a ataques de fuerza bruta si se almacena en texto plano o con funciones rápidas como MD5 o SHA-256 plano.
- **Decisión:**
  - Hashear los PINs con **BCrypt** (`org.mindrot:jbcrypt`) con factor de costo 10 y salt dinámico.
  - Hashear los tokens de vinculación de terminales con **SHA-256**.
- **Justificación:**
  - BCrypt es una función deliberadamente lenta que neutraliza ataques de diccionario y tablas rainbow en caso de extracción de respaldos.
  - Se autoriza al supervisor en operaciones sensibles solicitando su PIN al momento.

---

## ADR-006: Política de Inventario Negativo Configurable (`allow_negative_stock`)

- **Estado:** Aceptado
- **Contexto:** En el comercio tradicional de abarrotes, los proveedores entregan mercancía (ej. refrescos, lácteos) que se pone a la venta de inmediato antes de que el dueño tenga tiempo de capturar la factura en el sistema. Si el POS prohíbe terminantemente vender sin existencias registradas, detiene la venta en mostrador y genera fricción con el cliente final.
- **Decisión:** Implementar la política de inventario negativo como una configuración de negocio administrable (`allow_negative_stock: Boolean` en la tabla `settings`).
  - Si está en `true` (por defecto): La venta descuenta el inventario llevándolo a valores negativos si es necesario, sin bloquear el cobro.
  - Si está en `false`: El servidor valida la existencia antes de confirmar la venta; si es insuficiente, rechaza la transacción con código `422 INSUFFICIENT_STOCK`.
- **Justificación:** Da total soberanía al dueño de la tienda para elegir entre flexibilidad operativa o control estricto de mermas.

---

## ADR-007: Compose Multiplatform y Adaptadores de Hardware Desacoplados

- **Estado:** Aceptado
- **Contexto:** El cajero debe poder operar con un lector de código de barras USB y una impresora térmica de tickets, manteniendo la posibilidad de ejecutar pruebas automáticas e interfaces reactivas modernas.
- **Decisión:**
  - Utilizar **Compose Multiplatform** para una UI declarativa rápida.
  - Implementar adaptadores físicos desacoplados:
    - [`KeyboardWedgeBarcodeInput`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/desktop/src/main/kotlin/mx/nexuspos/desktop/hardware/KeyboardWedgeBarcodeInput.kt): Discrimina ráfagas de escáner USB por latencia inter-tecla (< 80 ms).
    - [`EscPosReceiptPrinter`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/client/desktop/src/main/kotlin/mx/nexuspos/desktop/hardware/EscPosReceiptPrinter.kt): Formatea comandos binarios ESC/POS y tickets en texto puro para previsualización.
- **Justificación:** Permite el desarrollo, depuración y pruebas automatizadas en cualquier máquina de desarrollo sin requerir hardware físico conectado.
