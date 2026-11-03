import base64
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.main import create_app

LEGACY_TEST_SECRET = "WORKSHOP_SECRET_2026"


def make_token(username: str = "student", role: str = "STUDENT", expires_at: int | None = None, secret: str = LEGACY_TEST_SECRET) -> str:
    expiry = expires_at if expires_at is not None else int(time.time()) + 3600
    raw = f"{username}|{role}|{expiry}|{secret}"
    return base64.b64encode(raw.encode()).decode()


def auth_headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {make_token(username, role)}"}


@pytest.fixture
def client(tmp_path: Path):
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def error_client(tmp_path: Path):
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as test_client:
        yield test_client


# Token (H-API-01)


def test_token_without_bearer_prefix_is_accepted(client: TestClient) -> None:
    response = client.get("/api/tasks", headers={"Authorization": make_token()})
    assert response.status_code == 200


def test_token_signed_with_other_secret_is_rejected(client: TestClient) -> None:
    response = client.get("/api/tasks", headers={"Authorization": f"Bearer {make_token(secret='OTRO')}"})
    assert response.status_code == 401


def test_expired_token_is_rejected(client: TestClient) -> None:
    response = client.get("/api/tasks", headers={"Authorization": f"Bearer {make_token(expires_at=int(time.time()) - 1)}"})
    assert response.status_code == 401


def test_token_expiring_exactly_now_is_accepted(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    now = 1_900_000_000
    monkeypatch.setattr(time, "time", lambda: float(now))
    response = client.get("/api/tasks", headers={"Authorization": f"Bearer {make_token(expires_at=now)}"})
    assert response.status_code == 200


def test_role_claim_does_not_change_own_task_list(client: TestClient) -> None:
    as_student = client.get("/api/tasks", headers=auth_headers("student", "STUDENT")).json()
    as_trainer_role = client.get("/api/tasks", headers=auth_headers("student", "TRAINER")).json()
    assert as_student == as_trainer_role


# Listado (H-API-03, H-API-05)


def test_list_without_owner_returns_identity_tasks(client: TestClient) -> None:
    tasks = client.get("/api/tasks", headers=auth_headers()).json()
    assert len(tasks) == 2
    assert {task["owner"] for task in tasks} == {"student"}


def test_list_with_empty_owner_falls_back_to_identity(client: TestClient) -> None:
    tasks = client.get("/api/tasks", params={"owner": ""}, headers=auth_headers()).json()
    assert len(tasks) == 2


def test_list_with_own_owner_returns_own_tasks(client: TestClient) -> None:
    tasks = client.get("/api/tasks", params={"owner": "student"}, headers=auth_headers()).json()
    assert len(tasks) == 2


def test_list_with_other_owner_is_forbidden(client: TestClient) -> None:
    response = client.get("/api/tasks", params={"owner": "trainer"}, headers=auth_headers())
    assert response.status_code == 403
    assert response.json() == {"detail": "Forbidden"}


def test_list_with_other_owner_is_forbidden_for_trainer_role(client: TestClient) -> None:
    response = client.get("/api/tasks", params={"owner": "trainer"}, headers=auth_headers("student", "TRAINER"))
    assert response.status_code == 403


def test_list_with_unknown_owner_is_forbidden(client: TestClient) -> None:
    response = client.get("/api/tasks", params={"owner": "nadie"}, headers=auth_headers())
    assert response.status_code == 403


def test_list_with_quote_in_own_username_returns_empty_list(error_client: TestClient) -> None:
    response = error_client.get("/api/tasks", headers=auth_headers("O'Brien"))
    assert response.status_code == 200
    assert response.json() == []


# Resumen (H-API-03, H-API-05)


def test_summary_for_student_matches_seeds(client: TestClient) -> None:
    summary = client.get("/api/summary", headers=auth_headers()).json()
    assert summary == {
        "owner": "student",
        "total": 2,
        "byStatus": {"TODO": 1, "IN_PROGRESS": 1},
        "highPriorityOpen": 1,
    }


def test_summary_for_trainer_matches_seeds(client: TestClient) -> None:
    summary = client.get("/api/summary", headers=auth_headers("trainer")).json()
    assert summary == {
        "owner": "trainer",
        "total": 1,
        "byStatus": {"TODO": 1},
        "highPriorityOpen": 0,
    }


def test_summary_with_other_owner_is_forbidden(client: TestClient) -> None:
    response = client.get("/api/summary", params={"owner": "trainer"}, headers=auth_headers())
    assert response.status_code == 403


def test_summary_with_quote_in_own_username_returns_empty_summary(error_client: TestClient) -> None:
    response = error_client.get("/api/summary", headers=auth_headers("O'Brien"))
    assert response.status_code == 200
    assert response.json() == {"owner": "O'Brien", "total": 0, "byStatus": {}, "highPriorityOpen": 0}


# Alta de tareas (H-API-02, H-API-06)


def test_create_applies_default_status_priority_and_owner(client: TestClient) -> None:
    response = client.post("/api/tasks", headers=auth_headers(), json={"title": "T", "description": "D"})
    assert response.status_code == 201
    created = response.json()
    assert created["status"] == "TODO"
    assert created["priority"] == "MEDIUM"
    assert created["owner"] == "student"
    assert isinstance(created["createdAt"], str)


def test_create_returns_all_submitted_fields(client: TestClient) -> None:
    body = {"title": "Login", "description": "Revisar", "status": "DONE", "priority": "LOW", "owner": "student"}
    created = client.post("/api/tasks", headers=auth_headers(), json=body).json()
    assert {key: created[key] for key in body} == body


def test_create_coerces_numeric_title_to_text(client: TestClient) -> None:
    created = client.post("/api/tasks", headers=auth_headers(), json={"title": 123}).json()
    assert created["title"] == "123"


def test_create_missing_title_and_description_store_empty_text(client: TestClient) -> None:
    created = client.post("/api/tasks", headers=auth_headers(), json={}).json()
    assert created["title"] == ""
    assert created["description"] == ""


def test_create_null_title_is_treated_as_missing(client: TestClient) -> None:
    created = client.post("/api/tasks", headers=auth_headers(), json={"title": None}).json()
    assert created["title"] == ""


def test_create_with_quote_in_title_stores_literal_text(error_client: TestClient) -> None:
    response = error_client.post("/api/tasks", headers=auth_headers(), json={"title": "O'Brien"})
    assert response.status_code == 201
    assert response.json()["title"] == "O'Brien"


def test_create_with_other_owner_is_forbidden(client: TestClient) -> None:
    response = client.post("/api/tasks", headers=auth_headers(), json={"owner": "trainer"})
    assert response.status_code == 403
    assert client.get("/api/tasks", headers=auth_headers("trainer")).json()[0]["title"] == "Trainer notes"


def test_create_with_null_owner_uses_token_owner(client: TestClient) -> None:
    created = client.post("/api/tasks", headers=auth_headers(), json={"owner": None}).json()
    assert created["owner"] == "student"


# Carga de archivos (H-API-04)


def test_upload_keeps_plain_name(client: TestClient, tmp_path: Path) -> None:
    response = client.post(
        "/api/requirements",
        headers=auth_headers(),
        files={"file": ("requirements.txt", b"Login must be accessible", "text/plain")},
    )
    assert response.status_code == 201
    assert response.json()["fileName"] == "requirements.txt"
    assert (tmp_path / "uploads" / "requirements.txt").read_bytes() == b"Login must be accessible"


def test_upload_keeps_name_with_spaces(client: TestClient, tmp_path: Path) -> None:
    response = client.post(
        "/api/requirements",
        headers=auth_headers(),
        files={"file": ("requisitos final.txt", b"x", "text/plain")},
    )
    assert response.status_code == 201
    assert (tmp_path / "uploads" / "requisitos final.txt").exists()


def test_upload_rejects_extension_outside_allowlist(client: TestClient) -> None:
    response = client.post(
        "/api/requirements",
        headers=auth_headers(),
        files={"file": ("tool.exe", b"x", "application/octet-stream")},
    )
    assert response.status_code == 400
    assert response.json() == {"detail": "Invalid upload"}


@pytest.mark.parametrize("name", ["..", "carpeta/", "sin_extension", "nota.TXT.exe"])
def test_upload_rejects_unsafe_or_unlisted_names(client: TestClient, name: str) -> None:
    response = client.post("/api/requirements", headers=auth_headers(), files={"file": (name, b"x", "text/plain")})
    assert response.status_code == 400


def test_upload_name_with_directory_stays_in_upload_dir(client: TestClient, tmp_path: Path) -> None:
    response = client.post("/api/requirements", headers=auth_headers(), files={"file": ("../escape.txt", b"x", "text/plain")})
    assert response.status_code == 201
    assert response.json()["fileName"] == "escape.txt"
    assert not (tmp_path / "escape.txt").exists()
    assert (tmp_path / "uploads" / "escape.txt").exists()


def test_upload_name_with_windows_separator_stays_in_upload_dir(client: TestClient, tmp_path: Path) -> None:
    response = client.post("/api/requirements", headers=auth_headers(), files={"file": ("..\\escape.md", b"x", "text/plain")})
    assert response.status_code == 201
    assert not (tmp_path / "escape.md").exists()
    assert (tmp_path / "uploads" / "escape.md").exists()


def test_upload_without_token_is_rejected(client: TestClient) -> None:
    response = client.post("/api/requirements", files={"file": ("requirements.txt", b"x", "text/plain")})
    assert response.status_code == 401


def test_upload_without_file_field_returns_422(client: TestClient) -> None:
    response = client.post("/api/requirements", headers=auth_headers())
    assert response.status_code == 422


def test_cross_owner_access_is_allowed_when_role_is_configured(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.authorization.ROLES_WITH_CROSS_OWNER_ACCESS", frozenset({"TRAINER"}))
    tasks = client.get("/api/tasks", params={"owner": "trainer"}, headers=auth_headers("student", "TRAINER")).json()
    assert [task["owner"] for task in tasks] == ["trainer"]


# Carga de archivos: límite de tamaño y sobrescritura (H-API-07)


def test_upload_two_mib_file_is_accepted_today(client: TestClient) -> None:
    content = b"x" * (2 * 1024 * 1024)
    response = client.post("/api/requirements", headers=auth_headers(), files={"file": ("requirements.txt", content, "text/plain")})
    assert response.status_code == 201
    assert response.json()["size"] == len(content)


def test_upload_same_name_is_rejected_and_keeps_original(client: TestClient, tmp_path: Path) -> None:
    client.post("/api/requirements", headers=auth_headers(), files={"file": ("requirements.txt", b"primero", "text/plain")})
    response = client.post("/api/requirements", headers=auth_headers(), files={"file": ("requirements.txt", b"segundo", "text/plain")})
    assert response.status_code == 409
    assert response.json() == {"detail": "Upload conflict"}
    assert (tmp_path / "uploads" / "requirements.txt").read_bytes() == b"primero"


def test_upload_at_size_limit_is_accepted(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.uploads.MAX_UPLOAD_BYTES", 10)
    response = client.post("/api/requirements", headers=auth_headers(), files={"file": ("requirements.txt", b"0123456789", "text/plain")})
    assert response.status_code == 201
    assert response.json()["size"] == 10


def test_upload_over_size_limit_is_rejected_and_not_stored(client: TestClient, tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.uploads.MAX_UPLOAD_BYTES", 10)
    response = client.post("/api/requirements", headers=auth_headers(), files={"file": ("requirements.txt", b"01234567890", "text/plain")})
    assert response.status_code == 413
    assert response.json() == {"detail": "Upload too large"}
    assert not (tmp_path / "uploads" / "requirements.txt").exists()


def test_upload_is_read_in_chunks_without_losing_bytes(client: TestClient, tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.uploads.UPLOAD_CHUNK_BYTES", 4)
    content = b"0123456789"
    response = client.post("/api/requirements", headers=auth_headers(), files={"file": ("requirements.txt", content, "text/plain")})
    assert response.status_code == 201
    assert (tmp_path / "uploads" / "requirements.txt").read_bytes() == content


def test_upload_response_keeps_stored_path_size_and_owner(client: TestClient, tmp_path: Path) -> None:
    content = b"Login must be accessible"
    body = client.post("/api/requirements", headers=auth_headers(), files={"file": ("requirements.txt", content, "text/plain")}).json()
    assert body["storedAt"] == str(tmp_path / "uploads" / "requirements.txt")
    assert body["size"] == len(content)
    assert body["owner"] == "student"
