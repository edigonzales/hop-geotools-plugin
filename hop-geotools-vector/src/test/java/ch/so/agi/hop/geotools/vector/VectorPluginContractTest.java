package ch.so.agi.hop.geotools.vector;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.hop.core.annotations.Transform;
import org.junit.jupiter.api.Test;

class VectorPluginContractTest {

  @Test
  void readerAndWriterUseSharedGeometryClassLoaderGroup() {
    Transform reader = VectorReaderMeta.class.getAnnotation(Transform.class);
    Transform writer = VectorWriterMeta.class.getAnnotation(Transform.class);

    assertThat(reader).isNotNull();
    assertThat(writer).isNotNull();
    assertThat(reader.classLoaderGroup()).isEqualTo("sogeo-geometry");
    assertThat(writer.classLoaderGroup()).isEqualTo("sogeo-geometry");
  }

  @Test
  void userFacingNamesDoNotExposeGeoTools() {
    Transform reader = VectorReaderMeta.class.getAnnotation(Transform.class);
    Transform writer = VectorWriterMeta.class.getAnnotation(Transform.class);

    assertThat(reader.name()).isEqualTo("Vector Reader");
    assertThat(writer.name()).isEqualTo("Vector Writer");
    assertThat(reader.name()).doesNotContainIgnoringCase("geotools");
    assertThat(writer.name()).doesNotContainIgnoringCase("geotools");
  }

  @Test
  void derivesLayerNameFromOutputFile() {
    assertThat(VectorWriter.defaultLayerName(Path.of("/tmp/parcels.gpkg"))).isEqualTo("parcels");
    assertThat(VectorWriter.defaultLayerName(Path.of("roads.shp"))).isEqualTo("roads");
  }
}
