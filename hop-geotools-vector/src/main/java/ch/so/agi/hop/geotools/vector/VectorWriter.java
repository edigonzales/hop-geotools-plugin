package ch.so.agi.hop.geotools.vector;

import java.nio.file.Path;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.geotools.api.data.DefaultTransaction;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.locationtech.jts.geom.Geometry;

public class VectorWriter extends BaseTransform<VectorWriterMeta, VectorWriterData> {

  public VectorWriter(
      TransformMeta transformMeta,
      VectorWriterMeta meta,
      VectorWriterData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (row == null) {
      finish();
      setOutputDone();
      return false;
    }

    try {
      prepareDefinition();
      Geometry geometry = geometry(row);

      if (!data.initialized) {
        if (geometry == null) {
          data.pendingRows.add(row.clone());
          return true;
        }
        openWriter(geometry);
        flushPendingRows();
      }

      writeRow(row);
      incrementLinesOutput();
      return true;
    } catch (Exception e) {
      rollbackQuietly();
      closeResources();
      if (e instanceof HopException hopException) {
        throw hopException;
      }
      throw new HopTransformException("Unable to write vector feature", e);
    }
  }

  private void prepareDefinition() throws HopTransformException {
    if (data.definitionPrepared) {
      return;
    }

    data.inputRowMeta = getInputRowMeta();
    if (data.inputRowMeta == null) {
      throw new HopTransformException("Vector Writer requires input row metadata");
    }

    String fileName = resolve(meta.getFileName());
    if (fileName == null || fileName.isBlank()) {
      throw new HopTransformException("Vector Writer requires an output file name");
    }
    data.outputFile = Path.of(fileName.trim());
    try {
      GeoToolsVectorSupport.detectFormat(data.outputFile);
    } catch (IllegalArgumentException e) {
      throw new HopTransformException(e.getMessage(), e);
    }

    data.geometryFieldName = resolve(meta.getGeometryField());
    if (data.geometryFieldName == null || data.geometryFieldName.isBlank()) {
      data.geometryFieldName = "geometry";
    } else {
      data.geometryFieldName = data.geometryFieldName.trim();
    }
    data.geometryFieldIndex = data.inputRowMeta.indexOfValue(data.geometryFieldName);
    if (data.geometryFieldIndex < 0) {
      throw new HopTransformException(
          "Geometry input field '" + data.geometryFieldName + "' was not found");
    }

    String configuredLayer = meta.getLayerName() == null ? "" : resolve(meta.getLayerName()).trim();
    data.layerName = configuredLayer.isBlank() ? defaultLayerName(data.outputFile) : configuredLayer;
    data.definitionPrepared = true;
  }

  private void openWriter(Geometry sampleGeometry) throws Exception {
    SimpleFeatureType featureType =
        GeoToolsVectorSupport.buildFeatureType(
            data.layerName, data.inputRowMeta, data.geometryFieldIndex, sampleGeometry);

    data.dataStore = GeoToolsVectorSupport.create(data.outputFile);
    data.dataStore.createSchema(featureType);
    String[] typeNames = data.dataStore.getTypeNames();
    if (typeNames.length == 0) {
      throw new HopTransformException("GeoTools created no writable layer in " + data.outputFile);
    }

    data.transaction = new DefaultTransaction("hop-vector-writer");
    data.writer = data.dataStore.getFeatureWriterAppend(typeNames[0], data.transaction);
    data.initialized = true;

    if (isBasic()) {
      logBasic("Created vector dataset " + data.outputFile + " layer " + typeNames[0]);
    }
  }

  private void flushPendingRows() throws Exception {
    for (Object[] pending : data.pendingRows) {
      writeRow(pending);
      incrementLinesOutput();
    }
    data.pendingRows.clear();
  }

  private void writeRow(Object[] row) throws Exception {
    SimpleFeature feature = data.writer.next();
    for (int i = 0; i < data.inputRowMeta.size(); i++) {
      if (i == data.geometryFieldIndex) {
        continue;
      }
      IValueMeta valueMeta = data.inputRowMeta.getValueMeta(i);
      Object value = GeoToolsVectorSupport.normalizeAttributeValue(valueMeta, row[i]);
      feature.setAttribute(valueMeta.getName(), value);
    }
    feature.setDefaultGeometry(geometry(row));
    data.writer.write();
  }

  private Geometry geometry(Object[] row) throws HopTransformException {
    Object value = row[data.geometryFieldIndex];
    if (value == null) {
      return null;
    }
    if (value instanceof Geometry geometry) {
      return geometry;
    }
    throw new HopTransformException(
        "Geometry field '"
            + data.geometryFieldName
            + "' contains "
            + value.getClass().getName()
            + " instead of a JTS Geometry");
  }

  private void finish() throws HopTransformException {
    if (!data.definitionPrepared) {
      closeResources();
      return;
    }
    if (!data.initialized && !data.pendingRows.isEmpty()) {
      closeResources();
      throw new HopTransformException(
          "Vector Writer cannot infer a geometry type because all input geometries are null");
    }

    try {
      if (data.writer != null) {
        data.writer.close();
        data.writer = null;
      }
      if (data.transaction != null) {
        data.transaction.commit();
      }
    } catch (Exception e) {
      rollbackQuietly();
      throw new HopTransformException("Unable to finalize vector dataset", e);
    } finally {
      closeResources();
    }
  }

  private void rollbackQuietly() {
    if (data.transaction != null) {
      try {
        data.transaction.rollback();
      } catch (Exception e) {
        logError("Unable to roll back vector output transaction", e);
      }
    }
  }

  @Override
  public void dispose() {
    closeResources();
    super.dispose();
  }

  private void closeResources() {
    if (data.writer != null) {
      try {
        data.writer.close();
      } catch (Exception e) {
        logError("Unable to close vector feature writer", e);
      }
      data.writer = null;
    }
    if (data.transaction != null) {
      try {
        data.transaction.close();
      } catch (Exception e) {
        logError("Unable to close vector output transaction", e);
      }
      data.transaction = null;
    }
    if (data.dataStore != null) {
      try {
        data.dataStore.dispose();
      } catch (Exception e) {
        logError("Unable to dispose vector output datastore", e);
      }
      data.dataStore = null;
    }
  }

  static String defaultLayerName(Path file) {
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }
}
