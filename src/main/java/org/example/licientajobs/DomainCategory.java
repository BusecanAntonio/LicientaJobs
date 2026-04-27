package org.example.licientajobs;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("DomainCategory")
public class DomainCategory {

    @Id
    private String name; // e.g., "Software Development", "Data & AI"

    @Relationship(type = "HAS_SUBCATEGORY", direction = Relationship.Direction.OUTGOING)
    private Set<Subcategory> subcategories = new HashSet<>();

    public DomainCategory() {}

    public DomainCategory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Subcategory> getSubcategories() {
        return subcategories;
    }

    public void setSubcategories(Set<Subcategory> subcategories) {
        this.subcategories = subcategories;
    }
}
