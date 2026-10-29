# Tasks

## 1. Caracterización HTTP (antes de cambios funcionales)

- [x] 1.1 Ejecutar desde `auth/` `mvn -B -ntp -Dtest=AuthResourceTest test` y confirmar que la suite actual pasa antes de cambiar código productivo.
- [x] 1.2 Completar en `AuthResourceTest` la caracterización HTTP de `username` y `password` explícitamente `null`, verificando HTTP 400 y el mensaje documentado; conservar los casos existentes de ausencia/blanco. Verificar con la prueba focalizada.
- [x] 1.3 Completar las aserciones HTTP del éxito: `Content-Type`, todos los campos acordados y token no vacío; conservar la variante con `client` omitido y nunca registrar ni comparar el valor del token. Verificar con la prueba focalizada.
- [x] 1.4 Revisar que los casos existentes de 401, 500 y CORS coincidan con `auth-login/spec.md`; añadir solo aserciones faltantes y no fijar el cuerpo 403. Verificar con la prueba focalizada.

## 2. Confirmación del contrato

- [x] 2.1 Confirmar con los consumidores que request, estados y campos de `POST /auth/login` coinciden con `docs/ENDPOINTS.md`; registrar evidencia en el Issue #26. Si hay desacuerdo, detener el cambio funcional y actualizar la spec mediante revisión aprobada. Verificar que la decisión cubra request, estados y campos de respuesta.

## 3. Corrección funcional condicionada

- [x] 3.1 Solo si las pruebas demuestran una diferencia respecto del contrato confirmado, aplicar la corrección mínima dentro de `auth/`, preservando ruta y formato; si no hay diferencia, dejar producción intacta. Verificar con los escenarios HTTP afectados y confirmar que no cambió el contrato protegido.

## 4. Validación final

- [x] 4.1 Ejecutar desde `auth/` `mvn -B -ntp verify`; confirmar suite completa y gate JaCoCo de al menos 85% por clase, sin secretos ni tokens en la salida.

Este checklist no modifica `api/`, `web/` ni documentos generales, y no incluye refactor de persistencia, JWT o CORS.