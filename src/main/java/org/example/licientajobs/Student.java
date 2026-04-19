package org.example.licientajobs;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Node
public class Student {

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private String email;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private String major;
    private String phoneNumber;
    private String address;
    private Integer startYear;
    private Integer endYear;
    private String quizResult; // To store the dominant trait
    private Long interestedDomainId; // To store the ID of the chosen job/domain
    private Map<String, String> applicationAnswers = new HashMap<>(); // To store answers for job questions
    
    private String addedBy; // Username of the user who added this student

    private List<String> notifications = new ArrayList<>();

    @Relationship(type = "APPLIED_FOR", direction = Relationship.Direction.OUTGOING)
    private List<JobApplication> jobApplications = new ArrayList<>();

    public Student() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public int getAge() {
        if (dateOfBirth == null) return 0;
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Integer getStartYear() { return startYear; }
    public void setStartYear(Integer startYear) { this.startYear = startYear; }

    public Integer getEndYear() { return endYear; }
    public void setEndYear(Integer endYear) { this.endYear = endYear; }

    public String getQuizResult() { return quizResult; }
    public void setQuizResult(String quizResult) { this.quizResult = quizResult; }
    
    public Long getInterestedDomainId() { return interestedDomainId; }
    public void setInterestedDomainId(Long interestedDomainId) { this.interestedDomainId = interestedDomainId; }

    public Map<String, String> getApplicationAnswers() { return applicationAnswers; }
    public void setApplicationAnswers(Map<String, String> applicationAnswers) { this.applicationAnswers = applicationAnswers; }

    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }

    public List<String> getNotifications() { return notifications; }
    public void setNotifications(List<String> notifications) { this.notifications = notifications; }
    
    public void addNotification(String notification) {
        if (this.notifications == null) {
            this.notifications = new ArrayList<>();
        }
        this.notifications.add(notification);
    }

    public List<JobApplication> getJobApplications() { return jobApplications; }
    public void setJobApplications(List<JobApplication> jobApplications) { this.jobApplications = jobApplications; }
}
