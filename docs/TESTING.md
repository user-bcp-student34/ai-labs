# Pruebas de `api/`

Documento de pruebas del componente `api`. Relaciona cada caso con el endpoint de `docs/ENDPOINTS.md` y cada ejecución con sus IDs de `HALLAZGOS.md`. Las pruebas no se han modificado para pasar: los defectos conocidos aparecen en "Fallos y defectos", con su ID.

## Entorno y comandos

- Directorio de ejecución: `api/`.
- Intérprete: `api/.venv/Scripts/python.exe`, Python 3.13.15 (Windows).
- Herramientas: pytest 9.0.2 y pytest-cov 7.0.0 (`api/requirements-dev.txt`), coverage.py con medición de ramas (`api/pyproject.toml`: `branch = true`, `source = ["app"]`).
- Comando de pruebas: `.venv/Scripts/python.exe -m pytest -p no:warnings`.
- Comando con cobertura: `.venv/Scripts/python.exe -m pytest -p no:warnings --cov=app --cov-branch --cov-report=term-missing`.
- Comando del laboratorio, desde la raíz: `python tools/lab.py test api`. No se ejecutó en esta sesión; ver "Pendientes".
- Archivos de prueba: `api/tests/test_api.py` (4 funciones, de la plantilla), `api/tests/test_characterization.py` (38 funciones), `api/tests/test_endpoints.py` (19 funciones, 1 de ellas `xfail`), `api/tests/test_uploads.py` (2 funciones).

## Casos por endpoint

Tipos: **feliz** (dato válido), **borde** (payload vacío, archivo sin extensión, límite de tamaño), **error** (respuesta de error documentada) y **autorización** (un usuario no lee ni escribe tareas de otro owner). "Resultado esperado" sale de `docs/ENDPOINTS.md`.

### GET /api/tasks

| Caso | Tipo | Resultado esperado | Prueba |
|---|---|---|---|
| Lista las tareas del usuario del token | feliz | 200 con las 2 semillas de `student` | `test_lists_seeded_tasks` (test_api.py), `test_list_without_owner_returns_identity_tasks` |
| Cada tarea tiene exactamente los campos documentados | feliz | `id`, `title`, `description`, `status`, `owner`, `priority`, `createdAt` | `test_list_items_have_exactly_the_documented_fields` |
| `owner` vacío equivale a no enviarlo | borde | 200 con las tareas del token | `test_list_with_empty_owner_falls_back_to_identity` |
| `owner` igual al usuario del token | feliz | 200 con sus tareas | `test_list_with_own_owner_returns_own_tasks` |
| Usuario sin tareas | borde | 200 con `[]` | `test_list_for_user_without_tasks_returns_empty_list` |
| Sin cabecera `Authorization` | error | 401, `detail` empieza por `Invalid token` | `test_list_without_authorization_header_returns_401_with_detail` |
| Token sin el formato `usuario\|rol\|exp\|secreto` | error | 401 | `test_rejects_invalid_token` |
| Token firmado con otro secreto | error | 401 | `test_token_signed_with_other_secret_is_rejected` |
| Token expirado | error | 401 | `test_expired_token_is_rejected` |
| Un usuario no lee tareas de otro owner | autorización | 403 con `{"detail": "Forbidden"}`, sin datos | `test_list_with_other_owner_returns_403_without_data`, `test_list_with_other_owner_is_forbidden` |
| El rol del token no cambia la lista | autorización | Mismo resultado con rol `STUDENT` y `TRAINER` | `test_role_claim_does_not_change_own_task_list` |
| Owner ajeno, incluso con rol `TRAINER` | autorización | 403 | `test_list_with_other_owner_is_forbidden_for_trainer_role` |

### POST /api/tasks

| Caso | Tipo | Resultado esperado | Prueba |
|---|---|---|---|
| Alta con todos los campos | feliz | 201 con los valores enviados y `owner` del token | `test_create_returns_all_submitted_fields`, `test_creates_task_and_summary` |
| Alta solo con título y descripción | feliz | 201 con `status` `TODO`, `priority` `MEDIUM`, `owner` del token | `test_create_applies_default_status_priority_and_owner` |
| Payload vacío `{}` | borde | 201 con título y descripción `""` | `test_create_missing_title_and_description_store_empty_text` |
| `title` `null` | borde | 201 con `title` `""` | `test_create_null_title_is_treated_as_missing` |
| `owner` `null` | borde | 201 con el owner del token | `test_create_with_null_owner_uses_token_owner` |
| `title` numérico | borde | 201 con `title` `"123"` | `test_create_coerces_numeric_title_to_text` |
| Comilla simple en el título | borde | 201 con el texto literal | `test_create_with_quote_in_title_stores_literal_text` |
| Sin cabecera `Authorization` | error | 401 | `test_create_without_authorization_header_returns_401` |
| Cuerpo JSON que no es objeto (`[1]`) | error | 422 con `type` `dict_type` | `test_create_with_array_body_returns_422` |
| Sin cuerpo | error | 422 con `type` `missing` | `test_create_without_body_returns_422` |
| `owner` de otro usuario | autorización | 403 y la tarea no se crea | `test_create_with_other_owner_is_forbidden` |
| Intento de escribir en la lista de otro, comprobado después | autorización | La lista del otro owner no cambia | `test_create_with_other_owner_leaves_other_list_unchanged` |

### GET /api/summary

| Caso | Tipo | Resultado esperado | Prueba |
|---|---|---|---|
| Resumen de `student` con las semillas | feliz | 200 con `total` 2, `byStatus` `{"TODO": 1, "IN_PROGRESS": 1}`, `highPriorityOpen` 1 | `test_summary_for_student_matches_seeds` |
| Resumen de `trainer` | feliz | 200 con `total` 1 | `test_summary_for_trainer_matches_seeds` |
| Usuario sin tareas | borde | 200 con `total` 0, `byStatus` `{}`, `highPriorityOpen` 0 | `test_summary_for_user_without_tasks_is_empty` |
| `HIGH` con `DONE` o `ARCHIVED` no cuenta como abierta | borde | `highPriorityOpen` 0 | `test_summary_excludes_high_priority_done_and_archived_tasks` |
| `HIGH` abierta cuenta | feliz | `highPriorityOpen` 1 | `test_summary_counts_high_priority_open_task` |
| Sin cabecera `Authorization` | error | 401 | `test_summary_without_authorization_header_returns_401` |
| Owner de otro usuario | autorización | 403 con `{"detail": "Forbidden"}` | `test_summary_with_other_owner_is_forbidden` |
| Nombre de usuario con comilla simple | borde | 200 con resumen vacío, sin error SQL | `test_summary_with_quote_in_own_username_returns_empty_summary` |

### POST /api/requirements

| Caso | Tipo | Resultado esperado | Prueba |
|---|---|---|---|
| Subida de `requirements.txt` | feliz | 201 con `owner`, `fileName`, `storedAt`, `size` | `test_upload_keeps_plain_name`, `test_uploads_requirement` |
| Nombre con espacios | feliz | 201 con el mismo nombre | `test_upload_keeps_name_with_spaces` |
| Sin campo `file` (payload vacío) | borde | 422 con `loc` `["body", "file"]` | `test_upload_without_file_field_returns_422` |
| Archivo sin extensión (`sin_extension`) | borde | 400 con `Invalid upload` | `test_upload_rejects_unsafe_or_unlisted_names[sin_extension]` |
| Nombre `..` y nombre que termina en `/` | borde | 400 | `test_upload_rejects_unsafe_or_unlisted_names[..]`, `[carpeta/]` |
| Extensión engañosa `nota.TXT.exe` | borde | 400 | `test_upload_rejects_unsafe_or_unlisted_names[nota.TXT.exe]` |
| Nombre con directorios (`../escape.txt`) | borde | 201, nombre normalizado, fuera de `uploads/` no se escribe | `test_upload_name_with_directory_stays_in_upload_dir` |
| Separador Windows (`..\escape.md`) | borde | 201, nombre normalizado | `test_upload_name_with_windows_separator_stays_in_upload_dir` |
| Archivo exactamente en 5 MiB | borde (límite) | 201 con `size` 5 242 880 | `test_upload_exactly_at_real_size_limit_is_accepted` |
| Archivo de 5 MiB + 1 byte | borde (límite) | 413 con `Upload too large`, sin archivo en disco | `test_upload_one_byte_over_real_size_limit_returns_413_and_stores_nothing` |
| Límite pequeño con `monkeypatch` | borde (límite) | 201 en el límite, 413 por encima | `test_upload_at_size_limit_is_accepted`, `test_upload_over_size_limit_is_rejected_and_not_stored` |
| Lectura por bloques sin perder bytes | borde | 201 con contenido íntegro | `test_upload_is_read_in_chunks_without_losing_bytes` |
| Extensión fuera de la lista (`tool.exe`) | error | 400 con `{"detail": "Invalid upload"}` | `test_upload_rejects_extension_outside_allowlist` |
| Nombre que ya existe | error | 409 con `Upload conflict`, contenido original intacto | `test_upload_same_name_is_rejected_and_keeps_original` |
| Sin cabecera `Authorization` | error | 401 | `test_upload_without_token_is_rejected` |
| Nombre nulo | error | `InvalidUploadError` (unidad) | `test_safe_upload_name_rejects_missing_filename` |
| Destino fuera del directorio de subida | error | `InvalidUploadError`, sin escribir | `test_save_requirement_rejects_target_outside_upload_dir` |
| Prefijo de unidad (`C:x.txt`), solo Windows | error conocido | Esperado: `fileName` igual al archivo guardado | `test_upload_name_with_drive_prefix_reports_the_stored_name` (xfail, H-API-04) |

### GET /api/backend-health

No aplica el caso de payload vacío ni el de archivo sin extensión: el endpoint no recibe cuerpo ni archivos. El límite de tamaño tampoco aplica.

| Caso | Tipo | Resultado esperado | Prueba |
|---|---|---|---|
| Servicio de auth responde 200 | feliz | 200 con `requestedBy` `student`, `status` 200 y `body` del servicio | `test_backend_health_returns_auth_status_and_body` |
| Servicio de auth responde 503 | borde | 200 con `status` 503 y el `body` que devolvió | `test_backend_health_passes_through_auth_error_status` |
| Servicio de auth no responde | error documentado | 200 con `status` 0 y `body` no vacío | `test_backend_health_with_unreachable_auth_returns_200_and_status_zero` (defecto H-API-16) |
| Sin cabecera `Authorization` | error | 401 | `test_backend_health_without_authorization_header_returns_401` |

Las pruebas de `backend-health` usan un servidor HTTP local en `127.0.0.1` con puerto libre. No dependen de `auth/`.

## Ejecuciones

| Fecha | Componente | IDs | Comando | Directorio | Código de salida | Resultado |
|---|---|---|---|---|---|---|
| 2026-11-02 | api | H-API-02 a H-API-07 (estado previo a esta ronda) | `.venv/Scripts/python.exe -m pytest -p no:warnings` | `api/` | No registrado en esa ocasión | 45 passed. Línea base previa a `test_endpoints.py` y `test_uploads.py` |
| 2026-11-02 | api | H-API-02 a H-API-07, H-API-16, H-API-04 (xfail) | `.venv/Scripts/python.exe -m pytest -p no:warnings -rfEx --cov=app --cov-branch --cov-report=term-missing` | `api/` | 0 | 65 passed, 1 xfailed |
| 2026-11-02 | api | Cálculo de cobertura del diff (sin cambios de código) | `.venv/Scripts/python.exe -m pytest -p no:warnings -q --cov=app --cov-branch --cov-report=json:<tmp>` más `git diff -U0 main -- api/app` desde `ai-labs/` | `api/` | 0 (pytest) | 65 passed, 1 xfailed; cobertura del diff 102/102 |

Los 66 tests recolectados son 65 pasados y 1 `xfail`. Ninguna ejecución marcó fallos.

## Cobertura

**Método:** coverage.py medido por pytest-cov, con ramas, sobre el paquete `app`. Para el diff se toma el conjunto de líneas añadidas de `git diff -U0 main -- api/app` (desde `ai-labs/`). Los archivos nuevos sin seguimiento (`authorization.py`, `repository.py`, `uploads.py`) cuentan completos. El numerador son las líneas de cambio ejecutables que aparecen como ejecutadas en el reporte JSON. El denominador son todas las líneas de cambio ejecutables.

**Código modificado (diff):**

| Archivo | Numerador (ejecutadas) | Denominador (ejecutables de cambio) | Sin cubrir |
|---|---|---|---|
| `api/app/main.py` | 32 | 32 | ninguna |
| `api/app/repository.py` (nuevo) | 15 | 15 | ninguna |
| `api/app/uploads.py` (nuevo) | 47 | 47 | ninguna |
| `api/app/authorization.py` (nuevo) | 8 | 8 | ninguna |
| **Total** | **102** | **102** | **100%** |

Cobertura del diff: 102 / 102 = **100%**. Supera la meta de 85% (AGENTS.md).

Limitación: este cálculo es de líneas. Las ramas del diff no se miden por separado. La cobertura de ramas global es 30 ramas con 0 parciales, así que ninguna rama parcial queda en el código medido.

**Cobertura global (`app`, toda la aplicación):** 179 de 179 líneas (100%), 30 ramas, 0 parciales, según `--cov-report=term-missing`. El gate del 85% de toda la app también se cumple (README).

**Cobertura por archivo (informe de terminal):** `main.py` 100%, `repository.py` 100%, `uploads.py` 100%, `authorization.py` 100%.

## Fallos y defectos

Estos son defectos conocidos. No se han escondido dentro de un test: cada uno tiene su ID.

| Prueba | Estado | Defecto | Explicación |
|---|---|---|---|
| `test_upload_name_with_drive_prefix_reports_the_stored_name` | XFAIL (estricto) | H-API-04, Pendiente | `C:x.txt` se guarda como `x.txt`, pero `fileName` devuelve `C:x.txt`. Confirmado en la ejecución manual del 2026-11-02 y en esta prueba en Windows. Se marca `xfail` para que el defecto quede visible; al corregirlo, la prueba falla y hay que quitar el marcador. |
| `test_backend_health_with_unreachable_auth_returns_200_and_status_zero` | Pasa (fija el contrato observado) | H-API-16, Pendiente | Un fallo del servicio de auth responde 200 con `status` 0. La prueba fija el comportamiento documentado en `docs/ENDPOINTS.md`, pero el 200 es el defecto. Si se corrige, esta prueba debe cambiar y se debe actualizar `docs/ENDPOINTS.md`. Requiere decidir qué código devolver. |
| Pruebas de 401 (`test_list_without_authorization_header_returns_401_with_detail` y similares) | Pasan | H-API-10, Pendiente | Comprueban solo que el `detail` empieza por `Invalid token`. El texto completo incluye el mensaje interno de la excepción (`Invalid token: Incorrect padding`), y eso es el defecto. |

Defectos que las pruebas no pueden demostrar en su estado actual:
- **H-API-01 y H-API-05/06:** el token se valida con un secreto constante en el código (`api/app/main.py:18`, `:38`). Quien conozca ese valor puede crear un token para otro usuario, y el control de acceso por owner depende de esa identidad. Las pruebas usan un token de prueba con el mismo valor, así que no prueban la falsificación. Cuando se corrija H-API-01, los helpers de token de `test_api.py`, `test_characterization.py`, `test_endpoints.py` y `test_uploads.py` cambian.
- **H-API-07:** el límite de 5 MiB se aplica después de recibir el archivo completo. Las pruebas no miden la transferencia.
- **H-API-11 (CORS):** no hay pruebas de cabeceras CORS.

## Pendientes

Sin ejecutar ni con evidencia:
- Expiración exactamente en el límite (`exp` igual a la hora actual). `api/app/main.py:38` la acepta. No hay prueba porque depende del reloj; hace falta fijar el tiempo.
- `backend-health` contra el servicio real de `auth/`. Las pruebas usan un servidor local. El servicio real no se levantó en esta sesión.
- Límite de transferencia de H-API-07 (red o proxy). Fuera del alcance de estas pruebas.
- Comportamiento concurrente de la conexión SQLite compartida (H-API-14). No hay pruebas.
- `python tools/lab.py test api` desde la raíz. No se ejecutó; el ejecutado es `pytest` con cwd `api/`.
- Revisión humana de las pruebas (paso 7 del Lab).

## Pruebas propuestas (no implementadas)

- Pruebas de cabeceras CORS en `GET /api/tasks`, para H-API-11.
- Prueba de falsificación de token, solo cuando H-API-01 esté corregido, para confirmar que el token forjado con otro secreto da 401.
- Helpers de pruebas duplicados entre `test_api.py`, `test_characterization.py`, `test_endpoints.py` y `test_uploads.py` (`make_token`, `auth_headers`). Sugerencia: moverlos a `tests/conftest.py`.
- Configuración de lint o de comprobación de tipos para `api/` (no existe en `pyproject.toml`).

## Documentos consultados

`docs/ENDPOINTS.md`, `api/app/main.py`, `api/app/repository.py`, `api/app/uploads.py`, `api/app/authorization.py`, `api/tests/test_api.py`, `api/tests/test_characterization.py`, `api/tests/test_endpoints.py`, `api/tests/test_uploads.py`, `api/pyproject.toml`, `api/requirements-dev.txt`, `HALLAZGOS.md`, `PLAN.md`, `README.md`, `RETO.md`, `docs/GOOD_PRACTICES.md`, `prompts/pruebas`, `prompts/revisar-correcciones` y `AGENTS.md`. No existe `docs/TESTING.md` previo.
