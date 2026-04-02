package org.example.licientajobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class JsonFallbackService {

    private static final Logger logger = LoggerFactory.getLogger(JsonFallbackService.class);
    private final File fallbackFile = new File("fallback-data.json");
    private final ObjectMapper objectMapper;

    public JsonFallbackService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @PostConstruct
    public void init() {
        logger.info("JsonFallbackService initialized. Using file: {}", fallbackFile.getAbsolutePath());
        if (!fallbackFile.exists()) {
            logger.warn("Fallback data file not found at: {}. The application may not have initial data.", fallbackFile.getAbsolutePath());
        }
    }

    public FallbackData readFallbackData() {
        if (!fallbackFile.exists()) {
            return new FallbackData();
        }
        try {
            return objectMapper.readValue(fallbackFile, FallbackData.class);
        } catch (IOException e) {
            logger.error("Error reading from fallback file: {}", fallbackFile.getAbsolutePath(), e);
            return new FallbackData();
        }
    }

    public void writeFallbackData(FallbackData data) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(fallbackFile, data);
            logger.info("Successfully wrote data to fallback file: {}", fallbackFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error writing to fallback file: {}", fallbackFile.getAbsolutePath(), e);
        }
    }
}