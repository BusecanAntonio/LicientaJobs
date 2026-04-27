package org.example.licientajobs;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("ESCOSkill")
public class ESCOSkill {

    @Id
    private String uri;

    private String preferredLabel;

    public ESCOSkill() {}

    public ESCOSkill(String uri, String preferredLabel) {
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
}
