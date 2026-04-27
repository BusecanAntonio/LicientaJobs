package org.example.licientajobs;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("ESCOOccupation")
public class ESCOOccupation {

    @Id
    private String uri;

    private String preferredLabel;
    
    @Relationship(type = "HAS_ESSENTIAL_SKILL", direction = Relationship.Direction.OUTGOING)
    private Set<ESCOSkill> essentialSkills = new HashSet<>();

    public ESCOOccupation() {}

    public ESCOOccupation(String uri, String preferredLabel) {
        this.uri = uri;
        this.preferredLabel = preferredLabel;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getPreferredLabel() {
        return preferredLabel;
    }

    public void setPreferredLabel(String preferredLabel) {
        this.preferredLabel = preferredLabel;
    }

    public Set<ESCOSkill> getEssentialSkills() {
        return essentialSkills;
    }

    public void setEssentialSkills(Set<ESCOSkill> essentialSkills) {
        this.essentialSkills = essentialSkills;
    }
}
