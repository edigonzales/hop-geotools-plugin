#!/usr/bin/env python3
from pathlib import Path
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
target = root / "assemblies" / "assemblies-hop-geotools" / "target"
zips = sorted(target.glob("hop-geotools-plugin-*.zip"))
if len(zips) != 1:
    raise SystemExit(f"Expected exactly one plugin ZIP in {target}, found {len(zips)}")

zip_path = zips[0]
with zipfile.ZipFile(zip_path) as archive:
    names = [Path(name).name.lower() for name in archive.namelist() if not name.endswith("/")]

required = [
    "hop-geotools-vector-",
    "gt-main-",
    "gt-shapefile-",
    "gt-geopkg-",
    "sqlite-jdbc-",
]
for fragment in required:
    if not any(fragment in name for name in names):
        raise SystemExit(f"{zip_path.name}: required dependency matching {fragment!r} is missing")

forbidden = ["hop-geometry-type", "jts-core-"]
for fragment in forbidden:
    matches = [name for name in names if fragment in name]
    if matches:
        raise SystemExit(
            f"{zip_path.name}: shared geometry dependency {fragment!r} must not be bundled: {matches}"
        )

print(f"Distribution OK: {zip_path}")
print("  GeoTools vector runtime is bundled")
print("  hop-geometry-type and jts-core remain shared via classLoaderGroup=sogeo-geometry")
