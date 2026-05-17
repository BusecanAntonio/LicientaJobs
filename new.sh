ollama run llama3
#MATCH p=(n)-[r]->(m) RETURN p LIMIT 500;
#MATCH p=(s:Student {name: "Andrei Busecan"})-[*1..2]-() RETURN p LIMIT 100
#MATCH p=(j:JobApplication)-[:REQUIRES_SKILL]->(s:ESCOSkill) RETURN p LIMIT 50

#MATCH (u:Student)-[app:APPLIED_FOR]->(j:JobApplication) MATCH (j)-[req:REQUIRES_SKILL]->(jobSkill:ESCOSkill) OPTIONAL MATCH (u)-[has:HAS_SKILL]->(jobSkill) RETURN u, app, j, req, jobSkill, has LIMIT 100
