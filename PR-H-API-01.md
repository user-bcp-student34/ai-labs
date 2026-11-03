# H-API-01 (parcial): verificación JWT firmada y flag de transición en `api`

Borrador de PR. No se ha creado PR, no hay push ni remoto nuevo (Día 1, cierre local). Los cambios están sin commit en la rama local `day1/prompting`.

## Qué cambió

- `api/app/security.py` (nuevo): verifica tokens. Si el token contiene `.`, es JWT HS256 con PyJWT (firma, `exp`, `iss`, `aud`, `sub`, `role` obligatorios). Si no, valida el formato legacy `usuario|rol|expiración|secreto`. Cualquier fallo responde 401 con `{"detail": "Invalid token"}`.
- `api/app/settings.py` (nuevo): lee `WORKSHOP_TOKEN_SECRET`, `WORKSHOP_TOKEN_ISSUER`, `WORKSHOP_TOKEN_AUDIENCE` y `WORKSHOP_LEGACY_TOKENS_ENABLED`. El flag está activo por defecto. Un secreto vacío cuenta como ausente.
- `api/app/main.py`: `legacy_identity` se sustituye por `authenticated_identity`, que delega en `verify_authorization`. `create_app` acepta `token_settings` opcional. El literal `LEGACY_SECRET` se mueve a `settings.py`.
- `api/requirements.txt`: `PyJWT==2.15.1`.
- `api/tests/test_security.py` (nuevo, 24 pruebas) y `api/tests/test_characterization.py` (+1 caso).
- `docs/ENDPOINTS.md`: ejemplos de 401 actualizados.

## Por qué

H-API-01 (aprobado): el token tenía el secreto embebido en el código, sin firma, emisor ni audiencia. Este PR añade la verificación firmada sin romper a los clientes actuales, porque el formato legacy sigue aceptado mientras el flag esté activo.

## Qué se preservó

- Formato legacy aceptado por defecto, incluido el token sin prefijo `Bearer` (decisión 4).
- Caracterización: token con otro secreto → 401, expirado → 401, `rol` sin efecto en el listado.
- Formas y códigos de `GET`/`POST /api/tasks`, `GET /api/summary`, `POST /api/requirements`, `GET /api/backend-health` (`docs/ENDPOINTS.md`).
- `POST /auth/login`: sin cambios en este PR.

## Cambio observable

El cuerpo del 401 pasa de `"Invalid token: <error>"` a `"Invalid token"`. Las pruebas existentes usan `startswith("Invalid token")`. Falta confirmar con `web/` que no depende del texto.

## Cómo se probó

Comando: `python tools/lab.py test api` (desde la raíz).

| | Passed | Xfailed | Cobertura total `app/` (branch) |
|---|---|---|---|
| Línea base | 65 | 1 | 100 % |
| Tras el cambio | 90 | 1 | 100 %, 0 líneas y 0 ramas sin cubrir |

- Cobertura del diff: 100 %, porque no hay líneas sin cubrir en `app/`. Método: pytest-cov con `--cov-branch`, sin herramienta de diff separada.
- El xfail es preexistente (`api/tests/test_endpoints.py:169`, H-API-04).
- Mutación en copia temporal fuera del repo: quitar la comprobación del flag hace fallar `test_legacy_token_is_rejected_when_flag_is_off`. Quitar `audience=` no hace fallar la prueba de audiencia incorrecta, porque PyJWT rechaza cualquier `aud` sin `audience` explícito. Limitación anotada.
- No se ha ejecutado CodeQL, Dependency Review ni JaCoCo en local. Los resultados de CI no están disponibles.

## Qué no se resolvió

- El literal legacy sigue en el código: `api/app/settings.py:11`, `api/tests/test_api.py:11`, `api/tests/test_characterization.py:10`, `api/tests/test_endpoints.py:15`. Criterio 11 de la spec no cumplido.
- H-API-01 sigue abierto: el flag está activo por defecto.
- Variable de secreto legacy `WORKSHOP_LEGACY_TOKEN_SECRET`: decidida, no implementada. Requiere cambiar `compose.yaml`, fuera de `api/`. Sin ese cambio, mover el secreto rompería el login del despliegue actual.
- `auth/` no emite JWT. El secreto compartido con `auth/` queda para el corte.
- `iss` y `aud` por defecto (`workshop-auth`, `workshop-api`) no están acordados con `auth/`.
- Advisories de PyJWT y de H-API-08 no consultados.
- `docs/ENDPOINTS.md`, sección Autenticación: sigue describiendo solo el formato legacy y cita líneas obsoletas.
- `web/`: no verificado.

## Cómo revertir

- Sin commit: `git restore --source=HEAD -- api/app/main.py api/requirements.txt api/tests/test_characterization.py docs/ENDPOINTS.md` y borrar `api/app/security.py`, `api/app/settings.py`, `api/tests/test_security.py`.
- Con commit (tras revisión): `git revert <commit>`.
- Sin revertir código: `WORKSHOP_LEGACY_TOKENS_ENABLED` sigue controlando el formato legacy en tiempo de ejecución, sin cambios de código.

## Mensaje de commit sugerido

```text
H-API-01: verifica JWT HS256 y mantiene formato legacy tras flag

Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>
```

🤖 Generated with [Claude Code](https://claude.com/claude-code)
