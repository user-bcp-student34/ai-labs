# Plan de correcciones

Plan de los hallazgos de `HALLAZGOS.md` del componente `api`. Cada hallazgo se corrige en un cambio aparte y revisable. Los estados siguen en `HALLAZGOS.md`.

> Recreado el 2026-11-02. `PLAN.md` no existía en la raíz al inicio de esta ejecución. Se reconstruyó desde la última versión de la sesión, incluido el registro de ejecución. Revisar si hubo borrado intencional.

## api

### Alcance y contratos

- Hallazgos cubiertos: H-API-01 a H-API-07. H-API-01 y H-API-07 figuran como "Aprobado" en `HALLAZGOS.md` (2026-11-02). Fuera de este plan: H-API-08 (SCA), H-API-09 (arquitectura), H-API-10 a H-API-17.
- `docs/ENDPOINTS.md` no existe. El contrato a preservar es el comportamiento observado en el código, citado en `HALLAZGOS.md` (sección "Contrato observado").
  - `GET /api/tasks`, `POST /api/tasks`, `GET /api/summary`, `POST /api/requirements`, `GET /api/backend-health`, con los mismos códigos de éxito (200, 201) y las mismas claves de respuesta.
  - Query `owner` opcional; body JSON con `title`, `description`, `status`, `priority`, `owner`.
  - Token en `Authorization`, formato `usuario|rol|expiración|secreto` en Base64.
- Módulos: `app/main.py` conserva `create_app` y `app` (`Dockerfile`: `app.main:app`; `tests/test_api.py`: `from app.main import create_app`).
- Convenciones de `docs/GOOD_PRACTICES.md` (api): modelos Pydantic, capas ruta/servicio/repositorio, autorización por identidad, SQL parametrizado, `HTTPException` con mensajes genéricos, archivos normalizados antes de guardar.
- Dependencias: Pydantic y FastAPI ya instalados. Única dependencia nueva propuesta: PyJWT para H-API-01.

| Módulo | Responsabilidad | Hallazgo | Estado |
|---|---|---|---|
| `app/settings.py` | Variables de entorno (secreto, rutas, URL de auth) | H-API-01 | Pendiente (bloqueado) |
| `app/security.py` | Verificación del token y devolución de identidad | H-API-01 | Pendiente (bloqueado) |
| `app/repository.py` | Acceso a SQLite con `?` | H-API-02, H-API-03 | Creado |
| `app/authorization.py` | Regla única de dueño permitido | H-API-05, H-API-06 | Creado |
| `app/uploads.py` | Nombre seguro y guardado | H-API-04 | Creado |

### Orden de trabajo

1. Pruebas de caracterización de cada ruta, antes de tocar el código.
2. H-API-03 y H-API-02: parametrizar SQL. Hecho.
3. H-API-04: carga segura de archivos. Hecho.
4. H-API-05 y H-API-06: autorización por identidad. Hecho.
5. H-API-01: token verificado. Bloqueado por `compose.yaml` y por `auth/`.

### H-API-01 — Token Base64 con secreto embebido

- **Estado:** Aprobado en `HALLAZGOS.md` (2026-11-02). No implementado: bloqueado por `compose.yaml` y `auth/` (ver "Bloqueo" más abajo).
- **Hallazgo:** Token Base64 con secreto embebido, sin firma, emisor ni audiencia (RETO: Auth). Severidad alta.
- **Problema:** `api/app/main.py:18` guarda el secreto en el código. `api/app/main.py:33-42` decodifica Base64, separa por `|` y compara el cuarto campo con esa constante (L38). La expiración se comprueba en la misma condición, pero no hay firma, `iss` ni `aud`. El rol devuelto no se valida. Quien tenga el código puede crear un token para cualquier usuario y rol.
- **Cambio propuesto:**
  - Secreto en `app/settings.py`, leído de `WORKSHOP_TOKEN_SECRET`. Sin valor por defecto real.
  - `verify_token` en `app/security.py`: verifica HS256, `exp`, `iss` y `aud` con PyJWT. Algoritmos fijados (`algorithms=["HS256"]`). Devuelve `Identity(username, role)`.
  - Error `HTTPException(401, detail="Invalid token")`, sin `str(error)`.
  - Transición: flag `WORKSHOP_LEGACY_TOKENS_ENABLED`, activo por defecto mientras `auth/` no emita JWT. Aprobado con "apruebo el plan".
- **Archivos a tocar:** `api/app/settings.py` y `api/app/security.py` (nuevos); `api/app/main.py` (quitar `LEGACY_SECRET` L18 y `legacy_identity` L33-42); `api/requirements.txt` (PyJWT); `api/tests/test_api.py` L10-12; `api/tests/conftest.py` (nuevo). Fuera de `api/`: `compose.yaml` (raíz) y `auth/`.
- **Caracterización previa** (`api/tests/test_characterization.py`, escrita y en verde sobre el código actual):
  - Token sin prefijo `Bearer` → 200.
  - Token con otro secreto → 401.
  - Token expirado → 401.
  - Campo `role` no altera el listado.
  - Pendiente: el caso de `exp` igual a la hora actual, que necesita reloj fijo.
- **Clean Code:** `verify_token` hace una sola cosa. Constantes con nombre: `TOKEN_ALGORITHM = "HS256"`, `TOKEN_PREFIX = "Bearer "`.
- **Bloqueo:** `WORKSHOP_TOKEN_SECRET` debe llegar al contenedor de `api`, lo que exige cambiar `compose.yaml` (raíz, fuera del ámbito de `api/`). Además, `auth/` debe emitir el formato firmado. Sin ambos, el login deja de validarse.

### H-API-02 — INSERT de tareas construido por concatenación

- **Estado:** Resuelto (2026-11-02). Revisión humana pendiente.
- **Hallazgo:** INSERT de tareas construido por concatenación (RETO: SQL). Severidad alta.
- **Problema:** `api/app/main.py:79-85` pegaba `title`, `description`, `status`, `owner` y `priority` del body dentro del texto SQL. Una comilla simple rompía la sentencia. El SELECT por id (`api/app/main.py:87`, con `lastrowid`) seguía el mismo patrón.
- **Cambio propuesto:** `TaskRepository.insert` y `TaskRepository.get` con `?` y tupla de parámetros (`app/repository.py`). Null como ausente con `text_field` y `optional_text_field` (`api/app/main.py`), aprobado como decisión 9.
- **Cambio aplicado:**
  - `api/app/repository.py` (nuevo): `insert(title, description, status, owner, priority) -> int` y `get(task_id)`, ambos con `?`.
  - `api/app/main.py`: `create_task` llama al repositorio. Los campos usan `text_field` con constantes `DEFAULT_TASK_STATUS = "TODO"` y `DEFAULT_TASK_PRIORITY = "MEDIUM"`.
- **Desviación:** no se creó `app/schemas.py` ni `TaskCreate`. El modelo Pydantic queda para H-API-12.
- **Caracterización** (`test_characterization.py`):
  - Antes: comilla en `title` → 500; `title: null` → `"None"`.
  - Después: comilla → 201 con texto literal; `null` → `""`; owner `null` → `student`.
  - Resultado: 30 passed, 1 xfailed, tras actualizar las dos pruebas que fijaban `"None"` (2 fallos antes de actualizarlas).
- **Clean Code:** cada función hace una cosa. Defaults como constantes, sin literales repetidos.

### H-API-03 — Filtro por owner concatenado en listado y resumen

- **Estado:** Resuelto (2026-11-02). Revisión humana pendiente.
- **Hallazgo:** Filtro por owner concatenado en `GET /api/tasks` y `GET /api/summary` (RETO: SQL). Severidad alta.
- **Problema:** `api/app/main.py:70` y `api/app/main.py:93` pegaban el valor de `owner` en `WHERE owner='...'`.
- **Cambio propuesto:** `list_by_owner` y `status_and_priority_by_owner` con `WHERE owner = ?`. La comparación es exacta, sin `LIKE`.
- **Cambio aplicado:** `api/app/repository.py`, con `TASK_COLUMNS` como constante compartida. Las columnas se interpolan con f-string desde esa constante fija, no desde entrada del cliente; se deja señalado para revisión humana.
- **Desviación:** nombres de métodos ligeramente distintos al plan original (`status_and_priority_by_owner`, `insert(title, ...)`).
- **Caracterización:**
  - Antes: `owner=O'Brien` → 500 en listado y resumen.
  - Después: 200 con lista y resumen vacíos. Las semillas no cambian: student 2 tareas, total 2, `highPriorityOpen` 1; trainer 1 tarea.
- **Clean Code:** la consulta vive en el repositorio; la ruta solo llama y responde.

### H-API-04 — Nombre de archivo sin normalizar

- **Estado:** Resuelto (2026-11-02). Revisión humana pendiente.
- **Hallazgo:** `upload_requirement` usa `file.filename` sin normalizar (RETO: Files). Severidad alta.
- **Problema:** `api/app/main.py:109` unía `Path(uploads) / file.filename`. `python_multipart/multipart.py:1688-1690` (dependencia instalada) entrega el nombre sin quitar directorios. Hipótesis confirmada en un experimento local: `../escape.txt` escribía fuera de `uploads/`.
- **Cambio aplicado:**
  - `api/app/uploads.py` (nuevo): `safe_upload_name` toma el último segmento (separadores `/` y `\`). Rechaza vacío, `.` y `..`, y exige extensión en `ALLOWED_UPLOAD_EXTENSIONS = {".txt", ".md"}`. `save_requirement` comprueba además que el destino quede dentro de `upload_dir`.
  - `api/app/main.py`: la ruta convierte `InvalidUploadError` en 400 con `Invalid upload`.
  - `fileName` devuelve el nombre normalizado (decisión 8, aprobada con "apruebo el plan").
- **Caracterización:**
  - Antes: `../escape.txt` escribía fuera de `uploads/` (`xfail` estricto confirmado con `--runxfail`). `.exe` daba 201.
  - Después: `escape.txt` queda dentro de `uploads/` con 201. `.exe`, `..`, `carpeta/`, `sin_extension` dan 400. Separador `\` también queda dentro.
- **Clean Code:** `safe_upload_name` usa cláusulas de guarda. Extensiones como constante con nombre.

### H-API-05 — Autorización por parámetro del cliente

- **Estado:** Resuelto (2026-11-02). Revisión humana pendiente.
- **Hallazgo:** Autorización por parámetro del cliente en listado y resumen (RETO: Autorización). Severidad alta.
- **Problema:** `api/app/main.py:68-69` y `api/app/main.py:91-92` usaban `owner or identity["username"]`. Un usuario autenticado leía tareas de otro owner. El rol no se comprobaba.
- **Cambio aplicado:** `resolve_task_owner(identity, requested_owner)` en `api/app/authorization.py` (nuevo):
  1. Vacío o igual al usuario del token → usuario del token.
  2. Rol en `ROLES_WITH_CROSS_OWNER_ACCESS` → owner pedido.
  3. Otro caso → 403 `Forbidden`.
  `ROLES_WITH_CROSS_OWNER_ACCESS` queda vacío (decisión 5: nadie accede a owners ajenos).
- **Caracterización:**
  - Antes: `owner=trainer` → 200 con tareas de trainer.
  - Después: 403. Un test con el conjunto temporalmente configurado comprueba que la regla de rol funciona.
- **Clean Code:** una sola función decide el dueño. Las rutas solo la llaman.

### H-API-06 — Escritura con owner elegido por el cliente

- **Estado:** Resuelto (2026-11-02). Revisión humana pendiente.
- **Hallazgo:** Escritura de tareas con owner elegido por el cliente (RETO: Autorización). Severidad alta.
- **Problema:** `api/app/main.py:83-84` usaba `payload.get("owner", identity["username"])`. Un usuario creaba tareas en la lista de otro. `owner: null` producía `"None"`.
- **Cambio aplicado:** `api/app/main.py` `create_task` usa `resolve_task_owner(identity, optional_text_field(payload, "owner"))`. Owner ajeno → 403 y no se crea la tarea. `null` → token.
- **Desviación:** owner vacío `""` en el alta ahora se trata como ausente y guarda el owner del token. Antes guardaba `""`. Sigue la regla de `resolve_task_owner`; el plan no lo nombraba para el alta.
- **Caracterización:**
  - Antes: `owner: trainer` como student → 201 con owner trainer.
  - Después: 403; la tarea de trainer no cambia.

### H-API-07 — Subida sin límite de tamaño ni de extensión, y sobrescritura silenciosa

- **Estado:** Resuelto (2026-11-02). Revisión humana pendiente. Aprobado en `HALLAZGOS.md` antes de implementar.
- **Hallazgo:** Subida sin límite de tamaño ni de extensión, y sobrescritura silenciosa (RETO: Files). Severidad media.
- **Problema:**
  - `api/app/uploads.py:34`: `await file.read()` carga el archivo completo en memoria, sin tope.
  - `api/app/uploads.py:35`: `write_bytes` reemplaza un archivo existente con el mismo nombre, sin aviso.
  - `api/app/main.py:127`: `storedAt` devuelve la ruta absoluta del servidor. Es un riesgo de exposición, pero forma parte del contrato observado.
  - La extensión ya queda validada desde H-API-04 (`api/app/uploads.py:6`, `ALLOWED_UPLOAD_EXTENSIONS`). No se repite aquí.
- **Cambio propuesto:**
  - Límite de tamaño: `MAX_UPLOAD_BYTES` en `app/uploads.py`. Valor propuesto 5 MiB, escrito como `MAX_UPLOAD_MIB = 5` y `BYTES_PER_MIB = 1024 * 1024`, para no dejar números mágicos. El valor es una decisión pendiente (ver abajo).
  - Lectura por bloques: `read_limited(file, limit)` lee `UPLOAD_CHUNK_BYTES` (64 KiB) en un bucle. Si el total supera el límite, lanza `UploadTooLargeError` y deja de leer. Así no se carga un archivo grande entero.
  - `api/app/main.py`: `UploadTooLargeError` → 413 con `Upload too large`.
  - Sobrescritura: si el destino ya existe, `save_requirement` lanza `UploadConflictError` y no escribe. `api/app/main.py` la convierte en 409 con `Upload conflict`. Alternativa descartada: nombre único, que cambia el valor de `storedAt`, parte del contrato observado.
  - `storedAt` se mantiene como ruta absoluta mientras no se apruebe el cambio de contrato. Queda registrado como riesgo.
  - Sin dependencias nuevas.
- **Archivos a tocar:** `api/app/uploads.py` (constantes, `read_limited`, comprobación de existencia, excepciones nuevas); `api/app/main.py` (mapear 413 y 409); `api/tests/test_characterization.py` (pruebas nuevas).
- **Caracterización previa** (`api/tests/test_characterization.py`, sobre el código actual):
  - C1: archivo de 2 MiB → 201 hoy, porque no hay límite. Se fija con un tamaño moderado, que sigue dando 201 con el límite propuesto.
  - C2: segunda subida con el mismo nombre → 201 y contenido reemplazado. Se fija como comportamiento actual; tras el cambio, 409 y contenido original intacto.
  - C3: `size` igual a `len(content)` y `storedAt` igual a la ruta del destino. Son contrato; no cambian.
  - C4: `fileName` igual al nombre original. Ya cubierto por `test_upload_keeps_plain_name`.
- **Pruebas de corrección** (después del cambio):
  - Archivo exactamente en el límite → 201.
  - Archivo un byte por encima del límite → 413, y el archivo no existe en disco.
  - Segunda subida con el mismo nombre → 409, y contenido original intacto.
  - Para no crear archivos de 5 MiB en las pruebas, el límite se cambia con `monkeypatch` sobre `app.uploads.MAX_UPLOAD_BYTES`.
- **Clean Code:** `read_limited` hace una sola cosa. `save_requirement` solo orquesta: valida el nombre, comprueba existencia, lee con límite y escribe. Sin anidamiento: cláusulas de guarda. Constantes con nombre, sin números sueltos.
- **Decisiones pendientes para H-API-07:**
  - Valor del límite (propuesto 5 MiB).
  - Sobrescritura: 409 (propuesto) frente a nombre único.
  - `storedAt`: mantener la ruta absoluta (propuesto) frente a una ruta relativa, que sería un cambio de contrato.

### Decisiones registradas (2026-11-02)

Aprobadas con "apruebo el plan" (valores propuestos; la segunda ronda de preguntas fue cancelada):
- Decisión 1 y 2: PyJWT y HS256 aprobados. Falta coordinar con `auth/` antes del corte.
- Decisión 3: flag `WORKSHOP_LEGACY_TOKENS_ENABLED` activo por defecto.
- Decisión 4: token sin prefijo `Bearer` aceptado.
- Decisión 5: ningún rol accede a owners ajenos.
- Decisión 6: extensiones `.txt` y `.md`.
- Decisión 7: 400 para carga inválida, 403 para owner ajeno.
- Decisión 8: `fileName` normalizado.
- Decisión 9: `null` como ausente.

### Pendientes de coordinación

- `auth/` (fuera de ámbito): emitir el formato firmado antes del corte de H-API-01.
- `compose.yaml` (raíz, fuera de ámbito): pasar `WORKSHOP_TOKEN_SECRET` a `api`.
- `web/` (fuera de ámbito): confirmar si usa `owner` en las consultas de tareas, y si envía `Bearer`.

### Ejecución

Comandos (cwd `api/`): `.venv/Scripts/python.exe -m pytest -p no:warnings --cov=app --cov-branch`.

| Fecha | IDs | Ejecución | Resultado |
|---|---|---|---|
| 2026-11-02 | H-API-02, H-API-03 | Línea base antes de cambiar (`tests/test_api.py`) | 4 passed |
| 2026-11-02 | H-API-02, H-API-03 | Caracterización escrita antes del cambio | 26 passed, 1 xfailed |
| 2026-11-02 | H-API-02, H-API-03 | Tras parametrizar; tres pruebas con comilla actualizadas | 30 passed, 1 xfailed |
| 2026-11-02 | H-API-02 (null) | Decisión 9 aplicada | 2 fallos antes de actualizar pruebas; luego 30 passed, 1 xfailed |
| 2026-11-02 | H-API-04, H-API-05, H-API-06 | Tras cambio de rutas, antes de ajustar pruebas | 9 fallos, todos por comportamiento aprobado que cambia |
| 2026-11-02 | H-API-04, H-API-05, H-API-06 | Tras ajustar pruebas | 38 passed |
| 2026-11-02 | H-API-05 | Prueba de regla de rol configurado (monkeypatch) | 39 passed |

Cobertura (por archivo, no del diff):
- `app/authorization.py` 100%.
- `app/uploads.py` 88%. Sin cubrir: `filename` nulo (L22) y comprobación defensiva (L33), inalcanzable tras normalizar.
- `app/repository.py` 100%.
- `app/main.py` 93%. Sin cubrir, fuera de este cambio: resumen (`116->109`, `117->109`) y `backend_health` (`131-136`).
- Método: pytest-cov por archivo. No es cobertura del diff; no se calculó con herramienta.

Desviaciones globales:
- `app/schemas.py` y `TaskCreate` no se crearon (H-API-02). Pendientes para H-API-12.
- `HTTPException` directo en `authorization.py`, no `ForbiddenError` (H-API-05/06).
- Owner vacío en el alta se trata como ausente (H-API-06).

### Ejecución 2026-11-02: H-API-07

Comandos (cwd `api/`): `.venv/Scripts/python.exe -m pytest -p no:warnings --cov=app --cov-branch`.

| Fase | IDs | Resultado |
|---|---|---|
| Caracterización escrita y ejecutada antes del cambio (3 pruebas nuevas) | H-API-07 | 42 passed |
| Tras el cambio, antes de ajustar la prueba de sobrescritura | H-API-07 | 1 fallo: la sobrescritura da 409, como se planeó |
| Tras ajustar la prueba y añadir pruebas de corrección (límite exacto, sobre el límite, lectura por bloques) | H-API-07 | 45 passed |

Cobertura por archivo:
- `app/uploads.py` 93%. Sin cubrir: nombre nulo y comprobación defensiva de `save_requirement`.
- `app/main.py` 94%. Sin cubrir, fuera de este cambio: resumen y `backend_health`.
- Método: pytest-cov por archivo. No es cobertura del diff.

Desviaciones de este tramo:
- La sobrescritura usa creación exclusiva (`open("xb")`) en lugar de comprobar la existencia antes de escribir. Así no hay carrera entre comprobar y escribir.
- El límite se aplica al leer el archivo, cuando el cuerpo multipart ya llegó al servidor. No reduce la transferencia ni el uso temporal que Starlette hace al recibir el cuerpo. Riesgo residual registrado; un tope en la capa de red queda fuera de este cambio.
- `storedAt` se mantiene como ruta absoluta, porque forma parte del contrato. Sigue el riesgo de exposición que describe H-API-07.
- Errores nuevos: 413 `Upload too large` y 409 `Upload conflict`, con mensajes genéricos.

H-API-01 sigue bloqueado, sin cambios en esta ronda.

### Documentación de contrato (2026-11-02)

- `docs/ENDPOINTS.md` creado (componente `api`). Se ejecutaron 27 escenarios manuales de la API con datos ficticios en un directorio temporal, sin modificar `api/`. Cubren 200, 201, 400, 401, 403, 409, 413 y 422. Los cuerpos de respuesta del documento salen de esa ejecución.
- Sin verificar: `GET /api/backend-health` con el servicio de autenticación en marcha. Solo se probó con el servicio caído.
- IDs relacionados: H-API-01, H-API-04, H-API-05, H-API-06, H-API-07, H-API-10, H-API-11, H-API-13 y H-API-16.

### Pruebas de contrato (2026-11-02)

- Componente `api`, IDs H-API-02 a H-API-07, H-API-04 (xfail), H-API-16 y H-API-10 (verificación parcial).
- Se añadieron `api/tests/test_endpoints.py` (19 funciones, 1 `xfail`) y `api/tests/test_uploads.py` (2 funciones). Cubren cada endpoint de `docs/ENDPOINTS.md`: caso feliz, casos borde y cada error documentado.
- Ejecución: `api/`, `.venv/Scripts/python.exe -m pytest -p no:warnings --cov=app --cov-branch`. Código de salida 0; 65 passed, 1 xfailed.
- Cobertura del diff (líneas añadidas en `git diff -U0 main -- api/app`): 102/102 = 100%. Cobertura global `app`: 179/179 líneas, 30 ramas, 0 parciales.
- Defectos documentados en `docs/TESTING.md`, sección "Fallos y defectos": H-API-04 (prefijo de unidad), H-API-16 (200 con status 0) y H-API-10 (texto interno en el 401).
- Resultado detallado y pendientes: ver `docs/TESTING.md`.

Pendiente, sin ejecutar: revisión humana del diff (paso 7 del Lab) y `python tools/lab.py test api` desde la raíz.
