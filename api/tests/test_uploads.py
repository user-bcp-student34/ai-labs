import asyncio
from pathlib import Path

import pytest

from app.uploads import InvalidUploadError, safe_upload_name, save_requirement


class FakeUpload:
    def __init__(self, filename: str | None, content: bytes = b"x") -> None:
        self.filename = filename
        self._content = content

    async def read(self, size: int = -1) -> bytes:
        content, self._content = self._content, b""
        return content


def test_safe_upload_name_rejects_missing_filename() -> None:
    with pytest.raises(InvalidUploadError):
        safe_upload_name(None)


def test_save_requirement_rejects_target_outside_upload_dir(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    upload_dir = tmp_path / "uploads"
    upload_dir.mkdir()
    monkeypatch.setattr("app.uploads.safe_upload_name", lambda raw_name: "../fuera.txt")
    with pytest.raises(InvalidUploadError):
        asyncio.run(save_requirement(upload_dir, FakeUpload("x.txt")))
    assert not (tmp_path / "fuera.txt").exists()
