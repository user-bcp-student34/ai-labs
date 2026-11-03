# Hallazgos de ReqFlow

Seguimiento compartido de hallazgos. Cada fila lleva componente e ID (H-AUTH-NN, H-API-NN, H-WEB-NN). Esta sección cubre `api`.

## api

### Discovery: alcance y método

- Fecha: 2026-11-02. Fase: Discovery, sin cambios de código.
- Fuentes leídas: `README.md`, `RETO.md`, `docs/GOOD_PRACTICES.md`, `docs/prompts/api`, `AGENTS.md` (sección api), y el código de `api/` (`app/main.py`, `tests/test_api.py`, `requirements.txt`, `pyproject.toml`, `Dockerfile`).
- Lectura adicional, solo para verificar dependencias instaladas en `api/.venv`: `starlette/middleware/cors.py`, `python_multipart/multipart.py`, `fastapi/applications.py`.
- Método: lectura de código. Un hecho es algo visible en una línea citada. Una hipótesis queda marcada como tal y necesita prueba.
- Ninguna prueba se ejecutó en este paso.
- `AGENTS.md` sigue vigente: todas las rutas están en `api/app/main.py`, un único archivo de 130 líneas. Las cinco rutas HTTP están verificadas en código con `@app.get` y `@app.post` en las líneas 67, 77, 90, 107 y 114.

### Contrato observado

No existe `docs/ENDPOINTS.md`, así que este contrato se deriva del código. No es un contrato acordado. No se ha registrado ninguno para `api`.

| Método y ruta | Función | Autenticación | Entrada | Respuesta observada | Evidencia |
|---|---|---|---|---|---|
| GET /api/tasks | list_tasks | Bearer (legacy_identity) | query `owner` opcional | 200 lista de tareas con id, title, description, status, owner, priority, createdAt | api/app/main.py:67-75 |
| POST /api/tasks | create_task | Bearer | body JSON libre: title, description, status (por defecto TODO), priority (por defecto MEDIUM), owner (por defecto usuario del token) | 201 con la tarea creada | api/app/main.py:77-88 |
| GET /api/summary | summary | Bearer | query `owner` opcional | 200 con owner, total, byStatus, highPriorityOpen | api/app/main.py:90-105 |
| POST /api/requirements | upload_requirement | Bearer | multipart, campo `file` | 201 con owner, fileName, storedAt (ruta del servidor), size | api/app/main.py:107-112 |
| GET /api/backend-health | backend_health | Bearer | ninguna | 200 con requestedBy, status, body. Si falla, también 200 con status 0 | api/app/main.py:114-121 |

Respuestas de error observadas:
- Token ausente, mal formado, con secreto distinto o expirado: 401 con `detail` "Invalid token: " más el texto de la excepción (api/app/main.py:22-26).
- Ruta sin token: 401 también, porque las cinco rutas dependen de `legacy_identity`.

Contrato acordado: no registrado. Pendiente de acuerdo entre responsables: en especial, qué debe responder `/api/tasks?owner=<otro>` (ver H-API-05).

Consumidor: el RETO indica que el frontend consume `/api/tasks`. No se verificó en `web/`, que queda fuera de ámbito.

### Flujo del token

Emisión: el token lo genera `auth/`, fuera de ámbito. No está verificado. El formato consumido se deduce de `api/app/main.py:17-24` y del helper de pruebas `api/tests/test_api.py:10-12`.

Verificación en `legacy_identity` (api/app/main.py:17-26):
1. Lee la cabecera `Authorization` (L17). Quita la cadena "Bearer " en cualquier posición (L19). No valida el prefijo.
2. Decodifica base64 y UTF-8 (L20).
3. Separa por `|` y exige 4 campos (L21-22). Formato: `usuario|rol|expiración_epoch|secreto`.
4. Compara el cuarto campo con la constante `LEGACY_SECRET` del código (L14, L22).
5. Comprueba expiración: rechaza si `exp < ahora` (L22).
6. Devuelve `username` y `role` (L24). El rol no se usa para autorizar en ningún punto.

Lo que no hay: firma criptográfica, emisor (`iss`), audiencia (`aud`), ni secreto externalizado.

Quién usa la identidad: `list_tasks` (L68), `create_task` (L78), `summary` (L91), `upload_requirement` (L108) y `backend_health` (L115).

### Acceso a SQLite

Conexión única creada en `create_app` (api/app/main.py:35), con `check_same_thread=False` y compartida por todas las rutas. La base por defecto es `./data/workshop.db`, configurable con `WORKSHOP_DB_PATH` (L30).

Inventario de consultas (ninguna usa parámetros `?`; verificado con búsqueda en `main.py`):

| # | Ruta o función | Consulta | Entrada HTTP | Cómo se construye | Evidencia |
|---|---|---|---|---|---|
| 1 | list_tasks | SELECT tareas por owner | Sí: query `owner`, o usuario del token si falta | Concatenación de cadena | api/app/main.py:69-70 |
| 2 | create_task | INSERT de tarea | Sí: title, description, status, priority, owner del body | Concatenación de cadena | api/app/main.py:79-85 |
| 3 | create_task | SELECT por id tras insertar | No directa: `cursor.lastrowid` (entero interno) | Concatenación de cadena, con valor interno | api/app/main.py:87 |
| 4 | summary | SELECT status, priority por owner | Sí: query `owner`, o usuario del token | Concatenación de cadena | api/app/main.py:92-93 |
| 5 | create_app | CREATE TABLE, COUNT, seeds | No | SQL constante | api/app/main.py:37-43 |

Comprobado: las consultas 1, 2 y 4 reciben entrada HTTP y la concatenan sin escapar. La consulta 3 concatena un valor generado por SQLite, así que su riesgo es bajo, pero tampoco usa parámetros.

Hipótesis (no probada): una comilla simple en cualquiera de esos campos rompe la sentencia. Ver las pruebas propuestas.

### Carga de archivos

Ruta `POST /api/requirements` (api/app/main.py:107-112):
- L109: destino = `Path(uploads) / file.filename`, sin normalizar el nombre.
- L110: `await file.read()` lee el archivo completo en memoria, sin tope de tamaño.
- L111: `write_bytes` sobrescribe si ya existe un archivo con el mismo nombre.
- L112: la respuesta devuelve `fileName` tal cual y `storedAt` con la ruta del servidor.
- No hay validación de extensión ni de tipo MIME.

Comprobado en la dependencia instalada: `python_multipart/multipart.py:1688-1690` toma `filename` del `Content-Disposition` sin quitar directorios. Búsqueda de `basename`, `os.path.split` y `PurePath` en `python_multipart/multipart.py`, `starlette/datastructures.py` y `starlette/formparsers.py`: sin resultados en esos archivos. Hipótesis: un nombre con secuencias de directorio puede escribir fuera de `uploads/`. No probado en ejecución.

Directorio de subida: `WORKSHOP_UPLOAD_DIR` o `./uploads` (api/app/main.py:31).

### Matriz de seguimiento

Orden de mayor a menor severidad. Todos los estados son "Pendiente". Ningún hallazgo de RETO.md de `api` quedó como "No encontrado".

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-API-01 | api | Token Base64 con secreto embebido, sin firma, emisor ni audiencia (RETO: Auth) | Vulnerabilidad | El token es base64 de usuario, rol, expiración y secreto. La validación compara el secreto con una constante del código y comprueba la expiración, pero no hay firma ni emisor/audiencia. Con acceso al código se puede generar un token para cualquier usuario y rol. El rol no se usa para autorizar. Hecho por lectura. | api/app/main.py:14, 17-26; api/tests/test_api.py:10-12 | alta | Aprobado |
| H-API-02 | api | INSERT de tareas construido por concatenación (RETO: SQL) | Vulnerabilidad | Los cinco campos del body entran en el texto SQL entre comillas simples. Una comilla rompe la sentencia y permite alterarla. No hay parámetros en ninguna consulta. Hecho por lectura. | api/app/main.py:79-85 | alta | Resuelto: consultas con `?` en api/app/repository.py; revisión humana pendiente |
| H-API-03 | api | Filtro por owner concatenado en GET /api/tasks y GET /api/summary (RETO: SQL) | Vulnerabilidad | El valor de `owner` (o el usuario del token si falta) se pega en `WHERE owner='...'`. Una comilla rompe la consulta y el mismo patrón permite modificar la condición. Hecho por lectura. | api/app/main.py:69-70, 92-93 | alta | Resuelto: consultas con `?` en api/app/repository.py; revisión humana pendiente |
| H-API-04 | api | upload_requirement usa file.filename sin normalizar (RETO: Files) | Vulnerabilidad | `Path(uploads) / filename` acepta secuencias de directorio. Un nombre con `../` o ruta absoluta puede escribir fuera de `uploads/`. python-multipart pasa el nombre sin quitar directorios. Hipótesis: escritura fuera de `uploads/`, pendiente de prueba. | api/app/main.py:109, 112; api/.venv/Lib/site-packages/python_multipart/multipart.py:1688-1690 | alta | Resuelto: nombre normalizado y extensión permitida en api/app/uploads.py; revisión humana pendiente |
| H-API-05 | api | Autorización por parámetro del cliente en listado y resumen (RETO: Autorización) | Vulnerabilidad | `owner` de la query tiene prioridad sobre la identidad autenticada (`owner or identity["username"]`). Un usuario autenticado puede leer tareas de otro owner. El rol no se comprueba. Hecho por lectura. Qué respuesta se espera es decisión pendiente de acuerdo. | api/app/main.py:68-69, 91-92, 24 | alta | Resuelto: dueño resuelto por identidad en api/app/authorization.py (owner ajeno, 403); revisión humana pendiente |
| H-API-06 | api | Escritura de tareas con owner elegido por el cliente (RETO: Autorización) | Vulnerabilidad | `payload.get("owner")` sustituye al usuario del token. Un usuario puede crear tareas en la lista de otro. Relacionado con H-API-05. Hecho por lectura. | api/app/main.py:83-84 | alta | Resuelto: owner del body validado con api/app/authorization.py (owner ajeno, 403; null como ausente); revisión humana pendiente |
| H-API-07 | api | Subida sin límite de tamaño ni de extensión, y sobrescritura silenciosa (RETO: Files) | Vulnerabilidad | `await file.read()` carga todo en memoria sin tope. No hay lista de extensiones ni de MIME. `write_bytes` reemplaza un archivo con el mismo nombre. La respuesta expone la ruta del servidor en `storedAt`. Hecho por lectura. | api/app/main.py:109-112 | media | Resuelto: límite de 5 MiB con lectura por bloques (413) y sobrescritura rechazada con 409 en api/app/uploads.py; revisión humana pendiente |
| H-API-08 | api | Dependencias fijadas con versiones a revisar: urllib3 2.5.0 y python-multipart 0.0.21 (RETO: SCA) | Vulnerabilidad | Versiones exactas en requirements.txt. urllib3 se usa en `backend_health` (L118) y python-multipart procesa la subida. CVE concretos y versiones corregidas no verificados: no se consultaron advisories en esta etapa. Severidad provisional hasta revisar advisories. | api/requirements.txt:15-16; api/app/main.py:10, 118 | media | Pendiente |
| H-API-09 | api | Rutas, base de datos, reglas y serialización en una sola función (RETO: Arquitectura) | Refactor | `create_app` concentra conexión y seeds, serialización `as_task`, reglas del resumen y cinco rutas, sin capas ni repositorio. Además `app = create_app()` se ejecuta al importar el módulo: crea `data/` y `uploads/` y abre la base al importarlo, incluido en pruebas. Hecho por lectura. | api/app/main.py:29-127, 56-65, 94-105, 130 | media | Pendiente |
| H-API-10 | api | Errores con texto interno expuesto al cliente (adicional a RETO; regla de GOOD_PRACTICES api) | Vulnerabilidad | El 401 incluye `"Invalid token: " + str(error)`. `backend_health` devuelve `str(error)` en el cuerpo. Puede exponer detalles de excepciones y de la red interna. Hecho por lectura. | api/app/main.py:26, 121 | media | Pendiente |
| H-API-11 | api | CORS con origen "*" y allow_credentials=True (adicional a RETO) | Vulnerabilidad | Con `allow_credentials=True`, Starlette 0.50.0 deja `preflight_explicit_allow_origin` en True, lo que hace que el preflight refleje el origen. El token viaja en cabecera, no en cookie, así que el riesgo directo es bajo. Comprobado en la asignación; no probado en ejecución. | api/app/main.py:48-54; api/.venv/Lib/site-packages/starlette/middleware/cors.py:36 | baja | Pendiente |
| H-API-12 | api | Calidad: dict sin modelos, excepciones amplias, condicionales anidados y duplicación (RETO: Calidad) | Refactor | Body y respuestas tipados como `dict[str, Any]`, aunque requirements.txt fija pydantic 2.13.4 sin usarlo para modelos. `except Exception` en auth y en health. `ifs` anidados en summary. Duplicación de la lista de columnas y de `owner or identity["username"]`. Constantes mágicas: HIGH, DONE, ARCHIVED, timeout 3.0. | api/app/main.py:25, 68, 78, 94, 101-104, 40-42, 69, 92, 116-120; api/requirements.txt:14 | baja | Pendiente |
| H-API-13 | api | POST /api/tasks no valida campos: acepta título vacío y estado o prioridad libres | Bug | Los valores por defecto se aplican con `payload.get`. No hay lista de estados ni prioridades, y el título vacío pasa la restricción NOT NULL. El resumen cuenta cualquier estado. Hecho por lectura. | api/app/main.py:79-84 | baja | Pendiente |
| H-API-14 | api | Conexión SQLite única compartida entre hilos sin lock | Bug | `check_same_thread=False` con una sola conexión que usan todas las rutas. Las rutas `def` de FastAPI pueden correr en hilos distintos. Posible error intermitente; no reproducido. Hipótesis. | api/app/main.py:35, 46 | baja | Pendiente |
| H-API-15 | api | Hook de cierre con on_event, obsoleto en FastAPI 0.128.2 | Refactor | La docstring instalada indica que `on_event` está obsoleto y recomienda lifespan. Riesgo bajo; afecta a una migración futura. | api/app/main.py:123-125; api/.venv/Lib/site-packages/fastapi/applications.py:4574 | baja | Pendiente |
| H-API-16 | api | backend-health devuelve 200 con status 0 al fallar, y crea un PoolManager por petición | Bug | Un fallo de conexión sale como HTTP 200 con status 0, que el cliente puede tomar por respuesta válida. Cada llamada crea un PoolManager sin cerrarlo. Timeout fijo 3.0 sin constante. | api/app/main.py:116-121 | baja | Pendiente |
| H-API-17 | api | Imagen Docker sin usuario no root | Vulnerabilidad | El Dockerfile no define `USER`; uvicorn corre como root dentro del contenedor. Riesgo bajo en laboratorio local. | api/Dockerfile:20-28 | baja | Pendiente |

Cobertura de RETO.md para `api`:
- SCA: H-API-08. Encontrado.
- Auth (token Base64): H-API-01. Encontrado.
- Autorización: H-API-05 y H-API-06. Encontrado.
- SQL (filtros e inserts): H-API-03 y H-API-02. Encontrado.
- Files: H-API-04 y H-API-07. Encontrado.
- Arquitectura: H-API-09. Encontrado.
- Calidad: H-API-12. Encontrado.

### Pruebas propuestas (no ejecutadas)

Todas usan datos ficticios en un directorio temporal, como `tmp_path` en `api/tests/test_api.py`. Los resultados esperados según código son hipótesis, no resultados. Ninguna prueba se ha pasado.

| Caso | Entrada | Resultado observado según código (hipótesis salvo indicación) | Hallazgo |
|---|---|---|---|
| Entrada normal | GET /api/tasks con token de student | 200 con 2 tareas. Ya cubierto por `test_lists_seeded_tasks` (api/tests/test_api.py:19-24) | Línea base |
| Comilla simple | GET /api/tasks?owner=O'Brien con token de student | Error SQL no controlado (500 o excepción en TestClient) | H-API-03 |
| Identificador ajeno | GET /api/tasks?owner=trainer con token de student | 200 con la tarea del owner trainer. Comportamiento esperado según el contrato acordado: pendiente | H-API-05 |
| Escritura con owner ajeno | POST /api/tasks con owner=trainer y token de student | 201 con owner trainer | H-API-06 |
| Comilla en el alta | POST /api/tasks con comilla simple en title | Error SQL no controlado | H-API-02 |
| Nombre con directorios | Subida con un nombre que incluya una secuencia de directorio, en un directorio temporal | Hipótesis: el archivo se escribe fuera de uploads/ | H-API-04 |
| Token con secreto distinto | Token con usuario ficticio y otro secreto | 401. Hoy solo `test_rejects_invalid_token` cubre el caso "invalid" (api/tests/test_api.py:54-58) | H-API-01 |

Cobertura actual de `tests/test_api.py`: cubre listado, alta con resumen, subida y token inválido. No cubre el parámetro `owner`, comillas, nombres de archivo ni límites de tamaño.

### Evidencia faltante y pendientes

- `docs/ENDPOINTS.md` no existe. Contrato observado derivado del código. Contrato acordado no registrado.
- `PLAN.md`, `docs/TESTING.md` y `prompts/` de la raíz no existen. No se crearon en este paso.
- `docs/prompts/api` está en `docs/prompts/`, no en `prompts/` de la raíz que pide README paso 3. No se movió.
- Git: el comando de estado y de archivos rastreados fue rechazado, así que no se verificó el estado del repositorio en esta sesión. Rama actual `main`. README pide `day1/prompting`; no se cambió de rama.
- Advisories de urllib3 2.5.0 y python-multipart 0.0.21: no consultados. H-API-08 queda provisional.
- Emisor del token en `auth/`: fuera de ámbito, no verificado. Cualquier cambio en el formato del token debe coordinarse con `auth` y con su consumidor (README, paso 6).
- Cobertura: no medida. Meta del laboratorio: 85% del código modificado. Ninguna cobertura medida todavía.
- Pruebas: no ejecutadas. Comandos propuestos: `python tools/lab.py test api` desde la raíz, o `python -m pytest` con cwd `api/`. Requiere el entorno preparado.
- Consumidor del frontend (`web/`): fuera de ámbito, no verificado.

### Seguimiento de ejecución (Lab paso 6, 2026-11-02)

- Aprobación: `PLAN.md` aprobado por una persona. Las decisiones pendientes de `PLAN.md` siguen abiertas; la aprobación no las resuelve.
- Implementado: H-API-02 y H-API-03. Consultas parametrizadas con `?` en `api/app/repository.py` (`TaskRepository`); `api/app/main.py:67-93` llama al repositorio. Sin cambios de rutas, claves de respuesta ni autorización.
- Bloqueado: H-API-01. Requiere `WORKSHOP_TOKEN_SECRET` en el contenedor de `api` (`compose.yaml`, raíz, fuera del ámbito de `api/`) y que `auth/` emita el formato firmado. Sin esos cambios, el login deja de validarse. Aprobados PyJWT y HS256; no implementado.
- Implementado 2026-11-02, aprobado con "apruebo el plan" (valores propuestos): H-API-04 (`api/app/uploads.py`: nombre normalizado, extensiones `.txt` y `.md`, 400 para carga inválida), H-API-05 y H-API-06 (`api/app/authorization.py`: ningún rol accede a owners ajenos, 403; `null` como ausente).
- Ejecución: `cd api && python -m pytest -p no:warnings --cov=app --cov-branch`: 39 passed. Antes del cambio de H-API-04/05/06: 30 passed, 1 xfailed. Pruebas que fijaban el comportamiento anterior actualizadas con su nuevo resultado (owner ajeno → 403, `.exe` → 400, traversal → nombre dentro de `uploads/`).
- Cobertura: `authorization.py` 100%; `uploads.py` 88% (sin cubrir: rama `filename` nulo y comprobación defensiva inalcanzable tras normalizar); `repository.py` 100%; `main.py` 93%, con líneas sin cubrir que no son de este cambio (`116->109`, `117->109` en el resumen; `131-136` en `backend_health`). Método: pytest-cov, por archivo; no es cobertura del diff.
- Sin commit. Pendiente: revisión humana del diff (paso 7), `docs/ENDPOINTS.md` (paso 8) y `docs/TESTING.md` (paso 9).
- H-API-02 sin modelo Pydantic `TaskCreate`: queda para H-API-12. El `null` como ausente (decisión 9, aprobada) está aplicado en `create_task` con `text_field` (`api/app/main.py`).
- Caracterización: `api/tests/test_characterization.py`. Fija el comportamiento actual de token, listado, resumen, alta y carga de archivos.
- Cambio de comportamiento intencional (previsto en el plan): con comilla en `owner`, listado y resumen pasan de 500 a 200 (lista o resumen vacíos); con comilla en `title`, el alta pasa de 500 a 201 con el texto literal.
- Hipótesis de H-API-04 confirmada en prueba local con directorio temporal: `"../escape.txt"` se escribe fuera de `uploads/`. La prueba está como `xfail` estricto (`test_upload_name_with_directory_does_not_escape_upload_dir`). Ejecución con `--runxfail`: `AssertionError: assert not True`. El hallazgo sigue "Pendiente" hasta su corrección.
- Ejecución: `cd api && python -m pytest -p no:warnings --cov=app --cov-branch`: 30 passed, 1 xfailed. Línea base previa al cambio: 4 passed.
- Cobertura: `app/repository.py` 100%; `app/main.py` 93% (branch). Total `app` 94%. Método: pytest-cov, sobre todo `app`. No es cobertura del diff. Las líneas sin cubrir (`app/main.py:102->95`, `103->95`, `116-121`) no son líneas modificadas; `backend_health` no tiene pruebas (H-API-10 y H-API-16). Cobertura del diff: no calculada con herramienta.
- Implementado 2026-11-02, H-API-07 (`api/app/uploads.py`): límite `MAX_UPLOAD_BYTES` de 5 MiB con lectura por bloques; 413 al superarlo, sin dejar archivo. Sobrescritura: creación exclusiva (`xb`), 409 si el nombre ya existe. Caracterización antes: 2 MiB → 201 y sobrescritura → 201. Después: 45 passed; `uploads.py` 93%. Limitación: el límite se aplica cuando el cuerpo ya llegó al servidor; no limita la transferencia. `storedAt` se mantiene como ruta absoluta (contrato).
- Sin commit. Pendiente: revisión humana del diff (paso 7) y `docs/TESTING.md` (paso 9).
