package org.example.licientajobs;

import java.util.List;

public class FallbackData {
    private List<Student> students;
    private List<JobApplication> availableJobs;

    public List<Student> getStudents() {
        return students;
    }

    public void setStudents(List<Student> students) {
        this.students = students;
    }

    public List<JobApplication> getAvailableJobs() {
        return availableJobs;
    }

    public void setAvailableJobs(List<JobApplication> availableJobs) {
        this.availableJobs = availableJobs;
    }
}