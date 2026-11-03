## Unidad bajo prueba
`resolve_task_owner` en `api/app/authorization.py:6-11`.

## Fuente del comportamiento esperado
`docs/ENDPOINTS.md`, `GET /api/tasks`, parámetro `owner` (contrato observado): vacío o igual al usuario del token devuelve las tareas del token; otro valor responde 403. Contrato acordado: ningún rol accede a tareas de otro owner.

## Cambio a cubrir
`api/app/authorization.py`, completo.

## Pruebas existentes
Sin pruebas directas de la función. Se ejercita por endpoint en `api/tests/test_endpoints.py` y `api/tests/test_characterization.py`.

## Defectos conocidos
H-API-01, H-API-05 y H-API-06 (`docs/ENDPOINTS.md`, "Riesgos pendientes"): la identidad del token es falsificable. Queda fuera de esta unidad, que recibe una identidad ya validada.

## Entorno
`api/.venv`, Python 3.13, pytest 9.0.2 y pytest-cov 7.0.0.

## Meta de cobertura / DoD
85% o más del código modificado. Pruebas ejecutadas de verdad. Revisión humana pendiente.
