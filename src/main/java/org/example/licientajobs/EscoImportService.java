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

    public void importEscoData() {
        logger.info("--- STARTING ESCO & COR DATA IMPORT (This will take several minutes) ---");

        try {
            logger.info("[STEP 1/6] Deleting old ESCO data...");
            neo4jClient.query("MATCH (n:ESCOOccupation) DETACH DELETE n").run();
            neo4jClient.query("MATCH (n:ESCOSkill) DETACH DELETE n").run();
            neo4jClient.query("MATCH (n:COROccupation) DETACH DELETE n").run();
            logger.info("[STEP 1/6] Finished deleting old data.");

            logger.info("[STEP 2/6] Setting up uniqueness constraints...");
            try {
                neo4jClient.query("CREATE CONSTRAINT IF NOT EXISTS FOR (o:ESCOOccupation) REQUIRE o.uri IS UNIQUE").run();
                neo4jClient.query("CREATE CONSTRAINT IF NOT EXISTS FOR (s:ESCOSkill) REQUIRE s.uri IS UNIQUE").run();
                neo4jClient.query("CREATE CONSTRAINT IF NOT EXISTS FOR (c:COROccupation) REQUIRE c.code IS UNIQUE").run();
                logger.info("[STEP 2/6] Constraints are set.");
            } catch (Exception e) {
                logger.warn("[STEP 2/6] Constraints already exist or DB does not support them directly: " + e.getMessage());
            }

            logger.info("[STEP 3/6] Importing ESCO Occupations...");
            importNodes(occupationsFile, "ESCOOccupation", "conceptUri", "preferredLabel");
            logger.info("[STEP 3/6] Finished importing ESCO Occupations.");

            logger.info("[STEP 4/6] Importing ESCO Skills...");
            importNodes(skillsFile, "ESCOSkill", "conceptUri", "preferredLabel");
            logger.info("[STEP 4/6] Finished importing ESCO Skills.");

            logger.info("[STEP 5/6] Creating Occupation -> Skill relations...");
            importRelations(relationsFile);
            logger.info("[STEP 5/6] Finished creating relations.");
            
            logger.info("[STEP 6/6] Importing and mapping COR codes to ESCO...");
            importCorAndMapToEsco(corMappingFile);
            logger.info("[STEP 6/6] Finished importing and mapping COR codes.");

            logger.info("--- ESCO & COR DATA IMPORT FINISHED SUCCESSFULLY! ---");

        } catch (Exception e) {
            logger.error("--- CRITICAL ERROR during ESCO data import ---", e);
        }
    }

    private void importNodes(Resource file, String label, String uriCol, String labelCol) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = headerLine.split(",");
            int uriIdx = -1;
            int labelIdx = -1;

            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].replace("\"", "").trim();
                if (h.equals(uriCol)) uriIdx = i;
                if (h.equals(labelCol)) labelIdx = i;
            }

            if (uriIdx == -1 || labelIdx == -1) {
                logger.error("Could not find required columns '{}' and '{}' in file {}", uriCol, labelCol, file.getFilename());
                return;
            }

            List<Map<String, Object>> batch = new ArrayList<>();
            String line;
            int totalCount = 0;

            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                
                if (values.length > Math.max(uriIdx, labelIdx)) {
                    String uri = values[uriIdx].replace("\"", "").trim();
                    String preferredLabel = values[labelIdx].replace("\"", "").trim();

                    if (!uri.isEmpty()) {
                        batch.add(Map.of("uri", uri, "preferredLabel", preferredLabel));
                    }
                }

                if (batch.size() >= 1000) {
                    logger.debug("Processing batch of {} nodes for label '{}'. Total processed so far: {}", batch.size(), label, totalCount);
                    executeNodeBatch(batch, label);
                    totalCount += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                logger.debug("Processing final batch of {} nodes for label '{}'.", batch.size(), label);
                executeNodeBatch(batch, label);
                totalCount += batch.size();
            }
            logger.info("Total nodes saved for label '{}': {}", label, totalCount);
        }
    }

    private void executeNodeBatch(List<Map<String, Object>> batch, String label) {
        try {
            String cypher = "UNWIND $batch AS row " +
                            "MERGE (n:" + label + " {uri: row.uri}) " +
                            "SET n.preferredLabel = row.preferredLabel";
            neo4jClient.query(cypher)
                    .bind(batch).to("batch")
                    .run();
        } catch (Exception e) {
            logger.error("Failed to execute node batch for label '{}'. Error: {}", label, e.getMessage());
            // Optionally re-throw if you want the whole process to stop
            // throw new RuntimeException(e);
        }
    }

    private void importRelations(Resource file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            String[] headers = headerLine.split(",");
            int occUriIdx = -1, skillUriIdx = -1, relationTypeIdx = -1;

            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].replace("\"", "").trim();
                if (h.equals("occupationUri")) occUriIdx = i;
                if (h.equals("skillUri")) skillUriIdx = i;
                if (h.equals("relationType")) relationTypeIdx = i;
            }

            if (occUriIdx == -1 || skillUriIdx == -1) {
                logger.error("Missing required columns in relations file: {}", file.getFilename());
                return;
            }

            List<Map<String, Object>> essentialBatch = new ArrayList<>();
            List<Map<String, Object>> optionalBatch = new ArrayList<>();
            String line;
            int totalCount = 0;

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
                    logger.debug("Processing batch of {} ESSENTIAL relations.", essentialBatch.size());
                    executeRelationBatch(essentialBatch, "HAS_ESSENTIAL_SKILL");
                    totalCount += essentialBatch.size();
                    essentialBatch.clear();
                }
                if (optionalBatch.size() >= 1000) {
                    logger.debug("Processing batch of {} OPTIONAL relations.", optionalBatch.size());
                    executeRelationBatch(optionalBatch, "HAS_OPTIONAL_SKILL");
                    totalCount += optionalBatch.size();
                    optionalBatch.clear();
                }
            }
            if (!essentialBatch.isEmpty()) {
                logger.debug("Processing final batch of {} ESSENTIAL relations.", essentialBatch.size());
                executeRelationBatch(essentialBatch, "HAS_ESSENTIAL_SKILL");
                totalCount += essentialBatch.size();
            }
            if (!optionalBatch.isEmpty()) {
                logger.debug("Processing final batch of {} OPTIONAL relations.", optionalBatch.size());
                executeRelationBatch(optionalBatch, "HAS_OPTIONAL_SKILL");
                totalCount += optionalBatch.size();
            }
            
            logger.info("Total relations processed: {}", totalCount);
        }
    }

    private void executeRelationBatch(List<Map<String, Object>> batch, String relType) {
        try {
            String cypher = "UNWIND $batch AS row " +
                            "MATCH (o:ESCOOccupation {uri: row.occUri}) " +
                            "MATCH (s:ESCOSkill {uri: row.skillUri}) " +
                            "MERGE (o)-[:" + relType + "]->(s)";
            neo4jClient.query(cypher)
                    .bind(batch).to("batch")
                    .run();
        } catch (Exception e) {
            logger.error("Failed to execute relation batch for type '{}'. Error: {}", relType, e.getMessage());
        }
    }
    
    public void importCorAndMapToEsco(Resource file) {
        // This method seems fine as it is, but let's add a final log for consistency.
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            reader.readLine(); // Skip header
            List<Map<String, Object>> batch = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", 3);
                if (values.length >= 3) {
                    batch.add(Map.of("corCode", values[0].trim(), "romanianName", values[1].trim(), "escoLabel", values[2].trim()));
                }
            }
            
            if (!batch.isEmpty()) {
                String cypher = "UNWIND $batch AS row " +
                                "MERGE (c:COROccupation {code: row.corCode}) SET c.name = row.romanianName " +
                                "WITH c, row " +
                                "OPTIONAL MATCH (e:ESCOOccupation) WHERE toLower(e.preferredLabel) CONTAINS toLower(row.escoLabel) " +
                                "WITH c, e WHERE e IS NOT NULL " +
                                "MERGE (c)-[:EQUIVALENT_TO]->(e)";
                neo4jClient.query(cypher).bind(batch).to("batch").run();
                logger.info("Finished processing {} COR mappings.", batch.size());
            }
        } catch (Exception e) {
             logger.error("Error during COR import and mapping: ", e);
        }
    }
}