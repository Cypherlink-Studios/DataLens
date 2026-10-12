package dev.darkblade.datalens.core.serialize;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.darkblade.datalens.model.DataNode;
import dev.darkblade.datalens.model.DataType;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes and deserializes {@link DataNode} trees to JSON and YAML.
 * Uses Jackson for JSON and SnakeYAML (bundled in Paper) for YAML.
 */
public final class SerializationService {

    private final ObjectMapper mapper;

    public SerializationService() {
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ── JSON ──────────────────────────────────────────────────────────────────

    /** Serializes a DataNode tree to a pretty-printed JSON string. */
    public String toJson(DataNode node) {
        try {
            return mapper.writeValueAsString(toJacksonNode(node, mapper));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize DataNode to JSON", e);
        }
    }

    /** Deserializes a JSON string back into a DataNode tree. */
    public DataNode fromJson(String json) {
        try {
            return fromMap(mapper.readValue(json, Map.class), "root");
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize DataNode from JSON", e);
        }
    }

    // ── YAML ──────────────────────────────────────────────────────────────────

    /** Serializes a DataNode tree to a YAML string. */
    public String toYaml(DataNode node) {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);
        return yaml.dump(toMap(node));
    }

    // ── SNBT ──────────────────────────────────────────────────────────────────

    /** Serializes a DataNode tree to a Stringified NBT (SNBT) string. */
    public String toSnbt(DataNode node) {
        StringBuilder sb = new StringBuilder();
        appendSnbt(node, sb);
        return sb.toString();
    }

    private void appendSnbt(DataNode node, StringBuilder sb) {
        if (node == null) {
            sb.append("{}");
            return;
        }

        switch (node.getType()) {
            case COMPOUND -> {
                sb.append("{");
                List<DataNode> children = node.getChildren();
                for (int i = 0; i < children.size(); i++) {
                    if (i > 0) sb.append(",");
                    DataNode child = children.get(i);
                    appendKey(child.getKey(), sb);
                    sb.append(":");
                    appendSnbt(child, sb);
                }
                sb.append("}");
            }
            case LIST -> {
                sb.append("[");
                List<DataNode> children = node.getChildren();
                for (int i = 0; i < children.size(); i++) {
                    if (i > 0) sb.append(",");
                    appendSnbt(children.get(i), sb);
                }
                sb.append("]");
            }
            case BYTE -> sb.append(node.getValue()).append("b");
            case SHORT -> sb.append(node.getValue()).append("s");
            case INT -> sb.append(node.getValue());
            case LONG -> sb.append(node.getValue()).append("L");
            case FLOAT -> sb.append(node.getValue()).append("f");
            case DOUBLE -> sb.append(node.getValue()).append("d");
            case BOOLEAN -> sb.append(Boolean.TRUE.equals(node.getValue()) ? "1b" : "0b");
            case STRING, UUID, UNKNOWN -> escapeString(node.getValue() != null ? node.getValue().toString() : "", sb);
            case BYTE_ARRAY -> {
                sb.append("[B;");
                byte[] arr = (byte[]) node.getValue();
                if (arr != null) {
                    for (int i = 0; i < arr.length; i++) {
                        if (i > 0) sb.append(",");
                        sb.append(arr[i]).append("b");
                    }
                }
                sb.append("]");
            }
            case INT_ARRAY -> {
                sb.append("[I;");
                int[] arr = (int[]) node.getValue();
                if (arr != null) {
                    for (int i = 0; i < arr.length; i++) {
                        if (i > 0) sb.append(",");
                        sb.append(arr[i]);
                    }
                }
                sb.append("]");
            }
            case LONG_ARRAY -> {
                sb.append("[L;");
                long[] arr = (long[]) node.getValue();
                if (arr != null) {
                    for (int i = 0; i < arr.length; i++) {
                        if (i > 0) sb.append(",");
                        sb.append(arr[i]).append("L");
                    }
                }
                sb.append("]");
            }
        }
    }

    private void appendKey(String key, StringBuilder sb) {
        if (key.matches("^[a-zA-Z0-9_.-]+$") && !key.contains(":")) {
            sb.append(key);
        } else {
            escapeString(key, sb);
        }
    }

    private void escapeString(String s, StringBuilder sb) {
        sb.append("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append("\"");
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    private com.fasterxml.jackson.databind.JsonNode toJacksonNode(DataNode node, ObjectMapper mapper) {
        if (node.getType() == DataType.COMPOUND) {
            ObjectNode obj = mapper.createObjectNode();
            obj.put("__type", "COMPOUND");
            ObjectNode children = mapper.createObjectNode();
            for (DataNode child : node.getChildren()) {
                children.set(child.getKey(), toJacksonNode(child, mapper));
            }
            obj.set("children", children);
            return obj;
        }
        if (node.getType() == DataType.LIST) {
            ObjectNode obj = mapper.createObjectNode();
            obj.put("__type", "LIST");
            ArrayNode arr = mapper.createArrayNode();
            for (DataNode child : node.getChildren()) {
                arr.add(toJacksonNode(child, mapper));
            }
            obj.set("elements", arr);
            return obj;
        }
        // Primitive / array
        ObjectNode obj = mapper.createObjectNode();
        obj.put("__type", node.getType().name());
        Object val = node.getValue();
        if (val == null) obj.putNull("value");
        else if (val instanceof String s) obj.put("value", s);
        else if (val instanceof Number n) obj.put("value", n.toString());
        else if (val instanceof Boolean b) obj.put("value", b);
        else if (val instanceof byte[] arr) obj.put("value", Arrays.toString(arr));
        else if (val instanceof int[] arr) obj.put("value", Arrays.toString(arr));
        else if (val instanceof long[] arr) obj.put("value", Arrays.toString(arr));
        else obj.put("value", val.toString());
        return obj;
    }

    private Map<String, Object> toMap(DataNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("__type", node.getType().name());
        if (node.getType().isContainer()) {
            Map<String, Object> childMap = new LinkedHashMap<>();
            for (DataNode child : node.getChildren()) {
                childMap.put(child.getKey(), toMap(child));
            }
            map.put(node.getType() == DataType.LIST ? "elements" : "children", childMap);
        } else {
            map.put("value", node.getValue() != null ? node.getValue().toString() : null);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private DataNode fromMap(Map<?, ?> map, String key) {
        Object rawType = map.get("__type");
        String typeName = rawType instanceof String s ? s : "UNKNOWN";
        DataType type;
        try { type = DataType.valueOf(typeName); }
        catch (IllegalArgumentException e) { type = DataType.UNKNOWN; }

        if (type == DataType.COMPOUND) {
            DataNode node = DataNode.ofCompound(key);
            Map<?, ?> children = (Map<?, ?>) map.get("children");
            if (children != null) {
                for (Map.Entry<?, ?> e : children.entrySet()) {
                    node.addChild(fromMap((Map<?, ?>) e.getValue(), (String) e.getKey()));
                }
            }
            return node;
        }
        if (type == DataType.LIST) {
            DataNode node = DataNode.ofList(key);
            Map<?, ?> elements = (Map<?, ?>) map.get("elements");
            if (elements != null) {
                for (Map.Entry<?, ?> e : elements.entrySet()) {
                    node.addChild(fromMap((Map<?, ?>) e.getValue(), (String) e.getKey()));
                }
            }
            return node;
        }
        // Primitive
        return DataNode.ofPrimitive(key, type, map.get("value"));
    }
}
