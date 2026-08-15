#!/usr/bin/env python3
from io import BytesIO
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
target = root / "assemblies" / "assemblies-hop-geotools" / "target"
zips = sorted(target.glob("hop-geotools-plugin-*.zip"))
if len(zips) != 1:
    raise SystemExit(f"Expected exactly one plugin ZIP in {target}, found {len(zips)}")

zip_path = zips[0]
with zipfile.ZipFile(zip_path) as archive:
    entries = [name for name in archive.namelist() if not name.endswith("/")]
    names = [Path(name).name.lower() for name in entries]

    required = [
        "hop-geotools-vector-",
        "gt-main-",
        "gt-shapefile-",
        "gt-geopkg-",
        "gt-epsg-hsql-",
        "sqlite-jdbc-",
        "imagen-core-",
        "indriya-",
        "systems-common-",
        "unit-api-",
    ]
    for fragment in required:
        if not any(fragment in name for name in names):
            raise SystemExit(f"{zip_path.name}: required dependency matching {fragment!r} is missing")

    forbidden = [
        "hop-geometry-type",
        "jts-core-",
        "gt-coverage-",
    ]
    for fragment in forbidden:
        matches = [name for name in names if fragment in name]
        if matches:
            raise SystemExit(
                f"{zip_path.name}: dependency {fragment!r} must not be bundled in the vector MVP: {matches}"
            )

    indriya_entries = [
        entry for entry in entries if Path(entry).name.lower().startswith("indriya-") and entry.endswith(".jar")
    ]
    if len(indriya_entries) != 1:
        raise SystemExit(
            f"{zip_path.name}: expected exactly one Indriya runtime JAR, found {indriya_entries}"
        )

    service_path = "META-INF/services/tech.units.indriya.spi.NumberSystem"
    with archive.open(indriya_entries[0]) as nested_file:
        with zipfile.ZipFile(BytesIO(nested_file.read())) as nested:
            if service_path not in nested.namelist():
                raise SystemExit(
                    f"{zip_path.name}: {indriya_entries[0]} is missing {service_path}"
                )
            provider = nested.read(service_path).decode("utf-8").strip()
            if "tech.units.indriya.function.DefaultNumberSystem" not in provider.splitlines():
                raise SystemExit(
                    f"{zip_path.name}: Indriya NumberSystem service does not declare DefaultNumberSystem"
                )

size_mib = zip_path.stat().st_size / (1024 * 1024)
print(f"Distribution OK: {zip_path} ({size_mib:.1f} MiB)")
print("  GeoTools vector + EPSG/units runtime is bundled")
print("  Indriya NumberSystem service metadata is present")
print("  hop-geometry-type and jts-core remain shared via classLoaderGroup=sogeo-geometry")
print("  gt-coverage is not bundled; ImageN core remains as a required gt-main dependency")
