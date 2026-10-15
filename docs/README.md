# NexusPOS — Documentación del Sistema

Bienvenido a la documentación oficial de **NexusPOS**, sistema de punto de venta (POS) de alto rendimiento con arquitectura **LAN-First con Servidor Autoritativo**, optimizado para tiendas de abarrotes, misceláneas y comercios minoristas con operación multicaja en red local.

---

## 1. Estructura de la Documentación

La documentación está organizada en dos niveles principales (Diseño Técnico y Especificación Funcional de Módulos), complementados por los contratos de API y la estrategia de pruebas:

```text
docs/
├── README.md                          # Este documento (visión general, requisitos e inicio rápido)
├── architecture/                      # Nivel 1: Diseño Técnico y Arquitectura General
│   ├── overview.md                    # Capas, topología de red, módulos Gradle y principios
│   ├── technical-decisions.md         # Registro de Decisiones de Arquitectura (ADRs)
│   ├── data-flow.md                   # Flujo de datos, estado del cliente, persistencia y WebSockets
│   └── security.md                    # Autenticación PIN BCrypt, roles y seguridad en LAN
├── features/                          # Nivel 2: Documentación Funcional de Módulos (Plantilla 10 secciones)
│   ├── top-header-navigation.md       # Cabecera global, atajos F1-F6, semáforo LAN y báscula
│   ├── sales.md                       # Venta, carrito, cobro atómico, folio e impresión ESC/POS
│   ├── cash-session.md                # Sesiones de caja, fondo inicial, movimientos y arqueo (RN-CAJ-04)
│   ├── credit-fiado.md                # Crédito/Fiado a clientes, límites, saldo y abonos
│   ├── catalog-inventory.md           # Catálogo, presentaciones, granel/peso y stock negativo configurable
│   └── authentication.md              # Login por PIN, cambio de cajero y estado LAN
├── api/
│   └── endpoints.md                   # Contratos REST /api/v1, DTOs, Idempotency-Key y WebSocket
└── testing/
    └── testing-strategy.md            # Estrategia de pruebas unitarias, integración y hardware
```

---

## 2. Requisitos Previos

- **Java Development Kit (JDK):** Versión 17 o superior (recomendado Eclipse Temurin 21).
- **Docker & Docker Compose:** Para ejecutar la base de datos central PostgreSQL 16.
- **Sistema Operativo:** Linux, macOS o Windows (tanto para el servidor como para las terminales de caja).

---

## 3. Guía de Ejecución Rápida

### Paso 1: Iniciar PostgreSQL
En la raíz del proyecto, levantar el contenedor de base de datos con persistencia en volumen:
```bash
docker compose up -d
```
*Esto iniciará PostgreSQL 16 en el puerto `5432` con usuario `nexuspos` y base de datos `nexuspos`.*

### Paso 2: Iniciar el Servidor Autoritativo de Tienda
```bash
./gradlew :server:run
```
*El servidor arranca en el puerto `8080`, ejecuta automáticamente las migraciones versionadas de **Flyway** y precarga el catálogo inicial mexicano de abarrotes.*

### Paso 3: Iniciar la Terminal de Caja de Escritorio
En la misma PC o en cualquier equipo conectado a la red local (LAN):
```bash
./gradlew :client:desktop:run
```

---

## 4. Atajos de Teclado Rápidos en Mostrador

La terminal de venta está diseñada para ser operada a máxima velocidad sin necesidad de ratón:

### Navegación Global por Módulos
| Atajo | Módulo | Descripción |
| :--- | :--- | :--- |
| **F1** | **Ventas** | Pantalla principal de captura de productos y cobro (activo por defecto). |
| **F2** | **Clientes** | Cuentas corrientes, límites de crédito y abonos de clientes fiados. |
| **F3** | **Productos** | Catálogo general de artículos, precios y códigos de barras. |
| **F4** | **Inventario** | Control de existencias físicas y alertas de bajo stock. |
| **F5** | **Configuración** | Parámetros del negocio, impresoras y periféricos. |
| **F6** | **Cortes** | Arqueo de caja, desglose de efectivo y corte Z/X. |

### Operación en Punto de Venta
| Atajo | Acción | Contexto |
| :--- | :--- | :--- |
| **Enter** | Agregar código de barras escaneado o tecleado al ticket. | Módulo de Ventas |
| **F12** | Abrir diálogo de cobro en efectivo con cálculo de cambio. | Con carrito y turno activo |
| **Esc** | Cerrar modales abiertos o regresar a **[F1] Ventas** / Salir. | Contextual en toda la app |

---

## 5. Verificación de Pruebas

Para ejecutar la suite de pruebas completa en todos los módulos:
```bash
./gradlew :shared:core:jvmTest :shared:contracts:jvmTest :server:test :client:app:jvmJar :client:desktop:test
```
