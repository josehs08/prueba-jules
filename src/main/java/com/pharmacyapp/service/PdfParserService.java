package com.pharmacyapp.service;

import com.pharmacyapp.model.Medication;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PdfParserService {

    // Regex Patterns
    // More specific medication name patterns can be complex. Starting with a generic line.
    // Consider looking for lines BEFORE dosage/quantity if "Name:" is not present.
    // This pattern attempts to capture a line that might be a medication name, especially if followed by dosage.
    private static final Pattern NAME_PATTERN = Pattern.compile(
        "(?:Medication|Name|Drug)[:\\s]*([A-Za-z0-9\\s.,'-]+)(?=\\n|$|\\s*(?:Dosage|Quantity|Strength))", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_FALLBACK_PATTERN = Pattern.compile(
        "^([A-Za-z0-9\\s.,'-]+)(?:\\n|\\r\\n)\\s*(?:\\d+(\\.\\d+)?\\s?(mg|ml|g|mcg|iu|units))", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);


    private static final Pattern DOSAGE_PATTERN = Pattern.compile(
        "(?:Dosage|Strength)[:\\s]*([\\d.,]+\\s*(?:mg|g|mcg|µg|mL|L|IU|units(?:/mL)?)(?:\\s*/\\s*[\\d.,]+\\s*(?:mg|g|mcg|µg|mL|L|IU|units(?:/mL)?))?)(?:\\s*\\([^)]*\\))?", Pattern.CASE_INSENSITIVE);
    // Fallback if no explicit "Dosage:" or "Strength:" label
    private static final Pattern DOSAGE_FALLBACK_PATTERN = Pattern.compile(
        "(\\b[\\d.,]+\\s*(?:mg|g|mcg|µg|mL|L|IU|units(?:/mL)?)(?:\\s*/\\s*[\\d.,]+\\s*(?:mg|g|mcg|µg|mL|L|IU|units(?:/mL)?))?)(?:\\s*\\([^)]*\\))?", Pattern.CASE_INSENSITIVE);


    private static final Pattern QUANTITY_PATTERN = Pattern.compile(
        "(?:Quantity|Qty|Pack Size)[:\\s]*([\\d.,]+\\s*(?:Tablets?|Capsules?|Caps?|Tabs?|Bottle|Pack|Vial|Syringe|Puffs|Inhaler|Each|EA))", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUANTITY_FALLBACK_PATTERN = Pattern.compile( // Broader search if no label
        "(\\b\\d+\\b)\\s*(?:Tablets?|Capsules?|Caps?|Tabs?|Bottle|Pack|Vial|Syringe|Puffs|Inhaler|Each|EA)", Pattern.CASE_INSENSITIVE);


    private static final Pattern EXP_DATE_PATTERN = Pattern.compile(
        "(?:EXP|Expiry|Expiration Date|Use By)[:\\s]*([0-9]{1,2}[-/][0-9]{1,2}[-/][0-9]{2,4}|[0-9]{1,2}[-/][0-9]{2,4}|[A-Za-z]{3,}\\s\\d{1,2},?\\s\\d{2,4}|\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXP_DATE_FALLBACK_PATTERN = Pattern.compile( // Fallback for dates like MM/YY or MM/YYYY without explicit label
        "\\b([0-9]{1,2}[-/](?:[0-9]{4}|[0-9]{2}))\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern MANUFACTURER_PATTERN = Pattern.compile(
        "(?:Manufacturer|MFG|Mfd\\. By|Distributed By)[:\\s]*([A-Za-z0-9\\s.,'-]+)(?=\\n|$)", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOT_NUMBER_PATTERN = Pattern.compile(
        "(?:Lot|Batch|Lot No\\.|Batch No\\.)[:\\s#]*([A-Za-z0-9-]+)(?=\\n|$|\\s)", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yy"),
        DateTimeFormatter.ofPattern("MM/yyyy"), // Handles MM/YYYY, defaults to last day of month
        DateTimeFormatter.ofPattern("M/yy"),    // Handles M/YY, defaults to last day of month
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MMM d, yyyy"),
        DateTimeFormatter.ofPattern("MMMM d, yyyy")
    };

    /**
     * Extracts all text content from a PDF file.
     *
     * @param pdfFilePath The path to the PDF file.
     * @return A String containing all extracted text, or null if an error occurs.
     */
    public String extractTextFromFile(String pdfFilePath) {
        File pdfFile = new File(pdfFilePath);
        if (!pdfFile.exists()) {
            System.err.println("PDF file not found: " + pdfFilePath);
            return null;
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            if (!document.isEncrypted()) {
                PDFTextStripper textStripper = new PDFTextStripper();
                return textStripper.getText(document);
            } else {
                System.err.println("Cannot extract text from an encrypted PDF: " + pdfFilePath);
                return null;
            }
        } catch (IOException e) {
            // Log the error or throw a custom exception
            System.err.println("Error reading PDF file: " + pdfFilePath + " - " + e.getMessage());
            // e.printStackTrace(); // Consider using a logger
            return null;
        }
    }

    public Medication parseMedicationDetails(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new Medication(null, null, 0, null, null, null); // Return empty/default Medication
        }

        String name = extractField(text, NAME_PATTERN, 1);
        if (name == null || name.trim().isEmpty()) {
            name = extractField(text, NAME_FALLBACK_PATTERN, 1);
        }
        
        String dosage = extractField(text, DOSAGE_PATTERN, 1);
        if (dosage == null || dosage.trim().isEmpty()) {
            dosage = extractField(text, DOSAGE_FALLBACK_PATTERN, 1);
        }

        String quantityStr = extractField(text, QUANTITY_PATTERN, 1);
         if (quantityStr == null || quantityStr.trim().isEmpty()) {
            quantityStr = extractField(text, QUANTITY_FALLBACK_PATTERN, 1);
        }
        // Try to extract just the number part from quantity string if it includes units like "30 Tablets"
        int quantity = 0;
        if (quantityStr != null) {
            Matcher numMatcher = Pattern.compile("(\\d+)").matcher(quantityStr);
            if (numMatcher.find()) {
                try {
                    quantity = Integer.parseInt(numMatcher.group(1));
                } catch (NumberFormatException e) {
                    // Log error or ignore
                }
            }
        }


        String expDateStr = extractField(text, EXP_DATE_PATTERN, 1);
        if (expDateStr == null || expDateStr.trim().isEmpty()){
            expDateStr = extractField(text, EXP_DATE_FALLBACK_PATTERN, 1);
        }
        LocalDate expirationDate = parseDate(expDateStr);

        String manufacturer = extractField(text, MANUFACTURER_PATTERN, 1);
        String lotNumber = extractField(text, LOT_NUMBER_PATTERN, 1);

        // Clean up extracted fields if necessary (e.g., trim whitespace)
        name = (name != null) ? name.trim() : null;
        dosage = (dosage != null) ? dosage.trim() : null;
        // quantity is already int
        manufacturer = (manufacturer != null) ? manufacturer.trim() : null;
        lotNumber = (lotNumber != null) ? lotNumber.trim() : null;


        return new Medication(name, dosage, quantity, expirationDate, manufacturer, lotNumber);
    }

    private String extractField(String text, Pattern pattern, int groupIndex) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(groupIndex).trim();
        }
        return null;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                // For formats like MM/yy or MM/yyyy, default to the last day of the month.
                if (formatter.toString().contains("MM/yy") && !formatter.toString().contains("d")) { // Approximation
                    YearMonth ym = YearMonth.parse(dateStr.trim(), formatter);
                    return ym.atEndOfMonth();
                }
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        System.err.println("Could not parse date: " + dateStr);
        return null; // Or throw an exception, or log
    }
}
