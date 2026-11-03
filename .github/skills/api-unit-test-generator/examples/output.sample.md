# API Unit Test Generator: `resolve_task_owner`

*Extracto ilustrativo: muestra las secciones 1, 4 y 9. Los resultados de la sección 6 salen de la ejecución real y no se simulan aquí.*

## 1. Summary
Pruebas unitarias de `resolve_task_owner` (`api/app/authorization.py:6-11`), que hoy solo se ejercita a través de los endpoints. La función se aísla con identidades en memoria, sin `TestClient` ni base de datos. Meta: cobertura del cambio de 85% o más.

## 4. Casos de prueba
| Unidad | Caso | Tipo | Resultado esperado | Fuente |
|---|---|---|---|---|
| `resolve_task_owner` | `owner` ausente (`None`) | feliz | Devuelve el usuario de la identidad | `authorization.py:7-8`; ENDPOINTS, `GET /api/tasks` |
| `resolve_task_owner` | `owner` igual al usuario | feliz | Devuelve el usuario | `authorization.py:7-8` |
| `resolve_task_owner` | `owner` vacío (`""`) | borde | Equivale a ausente: devuelve el usuario | `authorization.py:7-8`; ENDPOINTS: "Vacío o igual al usuario del token" |
| `resolve_task_owner` | `owner` ajeno, rol `STUDENT` | autorización | `HTTPException` 403 con detalle `Forbidden`, sin datos | `authorization.py:11`; ENDPOINTS |
| `resolve_task_owner` | `owner` ajeno, rol `TRAINER` | autorización | 403 igual que con `STUDENT` | `authorization.py:3,9-11`; ENDPOINTS, contrato acordado |
| `resolve_task_owner` | `owner` ajeno con el rol incluido en `ROLES_WITH_CROSS_OWNER_ACCESS` (vía `monkeypatch`) | borde (rama) | Devuelve el owner pedido | `authorization.py:9-10`; ver pregunta 1 |

## 9. Preguntas abiertas y documentos consultados
**Preguntas abiertas**
1. El contrato acordado dice que ningún rol accede a tareas de otro owner, pero el código conserva `ROLES_WITH_CROSS_OWNER_ACCESS` (hoy vacío). ¿Se mantiene esa rama? Hasta decidirlo, su prueba fija el comportamiento actual y no un requisito.
2. ¿Qué debe ocurrir si la identidad no trae `username` o `role`? No está documentado. No se escribe prueba; hoy lanzaría `KeyError`.

**Documentos consultados:** `api/app/authorization.py`, `docs/ENDPOINTS.md`, `api/tests/test_characterization.py`, `api/tests/test_endpoints.py` y `AGENTS.md`.
