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
| H-API-18 | api | Límite de subida se aplica después de recibir y escribir el archivo completo (RETO: Files) | Vulnerabilidad | Starlette 0.50.0 escribe el archivo en disco temporal sin tope de tamaño antes de llamar al handler; el límite de 5 MiB se aplica después. Hecho por lectura; no medido. | api/app/uploads.py:41-47, 63; api/app/main.py:122; api/.venv/Lib/site-packages/starlette/formparsers.py:166-167, 209 | media | CANDIDATO |
| H-API-19 | api | Campos del body convertidos con `str()`: objetos y listas se guardan como repr de Python | Bug | `str(value)` acepta cualquier tipo JSON. Probado en directorio temporal: `title` `{"a": 1}` se guarda como `"{'a': 1}"`. No hay prueba para este caso. Relacionado con H-API-13, causa distinta. | api/app/main.py:23-30, 96-97; api/tests/test_characterization.py:155-157 | baja | CANDIDATO |
| H-API-20 | api | Pruebas usan el secreto literal de producción (AGENTS.md, api, regla 6) | Refactor | El literal de `LEGACY_SECRET` aparece en el código y en tres archivos de prueba. Con H-API-01, las pruebas no ejercitarían la configuración por entorno. | api/app/main.py:18; api/tests/test_api.py:11; api/tests/test_characterization.py:10; api/tests/test_endpoints.py:15 | media | CANDIDATO |

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

### Discovery 2026-11-03: candidatos nuevos (CANDIDATO)

- Fecha: 2026-11-03. Fase: Discovery, sin cambios de código ni commit. Alcance: `api`. Estado de partida: el código ya incluye los cambios de H-API-02 a H-API-07, así que los hallazgos se verifican contra el código actual.
- Método: lectura de código, `api/.venv/Lib/site-packages/starlette/formparsers.py` (starlette 0.50.0), `grep` y dos pruebas en un directorio temporal fuera del repositorio con `TestClient` (sin tocar archivos del repo). No se consultaron advisories.
- Todos los hallazgos de esta subsección están en estado CANDIDATO y esperan revisión humana.

### H-API-18: límite de subida aplicado después de recibir el archivo completo
- Estado: CANDIDATO
- Evidencia: `api/app/uploads.py:41-47` y `:63` limitan a 5 MiB dentro del handler. Antes, Starlette parsea el multipart: las partes de archivo se escriben en `SpooledTemporaryFile` sin tope (`api/.venv/Lib/site-packages/starlette/formparsers.py:166-167`, `:209`). `max_part_size` (línea 128) solo aplica a partes que no son archivo. Handler: `api/app/main.py:122`.
- Impacto: un cuerpo de cientos de MB se transfiere y se escribe en disco temporal antes de responder 413. Riesgo de llenar el disco y de consumir tiempo. H-API-07 anota esta limitación, pero figura como Resuelto; por eso se propone como hallazgo propio.
- Prioridad propuesta: media.
- Contrato potencialmente afectado: `POST /api/requirements` (413 "Upload too large" a partir de 5 MiB). El cambio afectaría al tope del cuerpo, no al valor de 5 MiB.
- Cómo validarlo: por lectura, el disco temporal crece con el tamaño del cuerpo antes de la respuesta 413. Para medirlo: subir un cuerpo de 100 MiB generado en streaming en un directorio temporal, registrar el crecimiento de `tempfile.gettempdir()` y el tiempo hasta el 413. Resultado que lo confirma: el crecimiento supera 5 MiB antes de la respuesta.

### H-API-19: campos del body convertidos con `str()`
- Estado: CANDIDATO
- Evidencia: `api/app/main.py:23-25` (`str(value)`) y `:28-30`, usados en `:96-97`. Prueba del 2026-11-03 en directorio temporal: `POST /api/tasks` con `{"title": {"a": 1}, "description": ["x", 2]}` responde 201 y guarda `"{'a': 1}"` y `"['x', 2]"`, que es repr de Python y no JSON. `api/tests/test_characterization.py:155-157` fija solo el caso numérico (`123` → `"123"`).
- Impacto: corrupción silenciosa de `title` y `description`; el frontend mostraría la repr. No altera los conteos del resumen. Impacto bajo.
- Prioridad propuesta: baja.
- Contrato potencialmente afectado: `POST /api/tasks`, tipos aceptados en `title` y `description`. Decisión pendiente: rechazar no escalares con 422 o conservar la coerción y documentarla.
- Cómo validarlo: `POST /api/tasks` con `{"title": {"a": 1}}` y luego `GET /api/tasks`. Hoy devuelve `"{'a': 1}"`. Tras la decisión, una prueba de caracterización del comportamiento elegido.
- Relación: H-API-13 (validación de campos). La causa es distinta: coerción, no validación de valores.

### H-API-20: pruebas usan el secreto literal de producción
- Estado: CANDIDATO
- Evidencia: `api/app/main.py:18` (`LEGACY_SECRET = "WORKSHOP_SECRET_2026"`). El mismo literal aparece en `api/tests/test_api.py:11`, `api/tests/test_characterization.py:10` y `api/tests/test_endpoints.py:15`. `grep -rn "WORKSHOP_SECRET_2026" api --include=*.py` (excluyendo `.venv`) da 4 coincidencias.
- Impacto: incumple la regla 6 del contrato de api de AGENTS.md ("No uses secretos reales ni valores de producción"). Si H-API-01 mueve el secreto a una variable de entorno (`WORKSHOP_TOKEN_SECRET`, ver la línea de implementación bloqueada de H-API-01), las pruebas seguirán firmando con el literal y no ejercitarán la configuración nueva. Un cambio de secreto obligaría a editar cuatro sitios.
- Prioridad propuesta: media.
- Contrato potencialmente afectado: formato del token `usuario|rol|expiración|secreto`, compartido con `auth/` (fuera de ámbito), y regla 6 del contrato de api.
- Cómo validarlo: `grep -rn "WORKSHOP_SECRET_2026" api --include=*.py` debe dar 0 coincidencias en las pruebas tras el cambio. Las pruebas deben pasar con un secreto de prueba inyectado por variable de entorno. Hoy: 4 coincidencias.

### H-API-21: subidas en un espacio de nombres global; el 409 bloquea el nombre a otros owners
- Estado: CANDIDATO
- Evidencia: `api/app/uploads.py:58-60` (`target = upload_dir / name`, sin owner en la ruta) y `:50-55` (creación exclusiva; `FileExistsError` → 409). `api/app/main.py:131` registra el owner solo en la respuesta. Prueba del 2026-11-03 en directorio temporal fuera del repo: `student` sube `requisitos.txt` → 201; `trainer` sube el mismo nombre → 409 `Upload conflict`.
- Impacto: un usuario puede bloquear de forma permanente un nombre de archivo a otro (no hay ruta de borrado) y el 409 revela que el archivo existe. Afecta a la identidad por usuario declarada en la regla 4 de AGENTS.md (api). No hay lectura de archivos por API, así que no hay fuga de contenido.
- Prioridad propuesta: media.
- Contrato potencialmente afectado: `POST /api/requirements` (201 y 409 introducidos por H-API-07) y `storedAt`. Decisión pendiente: subdirectorio por owner, prefijo por owner o aceptar nombres globales.
- Cómo validarlo: prueba con dos tokens (`student`, `trainer`) y el mismo nombre; hoy 201 y 409. Tras la decisión, caracterización del comportamiento elegido en `api/tests/test_uploads.py` o `test_characterization.py`.

### H-API-22: alta de tareas sin límite de tamaño en título, descripción ni cuerpo JSON
- Estado: CANDIDATO
- Evidencia: `api/app/main.py:94-102` (`payload: dict[str, Any] = Body(...)`, sin tope) y `:28-30` (`text_field`, sin longitud). `api/app/repository.py:16-22` inserta el valor completo. Prueba del 2026-11-03 en directorio temporal fuera del repo: `POST /api/tasks` con un título de 2 MiB responde 201 y guarda 2 097 152 caracteres.
- Impacto: cualquier usuario autenticado puede hacer crecer la base SQLite y la memoria por petición sin límite. No altera autorización ni los conteos. Es el equivalente en JSON de H-API-18 (multipart), pero sin la regla de 5 MiB de la subida.
- Prioridad propuesta: baja. Sube a media si la API sale del entorno de laboratorio.
- Contrato potencialmente afectado: `POST /api/tasks` (201 y forma del body). Decisión pendiente: límite de longitud por campo y tope del cuerpo, con qué valores y qué código de error (413 o 422).
- Cómo validarlo: `POST /api/tasks` con un título de 2 MiB; hoy 201. Tras la decisión, prueba de caracterización del límite elegido (valor en constante con nombre, sin número mágico).

### H-API-23: escritura de subida no atómica; un fallo a mitad deja un archivo que bloquea el nombre
- Estado: CANDIDATO
- Evidencia: `api/app/uploads.py:50-55` (`open("xb")` crea el archivo antes de escribir; solo se captura `FileExistsError`, sin borrado en fallo) y `:63-64`. `api/app/main.py:127-130` no limpia. Prueba del 2026-11-03 en directorio temporal, forzando un fallo de escritura con contenido que no es `bytes`: queda un archivo de 0 bytes y un segundo intento lanza `UploadConflictError` (409 en la API).
- Impacto: si la escritura falla (p. ej. disco lleno, supuesto no reproducido), el nombre queda ocupado con un archivo parcial y las subidas posteriores reciben 409 sin posibilidad de reintento. Combinado con H-API-21 y sin ruta de borrado, el bloqueo es permanente.
- Prioridad propuesta: baja. Hipótesis de causa real (disco lleno) no reproducida.
- Contrato potencialmente afectado: `POST /api/requirements` (500 en fallo, 409 después). Decisión pendiente: escribir en temporal y renombrar al final, o borrar el archivo ante error.
- Cómo validarlo: prueba que sustituye `write` por una función que lanza `OSError` y comprueba que no queda archivo en `uploads/` y que un segundo intento no devuelve 409. Hoy el segundo intento devuelve 409 (probado con `TypeError`).

### Correcciones propuestas a filas existentes (no modificadas)
- H-API-07, "La respuesta expone la ruta del servidor en `storedAt`": solo es una ruta absoluta si `WORKSHOP_UPLOAD_DIR` es absoluta. Con el valor por defecto `./uploads`, `str(Path("./uploads") / "a.txt")` devuelve `uploads\a.txt` en Windows (comprobado el 2026-11-03 con el Python del venv). Queda para decisión humana.

### Hipótesis descartada
- Nombre de archivo con NUL (`a\x00.txt`): llegó al servidor como `a%00.txt` y se guardó sin error ni 500. No se confirma como hallazgo.
- Nombre con `:` (p. ej. `a:b.txt`) como flujo de datos alterno de NTFS en Windows (2026-11-03): la API responde 400. `api/app/uploads.py:61-62` lo rechaza porque en Windows `a:b.txt` se resuelve con unidad y el padre no coincide con `uploads/`. No es hallazgo en Windows; en Linux (contenedor) el nombre sería válido y no se probó allí.

### Discovery 2026-11-03, segunda pasada: candidatos nuevos (CANDIDATO)

- Fecha: 2026-11-03. Fase: Discovery, sin cambios de código ni commit. Alcance: `api`. Solo se registran candidatos no cubiertos por H-API-01 a H-API-23.
- Método: lectura de `api/app/authorization.py`, `api/app/uploads.py` y `api/app/main.py`, y dos scripts en un directorio temporal fuera del repositorio (`/tmp/tmp.2TfrnCXb3w`), ejecutados con `api/.venv` y `-I`, con `TestClient(raise_server_exceptions=False)` salvo indicación. No se consultaron advisories ni se ejecutó `pytest`.
- Descartado en esta pasada: la comparación no constante de `LEGACY_SECRET` (`api/app/main.py:38`). Queda dentro de H-API-01, porque el secreto está en el código; no hay explotación demostrada por separado.

### H-API-24: nombres que el sistema de archivos rechaza producen 500 en lugar de 400
- Estado: CANDIDATO
- Evidencia: `api/app/uploads.py:32-38` (`safe_upload_name` no valida longitud ni caracteres) y `:50-55` (`write_new_file` solo captura `FileExistsError`). `api/app/main.py:123-130` mapea InvalidUpload, TooLarge y Conflict, pero no `OSError`. Prueba del 2026-11-03: `POST /api/requirements` con un nombre de 300 caracteres + `.txt` lanza `OSError [Errno 22]` no capturado (500 Internal Server Error); `a<b>.txt`, `a?.txt` y `a|b.txt` también dan 500. Control: `ok.txt` → 201. No queda archivo residual. Probado en Windows; en Linux no probado.
- Impacto: el cliente recibe 500 en vez del 400 "Invalid upload" previsto para nombres inválidos (`uploads.py:37` ya lo usa para extensiones no permitidas), y el servidor registra un traceback. No hay escritura parcial ni acceso indebido. Impacto bajo; afecta al contrato de errores.
- Prioridad propuesta: baja.
- Contrato potencialmente afectado: `POST /api/requirements`, códigos de error (400 para nombre inválido).
- Cómo validarlo: `TestClient` con un nombre de 300 caracteres + `.txt`: hoy 500. Tras la decisión, prueba de caracterización en `api/tests/test_uploads.py` o `test_endpoints.py` que espere 400. Conviene usar el nombre largo y no caracteres de Windows, porque el nombre largo también debería fallar en Linux (ENAMETOOLONG); eso aún no está verificado.

### H-API-25: token con usuario vacío crea tareas con owner vacío
- Estado: CANDIDATO
- Evidencia: `api/app/main.py:38` valida solo el número de campos, no que `parts[0]` (usuario) no esté vacío; `api/app/main.py:40` devuelve ese `username` vacío. `api/app/authorization.py:7` acepta owner "" como identidad propia. Prueba del 2026-11-03 con token `|STUDENT|<exp>|WORKSHOP_SECRET_2026`: `POST /api/tasks` → 201 con `owner: ""`; `GET /api/tasks` → 200 con `owner: ""`. Por lectura, `resolve_task_owner` (`authorization.py:9-11`) sigue rechazando con 403 los owners ajenos, así que no da acceso cruzado.
- Impacto: filas huérfanas con owner vacío en la base y en el resumen de ese identificador. Requiere conocer el secreto (depende de H-API-01). Impacto bajo; es un hueco de validación, como H-API-13.
- Prioridad propuesta: baja. Si H-API-01 se resuelve con PyJWT (aprobado en `PLAN.md`), el sujeto del token pasa a validarse allí y este candidato se cubre o se reemplaza.
- Contrato potencialmente afectado: formato del token `usuario|rol|expiración|secreto`, compartido con `auth/` (fuera de ámbito), y `POST /api/tasks` / `GET /api/tasks` (owner vacío no está definido en el contrato).
- Cómo validarlo: token con usuario vacío: hoy 201 y 200. Propuesta a decidir: rechazar con 401 un usuario vacío en `legacy_identity`. Tras la decisión, prueba de caracterización en `api/tests/test_api.py` con `make_token("")`.

### Implementación H-API-01 (2026-11-03): parcial, sin commit

- Rama local `day1/prompting`, creada desde `main`. Sin commit, sin push y sin PR (Día 1, README paso 10).
- Decisiones resueltas (de la arquitectura aprobada):
  1. Secreto legacy: variable `WORKSHOP_LEGACY_TOKEN_SECRET` decidida, **no implementada**. Requiere `compose.yaml`, fuera de `api/`. El literal se movió de `main.py` a `api/app/settings.py:11` (`LEGACY_TOKEN_SECRET`); no se eliminó.
  2. Flag `WORKSHOP_LEGACY_TOKENS_ENABLED` activo por defecto (`api/app/settings.py`).
  3. Sin `WORKSHOP_TOKEN_SECRET`: el token firmado se rechaza con 401; la API no falla al arrancar (`api/app/security.py`, `verify_jwt`).
  4. Claims: usuario en `sub`, rol en `role`. `iss` y `aud` vienen de `WORKSHOP_TOKEN_ISSUER` y `WORKSHOP_TOKEN_AUDIENCE`, con valores por defecto propuestos `workshop-auth` y `workshop-api`. No están acordados con `auth/`.
  5. Secreto compartido con `auth/`: pendiente del corte (paso 6 de la arquitectura).
  6. `create_app(db_path, upload_dir, token_settings=None)`: parámetro opcional; los tests existentes siguen llamando igual.
  7. PyJWT `2.15.1` fijado en `api/requirements.txt` e instalado en `api/.venv`. Advisories no consultados; H-API-08 sigue pendiente.
  8. `web/`: no verificado; sin cambios.
  9. ADR: no generado en esta pasada. La decisión de dispatch por forma y el flag son candidatas a ADR para revisión humana.
- Implementado:
  - `api/app/settings.py` (nuevo) y `api/app/security.py` (nuevo). El dispatch es por presencia de `.` en el token: JWT si la hay, legacy si no. Sin fallback: un JWT inválido no cae al formato legacy.
  - `api/app/main.py`: `legacy_identity` sustituido por `authenticated_identity`, que delega en `verify_authorization`; `LEGACY_SECRET` movido a `settings.py`.
  - `api/requirements.txt`: `PyJWT==2.15.1`.
  - `api/tests/test_characterization.py`: +1 caso fijado, `exp == ahora` se acepta en el formato legacy.
  - `api/tests/test_security.py` (nuevo): 24 pruebas de JWT, flag, dispatch y configuración.
  - `docs/ENDPOINTS.md`: dos ejemplos de 401 actualizados a `{"detail": "Invalid token"}`.
- Comportamiento cambiado: el 401 pasa de `"Invalid token: <error>"` a `"Invalid token"`. Las pruebas existentes usan `startswith("Invalid token")` y siguen en verde.
- Ejecución (`python tools/lab.py test api`):
  - Línea base antes del cambio: 65 passed, 1 xfailed; cobertura total 100 % (branch).
  - Después: 90 passed, 1 xfailed; cobertura total 100 % (branch), 0 líneas y 0 ramas sin cubrir en `app/`.
  - Cobertura del diff: como no hay líneas sin cubrir en `app/`, el diff también queda cubierto al 100 %. Método: pytest-cov con `--cov-branch`, sin herramienta de diff separada.
  - El xfail es preexistente: `test_endpoints.py:169`, H-API-04 (prefijo de unidad en Windows).
- Mutación (copia temporal fuera del repo, `pytest tests/test_security.py`):
  - Quitar la comprobación de `legacy_enabled`: falla `test_legacy_token_is_rejected_when_flag_is_off`.
  - Quitar `audience=`: falla la prueba de token válido, pero no la de audiencia incorrecta, porque PyJWT rechaza cualquier `aud` cuando no se pasa `audience`. Limitación: la prueba de audiencia no discrimina ese mutante.
- Criterios de la spec de H-API-01:
  - Cumplidos por prueba: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13.
  - Parcial: 14 (PyJWT fijado, sin `# noqa`; CodeQL y Dependency Review no se ejecutan en local) y 15 (método declarado arriba).
  - No cumplido: 11. El literal sigue en `api/app/settings.py:11`, `api/tests/test_api.py:11`, `api/tests/test_characterization.py:10` y `api/tests/test_endpoints.py:15`.
- Bloqueado: el paso 3 completo y el criterio 11 dependen de `compose.yaml`. Sin ese cambio, mover el secreto legacy a una variable rompería el login del despliegue actual, así que no se hizo.
- Riesgo residual: H-API-01 sigue abierto. El flag está activo por defecto y el literal sigue en el código. Nada emite JWT todavía, así que el despliegue actual no cambia salvo el texto del 401.
- Pendiente:
  - Revisión humana del diff.
  - `docs/ENDPOINTS.md`: la sección Autenticación sigue describiendo solo el formato legacy y citas a `main.py:18` y `main.py:33-42`, que están obsoletas.
  - Revisión de `web/`: uso del texto del 401 y de `Bearer`.
  - Advisories de PyJWT.
  - Commit tras la revisión humana.

### Pendiente
- Ejecución de `python -m pytest` en `api/` no se hizo en este paso. Cobertura: no medida.
- Revisión humana de H-API-18 a H-API-23 antes de cambiar estado o crear issues. No se creó ningún issue.
- Pruebas de 2026-11-03 (H-API-21 a H-API-23): scripts en un directorio temporal fuera del repositorio, ejecutados con `api/.venv` y `-I`. No se modificó ningún archivo de `api/`. `python -m pytest` no se ejecutó en este paso.
