import base64
import dataclasses
import time
from pathlib import Path

import jwt
import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.settings import TokenSettings, load_token_settings

JWT_SECRET = "jwt-test-secret"
LEGACY_TEST_SECRET = "legacy-test-secret"
ISSUER = "workshop-auth"
AUDIENCE = "workshop-api"
SETTINGS = TokenSettings(
    secret=JWT_SECRET,
    issuer=ISSUER,
    audience=AUDIENCE,
    legacy_enabled=True,
    legacy_secret=LEGACY_TEST_SECRET,
)


def jwt_claims(**changes: object) -> dict[str, object]:
    claims = {"sub": "student", "role": "STUDENT", "iss": ISSUER, "aud": AUDIENCE, "exp": int(time.time()) + 3600}
    claims.update(changes)
    return claims


def jwt_token(claims: dict[str, object], secret: str = JWT_SECRET) -> str:
    return jwt.encode(claims, secret, algorithm="HS256")


def legacy_token(secret: str = LEGACY_TEST_SECRET, username: str = "student") -> str:
    raw = f"{username}|STUDENT|{int(time.time()) + 3600}|{secret}"
    return base64.b64encode(raw.encode()).decode()


def bearer(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def make_client(tmp_path: Path):
    def build(settings: TokenSettings) -> TestClient:
        return TestClient(create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"), settings))

    return build


def assert_rejected(response) -> None:
    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid token"}


# JWT firmado


def test_valid_jwt_lists_tasks_of_subject(make_client) -> None:
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(jwt_token(jwt_claims(sub="student"))))
    assert response.status_code == 200
    assert {task["owner"] for task in response.json()} == {"student"}


def test_valid_jwt_without_bearer_prefix_is_accepted(make_client) -> None:
    response = make_client(SETTINGS).get("/api/tasks", headers={"Authorization": jwt_token(jwt_claims())})
    assert response.status_code == 200


def test_jwt_signed_with_other_secret_is_rejected(make_client) -> None:
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(jwt_token(jwt_claims(), secret="otro")))
    assert_rejected(response)


def test_expired_jwt_is_rejected(make_client) -> None:
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(jwt_token(jwt_claims(exp=int(time.time()) - 1))))
    assert_rejected(response)


def test_jwt_with_other_issuer_is_rejected(make_client) -> None:
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(jwt_token(jwt_claims(iss="otro"))))
    assert_rejected(response)


def test_jwt_with_other_audience_is_rejected(make_client) -> None:
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(jwt_token(jwt_claims(aud="otro"))))
    assert_rejected(response)


def test_jwt_without_expiry_is_rejected(make_client) -> None:
    claims = jwt_claims()
    del claims["exp"]
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(jwt_token(claims)))
    assert_rejected(response)


def test_jwt_without_subject_is_rejected(make_client) -> None:
    claims = jwt_claims()
    del claims["sub"]
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(jwt_token(claims)))
    assert_rejected(response)


def test_unsigned_jwt_with_none_algorithm_is_rejected(make_client) -> None:
    unsigned = jwt.encode(jwt_claims(), None, algorithm="none")
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(unsigned))
    assert_rejected(response)


def test_jwt_is_rejected_when_no_secret_is_configured(make_client) -> None:
    settings = dataclasses.replace(SETTINGS, secret=None)
    response = make_client(settings).get("/api/tasks", headers=bearer(jwt_token(jwt_claims())))
    assert_rejected(response)


def test_jwt_is_accepted_when_legacy_tokens_are_disabled(make_client) -> None:
    settings = dataclasses.replace(SETTINGS, legacy_enabled=False)
    response = make_client(settings).get("/api/tasks", headers=bearer(jwt_token(jwt_claims())))
    assert response.status_code == 200


def test_invalid_jwt_does_not_fall_back_to_legacy(make_client) -> None:
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(jwt_token(jwt_claims(), secret="otro")))
    assert_rejected(response)


# Formato legacy y flag de transición


def test_legacy_token_is_accepted_while_flag_is_on(make_client) -> None:
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(legacy_token()))
    assert response.status_code == 200


def test_legacy_token_is_rejected_when_flag_is_off(make_client) -> None:
    settings = dataclasses.replace(SETTINGS, legacy_enabled=False)
    response = make_client(settings).get("/api/tasks", headers=bearer(legacy_token()))
    assert_rejected(response)


def test_legacy_token_with_other_secret_is_rejected(make_client) -> None:
    response = make_client(SETTINGS).get("/api/tasks", headers=bearer(legacy_token(secret="otro")))
    assert_rejected(response)


def test_missing_authorization_header_is_rejected(make_client) -> None:
    assert_rejected(make_client(SETTINGS).get("/api/tasks"))


def test_malformed_base64_is_rejected(make_client) -> None:
    assert_rejected(make_client(SETTINGS).get("/api/tasks", headers=bearer("no-es-base64!")))


# Configuración desde el entorno


def test_settings_read_secret_and_defaults_from_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("WORKSHOP_TOKEN_SECRET", "de-entorno")
    monkeypatch.delenv("WORKSHOP_TOKEN_ISSUER", raising=False)
    monkeypatch.delenv("WORKSHOP_TOKEN_AUDIENCE", raising=False)
    monkeypatch.delenv("WORKSHOP_LEGACY_TOKENS_ENABLED", raising=False)
    settings = load_token_settings()
    assert settings.secret == "de-entorno"
    assert settings.issuer == "workshop-auth"
    assert settings.audience == "workshop-api"
    assert settings.legacy_enabled is True


def test_empty_secret_in_environment_counts_as_missing(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("WORKSHOP_TOKEN_SECRET", "")
    assert load_token_settings().secret is None


@pytest.mark.parametrize("value", ["false", "0", "no", "off", " FALSE "])
def test_flag_values_that_disable_legacy_tokens(monkeypatch: pytest.MonkeyPatch, value: str) -> None:
    monkeypatch.setenv("WORKSHOP_LEGACY_TOKENS_ENABLED", value)
    assert load_token_settings().legacy_enabled is False
