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
        // Ignore properties in JSON that are not in the Java class (e.g., old fields like "age")
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @PostConstruct
    public void init() {
        logger.info("Initializing JsonFallbackService. File path: {}", fallbackFile.getAbsolutePath());
        if (!fallbackFile.exists()) {
            try {
                if (fallbackFile.createNewFile()) {
                    logger.info("Created fallback file: {}", fallbackFile.getAbsolutePath());
                    writeAllStudents(new ArrayList<>()); // Initialize with empty list
                }
            } catch (IOException e) {
                logger.error("Could not create fallback file: {}", fallbackFile.getAbsolutePath(), e);
            }
        }
    }

    public List<Student> readAllStudents() {
        if (!fallbackFile.exists()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(fallbackFile, new TypeReference<>() {});
        } catch (IOException e) {
            logger.error("Error reading from fallback file: {}", fallbackFile.getAbsolutePath(), e);
            return new ArrayList<>();
        }
    }

    public void writeAllStudents(List<Student> students) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(fallbackFile, students);
            logger.info("Successfully wrote {} students to fallback file: {}", students.size(), fallbackFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Error writing to fallback file: {}", fallbackFile.getAbsolutePath(), e);
        }
    }

    public void saveStudent(Student student) {
        List<Student> students = readAllStudents();
        // Remove if exists (update logic)
        students.removeIf(s -> s.getId() != null && s.getId().equals(student.getId()));
        students.add(student);
        writeAllStudents(students);
    }
}