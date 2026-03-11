package org.example.licientajobs;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node
public class JobApplication {

    @Id
    @GeneratedValue
    private Long id;

    private String jobTitle;
    private String company;
    private String description;
    private String licentaGrade;
    private String courseGrades;
    
    // Status: PENDING, ACCEPTED, REJECTED
    private String status = "PENDING";

    public JobApplication() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLicentaGrade() { return licentaGrade; }
    public void setLicentaGrade(String licentaGrade) { this.licentaGrade = licentaGrade; }

    public String getCourseGrades() { return courseGrades; }
    public void setCourseGrades(String courseGrades) { this.courseGrades = courseGrades; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}