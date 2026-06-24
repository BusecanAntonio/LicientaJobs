package org.example.licientajobs;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Service
public class PdfService {

    /**
     * Converts a PDF file to a TXT file.
     *
     * @param pdfPath The path to the input PDF file.
     * @param txtPath The path where the output TXT file will be saved.
     * @throws IOException If there is an error reading the PDF or writing the TXT.
     */
    public void convertPdfToTxt(Path pdfPath, Path txtPath) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            if (document.isEncrypted()) {
                throw new IOException("Cannot process encrypted PDF documents.");
            }
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);
            java.nio.file.Files.writeString(txtPath, text);
        }
    }
}
