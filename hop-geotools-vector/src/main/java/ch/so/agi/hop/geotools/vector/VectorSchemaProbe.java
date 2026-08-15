package ch.so.agi.hop.geotools.vector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.geotools.api.data.DataStore;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.feature.type.GeometryDescriptor;

final class VectorSchemaProbe {

  record FieldDefinition(String name, String type) {}

  record LayerDefinition(
      String name, String geometryFieldName, String geometryType, List<FieldDefinition> fields) {
    LayerDefinition {
      fields = List.copyOf(fields);
    }
  }

  private VectorSchemaProbe() {}

  static List<LayerDefinition> readLayers(Path file) throws IOException {
    DataStore store = null;
    try {
      store = GeoToolsVectorSupport.open(file);
      List<LayerDefinition> layers = new ArrayList<>();
      for (String typeName : store.getTypeNames()) {
        SimpleFeatureType featureType = store.getSchema(typeName);
        GeometryDescriptor geometryDescriptor = featureType.getGeometryDescriptor();
        String geometryFieldName =
            geometryDescriptor == null ? "" : geometryDescriptor.getLocalName();
        String geometryType =
            geometryDescriptor == null || geometryDescriptor.getType().getBinding() == null
                ? ""
                : geometryDescriptor.getType().getBinding().getSimpleName();

        List<FieldDefinition> fields = new ArrayList<>();
        for (AttributeDescriptor descriptor : featureType.getAttributeDescriptors()) {
          if (!(descriptor instanceof GeometryDescriptor)) {
            fields.add(
                new FieldDefinition(descriptor.getLocalName(), displayAttributeType(descriptor)));
          }
        }
        layers.add(new LayerDefinition(typeName, geometryFieldName, geometryType, fields));
      }
      return List.copyOf(layers);
    } finally {
      if (store != null) {
        store.dispose();
      }
    }
  }

  static LayerDefinition resolveLayer(List<LayerDefinition> layers, String requestedLayer) {
    if (layers == null || layers.isEmpty()) {
      throw new IllegalArgumentException("Vector dataset contains no layers.");
    }
    if (requestedLayer == null || requestedLayer.isBlank()) {
      return layers.get(0);
    }
    for (LayerDefinition layer : layers) {
      if (layer.name().equals(requestedLayer)) {
        return layer;
      }
    }
    for (LayerDefinition layer : layers) {
      if (layer.name().equalsIgnoreCase(requestedLayer)) {
        return layer;
      }
    }
    throw new IllegalArgumentException("Layer '" + requestedLayer + "' not found.");
  }

  static String formatFieldPreview(LayerDefinition layer) {
    StringBuilder preview = new StringBuilder();
    preview.append("Layer: ").append(layer.name()).append('\n');
    preview
        .append("Geometry type: ")
        .append(layer.geometryType().isBlank() ? "(none)" : layer.geometryType())
        .append('\n');
    preview
        .append("Geometry field: ")
        .append(layer.geometryFieldName().isBlank() ? "(none)" : layer.geometryFieldName())
        .append('\n');
    preview.append("Fields:");
    if (layer.fields().isEmpty()) {
      preview.append("\n- (none)");
    } else {
      for (FieldDefinition field : layer.fields()) {
        preview.append("\n- ").append(field.name()).append(" (").append(field.type()).append(')');
      }
    }
    return preview.toString();
  }

  private static String displayAttributeType(AttributeDescriptor descriptor) {
    Class<?> binding = descriptor.getType().getBinding();
    if (binding == null) {
      return "STRING";
    }
    if (Boolean.class.isAssignableFrom(binding) || boolean.class.equals(binding)) {
      return "BOOLEAN";
    }
    if (Byte.class.isAssignableFrom(binding)
        || Short.class.isAssignableFrom(binding)
        || Integer.class.isAssignableFrom(binding)
        || Long.class.isAssignableFrom(binding)
        || byte.class.equals(binding)
        || short.class.equals(binding)
        || int.class.equals(binding)
        || long.class.equals(binding)) {
      return "INTEGER";
    }
    if (Number.class.isAssignableFrom(binding)
        || float.class.equals(binding)
        || double.class.equals(binding)) {
      return "NUMBER";
    }
    if (java.util.Date.class.isAssignableFrom(binding)) {
      return "DATE";
    }
    return "STRING";
  }
}
