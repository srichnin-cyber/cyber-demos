package com.example.demo.docgen.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Application configuration for template prewarming with placeholder variables.
 * 
 * Enables declarative definition of prewarming scenarios in application.yml.
 * Each scenario specifies a template and the variable values to resolve.
 * 
 * Example application.yml:
 * 
 * docgen:
 *   templates:
 *     prewarming:
 *       enabled: true
 *       scenarios:
 *         - name: "Production"
 *           templateId: "enrollment-form"
 *           namespace: "tenant-a"
 *           description: "Production defaults"
 *           variables:
 *             environment: production
 *             formType: enrollment
 *             version: 2024-prod
 *             region: us-east-1
 *         - name: "Development"
 *           templateId: "enrollment-form"
 *           namespace: "tenant-a"
 *           variables:
 *             environment: development
 *             formType: enrollment
 *             version: dev
 *             region: local
 *         - name: "Staging"
 *           templateId: "enrollment-form"
 *           namespace: "tenant-a"
 *           variables:
 *             environment: staging
 *             formType: enrollment
 *             version: 2024-staging
 *             region: us-west-2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Component
@ConfigurationProperties(prefix = "docgen.templates.prewarming")
public class PrewarmingConfiguration {

    /**
     * Enable/disable prewarming with placeholder variables
     */
    private boolean enabled = true;

    /**
     * List of prewarming scenarios
     */
    private List<PrewarmingScenario> scenarios = new ArrayList<>();

}
