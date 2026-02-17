# Quick Manual Testing - Excel Section Renderer

## 1. Start the Application
mvn spring-boot:run

## 2. Test Basic Excel Rendering
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "common-templates",
    "templateId": "excel-enrollment",
    "data": {
      "firstName": "John",
      "lastName": "Doe",
      "email": "john@example.com",
      "phoneNumber": "555-1234",
      "address": {
        "street": "123 Main St",
        "city": "Springfield",
        "state": "IL",
        "zipCode": "62701"
      },
      "policyNumber": "POL-001",
      "coverageType": "Family",
      "effectiveDate": "2026-03-01",
      "planName": "Gold"
    }
  }' -v

## 3. Test with Different Namespace
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "tenant-a",
    "templateId": "excel-enrollment",
    "data": {
      "firstName": "Alice",
      "lastName": "Smith",
      "email": "alice@example.com",
      "phoneNumber": "555-5678",
      "address": {
        "street": "456 Oak Ave",
        "city": "Metropolis",
        "state": "NY",
        "zipCode": "10001"
      },
      "policyNumber": "POL-002",
      "coverageType": "Individual",
      "effectiveDate": "2026-04-01",
      "planName": "Silver"
    }
  }' -v

## 4. Run Unit Tests
mvn test -Dtest=ExcelSectionRendererTest

## 5. Run All Tests
mvn test

## 6. Build Project
mvn clean compile

## 7. Check Excel Output
# The filled Excel workbook will be stored in the RenderContext metadata
# Future: ExcelOutputService will enable direct Excel file download
