package ch.so.agi.hop.geotools.vector;

import java.nio.file.Path;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.feature.type.GeometryDescriptor;
import org.locationtech.jts.geom.Geometry;

public class VectorReader extends BaseTransform<VectorReaderMeta, VectorReaderData> {

  public VectorReader(
      TransformMeta transformMeta,
      VectorReaderMeta meta,
      VectorReaderData data,
      int copyNr,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transformMeta, meta, data, copyNr, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    if (!data.initialized) {
      initialize();
    }

    if (data.iterator.hasNext()) {
      SimpleFeature feature = data.iterator.next();
      Object[] row = RowDataUtil.allocateRowData(data.outputRowMeta.size());
      int index = 0;
      for (String attributeName : data.attributeNames) {
        row[index++] = feature.getAttribute(attributeName);
      }
      Object defaultGeometry = feature.getDefaultGeometry();
      row[data.geometryIndex] =
          defaultGeometry instanceof Geometry geometry
              ? CurveGeometryAdapter.toHopGeometry(geometry)
              : defaultGeometry;
      putRow(data.outputRowMeta, row);
      return true;
    }

    closeResources();
    setOutputDone();
    return false;
  }

  private void initialize() throws HopTransformException {
    String fileName = resolve(meta.getFileName());
    if (fileName == null || fileName.isBlank()) {
      throw new HopTransformException("Vector Reader requires a file name");
    }

    try {
      data.dataStore = GeoToolsVectorSupport.open(Path.of(fileName.trim()));
      String layer = meta.getLayerName() == null ? "" : resolve(meta.getLayerName()).trim();
      String typeName = GeoToolsVectorSupport.resolveTypeName(data.dataStore, layer);
      SimpleFeatureSource source = data.dataStore.getFeatureSource(typeName);
      SimpleFeatureType featureType = source.getSchema();

      data.attributeNames.clear();
      for (AttributeDescriptor descriptor : featureType.getAttributeDescriptors()) {
        if (!(descriptor instanceof GeometryDescriptor)) {
          data.attributeNames.add(descriptor.getLocalName());
        }
      }
      data.outputRowMeta =
          GeoToolsVectorSupport.toHopRowMeta(featureType, meta.getGeometryFieldName());
      data.geometryIndex = data.outputRowMeta.size() - 1;
      data.iterator = source.getFeatures().features();
      data.initialized = true;

      if (isBasic()) {
        logBasic("Opened vector dataset " + fileName + " layer " + typeName);
      }
    } catch (Exception e) {
      closeResources();
      throw new HopTransformException("Unable to open vector dataset: " + fileName, e);
    }
  }

  @Override
  public void dispose() {
    closeResources();
    super.dispose();
  }

  private void closeResources() {
    if (data.iterator != null) {
      try {
        data.iterator.close();
      } catch (Exception e) {
        logError("Unable to close vector feature iterator", e);
      }
      data.iterator = null;
    }
    if (data.dataStore != null) {
      try {
        data.dataStore.dispose();
      } catch (Exception e) {
        logError("Unable to dispose vector datastore", e);
      }
      data.dataStore = null;
    }
  }
}
