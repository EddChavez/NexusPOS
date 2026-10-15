# NexusPOS — Funcionalidades por fases

**Versión:** 2.0 (borrador de trabajo) · **Fecha:** 14 de octubre de 2026

Este documento ordena **qué se construye y cuándo**. Los detalles de comportamiento están en `02-diseno-funcional.md` y la estructura técnica en `01-arquitectura.md`.

Las fases son una **priorización, no una obligación**: cada fase termina cuando se cumplen sus criterios de salida, y lo que no hace falta en la tienda no se construye.

Referencias: `F1-07` identifica una funcionalidad; `RN-VEN-01` una regla de negocio; **Dep.** indica de qué depende.

---

## Resumen

| Fase | Meta | Resultado |
| --- | --- | --- |
| **0. Fundaciones** | Base técnica verificada | Repositorio, servidor mínimo, persistencia probada y reglas de código automatizadas |
| **1. MVP operativo** | Operar la tienda con varias cajas | Vender, fiar, cobrar, cerrar caja y saber cuánto se ganó, con respaldos |
| **2. Eficiencia** | Ahorrar tiempo y errores | Báscula, compras, precios masivos, supervisor móvil |
| **3. Crecimiento** | Capacidades de negocio adicionales | Caducidades, recargas, contingencia, kardex, compras formales |
| **4. Condicional** | Solo si la tienda lo necesita | Facturación, terminales bancarias, otras monedas y pagos |

---

## Fase 0 — Fundaciones técnicas

| ID | Funcionalidad | Notas |
| --- | --- | --- |
| F0-01 | Repositorio Gradle, catálogo de versiones y CI básica | Sin versiones dinámicas |
| F0-02 | Spike de persistencia: SQLite (WAL) + Exposed + Flyway | Incluye prueba con dos escritores concurrentes. Decide el ADR-003 |
| F0-03 | Servidor Ktor con `health`, `system/info`, configuración externa y errores tipados | |
| F0-04 | `Money`, `Quantity`, `Unit` y `PricingRules` con pruebas | Base de todo cálculo |
| F0-05 | Pruebas de arquitectura (reglas de dependencia) | Por ejemplo con Konsist |

**Criterio de salida:** el servidor arranca, migra una base vacía y pasa pruebas de concurrencia; las reglas de dependencia se verifican automáticamente.

---

## Fase 1 — MVP operativo

### Hitos

| Hito | Contenido | Resultado visible |
| --- | --- | --- |
| **M1** | Emparejamiento, PIN, catálogo, búsqueda, primera venta en efectivo | Una caja vende contra el servidor |
| **M2** | Inventario, sesiones de caja, entradas/salidas, corte | Se puede abrir y cerrar turno con diferencia calculada |
| **M3** | Clientes y fiado | Se vende a crédito y se cobran abonos |
| **M4** | Cancelaciones, devoluciones, ticket y reimpresión | Se corrigen errores con trazabilidad |
| **M5** | Reportes, eventos en vivo, instalador, respaldos | La tienda puede operar y recuperarse |

### Funcionalidades

| ID | Funcionalidad | Hito | Dep. |
| --- | --- | --- | --- |
| F1-01 | Catálogo: productos, departamentos, unidad base, costo, impuesto, mínimo y máximo | M1 | F0-04 |
| F1-02 | Presentaciones con código de barras y factor (pieza, paquete) | M1 | F1-01 |
| F1-03 | Búsqueda por código y por nombre | M1 | F1-01 |
| F1-04 | Pantalla de venta con operación por teclado y carrito en el cliente | M1 | F1-03 |
| F1-05 | Artículo común con precio libre | M1 | F1-04 |
| F1-06 | Venta a granel con peso manual y hasta tres decimales | M1 | F1-04 |
| F1-07 | Pagos: efectivo con cambio, tarjeta registrada y transferencia | M1 | F1-04 |
| F1-08 | Cobro transaccional e idempotente con folio consecutivo | M1 | F1-07 |
| F1-09 | Inventario: existencias, entradas, ajustes con motivo, mínimos y alertas | M2 | F1-01 |
| F1-10 | Sesiones de caja, entradas y salidas, vista previa y cierre del corte | M2 | F1-08 |
| F1-11 | Clientes y crédito: límite, cargo, abono, liquidación, saldo y movimientos | M3 | F1-08 |
| F1-12 | Cancelación y devolución con movimientos inversos | M4 | F1-08, F1-09, F1-11 |
| F1-13 | Ticket ESC/POS de 58 u 80 mm y reimpresión con marca COPIA | M4 | F1-08 |
| F1-14 | Usuarios con PIN, roles, permisos y autorización puntual de encargado | M1 | |
| F1-15 | Emparejamiento por código, token de dispositivo y revocación | M1 | |
| F1-16 | Descuento manual con permiso y tope por rol | M4 | F1-04, F1-14 |
| F1-17 | Reportes: ventas del día, ganancia, más vendidos, bajo mínimo, corte, movimientos de inventario | M5 | F1-08, F1-09 |
| F1-18 | Eventos en vivo por WebSocket y descarga incremental del catálogo | M5 | F1-01 |
| F1-19 | Auditoría de operaciones sensibles | M4 | F1-14 |
| F1-20 | Carga inicial del catálogo desde CSV | M1 | F1-01 |
| F1-21 | Instalador de la PC principal, servicio con arranque automático y diagnóstico del servidor | M5 | F0-03 |
| F1-22 | Respaldo automático diario, verificación y guía de restauración probada | M5 | F1-21 |

> **Ajuste respecto a la versión anterior:** la carga inicial del catálogo (F1-20) y el respaldo automático (F1-22) pasan al MVP. Capturar cientos de productos a mano retrasa el arranque, y con una sola PC como servidor el respaldo no puede esperar.

### Criterios de salida

- Los 13 escenarios de aceptación de `02-diseno-funcional.md` (sección 11) pasan, de forma automatizada cuando es posible.
- Prueba de concurrencia aprobada: dos cajas vendiendo el mismo producto y reintentos con la misma clave.
- Un respaldo se restaura en una máquina limpia siguiendo la guía.
- **Operación en paralelo** con el método actual de la tienda durante un periodo acordado (propuesta: una semana), con corte comparado cada día.
- Sin errores abiertos de dinero, inventario o crédito.

---

## Fase 2 — Eficiencia

| ID | Funcionalidad | Notas |
| --- | --- | --- |
| F2-01 | Báscula conectada (USB/serie) | La lectura se confirma; el modo manual siempre existe |
| F2-02 | Ventas en espera | Atender a varios clientes a la vez; el carrito se guarda en la caja |
| F2-03 | Precios de mayoreo | Listas de precio por producto |
| F2-04 | Promociones por rango de cantidad | Ejemplo: de 3 a 5 piezas, otro precio |
| F2-05 | Pagos mixtos | Incluye fiar solo una parte |
| F2-06 | Estado de cuenta imprimible y reporte de saldos de crédito | |
| F2-07 | Proveedores, compras sugeridas según mínimos y recepción de mercancía | La recepción genera entradas de inventario |
| F2-08 | Actualización masiva de precios | Por porcentaje, departamento o proveedor; con vista previa |
| F2-09 | Plantillas y exportación a Excel | Complementa la carga CSV del MVP |
| F2-10 | Verificador de precios | Consulta rápida sin vender |
| F2-11 | Historial de cortes y reportes adicionales | Ventas por cliente, por cajero y por periodo |
| F2-12 | App móvil de supervisión (Android) | Ventas, inventario y alertas dentro de la LAN; conteo de inventario con la cámara (opcional) |
| F2-13 | Segundo destino de respaldo y verificación periódica | Disco externo y, si se desea, otra carpeta o equipo |

**Criterio de salida:** cada funcionalidad probada en la tienda por un periodo de uso real; reportes cuadran con el corte; la báscula no bloquea la venta cuando falla.

---

## Fase 3 — Crecimiento

| ID | Funcionalidad | Notas |
| --- | --- | --- |
| F3-01 | Caducidades por lote o fecha | Alertas en lácteos, pan y botanas |
| F3-02 | Recargas y pago de servicios | Mediante proveedor externo; requiere internet |
| F3-03 | **Modo de contingencia** | Ventas solo en efectivo en una cola local si no hay servidor; sin fiado ni validación de existencias; requiere ADR-009 |
| F3-04 | Kardex | Historial detallado de movimientos por producto |
| F3-05 | Órdenes de compra | Enviar al proveedor, recibir, cancelar |
| F3-06 | Productos compuestos (kits) | Descuentan sus componentes |
| F3-07 | Catálogo imprimible y etiquetas | |
| F3-08 | Notificaciones por correo | Cortes y alertas; requiere internet |
| F3-09 | Nube, acceso remoto y multi-sucursal | Requiere diseño explícito de sincronización; no se deriva de lo anterior |
| F3-10 | Migración a PostgreSQL | Solo si se cumplen las condiciones del ADR-003 |

**Criterio de salida:** cada elemento se activa por necesidad demostrada, con su propio ADR cuando cambie la arquitectura.

---

## Fase 4 — Solo si existe la necesidad

| ID | Funcionalidad | Notas |
| --- | --- | --- |
| F4-01 | Facturación electrónica CFDI 4.0 mediante un PAC | Facturar ventas y tickets, facturas globales, cancelación y sustitución. Integrar por API; no implementar el timbrado |
| F4-02 | Integración con terminales bancarias | Cobro y cancelación desde el sistema |
| F4-03 | Dólares, vales y cheques | |
| F4-04 | Tarjetas virtuales | |
| F4-05 | App para iOS | La base multiplataforma lo permite |

---

## Cobertura frente a eleventa

Cómo se traduce cada capítulo del manual a NexusPOS.

| Capítulo de eleventa | En NexusPOS | Dónde |
| --- | --- | --- |
| Empezando (instalación, licencia) | Instalador propio y emparejamiento; sin licencia | F1-15, F1-21 |
| Ventas (productos comunes, granel, descuentos, entradas y salidas, devoluciones) | Se adopta | F1-04 a F1-08, F1-12, F1-16 |
| Ventas: varios clientes a la vez | Se adopta más tarde | F2-02 |
| Ventas: verificador de precios | Se adopta más tarde | F2-10 |
| Venta por paquete o unidad | Se adopta mediante presentaciones con factor | F1-02 |
| Formas de pago: efectivo, tarjeta, transferencia, crédito | Se adopta | F1-07, F1-11 |
| Formas de pago: mixto | Más tarde | F2-05 |
| Formas de pago: dólares, vales, cheque | Condicional | F4-03 |
| Terminales Mercado Pago y Prosepago | Condicional | F4-02 |
| Crédito a clientes | Se adopta; estado de cuenta más tarde | F1-11, F2-06 |
| Clientes | Se adopta; datos de facturación solo si hay CFDI | F1-11, F4-01 |
| Productos: alta, búsqueda, departamentos | Se adopta | F1-01 a F1-03 |
| Productos: promociones | Más tarde | F2-04 |
| Productos: kits | Crecimiento | F3-06 |
| Productos: importar y exportar | CSV en el MVP; Excel después | F1-20, F2-09 |
| Productos: catálogo imprimible | Crecimiento | F3-07 |
| Actualizador masivo | Más tarde | F2-08 |
| Inventarios: control, ajustes, bajo mínimo, reportes | Se adopta | F1-09, F1-17 |
| Inventarios: kardex | Crecimiento | F3-04 |
| Compras y proveedores | Más tarde; órdenes formales en Crecimiento | F2-07, F3-05 |
| Facturación electrónica | Condicional | F4-01 |
| Cortes y turnos | Se adopta; historial después | F1-10, F2-11 |
| Corte por correo | Crecimiento | F3-08 |
| Reportes | Básicos en el MVP | F1-17, F2-11 |
| Configuración (cajeros, ticket, impresora, impuestos, respaldo) | Se adopta lo esencial | F1-14, F1-13, F1-22 |
| Báscula | Más tarde | F2-01 |
| Nube y pulso eleventa | Móvil por LAN primero; nube después | F2-12, F3-09 |
| Recargas y pago de servicios | Crecimiento | F3-02 |
| Tarjetas virtuales | Condicional | F4-04 |
| Opciones adicionales y compra de licencia | No aplica: sistema propio | |

---

## Riesgos y cómo se tratan

| Riesgo | Mitigación |
| --- | --- |
| Servidor único como punto de falla | UPS, arranque automático, respaldo diario probado, diagnóstico visible; contingencia en la Fase 3 si hace falta |
| Impresoras y básculas con comportamientos distintos | Adaptadores, adaptadores falsos para pruebas y validación con el modelo real al inicio de cada fase |
| Alcance que crece antes de que el MVP funcione | Los criterios de salida de cada fase son la puerta; no se empieza la siguiente sin cumplirlos |
| Reglas fiscales y de redondeo sin definir | Se resuelven en F0 y se documentan en un ADR antes de F1-08 |
| Carga inicial de datos lenta | Importación CSV en el MVP (F1-20) |
| Pérdida de datos | Respaldo diario en el MVP, segundo destino en la Fase 2, restauración ensayada |
| Dependencia de una sola persona | Documentación, ADR y pruebas automatizadas desde el inicio |

## Decisiones que bloquean el inicio de la Fase 1

Deben resolverse antes de F1-08 (ver la lista completa en `01-arquitectura.md`, sección 17):

1. Base de datos (SQLite o PostgreSQL) según el spike de F0-02.
2. Reglas de impuestos, precios con impuesto incluido y redondeo.
3. Política de existencias negativas.
4. Modelo de impresora y sistema operativo de las cajas.
5. Tiempo de expiración de sesión y reglas de PIN.
