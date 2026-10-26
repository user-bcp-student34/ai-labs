# Pruebas de contratos HTTP

## auth

Los casos de API usan `@QuarkusTest` y RestAssured contra el contexto HTTP de
Quarkus; SQLite se ejecuta mediante el driver del proyecto, sin un servicio
externo. Las pruebas están en
`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`. El contrato
de referencia es el contrato observado en `docs/ENDPOINTS.md`, no un contrato
externo acordado.

### Matriz de casos

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `GET /q/health` | Health disponible | Camino feliz | HTTP 200, `Content-Type: application/json`, `status` igual a `UP`. PASS |
| `GET /q/health` | No hay health checks registrados | Borde | HTTP 200 y `checks` vacío. PASS |
| `GET /q/health` | Estado DOWN | Error | HTTP 503 está descrito por la extensión, pero auth no registra health checks que lo provoquen. Pendiente; no se fabricó una condición DOWN solo para aprobar el test. |
| `POST /auth/login` | Credenciales de laboratorio válidas y `client` presente | Camino feliz | HTTP 200; usuario, rol, expiración, indicador y token no nulo. PASS |
| `POST /auth/login` | Campo opcional `client` omitido | Borde | HTTP 200 y token emitido. PASS |
| `POST /auth/login` | Username solo con espacios | Borde | HTTP 400; `{"message":"Username is required"}`. PASS |
| `POST /auth/login` | Contraseña con comilla como dato | Borde | HTTP 401; no altera la consulta. PASS |
| `POST /auth/login` | Cuerpo JSON `null` | Error | HTTP 400; `{"message":"Request is null"}`. PASS |
| `POST /auth/login` | Username ausente | Error | HTTP 400; `{"message":"Username is required"}`. PASS |
| `POST /auth/login` | Username vacío/blanco | Error | HTTP 400; `{"message":"Username is required"}`. PASS |
| `POST /auth/login` | Password ausente | Error | HTTP 400; `{"message":"Password is required"}`. PASS |
| `POST /auth/login` | Password vacío/blanco | Error | HTTP 400; `{"message":"Password is required"}`. PASS |
| `POST /auth/login` | Credenciales inválidas | Error | HTTP 401; mensaje `Invalid user student or password`. PASS |
| `POST /auth/login` | Origen CORS no configurado | Error | HTTP 403 y sin header `Access-Control-Allow-Origin`. El cuerpo no forma parte del contrato comprobado. PASS |
| `POST /auth/login` | Excepción controlada del servicio | Error | HTTP 500; cuerpo genérico `message: Internal server error`, `exception: INTERNAL_ERROR`. PASS |
| `POST /auth/login` | JSON malformado o `Content-Type` no soportado | Error del servidor HTTP | El cuerpo y el status del parser/framework están sin especificar en `docs/ENDPOINTS.md`; pendiente de caracterización. |

### Código de pruebas añadidas

Las siguientes pruebas se añadieron en
`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`. Los métodos
de login feliz y de errores ya existentes se mantienen en el mismo archivo.

```java
@Test
void reportsHealthAsUp() {
    given()
            .accept("application/json")
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("status", is("UP"));
}

@Test
void reportsNoHealthChecksWhenNoneAreRegistered() {
    given()
            .accept("application/json")
            .when().get("/q/health")
            .then()
            .statusCode(200)
            .body("checks", hasSize(0));
}

@Test
void logsInWithoutOptionalClientField() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(200)
            .body("user.username", is("student"))
            .body("token", notNullValue());
}

@Test
void rejectsBlankUsername() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"  \",\"password\":\"Workshop123!\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body("message", is("Username is required"));
}

@Test
void rejectsMissingPassword() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"student\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body("message", is("Password is required"));
}

@Test
void returnsGenericErrorWhenAuthenticationServiceFailsOverHttp() {
    QuarkusMock.installMockForType(new AuthService() {
        @Override
        public Optional<LoginResponse> authenticate(String username, String password) {
            throw new AuthenticationServiceException(
                    "sensitive internal detail", new IllegalStateException());
        }
    }, AuthService.class);

    given()
            .contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(500)
            .body("message", is("Internal server error"))
            .body("exception", is("INTERNAL_ERROR"));
}
```

Los casos existentes adicionales se implementan en
`AuthResourceTest.java`: `logsInWithWorkshopUser`,
`rejectsNullRequest`, `rejectsMissingUsername`, `rejectsBlankPassword`,
`rejectsInvalidCredentials`, `treatsQuoteInPasswordAsInvalidCredentials`,
`rejectsUnconfiguredOriginInCorsResponse`,
`allowsDocumentedLocalFrontendOrigin` y
`exposesUnexpectedExceptionDetailsInObservedErrorResponse`. Este último
comprueba directamente el resource; el nuevo
`returnsGenericErrorWhenAuthenticationServiceFailsOverHttp` cubre además la
respuesta HTTP serializada.

### Ejecuciones y cobertura

| componente e IDs | directorio | comando | código de salida | resultado |
|---|---|---|---:|---|
| auth; contrato de login y hallazgos `H-AUTH-01` a `H-AUTH-07`, `H-AUTH-09` | `auth/` | `mvn -B -ntp -Dtest=AuthResourceTest test` | 0 | PASS; 17 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| auth; regresión `H-AUTH-02` a `H-AUTH-07`, `H-AUTH-09` | `auth/` | `mvn -B -ntp verify` | 0 | PASS; 32 pruebas, 0 fallos, 0 errores, 0 omitidas; `jacoco:check` PASS. H-AUTH-08 SCA no forma parte de esta ejecución Maven. |

**Método de medición:** JaCoCo 0.8.12, contador de líneas
(`LINE_COVERED / (LINE_COVERED + LINE_MISSED)`) desde
`auth/target/site/jacoco/jacoco.csv`. Resultado agregado: `214/230 = 93.0%`.
La clase productiva `AuthResource`, ejercitada por las pruebas de este cambio,
queda en `25/25 = 100%`. El gate configurado en `auth/pom.xml` exige al menos
85% por clase para todas las clases productivas; la ejecución informa
“All coverage checks have been met.” JaCoCo no mide cobertura del código de
prueba en sí.

`git diff --check`, ejecutado desde la raíz, terminó sin errores.

### Fallos, pendientes y pruebas propuestas

- **Fallos:** ninguno en las ejecuciones anteriores.
- **Nota de ejecución:** los logs incluyen `AccessDeniedException` cuando
  SQLite intenta borrar una DLL nativa antigua del directorio temporal de
  Windows; ambas ejecuciones terminaron con código 0 y Surefire reportó cero
  errores. El test de 500 también registra intencionalmente el error simulado
  del servicio; el cuerpo HTTP comprobado permanece genérico.
- **Pendiente:** no hay un health check de aplicación que permita producir el
  estado DOWN/HTTP 503 documentado por la extensión. No se añadió un check de
  prueba que altere artificialmente el comportamiento de producción.
- **Pendiente:** el status/cuerpo para JSON malformado o media type no
  soportado, y el cuerpo de CORS 403, no están especificados por auth. No se
  inventaron asserts para cuerpos no observados.
- **Prueba propuesta:** cuando se acuerde e implemente un health check, añadir
  una prueba que lo haga responder DOWN y afirme el status y el cuerpo
  acordados. Caracterizar también los errores del parser y el cuerpo CORS si
  pasan a ser parte del contrato.
