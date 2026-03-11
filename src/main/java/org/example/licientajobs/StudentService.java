package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
        List<Student> allStudentsFromDb = studentRepository.findAll();
        jsonFallbackService.writeAllStudents(allStudentsFromDb);
    }

    private void notifyClients(String message) {
        logger.info("Notifying clients about the update via WebSocket: {}", message);
        // We send a simple object or string. Here, sending a notification message.
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
            jsonFallbackService.saveStudent(student);
            notifyClients("Data for " + student.getName() + " has been updated (Offline Mode).");
            return student;
        }
    }

    public List<Student> findAllStudents() {
        try {
            return studentRepository.findAll();
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Reading from JSON fallback file.", e);
            return jsonFallbackService.readAllStudents();
        }
    }

    public Optional<Student> findStudentById(Long id) {
        try {
            return studentRepository.findById(id);
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Reading from JSON fallback file.", e);
            return jsonFallbackService.readAllStudents().stream()
                .filter(s -> s.getId() != null && s.getId().equals(id))
                .findFirst();
        }
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
                        // We save the student, which cascades the update to the job application
                        saveStudent(student); 
                        notifyClients("Job application status for " + student.getName() + " changed to " + status);
                    });
            }
        }
    }
}