# Arquitectura: Seguridad y Control de Acceso (Security)

Este documento especifica el modelo de seguridad, autenticación, autorización basada en roles (RBAC) y protección de datos dentro de la red local de NexusPOS.

---

## 1. Modelo de Amenazas y Entorno Operativo

NexusPOS está diseñado para operar en una **Red de Área Local (LAN)** privada:
- **Sin exposición directa a Internet:** El servidor no expone puertos públicos en el módem de la tienda ni requiere servicios en la nube para cobrar.
- **Acceso físico compartido:** En una tienda de abarrotes, múltiples cajeros pueden alternar turnos en la misma computadora o en terminales secundarias visibles para los clientes.
- **Principales riesgos mitigados:**
  1. Uso indebido de permisos de administrador por cajeros temporales.
  2. Extracción de respaldos de base de datos con contraseñas o PINs en texto plano.
  3. Suplantación de identidad entre cajas o alteración de ventas y cortes de caja.

---

## 2. Autenticación de Usuarios con BCrypt

Para balancear la velocidad operativa en mostrador con la seguridad criptográfica:
- Los cajeros y supervisores ingresan mediante un **PIN numérico de 4 dígitos**.
- Los PINs **nunca se almacenan en texto plano**.

### Implementación del Hashing:
En [`Security.kt`](file:///Users/eddch/Documents/OzafryDev/NexusPOS/server/src/main/kotlin/mx/nexuspos/server/platform/Security.kt), se utiliza la implementación estándar de **BCrypt**:
```kotlin
// Generación segura de hash con salt dinámico y factor de costo 10
fun hashPin(pin: String): String = BCrypt.hashpw(pin, BCrypt.gensalt(10))

// Verificación en tiempo constante contra ataques de temporización
fun verifyPin(pin: String, hashed: String): Boolean = BCrypt.checkpw(pin, hashed)
```

**Ventajas frente a hashing simple (MD5 / SHA-256):**
- Un PIN de 4 dígitos tiene únicamente 10,000 combinaciones posibles (`0000`–`9999`).
- BCrypt incorpora un retardo computacional deliberado (~70 ms por intento), neutralizando ataques automatizados de fuerza bruta y tablas rainbow en caso de que alguien obtenga una copia del archivo de base de datos.

---

## 3. Matriz de Roles y Permisos (RBAC)

El sistema define 3 roles jerárquicos:

| Operación / Recurso | Cajero (`CASHIER`) | Encargado (`SUPERVISOR`) | Administrador (`ADMIN`) |
| :--- | :---: | :---: | :---: |
| Escanear y cobrar ventas en efectivo | ✅ | ✅ | ✅ |
| Vender a cuenta de crédito (fiado) | ✅ | ✅ | ✅ |
| Recibir abonos de clientes | ✅ | ✅ | ✅ |
| Abrir y cerrar su propia sesión de caja | ✅ | ✅ | ✅ |
| Registrar retiros/salidas menores de efectivo | ✅ (con límite) | ✅ | ✅ |
| Reimpresión de tickets anteriores | ✅ (marca `COPIA`) | ✅ | ✅ |
| Cancelación de tickets y devoluciones | ❌ *(Requiere PIN supervisor)* | ✅ | ✅ |
| Ver reporte de ventas y cortes de otros cajeros | ❌ | ✅ | ✅ |
| Ajustes manuales de existencias de inventario | ❌ | ✅ | ✅ |
| Alta, baja y edición de productos y precios | ❌ | ❌ | ✅ |
| Activar/Desactivar inventario negativo | ❌ | ❌ | ✅ |
| Alta de usuarios y cambio de PINs | ❌ | ❌ | ✅ |
| Emparejar o revocar terminales y dispositivos | ❌ | ❌ | ✅ |
| Respaldos de base de datos y configuración | ❌ | ❌ | ✅ |

### Autorización al Vuelo (*Elevation of Privilege*):
Cuando un cajero intenta una acción que requiere rol de encargado (por ejemplo, autorizar una cancelación o vender por encima de un límite), la interfaz no requiere cerrar sesión; muestra un diálogo modal solicitando el PIN del supervisor para autorizar exclusivamente esa transacción (`POST /api/v1/auth/authorize-supervisor`).

---

## 4. Emparejamiento y Confianza de Dispositivos

Para evitar que cualquier dispositivo conectado a la red Wi-Fi o cableada envíe peticiones al servidor:
1. El Administrador genera un **código de emparejamiento de un solo uso** de 6 caracteres en la PC principal (`POST /api/v1/devices/pair-code`).
2. La terminal secundaria ingresa el código durante la configuración inicial.
3. El servidor valida el código y genera un `device_token` único, almacenado como hash **SHA-256** en la tabla `devices`.
4. El cliente incluye el token en el encabezado `X-Device-Token` de cada petición.
5. El Administrador puede revocar cualquier terminal en cualquier momento desde la pantalla de Configuración.

---

## 5. Aislamiento y Respaldo de Datos

- **Base de Datos PostgreSQL:** El puerto `5432` solo escucha peticiones de la red local del contenedor Docker o `localhost`. No está mapeado hacia redes públicas.
- **Respaldos Automáticos:** El servidor ejecuta una tarea de respaldo diaria exportando un dump SQL comprimido con checksum SHA-256 hacia un almacenamiento secundario o memoria USB.
