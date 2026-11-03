from fastapi import HTTPException

ROLES_WITH_CROSS_OWNER_ACCESS: frozenset[str] = frozenset()


def resolve_task_owner(identity: dict[str, str], requested_owner: str | None) -> str:
    if not requested_owner or requested_owner == identity["username"]:
        return identity["username"]
    if identity["role"] in ROLES_WITH_CROSS_OWNER_ACCESS:
        return requested_owner
    raise HTTPException(status_code=403, detail="Forbidden")
