package com.pharmacyapp.service;

import com.pharmacyapp.model.Medication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class PdfParserServiceTest {

    private static PdfParserService pdfParserService;
    private static final String TEST_RESOURCES_PATH = "src/test/resources/";
    private static final String SAMPLE_PDF_FILENAME = "sample_invoice.pdf"; // Used for extractTextFromFile tests
    private static final String NON_EXISTENT_PDF_FILENAME = "non_existent.pdf"; // Used for extractTextFromFile tests

    @BeforeAll
    static void setUp() {
        pdfParserService = new PdfParserService();
    }

    // --- Tests for extractTextFromFile ---
    @Test
    void testExtractTextFromFile_success() {
        Path pdfPath = Paths.get(TEST_RESOURCES_PATH, SAMPLE_PDF_FILENAME);
        assertTrue(Files.exists(pdfPath), "Test PDF file should exist: " + pdfPath.toAbsolutePath());

        String extractedText = pdfParserService.extractTextFromFile(pdfPath.toString());

        assertNotNull(extractedText, "Extracted text should not be null for a valid PDF.");
        assertFalse(extractedText.isEmpty(), "Extracted text should not be empty for the sample PDF.");
        assertTrue(extractedText.contains("Sample PDF Content"), "Extracted text should contain known content from sample PDF.");
        assertTrue(extractedText.contains("TestMed 100mg"), "Extracted text should contain medication information from sample PDF.");
    }

    @Test
    void testExtractTextFromFile_fileNotFound() {
        String nonExistentFilePath = Paths.get(TEST_RESOURCES_PATH, NON_EXISTENT_PDF_FILENAME).toString();
        String extractedText = pdfParserService.extractTextFromFile(nonExistentFilePath);
        assertNull(extractedText, "Extracted text should be null when PDF file does not exist.");
    }

    @Test
    void testExtractTextFromFile_notAPdf(@TempDir Path tempDir) throws IOException {
        Path notAPdfFile = tempDir.resolve("not_a_pdf.txt");
        Files.writeString(notAPdfFile, "This is not a PDF file, just plain text.");

        String extractedText = pdfParserService.extractTextFromFile(notAPdfFile.toString());
        assertNull(extractedText, "Extracted text should be null for a non-PDF file (e.g. a .txt file).");
    }

    @Test
    void testExtractTextFromFile_emptyFile(@TempDir Path tempDir) throws IOException {
        Path emptyFile = tempDir.resolve("empty.pdf"); // File is empty, not a valid PDF structure
        Files.createFile(emptyFile);

        String extractedText = pdfParserService.extractTextFromFile(emptyFile.toString());
        // PDDocument.load on an empty file will likely throw an IOException.
        assertNull(extractedText, "Extracted text should be null for an empty file that isn't a valid PDF.");
    }

    // --- Tests for parseMedicationDetails ---

    @Test
    void testParseMedicationDetails_allFieldsPresent() {
        String text = "Medication: SuperDrug X\n" +
                      "Dosage: 50 mg\n" +
                      "Quantity: 30 Tablets\n" +
                      "Expiration Date: 12/2025\n" +
                      "Manufacturer: PharmaGiant Inc.\n" +
                      "Lot No.: SGX12345";
        Medication med = pdfParserService.parseMedicationDetails(text);

        assertNotNull(med);
        assertEquals("SuperDrug X", med.getName());
        assertEquals("50 mg", med.getDosage());
        assertEquals(30, med.getQuantity());
        assertEquals(YearMonth.of(2025, 12).atEndOfMonth(), med.getExpirationDate());
        assertEquals("PharmaGiant Inc.", med.getManufacturer());
        assertEquals("SGX12345", med.getLotNumber());
    }
    
    @Test
    void testParseMedicationDetails_nameFallback() {
        String text = "Amoxicillin Trihydrate\n" +
                      "250 mg / 5 mL\n" + // Dosage on next line, triggers fallback name regex
                      "Quantity: 1 Bottle (150 mL when reconstituted)\n" +
                      "EXP: 05/26\n" +
                      "MFG: GoodPharma\n" +
                      "Lot: XYZ001";
        Medication med = pdfParserService.parseMedicationDetails(text);
        assertNotNull(med);
        assertEquals("Amoxicillin Trihydrate", med.getName());
        assertEquals("250 mg / 5 mL", med.getDosage());
        assertEquals(1, med.getQuantity()); // "1 Bottle"
        assertEquals(YearMonth.of(2026, 5).atEndOfMonth(), med.getExpirationDate());
        assertEquals("GoodPharma", med.getManufacturer());
        assertEquals("XYZ001", med.getLotNumber());
    }


    @Test
    void testParseMedicationDetails_someFieldsMissing() {
        String text = "Drug Name: CommonPill\n" +
                      "Strength: 100mcg\n" +
                      "EXP: 01/01/2027";
        Medication med = pdfParserService.parseMedicationDetails(text);

        assertNotNull(med);
        assertEquals("CommonPill", med.getName());
        assertEquals("100mcg", med.getDosage());
        assertEquals(0, med.getQuantity()); // Quantity missing
        assertEquals(LocalDate.of(2027, 1, 1), med.getExpirationDate());
        assertNull(med.getManufacturer()); // Manufacturer missing
        assertNull(med.getLotNumber());     // Lot number missing
    }

    @ParameterizedTest
    @CsvSource({
            "Expiry: 03/25, 2025-03-31",          // MM/yy
            "EXP: 12/2024, 2024-12-31",          // MM/yyyy
            "Expiration Date: 05/15/2026, 2026-05-15", // MM/dd/yyyy
            "Use By: 1/5/27, 2027-05-01",            // M/d/yy
            "2028-07-15, 2028-07-15",             // yyyy-MM-dd (fallback)
            "Sep 5, 2029, 2029-09-05",            // MMM d, yyyy
            "October 10, 2030, 2030-10-10"       // MMMM d, yyyy
    })
    void testParseMedicationDetails_variousDateFormats(String dateText, String expectedDateStr) {
        String text = "Medication: DateTest\nDosage: 1mg\nQuantity: 1 Tab\n" + dateText;
        Medication med = pdfParserService.parseMedicationDetails(text);
        assertNotNull(med);
        LocalDate expectedDate;
        if (dateText.matches(".*\\d{1,2}/\\d{2,4}") && !dateText.matches(".*\\d{1,2}/\\d{1,2}/\\d{2,4}")) {
             // Handles MM/yy and MM/yyyy by taking end of month
            String[] parts = dateText.split("[:\\s]*")[1].split("/");
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);
            if (year < 100) year += 2000; // Convert yy to yyyy
            expectedDate = YearMonth.of(year, month).atEndOfMonth();
        } else {
            expectedDate = LocalDate.parse(expectedDateStr);
        }
        assertEquals(expectedDate, med.getExpirationDate(), "Failed for date string: " + dateText);
    }

    @Test
    void testParseMedicationDetails_noMedicationInfo() {
        String text = "This is a document about something completely different. No drug info here.";
        Medication med = pdfParserService.parseMedicationDetails(text);

        assertNotNull(med); // Should return an empty Medication object
        assertNull(med.getName());
        assertNull(med.getDosage());
        assertEquals(0, med.getQuantity());
        assertNull(med.getExpirationDate());
        assertNull(med.getManufacturer());
        assertNull(med.getLotNumber());
    }

    @Test
    void testParseMedicationDetails_emptyInput() {
        String text = "";
        Medication med = pdfParserService.parseMedicationDetails(text);
        assertNotNull(med);
        assertNull(med.getName()); // All fields should be null or default
    }

    @Test
    void testParseMedicationDetails_nullInput() {
        Medication med = pdfParserService.parseMedicationDetails(null);
        assertNotNull(med);
        assertNull(med.getName()); // All fields should be null or default
    }
    
    @Test
    void testParseMedicationDetails_quantityWithUnits() {
        String text = "Medication: TestQty\nDosage: 10mg\nQuantity: 60 Capsules\nEXP: 01/2025";
        Medication med = pdfParserService.parseMedicationDetails(text);
        assertEquals(60, med.getQuantity());
    }
    
    @Test
    void testParseMedicationDetails_quantityWithoutUnits() {
         //This test might fail if regex for quantity requires units.
         //The current regex for QUANTITY_PATTERN requires a unit.
         //The fallback does not.
        String text = "Medication: TestQtyNoUnit\nDosage: 10mg\nQty: 90\nEXP: 02/2025";
        Medication med = pdfParserService.parseMedicationDetails(text);
        // If QUANTITY_FALLBACK_PATTERN isn't robust enough, this might be 0.
        // Current QUANTITY_FALLBACK_PATTERN looks for "(\\b\\d+\\b)\\s*(?:Tablets?|...)"
        // which means it still expects a unit *after* the number.
        // To pass this as 90, the fallback would need to be just "(\\b\\d+\\b)" if Qty: label is found
        // OR the main QTY pattern would need to be more lenient.
        // For now, let's assume the label "Qty:" with a number implies the quantity.
        // The current regex for QUANTITY_PATTERN is `(?:Quantity|Qty|Pack Size)[:\\s]*([\\d.,]+\\s*(?:Tablets?|...))`
        // This means "90" alone after "Qty:" will be extracted as "90", then `Integer.parseInt("90")` should be 90.
        // The issue is the "unit" part is in the capturing group.
        // Corrected Quantity regex in PdfParserService: (?:Quantity|Qty|Pack Size)[:\\s]*([\\d.,]+(?:\\s*(?:Tablets?|...))?)
        // The provided regex in previous step was `(?:Quantity|Qty|Pack Size)[:\\s]*([\\d.,]+\\s*(?:Tablets?|Capsules?|Caps?|Tabs?|Bottle|Pack|Vial|Syringe|Puffs|Inhaler|Each|EA))`
        // This means the unit part is inside the capturing group.
        // The code `Matcher numMatcher = Pattern.compile("(\\d+)").matcher(quantityStr);` will extract the number.
        assertEquals(90, med.getQuantity());
    }

    @Test
    void testParseMedicationDetails_dosageFallback() {
        String text = "Lisinopril\n20mg Tablets\nQuantity: 30\nEXP: 10/2028";
        Medication med = pdfParserService.parseMedicationDetails(text);
        assertNotNull(med);
        assertEquals("Lisinopril", med.getName());
        assertEquals("20mg", med.getDosage()); // Fallback dosage should find "20mg"
        assertEquals(30, med.getQuantity());
    }
}
