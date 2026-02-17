package com.example.demo.docgen.controller;

import com.example.demo.docgen.model.DocumentGenerationRequest;
import com.example.demo.docgen.service.DocumentComposer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for document generation
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentComposer documentComposer;
    
    /**
     * Generate a PDF document from a template and data
     *
     * POST /api/documents/generate
     * {
     *   "namespace": "tenant-a",
     *   "templateId": "enrollment-form.yaml",
     *   "data": {
     *     "applicant": { "firstName": "John", "lastName": "Doe" },
     *     ...
     *   }
     * }
     *
     * The namespace identifies which tenant's template directory to use (e.g., "tenant-a", "tenant-b").
     * If omitted, defaults to "common-templates".
     * The templateId is relative to {namespace}/templates/ folder.
     *
     * @param request Generation request with namespace, templateId, and data
     * @return PDF document as byte array
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateDocument(@RequestBody DocumentGenerationRequest request) {
        log.info("Received document generation request for template: {} from namespace: {}", request.getTemplateId(), request.getNamespace());
        try {
            byte[] pdf = documentComposer.generateDocument(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "document.pdf");
            headers.setContentLength(pdf.length);

            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (com.example.demo.docgen.exception.TemplateLoadingException tle) {
            String code = tle.getCode();
            String description = tle.getDescription();

            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("code", code);
            body.put("description", description);

            if ("TEMPLATE_NOT_FOUND".equals(code)) {
                return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
            } else if ("UNRESOLVED_PLACEHOLDER".equals(code)) {
                return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
            }

            return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Generate and download filled Excel workbook (XLSX)
     */
    @PostMapping("/generate/excel")
    public ResponseEntity<?> generateExcel(@RequestBody com.example.demo.docgen.model.DocumentGenerationRequest request) {
        log.info("Received Excel generation request for template: {} from namespace: {}", request.getTemplateId(), request.getNamespace());
        try {
            byte[] xlsx = documentComposer.generateExcel(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "document.xlsx");
            headers.setContentLength(xlsx.length);

            return new ResponseEntity<>(xlsx, headers, HttpStatus.OK);
        } catch (com.example.demo.docgen.exception.TemplateLoadingException tle) {
            String code = tle.getCode();
            String description = tle.getDescription();

            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("code", code);
            body.put("description", description);

            if ("TEMPLATE_NOT_FOUND".equals(code)) {
                return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
            } else if ("UNRESOLVED_PLACEHOLDER".equals(code)) {
                return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
            }

            return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Document generation service is running");
    }
}
