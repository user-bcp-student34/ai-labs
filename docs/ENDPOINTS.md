# Contratos HTTP observados

Este documento describe el contrato observado del componente `auth`. No hay
un contrato externo acordado disponible en el repositorio; las preguntas
abiertas se conservan explícitamente. La URL local documentada es
`http://localhost:8080`; en producción el proceso escucha en el puerto 8080 y
se enlaza a `0.0.0.0` (`README.md`; `auth/src/main/resources/application.properties:1-3`).

## auth — Quarkus

### `GET /q/health`

1. **Qué hace:** permite a un supervisor comprobar si el servicio de
   autenticación está disponible.

2. **Autenticación:** no se define autenticación ni un header `Authorization`
   para este endpoint. La ruta la proporciona `quarkus-smallrye-health`
   (`auth/pom.xml:58`); no hay un control de autorización de aplicación
   configurado para la ruta en `auth/`.

3. **Request:**

   | campo | tipo | obligatorio | descripción |
   |---|---|---|---|
   | Cuerpo | Ninguno | No | La ruta de health no define campos de request. |

   Ejemplo HTTP (no lleva JSON porque es una petición GET sin cuerpo):

   ```http
   GET /q/health HTTP/1.1
   Host: localhost:8080
   Accept: application/json
   ```

4. **Response exitosa:** HTTP `200`.

   | campo | tipo | descripción |
   |---|---|---|
   | `status` | string | Estado agregado del servicio; el ejemplo muestra `UP`. |
   | `checks` | array | Resultados de checks de health. No hay checks de aplicación propios en `auth/`; el ejemplo representa la respuesta vacía por defecto de SmallRye Health. |

   Ejemplo JSON de la respuesta por defecto de SmallRye Health:

   ```json
   {
     "status": "UP",
     "checks": []
   }
   ```

   Las pruebas de integración de auth afirman HTTP `200`, `status: UP`,
   `checks: []` y `Content-Type: application/json`
   (`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:28-45`).

5. **Respuestas de error:**

   - **HTTP `503` — estado DOWN:** la extensión puede informar que el servicio
     no está saludable. No hay checks de aplicación en auth que definan cuándo
     ocurriría y ninguna prueba provoca ni afirma esta respuesta.
     **Pregunta abierta:** ¿qué check de health y qué cuerpo DOWN debe
     considerarse contrato para los consumidores? No se inventa un ejemplo
     JSON hasta confirmar ese comportamiento.
   - No se encontró una respuesta de error específica implementada por auth.
     Errores de red, proxy o servidor no tienen un cuerpo JSON definido por
     este componente.

6. **Notas de seguridad:** no requiere token; por ello, el acceso debe
   restringirse mediante la configuración de red del despliegue si el estado
   del servicio no debe ser público. En producción, el host se enlaza a
   `0.0.0.0` (`auth/src/main/resources/application.properties:3`).

### `POST /auth/login`

1. **Qué hace:** autentica a una persona con sus credenciales y, si son
   válidas, devuelve un token firmado junto con la información básica del
   usuario.

2. **Autenticación:** no requiere una sesión ni un header `Authorization`;
   las credenciales se envían en el cuerpo. Requiere
   `Content-Type: application/json` (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:15-17`).

3. **Request:**

   | campo | tipo | obligatorio | descripción |
   |---|---|---|---|
   | `username` | string | Sí | Nombre de usuario; debe contener al menos un carácter distinto de espacio en blanco. |
   | `password` | string | Sí | Contraseña; debe contener al menos un carácter distinto de espacio en blanco. |
   | `client` | string | No | Campo aceptado por el DTO, pero no se usa en la autenticación ni en la respuesta. |

   Ejemplo JSON completo observado en la prueba de login:

   ```json
   {
     "username": "student",
     "password": "Workshop123!",
     "client": "angular-workshop"
   }
   ```

   El usuario y contraseña del ejemplo son credenciales ficticias del flujo de
   laboratorio; el seeding de estas cuentas está limitado a modos distintos
   de `NORMAL`. No usar estas credenciales en producción
   (`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:26-38`;
   `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:47,79-81`).

4. **Response exitosa:** HTTP `200`.

   | campo | tipo | descripción |
   |---|---|---|
   | `token` | string | JWT firmado emitido para el usuario autenticado. Su valor cambia en cada emisión. |
   | `expiresIn` | integer | Tiempo de vida del token en segundos; actualmente `3600`. |
   | `user` | object | Datos del usuario que se devuelven al cliente. |
   | `user.username` | string | Nombre de usuario. |
   | `user.displayName` | string | Nombre para mostrar, escapado para HTML. |
   | `user.role` | string | Rol del usuario. |
   | `debug` | string | Indicador fijo del flujo actual: `authenticated-with-legacy-flow`. |

   Ejemplo JSON completo. El token es un marcador de posición para el valor
   dinámico firmado por el servidor; no es un token utilizable:

   ```json
   {
     "token": "<JWT HS256 emitido por el servidor>",
     "expiresIn": 3600,
     "user": {
       "username": "student",
       "displayName": "Workshop Student",
       "role": "STUDENT"
     },
     "debug": "authenticated-with-legacy-flow"
   }
   ```

   La forma y valores no dinámicos del ejemplo están cubiertos por
   `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:26-38`;
   los campos de salida se definen en
   `auth/src/main/java/com/bcp/workshop/auth/LoginResponse.java:3-4`.

5. **Respuestas de error:**

   - **HTTP `400` — cuerpo JSON nulo:**

     ```json
     {
       "message": "Request is null"
     }
     ```

   - **HTTP `400` — falta `username` o está vacío/solo contiene espacios:**

     ```json
     {
       "message": "Username is required"
     }
     ```

   - **HTTP `400` — falta `password` o está vacío/solo contiene espacios:**

     ```json
     {
       "message": "Password is required"
     }
     ```

   - **HTTP `401` — usuario o contraseña inválidos:** el mensaje incluye el
     username recibido:

     ```json
     {
       "message": "Invalid user student or password"
     }
     ```

   - **HTTP `500` — falla de autenticación controlada por el servicio:** el
     detalle interno se registra en el servidor; el cuerpo al cliente es:

     ```json
     {
       "message": "Internal server error",
       "exception": "INTERNAL_ERROR"
     }
     ```

   - **HTTP `403` — origen CORS no permitido:** una prueba observa este estado
     al enviar un origen no configurado. No comprueba ni define el cuerpo
     generado por el filtro CORS; el formato JSON, si lo hay, queda como
     **pregunta abierta** y no se presenta un ejemplo inventado.

   - **Errores del parser o del servidor HTTP:** el contrato de auth no define
     el cuerpo para JSON malformado, un `Content-Type` no soportado u otros
     errores generados antes de entrar al método. Sus cuerpos exactos quedan
     como **pregunta abierta**; no están cubiertos por una prueba de contrato
     (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:26-67`).

   Los ejemplos de `400`, `401` y `500` corresponden a las ramas de
   `AuthResource` y a sus pruebas
   (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:47-71`;
   `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:42-88,136-152`).

6. **Notas de seguridad:**

   - El token es JWT firmado con HS256 e incluye `sub`, `role`, `iat` y `exp`;
     su expiración efectiva es de 3600 segundos
     (`auth/src/main/java/com/bcp/workshop/auth/TokenService.java:19,40-49`).
   - En modo normal se requiere `AUTH_TOKEN_SECRET`, codificado en Base64 y
     con 32 bytes después de decodificar. No enviar ni incluir ese secreto en
     el request (`auth/src/main/java/com/bcp/workshop/auth/TokenService.java:31-37,55-70`;
     `auth/src/main/resources/application.properties:9`).
   - La compatibilidad del token y sus claims con consumidores externos no
     está confirmada en este repositorio. El consumidor debe verificar la
     firma con la misma clave, validar expiración y acordar el uso del claim
     `role` antes de depender de él.
   - `client` no afecta autorización ni claims. El cuerpo de error `401`
     refleja el username enviado; los consumidores no deben usarlo para
     distinguir si una cuenta existe.
