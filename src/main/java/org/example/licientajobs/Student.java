package org.example.licientajobs;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.List;

@Node
public class Student {

    @Id
    @GeneratedValue // Neo4j generează automat un ID numeric
    private Long studentId; // schimbăm din String în Long

    private String name;
    private String email;
    private int age;
    private String major;
    private String phoneNumber;
    private String address;
    private List<Integer> grades;
    private String group;
    private int year;
    private String notes;

    public Student() {}

    public Student(String name, String email, int age, String major, String phoneNumber,
                   String address, List<Integer> grades, String group, int year, String notes) {
        this.name = name;
        this.email = email;
        this.age = age;
        this.major = major;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.grades = grades;
        this.group = group;
        this.year = year;
        this.notes = notes;
    }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public List<Integer> getGrades() { return grades; }
    public void setGrades(List<Integer> grades) { this.grades = grades; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
