# Configuración inicial de NexusPOS V2

Actualiza el servidor y Desktop juntos. Antes de actualizar una tienda, sincroniza las ventas pendientes y respalda PostgreSQL. Configura la primera terminal en el servidor local o mediante un punto de acceso HTTPS de confianza hacia el servidor.

## Instalar una sucursal nueva

1. Inicia el servidor con una base de datos propia de la tienda.
2. Con las mismas variables `DB_URL`, `DB_USER` y `DB_PASSWORD` del servicio, ejecuta el binario del servidor con el argumento `setup-code`. Usa el código de ocho dígitos de la última línea de la salida dentro de diez minutos.
3. Abre Desktop, conecta al servidor y completa Negocio, Sucursal, Caja, Administrador raíz y Preferencias. Usa el código al guardar la configuración.
4. Selecciona **Usar PIN raíz** e ingresa el PIN de seis dígitos. En Configuración V2 registra a los operadores y asigna sus roles. En las demás terminales indica la dirección del servidor y el identificador de una caja existente.

## Adoptar o recuperar la cuenta raíz

- **Tienda en operación.** Ejecuta `server adopt-root-code` en el host y abre **Configuración → Negocio y sucursal → Configurar administrador raíz**. El usuario promovido conserva su identificador. Usa el nuevo PIN para volver a entrar.
- **Recuperación.** Ejecuta `server recover-root-code` y usa **Recuperar PIN raíz** en la pantalla de acceso. La recuperación cambia el PIN de la cuenta raíz e invalida sus sesiones Desktop y móvil.
- Cada código tiene seis intentos. Genera otro en el servidor si caducó, se consumió o agotaste los intentos.

## Comprobaciones del despliegue

Prueba permisos con un usuario operador y otro propietario. Confirma que el folio existente se mantiene, que las cajas se registran una sola vez y que el relevo conserva el turno. Prueba abonos en efectivo y con tarjeta para confirmar que solo el efectivo se contabiliza en la caja. Si activas un límite de efectivo, verifica que una operación que lo supera requiera el PIN de un usuario autorizado.

Para vincular Android, usa el proxy HTTPS de la tienda y sigue la [guía del piloto móvil](mobile-pilot.md).
