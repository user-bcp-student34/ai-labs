# Revisión de parametrización SQL en auth

## AMBITO

Workspace `ai-labs` abierto en su raíz. Todas las rutas parten de esa raíz.
Lee código solo en `auth/`; no leas código de `api/` ni `web/`. Lee
`README.md`, `RETO.md`, `docs/GOOD_PRACTICES.md`, y las secciones de auth de
`HALLAZGOS.md` y `PLAN.md`. Consulta `docs/ENDPOINTS.md` y `docs/TESTING.md`
si existen.

Esta tarea es de solo lectura: no modifiques código, configuración, pruebas ni
documentación. Ejecuta Git desde la raíz; cualquier comando de prueba propuesto
debe indicar `auth/` como directorio de trabajo. Usa `H-AUTH-NN` sin crear ni
renumerar hallazgos.

## CONTEXTO

El componente de autenticación usa Java, Quarkus y SQLite. El login se expone
mediante `POST /auth/login`. `H-AUTH-03` trata la concatenación de credenciales
en SQL y puede figurar como Resuelto en la matriz; verifica el estado actual
sin presuponer que el defecto persista. Sigue la entrada desde el resource
HTTP, pasando por el servicio, hasta la consulta al repositorio.

## OBJETIVO

Determinar, con evidencia del código actual, si `username` o `password`
influyen en una consulta SQL mediante concatenación de strings o se pasan como
parámetros JDBC. Identifica también qué hacen las pruebas existentes con
credenciales válidas, inválidas y una comilla como dato.

## RESTRICCIONES

- No edites archivos ni ejecutes pruebas o ataques contra servicios remotos.
- Usa solo evidencia local y datos ficticios. No incluyas secretos reales ni
  payloads de explotación.
- No des por confirmado ningún candidato de `RETO.md` sin señalar código y
  línea.
- Distingue los hechos observados de hipótesis. Si un comportamiento no se
  puede comprobar leyendo el código, indícalo como pregunta abierta.
- No afirmes que una prueba pasó si solo la leíste o la propusiste.
- Si propones pruebas, manténlas locales y compatibles con el contrato
  observado de `POST /auth/login`.

## CRITERIOS DE ACEPTACION

1. Traza `username` y `password` desde la petición HTTP hasta la consulta de
   persistencia, citando cada salto como `auth/ruta:linea`.
2. Indica si la consulta concatena valores o usa `PreparedStatement` (u otro
   mecanismo parametrizado); no confundas la presencia de texto SQL con la
   interpolación de valores.
3. Comprueba qué pruebas existentes cubren login válido, credenciales
   inválidas y una comilla enviada como dato; cita archivo y línea.
4. Si falta cobertura, propone una prueba local para esos tres casos e indica
   el resultado esperado según `docs/ENDPOINTS.md` o el comportamiento
   observado en el código.
5. Si `H-AUTH-03` existe, informa si la evidencia actual respalda su estado.
   No cambies `HALLAZGOS.md` ni el estado del hallazgo.
6. Enumera archivos ausentes, preguntas abiertas y comandos no ejecutados.

## FORMATO DE SALIDA

Empieza con una tabla:

| afirmación | evidencia (`auth/ruta:linea`) | comprobado o hipótesis | prueba propuesta |
|---|---|---|---|

Después resume el recorrido de la entrada hasta la consulta y el estado de
`H-AUTH-03`. Termina con preguntas abiertas y comandos no ejecutados,
indicando que no se ejecutaron pruebas.
