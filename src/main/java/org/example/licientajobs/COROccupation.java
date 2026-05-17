package org.example.licientajobs;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("COROccupation")
public class COROccupation {

    @Id
    private String code;

    private String name;

    @Relationship(type = "EQUIVALENT_TO", direction = Relationship.Direction.OUTGOING)
    private Set<ESCOOccupation> escoEquivalents = new HashSet<>();

    public COROccupation() {}

    public COROccupation(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<ESCOOccupation> getEscoEquivalents() {
        return escoEquivalents;
    }

    public void setEscoEquivalents(Set<ESCOOccupation> escoEquivalents) {
        this.escoEquivalents = escoEquivalents;
    }
}
