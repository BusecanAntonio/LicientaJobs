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
    // Renamed to reflect its purpose as a comprehensive fallback for all data
    private final File allDataFallbackFile = new File("FallbackData.json");
    private final File usersFallbackFile = new File("usersRe.json");
    // Keeping studentsFallbackFile for now, but consider consolidating if all student data is in FallbackData.json
    private final File studentsFallbackFile = new File("src/main/resources/Students.json");
    private final ObjectMapper objectMapper;

    public JsonFallbackService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @PostConstruct
    public void init() {
        logger.info("JsonFallbackService initialized. Using allDataFallbackFile: {}", allDataFallbackFile.getAbsolutePath());
        if (!allDataFallbackFile.exists()) {
            try {
                logger.warn("All data fallback file not found at: {}. Creating an empty one.", allDataFallbackFile.getAbsolutePath());
                allDataFallbackFile.createNewFile();
                writeFallbackData(new FallbackData()); // Write an empty FallbackData object
            } catch (IOException e) {
                logger.error("Error creating all data fallback file", e);
            }
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
            // Consider creating an empty one or removing if allDataFallbackFile is the primary student backup
        }
    }

    public FallbackData readFallbackData() {
        if (!allDataFallbackFile.exists()) {
            return new FallbackData();
        }
        try {
            return objectMapper.readValue(allDataFallbackFile, FallbackData.class);
        } catch (IOException e) {
            logger.error("Error reading from all data fallback file: {}", allDataFallbackFile.getAbsolutePath(), e);
            return new FallbackData();
        }
    }

    public void writeFallbackData(FallbackData data) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(allDataFallbackFile, data);
            logger.info("Successfully wrote data to all data fallback file: {}", allDataFallbackFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error writing to all data fallback file: {}", allDataFallbackFile.getAbsolutePath(), e);
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