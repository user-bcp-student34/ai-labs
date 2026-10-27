# Hallazgos

## auth

Seguimiento del flujo de login. Se conserva la numeración del discovery y
los estados reflejan el trabajo ejecutado y sus límites; la aprobación humana
registrada está en `PLAN.md`. La evidencia distingue patrones observados,
resultados de pruebas y verificaciones aún pendientes.

| id | componente | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|---|
| H-AUTH-03 | auth | Concatenación de credenciales en SQL | Vulnerabilidad | Al interpolar username y password en el SQL ejecutado por `Statement`, una entrada especialmente construida podría alterar la consulta y eludir la verificación o acceder a datos. No se probaron payloads. | `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:63-70` | alta | Resuelto — la consulta usa `PreparedStatement` con parámetros; caracterización y regresión pasan. |
| H-AUTH-04 | auth | Token Base64 con secreto embebido y sin firma visible | Vulnerabilidad | Base64 solo codificaba los campos y el secreto estaba fijo en código; no había integridad verificable. | `auth/src/main/java/com/bcp/workshop/auth/TokenService.java:19-51`; `auth/src/main/resources/application.properties:9`; `auth/src/test/java/com/bcp/workshop/auth/TokenServiceTest.java:33-50` | alta | Resuelto — se genera JWT HS256 con `sub`, `role`, `iat`, `exp` y clave externa; test verifica claims y firma. La compatibilidad del consumidor fuera de auth sigue pendiente. |
| H-AUTH-02 | auth | Contraseñas almacenadas en texto plano | Vulnerabilidad | Las contraseñas legadas se migran a hashes y `secure_delete` se activa antes de la migración; el test verifica que los bytes ya no aparezcan en el archivo SQLite de prueba. No se verificaron ni rotaron copias de seguridad anteriores externas al componente. | `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:56,118-146`; `auth/src/test/java/com/bcp/workshop/auth/UserRepositoryTest.java:60-68` | alta | Pendiente — corrección y prueba del archivo activo completadas; falta confirmar la gestión/rotación de respaldos previos, fuera del alcance local. |
| H-AUTH-05 | auth | Secreto hardcodeado y CORS con origen amplio | Vulnerabilidad | El secreto fijo podía recuperarse del código y el patrón de CORS permitía orígenes no previstos. | `auth/src/main/java/com/bcp/workshop/auth/TokenService.java:24-37,57-67`; `auth/src/main/resources/application.properties:5,9`; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:96-119` | alta | Resuelto — la clave se obtiene de `AUTH_TOKEN_SECRET`, el CORS acepta el origen local documentado por defecto y otros orígenes requieren `AUTH_CORS_ORIGINS`; no se usaron secretos reales. Configuración de despliegue externa pendiente. |
| H-AUTH-07 | auth | Respuesta de error expone detalles internos | Vulnerabilidad | La respuesta exponía mensaje y clase de excepción, y la persistencia imprimía stack traces; esto podía revelar detalles internos o convertir fallos de base en credenciales inválidas. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:38-43`; `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:64,81-82` | media | Resuelto — el cliente recibe mensaje/código genéricos, el detalle se registra en el servidor y los errores de persistencia ya no se ocultan como login fallido. |
| H-AUTH-08 | auth | Commons Text 1.9 reportado por SCA con CVE-2022-42889 | Vulnerabilidad | El SCA inicial reportó severidad crítica para Commons Text 1.9; el advisory afecta el uso de `StringSubstitutor` con entrada no confiable. No se encontró esa API en auth. Commons Text se actualizó a 1.15.0 y el SCA final de las coordenadas runtime resueltas no reportó CVEs conocidos. | `auth/pom.xml:13,53-54`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:31` | alta | Resuelto — dependencia parcheada y escaneo final del árbol runtime resuelto sin CVEs conocidos; CodeQL y Dependency Review remotos no ejecutados. |
| H-AUTH-09 | auth | Credenciales de demostración conocidas se inicializan sin restricción de entorno | Vulnerabilidad | La inicialización crea cuentas de demostración con contraseñas fijas conocidas; habilitarlas en producción permitiría autenticarse con credenciales públicas. El repositorio ahora limita el seeding a modos distintos de `NORMAL`. | `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:37-47,56-62,79-80`; `auth/src/test/java/com/bcp/workshop/auth/UserRepositoryTest.java:72-97` | alta | Resuelto — `LaunchMode.NORMAL` no crea usuarios de demostración y `LaunchMode.TEST` conserva las cuentas del laboratorio; pruebas enfocadas y suite completa pasan. |
| H-AUTH-06 | auth | Resource mezcla validación, negocio, persistencia y respuesta | Refactor | La mezcla y el anidamiento hacían más difícil probar y cambiar responsabilidades sin afectar el endpoint; aumentaba el riesgo de regresiones durante futuras modificaciones. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:17-58`; `auth/src/main/java/com/bcp/workshop/auth/AuthService.java:10-38`; `auth/src/main/java/com/bcp/workshop/auth/UserRepository.java:19,63-80` | baja | Resuelto — se separaron resource HTTP, servicio de autenticación y repositorio; las pruebas de caracterización/regresión pasan. |
| H-AUTH-01 | auth | Contrato observado de login pendiente de contrastar con contrato acordado | Sugerencia | El código permite observar ruta y respuestas, pero falta la especificación acordada; no puede confirmarse si estados, campos y mensajes son el contrato que debe preservarse. | `auth/src/main/java/com/bcp/workshop/auth/AuthResource.java:15,22-24,26-49`; `auth/src/test/java/com/bcp/workshop/auth/AuthResourceTest.java:12-21` | baja | Pendiente |

### Notas de evidencia y límites

- Los siete hallazgos sembrados de auth en `RETO.md` tienen evidencia de su
  presencia como patrón o versión declarada en el código/manifiesto. Para
  H-AUTH-08, el escaneo inicial detectó CVE-2022-42889 en Commons Text 1.9.
  Tras actualizar Commons Text, Quarkus, Jackson y Netty, el escaneo final de
  las coordenadas runtime Maven resueltas, en dos lotes, no reportó CVEs
  conocidos. La verificación corresponde a ese árbol runtime; CodeQL y
  Dependency Review remotos no se ejecutaron.
- `docs/ENDPOINTS.md` no existe. El contrato observado es `POST /auth/login`
  (`AuthResource.java:15,22-24`): el código contempla petición/campos vacíos
  con 400, credenciales no encontradas con 401 y éxito con token, `expiresIn`,
  usuario y `debug` (`AuthResource.java:26-49`). La prueba inspeccionada solo
  cubre el caso exitoso y comprueba username y token
  (`AuthResourceTest.java:12-21`); no hay contrato acordado disponible para
  contrastar esos comportamientos.
- `docs/TESTING.md` y `prompts/` tampoco existen. En la verificación final con
  JDK 21, `mvn -B -ntp verify` terminó con 26 pruebas, 0 fallos, 0 errores y
  0 omitidas. JaCoCo midió 93.0% de cobertura de líneas agregada; las clases
  productivas modificadas superan 85%: AuthResource 100%, UserRepository
  90.5%, AuthService 100%, TokenService 89.7% y PasswordHasher 94.3%.
- CodeQL y Dependency Review remotos no se ejecutaron. La compatibilidad del
  token JWT con consumidores externos y los orígenes CORS de despliegue siguen
  pendientes de coordinación. El escaneo SCA final de las coordenadas runtime
  resueltas no reportó CVEs conocidos. H-AUTH-02 permanece pendiente por la
  gestión externa de respaldos previos; H-AUTH-09 está Resuelto mediante la
  restricción del seeding a modos no normales.
- H-AUTH-02 permanece pendiente solo por la verificación y gestión de copias
  de seguridad previas, que no puede probarse desde este entorno. La prueba
  local confirma que el archivo SQLite migrado no contiene los bytes de la
  contraseña legada de prueba. H-AUTH-09 y H-AUTH-08 quedan resueltos conforme
  a sus pruebas y escaneo descritos arriba.

### Historial del discovery anterior

La primera versión registró H-AUTH-01 a H-AUTH-07 como “Confirmado” por
inspección estática y H-AUTH-08 como pendiente de análisis SCA; también dejó
pendiente contrastar el contrato externo y advirtió que no se habían ejecutado
pruebas. Se conservan esos IDs y esas conclusiones en la matriz actual, que usa
“Pendiente” como estado inicial solicitado para el seguimiento.


### AUTH Discovery Analysis

- H-AUTH-10: Diferencia temporal entre usuario existente e inexistente
Estado: CANDIDATO
Evidencia: AuthService.java:25 omite la verificación PBKDF2 si no encuentra el usuario; PasswordHasher.java:15 fija 310.000 iteraciones. Ambos flujos pueden terminar como 401: AuthResource.java:66-67.
Impacto: Las diferencias de latencia podrían permitir inferir si un nombre de usuario existe.
Prioridad propuesta: Media.
Contrato potencialmente afectado: Privacidad y comportamiento observable de POST /auth/login.
Cómo validarlo: Medir repetidamente, en entorno autorizado y controlado, la latencia para usuario existente con contraseña incorrecta y para usuario inexistente; confirmar también que ambos reciben el mismo estado y estructura de respuesta.

- H-AUTH-11: Campo debug fijo en la respuesta exitosa
Estado: CANDIDATO
Evidencia: AuthService.java:12 define un marcador fijo y AuthService.java:33-37 lo incluye al construir la respuesta; el campo forma parte de LoginResponse.java:3.
Impacto: Expone un indicador interno del flujo en cada login exitoso y puede fijar accidentalmente un detalle de implementación en el contrato público.
Prioridad propuesta: Baja.
Contrato potencialmente afectado: Campos de la respuesta exitosa de POST /auth/login.
Cómo validarlo: Confirmar con los consumidores si el campo es requerido y probar que la respuesta contiene únicamente los campos acordados.

- H-AUTH-12: Emisor y audiencia del JWT no están declarados explícitamente
Estado: CANDIDATO
Evidencia: TokenService.java:44-49 configura sub, role, iat y exp, sin declarar explícitamente emisor ni audiencia.
Impacto: Si los consumidores requieren validar emisor o audiencia, el token podría no satisfacer ese contrato. La HU no confirma ese requisito.
Prioridad propuesta: Baja, pendiente de confirmar las expectativas de los consumidores.
Contrato potencialmente afectado: Claims del JWT y validación del token en consumidores externos.
Cómo validarlo: Contrastar la configuración de validación de los consumidores con los claims emitidos y acordar si se requieren emisor o audiencia antes de definir una prueba de contrato.