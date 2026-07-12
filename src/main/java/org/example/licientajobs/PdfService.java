package org.example.licientajobs;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PdfService {

    private static final Logger logger = LoggerFactory.getLogger(PdfService.class);

    private final OllamaService ollamaService;

    public PdfService(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    /**
     * Extrage textul complet dintr-un PDF
     */
    /**
     * Extrage textul complet dintr-un PDF (versiune mai robustă)
     */
    public String extractTextFromPdf(Path pdfPath) throws IOException {
        if (!Files.exists(pdfPath)) {
            throw new IOException("Fișierul PDF nu există: " + pdfPath);
        }

        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            if (document.isEncrypted()) {
                throw new IOException("Documentul PDF este protejat cu parolă.");
            }

            // Configurare mai tolerantă
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setSortByPosition(true);
            pdfStripper.setStartPage(1);
            pdfStripper.setEndPage(document.getNumberOfPages());

            String text = pdfStripper.getText(document);

            return text.replaceAll("\\s+", " ").trim();

        } catch (EOFException e) {
            logger.error("Eroare EOF la procesarea PDF-ului (posibil fișier corupt sau font problematic): {}", pdfPath.getFileName(), e);
            return "";  // returnăm string gol ca să nu crape totul
        } catch (Exception e) {
            logger.error("Eroare generală la extragerea textului din PDF: {}", pdfPath.getFileName(), e);
            throw new IOException("Nu s-a putut citi conținutul PDF-ului.", e);
        }
    }

    /**
     * Convertește PDF în fișier TXT (dacă ai nevoie să salvezi textul)
     */
    public void convertPdfToTxt(Path inputPath, Path txtPath) throws IOException {
        String text = extractText(inputPath);   // înainte era: extractTextFromPdf(pdfPath)
        Files.writeString(txtPath, text);
        logger.info("Fișier convertit în TXT: {} → {} ({} caractere)",
                inputPath.getFileName(), txtPath.getFileName(), text.length());
    }


    /**
     * Citește direct un fișier TXT
     */
    public String extractTextFromTxt(Path txtPath) throws IOException {
        if (!Files.exists(txtPath)) {
            throw new IOException("Fișierul TXT nu există: " + txtPath);
        }
        return Files.readString(txtPath).trim();
    }

    public String extractText(Path filePath) throws IOException {
        String filename = filePath.getFileName().toString().toLowerCase();

        if (filename.endsWith(".pdf")) {
            return extractTextFromPdf(filePath);
        } else if (filename.endsWith(".txt")) {
            return extractTextFromTxt(filePath);
        } else {
            throw new IOException("Format de fișier nesuportat: " + filename +
                    " (acceptate: .pdf, .txt)");
        }
    }

    /**
     * Detectează automat tipul documentului folosind Ollama
     * Returnează: CV, DIPLOMA, RECOMMENDATION sau UNKNOWN
     */
    public String detectDocumentType(Path filePath) throws IOException {
        String text = extractText(filePath).toLowerCase();   // înainte: extractTextFromPdf(pdfPath)

        if (text.length() < 100) {
            return "UNKNOWN";
        }

        // === 1. Reguli simple și rapide (fără Ollama) ===
        if (text.contains("curriculum vitae") ||
                text.contains("profil profesional") ||
                text.contains("competente tehnice") ||
                text.contains("proiecte") && text.contains("educație")) {
            return "CV";
        }

        if (text.contains("diploma de") ||
                text.contains("absolvire") &&
                        text.contains("universitatea")) {
            return "DIPLOMA";
        }

        if (text.contains("scrisoare de recomandare") ||
                text.contains("recomand cu căldură") ||
                text.contains("to whom it may concern")) {
            return "RECOMMENDATION";
        }

        // === 2. Dacă regulile simple nu sunt suficiente → folosim Ollama ===
        String prompt = """
    Document Classification Task.
    Return ONLY one word: CV, DIPLOMA, RECOMMENDATION or UNKNOWN.

    Strong indicators:
    - CV: Curriculum Vitae, Competențe, Proiecte, Experiență, Studii, Git, Java, Python
    - DIPLOMA: Diplomă, Absolvire, Licență, Master, Universitatea, Anul
    - RECOMMENDATION: Scrisoare de recomandare, Recomand, To Whom It May Concern

    Text:
    %s
    """.formatted(text.length() > 5000 ? text.substring(0, 5000) : text);

        try {
            String response = ollamaService.generateResponse(prompt).trim().toUpperCase();

            if (response.contains("CV")) return "CV";
            if (response.contains("DIPLOMA")) return "DIPLOMA";
            if (response.contains("RECOMMENDATION")) return "RECOMMENDATION";

        } catch (Exception e) {
            logger.warn("Ollama detectare eșuată", e);
        }

        return "UNKNOWN";
    }
}