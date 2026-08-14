package ch.so.agi.hop.geotools.vector;

import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.annotations.Transform;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.geotools.api.data.DataStore;
import org.geotools.api.feature.simple.SimpleFeatureType;

@Transform(
    id = "GEOTOOLS_VECTOR_READER",
    name = "Vector Reader",
    description = "Read vector features from Shapefile or GeoPackage",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"vector", "shapefile", "geopackage", "gis"})
public class VectorReaderMeta extends BaseTransformMeta<VectorReader, VectorReaderData> {

  @HopMetadataProperty private String fileName;
  @HopMetadataProperty private String layerName;
  @HopMetadataProperty private String geometryFieldName;

  @Override
  public void setDefault() {
    fileName = "";
    layerName = "";
    geometryFieldName = "geometry";
  }

  @Override
  public void getFields(
      IRowMeta rowMeta,
      String origin,
      IRowMeta[] info,
      TransformMeta nextTransform,
      IVariables variables,
      IHopMetadataProvider metadataProvider)
      throws HopTransformException {
    String resolvedFileName = resolve(variables, fileName);
    if (resolvedFileName.isBlank() || resolvedFileName.contains("${")) {
      return;
    }

    DataStore store = null;
    try {
      store = GeoToolsVectorSupport.open(Path.of(resolvedFileName));
      String typeName = GeoToolsVectorSupport.resolveTypeName(store, resolve(variables, layerName));
      SimpleFeatureType featureType = store.getSchema(typeName);
      IRowMeta detected = GeoToolsVectorSupport.toHopRowMeta(featureType, geometryFieldName);
      for (int i = 0; i < detected.size(); i++) {
        rowMeta.addValueMeta(detected.getValueMeta(i));
      }
    } catch (Exception e) {
      if (isDebug()) {
        logDebug("Unable to probe vector schema for design-time metadata: " + e.getMessage());
      }
    } finally {
      if (store != null) {
        store.dispose();
      }
    }
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      PipelineMeta pipelineMeta,
      TransformMeta transformMeta,
      IRowMeta prev,
      String[] input,
      String[] output,
      IRowMeta info,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (fileName == null || fileName.isBlank()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "Vector file name is required", transformMeta));
      return;
    }
    String resolved = resolve(variables, fileName);
    if (!resolved.contains("${")) {
      try {
        GeoToolsVectorSupport.detectFormat(Path.of(resolved));
      } catch (IllegalArgumentException e) {
        remarks.add(
            new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
        return;
      }
    }
    remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_OK, "Vector Reader is configured", transformMeta));
  }

  private static String resolve(IVariables variables, String value) {
    if (value == null) {
      return "";
    }
    return variables == null ? value.trim() : variables.resolve(value).trim();
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getLayerName() {
    return layerName;
  }

  public void setLayerName(String layerName) {
    this.layerName = layerName;
  }

  public String getGeometryFieldName() {
    return geometryFieldName;
  }

  public void setGeometryFieldName(String geometryFieldName) {
    this.geometryFieldName = geometryFieldName;
  }
}
