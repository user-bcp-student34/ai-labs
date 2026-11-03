---
name: api-unit-test-generator
description: Genera pruebas unitarias con pytest para el componente api (FastAPI) a partir de api/app y docs/ENDPOINTS.md, cubriendo camino feliz, casos borde y errores, y reporta la ejecución real y la cobertura del cambio.
---

# Skill: API -> Unit Test Generator

## 1) Metadata
- **Skill ID:** api-unit-test-generator
- **Version:** 0.1.0
- **Purpose:** Estandarizar la creación de pruebas unitarias de `api/`: cada prueba es trazable a un comportamiento documentado, aislada, determinista y ejecutada de verdad, con la cobertura del código modificado medida y declarada.
- **Audience (roles):** Backend, QA, Tech Lead, Cyber
- **When to use:** Al agregar o cambiar código en `api/app/`, antes de refactorizarlo (pruebas de caracterización) o cuando `docs/TESTING.md` muestre casos sin cubrir.
- **When NOT to use:** Cambios en `auth/` o `web/` (otro componente), pruebas de carga o contra servicios reales, y cambios sin efecto en el comportamiento (comentarios, formato).
- **Sensitive inputs:** No incluyas secretos, tokens ni credenciales reales ni valores de producción. Tampoco copies el valor de `LEGACY_SECRET` a pruebas, documentos ni salida.
- **Expected output:** Archivos `api/tests/test_<unidad>.py` y un documento Markdown con las secciones de Output.

## 2) Inputs
Proporciona la información en este formato:

- **Unidad bajo prueba:** módulo, función, clase o endpoint de `api/app/`, con `ruta:línea`.
- **Fuente del comportamiento esperado:** sección de `docs/ENDPOINTS.md` o código; indica si es contrato observado o acordado.
- **Cambio a cubrir:** archivos o rango de Git frente a `main` (base de la cobertura del cambio).
- **Pruebas existentes:** archivos de `api/tests/` con helpers y fixtures reutilizables.
- **Defectos conocidos:** IDs `H-API-NN` de `HALLAZGOS.md` que afecten a la unidad.
- **Entorno:** intérprete de `api/.venv` (Python 3.11-3.13) y dependencias de `api/requirements-dev.txt`.
- **Meta de cobertura / DoD:** por defecto, 85% o más del código modificado (`AGENTS.md`).

## 3) Output
Entrega los archivos de prueba en `api/tests/` y devuelve un documento Markdown con estas secciones y bullets:

1. **Summary**
2. **Scope**
    - Dentro del alcance
    - Fuera del alcance
3. **Supuestos**
4. **Casos de prueba** (tabla: unidad | caso | tipo | resultado esperado | fuente)
    - Tipos: feliz, borde, error, autorización
5. **Código de las pruebas** (ruta de cada archivo y el código nuevo)
6. **Ejecución y cobertura**
    - Comando, directorio, código de salida y resultado reales
    - Cobertura del cambio (método, numerador, denominador) y cobertura global, por separado
7. **Fallos y defectos** (con ID `H-API-NN`)
8. **Pendientes y pruebas propuestas**
9. **Preguntas abiertas y documentos consultados**

## 4) Procedure
1. Lee la unidad, su contrato en `docs/ENDPOINTS.md` y las pruebas existentes de `api/tests/`.
2. Ejecuta la línea base antes de escribir (comando de la sección 5) y anota el resultado.
3. Lista los casos con su fuente. Lo que no esté documentado va a Preguntas abiertas, no a una aserción.
4. Escribe las pruebas, ejecútalas y mide la cobertura del cambio.
5. Redacta el documento. Si el paso lo pide, actualiza `docs/TESTING.md` solo con las filas de api y conserva las de otros componentes.

## 5) Constraints
**Alcance**
- Lee y escribe código solo en `api/`. Ejecuta Git desde la raíz de `ai-labs` y las pruebas con cwd `api/` (`AGENTS.md`).
- No inventes comportamiento: cada caso cita `api/ruta:línea` o una sección de `docs/ENDPOINTS.md`.
- No modifiques `api/app/` para que una prueba pase ni pruebas existentes, salvo que el cambio lo exija.
- No hagas push, tags ni PR (Día 1, `AGENTS.md`).

**Defectos**
- No adaptes una prueba para que pase con un defecto. La prueba del comportamiento correcto va con `pytest.mark.xfail(strict=True, reason="H-API-NN: ...")`. Si fijas el comportamiento actual como contrato observado, dilo en la prueba y en Fallos y defectos.
- No uses `# noqa`, `# pragma: no cover` ni `skip` para ocultar un hallazgo.

**Aislamiento**
- Una prueba, un comportamiento; sin orden entre pruebas, sin `sleep` y sin red real. Para fijar el reloj o una constante usa `monkeypatch`.
- BD y subidas en `tmp_path`: `create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))`. Importar `app.main` ya crea `api/data/workshop.db` por `app = create_app()` (`api/app/main.py:149`); está en `.gitignore`.
- Dobles ya usados: `FakeUpload` con `filename` y `read()` (`api/tests/test_uploads.py`), `monkeypatch` de constantes (`app.uploads.MAX_UPLOAD_BYTES`, `app.authorization.ROLES_WITH_CROSS_OWNER_ACCESS`) y de `AUTH_HEALTH_URL`, y un servidor HTTP local en `127.0.0.1` con puerto libre para `backend-health`.
- Coroutines con `asyncio.run(...)`: `requirements-dev.txt` no incluye `pytest-asyncio`.
- Stack permitido: pytest, pytest-cov, httpx/`TestClient`, coverage.py y biblioteca estándar. No agregues dependencias sin justificarlas.
- Token de prueba: reutiliza el helper de `api/tests/conftest.py`. Si no existe, créalo ahí en lugar de copiar `make_token` a otro archivo, e importa el secreto de `app.main`. Para "secreto incorrecto" usa un texto ficticio. Propón en Pendientes migrar las copias existentes.

**Calidad (clean code de `AGENTS.md`)**
- Nombres que expliquen el propósito: `test_<unidad>_<escenario>_<resultado>`.
- Casos repetidos con `parametrize`, constantes con nombre (`MAX_UPLOAD_BYTES`) en lugar de números sueltos y sin anidamiento profundo.

**Seguridad**
- Si la unidad toca tareas, incluye una prueba de que un usuario no lee ni escribe tareas de otro owner (403, sin datos).
- Si toca SQL, incluye una entrada con comilla simple o texto tipo SQL y comprueba que se trata como dato.
- Si toca archivos, cubre nombre (`..`, separadores, sin extensión), extensión (`.txt`, `.md`) y tamaño (límite exacto y 1 byte por encima).

**Ejecución y cobertura** (cwd `api/`; en Linux/macOS, `.venv/bin/python`)
- Comando: `.venv/Scripts/python.exe -m pytest -p no:warnings -rfEx --cov=app --cov-branch --cov-report=term-missing`
- Gate del laboratorio, desde la raíz: `python tools/lab.py test api` (cobertura global con `--cov-fail-under=85`).
- Cobertura del cambio: líneas añadidas según `git diff -U0 main -- api/app`, desde la raíz (los archivos nuevos sin seguimiento cuentan completos), cruzadas con las líneas ejecutables del informe `--cov-report=json`. Numerador: ejecutadas. Denominador: ejecutables del cambio. Declara el límite: es cobertura de líneas y las ramas del diff no se miden por separado.
- No excluyas código de la medición. Registra la cobertura global aparte.

**Resultados**
- Solo resultados realmente ejecutados: copia comando, directorio, código de salida y conteos (passed, failed, xfailed) de la salida real. Lo que no ejecutaste va a Pendientes. No inventes aprobaciones humanas: la revisión humana de las pruebas queda pendiente hasta que ocurra.
- Mantén el documento, sin contar el código de las pruebas, en 1-2 páginas.

## 6) Validation Rules
La salida es válida si:
- Incluye las nueve secciones definidas en Output.
- Por cada unidad hay al menos un caso feliz, uno borde (payload vacío, archivo sin extensión o límite de tamaño, según aplique) y uno por cada error documentado; los que no aplican se justifican.
- Si la unidad toca tareas, incluye una prueba de acceso a otro owner.
- Cada caso de la tabla tiene fuente y una prueba que lo implementa.
- Todas las pruebas se ejecutaron: código de salida y conteos reales, y los defectos conocidos son `xfail(strict=True)` con su ID.
- La cobertura del cambio es de 85% o más, con numerador y denominador, o la brecha está explicada. La global va por separado.
- No contiene secretos, `# noqa` ni exclusiones.
- No contiene supuestos no declarados.

## 7) Evidence
Adjunta o referencia estos elementos en la entrega:
- `SKILL.md` versionado.
- `examples/input.sample.md`.
- `examples/output.sample.md`.
- Salida de pytest (comando, directorio, código de salida) e informe de cobertura.
- `docs/TESTING.md` actualizado, solo con filas de api, cuando el paso lo pida.
- Tag de versión propuesto: `skill-api-unit-tests-v0.1.0`. No lo crees en el Día 1 (`AGENTS.md` prohíbe tags); queda como pendiente.
