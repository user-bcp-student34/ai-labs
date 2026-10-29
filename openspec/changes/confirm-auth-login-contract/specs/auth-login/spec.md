# Spec Delta

## Purpose

Define el contrato HTTP observable del endpoint de login para que consumidores y pruebas de caracterización puedan depender de reglas estables de validación, resultados de autenticación y campos de respuesta.

## ADDED Requirements

### Requirement: MUST preservarse el contrato documentado de solicitudes de login

El sistema MUST (DEBE) exponer `POST /auth/login` como un endpoint JSON que no requiere un header de autorización previo. `username` y `password` son cadenas obligatorias y no vacías. El campo opcional `client` MUST NOT (NO DEBE) ser necesario para autenticarse.

#### Scenario: Request nulo
- **WHEN** un consumidor envía `POST /auth/login` con `Content-Type: application/json` y un cuerpo JSON `null`
- **THEN** el endpoint responde HTTP 400 con un cuerpo JSON cuyo `message` es `Request is null`

#### Scenario: Username ausente o en blanco
- **WHEN** un consumidor omite `username`, lo establece como null o solo envía espacios en blanco mientras proporciona una contraseña
- **THEN** el endpoint responde HTTP 400 con un cuerpo JSON cuyo `message` es `Username is required`

#### Scenario: Password ausente o en blanco
- **WHEN** un consumidor proporciona un username no vacío y omite `password`, lo establece como null o solo envía espacios en blanco
- **THEN** el endpoint responde HTTP 400 con un cuerpo JSON cuyo `message` es `Password is required`

#### Scenario: Se omite el client opcional
- **WHEN** un consumidor envía credenciales válidas sin el campo opcional `client`
- **THEN** la solicitud puede obtener la misma respuesta de autenticación exitosa que una solicitud que incluye `client`

### Requirement: MUST preservarse el resultado de autenticación documentado para login

El sistema MUST (DEBE) distinguir las credenciales inválidas de una autenticación exitosa mediante el estado HTTP y los campos JSON documentados.

#### Scenario: Las credenciales no existen o no coinciden
- **WHEN** un consumidor envía credenciales sintácticamente válidas que no autentican
- **THEN** el endpoint responde HTTP 401 con un cuerpo JSON cuyo `message` es `Invalid user <username> or password`, usando el username enviado en lugar de `<username>`

#### Scenario: Las credenciales autentican correctamente
- **WHEN** un consumidor envía credenciales válidas con o sin `client`
- **THEN** el endpoint responde HTTP 200 con un cuerpo JSON que contiene un `token` no vacío, `expiresIn` igual a 3600 y un objeto `user` con `username`, `displayName` y `role`
- **AND** la respuesta contiene `debug` igual a `authenticated-with-legacy-flow`
- **AND** las pruebas tratan el token como dinámico y opaco, y no registran ni comparan su valor

### Requirement: MUST preservarse los errores y el comportamiento de origen documentados para login

El sistema MUST (DEBE) conservar la respuesta genérica de error de servidor y el comportamiento ante orígenes no permitidos documentados para el endpoint de login. Este contrato no especifica el cuerpo de la respuesta 403.

#### Scenario: Falla el servicio de autenticación
- **WHEN** la autenticación falla por un error interno del servicio
- **THEN** el endpoint responde HTTP 500 con un cuerpo JSON que contiene `message` igual a `Internal server error` y `exception` igual a `INTERNAL_ERROR`
- **AND** los detalles internos de la excepción no se incluyen en el cuerpo de respuesta

#### Scenario: El origen no está permitido
- **WHEN** una solicitud de login contiene un `Origin` que no está configurado como permitido
- **THEN** el endpoint responde HTTP 403 y no devuelve el header `Access-Control-Allow-Origin`
- **AND** este contrato no define el cuerpo de respuesta para este rechazo CORS