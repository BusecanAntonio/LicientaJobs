package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DataSynchronizer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSynchronizer.class);

    private final JsonFallbackService jsonFallbackService;
    private final StudentRepository studentRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final EscoImportService escoImportService;
    private final Neo4jClient neo4jClient;

    public DataSynchronizer(JsonFallbackService jsonFallbackService, StudentRepository studentRepository, 
                            JobApplicationRepository jobApplicationRepository, EscoImportService escoImportService,
                            Neo4jClient neo4jClient) {
        this.jsonFallbackService = jsonFallbackService;
        this.studentRepository = studentRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.escoImportService = escoImportService;
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(String... args) {
        try {
            logger.info("Synchronizing initial data from fallback file...");
            FallbackData fallbackData = jsonFallbackService.readFallbackData();

            Long occupationsCount = neo4jClient.query("MATCH (o:ESCOOccupation) RETURN count(o) as count")
                    .fetchAs(Long.class).mappedBy((ts, r) -> r.get("count").asLong()).one().orElse(0L);
            
            if (occupationsCount == 0) {
                logger.info("Nu s-au găsit Ocupații ESCO. Se începe importul automat (AȘTEAPTĂ CÂTEVA MINUTE SĂ SE TERMINE!)...");
                escoImportService.importEscoData();
            } else {
                logger.info("S-au găsit {} Ocupații ESCO în baza de date. Trecem peste importul din CSV.", occupationsCount);
            }

            List<Student> fallbackStudents = fallbackData.getStudents();
            if (fallbackStudents == null || fallbackStudents.isEmpty()) {
                fallbackStudents = jsonFallbackService.readStudentsFallbackData();
            }
            
            List<JobApplication> fallbackJobs = fallbackData.getAvailableJobs();

            if ((fallbackStudents == null || fallbackStudents.isEmpty()) && (fallbackJobs == null || fallbackJobs.isEmpty())) {
                logger.warn("Fallback data file is empty or contains no data. Nothing to synchronize.");
                return;
            }

            // 1. IMPORT JOBS FIRST
            if (fallbackJobs != null && !fallbackJobs.isEmpty()) {
                int jobsAdded = 0;
                for (JobApplication job : fallbackJobs) {
                    if (!jobApplicationRepository.existsByJobTitleAndCompany(job.getJobTitle(), job.getCompany())) {
                        job.setId(null);
                        jobApplicationRepository.save(job);
                        jobsAdded++;
                    }
                }
                logger.info("Added {} new jobs to Memgraph.", jobsAdded);
            }

            // 2. FORCE RELATIONS REGARDLESS OF EXISTING STUDENTS
            logger.info("Forcing relations for ALL students from fallback data...");
            if (fallbackStudents != null && !fallbackStudents.isEmpty()) {
                for (Student student : fallbackStudents) {
                    boolean exists = studentRepository.findAll().stream().anyMatch(s -> s.getName() != null && s.getName().equals(student.getName()));
                    if (!exists) {
                        student.setId(null);
                        if (student.getJobApplications() != null && !student.getJobApplications().isEmpty()) {
                            List<JobApplication> realDbJobs = new ArrayList<>();
                            for (JobApplication transientJob : student.getJobApplications()) {
                                List<JobApplication> foundJobs = jobApplicationRepository.findByJobTitleAndCompany(
                                        transientJob.getJobTitle(), transientJob.getCompany());
                                if (!foundJobs.isEmpty()) {
                                    realDbJobs.add(foundJobs.get(0));
                                } else {
                                    transientJob.setId(null);
                                    realDbJobs.add(transientJob);
                                }
                            }
                            student.setJobApplications(realDbJobs);
                        }
                        studentRepository.save(student);
                    } else {
                        if (student.getJobApplications() != null) {
                            for (JobApplication jobApp : student.getJobApplications()) {
                                try {
                                    neo4jClient.query(
                                        "MATCH (s:Student) WHERE s.name = $name " +
                                        "MATCH (j:JobApplication) WHERE j.jobTitle = $jobTitle AND j.company = $company " +
                                        "MERGE (s)-[:APPLIED_FOR]->(j)"
                                    )
                                    .bind(student.getName()).to("name")
                                    .bind(jobApp.getJobTitle()).to("jobTitle")
                                    .bind(jobApp.getCompany()).to("company")
                                    .run();
                                } catch (Exception ignored) { }
                            }
                        }
                    }
                }
            }
            
            // 3. GENERARE RELAȚII ÎNTRE JOBURI ȘI OCUPAȚII / SKILL-URI
            generateAdvancedRelationships();
            
            // 4. PRINTARE RELAȚII DIRECT ÎN CONSOLA INTELLIJ
            logger.info("==================================================");
            logger.info("VERIFICARE MEMGRAPH: IATĂ CE RELAȚII EXISTĂ ÎN BAZA DE DATE:");
            
            neo4jClient.query(
                "MATCH (n)-[r]->(m) " +
                "RETURN coalesce(n.name, n.jobTitle, n.preferredLabel, labels(n)[0], 'Nod') AS source, " +
                "type(r) AS relation, " +
                "coalesce(m.name, m.jobTitle, m.preferredLabel, labels(m)[0], 'Nod') AS target " +
                "LIMIT 50"
            ).fetch().all().forEach(row -> {
                logger.info("({})  ---[{}]--->  ({})", row.get("source"), row.get("relation"), row.get("target"));
            });
            
            Long relCount = neo4jClient.query("MATCH ()-[r]->() RETURN count(r) as count")
                    .fetchAs(Long.class).mappedBy((ts, r) -> r.get("count").asLong()).one().orElse(0L);
            logger.info("...și multe altele! (Total relații în DB: {})", relCount);
            logger.info("==================================================");

        } catch (Exception e) {
            logger.error("A critical error occurred during data synchronization with Memgraph.", e);
        }
    }

    private void generateAdvancedRelationships() {
        try {
            neo4jClient.query(
                "MATCH (j:JobApplication) " +
                "WITH j, COALESCE(j.requiredSkills, []) AS reqSkills " +
                "UNWIND reqSkills AS reqSkill " +
                "MATCH (s:ESCOSkill) " +
                "WHERE toLower(s.preferredLabel) = toLower(reqSkill) " +
                "MERGE (j)-[:REQUIRES_SKILL]->(s)"
            ).run();
            
            neo4jClient.query(
                "MATCH (st:Student) " +
                "WITH st, COALESCE(st.skills, []) AS stSkills " +
                "UNWIND stSkills AS studentSkill " +
                "MATCH (s:ESCOSkill) " +
                "WHERE toLower(s.preferredLabel) = toLower(studentSkill) " +
                "MERGE (st)-[:HAS_SKILL]->(s)"
            ).run();
            
            neo4jClient.query(
                "MATCH (j:JobApplication) " +
                "MATCH (o:ESCOOccupation) " +
                "WHERE toLower(j.jobTitle) CONTAINS toLower(o.preferredLabel) " +
                "WITH j, o LIMIT 100 " + 
                "MERGE (j)-[:RELATED_TO_OCCUPATION]->(o)"
            ).run();

            neo4jClient.query(
                "MATCH (j:JobApplication) " +
                "MERGE (c:DomainCategory {name: 'IT & Engineering'}) " +
                "MERGE (j)-[:BELONGS_TO_CATEGORY]->(c)"
            ).run();

        } catch (Exception e) {
            logger.error("Eroare la generarea relațiilor avansate: ", e);
        }
    }
}
