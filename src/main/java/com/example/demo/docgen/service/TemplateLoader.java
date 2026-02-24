package com.example.demo.docgen.service;

import com.example.demo.docgen.aspect.LogExecutionTime;
import com.example.demo.docgen.exception.TemplateLoadingException;
import com.example.demo.docgen.model.DocumentTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads document templates from JSON or YAML files.
 * Supports local classpath, file system, and remote Spring Cloud Config Server.
 * Also supports namespace-aware (multi-tenant) template loading.
 */
@Slf4j
@Component
public class TemplateLoader {
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final RestClient restClient = RestClient.create();
    private final NamespaceResolver namespaceResolver;

    /**
     * Determine if a resolved template ID can be safely served from the structural
     * cache instead of going through the variable-aware loader.
     * We only cache when there are no resolution variables and the ID contains
     * no placeholder tokens.
     */
    /*
     * Package-private for testing.  Determines whether the loader will attempt to
     * hit the cached overload instead of the variable-aware recursive path.
     */
    boolean shouldUseCache(Map<String, Object> variables, String resolvedId) {
        if (resolvedId == null) return false;
        boolean noVars = variables == null || variables.isEmpty();
        boolean noPlaceholders = !resolvedId.contains("${");
        return noVars && noPlaceholders;
    }

    /**
     * Parse a possibly namespaced identifier of the form "prefix:id" into an
     * (namespace, id) pair.  The returned namespace will be normalized; caller
     * may supply a default namespace when prefix is absent.
     */
    private java.util.Map.Entry<String,String> parseNamespaceAndId(String candidate, String defaultNamespace) {
        if (candidate != null && candidate.contains(":")) {
            String[] parts = candidate.split(":", 2);
            String prefix = parts[0];
            String id = parts.length > 1 ? parts[1] : "";
            String ns = prefix.equals("common") ? namespaceResolver.normalizeNamespace("common-templates") : namespaceResolver.normalizeNamespace(prefix);
            return new java.util.AbstractMap.SimpleEntry<>(ns, id);
        }
        return new java.util.AbstractMap.SimpleEntry<>(defaultNamespace, candidate);
    }
    
    // ThreadLocal to track templates currently being loaded (for circular reference detection)
    private final ThreadLocal<Set<String>> loadingStack = ThreadLocal.withInitial(HashSet::new);

    // NOTE: caching is used on several public methods below via Spring's @Cacheable.
    // Be aware that SpEL cache keys like `#templateId` or `#path` rely on method
    // parameter names being available at runtime. When compiling the project make
    // sure the `-parameters` javac flag is enabled (added to the maven-compiler-plugin)
    // or use explicit key expressions that do not depend on parameter names to
    // avoid null cache-key issues during runtime.

    @Value("${spring.cloud.config.uri:}")
    private String configServerUri;

    @Value("${docgen.templates.remote-enabled:true}")
    private boolean remoteEnabled;

    @Value("${docgen.templates.cache-enabled:false}")
    private boolean cacheEnabled;

    @Value("${spring.application.name:doc-gen-service}")
    private String applicationName;

    @Value("${spring.cloud.config.label:main}")
    private String configLabel;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;
    
    public TemplateLoader(NamespaceResolver namespaceResolver) {
        this.namespaceResolver = namespaceResolver;
    }
    
    /**
     * Load a document template from file
     *
     * @param templateId Path or ID of the template file
     * @return Loaded template
     */
    @LogExecutionTime("Loading Template Definition")
    @Cacheable(value = "documentTemplates", key = "#templateId")
    public DocumentTemplate loadTemplate(String templateId) {
        return loadTemplate(templateId, Collections.emptyMap());
    }

    /**
     * Load a document template with namespace support (multi-tenant)
     * 
     * @param namespace The namespace/tenant where the template resides (e.g., "tenant-a", "common-templates")
     * @param templateId The template ID relative to namespace/templates folder
     * @return Loaded template
     */
    @LogExecutionTime("Loading Namespace-aware Template")
    @Cacheable(value = "documentTemplates", key = "{ #namespace, #templateId }")
    public DocumentTemplate loadTemplate(String namespace, String templateId) {
        return loadTemplate(namespace, templateId, Collections.emptyMap());
    }
    
    /**
     * Load a document template with namespace and variable substitution support
     * 
     * @param namespace The namespace/tenant where the template resides (e.g., "tenant-a", "common-templates")
     * @param templateId The template ID relative to namespace/templates folder
     * @param variables Variables for placeholder resolution
     * @return Loaded template
     */
    @LogExecutionTime("Loading Namespace-aware Template (with variables)")
    public DocumentTemplate loadTemplate(String namespace, String templateId, Map<String, Object> variables) {
        String normalizedNamespace = namespaceResolver.normalizeNamespace(namespace);
        log.info("Loading template from namespace '{}': {} (resolved)", normalizedNamespace, templateId);
        
        String fullTemplatePath = namespaceResolver.resolveTemplatePath(normalizedNamespace, templateId);
        // Call loadTemplateWithNamespaceContext directly to avoid double-resolving the path
        DocumentTemplate template = loadTemplateWithNamespaceContext(fullTemplatePath, variables, normalizedNamespace);
        
        // Store the namespace in template metadata for later resource resolution
        if (template.getMetadata() == null) {
            template.setMetadata(new java.util.HashMap<>());
        }
        template.getMetadata().put("_namespace", normalizedNamespace);
        
        return template;
    }

    // IMPORTANT:
    // This overload (namespace + templateId + variables) does not have a @Cacheable
    // annotation above it because caching needs to be namespace-aware and must
    // include variables in the cache key when appropriate. If you add caching
    // here, ensure the key includes both namespace and templateId (and any
    // relevant variables) to avoid returning stale/mis-scoped templates.

    /**
     * Load a document template with placeholder resolution against provided variables.
     * Placeholders use the ${path.to.value} syntax and are resolved from the supplied map.
     * NOTE: This method loads templates directly from classpath/filesystem without namespace resolution.
     * For namespace-aware loading, use loadTemplate(String namespace, String templateId, Map variables) instead.
     * Includes circular reference detection.
     */
    @LogExecutionTime("Loading Template Definition (with variables)")
    public DocumentTemplate loadTemplate(String templateId, Map<String, Object> variables) {
        // Check for circular references
        Set<String> loading = loadingStack.get();
        if (loading.contains(templateId)) {
            String circularChain = buildCircularReferenceChain(loading, templateId);
            loadingStack.remove(); // Clean up ThreadLocal
            throw new TemplateLoadingException(
                "CIRCULAR_REFERENCE",
                "Circular template reference detected: " + circularChain
            );
        }
        
        // Add current template to loading stack
        loading.add(templateId);
        
        try {
            // Load directly from classpath/filesystem without namespace resolution
            String resolvedTemplateId = resolvePlaceholders(templateId, variables);
            // RESOLUTION NOTE:
            // At this point `resolvedTemplateId` may be a path which includes a namespace
            // prefix (e.g. "tenant-a/templates/foo.yaml") or a relative id like
            // "base-enrollment". The subsequent `loadRawTemplate` call attempts to
            // locate the concrete file by checking classpath and filesystem candidates
            // and — when remoteEnabled — consulting the Config Server.
            DocumentTemplate template = loadRawTemplate(resolvedTemplateId);
            
            // Handle inheritance and fragments for non-namespaced templates
            if (template.getBaseTemplateId() != null && !template.getBaseTemplateId().isEmpty()) {
                String baseId = template.getBaseTemplateId();
                String resolvedBaseId = resolvePlaceholders(baseId, variables);
                DocumentTemplate baseTemplate;
                if (shouldUseCache(variables, resolvedBaseId)) {
                    // safe to hit structural cache
                    baseTemplate = loadTemplate(resolvedBaseId);
                } else {
                    baseTemplate = loadTemplate(resolvedBaseId, variables);
                }
                template = mergeTemplates(baseTemplate, template);
            }
            
            // Handle fragments
            if (template.getIncludedFragments() != null && !template.getIncludedFragments().isEmpty()) {
                for (String fragmentId : template.getIncludedFragments()) {
                    String resolvedFragmentId = resolvePlaceholders(fragmentId, variables);
                    DocumentTemplate fragment;
                    if (shouldUseCache(variables, resolvedFragmentId)) {
                        fragment = loadTemplate(resolvedFragmentId);
                    } else {
                        fragment = loadTemplate(resolvedFragmentId, variables);
                    }
                    template = mergeTemplates(fragment, template);
                }
            }
            
            return template;
        } finally {
            // Remove current template from loading stack
            loading.remove(templateId);
            
            // Clean up ThreadLocal if stack is empty
            if (loading.isEmpty()) {
                loadingStack.remove();
            }
        }
    }

    /**
     * Internal helper to load templates while maintaining namespace context through inheritance.
     * Includes circular reference detection to prevent infinite recursion.
     * 
     * @param templateId The template ID (full path or relative ID)
     * @param variables Variables for placeholder resolution
     * @param contextNamespace The namespace context for resolving relative template references
     * @return Loaded template
     * @throws TemplateLoadingException if circular reference is detected
     */
    private DocumentTemplate loadTemplateWithNamespaceContext(String templateId, Map<String, Object> variables, String contextNamespace) {
        // Check for circular references
        Set<String> loading = loadingStack.get();
        if (loading.contains(templateId)) {
            String circularChain = buildCircularReferenceChain(loading, templateId);
            loadingStack.remove(); // Clean up ThreadLocal
            throw new TemplateLoadingException(
                "CIRCULAR_REFERENCE",
                "Circular template reference detected: " + circularChain
            );
        }
        
        // Add current template to loading stack
        loading.add(templateId);
        
        try {
            log.info("Loading template (resolved): {}", templateId);

            String resolvedTemplateId = resolvePlaceholders(templateId, variables);
            DocumentTemplate template = loadRawTemplate(resolvedTemplateId);

            // Determine the namespace for this template (extract from resolved path or use context)
            String currentNamespace = contextNamespace;
            if (currentNamespace == null && template.getMetadata() != null) {
                currentNamespace = (String) template.getMetadata().get("_namespace");
            }
            if (currentNamespace == null) {
                // Try to extract namespace from the resolved path
                currentNamespace = extractNamespaceFromPath(resolvedTemplateId);
            }

            // Handle inheritance
            if (template.getBaseTemplateId() != null && !template.getBaseTemplateId().isEmpty()) {
                String baseId = template.getBaseTemplateId();
                String resolvedBaseId = resolvePlaceholders(baseId, variables);
                
                // Determine effective namespace and id for potential cache hit
                String effectiveNamespace = currentNamespace;
                String effectiveId = resolvedBaseId;
                if (resolvedBaseId.contains(":")) {
                    String[] parts = resolvedBaseId.split(":", 2);
                    String prefix = parts[0];
                    String remainder = parts.length > 1 ? parts[1] : "";
                    effectiveId = remainder;
                    effectiveNamespace = prefix.equals("common") ? namespaceResolver.normalizeNamespace("common-templates") : namespaceResolver.normalizeNamespace(prefix);
                }

                // Resolve baseTemplate in the same namespace context (path used only for non-cache recursion)
                String baseTemplatePath = resolvedBaseId;
                if (resolvedBaseId.contains(":")) {
                    baseTemplatePath = namespaceResolver.resolveTemplatePath(effectiveNamespace, effectiveId);
                } else if (currentNamespace != null) {
                    baseTemplatePath = namespaceResolver.resolveTemplatePath(currentNamespace, resolvedBaseId);
                }

                DocumentTemplate baseTemplate;
                if (shouldUseCache(variables, resolvedBaseId)) {
                    // hit structural cache by namespace/id if available
                    if (effectiveNamespace != null) {
                        baseTemplate = loadTemplate(effectiveNamespace, effectiveId);
                    } else {
                        baseTemplate = loadTemplate(effectiveId);
                    }
                } else {
                    baseTemplate = loadTemplateWithNamespaceContext(baseTemplatePath, variables, currentNamespace);
                }
                template = mergeTemplates(baseTemplate, template);
            }

            // Handle fragments
            if (template.getIncludedFragments() != null && !template.getIncludedFragments().isEmpty()) {
                for (String fragmentId : template.getIncludedFragments()) {
                    String resolvedFragmentId = resolvePlaceholders(fragmentId, variables);
                    
                    // Determine effective namespace/id for fragment
                    String effectiveNamespace = currentNamespace;
                    String effectiveId = resolvedFragmentId;
                    if (resolvedFragmentId.contains(":")) {
                        String[] parts = resolvedFragmentId.split(":", 2);
                        String prefix = parts[0];
                        String remainder = parts.length > 1 ? parts[1] : "";
                        effectiveId = remainder;
                        effectiveNamespace = prefix.equals("common") ? namespaceResolver.normalizeNamespace("common-templates") : namespaceResolver.normalizeNamespace(prefix);
                    }
                    
                    // Resolve fragment in the same namespace context
                    String fragmentPath = resolvedFragmentId;
                    if (resolvedFragmentId.contains(":")) {
                        fragmentPath = namespaceResolver.resolveTemplatePath(effectiveNamespace, effectiveId);
                    } else if (currentNamespace != null) {
                        fragmentPath = namespaceResolver.resolveTemplatePath(currentNamespace, resolvedFragmentId);
                    }

                    DocumentTemplate fragment;
                    if (shouldUseCache(variables, resolvedFragmentId)) {
                        if (effectiveNamespace != null) {
                            fragment = loadTemplate(effectiveNamespace, effectiveId);
                        } else {
                            fragment = loadTemplate(effectiveId);
                        }
                    } else {
                        fragment = loadTemplateWithNamespaceContext(fragmentPath, variables, currentNamespace);
                    }
                    template.getSections().addAll(fragment.getSections());
                }
            }

            return template;
        } finally {
            // Remove current template from loading stack
            loading.remove(templateId);
            
            // Clean up ThreadLocal if stack is empty
            if (loading.isEmpty()) {
                loadingStack.remove();
            }
        }
    }
    
    /**
     * Build a human-readable description of the circular reference chain.
     * 
     * @param loadingStack Set of templates currently being loaded
     * @param circularTemplate The template that caused the circular reference
     * @return Description of circular chain
     */
    private String buildCircularReferenceChain(Set<String> loadingStack, String circularTemplate) {
        // Since we can't preserve order from Set, show what we know
        return "Template '" + circularTemplate + "' is already being loaded. " +
               "Currently loading: " + loadingStack;
    }

    /**
     * Extract namespace from a full template path like "tenant-a/templates/base-enrollment.yaml"
     * @param path The full template path
     * @return The namespace part, or null if path doesn't match expected structure
     */
    private String extractNamespaceFromPath(String path) {
        if (path == null) return null;
        // Look for pattern: {namespace}/templates/...
        String[] parts = path.split("/");
        if (parts.length >= 2 && parts[1].equals("templates")) {
            return parts[0];
        }
        return null;
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

    private String resolvePlaceholders(String templateId, Map<String, Object> variables) {
        if (templateId == null) return null;
        Matcher m = PLACEHOLDER.matcher(templateId);
        StringBuffer sb = new StringBuffer();

        boolean found = false;
        while (m.find()) {
            found = true;
            String path = m.group(1);
            Object val = getValueFromPath(variables, path);
            if (val == null) {
                throw new com.example.demo.docgen.exception.TemplateLoadingException(
                    "UNRESOLVED_PLACEHOLDER",
                    "Unresolved placeholder '" + path + "' in template id: " + templateId
                );
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val.toString()));
        }
        m.appendTail(sb);

        if (!found) return templateId;
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Object getValueFromPath(Map<String, Object> variables, String path) {
        if (variables == null || variables.isEmpty()) return null;
        String[] parts = path.split("\\.");
        Object current = variables;
        for (String p : parts) {
            if (current == null) return null;
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(p);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * Interpolate selected string fields within a loaded DocumentTemplate using the provided variables.
     * Currently interpolates PageSection.templatePath and header/footer content.
     */
    public void interpolateTemplateFields(DocumentTemplate template, Map<String, Object> variables) {
        if (template == null) return;

        if (template.getSections() != null) {
            for (com.example.demo.docgen.model.PageSection section : template.getSections()) {
                if (section.getTemplatePath() != null && section.getTemplatePath().contains("${")) {
                    section.setTemplatePath(resolvePlaceholders(section.getTemplatePath(), variables));
                }
                if (section.getCondition() != null && section.getCondition().contains("${")) {
                    section.setCondition(resolvePlaceholders(section.getCondition(), variables));
                }
            }
        }

        com.example.demo.docgen.model.HeaderFooterConfig hf = template.getHeaderFooterConfig();
        if (hf != null) {
            if (hf.getHeaders() != null) {
                for (com.example.demo.docgen.model.HeaderTemplate ht : hf.getHeaders()) {
                    if (ht.getContent() != null && ht.getContent().contains("${")) {
                        ht.setContent(resolvePlaceholders(ht.getContent(), variables));
                    }
                }
            }
            if (hf.getFooters() != null) {
                for (com.example.demo.docgen.model.FooterTemplate ft : hf.getFooters()) {
                    if (ft.getContent() != null && ft.getContent().contains("${")) {
                        ft.setContent(resolvePlaceholders(ft.getContent(), variables));
                    }
                }
            }
        }
    }

    /**
     * Clear the template cache
     */
    @CacheEvict(value = {"documentTemplates", "rawResources"}, allEntries = true)
    public void clearCache() {
        log.info("Clearing all template and resource caches");
    }

    /**
     * Fetch a raw resource (PDF or FTL) with caching
     */
    @LogExecutionTime("Fetching Raw Resource")
    @Cacheable(value = "rawResources", key = "#path")
    public byte[] getResourceBytes(String path) {
        log.debug("getResourceBytes called: path='{}', remoteEnabled={}, cacheEnabled={}, configServerUri='{}'", path, remoteEnabled, cacheEnabled, configServerUri);
        if (path == null || path.isEmpty()) {
            throw new TemplateLoadingException(
                "INVALID_PATH",
                "Resource path cannot be null or empty"
            );
        }
        
        String logMsg = cacheEnabled ? "Fetching raw resource (cache miss): {}" : "Fetching raw resource: {}";
        log.info(logMsg, path);
        try (InputStream is = getInputStream(path)) {
            return is.readAllBytes();
        } catch (IOException e) {
            log.error("Failed to read resource bytes from stream: {}", path, e);
            throw new TemplateLoadingException(
                "RESOURCE_READ_ERROR",
                "Failed to read resource: " + path,
                e
            );
        }
    }
    
    /**
     * Fetch a namespace-aware resource (PDF or FTL) with support for cross-namespace references
     * 
     * Examples:
     * - getNamespaceResourceBytes("tenant-a", "enrollment-header.ftl") 
     *   → "tenant-a/templates/enrollment-header.ftl"
     * - getNamespaceResourceBytes("tenant-a", "common:base-enrollment.ftl") 
     *   → "common-templates/templates/base-enrollment.ftl"
     * 
     * @param namespace The current namespace context
     * @param resourcePath The resource path (may include common: prefix)
     * @return Bytes of the resource
     */
    @LogExecutionTime("Fetching Namespace-aware Resource")
    public byte[] getNamespaceResourceBytes(String namespace, String resourcePath) {
        String normalizedNamespace = namespaceResolver.normalizeNamespace(namespace);
        String resolvedPath = namespaceResolver.resolveResourcePath(resourcePath, normalizedNamespace);
        log.info("Fetching namespace-aware resource: {} from namespace '{}' resolved to: {}", 
                 resourcePath, normalizedNamespace, resolvedPath);
        return getResourceBytes(resolvedPath);
    }

    private DocumentTemplate loadRawTemplate(String templateId) {
        // If remote is enabled, fetch from config-server (single source of truth)
        if (remoteEnabled && configServerUri != null && !configServerUri.isEmpty()) {
            // Try with extensions on config-server
            String[] extensions = {".yaml", ".yml", ".json"};
            for (String ext : extensions) {
                String pathWithExt = templateId + ext;
                try {
                    InputStream is = getFromConfigServer(pathWithExt);
                    if (is != null) {
                        try {
                            if (ext.equals(".json")) {
                                return jsonMapper.readValue(is, DocumentTemplate.class);
                            } else {
                                return yamlMapper.readValue(is, DocumentTemplate.class);
                            }
                        } catch (IOException e) {
                            log.error("Failed to parse template from config-server: {}", pathWithExt, e);
                            throw e;
                        }
                    }
                } catch (IOException e) {
                    // If the exception indicates connectivity problems, surface a CONFIG_SERVER_ERROR
                    if (e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException
                            || (e.getCause() != null && (e.getCause() instanceof java.net.ConnectException || e.getCause() instanceof java.net.SocketTimeoutException))) {
                        throw new com.example.demo.docgen.exception.TemplateLoadingException(
                                "CONFIG_SERVER_ERROR",
                                "Failed to contact Config Server at " + configServerUri,
                                e
                        );
                    }
                    log.error("Template {} not found on config-server", pathWithExt);
                }
            }
            
            // Not found on config-server
            throw new com.example.demo.docgen.exception.TemplateLoadingException(
                "TEMPLATE_NOT_FOUND",
                "Template not found for id '" + templateId + "' on config-server at " + configServerUri
            );
        }

        // Remote is disabled - try to resolve locally
        String resolvedPath = resolvePath(templateId);

        if (resolvedPath != null) {
            if (resolvedPath.endsWith(".json")) {
                return loadFromJson(resolvedPath);
            } else if (resolvedPath.endsWith(".yaml") || resolvedPath.endsWith(".yml")) {
                return loadFromYaml(resolvedPath);
            } else {
                // Path exists but has no known extension
                throw new com.example.demo.docgen.exception.TemplateLoadingException(
                    "UNSUPPORTED_TEMPLATE_FORMAT",
                    "Resolved template path '" + resolvedPath + "' does not have a supported extension. " +
                    "Use .json, .yaml, or .yml for template files."
                );
            }
        }

        // Not found locally
        throw new com.example.demo.docgen.exception.TemplateLoadingException(
            "TEMPLATE_NOT_FOUND",
            "Template not found for id '" + templateId + "'. Check src/main/resources/templates or enable remote templates."
        );
    }

    private List<String> buildCandidatePaths(String templateId) {
        List<String> candidates = new ArrayList<>();
        candidates.add(templateId);
        candidates.add("templates/" + templateId);
        String[] extensions = {".yaml", ".yml", ".json"};
        for (String ext : extensions) {
            candidates.add(templateId + ext);
            candidates.add("templates/" + templateId + ext);
        }
        return candidates;
    }

    // Merge a base template with a child template.
    // Rules:
    // - Sections from base are included unless explicitly excluded by the child.
    // - Sections with matching IDs are merged (child overrides fields on base section).
    // - Remaining child-only sections are appended.
    // - Header/Footer from base is used when child does not define one.
    private DocumentTemplate mergeTemplates(DocumentTemplate base, DocumentTemplate child) {
        List<com.example.demo.docgen.model.PageSection> mergedSections = new java.util.ArrayList<>();
        
        // Create a map of child sections for easy lookup
        java.util.Map<String, com.example.demo.docgen.model.PageSection> childSectionMap = new java.util.HashMap<>();
        for (com.example.demo.docgen.model.PageSection section : child.getSections()) {
            childSectionMap.put(section.getSectionId(), section);
        }

        // Add sections from base that are not excluded
        for (com.example.demo.docgen.model.PageSection baseSection : base.getSections()) {
            String sectionId = baseSection.getSectionId();
            
            if (child.getExcludedSections() != null && child.getExcludedSections().contains(sectionId)) {
                continue;
            }
            
            // If child has a section with the same ID, merge them
            if (childSectionMap.containsKey(sectionId)) {
                com.example.demo.docgen.model.PageSection childSection = childSectionMap.get(sectionId);
                mergeSection(baseSection, childSection);
                childSectionMap.remove(sectionId); // Mark as processed
            } else if (child.getSectionOverrides() != null && child.getSectionOverrides().containsKey(sectionId)) {
                // Legacy simple path override
                baseSection.setTemplatePath(child.getSectionOverrides().get(sectionId));
            }
            
            mergedSections.add(baseSection);
        }
        
        // Add remaining sections from child (those that didn't override base sections)
        mergedSections.addAll(childSectionMap.values());
        
        // Sort by order
        mergedSections.sort(java.util.Comparator.comparingInt(com.example.demo.docgen.model.PageSection::getOrder));
        
        child.setSections(mergedSections);
        
        // Merge header/footer if child doesn't have one
        if (child.getHeaderFooterConfig() == null) {
            child.setHeaderFooterConfig(base.getHeaderFooterConfig());
        }
        
        return child;
    }

    private void mergeSection(com.example.demo.docgen.model.PageSection base, com.example.demo.docgen.model.PageSection child) {
        if (child.getType() != null) base.setType(child.getType());
        if (child.getTemplatePath() != null) base.setTemplatePath(child.getTemplatePath());
        if (child.getMappingType() != null) base.setMappingType(child.getMappingType());
        if (child.getCondition() != null) base.setCondition(child.getCondition());
        if (child.getOrder() != 0) base.setOrder(child.getOrder());
        if (child.getViewModelType() != null) base.setViewModelType(child.getViewModelType());
        
        // Override mappings if provided
        if (child.getFieldMappings() != null && !child.getFieldMappings().isEmpty()) {
            base.setFieldMappings(child.getFieldMappings());
        }
        
        if (child.getFieldMappingGroups() != null && !child.getFieldMappingGroups().isEmpty()) {
            base.setFieldMappingGroups(child.getFieldMappingGroups());
        }

        if (child.getOverflowConfigs() != null && !child.getOverflowConfigs().isEmpty()) {
            base.setOverflowConfigs(child.getOverflowConfigs());
        }
    }

    private String resolvePath(String templateId) {
        // Resolve the templateId to a concrete path by checking multiple candidate
        // locations in order of precedence. This favors local classpath/filesystem
        // templates first and then considers common extension permutations.
        // If `remoteEnabled` is true, callers will later attempt to fetch from
        // the config server instead of relying on these local candidates.
        // Return the first existing candidate path, or null if none exist.
        if (templateId == null) return null;

        // 1. Try as is
        if (exists(templateId)) return templateId;

        // 2. Try with templates/ prefix
        String withPrefix = "templates/" + templateId;
        if (exists(withPrefix)) return withPrefix;

        // 3. Try with extensions
        String[] extensions = {".yaml", ".yml", ".json"};
        for (String ext : extensions) {
            if (exists(templateId + ext)) return templateId + ext;
            if (exists("templates/" + templateId + ext)) return "templates/" + templateId + ext;
        }

        // Nothing found
        return null;
    }

    private boolean exists(String path) {
        try {
            if (new ClassPathResource(path).exists() || new File(path).exists()) {
                return true;
            }
            // If the template isn't present locally we return true when remote
            // templates are enabled and a config server is configured because
            // we will attempt to fetch it from the config server later.
            return remoteEnabled && configServerUri != null && !configServerUri.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    private DocumentTemplate loadFromJson(String path) {
        try {
            InputStream inputStream = getInputStream(path);
            return jsonMapper.readValue(inputStream, DocumentTemplate.class);
        } catch (TemplateLoadingException e) {
            throw e; // Re-throw TemplateLoadingException as-is
        } catch (IOException e) {
            log.error("Failed to parse JSON template: {}", path, e);
            throw new TemplateLoadingException(
                "TEMPLATE_PARSE_ERROR",
                "Failed to parse JSON template: " + path,
                e
            );
        }
    }
    
    private DocumentTemplate loadFromYaml(String path) {
        try {
            InputStream inputStream = getInputStream(path);
            return yamlMapper.readValue(inputStream, DocumentTemplate.class);
        } catch (TemplateLoadingException e) {
            throw e; // Re-throw TemplateLoadingException as-is
        } catch (IOException e) {
            log.error("Failed to parse YAML template: {}", path, e);
            throw new TemplateLoadingException(
                "TEMPLATE_PARSE_ERROR",
                "Failed to parse YAML template: " + path,
                e
            );
        }
    }
    
    private InputStream getInputStream(String path) throws TemplateLoadingException {
        // If remote fetching is enabled, use Config Server as the single source of truth
        // IMPORTANT: When `remoteEnabled` is true the code will *not* fall back to
        // local classpath/filesystem when a fetch fails — this prevents accidental
        // mismatches between local and remote templates. During development or tests
        // disable remoteEnabled to force local loading.
        if (remoteEnabled && configServerUri != null && !configServerUri.isEmpty()) {
            log.debug("Remote templates enabled; attempting to fetch from Config Server at {} for path {}", configServerUri, path);
            try {
                InputStream remoteStream = getFromConfigServer(path);
                if (remoteStream != null) {
                    log.info("Loaded template from Config Server (HTTP): {}", path);
                    return remoteStream;
                }
            } catch (IOException e) {
                // Remote is the source of truth - don't fall back to local sources
                log.error("Failed to fetch template from Config Server for path '{}': {}", path, e.getMessage());
                throw new TemplateLoadingException(
                    "CONFIG_SERVER_ERROR",
                    "Template not found on Config Server at " + configServerUri,
                    e
                );
            } catch (Exception e) {
                log.error("Unexpected error fetching from Config Server for path '{}': {}", path, e.getMessage());
                throw new TemplateLoadingException(
                    "CONFIG_SERVER_ERROR",
                    "Unexpected error fetching template: " + e.getMessage(),
                    e
                );
            }
        }

        // Load from classpath only (single source of truth)
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                log.info("Loaded template from classpath resource: {}", path);
                return resource.getInputStream();
            }
        } catch (Exception e) {
            log.debug("Template not found in classpath: {}", path);
        }

        throw new TemplateLoadingException(
            "TEMPLATE_NOT_FOUND",
            buildDetailedErrorMessage(path)
        );
    }

    /**
     * Build a detailed error message indicating all locations that were checked
     */
    private String buildDetailedErrorMessage(String path) {
        StringBuilder message = new StringBuilder();
        message.append("PDF template resource not found: ").append(path).append("\n\n");
        message.append("Checked the following location:\n");
        message.append("  • Classpath/Resources: src/main/resources/").append(path).append("\n\n");
        message.append("If this is a tenant-specific resource, verify that the namespace and file path are correct.\n");
        message.append("Expected structure: src/main/resources/{namespace}/templates/{resourcePath}");
        
        return message.toString();
    }
    
    /**
     * Resolve the config-repo file path based on the git URI in spring.cloud.config.uri
     * For a local git repo, converts http://localhost:8888 -> file path
     */
    private String resolveConfigRepoPath(String path) {
        // Config repo is typically at: ../config-repo (relative to tech-pocs)
        // or at the path specified in the config-server git.uri
        try {
            // Try common patterns for config-repo location
            String[] possiblePaths = {
                "config-repo/" + path,
                "../config-repo/" + path,
                "../../config-repo/" + path,
                "/workspaces/techno-info/tech-pocs/config-repo/" + path
            };
            
            for (String testPath : possiblePaths) {
                File f = new File(testPath);
                if (f.exists()) {
                    log.debug("Found config-repo file at: {}", f.getAbsolutePath());
                    return f.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            log.debug("Error resolving config-repo path: {}", e.getMessage());
        }
        return null;
    }

    private InputStream getFromConfigServer(String path) throws IOException {
        // Extract namespace (application name) from path
        // Path format: {namespace}/templates/... 
        String applicationName = extractApplicationNameFromPath(path);
        
        // Remove the {namespace}/ prefix from path for the config server URL
        // Config server's search-paths=/{application} will add the application folder
        String configPath = path;
        if (applicationName != null && !applicationName.isEmpty() && !applicationName.equals("templates")) {
            String prefix = applicationName + "/";
            if (path.startsWith(prefix)) {
                configPath = path.substring(prefix.length());
            }
        }
        
        // Spring Cloud Config Server Plain Text API: /{application}/{profile}/{label}/{path}
        // With search-paths=/{application}, config-server will look in /config-repo/{application}/
        // and then append the remaining path
        String url = String.format("%s/%s/%s/%s/%s", 
            configServerUri, applicationName, activeProfile, configLabel, configPath);
        
        log.info("Fetching template from Config Server: {}", url);
        
        // Use plain java URL openStream to retrieve raw bytes to support binary resources
        // (some Http clients may interpret response as text and corrupt binary PDFs)
        try {
            java.net.URL u = new java.net.URL(url);
            java.net.URLConnection conn = u.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            try (InputStream is = conn.getInputStream()) {
                byte[] data = is.readAllBytes();
                if (data != null && data.length > 0) {
                    return new ByteArrayInputStream(data);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch template from Config Server: {}", url, e);
            throw new IOException("Failed to fetch template from Config Server: " + url, e);
        }

        throw new IOException("Template not found on Config Server: " + path);
    }

    /**
     * Extract the application name/namespace from a path.
     * Path format: {namespace}/templates/... returns {namespace}
     * Path format: templates/... returns default applicationName
     */
    private String extractApplicationNameFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return applicationName;
        }
        
        String[] parts = path.split("/");
        if (parts.length > 1 && !parts[0].equals("templates")) {
            // First part is the namespace (e.g., "common-templates" or "tenant-a")
            return parts[0];
        }
        
        // No namespace prefix, use default application name
        return applicationName;
    }
}
