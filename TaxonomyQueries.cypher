// ==========================================
// 1. CLEAR EXISTING TAXONOMY (Optional, for fresh start)
// ==========================================
// MATCH (n:DomainCategory)-[r]-() DELETE r, n;
// MATCH (n:Subcategory)-[r]-() DELETE r, n;


// ==========================================
// 2. CREATE DOMAIN CATEGORIES (Nivel 1)
// ==========================================
CREATE (:DomainCategory {name: 'Software Engineering'})
CREATE (:DomainCategory {name: 'AI / Machine Learning'})
CREATE (:DomainCategory {name: 'Data & Analytics'})
CREATE (:DomainCategory {name: 'Cloud / DevOps / Infra'})
CREATE (:DomainCategory {name: 'Cybersecurity'})
CREATE (:DomainCategory {name: 'Embedded / Hardware'})
CREATE (:DomainCategory {name: 'Product / UX / Design'})
CREATE (:DomainCategory {name: 'IT Support / Operations'})
CREATE (:DomainCategory {name: 'Gaming / Media Tech'})
CREATE (:DomainCategory {name: 'Leadership / Consulting'});


// ==========================================
// 3. CREATE SUBCATEGORIES (Nivel 2) & LINK TO DOMAINS
// ==========================================

// --- Software Engineering ---
MATCH (d:DomainCategory {name: 'Software Engineering'})
CREATE (s1:Subcategory {name: 'Frontend Developer'}), (d)-[:HAS_SUBCATEGORY]->(s1)
CREATE (s2:Subcategory {name: 'Backend Developer'}), (d)-[:HAS_SUBCATEGORY]->(s2)
CREATE (s3:Subcategory {name: 'Full Stack Developer'}), (d)-[:HAS_SUBCATEGORY]->(s3)
CREATE (s4:Subcategory {name: 'Mobile Developer'}), (d)-[:HAS_SUBCATEGORY]->(s4)
CREATE (s5:Subcategory {name: 'QA Automation'}), (d)-[:HAS_SUBCATEGORY]->(s5);

// --- AI / Machine Learning ---
MATCH (d:DomainCategory {name: 'AI / Machine Learning'})
CREATE (s1:Subcategory {name: 'ML Engineer'}), (d)-[:HAS_SUBCATEGORY]->(s1)
CREATE (s2:Subcategory {name: 'Data Scientist'}), (d)-[:HAS_SUBCATEGORY]->(s2)
CREATE (s3:Subcategory {name: 'Computer Vision Engineer'}), (d)-[:HAS_SUBCATEGORY]->(s3)
CREATE (s4:Subcategory {name: 'NLP Engineer'}), (d)-[:HAS_SUBCATEGORY]->(s4);

// --- Cloud / DevOps / Infra ---
MATCH (d:DomainCategory {name: 'Cloud / DevOps / Infra'})
CREATE (s1:Subcategory {name: 'DevOps Engineer'}), (d)-[:HAS_SUBCATEGORY]->(s1)
CREATE (s2:Subcategory {name: 'Cloud Engineer'}), (d)-[:HAS_SUBCATEGORY]->(s2)
CREATE (s3:Subcategory {name: 'Site Reliability Engineer'}), (d)-[:HAS_SUBCATEGORY]->(s3);

// --- Cybersecurity ---
MATCH (d:DomainCategory {name: 'Cybersecurity'})
CREATE (s1:Subcategory {name: 'SOC Analyst'}), (d)-[:HAS_SUBCATEGORY]->(s1)
CREATE (s2:Subcategory {name: 'Security Engineer'}), (d)-[:HAS_SUBCATEGORY]->(s2)
CREATE (s3:Subcategory {name: 'Pentester'}), (d)-[:HAS_SUBCATEGORY]->(s3);


// ==========================================
// 4. CREATE MAPPINGS TO ESCO OCCUPATIONS (Nivel 3)
// ==========================================
// Nota: ESCOOccupation-urile exista de obicei deja din importul CSV-urilor ESCO.
// Aceste interogari presupun ca nodurile ESCOOccupation au o proprietate preferredLabel.

// Example: Map Frontend Developer Subcategory to ESCO Occupations
MATCH (sub:Subcategory {name: 'Frontend Developer'})
MATCH (esco:ESCOOccupation)
WHERE esco.preferredLabel IN ['web developer', 'user interface developer', 'front-end developer']
MERGE (sub)-[:MAPS_TO_ESCO]->(esco);

// Example: Map Backend Developer Subcategory to ESCO Occupations
MATCH (sub:Subcategory {name: 'Backend Developer'})
MATCH (esco:ESCOOccupation)
WHERE esco.preferredLabel IN ['software developer', 'back-end developer', 'database developer']
MERGE (sub)-[:MAPS_TO_ESCO]->(esco);

// Example: Map ML Engineer to ESCO
MATCH (sub:Subcategory {name: 'ML Engineer'})
MATCH (esco:ESCOOccupation)
WHERE esco.preferredLabel IN ['machine learning engineer', 'data scientist', 'artificial intelligence engineer']
MERGE (sub)-[:MAPS_TO_ESCO]->(esco);

// Example: Map DevOps to ESCO
MATCH (sub:Subcategory {name: 'DevOps Engineer'})
MATCH (esco:ESCOOccupation)
WHERE esco.preferredLabel IN ['DevOps engineer', 'cloud engineer', 'ICT system administrator']
MERGE (sub)-[:MAPS_TO_ESCO]->(esco);
