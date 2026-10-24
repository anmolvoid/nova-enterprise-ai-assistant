package com.govind.aiassistant.util;

import com.govind.aiassistant.service.ChunkingService;
import com.govind.aiassistant.service.PdfService;

import java.util.List;

public class ChunkTest {

    public static void main(String[] args) throws Exception {

        PdfService pdfService = new PdfService();
        ChunkingService chunkingService = new ChunkingService();

        String text = pdfService.extractText(
            "C:\\Users\\Govind Jha\\Downloads\\GOVIND KUMAR_RESUME.pdf"
        );

        List<String> chunks = chunkingService.chunkText(
            text,
            1000,
            200
        );

        System.out.println("Total Chunks = " + chunks.size());

        System.out.println("\n===== FIRST CHUNK =====\n");
        System.out.println(chunks.get(0));

        System.out.println("\n===== SECOND CHUNK =====\n");
        System.out.println(chunks.get(1));
    }
}