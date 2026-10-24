package com.govind.aiassistant.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;

public class PdfTest {

    public static void main(String[] args) throws Exception {

       File file = new File(
    "C:\\Users\\Govind Jha\\Downloads\\GOVIND KUMAR_RESUME.pdf"
);

        System.out.println(file.getAbsolutePath());
        System.out.println(file.exists());

        try (PDDocument document = Loader.loadPDF(file)) {

            PDFTextStripper stripper = new PDFTextStripper();

            String text = stripper.getText(document);

            System.out.println(text);
        }
    }
}