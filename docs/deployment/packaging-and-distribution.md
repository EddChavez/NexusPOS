# Guía de Empaquetado y Distribución de NexusPOS

Esta guía describe cómo generar instaladores y ejecutables nativos autocontenidos para la terminal de escritorio **NexusPOS**, así como las estrategias recomendadas para el despliegue multi-caja en red local (LAN) en tiendas de abarrotes y comercios.

---

## 🎯 1. Visión General del Empaquetado

NexusPOS Desktop utiliza el plugin oficial de **Compose Multiplatform Desktop** (`org.jetbrains.compose`), el cual se apoya internamente en las herramientas nativas del JDK (`jpackage` y `jlink`) para construir ejecutables que **incluyen su propio Java Runtime Environment (JRE 21) embebido**.

### Beneficios clave:
- **Cero Dependencias en el Cliente:** La máquina cliente (cajero) no necesita tener instalado Java, JDK ni Gradle.
- **Aislamiento Total:** El JRE embebido está optimizado y no entra en conflicto con otras versiones de Java que pudieran existir en el sistema operativo.
- **Instalación Nativa:** Genera archivos `.msi` para Windows, `.dmg` para macOS y `.deb` para Linux con accesos directos en el menú de inicio y escritorio.

---

## 🛠️ 2. Configuración en Gradle

La configuración de empaquetado se encuentra en `client/desktop/build.gradle.kts`:

```kotlin
compose.desktop {
    application {
        mainClass = "mx.nexuspos.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "NexusPOS"
            packageVersion = "1.0.0"
            description = "NexusPOS - Terminal de Punto de Venta"
            copyright = "© 2026 NexusPOS"
            vendor = "OzafryDev"

            windows {
                menuGroup = "NexusPOS"
                shortcut = true
                dirChooser = true
            }

            macOS {
                bundleID = "mx.nexuspos.desktop"
                dockName = "NexusPOS"
            }

            linux {
                shortcut = true
            }
        }
    }
}
```

---

## 🚀 3. Tareas de Empaquetado y Comandos

A continuación se detallan los comandos disponibles mediante `./gradlew`:

### A. Carpeta de Aplicación Distribuible (Portátil / Sin Instalador)
Ideal para pruebas rápidas o despliegue en memoria USB sin requerir privilegios de administrador para instalar:

```bash
./gradlew :client:desktop:createDistributable
```
- **Ruta de salida:**
  `client/desktop/build/compose/binaries/main/app/`
- **Contenido generado:**
  - **macOS:** `NexusPOS.app` (contiene la app, dependencias `.jar` y el JRE embebido en `Contents/runtime`).
  - **Windows:** Carpeta `NexusPOS/` con el ejecutable `NexusPOS.exe` y la subcarpeta `runtime/`.
  - **Linux:** Carpeta `NexusPOS/` con el binario ejecutable `NexusPOS` y subcarpeta `runtime/`.

---

### B. Generar Instalador del Sistema Operativo Actual
Compila el instalador nativo según la plataforma en la que se ejecute la compilación:

```bash
./gradlew :client:desktop:packageDistributionForCurrentOS
```
- **Ruta de salida:**
  `client/desktop/build/compose/binaries/main/`

---

### C. Tareas Específicas por Plataforma

> **Nota:** Debido a que `jpackage` depende de las APIs nativas del sistema operativo anfitrión, los instaladores deben compilarse en la plataforma correspondiente:

| Sistema Operativo Objetivo | Tarea Gradle | Formato de Salida |
| :--- | :--- | :--- |
| **macOS** | `./gradlew :client:desktop:packageDmg` | `NexusPOS-1.0.0.dmg` |
| **Windows** | `gradlew.bat :client:desktop:packageMsi` | `NexusPOS-1.0.0.msi` |
| **Linux (Debian/Ubuntu)** | `./gradlew :client:desktop:packageDeb` | `NexusPOS_1.0.0_amd64.deb` |

---

### D. UberJAR / Fat JAR Autónomo (Multiplataforma con Java Instalado)
Si se desea distribuir un único archivo ejecutable `.jar` que corra en cualquier máquina con Java 17+ instalado:

```bash
./gradlew :client:desktop:packageUberJarForCurrentOS
```
- **Ruta de salida:**
  `client/desktop/build/compose/jars/NexusPOS-macos-arm64-1.0.0.jar` (o según la arquitectura).

---

## 🌐 4. Arquitectura de Despliegue en Red Local (LAN Multi-Caja)

En un entorno comercial típico (por ejemplo, una tienda de abarrotes con 1 servidor central y 2 o más cajas de cobro):

```text
               ┌────────────────────────────────────────────────────────┐
               │        MÁQUINA PRINCIPAL (Servidor Central)            │
               │                                                        │
               │   • PostgreSQL (Base de datos central - puerto 5432)   │
               │   • NexusPOS Server Ktor (puerto 8080)                 │
               │   • Terminal de Caja 01 (localhost:8080)               │
               └───────────────────────────┬────────────────────────────┘
                                           │ Red Local Ethernet / Wi-Fi
                                           │ (IP fija recomendada: 192.168.1.100)
                     ┌─────────────────────┴─────────────────────┐
                     │                                           │
       ┌─────────────▼───────────────┐             ┌─────────────▼───────────────┐
       │     CAJA 02 (Secundaria)    │             │     CAJA 03 (Secundaria)    │
       │                             │             │                             │
       │ • NexusPOS Instalador Nativo│             │ • NexusPOS Instalador Nativo│
       │ • config.properties:        │             │ • config.properties:        │
       │   server.host=192.168.1.100 │             │   server.host=192.168.1.100 │
       │   register.id=caja-2        │             │   register.id=caja-3        │
       └─────────────────────────────┘             └─────────────────────────────┘
```

### Configuración en Cajas Secundarias:
1. Instale `NexusPOS-1.0.0.msi` (o `.dmg` / `.deb`) en la máquina de la caja secundaria.
2. Al iniciar la aplicación por primera vez, el sistema detectará que no hay servidor en `localhost` y desplegará automáticamente la ventana modal **"Conexión con Servidor NexusPOS"**.
3. Ingrese la dirección IP del servidor central (ej. `192.168.1.100`), el puerto `8080` y el identificador de caja (ej. `caja-2`).
4. Presione **[🔍 Probar Conexión]** para verificar comunicación con `/health`.
5. Al hacer clic en **[💾 Guardar y Conectar]**, la configuración se almacenará permanentemente en el archivo local:
   - En Windows: `C:\Users\<usuario>\.nexuspos\config.properties`
   - En macOS / Linux: `~/.nexuspos/config.properties`

---

## 🖨️ 5. Despliegue de Periféricos y Hardware POS

Cada terminal física mantiene su configuración de hardware independiente en `~/.nexuspos/printer.properties`:
- **Impresoras Térmicas USB:** Seleccionadas a través del spooler del sistema operativo en `[F5] Configuración -> Impresora`.
- **Impresoras de Red Ethernet / Wi-Fi:** Mediante conexión Socket TCP directa al puerto `9100`.
- **Cajón de Dinero RJ11:** Conectado directamente al puerto `DK` de la impresora térmica; la terminal envía pulsos binarios ESC/POS `ESC p` al cobrar en efectivo o con la tecla `[F7]`.

---

## 🔧 6. Solución de Problemas Frecuentes (Troubleshooting)

### macOS: Advertencia de Desarrollador No Verificado (Gatekeeper)
Al abrir la aplicación descargada en macOS por primera vez sin firma de Apple Developer notarizada, puede aparecer el mensaje *"NexusPOS no se puede abrir porque el desarrollador no ha sido verificado"*.
- **Solución rápida:**
  Haga clic derecho (o `Ctrl` + clic) sobre `NexusPOS.app` y seleccione **Abrir**.
- **O mediante terminal:**
  ```bash
  xattr -cr /Applications/NexusPOS.app
  ```

### Windows: Error de Conexión en Cajas Secundarias
Si una caja secundaria no logra conectar con el servidor central:
1. Verifique que la máquina servidor tenga una IP estática o reservada en el router (ej. `192.168.1.100`).
2. Asegúrese de que el **Firewall de Windows** en la máquina servidor permita el tráfico entrante en el puerto `8080`:
   ```powershell
   New-NetFirewallRule -DisplayName "NexusPOS Server" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
   ```
3. Pruebe conectividad desde la caja secundaria abriendo en un navegador:
   `http://192.168.1.100:8080/health` (debe responder `{"status":"UP"}`).
