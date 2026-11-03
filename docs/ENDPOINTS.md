# Endpoints de la API (`api/`)

Documento de integración para la API FastAPI de tareas y requerimientos. Describe el contrato que el código implementa hoy. Todo lo que aparece aquí se comprobó leyendo `api/app/` o ejecutando la API con datos ficticios en un directorio temporal.

Versión de la API: `0.1.0` (`api/app/main.py:63`). Datos de ejemplo: semillas de la base de datos (`api/app/main.py:56-58`).

## Estado del contrato

| Tipo | Contenido |
|---|---|
| Contrato observado | Todo este documento: la forma de las peticiones y respuestas que produce el código actual. |
| Contrato acordado | Decisiones aprobadas en `PLAN.md` (sección "Decisiones registradas"): ningún rol accede a tareas de otro owner; extensiones `.txt` y `.md`; 400 para carga inválida y 403 para owner ajeno; 413 y 409 en la carga; `fileName` normalizado; `null` tratado como ausente; token sin prefijo `Bearer` aceptado mientras dure la transición. Ningún acuerdo con el equipo de `web/` está registrado. |
| Riesgos pendientes | Ver la sección "Riesgos pendientes" al final, con los IDs de `HALLAZGOS.md`. |

## Convenciones generales

- Base local: `http://localhost:8000` (según `README.md`). Documentación interactiva de FastAPI en `/docs`.
- Formato: JSON en peticiones y respuestas, salvo `POST /api/requirements`, que recibe `multipart/form-data`.
- Codificación de texto: UTF-8.
- Fechas: `createdAt` es texto con formato `YYYY-MM-DD HH:MM:SS`, generado por `datetime('now')` de SQLite, es decir, en UTC.
- Errores de autenticación y autorización: cuerpo `{"detail": "..."}`.
- Errores de validación de FastAPI (422): cuerpo `{"detail": [{"type", "loc", "msg", "input"}]}`.
- CORS: se aceptan todos los orígenes, todos los métodos y todas las cabeceras, con credenciales habilitadas (`api/app/main.py:66-72`). Riesgo registrado en `HALLAZGOS.md` como H-API-11.

### Autenticación

Todos los endpoints requieren la cabecera:

```
Authorization: Bearer <token>
```

Formato del token: la cadena `usuario|rol|expiración|secreto` codificada en Base64 estándar (`api/app/main.py:35-40`).

| Campo del token | Tipo | Descripción |
|---|---|---|
| `usuario` | texto | Identidad del usuario. Es la que usan las rutas para filtrar y para asignar el owner. |
| `rol` | texto | Rol declarado. Hoy no se usa para autorizar (ver "Notas de seguridad"). |
| `expiración` | entero | Epoch en segundos. El token se rechaza si `expiración` es menor que la hora actual (`api/app/main.py:38`). |
| `secreto` | texto | Debe ser igual a la constante `LEGACY_SECRET` del código (`api/app/main.py:18`). El valor no se documenta aquí. |

Comportamiento observado:
- El prefijo `Bearer ` es opcional. El código quita la cadena `"Bearer "` donde aparezca (`api/app/main.py:35`).
- Si la cabecera falta, está vacía, no es Base64 válido o no cumple el formato, la respuesta es 401.
- El token no lleva firma criptográfica ni emisor ni audiencia. Ver H-API-01 en "Riesgos pendientes".

## Endpoints

### GET /api/tasks

**1. Qué hace.** Devuelve las tareas de un owner. Sin parámetro `owner`, devuelve las del usuario del token. Con un `owner` ajeno, responde 403.

**2. Autenticación.** Sí. Cabecera: `Authorization: Bearer <token>`.

**3. Request.**

Parámetros de query:

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `owner` | texto | No | Owner cuyas tareas se piden. Vacío o igual al usuario del token: devuelve las del token. Otro valor: 403. |
| `Authorization` (cabecera) | texto | Sí | `Bearer <token>`. Ver "Autenticación". |

Sin cuerpo.

Ejemplo:

```http
GET /api/tasks HTTP/1.1
Host: localhost:8000
Authorization: Bearer <token>
```

**4. Response exitosa.** `200 OK`. Cuerpo: arreglo de tareas, ordenado por `id` ascendente (`api/app/repository.py:11`).

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | entero | Identificador de la tarea. |
| `title` | texto | Título. |
| `description` | texto | Descripción. |
| `status` | texto | Estado. El código no restringe los valores; los que aparecen en las semillas y en el resumen son `TODO`, `IN_PROGRESS` y `DONE`/`ARCHIVED` (`api/app/main.py:115-117`). |
| `owner` | texto | Owner de la tarea. |
| `priority` | texto | Prioridad. Las semillas usan `HIGH`, `MEDIUM` y `LOW`. |
| `createdAt` | texto | Fecha de creación, `YYYY-MM-DD HH:MM:SS`. |

Ejemplo real:

```json
[
  {
    "id": 1,
    "title": "Refactor login",
    "description": "Separar responsabilidades en frontend",
    "status": "TODO",
    "owner": "student",
    "priority": "HIGH",
    "createdAt": "2026-11-02 22:17:59"
  },
  {
    "id": 2,
    "title": "Review API",
    "description": "Revisar seguridad de endpoints",
    "status": "IN_PROGRESS",
    "owner": "student",
    "priority": "MEDIUM",
    "createdAt": "2026-11-02 22:17:59"
  }
]
```

Si el owner no tiene tareas, la respuesta es `200` con `[]`.

**5. Errores.**

| Código | Cuándo | Ejemplo de cuerpo |
|---|---|---|
| 401 | Falta la cabecera, el token no es Base64 válido, no tiene 4 campos, el secreto no coincide o está expirado. | `{"detail": "Invalid token: invalid token"}` |
| 401 | Cabecera con texto que no es Base64 válido (por ejemplo `Authorization: invalid`). | `{"detail": "Invalid token: Incorrect padding"}` |
| 403 | `owner` distinto del usuario del token. | `{"detail": "Forbidden"}` |

El texto después de `Invalid token: ` es el mensaje de la excepción interna. Ver H-API-10.

**6. Notas de seguridad.**
- La autorización depende de la identidad del token, no de un parámetro enviado por el cliente. Un `owner` ajeno da 403 (`api/app/authorization.py:6-11`).
- Esa identidad viene de un token que hoy puede fabricarse con el secreto del código (`api/app/main.py:18`, `:38`). Hasta que se corrija H-API-01, el control de acceso no es fuerte. Ver "Riesgos pendientes".
- El parámetro `owner` ya no se usa en SQL sin parámetros (`api/app/repository.py:11`).

### POST /api/tasks

**1. Qué hace.** Crea una tarea. El owner es el usuario del token, salvo que se pida otro owner, lo que da 403.

**2. Autenticación.** Sí. Cabecera: `Authorization: Bearer <token>`.

**3. Request.**

Cuerpo JSON: objeto. Otros campos se ignoran.

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `title` | texto | No | Título. Si falta o es `null`, se guarda `""`. Cualquier valor no nulo se convierte con `str()` (`api/app/main.py:23-30`). |
| `description` | texto | No | Descripción. Mismo tratamiento que `title`. |
| `status` | texto | No | Estado. Por defecto `TODO`. No hay lista de valores válidos. |
| `priority` | texto | No | Prioridad. Por defecto `MEDIUM`. No hay lista de valores válidos. |
| `owner` | texto o `null` | No | Vacío, `null` o ausente: owner = usuario del token. Igual al usuario del token: se acepta. Otro valor: 403. |
| `Authorization` (cabecera) | texto | Sí | `Bearer <token>`. |

Ejemplo:

```http
POST /api/tasks HTTP/1.1
Host: localhost:8000
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Documentar API",
  "description": "Escribir ENDPOINTS.md",
  "status": "IN_PROGRESS",
  "priority": "HIGH"
}
```

**4. Response exitosa.** `201 Created`. Cuerpo: la tarea creada, con la misma forma que en `GET /api/tasks`.

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | entero | Identificador asignado. |
| `title` | texto | Título guardado. |
| `description` | texto | Descripción guardada. |
| `status` | texto | Estado guardado. |
| `owner` | texto | Owner asignado. |
| `priority` | texto | Prioridad guardada. |
| `createdAt` | texto | Fecha de creación. |

Ejemplo real (petición del bloque anterior):

```json
{
  "id": 4,
  "title": "Documentar API",
  "description": "Escribir ENDPOINTS.md",
  "status": "IN_PROGRESS",
  "owner": "student",
  "priority": "HIGH",
  "createdAt": "2026-11-02 22:17:59"
}
```

Ejemplo real con el cuerpo `{}` (valores por defecto):

```json
{
  "id": 5,
  "title": "",
  "description": "",
  "status": "TODO",
  "owner": "student",
  "priority": "MEDIUM",
  "createdAt": "2026-11-02 22:17:59"
}
```

**5. Errores.**

| Código | Cuándo | Ejemplo de cuerpo |
|---|---|---|
| 401 | Igual que en `GET /api/tasks`. | `{"detail": "Invalid token: invalid token"}` |
| 403 | `owner` es un usuario distinto del token. | `{"detail": "Forbidden"}` |
| 422 | El cuerpo no es un objeto JSON. | `{"detail": [{"type": "dict_type", "loc": ["body"], "msg": "Input should be a valid dictionary", "input": [1]}]}` |
| 422 | No hay cuerpo. | `{"detail": [{"type": "missing", "loc": ["body"], "msg": "Field required", "input": null}]}` |

**6. Notas de seguridad.**
- Un cliente no puede crear tareas en la lista de otro owner: la regla de `GET /api/tasks` se aplica también aquí (`api/app/main.py:100`).
- `status` y `priority` aceptan cualquier texto. Eso afecta al resumen, que solo cuenta `HIGH` como prioridad alta (`api/app/main.py:115`). H-API-13.
- `title` y `description` vacíos se aceptan. Pregunta abierta: ¿deben ser obligatorios?

### GET /api/summary

**1. Qué hace.** Devuelve el resumen de tareas de un owner: total, conteo por estado y número de tareas de prioridad alta abiertas.

**2. Autenticación.** Sí. Cabecera: `Authorization: Bearer <token>`.

**3. Request.**

Parámetros de query:

| Parámetro | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `owner` | texto | No | Mismas reglas que en `GET /api/tasks`: vacío o igual al usuario del token, o 403. |
| `Authorization` (cabecera) | texto | Sí | `Bearer <token>`. |

Ejemplo:

```http
GET /api/summary HTTP/1.1
Host: localhost:8000
Authorization: Bearer <token>
```

**4. Response exitosa.** `200 OK`.

| Campo | Tipo | Descripción |
|---|---|---|
| `owner` | texto | Owner del resumen. |
| `total` | entero | Número de tareas del owner. |
| `byStatus` | objeto (texto → entero) | Conteo de tareas por estado. Solo aparecen los estados presentes. |
| `highPriorityOpen` | entero | Tareas con `priority` igual a `HIGH` y `status` distinto de `DONE` y de `ARCHIVED` (`api/app/main.py:115-118`). |

Ejemplo real (owner `student`, con las semillas):

```json
{
  "owner": "student",
  "total": 2,
  "byStatus": {
    "TODO": 1,
    "IN_PROGRESS": 1
  },
  "highPriorityOpen": 1
}
```

Si el owner no tiene tareas: `total` 0, `byStatus` `{}` y `highPriorityOpen` 0.

**5. Errores.**

| Código | Cuándo | Ejemplo de cuerpo |
|---|---|---|
| 401 | Igual que en `GET /api/tasks`. | `{"detail": "Invalid token: invalid token"}` |
| 403 | `owner` es un usuario distinto del token. | `{"detail": "Forbidden"}` |

**6. Notas de seguridad.**
- Mismas reglas de autorización que `GET /api/tasks`: el owner se resuelve con la identidad del token (`api/app/main.py:106`).
- El resumen cuenta cualquier estado que haya en la base de datos, aunque no esté en la lista de valores esperados.

### POST /api/requirements

**1. Qué hace.** Guarda un archivo de requisitos en el servidor. Solo acepta `.txt` y `.md`, y hasta 5 MiB.

**2. Autenticación.** Sí. Cabecera: `Authorization: Bearer <token>`.

**3. Request.**

Cuerpo `multipart/form-data`:

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `file` | archivo | Sí | Contenido del archivo. El nombre se normaliza: se conserva solo el último segmento tras separar por `/` y `\`. La extensión, sin distinguir mayúsculas, debe ser `.txt` o `.md`. Tamaño máximo: 5 MiB (5 242 880 bytes). |
| `Authorization` (cabecera) | texto | Sí | `Bearer <token>`. |

Ejemplo:

```http
POST /api/requirements HTTP/1.1
Host: localhost:8000
Authorization: Bearer <token>
Content-Type: multipart/form-data; boundary=----frontera

------frontera
Content-Disposition: form-data; name="file"; filename="requirements.txt"
Content-Type: text/plain

Login must be accessible
------frontera--
```

**4. Response exitosa.** `201 Created`.

| Campo | Tipo | Descripción |
|---|---|---|
| `owner` | texto | Usuario del token que subió el archivo. |
| `fileName` | texto | Nombre normalizado con el que se guardó. |
| `storedAt` | texto | Ruta absoluta del archivo en el servidor. Forma parte del contrato actual; ver riesgos. |
| `size` | entero | Tamaño en bytes del contenido guardado. |

Ejemplo real (en un directorio temporal de prueba; `storedAt` depende del servidor):

```json
{
  "owner": "student",
  "fileName": "requirements.txt",
  "storedAt": "C:\\Users\\Usuario\\AppData\\Local\\Temp\\tmp.67EsMtsOiv\\up\\requirements.txt",
  "size": 24
}
```

Ejemplo real con `../nota.md` como nombre: el archivo se guarda como `nota.md` dentro del directorio de subida.

```json
{
  "owner": "student",
  "fileName": "nota.md",
  "storedAt": "C:\\Users\\Usuario\\AppData\\Local\\Temp\\tmp.67EsMtsOiv\\up\\nota.md",
  "size": 1
}
```

**5. Errores.**

| Código | Cuándo | Ejemplo de cuerpo |
|---|---|---|
| 400 | Nombre vacío, `.`, `..`, sin extensión o con extensión distinta de `.txt`/`.md` (sin distinguir mayúsculas). | `{"detail": "Invalid upload"}` |
| 401 | Igual que en `GET /api/tasks`. | `{"detail": "Invalid token: invalid token"}` |
| 409 | Ya existe un archivo con el mismo nombre normalizado. No se sobrescribe. | `{"detail": "Upload conflict"}` |
| 413 | El contenido supera 5 MiB. No se guarda. | `{"detail": "Upload too large"}` |
| 422 | No se envió el campo `file`. | `{"detail": [{"type": "missing", "loc": ["body", "file"], "msg": "Field required", "input": null}]}` |

**6. Notas de seguridad.**
- La normalización elimina directorios del nombre: `../nota.md` se guarda como `nota.md`, dentro del directorio de subida (`api/app/uploads.py:35`).
- La comprobación de que el destino queda dentro del directorio de subida está en `api/app/uploads.py:61-62`.
- El límite de 5 MiB se aplica cuando el contenido ya se recibió por completo. No limita la transferencia (H-API-07).
- Un nombre con prefijo de unidad (por ejemplo `C:x.txt`) todavía no se normaliza en Windows: el archivo se guarda con otro nombre que el de `fileName` (H-API-04).

### GET /api/backend-health

**1. Qué hace.** Consulta el endpoint de salud del servicio de autenticación (`auth/`) y devuelve su respuesta. Sirve para diagnosticar la conexión entre componentes.

**2. Autenticación.** Sí. Cabecera: `Authorization: Bearer <token>`.

**3. Request.** Sin parámetros ni cuerpo.

Ejemplo:

```http
GET /api/backend-health HTTP/1.1
Host: localhost:8000
Authorization: Bearer <token>
```

La URL consultada es la variable de entorno `AUTH_HEALTH_URL`, con valor por defecto `http://localhost:8080/q/health` (`api/app/main.py:135`). El cliente no puede cambiarla.

**4. Response exitosa.** `200 OK`. Cuerpo:

| Campo | Tipo | Descripción |
|---|---|---|
| `requestedBy` | texto | Usuario del token. |
| `status` | entero | Código HTTP que devolvió el servicio de autenticación, o `0` si no hubo respuesta. |
| `body` | texto | Cuerpo de la respuesta del servicio de autenticación, o el texto del error si no hubo respuesta. |

Ejemplo real con el servicio de autenticación caído (en la prueba, `AUTH_HEALTH_URL` apuntaba a un puerto cerrado; el texto del error depende del sistema operativo y del idioma):

```json
{
  "requestedBy": "student",
  "status": 0,
  "body": "HTTPConnectionPool(host='127.0.0.1', port=9): Max retries exceeded with url: /q/health (Caused by NewConnectionError(...))"
}
```

No se verificó la respuesta exitosa con el servicio de autenticación en marcha. Pregunta abierta: ¿qué forma tiene `body` cuando `auth` responde 200?

**5. Errores.**

| Código | Cuándo | Ejemplo de cuerpo |
|---|---|---|
| 401 | Igual que en `GET /api/tasks`. | `{"detail": "Invalid token: invalid token"}` |
| 200 con `status` 0 | El servicio de autenticación no responde o da error de conexión. El código no devuelve 5xx en ese caso (`api/app/main.py:139-140`). | Ver el ejemplo anterior. |

**6. Notas de seguridad.**
- Un fallo de conexión sale como 200. Un cliente que solo mire el código HTTP lo tomaría por éxito. H-API-16.
- `body` puede exponer detalles internos de red y de la excepción. H-API-10.

## Riesgos pendientes

| Riesgo | Endpoints afectados | Estado en `HALLAZGOS.md` |
|---|---|---|
| El token se puede fabricar con el secreto del código. Un cliente puede hacerse pasar por otro usuario, incluido `trainer`. | Todos | H-API-01, Aprobado; bloqueado por `compose.yaml` y `auth/`. |
| Autorización por owner: correcta para el usuario del token, pero depende de la identidad, que es falsificable. | `GET /api/tasks`, `GET /api/summary`, `POST /api/tasks` | H-API-05 y H-API-06, Pendiente. |
| Nombre con prefijo de unidad (Windows) no normalizado. | `POST /api/requirements` | H-API-04, Pendiente. |
| El límite de 5 MiB se aplica después de recibir el archivo completo. | `POST /api/requirements` | H-API-07, Pendiente. |
| Mensajes de error con texto de excepción interna. | Todos los 401 y `GET /api/backend-health` | H-API-10, Pendiente. |
| `backend-health` responde 200 si falla la conexión. | `GET /api/backend-health` | H-API-16, Pendiente. |
| CORS con `*` y credenciales. | Todos | H-API-11, Pendiente. |
| `status` y `priority` aceptan cualquier texto. | `POST /api/tasks` | H-API-13, Pendiente. |
| Dependencias con versiones a revisar (urllib3 y python-multipart). | Todos | H-API-08, Pendiente. |

## Preguntas abiertas

1. ¿Quién emite el token, en qué formato, y cuándo se retira el formato Base64? (`auth/`, sin verificar desde `api/`.)
2. ¿Qué valores de `rol` existen? `api/` no los documenta; en las pruebas aparecen `STUDENT` y `TRAINER`.
3. ¿`title` debe ser obligatorio en `POST /api/tasks`?
4. ¿`status` y `priority` deben limitarse a una lista de valores?
5. ¿Qué forma tiene `body` en `GET /api/backend-health` cuando el servicio de autenticación responde 200? No se verificó.
6. ¿El frontend envía el prefijo `Bearer ` y usa el parámetro `owner`? (`web/`, no verificado desde `api/`.)
7. ¿La zona horaria de `createdAt` debe ser UTC? Hoy es UTC porque lo genera `datetime('now')` de SQLite.
8. ¿Se acepta el límite de 5 MiB como aplicado después de la recepción, o hace falta un tope en la capa de red?

## Fuentes

Documentos consultados para este contrato: `api/app/main.py`, `api/app/repository.py`, `api/app/uploads.py`, `api/app/authorization.py`, `api/tests/test_characterization.py`, `api/tests/test_api.py`, `HALLAZGOS.md`, `PLAN.md`, `README.md`, `RETO.md`, `docs/GOOD_PRACTICES.md`, `docs/prompts/api` y `AGENTS.md`. No existe `docs/TESTING.md`. Las respuestas de ejemplo salen de ejecuciones locales de la API con datos ficticios en un directorio temporal, sin modificar `api/`.
