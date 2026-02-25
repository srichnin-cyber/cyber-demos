package com.example.demo.docgen.service;

import com.example.demo.docgen.model.DocumentGenerationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
public class GenerateTwoRowExcelSampleTest {

    @Autowired
    private DocumentComposer composer;

    @Test
    public void generateSampleTwoRowExcel() throws Exception {
        Map<String, Object> data = new HashMap<>();

        List<Map<String, Object>> plans = new ArrayList<>();

        Map<String, Object> basic = new HashMap<>();
        basic.put("planName", "Basic");
        basic.put("group", "Group A");
        List<Map<String, Object>> basicBenefits = new ArrayList<>();
        basicBenefits.add(Map.of("name", "Doctor Visits", "value", "$20 copay"));
        basicBenefits.add(Map.of("name", "Prescriptions", "value", "$10 copay"));
        basic.put("benefits", basicBenefits);

        Map<String, Object> premium = new HashMap<>();
        premium.put("planName", "Premium");
        premium.put("group", "Group B");
        List<Map<String, Object>> premiumBenefits = new ArrayList<>();
        premiumBenefits.add(Map.of("name", "Doctor Visits", "value", "Covered 100%"));
        premiumBenefits.add(Map.of("name", "Prescriptions", "value", "$5 copay"));
        premium.put("benefits", premiumBenefits);

        plans.add(basic);
        plans.add(premium);

        data.put("comparisonTitle", "Insurance Plan Comparison - Two Row Header");
        data.put("plans", plans);

        DocumentGenerationRequest req = DocumentGenerationRequest.builder()
            .namespace("common-templates")
            .templateId("plan-comparison-two-row")
            .data(data)
            .build();

        byte[] bytes = composer.generateExcel(req);
        assertTrue(bytes != null && bytes.length > 0, "Generated Excel should not be empty");

        Path out = Paths.get("docs/sample-two-row.xlsx");
        Files.createDirectories(out.getParent());
        Files.write(out, bytes);
        System.out.println("Wrote sample Excel to: " + out.toAbsolutePath());
    }
}
