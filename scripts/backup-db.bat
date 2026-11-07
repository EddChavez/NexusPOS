@echo off
REM ==============================================================================
REM NexusPOS - Script de Respaldo Automatizado de Base de Datos PostgreSQL (Windows)
REM ==============================================================================
REM Variables opcionales:
REM   set DB_HOST=localhost
REM   set DB_PORT=5432
REM   set DB_NAME=nexuspos
REM   set DB_USER=nexus
REM   set DB_PASSWORD=nexuspassword
REM   set BACKUP_DIR=C:\NexusPOS\backups
REM ==============================================================================

if "%DB_HOST%"=="" set DB_HOST=localhost
if "%DB_PORT%"=="" set DB_PORT=5432
if "%DB_NAME%"=="" set DB_NAME=nexuspos
if "%DB_USER%"=="" set DB_USER=nexus
if "%DB_PASSWORD%"=="" set DB_PASSWORD=nexuspassword
if "%BACKUP_DIR%"=="" set BACKUP_DIR=%~dp0..\backups

set PGPASSWORD=%DB_PASSWORD%

echo ==========================================================
echo   NexusPOS - Respaldo de Base de Datos PostgreSQL
echo ==========================================================
echo Servidor:    %DB_HOST%:%DB_PORT%
echo Base datos:  %DB_NAME%
echo Usuario:     %DB_USER%
echo Directorio:  %BACKUP_DIR%
echo ==========================================================

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set TIMESTAMP=%datetime:~0,8%_%datetime:~8,6%
set BACKUP_FILENAME=nexuspos_backup_%TIMESTAMP%.sql
set BACKUP_FILEPATH=%BACKUP_DIR%\%BACKUP_FILENAME%

echo Iniciando exportacion con pg_dump...
pg_dump.exe -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% --no-owner --no-privileges --clean --if-exists -F c -b -v -f "%BACKUP_FILEPATH%"

if %ERRORLEVEL% EQU 0 (
    echo [OK] Respaldo generado exitosamente: %BACKUP_FILEPATH%
) else (
    echo [ERROR] Fallo al generar respaldo. Verifique que PostgreSQL este activo y en PATH.
    exit /b 1
)

echo Proceso finalizado correctamente.
