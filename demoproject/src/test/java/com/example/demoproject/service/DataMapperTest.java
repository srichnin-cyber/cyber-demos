package com.example.demoproject.service;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataMapperTest {

    @Test
    public void regionLabel_isTransformedToUpperCase_whenConditionMatches() throws Exception {
        DataMapper mapper = new DataMapper();

        // Use the test resources that contain the region = "EU" and country = "France"
        Map<String, Object> out = mapper.mapData("/2.mapping-config.yml", "/2.data.json");

        Object regionLabel = out.get("regionLabel");
        assertEquals("FRANCE", regionLabel);
    }
}
