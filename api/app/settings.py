import os
from dataclasses import dataclass

TOKEN_SECRET_ENV = "WORKSHOP_TOKEN_SECRET"
TOKEN_ISSUER_ENV = "WORKSHOP_TOKEN_ISSUER"
TOKEN_AUDIENCE_ENV = "WORKSHOP_TOKEN_AUDIENCE"
LEGACY_TOKENS_ENV = "WORKSHOP_LEGACY_TOKENS_ENABLED"
DEFAULT_TOKEN_ISSUER = "workshop-auth"
DEFAULT_TOKEN_AUDIENCE = "workshop-api"
ENABLED_FLAG_VALUES = frozenset({"1", "true", "yes", "on"})
LEGACY_TOKEN_SECRET = "WORKSHOP_SECRET_2026"


@dataclass(frozen=True)
class TokenSettings:
    secret: str | None
    issuer: str
    audience: str
    legacy_enabled: bool
    legacy_secret: str


def load_token_settings() -> TokenSettings:
    return TokenSettings(
        secret=os.getenv(TOKEN_SECRET_ENV) or None,
        issuer=os.getenv(TOKEN_ISSUER_ENV, DEFAULT_TOKEN_ISSUER),
        audience=os.getenv(TOKEN_AUDIENCE_ENV, DEFAULT_TOKEN_AUDIENCE),
        legacy_enabled=os.getenv(LEGACY_TOKENS_ENV, "true").strip().lower() in ENABLED_FLAG_VALUES,
        legacy_secret=LEGACY_TOKEN_SECRET,
    )
