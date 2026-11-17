package com.ignite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Service
public class PdfIngestionService {
    private static final Logger log = LoggerFactory.getLogger(PdfIngestionService.class);
    private static final int MIN_EXPECTED_CHARS = 100;
    private static final int CHUNK_SIZE_TOKENS = 500;

    private final SimpleVectorStore vectorStore ;
    private final RagProperties props;

    public PdfIngestionService(SimpleVectorStore vectorStore, RagProperties props) {
        this.vectorStore = vectorStore;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartUp(){
        File storeFile = storeFile();
        if (isStoreFresh(storeFile)){
            log.info("Vector store is up to date. Loading {}", storeFile.getAbsolutePath());
        } else {
            log.info("Vector store missing or older than the PDF. Ingesting {}", props.pdf().getFilename());
            ingest(storeFile);
        }
    }

    void ingest(File storeFile){
        List<Document> pages = readPdf();
        List<Document> chunks = split(pages);

        if (chunks.isEmpty()){
            log.error("No chunks produced. Nothing to embed. Check the PDF.");
            return ;
        }
        log.info("Embedding {} chunks with OpenAI ...", chunks.size());
        vectorStore.add(chunks);
        vectorStore.save(storeFile);
        log.info("Saved vectro store to {}", storeFile.getAbsolutePath());
    }

    private List<Document> readPdf() {
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                .build();

        PagePdfDocumentReader reader = new PagePdfDocumentReader(props.pdf(), config);
        List<Document> pages = reader.read();

        int totalChars = pages.stream()
                .map(Document::getText)
                .filter(Objects::nonNull)
                .mapToInt(String::length)
                .sum();

        log.info("Read {} pages from PDF with a total of {} characters", pages.size(), totalChars);
        if (totalChars < MIN_EXPECTED_CHARS){
            log.warn("Very little text extracted. The PDF is probably scanned (images only)" +
                    "and needs OCR before it can be used");
        }
        return pages;
    }

    private List<Document> split (List<Document> pages){
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE_TOKENS)
                .build();
        List<Document> chunks = splitter.apply(pages);
        log.info("Split {} pages into {} chunks", pages.size(), chunks.size());
        return chunks;
    }

    private boolean isStoreFresh(File storeFile){
        if (!storeFile.exists()) {
            return false;
        }
        try{
            return storeFile.lastModified() > props.pdf().lastModified();
        } catch (IOException e) {
            log.warn("Could not read PDF timestamp, re-ingesting : {}", e.getMessage());
            return false;
        }
    }

    private File storeFile(){
        File file = new File(props.vectorStorePath());
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return file;
    }
}
