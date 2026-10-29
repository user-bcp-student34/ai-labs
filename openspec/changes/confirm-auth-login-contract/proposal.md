# Proposal

## Why

GitHub Issue [#26](https://github.com/user-bcp-student34/ai-labs/issues/26) pide un contrato acordado y verificable para `POST /auth/login`, de modo que sus consumidores conozcan con fiabilidad los estados y campos de respuesta. `docs/ENDPOINTS.md` registra el comportamiento observado, pero lo identifica como pendiente de contrastar; formalizar ese baseline evita que las pruebas o cambios posteriores introduzcan incompatibilidades sin una decisión explícita.

## What Changes

### Alcance

- Definir como contrato preservado el comportamiento documentado para `POST /auth/login`: request JSON, campos requeridos, respuestas 400 y 401, respuesta exitosa y campos de usuario.
- Especificar escenarios HTTP observables para request nulo, campos requeridos ausentes o en blanco, credenciales no encontradas y autenticación exitosa, incluyendo que `client` es opcional.
- Preservar el formato de error 500 y el rechazo CORS observados, sin ampliar sus reglas ni inventar un cuerpo para 403.
- Usar el contrato como criterio para las pruebas de caracterización del Issue. Un ajuste de implementación solo se consideraría posteriormente si se comprobara una divergencia.

### Fuera de alcance

- Cambiar la ruta, los estados, los mensajes o la forma de las respuestas documentadas.
- Rediseñar la autenticación, el JWT, la persistencia, la autorización o la configuración CORS.
- Modificar `api/`, `web/` o el código funcional en esta etapa.
- Añadir tareas de implementación o un diseño técnico.

### Comportamiento que debe preservarse

La referencia de comportamiento es `docs/ENDPOINTS.md` (contrato observado del Día 1). Esta propuesta no declara cambios al contrato: los escenarios de la spec conservan el 400 para request/campos requeridos inválidos, el 401 para credenciales inválidas y el 200 con `token`, `expiresIn`, `user` y `debug` para autenticación exitosa. La confirmación externa mencionada por el Issue no se presume realizada; cualquier contradicción futura con consumidores requiere una decisión explícita y una actualización del contrato antes de cambiar estos escenarios.

## Capabilities

### New Capabilities

- `auth-login`: contrato HTTP observable de `POST /auth/login`, incluidos los casos de validación, autenticación, éxito y límites de error/acceso documentados.

### Modified Capabilities

- Ninguna. El repositorio no tiene specs existentes.

## Impact

- API: `POST /auth/login` del componente `auth/`, sin cambios de compatibilidad propuestos.
- Pruebas futuras: escenarios HTTP del componente `auth/` referenciados por el Issue #26.
- Documentación de referencia: `docs/ENDPOINTS.md`; no se modifican las secciones generales ni las de otros componentes.