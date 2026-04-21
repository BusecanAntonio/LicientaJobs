package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    private final UserRepository userRepository;
    private final JsonFallbackService jsonFallbackService;
    private final StorageService storageService;
    private final SimpMessagingTemplate messagingTemplate;

    public StudentService(StudentRepository studentRepository, UserRepository userRepository, JsonFallbackService jsonFallbackService, StorageService storageService, SimpMessagingTemplate messagingTemplate) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.jsonFallbackService = jsonFallbackService;
        this.storageService = storageService;
        this.messagingTemplate = messagingTemplate;
    }



    private void synchronizeDbToJson() {
        logger.info("Synchronizing all data from Memgraph to JSON file.");
        List<Student> students = studentRepository.findAll();
        jsonFallbackService.writeStudentsFallbackData(students);
    }

    private void notifyClients(String message) {
        logger.info("Notifying clients about the update via WebSocket: {}", message);
        messagingTemplate.convertAndSend("/topic/students", message);
    }

    // =========================================================
    // METODA 1: Cea principală (folosită de noul HomeController)
    // =========================================================
    // =========================================================
    // METODA 1: Cea principală (folosită de noul HomeController)
    // =========================================================
    public Student saveStudent(Student student, String currentUser) {
        try {
            // SETĂM PROPRIETARUL: Asta face ca studentul să apară în lista ta
            student.setAddedBy(currentUser);

            logger.info("Attempting to save student {} to Memgraph.", student.getName());
            Student savedStudent = studentRepository.save(student);
            synchronizeDbToJson();
            notifyClients("Data for " + savedStudent.getName() + " has been updated.");
            return savedStudent;

        } catch (Exception e) { // Exception prinde orice problemă de bază de date
            logger.warn("Memgraph connection failed. Saving only to JSON fallback.", e);
            List<Student> students = jsonFallbackService.readStudentsFallbackData();

            // PROTECȚIE ID: Ștergem din listă doar dacă studentul are deja un ID
            if (student.getId() != null) {
                students.removeIf(s -> s.getId() != null && s.getId().equals(student.getId()));
            }

            students.add(student);
            jsonFallbackService.writeStudentsFallbackData(students);
            notifyClients("Data for " + student.getName() + " has been updated (Offline Mode).");
            return student;
        }
    }

    // =========================================================
    // METODA 2: "Puntea" pentru codul vechi (QuizController, etc)
    // =========================================================
    public Student saveStudent(Student student) {
        // Când codul vechi apelează salvarea cu un singur parametru,
        // noi trimitem datele spre metoda de sus, folosind numele deja existent.
        return saveStudent(student, student.getAddedBy());
    }



    public List<Student> findAllStudents() {
        try {
            return studentRepository.findAll();
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Reading from JSON fallback file.", e);
            List<Student> students = jsonFallbackService.readStudentsFallbackData();
            return students != null ? students : new ArrayList<>();
        }
    }
    
    public List<Student> findAllStudentsByOwner(String username) {
        return findAllStudents().stream()
                .filter(s -> username.equals(s.getAddedBy()))
                .collect(Collectors.toList());
    }

    public Optional<Student> findStudentById(Long id) {
        try {
            return studentRepository.findById(id);
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Reading from JSON fallback file.", e);
            return jsonFallbackService.readStudentsFallbackData().stream()
                .filter(s -> s.getId() != null && s.getId().equals(id))
                .findFirst();
        }
    }
    
    public void deleteStudent(Long id, String loggedInUser) {
        findStudentById(id).ifPresent(student -> {
            if (loggedInUser.equals(student.getAddedBy())) {
                try {
                    logger.info("Attempting to delete student with id {} from Memgraph.", id);
                    studentRepository.deleteById(id);
                    synchronizeDbToJson();
                    notifyClients("Student with ID " + id + " has been deleted.");
                } catch (DataAccessResourceFailureException e) {
                    logger.warn("Memgraph connection failed. Deleting only from JSON fallback.", e);
                    List<Student> students = jsonFallbackService.readStudentsFallbackData();
                    boolean removed = students.removeIf(s -> s.getId() != null && s.getId().equals(id));
                    if (removed) {
                        jsonFallbackService.writeStudentsFallbackData(students);
                        notifyClients("Student with ID " + id + " has been deleted (Offline Mode).");
                    }
                }
            }
        });
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
    
    public void applyForJob(Long studentId, Long jobId, String loggedInUser) {
        Optional<Student> studentOptional = findStudentById(studentId);
        Optional<JobApplication> jobOptional = findJobById(jobId);

        if (studentOptional.isPresent() && jobOptional.isPresent() && loggedInUser.equals(studentOptional.get().getAddedBy())) {
            Student student = studentOptional.get();
            JobApplication jobToApply = jobOptional.get();

            JobApplication newApplication = new JobApplication();
            newApplication.setJobTitle(jobToApply.getJobTitle());
            newApplication.setCompany(jobToApply.getCompany());
            newApplication.setDescription(jobToApply.getDescription());
            newApplication.setWorkSchedule(jobToApply.getWorkSchedule());
            newApplication.setStatus("PENDING");

            student.getJobApplications().add(newApplication);
            saveStudent(student);
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

    public void registerUser(String username, String password, String fullName, String email) {
        User user = new User();
        user.setUsername(username);
        // IN A REAL APP, HASH THE PASSWORD!
        user.setPassword(password);
        user.setFullName(fullName);
        user.setEmail(email);
        try {
            userRepository.save(user);
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Saving user to fallback.", e);
        }
        
        List<User> fallbackUsers = jsonFallbackService.readUsersFallbackData();
        user.setId(System.currentTimeMillis()); // generate fake id for offline
        fallbackUsers.add(user);
        jsonFallbackService.writeUsersFallbackData(fallbackUsers);
    }

    public boolean authenticateUser(String username, String password) {
        try {
            List<User> users = userRepository.findByUsername(username);
            if(!users.isEmpty()) {
                return users.get(0).getPassword().equals(password);
            }
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Authenticating from fallback.", e);
        }
        
        List<User> fallbackUsers = jsonFallbackService.readUsersFallbackData();
        for (User user : fallbackUsers) {
            if (user.getUsername().equals(username) && user.getPassword().equals(password)) {
                return true;
            }
        }
        
        return false;
    }
    
    public void storeFile(Long studentId, MultipartFile file, String loggedInUser) {
        findStudentById(studentId).ifPresent(student -> {
            if (loggedInUser.equals(student.getAddedBy())) {
                String filename = storageService.store(file, studentId);
                student.addUploadedFile(filename);
                saveStudent(student);
            }
        });
    }
}
