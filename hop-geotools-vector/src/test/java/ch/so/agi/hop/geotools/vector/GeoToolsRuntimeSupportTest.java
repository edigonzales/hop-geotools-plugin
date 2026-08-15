package ch.so.agi.hop.geotools.vector;

import static org.assertj.core.api.Assertions.assertThat;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import tech.units.indriya.function.Calculus;

class GeoToolsRuntimeSupportTest {

  @Test
  void initializesIndriyaAndDecodesSwissCrs() throws Exception {
    GeoToolsRuntimeSupport.initialize();
    GeoToolsRuntimeSupport.initialize();

    assertThat(Calculus.currentNumberSystem()).isNotNull();

    CoordinateReferenceSystem crs = CRS.decode("EPSG:2056", true);
    assertThat(crs).isNotNull();
    assertThat(crs.getName().getCode()).isNotBlank();
  }
}
