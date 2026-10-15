# NexusPOS

Sistema de Punto de Venta (POS) **LAN-First con Servidor Autoritativo**, optimizado para tiendas de abarrotes, misceláneas y comercio minorista multicaja en red local.

---

## 🚀 Inicio Rápido

### 1. Iniciar Base de Datos (PostgreSQL 16)
```bash
docker compose up -d
```

### 2. Iniciar Servidor Central de Tienda
```bash
./gradlew :server:run
```
*Inicia en `http://localhost:8080`, ejecuta migraciones Flyway y precarga el catálogo de abarrotes.*

### 3. Iniciar Terminal de Caja de Escritorio
```bash
./gradlew :client:desktop:run
```

---

## 📚 Documentación

La documentación completa del sistema se encuentra organizada en la carpeta [`docs/`](./docs):

- **[Índice Maestro de Documentación](./docs/README.md)**
- **Nivel 1: Arquitectura y Diseño Técnico**
  - [Visión General y Topología de Red](./docs/architecture/overview.md)
  - [Registro de Decisiones de Arquitectura (ADRs)](./docs/architecture/technical-decisions.md)
  - [Flujo de Datos y Concurrencia](./docs/architecture/data-flow.md)
  - [Seguridad y Control de Acceso](./docs/architecture/security.md)
  - [Hoja de Ruta por Fases](./docs/architecture/funcionalidades-por-fases.md)
- **Nivel 2: Documentación Funcional de Módulos**
  - [Ventas y Cobro en Caja](./docs/features/sales.md)
  - [Sesiones y Cortes de Caja (Arqueo RN-CAJ-04)](./docs/features/cash-session.md)
  - [Crédito y Fiado a Clientes](./docs/features/credit-fiado.md)
  - [Catálogo, Pesaje e Inventario Negativo](./docs/features/catalog-inventory.md)
  - [Autenticación por PIN y Estado de Red](./docs/features/authentication.md)
- **APIs y Pruebas**
  - [Contratos de API REST y WebSocket](./docs/api/endpoints.md)
  - [Estrategia de Pruebas Automatizadas](./docs/testing/testing-strategy.md)

---

## 🧪 Pruebas Automatizadas

```bash
./gradlew :shared:core:jvmTest :shared:contracts:jvmTest :server:test :client:app:jvmJar :client:desktop:test
```