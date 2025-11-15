package com.example.demoproject.service;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import com.example.demoproject.model.MappingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demoproject.model.FieldMapping;


@Service
public class DataMapper {

    /**
     * Maps data from a JSON source to a target map using YAML configuration.
     *
     * @param yamlResourcePath the classpath path to the YAML mapping configuration
     *                         (e.g., "/mapping-config.yml")
     * @param jsonResourcePath the classpath path to the JSON data source (e.g.,
     *                         "/data.json")
     * @return a map of target field names to transformed values
     * @throws IllegalArgumentException if YAML or JSON resources are not found
     * @throws Exception                if loading or parsing the resources fails
     */
    public Map<String, Object> mapData(String yamlResourcePath, String jsonResourcePath) throws Exception {

        System.out.print("Mapping data using config: " + yamlResourcePath + " and data: " + jsonResourcePath + "\n");

        try (InputStream yamlIn = getClass().getResourceAsStream(yamlResourcePath);
                InputStream jsonIn = getClass().getResourceAsStream(jsonResourcePath)) {

            // Validate resources exist
            if (yamlIn == null) {
                System.err.println("YAML resource not found: " + yamlResourcePath);
                throw new IllegalArgumentException("YAML resource not found: " + yamlResourcePath);
            }
            if (jsonIn == null) {
                System.err.println("JSON resource not found: " + jsonResourcePath);
                throw new IllegalArgumentException("JSON resource not found: " + jsonResourcePath);
            }

            // Load YAML configuration
            Yaml yaml = new Yaml();
            MappingConfig config = yaml.loadAs(yamlIn, MappingConfig.class);
           //log config as pretty print string


            System.out.print("Loaded Mapping Config: " + config.toString() + "\n");

            // Parse JSON data
            String json = new String(jsonIn.readAllBytes(), StandardCharsets.UTF_8);
            System.out.print("Loaded JSON data: " + json + "\n");
            DocumentContext jsonContext = JsonPath.parse(json);
            // Also parse into a plain Map for reliable scalar extraction
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonMap = objectMapper.readValue(json, Map.class);

            // Apply field mappings and transformations
            Map<String, Object> mappedData = new HashMap<>();
            for (FieldMapping mapping : config.getMappings()) {

                // 1. Read source value (normalize source to a JsonPath)
                String source = mapping.getSource() == null ? "" : mapping.getSource().trim();

                Object rawValue = getValueFromMap(jsonMap, source);
                // 2. Evaluate condition
                System.out.print("Evaluating Condition for mapping for target field: " + mapping.getTarget() + "\n");
                if (!ConditionEvaluator.evaluate(mapping.getCondition(), jsonContext, rawValue)) {
                    System.out.println("⏭️ Skipped: " + mapping.getTarget() + " (condition failed)");
                    continue;
                }
                Object transformed = transformValue(rawValue, mapping);
                System.out.print("Transformed value for field " + mapping.getTarget() + ": " + transformed + "\n");

                // 4. Handle null/empty with default
                String finalValue = "";
                if (transformed != null) {
                    System.out.print("Final value before default check for field " + mapping.getTarget() + ": "
                            + transformed + "\n");
                    finalValue = transformed.toString();
                }
                if (finalValue == null || finalValue.trim().isEmpty()) {
                    System.out.print("Applying default value for field " + mapping.getTarget() + "\n");
                    finalValue = mapping.getDefaultValue() != null ? mapping.getDefaultValue() : "";
                }
                mappedData.put(mapping.getTarget(), finalValue);
            }

            System.out.print("Final Mapped Data: " + mappedData + "\n");
            return mappedData;
        }
    }

    /**
     * Extracts a value from JSON context and applies the transformation defined in
     * the mapping.
     *
     * @param rawValue    the raw Source value extracted from JSON
     * @param mapping     the field mapping containing source path and
     *                    transformation details
     * @return the transformed value, or empty string if extraction fails or value
     *         is null
     */
    private Object transformValue(Object rawValue, FieldMapping mapping) {
        // If JsonPath returned a DocumentContext (unexpected), convert to JSON string
        if (rawValue instanceof DocumentContext) {
            try {
                rawValue = ((DocumentContext) rawValue).jsonString();
            } catch (NoSuchMethodError | Exception ignored) {
                rawValue = rawValue.toString();
            }
        }

        return DataTransformer.applyTransform(rawValue, mapping.getTransform());
    }

    /**
     * Retrieve a nested value from a parsed JSON map using a dot-separated path.
     */
    @SuppressWarnings("rawtypes")
    private Object getValueFromMap(Map<String, Object> root, String path) {
        if (path == null || path.trim().isEmpty()) return root;
        String[] parts = path.split("\\.");
        Object cur = root;
        for (String p : parts) {
            if (cur instanceof Map) {
                cur = ((Map) cur).get(p);
            } else if (cur instanceof java.util.List) {
                try {
                    int idx = Integer.parseInt(p);
                    cur = ((java.util.List) cur).get(idx);
                } catch (Exception e) {
                    return null;
                }
            } else {
                return null;
            }
            if (cur == null) return null;
        }
        return cur;
    }
}