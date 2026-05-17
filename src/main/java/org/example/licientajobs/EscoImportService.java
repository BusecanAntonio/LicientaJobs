package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EscoImportService {

    private static final Logger logger = LoggerFactory.getLogger(EscoImportService.class);

    @Value("classpath:ESCO/occupations_en.csv")
    private Resource occupationsFile;

    @Value("classpath:ESCO/skills_en.csv")
    private Resource skillsFile;

    @Value("classpath:ESCO/occupationSkillRelations_en.csv")
    private Resource relationsFile;
    
    @Value("classpath:ESCO/cor_esco_mapping.csv")
    private Resource corMappingFile;

    @Autowired
    private Neo4jClient neo4jClient;

    /**
     * IMPORTUL DATELOR ESCO - Folosim Cypher direcțional pentru eficiență
     * ATENȚIE: Această metodă ar trebui rulată DOAR O DATĂ pentru a evita duplicatele 
     * sau când vrei să reconstruiești graficul ESCO.
     * Am inlocuit @Transactional cu gestionarea explicita a tranzactiilor
     * deoarece neo4jClient poate avea conflicte cu managerul de tranzactii implicite in context web
     */
    public void importEscoData() {
        logger.info("--- Începem importul datelor ESCO și COR (Durează câteva minute) ---");

        try {
            // 1. Curățăm nodurile vechi ESCO (Dacă există)
            logger.info("Pasul 1: Se șterg datele vechi...");
            neo4jClient.query("MATCH (n:ESCOOccupation) DETACH DELETE n").run();
            neo4jClient.query("MATCH (n:ESCOSkill) DETACH DELETE n").run();
            neo4jClient.query("MATCH (n:COROccupation) DETACH DELETE n").run();
            neo4jClient.query("MATCH (n:Subcategory)-[r:MAPS_TO_ESCO]->() DELETE r").run();

            // 2. Creăm Constrângeri de Unicitate (Important pentru viteza de import)
            logger.info("Pasul 2: Setare constrângeri de unicitate...");
            try {
                neo4jClient.query("CREATE CONSTRAINT IF NOT EXISTS FOR (o:ESCOOccupation) REQUIRE o.uri IS UNIQUE").run();
                neo4jClient.query("CREATE CONSTRAINT IF NOT EXISTS FOR (s:ESCOSkill) REQUIRE s.uri IS UNIQUE").run();
                neo4jClient.query("CREATE CONSTRAINT IF NOT EXISTS FOR (c:COROccupation) REQUIRE c.code IS UNIQUE").run();
            } catch (Exception e) {
                logger.warn("Constrângerile există deja sau baza de date nu le suportă direct: " + e.getMessage());
            }

            // 3. Citire și Salvare Ocupații (Occupations)
            logger.info("Pasul 3: Importăm Ocupațiile (Occupations)...");
            importNodes(occupationsFile, "ESCOOccupation", "conceptUri", "preferredLabel");

            // 4. Citire și Salvare Aptitudini (Skills)
            logger.info("Pasul 4: Importăm Aptitudinile (Skills)...");
            importNodes(skillsFile, "ESCOSkill", "conceptUri", "preferredLabel");

            // 5. Citire și Creare Relații (Occupation -> Skill)
            logger.info("Pasul 5: Creăm Relațiile Ocupație -> Skill...");
            importRelations(relationsFile);
            
            // 6. Import COR și maparea la ESCO
            logger.info("Pasul 6: Importăm și mapăm codurile COR la ESCO...");
            importCorAndMapToEsco(corMappingFile);

            logger.info("--- IMPORTUL ESCO & COR S-A TERMINAT CU SUCCES! ---");

        } catch (Exception e) {
            logger.error("Eroare critică în timpul importului: ", e);
        }
    }

    /**
     * Metodă utilitară pentru a citi un CSV și a face BATCH INSERT cu Cypher
     */
    private void importNodes(Resource file, String label, String uriCol, String labelCol) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = headerLine.split(",");
            int uriIdx = -1;
            int labelIdx = -1;

            // Găsim indexul coloanelor
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].replace("\"", "").trim();
                if (h.equals(uriCol)) uriIdx = i;
                if (h.equals(labelCol)) labelIdx = i;
            }

            if (uriIdx == -1 || labelIdx == -1) {
                logger.error("Nu s-au găsit coloanele {} și {} în fișierul {}", uriCol, labelCol, file.getFilename());
                return;
            }

            List<Map<String, Object>> batch = new ArrayList<>();
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                // Parsare simplă de CSV (atenție la virgulele din interiorul ghilimelelor)
                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                
                if (values.length > Math.max(uriIdx, labelIdx)) {
                    String uri = values[uriIdx].replace("\"", "").trim();
                    String preferredLabel = values[labelIdx].replace("\"", "").trim();

                    if (!uri.isEmpty()) {
                        batch.add(Map.of("uri", uri, "preferredLabel", preferredLabel));
                    }
                }

                if (batch.size() >= 1000) {
                    executeNodeBatch(batch, label);
                    count += batch.size();
                    batch.clear();
                    logger.info("S-au procesat {} noduri de tip {}", count, label);
                }
            }
            if (!batch.isEmpty()) {
                executeNodeBatch(batch, label);
                count += batch.size();
            }
            logger.info("Total salvate: {} noduri {}", count, label);
        }
    }

    private void executeNodeBatch(List<Map<String, Object>> batch, String label) {
        String cypher = "UNWIND $batch AS row " +
                        "MERGE (n:" + label + " {uri: row.uri}) " +
                        "SET n.preferredLabel = row.preferredLabel";
        neo4jClient.query(cypher)
                .bind(batch).to("batch")
                .run();
    }

    /**
     * Importă Relațiile (Occupation -> Skill) din occupationSkillRelations_en.csv
     */
    private void importRelations(Resource file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = headerLine.split(",");
            int occUriIdx = -1;
            int skillUriIdx = -1;
            int relationTypeIdx = -1;

            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].replace("\"", "").trim();
                if (h.equals("occupationUri")) occUriIdx = i;
                if (h.equals("skillUri")) skillUriIdx = i;
                if (h.equals("relationType")) relationTypeIdx = i;
            }

            if (occUriIdx == -1 || skillUriIdx == -1) {
                logger.error("Lipsesc coloane necesare în occupationSkillRelations_en.csv");
                return;
            }

            List<Map<String, Object>> essentialBatch = new ArrayList<>();
            List<Map<String, Object>> optionalBatch = new ArrayList<>();
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                
                if (values.length > Math.max(occUriIdx, skillUriIdx)) {
                    String occUri = values[occUriIdx].replace("\"", "").trim();
                    String skillUri = values[skillUriIdx].replace("\"", "").trim();
                    String relationType = (relationTypeIdx != -1 && values.length > relationTypeIdx) 
                                            ? values[relationTypeIdx].replace("\"", "").trim() 
                                            : "essential";

                    if (!occUri.isEmpty() && !skillUri.isEmpty()) {
                        if (relationType.contains("essential")) {
                            essentialBatch.add(Map.of("occUri", occUri, "skillUri", skillUri));
                        } else {
                            optionalBatch.add(Map.of("occUri", occUri, "skillUri", skillUri));
                        }
                    }
                }

                if (essentialBatch.size() >= 1000) {
                    executeRelationBatch(essentialBatch, "HAS_ESSENTIAL_SKILL");
                    count += essentialBatch.size();
                    essentialBatch.clear();
                }
                if (optionalBatch.size() >= 1000) {
                    executeRelationBatch(optionalBatch, "HAS_OPTIONAL_SKILL");
                    count += optionalBatch.size();
                    optionalBatch.clear();
                }
            }
            if (!essentialBatch.isEmpty()) executeRelationBatch(essentialBatch, "HAS_ESSENTIAL_SKILL");
            if (!optionalBatch.isEmpty()) executeRelationBatch(optionalBatch, "HAS_OPTIONAL_SKILL");
            
            logger.info("Import relații ESCO finalizat.");
        }
    }

    private void executeRelationBatch(List<Map<String, Object>> batch, String relType) {
        String cypher = "UNWIND $batch AS row " +
                        "MATCH (o:ESCOOccupation {uri: row.occUri}) " +
                        "MATCH (s:ESCOSkill {uri: row.skillUri}) " +
                        "MERGE (o)-[:" + relType + "]->(s)";
        neo4jClient.query(cypher)
                .bind(batch).to("batch")
                .run();
    }
    
    public void importCorAndMapToEsco(Resource file) {
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
                // OPTIONAL MATCH prevents the query from failing if ESCO equivalent is not found
                // So at least the COR node is created
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