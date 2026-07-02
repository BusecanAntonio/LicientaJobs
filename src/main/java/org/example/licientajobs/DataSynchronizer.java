package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DataSynchronizer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSynchronizer.class);

    private final JsonFallbackService jsonFallbackService;
    private final StudentRepository studentRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final EscoImportService escoImportService;
    private final Neo4jClient neo4jClient;

    @Value("classpath:ESCO/cor_esco_mapping.csv")
    private Resource corMappingFile;

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

            // AUTO-IMPORT COR IF MISSING OR IF RELATIONSHIPS ARE MISSING
            Long corRelCount = neo4jClient.query("MATCH (c:COROccupation)-[:EQUIVALENT_TO]->() RETURN count(c) as count")
                    .fetchAs(Long.class).mappedBy((ts, r) -> r.get("count").asLong()).one().orElse(0L);
            if (corRelCount == 0 && occupationsCount > 0) {
                logger.info("Import automat noduri COR și mapare deoarece lipsesc relațiile...");
                // Ștergem nodurile orfane (fără relații) create eventual în rulări anterioare greșite
                neo4jClient.query("MATCH (c:COROccupation) DETACH DELETE c").run();
                importCorAndMapToEsco(corMappingFile);
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
            // Ștergem relațiile vechi de tip REQUIRES_SKILL și HAS_SKILL pentru a nu crea duplicate la modificarea algoritmului
            neo4jClient.query("MATCH ()-[r:REQUIRES_SKILL]->() DELETE r").run();
            neo4jClient.query("MATCH ()-[r:HAS_SKILL]->() DELETE r").run();

            // Folosim o logică mai sigură. Dacă ESCOSkill-ul conține numele skillului sau invers
            logger.info("Regenerăm relațiile de tip REQUIRES_SKILL...");
            neo4jClient.query(
                "MATCH (j:JobApplication) " +
                "WHERE j.requiredSkills IS NOT NULL " +
                "UNWIND j.requiredSkills AS reqSkill " +
                "MATCH (s:ESCOSkill) " +
                "WHERE toLower(s.preferredLabel) CONTAINS toLower(reqSkill) OR toLower(reqSkill) CONTAINS toLower(s.preferredLabel) " +
                "WITH j, s " +
                "MERGE (j)-[:REQUIRES_SKILL]->(s)"
            ).run();

            logger.info("Regenerăm relațiile de tip HAS_SKILL...");
            neo4jClient.query(
                "MATCH (st:Student) " +
                "WHERE st.skills IS NOT NULL " +
                "UNWIND st.skills AS studentSkill " +
                "MATCH (s:ESCOSkill) " +
                "WHERE toLower(s.preferredLabel) CONTAINS toLower(studentSkill) OR toLower(studentSkill) CONTAINS toLower(s.preferredLabel) " +
                "WITH st, s " +
                "MERGE (st)-[:HAS_SKILL]->(s)"
            ).run();

            logger.info("Regenerăm relațiile de tip RELATED_TO_OCCUPATION...");
            neo4jClient.query(
                "MATCH (j:JobApplication) " +
                "MATCH (o:ESCOOccupation) " +
                "WHERE toLower(j.jobTitle) CONTAINS toLower(o.preferredLabel) OR toLower(o.preferredLabel) CONTAINS toLower(j.jobTitle) " +
                "WITH j, o " +
                "MERGE (j)-[:RELATED_TO_OCCUPATION]->(o)"
            ).run();

            neo4jClient.query(
                "MATCH (j:JobApplication) " +
                "MERGE (c:DomainCategory {name: 'IT & Engineering'}) " +
                "MERGE (j)-[:BELONGS_TO_CATEGORY]->(c)"
            ).run();

            Long reqSkillsCount = neo4jClient.query("MATCH ()-[r:REQUIRES_SKILL]->() RETURN count(r) as count")
                    .fetchAs(Long.class).mappedBy((ts, r) -> r.get("count").asLong()).one().orElse(0L);
            logger.info("S-au generat {} relații REQUIRES_SKILL.", reqSkillsCount);

        } catch (Exception e) {
            logger.error("Eroare la generarea relațiilor avansate: ", e);
        }
    }

    private void importCorAndMapToEsco(Resource file) {
        if (!file.exists()) {
            logger.warn("Fișierul COR {} nu a fost găsit.", file.getFilename());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            List<Map<String, Object>> batch = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", 3);
                if (values.length >= 3) {
                    batch.add(Map.of(
                        "corCode", values[0].trim(),
                        "romanianName", values[1].trim(),
                        "escoLabel", values[2].trim()
                    ));
                }
            }

            if (!batch.isEmpty()) {
                String cypher = "UNWIND $batch AS row " +
                                "MERGE (c:COROccupation {code: row.corCode}) " +
                                "SET c.name = row.romanianName " +
                                "WITH c, row " +
                                "OPTIONAL MATCH (e:ESCOOccupation) " +
                                "WHERE toLower(e.preferredLabel) CONTAINS toLower(row.escoLabel) " +
                                "WITH c, e WHERE e IS NOT NULL " +
                                "MERGE (c)-[:EQUIVALENT_TO]->(e)";

                neo4jClient.query(cypher)
                        .bind(batch).to("batch")
                        .run();

                logger.info("Import automat COR și mapare ESCO finalizate cu succes pentru {} înregistrări.", batch.size());
            }

        } catch (Exception e) {
             logger.error("Eroare la auto-importul COR: ", e);
        }
    }
}