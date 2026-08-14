package ch.so.agi.hop.geotools.vector;

import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;
import org.apache.hop.pipeline.transform.ITransformData;
import org.geotools.api.data.DataStore;
import org.geotools.data.simple.SimpleFeatureIterator;

public class VectorReaderData extends BaseTransformData implements ITransformData {
  boolean initialized;
  DataStore dataStore;
  SimpleFeatureIterator iterator;
  IRowMeta outputRowMeta;
  List<String> attributeNames = new ArrayList<>();
  int geometryIndex = -1;
}
