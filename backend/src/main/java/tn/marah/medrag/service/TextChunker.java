package tn.marah.medrag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    public record Chunk(int index, int page, String content) {}

    private final int chunkSize;
    private final int overlap;

    public TextChunker(@Value("${app.rag.chunk-size}") int chunkSize,
                       @Value("${app.rag.chunk-overlap}") int overlap) {
        if (overlap >= chunkSize / 2) {
            throw new IllegalArgumentException("chunk-overlap doit etre inferieur a la moitie de chunk-size");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<Chunk> chunkPage(String rawText, int page, int startIndex) {
        String text = rawText.replace("\u0000", "").replaceAll("\\s+", " ").trim();
        List<Chunk> chunks = new ArrayList<>();
        int index = startIndex;
        int position = 0;

        while (position < text.length()) {
            int end = Math.min(position + chunkSize, text.length());
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > position + chunkSize / 2) {
                    end = lastSpace;
                }
            }
            String piece = text.substring(position, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(new Chunk(index++, page, piece));
            }
            if (end >= text.length()) {
                break;
            }
            position = Math.max(end - overlap, position + 1);
        }
        return chunks;
    }
}
