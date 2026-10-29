#!/usr/bin/env python3
"""Fail when Flyway SQL and Kotlin migrations reuse a version number."""

from collections import defaultdict
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
MIGRATION_DIRS = (
    ROOT / "resource-tracker/src/main/resources/db/migration",
    ROOT / "resource-tracker/src/main/kotlin/db/migration",
)
NAME_PATTERN = re.compile(r"^V(\d+)__.+\.(?:sql|kt)$")


def main() -> int:
    versions: dict[int, list[Path]] = defaultdict(list)
    invalid_names: list[Path] = []

    for directory in MIGRATION_DIRS:
        for path in directory.glob("V*"):
            match = NAME_PATTERN.fullmatch(path.name)
            if match is None:
                invalid_names.append(path)
            else:
                versions[int(match.group(1))].append(path)

    duplicates = {version: paths for version, paths in versions.items() if len(paths) > 1}
    if invalid_names or duplicates:
        for path in invalid_names:
            print(f"Invalid migration filename: {path.relative_to(ROOT)}", file=sys.stderr)
        for version, paths in sorted(duplicates.items()):
            names = ", ".join(str(path.relative_to(ROOT)) for path in paths)
            print(f"Duplicate Flyway version V{version}: {names}", file=sys.stderr)
        return 1

    print(f"Checked {sum(map(len, versions.values()))} migration files; all versions are unique.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
