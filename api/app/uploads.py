from pathlib import Path
from typing import NamedTuple

from fastapi import UploadFile

ALLOWED_UPLOAD_EXTENSIONS = frozenset({".txt", ".md"})
INVALID_UPLOAD_NAMES = frozenset({"", ".", ".."})
BYTES_PER_MIB = 1024 * 1024
MAX_UPLOAD_MIB = 5
MAX_UPLOAD_BYTES = MAX_UPLOAD_MIB * BYTES_PER_MIB
UPLOAD_CHUNK_BYTES = 64 * 1024


class InvalidUploadError(Exception):
    pass


class UploadTooLargeError(Exception):
    pass


class UploadConflictError(Exception):
    pass


class StoredUpload(NamedTuple):
    name: str
    path: Path
    size: int


def safe_upload_name(raw_name: str | None) -> str:
    if raw_name is None:
        raise InvalidUploadError
    name = raw_name.replace("\\", "/").rsplit("/", 1)[-1]
    if name in INVALID_UPLOAD_NAMES or Path(name).suffix.lower() not in ALLOWED_UPLOAD_EXTENSIONS:
        raise InvalidUploadError
    return name


async def read_limited(file: UploadFile) -> bytes:
    content = bytearray()
    while chunk := await file.read(UPLOAD_CHUNK_BYTES):
        content.extend(chunk)
        if len(content) > MAX_UPLOAD_BYTES:
            raise UploadTooLargeError
    return bytes(content)


def write_new_file(target: Path, content: bytes) -> None:
    try:
        with target.open("xb") as handle:
            handle.write(content)
    except FileExistsError as error:
        raise UploadConflictError from error


async def save_requirement(upload_dir: Path, file: UploadFile) -> StoredUpload:
    name = safe_upload_name(file.filename)
    target = upload_dir / name
    if target.resolve().parent != upload_dir.resolve():
        raise InvalidUploadError
    content = await read_limited(file)
    write_new_file(target, content)
    return StoredUpload(name=name, path=target, size=len(content))
