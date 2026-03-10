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

    private void notifyClients() {
        logger.info("Notifying clients about the update via WebSocket.");
        messagingTemplate.convertAndSend("/topic/students", "update");
    }

    public Student saveStudent(Student student) {
        try {
            logger.info("Attempting to save student {} to Memgraph.", student.getName());
            Student savedStudent = studentRepository.save(student);

            synchronizeDbToJson();
            notifyClients(); // Notify clients after successful save

            return savedStudent;
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Saving only to JSON fallback.", e);
            jsonFallbackService.saveStudent(student);
            notifyClients(); // Also notify clients if fallback is used
            return student;
        }
    }

    public List<Student> findAllStudents() {
        try {
            logger.info("Attempting to find all students from Memgraph.");
            return studentRepository.findAll();
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Reading from JSON fallback file.", e);
            return jsonFallbackService.readAllStudents();
        }
    }

    public Optional<Student> findStudentById(Long id) {
        try {
            logger.info("Attempting to find student with id {} from Memgraph.", id);
            return studentRepository.findById(id);
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Reading from JSON fallback file.", e);
            return jsonFallbackService.readAllStudents().stream()
                .filter(s -> s.getId() != null && s.getId().equals(id))
                .findFirst();
        }
    }
}