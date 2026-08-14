package ch.so.agi.hop.geotools.vector;

import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import com.atolcd.hop.gis.geometry.curve.CurveWkbWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.geotools.geopkg.geom.GeoPkgGeomWriter;
import org.locationtech.jts.geom.Geometry;

/** Writes GeoPackage feature rows while preserving SQL/MM curved geometry WKB. */
final class GeoPackageFeatureWriter implements AutoCloseable {

  private static final String CURVE_EXTENSION_DEFINITION =
      "http://www.geopackage.org/spec/#extension_geometry_types";

  private final Connection connection;
  private final PreparedStatement insertStatement;
  private final IRowMeta rowMeta;
  private final int geometryFieldIndex;
  private final CurveType curveType;

  private GeoPackageFeatureWriter(
      Connection connection,
      PreparedStatement insertStatement,
      IRowMeta rowMeta,
      int geometryFieldIndex,
      CurveType curveType) {
    this.connection = connection;
    this.insertStatement = insertStatement;
    this.rowMeta = rowMeta;
    this.geometryFieldIndex = geometryFieldIndex;
    this.curveType = curveType;
  }

  static GeoPackageFeatureWriter open(
      Path file,
      String layerName,
      String geometryFieldName,
      IRowMeta rowMeta,
      int geometryFieldIndex,
      Geometry sampleGeometry)
      throws Exception {
    String database = file.toAbsolutePath().normalize().toString().replace('\\', '/');
    Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
    try {
      connection.setAutoCommit(false);
      CurveType curveType = CurveType.fromGeometry(sampleGeometry);
      if (curveType != null) {
        registerCurveType(connection, layerName, geometryFieldName, curveType);
      }
      PreparedStatement insert = connection.prepareStatement(insertSql(layerName, rowMeta));
      return new GeoPackageFeatureWriter(
          connection, insert, rowMeta, geometryFieldIndex, curveType);
    } catch (Exception e) {
      try {
        connection.close();
      } catch (SQLException ignored) {
        // Preserve the original failure.
      }
      throw e;
    }
  }

  void write(Object[] row, Geometry geometry) throws Exception {
    validateGeometryType(geometry);
    for (int i = 0; i < rowMeta.size(); i++) {
      int parameter = i + 1;
      if (i == geometryFieldIndex) {
        bindGeometry(parameter, geometry);
      } else {
        bindAttribute(parameter, rowMeta.getValueMeta(i), row[i]);
      }
    }
    insertStatement.executeUpdate();
  }

  void commit() throws SQLException {
    connection.commit();
  }

  void rollback() throws SQLException {
    connection.rollback();
  }

  @Override
  public void close() throws SQLException {
    SQLException failure = null;
    try {
      insertStatement.close();
    } catch (SQLException e) {
      failure = e;
    }
    try {
      connection.close();
    } catch (SQLException e) {
      if (failure == null) {
        failure = e;
      } else {
        failure.addSuppressed(e);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private void validateGeometryType(Geometry geometry) {
    if (geometry == null) {
      return;
    }
    CurveType actual = CurveType.fromGeometry(geometry);
    if (curveType == null && actual != null) {
      throw new IllegalArgumentException(
          "GeoPackage geometry type was inferred from a linear geometry, but a later row contains "
              + actual.geometryTypeName);
    }
    if (curveType != null && actual != curveType) {
      String actualName = actual == null ? geometry.getGeometryType() : actual.geometryTypeName;
      throw new IllegalArgumentException(
          "GeoPackage geometry type was inferred as "
              + curveType.geometryTypeName
              + " but a later row contains "
              + actualName);
    }
  }

  private void bindGeometry(int parameter, Geometry geometry) throws Exception {
    if (geometry == null) {
      insertStatement.setNull(parameter, Types.BLOB);
      return;
    }
    if (CurveGeometrySupport.isCurveGeometry(geometry)) {
      insertStatement.setBytes(parameter, curvedGeoPackageBinary(geometry));
    } else {
      insertStatement.setBytes(parameter, new GeoPkgGeomWriter(2).write(geometry));
    }
  }

  private void bindAttribute(int parameter, IValueMeta valueMeta, Object value) throws Exception {
    Object normalized = GeoToolsVectorSupport.normalizeAttributeValue(valueMeta, value);
    if (normalized == null) {
      insertStatement.setObject(parameter, null);
      return;
    }

    switch (valueMeta.getType()) {
      case IValueMeta.TYPE_BOOLEAN -> insertStatement.setBoolean(parameter, (Boolean) normalized);
      case IValueMeta.TYPE_INTEGER ->
          insertStatement.setLong(parameter, ((Number) normalized).longValue());
      case IValueMeta.TYPE_NUMBER ->
          insertStatement.setDouble(parameter, ((Number) normalized).doubleValue());
      case IValueMeta.TYPE_BIGNUMBER -> {
        if (normalized instanceof BigDecimal decimal) {
          insertStatement.setBigDecimal(parameter, decimal);
        } else {
          insertStatement.setDouble(parameter, ((Number) normalized).doubleValue());
        }
      }
      case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> {
        if (normalized instanceof java.util.Date date) {
          insertStatement.setString(parameter, Instant.ofEpochMilli(date.getTime()).toString());
        } else {
          insertStatement.setString(parameter, normalized.toString());
        }
      }
      case IValueMeta.TYPE_BINARY -> insertStatement.setBytes(parameter, (byte[]) normalized);
      default -> insertStatement.setString(parameter, normalized.toString());
    }
  }

  private static byte[] curvedGeoPackageBinary(Geometry geometry) {
    Geometry wkbGeometry = CurveGeometrySupport.copy(geometry);
    wkbGeometry.setSRID(0);
    byte[] wkb = new CurveWkbWriter(ByteOrder.LITTLE_ENDIAN).write(wkbGeometry);

    byte flags = 0x01; // Standard GeoPackage binary, no envelope, little endian.
    if (geometry.isEmpty()) {
      flags |= 0x10;
    }

    ByteBuffer buffer = ByteBuffer.allocate(8 + wkb.length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.put((byte) 0x47);
    buffer.put((byte) 0x50);
    buffer.put((byte) 0);
    buffer.put(flags);
    buffer.putInt(geometry.getSRID());
    buffer.put(wkb);
    return buffer.array();
  }

  private static void registerCurveType(
      Connection connection, String layerName, String geometryFieldName, CurveType curveType)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS gpkg_extensions (
            table_name TEXT,
            column_name TEXT,
            extension_name TEXT NOT NULL,
            definition TEXT NOT NULL,
            scope TEXT NOT NULL,
            CONSTRAINT ge_tce UNIQUE (table_name, column_name, extension_name),
            CONSTRAINT ge_rte FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name)
          )
          """);
    }

    try (PreparedStatement update =
        connection.prepareStatement(
            "UPDATE gpkg_geometry_columns SET geometry_type_name = ? WHERE table_name = ? AND column_name = ?")) {
      update.setString(1, curveType.geometryTypeName);
      update.setString(2, layerName);
      update.setString(3, geometryFieldName);
      if (update.executeUpdate() != 1) {
        throw new SQLException(
            "Could not update gpkg_geometry_columns for "
                + layerName
                + "."
                + geometryFieldName);
      }
    }

    try (PreparedStatement insert =
        connection.prepareStatement(
            """
            INSERT OR REPLACE INTO gpkg_extensions
              (table_name, column_name, extension_name, definition, scope)
            VALUES (?, ?, ?, ?, 'read-write')
            """)) {
      insert.setString(1, layerName);
      insert.setString(2, geometryFieldName);
      insert.setString(3, "gpkg_geom_" + curveType.geometryTypeName);
      insert.setString(4, CURVE_EXTENSION_DEFINITION);
      insert.executeUpdate();
    }
  }

  private static String insertSql(String layerName, IRowMeta rowMeta) {
    List<String> columns = new ArrayList<>();
    List<String> placeholders = new ArrayList<>();
    for (int i = 0; i < rowMeta.size(); i++) {
      columns.add(quoteIdentifier(rowMeta.getValueMeta(i).getName()));
      placeholders.add("?");
    }
    return "INSERT INTO "
        + quoteIdentifier(layerName)
        + " ("
        + String.join(", ", columns)
        + ") VALUES ("
        + String.join(", ", placeholders)
        + ")";
  }

  private static String quoteIdentifier(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  enum CurveType {
    CIRCULARSTRING("CIRCULARSTRING"),
    COMPOUNDCURVE("COMPOUNDCURVE"),
    CURVEPOLYGON("CURVEPOLYGON"),
    MULTICURVE("MULTICURVE"),
    MULTISURFACE("MULTISURFACE");

    private final String geometryTypeName;

    CurveType(String geometryTypeName) {
      this.geometryTypeName = geometryTypeName;
    }

    static CurveType fromGeometry(Geometry geometry) {
      Geometry normalized = CurveGeometryAdapter.toHopGeometry(geometry);
      if (normalized instanceof com.atolcd.hop.gis.geometry.curve.CircularString) {
        return CIRCULARSTRING;
      }
      if (normalized instanceof com.atolcd.hop.gis.geometry.curve.CompoundCurve) {
        return COMPOUNDCURVE;
      }
      if (normalized instanceof com.atolcd.hop.gis.geometry.curve.CurvePolygon) {
        return CURVEPOLYGON;
      }
      if (normalized instanceof com.atolcd.hop.gis.geometry.curve.MultiCurve) {
        return MULTICURVE;
      }
      if (normalized instanceof com.atolcd.hop.gis.geometry.curve.MultiSurface) {
        return MULTISURFACE;
      }
      return null;
    }
  }
}
