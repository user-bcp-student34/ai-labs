# Plan

## auth

### Alcance y criterio de selección

Se seleccionaron H-AUTH-01 a H-AUTH-08, todos en estado “Pendiente” en
`HALLAZGOS.md`. Motivo indicado: priorizar los riesgos de severidad alta y
preparar pruebas de caracterización antes de cualquier cambio. Los cambios de
producción quedan fuera de este paso; no se modifican código, configuración ni
pruebas al elaborar este plan.

### Aprobación humana

La persona usuaria de esta conversación autorizó explícitamente el alcance de
H-AUTH-01 a H-AUTH-12 y sus criterios de aceptación el 2026-10-26, solicitando
que esta aprobación quedara registrada aquí. No se proporcionó nombre ni rol,
por lo que no se atribuye identidad adicional ni se presenta como una revisión
independiente. Esta aprobación no sustituye las coordinaciones de compatibilidad
y configuración que se marcan como pendientes en los hallazgos correspondientes.

### Contrato observado y restricciones

`docs/ENDPOINTS.md` no existe, por lo que no hay contrato acordado que pueda
consultarse. Se toma como comportamiento observado `POST /auth/login`
(`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:15,22-49`):

- Petición nula, username vacío o password vacío: HTTP 400.
- Credenciales no encontradas: HTTP 401.
- Éxito: HTTP 200 con `token`, `expiresIn` (3600), `user` (`username`,
  `displayName`, `role`) y `debug`.
- Error inesperado atrapado por el resource: HTTP 500 con `message` y
  `exception`.

La prueba inicial cubría el login exitoso, el username y la presencia del token
(`auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:12-21`).
Después se agregaron caracterizaciones antes de las modificaciones productivas,
con resultados en el registro de ejecuciones.

Se conservaron la ruta, los estados, los campos públicos y el vencimiento
observados. La representación JWT reemplazó el token Base64; la compatibilidad
con consumidores externos sigue pendiente de confirmación. La lista CORS ya
admite el origen local documentado, pero los orígenes de despliegue deben
configurarse externamente. No se leyó ni modificó código fuera de `auth/`.

### H-AUTH-03 — Concatenación de credenciales en SQL

- **Hallazgo original:** las credenciales se interpolaban en SQL y se ejecutaba
  con `Statement`; el código actual parametriza la búsqueda en
  `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:63-70`.
- **Problema:** una entrada podría cambiar el significado de la consulta y
  causar autenticación incorrecta o acceso no autorizado.
- **Cambio propuesto:** reemplazar la consulta por `PreparedStatement` con
  parámetros para username y password/hash. Mantener la misma semántica de
  búsqueda y distinguir credenciales inválidas de fallos de persistencia.
- **Archivos previstos/actuales:** `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y una prueba
  de repositorio en `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización antes del cambio:** ampliar la prueba HTTP para cubrir
  login válido y credenciales inválidas; agregar un caso de username/password
  con comillas como dato ordinario que debe producir 401 sin alterar la
  consulta. No usar payloads de explotación.
- **Compatibilidad:** conservar estados y estructura de respuestas observados.

### H-AUTH-04 — Token Base64 con secreto embebido

- **Hallazgo original:** el token concatenaba username, rol, vencimiento y
  secreto fijo y luego solo aplicaba Base64. El código actual firma JWT HS256
  (`auth/src/main/java/com/bcp/workshop/auth/TokenService.java:19-51`).
- **Problema:** la codificación no firma los datos; un consumidor que confíe
  en ellos no puede detectar modificaciones.
- **Cambio propuesto:** emitir token firmado con claims explícitos de sujeto,
  rol y expiración, usando una biblioteca mantenida en vez de criptografía
  propia. Mantener `token` como campo string y conservar `expiresIn=3600`
  mediante una constante nombrada. Justificar y revisar cualquier dependencia
  antes de añadirla.
- **Archivos previstos/actuales:** `auth/pom.xml`,
  `auth/src/main/java/com/bcp/workshop/auth/TokenService.java`; configuración en
  `auth/src/main/resources/application.properties`; pruebas en
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización antes del cambio:** afirmar por prueba el HTTP 200, la
  presencia del campo `token`, el resto de campos de éxito y
  `expiresIn=3600`; no fijar Base64 como contrato público.
- **Compatibilidad/riesgo:** la representación del token cambiaría. Antes de
  implementar, coordinar con el responsable de sus consumidores fuera del
  alcance de lectura actual y confirmar cómo validarán la firma. No inventar
  aprobación ni ampliar este cambio a esos consumidores.

### H-AUTH-02 — Contraseñas almacenadas en texto plano

- **Hallazgo original:** la tabla guardaba contraseñas como texto y las
  comparaba directamente. El repositorio actual migra y verifica hashes en
  `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:20-27,82-126,149-155`
  y `auth/src/main/java/com/bcp/workshop/auth/PasswordHasher.java:15-41`.
- **Problema:** quien obtenga la base puede leer credenciales y probar su
  reutilización en otros servicios.
- **Cambio propuesto:** incorporar hash lento con salt (por ejemplo BCrypt,
  según `docs/GOOD_PRACTICES.md`), verificar hashes en el servicio de
  autenticación y dejar de persistir contraseñas en claro. Diseñar una
  migración idempotente que preserve las cuentas existentes antes de retirar
  la columna/valores legados. Para el hallazgo residual de la revisión,
  habilitar `PRAGMA secure_delete=ON` en la conexión de migración antes de
  borrar la tabla con contraseñas legadas; comprobar en prueba que el archivo
  SQLite migrado no conserva los bytes de la contraseña. La rotación de
  respaldos anteriores queda como coordinación externa no comprobable desde
  este componente. Justificar la dependencia de hashing si no hay una
  implementación mantenida disponible en el stack.
- **Archivos previstos/actuales:** `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java`;
  servicio de autenticación y componente de hashing propuestos en H-AUTH-06;
  `auth/pom.xml` solo si se justifica una biblioteca; pruebas de auth y
  persistencia en `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización antes del cambio:** probar login válido del usuario de
  laboratorio, password incorrecto (401), usuario desconocido (401) y
  persistencia/reinicio con una base temporal para definir cómo se conservan
  las cuentas existentes. Añadir una comprobación de que el almacenamiento
  migrado no conserva la contraseña original.
- **Compatibilidad/riesgo:** conservar respuesta, estados y usuarios válidos;
  validar la migración sobre una copia temporal de la base antes de cualquier
  cambio del esquema de ejecución.

### H-AUTH-05 — Secreto hardcodeado y CORS con origen amplio

- **Hallazgo original:** el secreto estaba en una constante Java y CORS usaba
  un patrón amplio. La configuración actual usa `AUTH_TOKEN_SECRET` y una
  lista de orígenes (`auth/src/main/java/com/bcp/workshop/auth/TokenService.java:19-51`;
  `auth/src/main/resources/application.properties:5-9`).
- **Problema:** el secreto queda expuesto en el código y la política CORS puede
  permitir solicitudes desde orígenes no previstos.
- **Cambio propuesto:** leer la clave de firma desde configuración externa,
  sin valor real en el repositorio; externalizar los orígenes CORS permitidos
  y usar una lista explícita. Acordar los orígenes del entorno antes de
  sustituir el patrón actual; el README documenta localhost:4200 para el
  frontend local, pero no establece orígenes de otros entornos.
- **Archivos previstos/actuales:** `auth/src/main/resources/application.properties`;
  `auth/src/main/java/com/bcp/workshop/auth/TokenService.java` (en
  coordinación con H-AUTH-04); pruebas de configuración/CORS en
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización antes del cambio:** probar que el origen local observado
  puede invocar login y registrar el comportamiento actual para otro origen
  como línea base, sin enviar credenciales reales. Antes de implementar la
  lista final, solicitar y registrar los orígenes acordados.
- **Compatibilidad/riesgo:** cambiar CORS puede afectar consumidores
  legítimos; no establecer una lista de producción por suposición.

### H-AUTH-07 — Respuesta de error expone detalles internos

- **Hallazgo original:** el resource devolvía mensaje y clase de excepción y la
  persistencia imprimía stack traces. El manejo actual está en
  `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:38-43` y
  `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:64,81-82`.
- **Problema:** el cliente puede inferir detalles internos; los errores de
  persistencia se mezclan con fallos de autenticación y dificultan el
  diagnóstico controlado.
- **Cambio propuesto:** responder con mensaje genérico y registrar en log del
  servidor un detalle controlado, sin credenciales ni stack trace en HTTP.
  Manejar errores esperados con excepciones específicas y no silenciar fallos
  de persistencia.
- **Archivos previstos/actuales:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`;
  `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java`; pruebas en
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.
- **Caracterización antes del cambio:** con un repositorio de prueba que lance
  una excepción controlada, registrar el HTTP 500 y las claves del JSON
  actuales (`message`, `exception`); añadir también casos 400 y 401 para
  asegurar que no se confunden con errores internos.
- **Compatibilidad/riesgo:** mantener HTTP 500 y una respuesta JSON. Eliminar
  el nombre real de la excepción y sustituir el mensaje por uno genérico
  cambia datos actualmente observables; antes de implementar, confirmar si
  algún consumidor depende de esos valores. No exponer el detalle para
  preservar compatibilidad.

### H-AUTH-08 — Versiones candidatas de dependencias (SCA)

- **Hallazgo:** se confirmó CVE-2022-42889, severidad crítica en el resultado
  de SCA, para `org.apache.commons:commons-text:1.9`
  (`auth/pom.xml:53-54`). El advisory oficial de Apache indica que las
  versiones anteriores a 1.10.0 solo son vulnerables si se usa
  `StringSubstitutor` con entrada no confiable; en `auth/` solo se encontró
  `StringEscapeUtils.escapeHtml4` en `AuthService.java:31`, pero se eligió
  actualizar la dependencia.
- **Problema:** el escáner reportó una versión afectada; el patrón de API
  vulnerable no se observó en el código del componente.
- **Cambio realizado:** actualizar Commons Text a `1.15.0`, versión publicada
  por Apache y superior a la versión corregida indicada por el advisory. No se
  cambió la versión de Quarkus `3.27.4`: el escaneo de Quarkus Core y las
  extensiones declaradas incluidas en el análisis no reportó hallazgos.
- **Archivos tocados:** `auth/pom.xml` y pruebas de caracterización existentes
  en `auth/src/test/java/com/bcp/workshop/auth/AuthServiceTest.java`.
- **Caracterización antes/después:** `authenticatesUserAndMapsEscapedResponse`
  comprueba el `displayName` escapado y el contenido de respuesta antes de la
  actualización (17 pruebas pasan) y después (23 pruebas pasan).
- **Evidencia y límite:** `appmod-cve-assessment` encontró CVE-2022-42889 en
  el escaneo inicial y ningún CVE conocido en el segundo escaneo de 10
  coordenadas directas y seleccionadas transitivas (incluyendo Commons Text,
  Quarkus Core/extensiones y SmallRye JWT). El árbol runtime de Maven se
  resolvió, pero no se enviaron al escáner todas sus dependencias transitivas;
  por ello no se afirma que haya sido un SCA exhaustivo del árbol completo.
  Para cerrar la brecha de revisión, ejecutar el mismo scanner contra todas
  las coordenadas Maven runtime resueltas antes de cerrar H-AUTH-08.
- **Compatibilidad:** el test de login confirma que el escape y el contrato
  probado siguen iguales con Commons Text `1.15.0`.

### H-AUTH-06 — Resource con varias responsabilidades

- **Hallazgo:** `AuthResource.login` valida, consulta la base, crea token y
  arma respuesta en un método anidado
  (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:24-50`).
- **Problema:** responsabilidades acopladas hacen más difícil probar cambios
  de seguridad y aumentan el riesgo de regresión del endpoint.
- **Cambio propuesto:** limitar `AuthResource` a HTTP y mapeo de respuesta;
  mover orquestación a `AuthService`, persistencia a `UserRepository` y
  operaciones de credenciales/token a los componentes de seguridad previstos
  en H-AUTH-02 y H-AUTH-04. Evitar duplicar esas capas al implementar los
  hallazgos relacionados.
- **Archivos a tocar:** `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java`,
  `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java` (migración al
  repositorio si se aprueba), nuevas clases enfocadas en
  `auth/src/main/java/com/bcp/workshop/auth/` y pruebas en
  `auth/src/test/java/com/bcp/workshop/auth/`.
- **Caracterización antes del cambio:** completar primero los casos de
  contrato de H-AUTH-01 y los de login válido, credenciales inválidas, campos
  vacíos y error interno; ejecutarlos antes de mover lógica.
- **Compatibilidad:** separar por cambios revisables, un método/una
  responsabilidad, sin duplicación, anidamiento profundo ni valores mágicos.

### H-AUTH-01 — Contrato observado pendiente de contrastar

- **Hallazgo:** falta `docs/ENDPOINTS.md`; solo se conoce el comportamiento
  observado en código y la prueba feliz existente
  (`auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:15,22-49`;
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:12-21`).
- **Problema:** sin un contrato acordado, un refactor podría cambiar formatos,
  campos o códigos de estado usados por consumidores.
- **Cambio propuesto:** usar el comportamiento observado como baseline y
  ampliar las pruebas de caracterización; solicitar confirmación del contrato
  antes de alterar campos, mensajes o códigos. No crear ni modificar
  `docs/ENDPOINTS.md` en esta etapa.
- **Archivos a tocar:** inicialmente
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`; código
  productivo solo si las pruebas de caracterización demuestran una desviación
  aprobada.
- **Caracterización antes del cambio:** probar 200 y campos de éxito, 400 para
  cuerpo/campos vacíos, 401 para credenciales inválidas y 500 controlado;
  comprobar contenido JSON y estados observados.
- **Compatibilidad:** cualquier diferencia frente a los resultados observados
  requiere decisión registrada antes de implementar.

### Orden propuesto y validación futura

1. Escribir y ejecutar primero las pruebas de caracterización de H-AUTH-01 y
   los casos previos a cada hallazgo; guardar resultados reales, sin
   anticiparlos.
2. Resolver el análisis SCA de H-AUTH-08 y las coordinaciones de contrato
   necesarias para token, CORS y errores.
3. Implementar en cambios revisables: SQL/repositorio, migración y hash de
   contraseñas, token/configuración, errores y separación de capas. Mantener
   los contratos y añadir pruebas antes de cada cambio productivo.
4. Ejecutar pruebas desde `auth/` (`mvn test`) y, para el informe JaCoCo,
   `mvn verify`; comprobar la meta documentada de 85% del código modificado.
   El registro de ejecuciones distingue caracterización previa de validación
   posterior a las correcciones.

### Registro de ejecuciones

- **2026-10-26 — auth; H-AUTH-01 a H-AUTH-08:** desde `auth/`, `mvn test`
  antes de ampliar las pruebas de caracterización. Resultado: PASS, 1 prueba,
  0 fallos, 0 errores.
- **2026-10-26 — auth; H-AUTH-01 a H-AUTH-08:** desde `auth/`, se amplió
  `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` con
  caracterizaciones de respuesta exitosa, validaciones, credenciales
  inválidas, entrada con comilla, CORS observado, almacenamiento actual,
  respuesta de excepción y escape de displayName. Ejecución
  `mvn -B -ntp test`: PASS, 10 pruebas, 0 fallos, 0 errores.
- **2026-10-26 — auth; H-AUTH-03:** antes de cambiar la consulta, la
  caracterización de credenciales con comilla pasó dentro de la ejecución
  anterior (10 pruebas, 0 fallos). Se parametrizó la consulta del repositorio
  `UserRepository` con `PreparedStatement`; después,
  `mvn -B -ntp test` desde `auth/`: PASS, 10 pruebas, 0 fallos, 0 errores.
  La prueba de caracterización y la prueba de corrección pasaron antes y
  después del cambio.
- **2026-10-26 — auth; H-AUTH-02:** las caracterizaciones previas registraron
  la contraseña de laboratorio en claro y el login existente (10 pruebas,
  0 fallos). La primera ejecución de la nueva prueba aislada de migración no
  compiló porque aún no existía el constructor de prueba planeado; se añadió
  antes de implementar la migración. Tras la migración transaccional y el
  verificador PBKDF2, `mvn -B -ntp test` usando Maven con JDK 25 falló
  (12 pruebas, 1 error): JaCoCo 0.8.12 no soporta esos class files y el driver
  SQLite no estaba cargado en la prueba directa. Se cargó el driver en la
  prueba y se repitió `mvn -B -ntp test` desde `auth/` con JDK 21, como pide el
  README: PASS, 12 pruebas, 0 fallos, 0 errores.
- **2026-10-26 — auth; H-AUTH-06 y H-AUTH-07:** antes de separar capas y
  sanitizar errores, el baseline con JDK 21 pasó con 12 pruebas. Durante el
  refactor hubo un fallo de compilación en el mapeo `Response` y luego una
  prueba unitaria que todavía esperaba un `Map`; ambos se corrigieron. Después,
  `mvn -B -ntp test` desde `auth/` con JDK 21: PASS, 15 pruebas, 0 fallos,
  0 errores. Las caracterizaciones del contrato y del error se ejecutaron
  antes y después del refactor.
- **2026-10-26 — auth; H-AUTH-08:** `mvn -B -ntp dependency:list
  -DincludeScope=runtime` desde `auth/` resolvió el árbol de runtime. El SCA
  inicial de 10 coordenadas encontró CVE-2022-42889 crítico en Commons Text
  `1.9`, con recomendación `1.10.0` o posterior. Se actualizó a `1.15.0`;
  la búsqueda en `auth/` encontró `StringEscapeUtils.escapeHtml4`, no
  `StringSubstitutor`. El nuevo escaneo de las mismas 10 coordenadas no
  encontró CVEs conocidos. No se escaneó el árbol transitivo completo.
- **2026-10-26 — auth; H-AUTH-04, H-AUTH-05, H-AUTH-08:** desde `auth/`, con
  JDK 21, `mvn -B -ntp verify`: PASS, 17 pruebas, 0 fallos, 0 errores.
  JaCoCo generó informe. Caracterizaciones de firma JWT, configuración/CORS y
  escape HTML verificadas antes y después de los cambios.
- **2026-10-26 — auth; H-AUTH-02, H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06,
  H-AUTH-07, H-AUTH-08:** una prueba adicional intentó forzar una excepción
  del generador JWT con un username nulo; falló porque la biblioteca lanza
  `NullPointerException`, fuera del contrato de errores probado. Se retiró
  esa prueba artificial, sin cambiar producción. Luego `mvn -B -ntp verify`
  con JDK 21: PASS, 23 pruebas, 0 fallos, 0 errores. Cobertura de líneas
  JaCoCo: 91.4% agregada; todas las clases productivas modificadas superan
  85% (AuthResource 100%, UserRepository 87.0%, AuthService 100%,
  TokenService 89.7%, PasswordHasher 94.3%). CodeQL y Dependency Review
  remotos: no ejecutados.
- **Revisión local — 2026-10-26 — auth; H-AUTH-02, H-AUTH-08:** el revisor
  confirmó que la migración no verifica el borrado de páginas SQLite con datos
  legados y que el SCA cubrió solo 10 coordenadas. H-AUTH-02 y H-AUTH-08
  regresan a “Pendiente” en `HALLAZGOS.md`; no se afirmó recuperación efectiva
  de los bytes ni se reportaron CVEs adicionales en las coordenadas analizadas.
- **Nuevo hallazgo de revisión — H-AUTH-09:** `UserRepository.initialize()`
  crea usuarios de demostración con contraseñas fijas sin limitar el seeding
  a desarrollo/pruebas (`auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:43-53,148-156`).
  La persona usuaria aprobó explícitamente incluir H-AUTH-09 en esta ejecución
  el 2026-10-26. No se modificó código al registrar la aprobación.
- **Estado posterior:** H-AUTH-03, H-AUTH-04, H-AUTH-05, H-AUTH-06 y
  H-AUTH-07 se mantienen resueltos. H-AUTH-01, H-AUTH-02, H-AUTH-08 y
  H-AUTH-09 están pendientes en `HALLAZGOS.md`. CodeQL y Dependency Review
  remotos no ejecutados.

### H-AUTH-09 — Credenciales de demostración conocidas

- **Problema verificado:** una base nueva recibe cuentas de demostración con
  claves fijas conocidas; el hash protege el almacenamiento, no el secreto
  que ya conoce cualquiera. Si el servicio queda accesible en un entorno
  desplegado, las cuentas permiten intentar autenticación con roles válidos.
- **Cambio propuesto:** deshabilitar el seeding por defecto y habilitarlo solo
  en perfiles de desarrollo/pruebas, preservando el entorno de laboratorio.
  No agregar secretos reales ni dejar credenciales conocidas habilitadas en
  producción.
- **Prueba requerida antes/después:** comprobar que el modo normal no crea
  usuarios con credenciales conocidas y que desarrollo/pruebas conserva el
  flujo de laboratorio documentado, sin imprimir ni registrar contraseñas.
- **Evidencia:** `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:43-53,148-156`;
  `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:22-34`;
  `auth/src/main/resources/application.properties:3`.

### Resultados finales de implementación — 2026-10-26

- **H-AUTH-02 — archivo SQLite y contraseñas legadas:** antes del cambio, la
  caracterización comprobaba que la migración dejaba los bytes de la contraseña
  legada en el archivo temporal. Después se activó `PRAGMA secure_delete=ON`
  antes de la transacción de migración. La prueba enfocada posterior confirmó
  que la contraseña se migra y que sus bytes no aparecen en el archivo SQLite
  de prueba. `mvn -B -ntp -Dtest=UserRepositoryTest test` desde `auth/`:
  PASS, 4 pruebas, 0 fallos y 0 errores. El hallazgo sigue **Pendiente** hasta
  que el responsable del entorno confirme la gestión/rotación de respaldos
  previos, que están fuera del alcance de este cambio y no fueron inspeccionados.
- **H-AUTH-09 — cuentas de demostración:** la prueba de caracterización previa
  confirmó que el modo normal insertaba `student` y `trainer`. El repositorio
  ahora solo siembra cuando el modo Quarkus no es `NORMAL`. Las pruebas
  verifican que `NORMAL` no crea esas cuentas y que `TEST` conserva las cuentas
  y la verificación de sus contraseñas de laboratorio. Resultado posterior:
  `mvn -B -ntp -Dtest=UserRepositoryTest test` desde `auth/`: PASS, 4 pruebas,
  0 fallos y 0 errores. H-AUTH-09 queda **Resuelto**.
- **H-AUTH-08 — dependencias runtime:** tras actualizar Quarkus a 3.27.4.1 y
  fijar Jackson 2.21.7 y Netty 4.1.137.Final, además de Commons Text 1.15.0,
  el escaneo final de las coordenadas Maven runtime resueltas, dividido en dos
  lotes, no reportó CVEs conocidos. H-AUTH-08 queda **Resuelto** para el árbol
  runtime escaneado. CodeQL y Dependency Review requieren ejecución remota y
  no se ejecutaron en este recorrido local.
- **Verificación completa posterior:** desde `auth/`, con JDK 21,
  `mvn -B -ntp verify`: PASS, 26 pruebas, 0 fallos, 0 errores y 0 omitidas.
  JaCoCo: 93.0% de líneas agregadas. Cobertura por clase productiva modificada:
  AuthResource 100% (25/25), UserRepository 90.5% (105/116), AuthService
  100% (17/17), TokenService 89.7% (26/29) y PasswordHasher 94.3% (33/35).
  Todas superan la meta de 85%. `git diff --check` desde la raíz: PASS.
- **Estado actualizado en `HALLAZGOS.md`:** H-AUTH-08 y H-AUTH-09 Resueltos;
  H-AUTH-02 Pendiente únicamente por la coordinación externa de respaldos.
  No se modificó el contrato HTTP del login en esta fase. No se inspeccionaron
  ni modificaron otros componentes; no se usaron secretos reales.

### Seguimiento de revisión local — 2026-10-26

- **Consistencia de estados:** se corrigió la nota de evidencia en
  `HALLAZGOS.md` que aún describía H-AUTH-08 y H-AUTH-09 como pendientes.
  La matriz y el resumen ahora concuerdan: H-AUTH-08/H-AUTH-09 Resueltos;
  H-AUTH-02 Pendiente por la coordinación externa de respaldos.
- **Gate JaCoCo:** `auth/pom.xml` ahora ejecuta `jacoco:check` durante
  `verify`, con cobertura de líneas mínima de 85% para cada clase. Esto evita
  que la cobertura alta de una clase oculte una clase por debajo del umbral.
  Desde `auth/`, con JDK 21, `mvn -B -ntp verify`: PASS, 26 pruebas,
  0 fallos, 0 errores, 0 omitidas; JaCoCo informó “All coverage checks have
  been met.” y Maven terminó en BUILD SUCCESS. `git diff --check` desde la
  raíz: PASS.
