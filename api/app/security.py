import base64
import time

import jwt
from fastapi import HTTPException

from app.settings import TokenSettings

TOKEN_ALGORITHM = "HS256"
TOKEN_PREFIX = "Bearer "
JWT_SEGMENT_SEPARATOR = "."
LEGACY_FIELD_SEPARATOR = "|"
LEGACY_FIELD_COUNT = 4
LEGACY_EXPIRY_INDEX = 2
LEGACY_SECRET_INDEX = 3
INVALID_TOKEN_DETAIL = "Invalid token"
REQUIRED_JWT_CLAIMS = ["sub", "role", "iss", "aud", "exp"]


class InvalidTokenError(Exception):
    pass


def current_epoch() -> int:
    return int(time.time())


def verify_authorization(header: str, settings: TokenSettings) -> dict[str, str]:
    token = header.replace(TOKEN_PREFIX, "")
    try:
        if JWT_SEGMENT_SEPARATOR in token:
            return verify_jwt(token, settings)
        return verify_legacy(token, settings)
    except (InvalidTokenError, jwt.PyJWTError, ValueError) as error:
        raise HTTPException(status_code=401, detail=INVALID_TOKEN_DETAIL) from error


def verify_jwt(token: str, settings: TokenSettings) -> dict[str, str]:
    if not settings.secret:
        raise InvalidTokenError
    claims = jwt.decode(
        token,
        settings.secret,
        algorithms=[TOKEN_ALGORITHM],
        audience=settings.audience,
        issuer=settings.issuer,
        options={"require": REQUIRED_JWT_CLAIMS},
    )
    return {"username": claims["sub"], "role": claims["role"]}


def verify_legacy(token: str, settings: TokenSettings) -> dict[str, str]:
    if not settings.legacy_enabled:
        raise InvalidTokenError
    parts = base64.b64decode(token).decode("utf-8").split(LEGACY_FIELD_SEPARATOR)
    if len(parts) != LEGACY_FIELD_COUNT or parts[LEGACY_SECRET_INDEX] != settings.legacy_secret:
        raise InvalidTokenError
    if int(parts[LEGACY_EXPIRY_INDEX]) < current_epoch():
        raise InvalidTokenError
    return {"username": parts[0], "role": parts[1]}
