# Revisión de runbook: INS-120 (nueva instalación de la API en api-qa-01)

*Datos ficticios. Los valores sensibles del ticket están redactados.*

## 1. Summary
- Ticket INS-120: nueva instalación de la API en api-qa-01 (Ubuntu 22.04, rol api, entorno QA).
- Comparables: 5 consultados, 3 incluidos (2 sin revertir y 1 revertido) y 2 excluidos.
- Propuesta: añadir 2 pasos, quitar 1 y revisar 2.
- Advertencia alta: el paso 1 de INS-120 repite la instalación con `chmod 777` que INS-101 revirtió. Además, el ticket contiene un token de prueba en texto plano, redactado en esta salida.

## 2. Scope
**Dentro del alcance**
- Los 8 pasos de INS-120.
- Contraste con INS-087, INS-095 y INS-110, y exclusión de INS-102 e INS-033.
- Advertencias de la reversión de INS-095.

**Fuera del alcance**
- Ejecución de pasos y acceso a api-qa-01 o a Jira.
- Pasos de web de INS-110 (rol web no involucrado en INS-120).
- Aprobación del cambio.

## 3. Supuestos
- S1: La exportación contiene todos los tickets Resueltos de tipo y rol consultados. No se verificó en Jira.
- S2: Jira no tiene estado "Revertido" en este flujo. La reversión se detecta por enlace "es revertido por".
- S3: La similitud usa cuatro ejes: rol, SO y versión, componente y herramienta.
- S4: Un faltante requiere al menos 2 fuentes no revertidas y al menos el 50% de ellas.
- S5: INS-101 existe y su estado no se consultó.
- S6: El entorno objetivo es QA. Producción no se evaluó.

## 4. Tickets comparables
| Clave | Tipo | Roles | SO/versión | Estado | Revertido | Similitud | Uso |
|---|---|---|---|---|---|---|---|
| INS-087 | Nueva instalación | api | Ubuntu 22.04 | Resuelto | No | Alta (4/4) | Incluido |
| INS-095 | Nueva instalación | api | Ubuntu 22.04 | Resuelto | Sí, por INS-101 | Alta (4/4) | Incluido solo como advertencia |
| INS-110 | Nueva instalación | api, web | Ubuntu 20.04 | Resuelto | No | Alta (3/4, SO distinto) | Incluido |
| INS-102 | Actualización | api | Ubuntu 22.04 | Resuelto | No | — | Excluido: tipo distinto |
| INS-033 | Nueva instalación | auth | Ubuntu 22.04 | Resuelto | No | — | Excluido: rol auth no involucrado |

## 5. Pasos faltantes recomendados
| ID | Paso propuesto | Rol | Fase | Soporte | Confianza | Acción |
|---|---|---|---|---|---|---|
| F-1 | Definir `WORKSHOP_UPLOAD_DIR`, crear el directorio y asignar su propiedad al usuario del servicio | api | Configuración | INS-087, INS-110 | Media (2 fuentes sin revertir) | **Añadir**; sustituye al `chmod 777` del ticket |
| F-2 | Verificar `GET /api/backend-health` y leer el campo `status`, no solo el código HTTP | api | Verificación | INS-087, INS-110 | Media (2 fuentes sin revertir) | **Añadir** |

- F-2: `backend-health` responde 200 aunque falle la conexión con auth (H-API-16, `HALLAZGOS.md`). La señal está en el campo `status`.
- Descartado: el plan de rollback de INS-087 no tiene otro soporte sin revertir. Confianza Baja; no se recomienda como faltante.

## 6. Pasos sobrantes
| ID | Paso del ticket | Rol | Soporte | Riesgo | Acción |
|---|---|---|---|---|---|
| S-1 | Dar permisos `chmod 777` al directorio uploads (paso 6) | api | Solo INS-095, revertido | Alto: cualquier usuario del host puede leer y escribir las subidas | **Quitar** |
| S-2 | Probar `GET /api/tasks` con un token de prueba escrito en el ticket (paso 7) | api | Concepto en INS-087, con token generado por script | Alto: credencial en texto plano en Jira | **Revisar**: usar un token generado en el momento |
| S-3 | Verificar que `/docs` responde (paso 8) | api | Sin soporte en comparables no revertidos | Bajo | **Revisar**: es válido, pero ningún comparable lo hace |

## 7. Advertencias de reversión
| Ticket | Revertido por | Motivo documentado | Pasos afectados | Recomendación |
|---|---|---|---|---|
| INS-095 | INS-101 (enlace "es revertido por"; estado no consultado) | "chmod 777 expone las subidas a todos los usuarios del host" | Creación del directorio uploads con `chmod 777` | **No usar** este paso. INS-120 lo repite (S-1). Los pasos de Python, requirements y `WORKSHOP_DB_PATH` de INS-095 no fueron revertidos, pero ya están respaldados por INS-087 y INS-110. |

## 8. Riesgos y mitigaciones
- **R1: pocas fuentes.** F-1 y F-2 solo tienen dos fuentes sin revertir, así que la confianza es Media. Mitigación: validar en QA con el responsable de la API antes de usar el paso en otro entorno.
- **R2: token en texto plano.** El ticket INS-120 contiene un token de prueba. Mitigación: la salida lo redacta, Cyber revisa si era real y lo rota si lo era, y el equipo deja de pegar credenciales en Jira.
- **R3: copia del paso revertido.** S-1 repite el `chmod 777` revertido en INS-095. Mitigación: quitar S-1 y comprobar que el directorio queda con propiedad del usuario de servicio tras F-1.
- **R4: versión de Python distinta.** INS-120 usa 3.13 y el comparable INS-110 usa 3.11. Mitigación: confirmar que `requirements.txt` instala en 3.13. No se ha comprobado.

## 9. Comprobaciones
- Comparables: 5 consultados = 3 incluidos + 2 excluidos, cada excluido con motivo.
- Revertidos incluidos: 1 (INS-095), con motivo en Advertencias.
- Adiciones: 2, cada una con 2 fuentes sin revertir.
- Pasos de web de INS-110: no contados, porque el rol web no está en INS-120.
- Sobrantes: 3 (1 Quitar, 2 Revisar).
- Redacción: 1 token redactado y 0 IPs. La salida no contiene secretos.

## 10. Preguntas abiertas
1. ¿INS-101 sigue vigente? Su estado no está en la exportación.
2. ¿El token de prueba de INS-120 era real? Si lo era, Cyber debe rotarlo.
3. ¿Existe un usuario de servicio definido para la API en api-qa-01? F-1 lo asume.
4. ¿INS-120 necesita el rol web? El ticket no lo menciona.
5. ¿El plan de rollback de INS-087 debe ser obligatorio en el estándar del equipo?
