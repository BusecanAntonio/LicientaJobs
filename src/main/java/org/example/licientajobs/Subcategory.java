package org.example.licientajobs;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("Subcategory")
public class Subcategory {

    @Id
    private String name; // e.g., "Backend Development", "ML Engineer"

    @Relationship(type = "MAPS_TO_ESCO", direction = Relationship.Direction.OUTGOING)
    private Set<ESCOOccupation> escoOccupations = new HashSet<>();

    public Subcategory() {}

    public Subcategory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<ESCOOccupation> getEscoOccupations() {
        return escoOccupations;
    }

    public void setEscoOccupations(Set<ESCOOccupation> escoOccupations) {
        this.escoOccupations = escoOccupations;
    }
}
