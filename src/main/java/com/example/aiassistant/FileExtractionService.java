package com.example.aiassistant;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class FileExtractionService {

    private static final int MAX_CHARS = 15000;

    public String extractText(String base64Content, String mimeType, String fileName) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(base64Content);

        String text;
        if (mimeType.equals("application/pdf")) {
            text = extractFromPdf(bytes);
        } else if (mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || mimeType.equals("application/vnd.ms-excel")) {
            text = extractFromExcel(bytes);
        } else if (mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            text = extractFromWord(bytes);
        } else if (mimeType.startsWith("text/") || mimeType.equals("text/csv")) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + mimeType);
        }

        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS) + "\n\n[Content truncated — file exceeds " + MAX_CHARS + " characters]";
        }

        return "File: " + fileName + "\n\n" + text;
    }

    private String extractFromPdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractFromExcel(byte[] bytes) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                for (Row row : sheet) {
                    StringBuilder rowText = new StringBuilder();
                    for (Cell cell : row) {
                        rowText.append(cellToString(cell)).append("\t");
                    }
                    if (!rowText.toString().isBlank()) {
                        sb.append(rowText).append("\n");
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    private String cellToString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String extractFromWord(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                sb.append(paragraph.getText()).append("\n");
            }
            return sb.toString();
        }
    }
}
