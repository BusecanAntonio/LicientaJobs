package org.example.licientajobs;

import org.springframework.data.annotation.Transient;
import org.springframework.data.neo4j.core.schema.CompositeProperty;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private String status = "PENDING";
    private boolean interviu;

    // New fields for recommendation logic
    private String seniority; // e.g., "Internship", "Junior", "Mid", "Senior"
    private boolean isRemote;
    private List<String> requiredSkills = new ArrayList<>();
    private String location; // e.g., "Germany", "Romania", "Netherlands"
    
    // New fields for geography
    private String country;
    private Double latitude;
    private Double longitude;

    @Transient
    private double matchScore;

    @CompositeProperty
    private Map<String, String> workSchedule = new HashMap<>(); // Use Map instead of custom class

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate applicationDate;

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
    
    public boolean isInterviu() { return interviu; }
    public void setInterviu(boolean interviu) { this.interviu = interviu; }

    public Map<String, String> getWorkSchedule() { return workSchedule; }
    public void setWorkSchedule(Map<String, String> workSchedule) { this.workSchedule = workSchedule; }

    public LocalDate getApplicationDate() { return applicationDate; }
    public void setApplicationDate(LocalDate applicationDate) { this.applicationDate = applicationDate; }

    // Getters and setters for new fields
    public String getSeniority() { return seniority; }
    public void setSeniority(String seniority) { this.seniority = seniority; }

    public boolean isRemote() { return isRemote; }
    public void setRemote(boolean remote) { isRemote = remote; }

    public List<String> getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }
}