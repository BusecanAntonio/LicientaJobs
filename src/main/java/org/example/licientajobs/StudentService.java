package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StudentService {

    private static final Logger logger = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository studentRepository;
    private final JsonFallbackService jsonFallbackService;
    private final SimpMessagingTemplate messagingTemplate;

    public StudentService(StudentRepository studentRepository, JsonFallbackService jsonFallbackService, SimpMessagingTemplate messagingTemplate) {
        this.studentRepository = studentRepository;
        this.jsonFallbackService = jsonFallbackService;
        this.messagingTemplate = messagingTemplate;
    }

    private void synchronizeDbToJson() {
        logger.info("Synchronizing all data from Memgraph to JSON file.");
        FallbackData data = jsonFallbackService.readFallbackData();
        data.setStudents(studentRepository.findAll());
        jsonFallbackService.writeFallbackData(data);
    }

    private void notifyClients(String message) {
        logger.info("Notifying clients about the update via WebSocket: {}", message);
        messagingTemplate.convertAndSend("/topic/students", message);
    }

    public Student saveStudent(Student student) {
        try {
            logger.info("Attempting to save student {} to Memgraph.", student.getName());
            Student savedStudent = studentRepository.save(student);
            synchronizeDbToJson();
            notifyClients("Data for " + savedStudent.getName() + " has been updated.");
            return savedStudent;
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Saving only to JSON fallback.", e);
            FallbackData data = jsonFallbackService.readFallbackData();
            data.getStudents().removeIf(s -> s.getId().equals(student.getId()));
            data.getStudents().add(student);
            jsonFallbackService.writeFallbackData(data);
            notifyClients("Data for " + student.getName() + " has been updated (Offline Mode).");
            return student;
        }
    }

    public List<Student> findAllStudents() {
        try {
            return studentRepository.findAll();
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Reading from JSON fallback file.", e);
            FallbackData data = jsonFallbackService.readFallbackData();
            return data.getStudents() != null ? data.getStudents() : new ArrayList<>();
        }
    }

    public Optional<Student> findStudentById(Long id) {
        try {
            return studentRepository.findById(id);
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Reading from JSON fallback file.", e);
            return jsonFallbackService.readFallbackData().getStudents().stream()
                .filter(s -> s.getId() != null && s.getId().equals(id))
                .findFirst();
        }
    }
    
    public Optional<JobApplication> findJobById(Long jobId) {
        return findAllAvailableJobs().stream()
                .filter(j -> j.getId() != null && j.getId().equals(jobId))
                .findFirst();
    }

    public List<JobApplication> findAllAvailableJobs() {
        FallbackData data = jsonFallbackService.readFallbackData();
        return data.getAvailableJobs() != null ? data.getAvailableJobs() : new ArrayList<>();
    }

    public void updateJobApplicationStatus(Long studentId, Long applicationId, String status) {
        Optional<Student> studentOpt = findStudentById(studentId);
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            if (student.getJobApplications() != null) {
                student.getJobApplications().stream()
                    .filter(app -> app.getId() != null && app.getId().equals(applicationId))
                    .findFirst()
                    .ifPresent(app -> {
                        app.setStatus(status);
                        
                        // Add notification
                        String notificationMsg = "Your application for " + app.getJobTitle() + " at " + app.getCompany() + " was " + status.toLowerCase() + ".";
                        student.addNotification(notificationMsg);
                        
                        saveStudent(student);
                        notifyClients("Job application status for " + student.getName() + " changed to " + status);
                    });
            }
        }
    }

    public void updateQuizResult(Long studentId, String quizResult) {
        Optional<Student> studentOpt = findStudentById(studentId);
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            student.setQuizResult(quizResult);
            saveStudent(student);
            notifyClients("Quiz result for " + student.getName() + " has been updated.");
        }
    }

    public JobApplication findRecommendedJob(String quizResult) {
        String[] answers = quizResult.split(",");
        String domain = answers[0];
        String schedule = answers[1];

        List<JobApplication> allJobs = findAllAvailableJobs();

        return allJobs.stream()
                .max(Comparator.comparingInt(job -> calculateMatchScore(job, domain, schedule)))
                .orElse(null);
    }

    private int calculateMatchScore(JobApplication job, String preferredDomain, String preferredSchedule) {
        int score = 0;
        String jobTitle = job.getJobTitle() != null ? job.getJobTitle().toLowerCase() : "";
        String jobDescription = job.getDescription() != null ? job.getDescription().toLowerCase() : "";

        // Domain matching
        if (jobTitle.contains(preferredDomain.toLowerCase()) || jobDescription.contains(preferredDomain.toLowerCase())) {
            score += 10;
        } else if (preferredDomain.equalsIgnoreCase("IT") && (jobTitle.contains("developer") || jobTitle.contains("engineer"))) {
            score += 5;
        } else if (preferredDomain.equalsIgnoreCase("Constructii") && (jobTitle.contains("constructor") || jobTitle.contains("arhitect"))) {
            score += 5;
        } else if (preferredDomain.equalsIgnoreCase("Electrica") && (jobTitle.contains("electrician") || jobTitle.contains("automatist"))) {
            score += 5;
        } else if (preferredDomain.equalsIgnoreCase("Gaming") && (jobTitle.contains("game") || jobTitle.contains("artist"))) {
            score += 5;
        }

        // Schedule matching (with null check)
        Map<String, String> scheduleMap = job.getWorkSchedule();
        if (scheduleMap != null && scheduleMap.get("shift") != null) {
            String workSchedule = scheduleMap.get("shift").toLowerCase();
            if (workSchedule.contains(preferredSchedule.toLowerCase())) {
                score += 5;
            }
        }

        return score;
    }
}
