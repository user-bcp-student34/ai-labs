# Squad Planning -> Jira Task List: Squad Pagos

*Extracto ilustrativo: muestra las secciones 1, 4, 5, 6, 8 y 9. Los datos son ficticios.*

## 1. Summary
- 8 correos pendientes en 7 grupos, que dan 8 tareas nuevas por 26 pts.
- Capacidad del squad: 31 pts. Asignados: 21 pts (68%). Sin asignar: T-08 (5 pts).
- El DoD del equipo (pruebas unitarias, documentación breve, PR revisado) se aplica a cada tarea.

## 4. Clasificación de correos
| Grupo | Correos | Clasificación | Criterio | Resultado |
|---|---|---|---|---|
| G-1 | C-01, C-02 | Duplicado | Mismo entregable (listado mensual de transferencias en CSV) y mismo componente; solo cambian remitente y motivo | T-01 y T-02: una necesidad dividida por especialidad |
| G-2 | C-03 | Relacionado con G-1 | Mismo componente (listado), entregable distinto (filtro frente a exportación) | T-03, vinculada a T-01 y T-02 |
| G-3 | C-04 | Distinto | Componente y entregable propios (logs de la API) | T-04 y T-07 |
| G-4 | C-05 | Distinto | Fallo del reintento, sin solapamiento | T-05 y T-06 |
| G-5 | C-07 | Distinto | Revisión de accesos, sin solapamiento | T-08 |
| G-6 | C-08 | Ya existe en Jira | Mismo entregable y componente que SQD-087 | Sin tarea nueva: vincular C-08 a SQD-087 |
| G-7 | C-06 | No accionable | Agradecimiento sin solicitud | Sin tarea |

## 5. Lista de tareas para Jira
| ID | Título | Tipo | Dificultad | Especialidad | Asignado | Prioridad | Depende de | Correos |
|---|---|---|---|---|---|---|---|---|
| T-01 | Exportar el listado mensual de transferencias a CSV | Tarea | Media (3) | Backend | Ana | Sin definir | - | C-01, C-02 |
| T-02 | Botón de descarga del CSV en el listado | Tarea | Baja (2) | Frontend | Luis | Sin definir | T-01 | C-01, C-02 |
| T-03 | Filtro por rango de fechas en el listado | Historia | Media (3), provisional | Frontend | Luis | Sin definir | - | C-03 |
| T-04 | Ocultar números de cuenta en los logs de la API | Tarea | Media (3) | Backend | Pablo | Sin definir | - | C-04 |
| T-05 | Corregir el error 500 al reintentar una transferencia | Bug | Alta (5), provisional | Backend | Ana | Alta (C-05: "urgente") | - | C-05 |
| T-06 | Prueba automatizada que reproduzca el reintento fallido | Tarea | Media (3) | QA | Marta | Alta (C-05: "urgente") | - | C-05 |
| T-07 | Verificar que los logs no muestran números de cuenta | Tarea | Baja (2) | Cyber | Sofía | Sin definir | T-04 | C-04 |
| T-08 | Revisar los accesos al servicio de transferencias | Tarea | Alta (5) | Cyber | Sin asignar | Plazo 2026-11-20 (C-07) | - | C-07 |

- **T-01:** el CSV descargado contiene todas las transferencias del listado mensual.
- **T-02:** al pulsar el botón se descarga el CSV generado por T-01.
- **T-03:** con un rango de fechas aplicado, el listado muestra solo las transferencias dentro del rango. Relacionada con T-01 y T-02.
- **T-04:** los logs de la API no contienen números de cuenta completos.
- **T-05:** reintentar una transferencia no devuelve 500 en el caso que reproduce T-06.
- **T-06:** existe una prueba automatizada que reproduce el error y pasa cuando T-05 está corregida.
- **T-07:** la revisión de los logs posterior a T-04 no encuentra números de cuenta completos, con evidencia adjunta.
- **T-08:** revisión de accesos completada antes del 2026-11-20.

## 6. Capacidad y carga por persona
| Persona | Especialidad | Capacidad | Asignado | Restante | Utilización |
|---|---|---|---|---|---|
| Ana | Backend | 10 | 8 (T-01, T-05) | 2 | 80% |
| Luis | Frontend | 8 | 5 (T-02, T-03) | 3 | 63% |
| Marta | QA | 6 | 3 (T-06) | 3 | 50% |
| Pablo | Backend, Arquitectura | 4 | 3 (T-04) | 1 | 75% |
| Sofía | Cyber | 3 | 2 (T-07) | 1 | 67% |
| **Total** | | **31** | **21** | **10** | **68%** |

Utilización redondeada a entero.

**Notas de asignación**
- T-05 y T-06 se asignaron primero por la señal "urgente" de C-05. T-05 fue a Ana porque Pablo tenía 4 pts y la tarea pide 5.
- T-04 fue a Pablo porque a Ana le quedaban 2 pts y la tarea pide 3.

**Sin asignar**
- T-08 (Cyber, 5 pts): Sofía es la única persona con la especialidad y le queda 1 pt tras T-07.

## 8. Comprobaciones
- Correos recibidos: 8 = 6 en tareas (C-01 a C-05 y C-07) + 1 ya existente (C-08) + 1 no accionable (C-06).
- Tareas con dificultad, especialidad, correo fuente y persona o motivo: 8 de 8.
- Asignaciones con especialidad declarada: 7 de 7. Personas sobre su capacidad: 0.
- Tabla de capacidad: 21 pts asignados = suma de T-01 a T-07 (3 + 2 + 3 + 3 + 5 + 3 + 2). Con T-08 (5 pts), el total es 26 pts.

## 9. Preguntas abiertas
1. ¿C-01 y C-02 piden el mismo listado, formato y campos? Se fusionaron como Duplicado a partir de los resúmenes. Confirmarlo con ambos remitentes y definir qué campos lleva el CSV.
2. ¿La API ya admite filtrar por fechas? Ni los correos ni el backlog lo dicen. Si no, T-03 necesita una tarea de Backend y su dificultad es provisional.
3. ¿Cómo se resuelve T-08 (5 pts, plazo 2026-11-20) si Cyber tiene 1 pt libre? Opciones para el squad: dividirla, pedir apoyo de Cyber de otro squad o renegociar el plazo. Si se divide, la capacidad de Sofía (3 pts) se disputa con T-07 (2 pts).
4. ¿Qué prioridad tienen T-01 a T-04 y T-07? Sin señal en los correos ni del PO, se ordenaron por antigüedad del correo (supuesto).
5. ¿Es correcta la dificultad provisional de T-05? La causa del error intermitente no se conoce. A Ana le quedarían 2 pts de margen.
