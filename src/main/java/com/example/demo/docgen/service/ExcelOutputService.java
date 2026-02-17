package com.example.demo.docgen.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Helper service to serialize Workbooks to bytes for HTTP responses or storage.
 */
@Component
public class ExcelOutputService {

    public byte[] toBytes(Workbook workbook) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            workbook.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Excel workbook", e);
        }
    }
}
