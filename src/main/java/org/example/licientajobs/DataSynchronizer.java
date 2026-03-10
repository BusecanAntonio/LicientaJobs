package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSynchronizer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSynchronizer.class);

    private final JsonFallbackService jsonFallbackService;
    private final StudentRepository studentRepository;

    public DataSynchronizer(JsonFallbackService jsonFallbackService, StudentRepository studentRepository) {
        this.jsonFallbackService = jsonFallbackService;
        this.studentRepository = studentRepository;
    }

    @Override
    public void run(String... args) {
        List<Student> fallbackStudents = jsonFallbackService.readAllStudents();
        if (fallbackStudents.isEmpty()) {
            logger.info("No fallback data to synchronize.");
            return;
        }

        logger.info("Found {} students in fallback file. Attempting to synchronize with Memgraph.", fallbackStudents.size());

        try {
            // Check if Memgraph is accessible
            studentRepository.count();
            logger.info("Memgraph is accessible. Synchronizing data...");

            for (Student student : fallbackStudents) {
                // A simple save will update existing or create new ones
                studentRepository.save(student);
            }

            // Clear the fallback file after successful synchronization
            jsonFallbackService.writeAllStudents(List.of());
            logger.info("Synchronization successful. Fallback file has been cleared.");

        } catch (Exception e) {
            logger.error("Could not synchronize fallback data with Memgraph. It might still be down.", e);
        }
    }
}