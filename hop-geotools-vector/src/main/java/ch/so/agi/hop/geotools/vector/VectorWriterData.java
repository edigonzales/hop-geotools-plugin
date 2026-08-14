package ch.so.agi.hop.geotools.vector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.FeatureWriter;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;

public class VectorWriterData extends BaseTransformData implements ITransformData {
  boolean definitionPrepared;
  boolean initialized;
  IRowMeta inputRowMeta;
  int geometryFieldIndex = -1;
  String geometryFieldName;
  String layerName;
  Path outputFile;
  GeoToolsVectorSupport.VectorFormat format;
  DataStore dataStore;
  Transaction transaction;
  FeatureWriter<SimpleFeatureType, SimpleFeature> writer;
  GeoPackageFeatureWriter geoPackageWriter;
  List<Object[]> pendingRows = new ArrayList<>();
}
