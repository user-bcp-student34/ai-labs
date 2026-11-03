import base64
import http.server
import socket
import sys
import threading
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.main import create_app
from app.uploads import MAX_UPLOAD_BYTES

LEGACY_TEST_SECRET = "WORKSHOP_SECRET_2026"
TASK_FIELDS = {"id", "title", "description", "status", "owner", "priority", "createdAt"}


def make_token(username: str = "student", role: str = "STUDENT") -> str:
    raw = f"{username}|{role}|{int(time.time()) + 3600}|{LEGACY_TEST_SECRET}"
    return base64.b64encode(raw.encode()).decode()


def auth_headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {make_token(username, role)}"}


@pytest.fixture
def client(tmp_path: Path):
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def serve_auth_health():
    servers = []

    def start(status: int, body: bytes = b'{"status":"UP"}') -> str:
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self) -> None:
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, format: str, *args: object) -> None:
                return

        server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        servers.append(server)
        return f"http://127.0.0.1:{server.server_port}/q/health"

    yield start
    for server in servers:
        server.shutdown()
        server.server_close()


def closed_port_url() -> str:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    return f"http://127.0.0.1:{port}/q/health"


# GET /api/tasks


def test_list_items_have_exactly_the_documented_fields(client: TestClient) -> None:
    tasks = client.get("/api/tasks", headers=auth_headers()).json()
    assert all(set(task) == TASK_FIELDS for task in tasks)


def test_list_for_user_without_tasks_returns_empty_list(client: TestClient) -> None:
    response = client.get("/api/tasks", headers=auth_headers("nuevo"))
    assert response.status_code == 200
    assert response.json() == []


def test_list_without_authorization_header_returns_401_with_detail(client: TestClient) -> None:
    response = client.get("/api/tasks")
    assert response.status_code == 401
    assert response.json()["detail"].startswith("Invalid token")


def test_list_with_other_owner_returns_403_without_data(client: TestClient) -> None:
    response = client.get("/api/tasks", params={"owner": "trainer"}, headers=auth_headers())
    assert response.status_code == 403
    assert response.json() == {"detail": "Forbidden"}


# POST /api/tasks


def test_create_without_authorization_header_returns_401(client: TestClient) -> None:
    response = client.post("/api/tasks", json={"title": "x"})
    assert response.status_code == 401
    assert response.json()["detail"].startswith("Invalid token")


def test_create_with_array_body_returns_422(client: TestClient) -> None:
    response = client.post("/api/tasks", headers=auth_headers(), json=[1])
    assert response.status_code == 422
    assert response.json()["detail"][0]["type"] == "dict_type"


def test_create_without_body_returns_422(client: TestClient) -> None:
    response = client.post("/api/tasks", headers=auth_headers())
    assert response.status_code == 422
    assert response.json()["detail"][0]["type"] == "missing"


def test_create_with_other_owner_leaves_other_list_unchanged(client: TestClient) -> None:
    client.post("/api/tasks", headers=auth_headers(), json={"owner": "trainer", "title": "intruso"})
    trainer_tasks = client.get("/api/tasks", headers=auth_headers("trainer")).json()
    assert [task["title"] for task in trainer_tasks] == ["Trainer notes"]


# GET /api/summary


def test_summary_for_user_without_tasks_is_empty(client: TestClient) -> None:
    response = client.get("/api/summary", headers=auth_headers("nuevo"))
    assert response.status_code == 200
    assert response.json() == {"owner": "nuevo", "total": 0, "byStatus": {}, "highPriorityOpen": 0}


def test_summary_without_authorization_header_returns_401(client: TestClient) -> None:
    response = client.get("/api/summary")
    assert response.status_code == 401
    assert response.json()["detail"].startswith("Invalid token")


def test_summary_excludes_high_priority_done_and_archived_tasks(client: TestClient) -> None:
    for status in ("DONE", "ARCHIVED"):
        client.post("/api/tasks", headers=auth_headers("nuevo"), json={"title": status, "status": status, "priority": "HIGH"})
    summary = client.get("/api/summary", headers=auth_headers("nuevo")).json()
    assert summary == {"owner": "nuevo", "total": 2, "byStatus": {"DONE": 1, "ARCHIVED": 1}, "highPriorityOpen": 0}


def test_summary_counts_high_priority_open_task(client: TestClient) -> None:
    client.post("/api/tasks", headers=auth_headers("nuevo"), json={"title": "abierta", "priority": "HIGH"})
    summary = client.get("/api/summary", headers=auth_headers("nuevo")).json()
    assert summary["highPriorityOpen"] == 1


# POST /api/requirements


def test_upload_exactly_at_real_size_limit_is_accepted(client: TestClient) -> None:
    content = b"x" * MAX_UPLOAD_BYTES
    response = client.post("/api/requirements", headers=auth_headers(), files={"file": ("limite.txt", content, "text/plain")})
    assert response.status_code == 201
    assert response.json()["size"] == MAX_UPLOAD_BYTES


def test_upload_one_byte_over_real_size_limit_returns_413_and_stores_nothing(client: TestClient, tmp_path: Path) -> None:
    content = b"x" * (MAX_UPLOAD_BYTES + 1)
    response = client.post("/api/requirements", headers=auth_headers(), files={"file": ("grande.txt", content, "text/plain")})
    assert response.status_code == 413
    assert response.json() == {"detail": "Upload too large"}
    assert not (tmp_path / "uploads" / "grande.txt").exists()


@pytest.mark.skipif(sys.platform != "win32", reason="El prefijo de unidad solo es un riesgo en Windows")
@pytest.mark.xfail(strict=True, reason="H-API-04: el prefijo de unidad no se normaliza; fileName no coincide con el archivo guardado")
def test_upload_name_with_drive_prefix_reports_the_stored_name(client: TestClient) -> None:
    body = client.post("/api/requirements", headers=auth_headers(), files={"file": ("C:x.txt", b"x", "text/plain")}).json()
    assert Path(body["storedAt"]).name == body["fileName"]


# GET /api/backend-health


def test_backend_health_returns_auth_status_and_body(client: TestClient, serve_auth_health, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AUTH_HEALTH_URL", serve_auth_health(200))
    response = client.get("/api/backend-health", headers=auth_headers())
    assert response.status_code == 200
    assert response.json() == {"requestedBy": "student", "status": 200, "body": '{"status":"UP"}'}


def test_backend_health_passes_through_auth_error_status(client: TestClient, serve_auth_health, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AUTH_HEALTH_URL", serve_auth_health(503, b"no disponible"))
    response = client.get("/api/backend-health", headers=auth_headers())
    assert response.status_code == 200
    assert response.json() == {"requestedBy": "student", "status": 503, "body": "no disponible"}


def test_backend_health_with_unreachable_auth_returns_200_and_status_zero(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    # Fija el contrato observado en docs/ENDPOINTS.md. El 200 con status 0 es el defecto H-API-16.
    monkeypatch.setenv("AUTH_HEALTH_URL", closed_port_url())
    response = client.get("/api/backend-health", headers=auth_headers())
    body = response.json()
    assert response.status_code == 200
    assert body["status"] == 0
    assert isinstance(body["body"], str) and body["body"] != ""


def test_backend_health_without_authorization_header_returns_401(client: TestClient) -> None:
    response = client.get("/api/backend-health")
    assert response.status_code == 401
    assert response.json()["detail"].startswith("Invalid token")
