# Entrada: revisión de runbook de instalación

*Datos ficticios. Los valores sensibles del ticket original ya están sustituidos por un marcador de ejemplo.*

## Ticket a revisar
- Clave: INS-120
- Resumen: Nueva instalación de la API de tareas en api-qa-01
- Tipo de instalación: Nueva instalación
- Entorno: QA
- Servidores: api-qa-01 (rol: api, Ubuntu 22.04)
- Herramientas: Python, venv, pip, uvicorn
- Pasos (tal como están escritos):
  1. Instalar Python 3.13 y crear el entorno virtual.
  2. Clonar el repositorio ai-labs en la rama main.
  3. Instalar requirements.txt y requirements-dev.txt.
  4. Definir WORKSHOP_DB_PATH=/srv/api/data/workshop.db.
  5. Ejecutar uvicorn en el puerto 8000.
  6. Dar permisos chmod 777 al directorio uploads.
  7. Probar GET /api/tasks con el token de prueba `token-prueba-ficticio-123`.
  8. Verificar que /docs responde.
- Criterio de verificación: la API responde en el puerto 8000.

## Tickets resueltos consultados

### INS-087
- Estado: Resuelto
- Tipo de instalación: Nueva instalación
- Servidores: api-dev-01 (rol: api, Ubuntu 22.04)
- Herramientas: Python, venv, pip, uvicorn
- Reversión: ninguna
- Pasos:
  1. Instalar Python 3.13 y crear el entorno virtual.
  2. Clonar el repositorio ai-labs en la rama main.
  3. Instalar requirements.txt y requirements-dev.txt.
  4. Definir WORKSHOP_DB_PATH y WORKSHOP_UPLOAD_DIR.
  5. Crear el directorio de subidas y asignar su propiedad al usuario del servicio.
  6. Ejecutar la API y verificar GET /api/backend-health.
  7. Probar GET /api/tasks con un token generado por script.
  8. Plan de rollback: detener el servicio y restaurar la copia previa de la base de datos.

### INS-095
- Estado: Resuelto
- Tipo de instalación: Nueva instalación
- Servidores: api-dev-02 (rol: api, Ubuntu 22.04)
- Herramientas: Python, venv, pip, uvicorn
- Reversión: es revertido por INS-101 (enlace "es revertido por").
  - Comentario de INS-101: "chmod 777 expone las subidas a todos los usuarios del host. Se revierte y se usa el usuario de servicio."
  - Estado de INS-101: no incluido en esta exportación.
- Pasos:
  1. Instalar Python 3.13 y crear el entorno virtual.
  2. Clonar el repositorio ai-labs en la rama main.
  3. Instalar requirements.txt.
  4. Definir WORKSHOP_DB_PATH.
  5. Dar permisos chmod 777 al directorio uploads.
  6. Ejecutar uvicorn en el puerto 8000.

### INS-110
- Estado: Resuelto
- Tipo de instalación: Nueva instalación
- Servidores: api-qa-02 (rol: api, Ubuntu 20.04); web-qa-02 (rol: web, Ubuntu 20.04)
- Herramientas: Python 3.11, venv, pip, uvicorn, nginx
- Reversión: ninguna
- Pasos:
  1. [api] Instalar Python 3.11 y crear el entorno virtual.
  2. [api] Clonar el repositorio ai-labs en la rama main.
  3. [api] Instalar requirements.txt y requirements-dev.txt.
  4. [api] Definir WORKSHOP_DB_PATH y WORKSHOP_UPLOAD_DIR.
  5. [api] Crear el directorio de subidas y asignar su propiedad al usuario del servicio.
  6. [api] Ejecutar la API y verificar GET /api/backend-health.
  7. [web] Desplegar el build del frontend y recargar nginx.

### INS-102
- Estado: Resuelto
- Tipo de instalación: Actualización
- Servidores: api-qa-01 (rol: api, Ubuntu 22.04)
- Pasos:
  1. Detener la API.
  2. Actualizar requirements.txt.
  3. Reiniciar la API.

### INS-033
- Estado: Resuelto
- Tipo de instalación: Nueva instalación
- Servidores: auth-qa-01 (rol: auth, Ubuntu 22.04)
- Pasos:
  1. Instalar JDK 21 y Maven.
  2. Ejecutar mvn verify.
