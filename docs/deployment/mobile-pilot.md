# Piloto Android de NexusPOS Móvil

NexusPOS Móvil ofrece consultas para encargados y administradores en la red de personal. Material 3 usa navegación inferior en celular, barra lateral a partir de 600 dp y lista con detalle a partir de 840 dp. La adaptación usa el ancho de la ventana, de modo que en multiventana la tablet puede mostrar la vista compacta.

| Pantalla | Funciones del piloto |
| --- | --- |
| Inicio | Venta neta del día sin IVA y estado de cada caja. |
| Productos | Búsqueda por nombre, SKU o código. Cámara para encontrar la presentación exacta. Precios por cantidad y existencias cuando aplican. |
| Turnos | Filtros por fecha y caja, turnos abiertos y cerrados, importes de arqueo del turno cerrado y búsqueda de sus ventas por folio. |

## Preparar la red de la tienda

1. Respalda PostgreSQL y despliega el servidor actualizado. Flyway aplica la migración V17 al iniciar. Si aplica, configura `NEXUS_STORE_TIMEZONE` con la zona IANA de la tienda. El valor predeterminado es `America/Mexico_City`.
2. Configura el DNS de la red del personal para resolver el nombre del servidor HTTPS. Como ejemplo, ejecuta `NEXUS_MOBILE_HOST=nexuspos.tienda.test caddy run --config deploy/mobile/Caddyfile --adapter caddyfile` en el host del servidor. Ajusta `NEXUS_BACKEND` si el servicio escucha en otro puerto o interfaz. El proxy publica únicamente las rutas móviles.
3. Limita TCP 443 a la red de personal. Restringe el puerto del servidor de NexusPOS al proxy y a las terminales autorizadas. Usa una red de invitados separada y evita publicar puertos hacia internet.
4. En equipos administrados, instala como CA de confianza el certificado raíz del proxy si usas la CA local de Caddy. Distribuye el certificado por el canal de administración de la tienda y compara su huella SHA-256 antes de instalarlo. Conserva la clave privada de la CA en el host. Si la tienda ya tiene un certificado TLS confiable para ese nombre, configura el proxy con ese certificado.

## Crear el APK para distribución interna

Configura el SDK de Android con `ANDROID_HOME` o `sdk.dir` en `local.properties`. Ejecuta `./gradlew :androidApp:assembleRelease`. Firma el APK de salida con la clave de distribución interna usando `apksigner` del SDK de Android. Conserva la misma clave para distribuir actualizaciones.

```sh
apksigner sign --ks /ruta/al/almacen-de-claves.p12 \
  --out NexusPOS-Movil.apk androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk
apksigner verify --verbose --print-certs NexusPOS-Movil.apk
```

## Vincular, probar y retirar equipos

1. En Desktop V2, abre **Configuración → Dispositivos**, ingresa el PIN de administrador y genera un código de un solo uso. Caduca en diez minutos.
2. En Android, acepta el acceso a la red local si el sistema lo solicita. Captura la dirección HTTPS, el código y un nombre que identifique el equipo. Inicia sesión con un PIN de supervisor o administrador. La app bloquea la vista tras 15 minutos de inactividad.
3. En celular y tablet, abre Inicio y compara los importes con el análisis de ventas del mismo día y zona horaria. Busca un producto con varias presentaciones y escanea el código de una de ellas. Prueba también la captura manual con la cámara deshabilitada.
4. Abre y cierra un turno de prueba. Verifica que el arqueo se muestre al cerrar y localiza una de sus ventas por folio. Desconecta la red para comprobar el estado de conexión y vuelve al Wi-Fi para actualizar las consultas.
5. En Desktop, revoca el equipo y comprueba que rechace la siguiente consulta. Para volver a usarlo, descarta la vinculación en el celular y genera otro código. Si el equipo se pierde, revócalo desde Desktop.
