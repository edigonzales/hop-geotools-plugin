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

@Transform(
    id = "GEOTOOLS_VECTOR_WRITER",
    name = "Vector Writer",
    description = "Write vector features to Shapefile or GeoPackage",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry",
    keywords = {"vector", "shapefile", "geopackage", "gis"})
public class VectorWriterMeta extends BaseTransformMeta<VectorWriter, VectorWriterData> {

  @HopMetadataProperty private String fileName;
  @HopMetadataProperty private String layerName;
  @HopMetadataProperty private String geometryField;

  @Override
  public void setDefault() {
    fileName = "";
    layerName = "";
    geometryField = "geometry";
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
    // Sink transform: no output rows.
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
              ICheckResult.TYPE_RESULT_ERROR, "Vector output file name is required", transformMeta));
      return;
    }

    String resolvedFileName = resolve(variables, fileName);
    if (!resolvedFileName.contains("${")) {
      try {
        GeoToolsVectorSupport.detectFormat(Path.of(resolvedFileName));
      } catch (IllegalArgumentException e) {
        remarks.add(
            new CheckResult(ICheckResult.TYPE_RESULT_ERROR, e.getMessage(), transformMeta));
        return;
      }
    }

    if (geometryField == null || geometryField.isBlank()) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR, "Geometry input field is required", transformMeta));
      return;
    }

    if (prev != null && prev.size() > 0 && prev.indexOfValue(resolve(variables, geometryField)) < 0) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              "Geometry input field '" + geometryField + "' was not found",
              transformMeta));
      return;
    }

    remarks.add(new CheckResult(ICheckResult.TYPE_RESULT_OK, "Vector Writer is configured", transformMeta));
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

  public String getGeometryField() {
    return geometryField;
  }

  public void setGeometryField(String geometryField) {
    this.geometryField = geometryField;
  }
}
