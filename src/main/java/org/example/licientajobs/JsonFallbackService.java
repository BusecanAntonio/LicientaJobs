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
    private final File fallbackFile = new File("Jobs.json");
    private final File usersFallbackFile = new File("usersRe.json");
    private final File studentsFallbackFile = new File("src/main/resources/Students.json");
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
        if (!usersFallbackFile.exists()) {
            try {
                usersFallbackFile.createNewFile();
                writeUsersFallbackData(new ArrayList<>());
            } catch (IOException e) {
                logger.error("Error creating users fallback file", e);
            }
        }
        if (!studentsFallbackFile.exists()) {
            logger.warn("Students fallback file not found at: {}.", studentsFallbackFile.getAbsolutePath());
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
    
    public List<Student> readStudentsFallbackData() {
        if (!studentsFallbackFile.exists()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(studentsFallbackFile, new TypeReference<List<Student>>() {});
        } catch (IOException e) {
            logger.error("Error reading from students fallback file: {}", studentsFallbackFile.getAbsolutePath(), e);
            return new ArrayList<>();
        }
    }

    public void writeStudentsFallbackData(List<Student> students) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(studentsFallbackFile, students);
            logger.info("Successfully wrote data to students fallback file: {}", studentsFallbackFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error writing to students fallback file: {}", studentsFallbackFile.getAbsolutePath(), e);
        }
    }

    public List<User> readUsersFallbackData() {
        if (!usersFallbackFile.exists()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(usersFallbackFile, new TypeReference<List<User>>() {});
        } catch (IOException e) {
            logger.error("Error reading from users fallback file", e);
            return new ArrayList<>();
        }
    }

    public void writeUsersFallbackData(List<User> users) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(usersFallbackFile, users);
            logger.info("Successfully wrote users data to fallback file: {}", usersFallbackFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error writing to users fallback file", e);
        }
    }
}
