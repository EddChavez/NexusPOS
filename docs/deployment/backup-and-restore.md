# Guía de Respaldos y Restauración de Base de Datos - NexusPOS

Esta guía describe el procedimiento de respaldo automatizado, restauración ante desastres y políticas de retención para la base de datos PostgreSQL de **NexusPOS**.

---

## 📦 1. Scripts Automatizados Incluidos

El repositorio cuenta con dos scripts listos para producción ubicados en `scripts/`:

| Script | Sistema Operativo | Formato de Salida | Compresión |
| :--- | :--- | :--- | :--- |
| `scripts/backup-db.sh` | Linux / macOS | `nexuspos_backup_YYYYMMDD_HHMMSS.sql.gz` | `gzip -9` |
| `scripts/backup-db.bat` | Windows | `nexuspos_backup_YYYYMMDD_HHMMSS.sql` | `pg_dump custom format (-F c)` |

Ambos scripts leen variables de entorno estándar de PostgreSQL o aplican valores por defecto:
- `DB_HOST`: Host o IP del servidor (default: `localhost`).
- `DB_PORT`: Puerto PostgreSQL (default: `5432`).
- `DB_NAME`: Nombre de la base de datos (default: `nexuspos`).
- `DB_USER`: Usuario de la base de datos (default: `nexus`).
- `DB_PASSWORD`: Contraseña (default: `nexuspassword`).
- `BACKUP_DIR`: Directorio destino (default: `./backups`).
- `RETENTION_DAYS`: Días de conservación (default: `30` días).

---

## 🚀 2. Ejecución Manual

### En Linux / macOS:
```bash
# Otorgar permisos de ejecución si no los tiene
chmod +x scripts/backup-db.sh

# Ejecución con configuración por defecto
./scripts/backup-db.sh

# Ejecución especificando variables de entorno
DB_HOST=192.168.1.50 DB_NAME=nexuspos DB_USER=nexus DB_PASSWORD=miPasswordSeguro ./scripts/backup-db.sh
```

### En Windows (cmd o PowerShell):
```cmd
REM Ejecutar directamente
scripts\backup-db.bat

REM O definiendo variables en la sesión
set DB_HOST=192.168.1.50
set DB_PASSWORD=miPasswordSeguro
scripts\backup-db.bat
```

---

## ⏰ 3. Programación Automática

### En Linux / macOS vía `crontab`:
Para programar un respaldo diario todas las noches a las **23:30 hrs**:
```bash
crontab -e
```
Agregue la siguiente línea al final del archivo:
```cron
30 23 * * * /bin/bash /opt/nexuspos/scripts/backup-db.sh >> /var/log/nexuspos-backup.log 2>&1
```

### En Windows vía `Programador de Tareas` (Task Scheduler):
1. Abra `taskschd.msc` (Programador de tareas).
2. Seleccione **Crear tarea básica...**
3. Nombre: `NexusPOS Respaldo Diario`.
4. Desencadenador: **Diariamente a las 23:30**.
5. Acción: **Iniciar un programa**.
6. Programa o script: `C:\NexusPOS\scripts\backup-db.bat`.
7. En "Iniciar en (opcional)": `C:\NexusPOS`.
8. Finalice la creación de la tarea.

---

## 🔄 4. Procedimiento de Restauración

### Restaurar archivo `.sql.gz` (Linux / macOS):
```bash
# 1. Descomprimir e inyectar a PostgreSQL
gunzip -c backups/nexuspos_backup_20261107_233000.sql.gz | psql -h localhost -U nexus -d nexuspos
```

### Restaurar archivo `.sql` en formato custom (Windows):
```cmd
pg_restore.exe -h localhost -U nexus -d nexuspos --clean --if-exists backups\nexuspos_backup_20261107_233000.sql
```

---

## 🛡️ 5. Pruebas de Integridad
El script `scripts/backup-db.sh` verifica automáticamente la integridad física del archivo generado ejecutando:
```bash
gzip -t backups/nexuspos_backup_YYYYMMDD_HHMMSS.sql.gz
```
Si el archivo llegase a estar truncado o corrupto, el script lo elimina de inmediato, alerta en `stderr` y sale con código `1`, evitando respaldos incompletos silenciosos.
