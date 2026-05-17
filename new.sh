ollama run llama3
#MATCH p=(n)-[r]->(m) RETURN p LIMIT 500;
#MATCH (st:Student) ... MATCH (s:ESCOSkill) ... MERGE (st)-[:HAS_SKILL]->(s)
#MATCH (j:JobApplication) ... MATCH (s:ESCOSkill) ... MERGE (j)-[:REQUIRES_SKILL]->(s)
