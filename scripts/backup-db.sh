#!/usr/bin/env bash
# ==============================================================================
# NexusPOS - Script de Respaldo Automatizado de Base de Datos PostgreSQL
# ==============================================================================
# Uso:
#   ./scripts/backup-db.sh
#
# Variables de entorno opcionales:
#   DB_HOST       (default: localhost)
#   DB_PORT       (default: 5432)
#   DB_NAME       (default: nexuspos)
#   DB_USER       (default: nexus)
#   DB_PASSWORD   (default: nexuspassword)
#   BACKUP_DIR    (default: ./backups)
#   RETENTION_DAYS (default: 30)
# ==============================================================================

set -eo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-nexuspos}"
DB_USER="${DB_USER:-nexus}"
export PGPASSWORD="${DB_PASSWORD:-nexuspassword}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"

echo "=========================================================="
echo "  NexusPOS - Respaldo de Base de Datos PostgreSQL"
echo "=========================================================="
echo "Servidor:    $DB_HOST:$DB_PORT"
echo "Base datos:  $DB_NAME"
echo "Usuario:     $DB_USER"
echo "Directorio:  $BACKUP_DIR"
echo "Retención:   $RETENTION_DAYS días"
echo "=========================================================="

# 1. Verificar herramienta pg_dump
if ! command -v pg_dump >/dev/null 2>&1; then
    echo "❌ ERROR: 'pg_dump' no está instalado o no se encuentra en el PATH." >&2
    echo "Instale las herramientas de cliente de PostgreSQL (ej. postgresql-client)." >&2
    exit 1
fi

# 2. Asegurar existencia de directorio de respaldos
mkdir -p "$BACKUP_DIR"

# 3. Generar nombre con marca temporal
TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
BACKUP_FILENAME="nexuspos_backup_${TIMESTAMP}.sql.gz"
BACKUP_FILEPATH="$BACKUP_DIR/$BACKUP_FILENAME"

echo "⏳ Iniciando exportación y compresión gzip..."
START_TIME="$(date +%s)"

# 4. Ejecución del volcado con compresión directa
if pg_dump \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    --no-owner \
    --no-privileges \
    --clean \
    --if-exists \
    | gzip -9 > "$BACKUP_FILEPATH"; then

    # 5. Verificación de integridad con gzip -t
    if gzip -t "$BACKUP_FILEPATH" >/dev/null 2>&1; then
        END_TIME="$(date +%s)"
        DURATION=$((END_TIME - START_TIME))
        FILE_SIZE="$(du -h "$BACKUP_FILEPATH" | awk '{print $1}')"

        echo "✅ Respaldo completado con éxito:"
        echo "   Archivo: $BACKUP_FILEPATH"
        echo "   Tamaño:  $FILE_SIZE"
        echo "   Tiempo:  ${DURATION}s"
    else
        echo "❌ ERROR: El archivo de respaldo está corrupto (falló gzip -t)." >&2
        rm -f "$BACKUP_FILEPATH"
        exit 1
    fi
else
    echo "❌ ERROR al ejecutar pg_dump. Verifique que el servicio PostgreSQL esté activo y las credenciales sean correctas." >&2
    rm -f "$BACKUP_FILEPATH"
    exit 1
fi

# 6. Política de rotación / retención automática
echo "🧹 Aplicando política de retención (eliminando respaldos anteriores a $RETENTION_DAYS días)..."
PRUNED_COUNT=0
while IFS= read -r old_file; do
    if [ -n "$old_file" ]; then
        rm -f "$old_file"
        echo "   Eliminado: $(basename "$old_file")"
        PRUNED_COUNT=$((PRUNED_COUNT + 1))
    fi
done < <(find "$BACKUP_DIR" -maxdepth 1 -name "nexuspos_backup_*.sql.gz" -type f -mtime "+$RETENTION_DAYS")

if [ "$PRUNED_COUNT" -eq 0 ]; then
    echo "   No se encontraron archivos obsoletos para eliminar."
fi

echo "=========================================================="
echo "  Proceso finalizado correctamente."
echo "=========================================================="
