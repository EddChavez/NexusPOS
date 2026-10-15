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
│   ├── security.md                    # Autenticación PIN BCrypt, roles y seguridad en LAN
│   └── funcionalidades-por-fases.md   # Hoja de ruta por fases (Fase 0 a Fase 4)
├── features/                          # Nivel 2: Documentación Funcional de Módulos (Plantilla 10 secciones)
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

## 4. Atajos de Teclado Rápidos en Caja

La terminal de venta está diseñada para ser operada a máxima velocidad sin necesidad de ratón:

| Atajo | Acción |
| :--- | :--- |
| **Enter** | Agregar código de barras escaneado o tecleado al carrito. |
| **F2** | Búsqueda rápida de productos por nombre o descripción. |
| **F4** | Seleccionar cliente para venta a crédito / fiado. |
| **F12** | Abrir diálogo de cobro en efectivo con cálculo de cambio. |
| **Esc** | Cerrar diálogos modales o cancelar selección. |

---

## 5. Verificación de Pruebas

Para ejecutar la suite de pruebas completa en todos los módulos:
```bash
./gradlew :shared:core:jvmTest :shared:contracts:jvmTest :server:test :client:app:jvmJar :client:desktop:test
```
