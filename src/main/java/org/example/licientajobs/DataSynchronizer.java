package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional; // Keep Optional for other potential uses, though not directly used for job existence check anymore

@Component
public class DataSynchronizer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSynchronizer.class);

    private final JsonFallbackService jsonFallbackService;
    private final StudentRepository studentRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final EscoImportService escoImportService;

    public DataSynchronizer(JsonFallbackService jsonFallbackService, StudentRepository studentRepository, 
                            JobApplicationRepository jobApplicationRepository, EscoImportService escoImportService) {
        this.jsonFallbackService = jsonFallbackService;
        this.studentRepository = studentRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.escoImportService = escoImportService;
    }

    @Override
    public void run(String... args) {
        try {
            logger.info("Synchronizing initial data from fallback file...");
            FallbackData fallbackData = jsonFallbackService.readFallbackData();
            
            // Poți decomenta linia de mai jos dacă vrei ca la pornire să randeze importul ESCO
            // Atenție: Importul ESCO pe fișiere mari durează și suprascrie datele!
            // escoImportService.importEscoData();

            // Read from new files
            List<Student> fallbackStudents = jsonFallbackService.readStudentsFallbackData();
            List<JobApplication> fallbackJobs = fallbackData.getAvailableJobs();

            if ((fallbackStudents == null || fallbackStudents.isEmpty()) && (fallbackJobs == null || fallbackJobs.isEmpty())) {
                logger.warn("Fallback data file is empty or contains no data. Nothing to synchronize.");
                return;
            }

            if (fallbackStudents != null && !fallbackStudents.isEmpty()) {
                logger.info("Checking {} students from fallback file...", fallbackStudents.size());
                int studentsAdded = 0;
                for (Student student : fallbackStudents) {
                    // Căutăm studentul după nume sau email (presupunem că ai o metodă findByName sau findByEmail)
                    // Dacă nu ai, ar trebui adăugată în StudentRepository. Pentru moment, bazat pe codul existent,
                    // vom presupune că dacă baza de date are deja studenți, am putea avea duplicate.
                    // O metodă robustă ar necesita o verificare unică per student.
                    // Pentru simplitate și siguranță, dacă nu există nicio logică unică,
                    // vom lăsa studenții doar dacă baza de date e goală, similar cu logica anterioară.
                }
                
                 if (studentRepository.count() == 0) {
                     logger.info("Student DB is empty. Saving {} students from fallback file...", fallbackStudents.size());
                     for (Student student : fallbackStudents) {
                         student.setId(null);
                         studentRepository.save(student);
                     }
                 } else {
                     logger.info("Students already exist in the database. Skipping student sync to prevent duplicates.");
                 }
            }

            if (fallbackJobs != null && !fallbackJobs.isEmpty()) {
                logger.info("Checking {} jobs from fallback file...", fallbackJobs.size());
                int jobsAdded = 0;
                for (JobApplication job : fallbackJobs) {
                    // Use existsByJobTitleAndCompany for checking existence
                    if (!jobApplicationRepository.existsByJobTitleAndCompany(job.getJobTitle(), job.getCompany())) {
                        job.setId(null);
                        jobApplicationRepository.save(job);
                        jobsAdded++;
                        logger.info("Added new job: {} at {}", job.getJobTitle(), job.getCompany());
                    } else {
                         // Optional: logger.debug("Job already exists: {} at {}", job.getJobTitle(), job.getCompany());
                    }
                }
                logger.info("Added {} new jobs to Memgraph.", jobsAdded);
            }
            logger.info("Data synchronization process finished.");

        } catch (Exception e) {
            logger.error("A critical error occurred during data synchronization with Memgraph.", e);
        }
    }
}
