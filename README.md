# Apache Hop GeoTools Plugin

GeoTools-based geospatial transforms for Apache Hop.

GeoTools is deliberately an implementation detail. The transforms exposed in Hop use functional names and live in the `Geospatial` category.

## MVP

The first implementation focuses on vector file I/O:

- **Vector Reader**
  - Shapefile (`.shp`)
  - GeoPackage (`.gpkg`)
  - optional layer name; the first layer is used when omitted
  - attributes become normal Hop fields
  - geometry is emitted using the existing Hop `Geometry` value type
- **Vector Writer**
  - Shapefile (`.shp`)
  - GeoPackage (`.gpkg`)
  - optional layer name; otherwise the output filename is used
  - schema is derived from the incoming Hop row metadata
  - geometry type is inferred from the first non-null geometry

The MVP intentionally does not include reprojection, clipping, filtering, PostGIS/WFS, GeoJSON, raster processing, append/overwrite modes, or generic GeoTools DataStore configuration.

## Geometry type and class loading

This plugin depends on [hop-geometry-type-plugin](https://github.com/edigonzales/hop-geometry-type-plugin).

Both plugins use the Hop class-loader group `sogeo-geometry`. The GeoTools plugin therefore declares `hop-geometry-type` and `jts-core` as provided dependencies and **does not package them in its ZIP**. JTS and the `ValueMetaGeometry` implementation come from the separately installed Geometry type plugin, avoiding two incompatible JTS `Geometry` classes in the same pipeline.

The vector distribution excludes GeoTools' raster `gt-coverage` module. `org.eclipse.imagen:imagen-core` is still present because GeoTools 35 declares it as a direct runtime dependency of `gt-main`; it is therefore part of the GeoTools core stack, not evidence that raster processing has been bundled into this MVP.

## Curved geometries

The Hop geometry value type is the canonical in-pipeline representation for SQL/MM curved geometries. GeoTools curve implementations are adapted at the `Vector Reader` boundary, so downstream transforms see the curve classes from `hop-geometry-type-plugin`, not `org.geotools.geometry.jts.*` curve classes.

The following 2D geometry types are preserved when reading and writing GeoPackage:

- `CIRCULARSTRING`
- `COMPOUNDCURVE`
- `CURVEPOLYGON`
- `MULTICURVE`
- `MULTISURFACE`

For GeoPackage output the writer registers the corresponding `gpkg_geom_<TYPE>` read-write extension, stores the extended type in `gpkg_geometry_columns`, and writes the exact SQL/MM curve WKB from the shared geometry type. This avoids the implicit linearization that occurs when a curved `LineString` is passed to the standard JTS `WKBWriter`.

Shapefile has no native curved geometry type. Curves are therefore explicitly linearized before they are handed to the Shapefile writer. The current curve implementation is 2D; Z/M curve ordinates are not supported yet.

## Build and test

Requirements:

- Java 17+
- Maven
- a local checkout of `hop-geometry-type-plugin`

Install the Geometry type snapshot first, then build this repository:

```bash
mvn -f ../hop-geometry-type-plugin/pom.xml -U clean install
mvn -U clean verify
python3 scripts/check-distribution.py
```

The tests perform real Shapefile and GeoPackage writes/reads in temporary directories. Curve tests perform file roundtrips for `CIRCULARSTRING`, `COMPOUNDCURVE`, and `CURVEPOLYGON`, verify the GeoPackage extension metadata, and verify explicit Shapefile linearization. CI runs the same build on Linux, macOS and Windows.

## Releases

Pushes to `main` publish the installable plugin distribution as a public GitHub Release after the Maven tests and `scripts/check-distribution.py` have passed.

The release contains exactly one platform-independent asset:

```text
hop-geotools-plugin-<version>.zip
```

The ZIP contains `plugins/transforms/geotools-vector/` and its GeoTools runtime dependencies. It deliberately does **not** contain `hop-geometry-type` or `jts-core`; the final Hop distribution supplies those once through the shared Geometry Type plugin.

`hop-distributions` consumes this GitHub Release asset directly. The Maven repositories are only needed for Java build dependencies such as the provided `hop-geometry-type` artifact.

## Fast local Hop development

The intended directory layout is:

```text
sources/
├── hop-geometry-type-plugin/
└── hop-geotools-plugin/
```

With a local Hop installation in `$HOP_HOME`, the standard development entry point builds both plugins, runs the tests, installs both plugin distributions and restarts Hop GUI:

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

If the Geometry type repository is elsewhere:

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME" /path/to/hop-geometry-type-plugin
```

The script installs:

```text
$HOP_HOME/plugins/misc/hop-geometry-type
$HOP_HOME/plugins/transforms/geotools-vector
```

and then restarts the local Hop GUI. Startup output is written to `${TMPDIR:-/tmp}/hop-geotools-dev-hop.log`.

See [docs/dev-setup.md](docs/dev-setup.md) for the individual steps and troubleshooting notes.

## Quick manual test

After running the development script, create a pipeline like:

```text
Vector Reader                    Vector Writer
------------                    -------------
input.shp             --->       output.gpkg
layer: (empty)                   layer: parcels
geometry field: geometry        geometry field: geometry
```

Run the pipeline and then use another `Vector Reader` on `output.gpkg` to verify the roundtrip.

## Modules

```text
hop-geotools-plugin
├── hop-geotools-vector
│   ├── Vector Reader
│   └── Vector Writer
└── assemblies/assemblies-hop-geotools
```

Raster support will be added separately as functional transforms such as `Raster Reproject` and `Raster Clip`; it is intentionally not part of the vector MVP.
