package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StudentService {

    private static final Logger logger = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final JsonFallbackService jsonFallbackService;
    private final StorageService storageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final OllamaService ollamaService;

    public StudentService(StudentRepository studentRepository, UserRepository userRepository, JsonFallbackService jsonFallbackService, StorageService storageService, SimpMessagingTemplate messagingTemplate, OllamaService ollamaService) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.jsonFallbackService = jsonFallbackService;
        this.storageService = storageService;
        this.messagingTemplate = messagingTemplate;
        this.ollamaService = ollamaService;
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

    public List<JobApplication> getJobRecommendations(Student student) {
        List<JobApplication> allJobs = findAllAvailableJobs();

        String interestsStr = student.getApplicationAnswers().getOrDefault("UserInterests", "");
        List<String> studentInterests = Arrays.asList(interestsStr.split("\\s*,\\s*"));

        List<String> studentSkills = student.getSkills() != null 
            ? student.getSkills().stream().map(String::toLowerCase).collect(Collectors.toList()) 
            : new ArrayList<>();

        String preferredSeniority = student.getPreferredSeniority() != null ? student.getPreferredSeniority().toLowerCase() : "";
        boolean prefersRemote = student.isPrefersRemote();
        List<String> preferredLocations = student.getPreferredLocations() != null ? student.getPreferredLocations() : new ArrayList<>();

        return allJobs.stream()
                .sorted((job1, job2) -> {
                    int score1 = calculateAdvancedMatchScore(job1, studentInterests, studentSkills, preferredSeniority, prefersRemote, preferredLocations);
                    int score2 = calculateAdvancedMatchScore(job2, studentInterests, studentSkills, preferredSeniority, prefersRemote, preferredLocations);
                    return Integer.compare(score2, score1); 
                })
                .collect(Collectors.toList());
    }

    public JobApplication findRecommendedJob(String quizResult) {
        List<JobApplication> allJobs = findAllAvailableJobs();
        if(allJobs.isEmpty()) return null;
        
        return allJobs.get(0); 
    }

    private int calculateAdvancedMatchScore(JobApplication job, List<String> studentInterests, List<String> studentSkills, String preferredSeniority, boolean prefersRemote, List<String> preferredLocations) {
        int score = 0;
        
        String jobTitle = job.getJobTitle() != null ? job.getJobTitle().toLowerCase() : "";
        String jobDescription = job.getDescription() != null ? job.getDescription().toLowerCase() : "";

        int categoryPoints = 0;
        for (String interest : studentInterests) {
            if (interest.isEmpty()) continue;
            String lowerInterest = interest.toLowerCase();
            if (jobTitle.contains(lowerInterest) || jobDescription.contains(lowerInterest)) {
                categoryPoints += 20;
            }
        }
        score += Math.min(categoryPoints, 40);

        int skillPoints = 0;
        List<String> jobSkills = job.getRequiredSkills() != null ? job.getRequiredSkills() : new ArrayList<>();
        if (!jobSkills.isEmpty() && !studentSkills.isEmpty()) {
            for (String requiredSkill : jobSkills) {
                if (studentSkills.contains(requiredSkill.toLowerCase())) {
                    skillPoints += (30 / jobSkills.size());
                }
            }
        }
        score += Math.min(skillPoints, 30);

        String jobSeniority = job.getSeniority() != null ? job.getSeniority().toLowerCase() : "";
        if (!preferredSeniority.isEmpty() && !jobSeniority.isEmpty()) {
            if (preferredSeniority.equals(jobSeniority)) {
                score += 20;
            } else if (
                (preferredSeniority.equals("junior") && jobSeniority.equals("internship")) ||
                (preferredSeniority.equals("internship") && jobSeniority.equals("junior")) ||
                (preferredSeniority.equals("mid") && jobSeniority.equals("junior")) ||
                (preferredSeniority.equals("senior") && jobSeniority.equals("mid"))
            ) {
                 score += 10;
            }
        } else if (preferredSeniority.isEmpty() && jobSeniority.isEmpty()){
            score += 10;
        }

        int locationScore = 0;
        if (prefersRemote && job.isRemote()) {
            locationScore += 10;
        } else if (!prefersRemote && !job.isRemote()) {
            String jobLocation = job.getLocation() != null ? job.getLocation() : "";
            if (!preferredLocations.isEmpty() && !jobLocation.isEmpty() && preferredLocations.contains(jobLocation)) {
                locationScore += 10;
            }
        }
        score += Math.min(locationScore, 10);

        return score;
    }

    public void registerUser(String username, String password, String fullName, String email) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setFullName(fullName);
        user.setEmail(email);
        try {
            userRepository.save(user);
        } catch (DataAccessResourceFailureException e) {
            logger.warn("Memgraph connection failed. Saving user to fallback.", e);
        }
        
        List<User> fallbackUsers = jsonFallbackService.readUsersFallbackData();
        user.setId(System.currentTimeMillis()); 
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

    /**
     * Fetch Github Repo Data + Contributors and ask LLM to summarize
     */
    public void processGithubLink(Student student, String githubLink) {
        try {
            if (student.getGithubProjects() == null) {
                student.setGithubProjects(new HashMap<>());
            }

            String regex = "github\\.com/([^/]+)/([^/]+)";
            Matcher matcher = Pattern.compile(regex).matcher(githubLink);
            
            String readmeContent = "";
            String extraMetadata = "";
            String repoName = githubLink;
            
            if (matcher.find()) {
                String owner = matcher.group(1);
                String repo = matcher.group(2).replace(".git", "");
                repoName = owner + "/" + repo;
                
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.set("User-Agent", "Spring-Boot-App");
                HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
                
                try {
                    // 1. Fetch README
                    String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/readme";
                    ResponseEntity<Map> response = restTemplate.exchange(apiUrl, HttpMethod.GET, entity, Map.class);
                    if (response.getBody() != null && response.getBody().containsKey("content")) {
                        String base64Content = ((String) response.getBody().get("content")).replaceAll("\\n", "");
                        byte[] decodedBytes = Base64.getDecoder().decode(base64Content);
                        readmeContent = new String(decodedBytes, StandardCharsets.UTF_8);
                        if (readmeContent.length() > 3000) {
                            readmeContent = readmeContent.substring(0, 3000);
                        }
                    }
                } catch (Exception e) { logger.warn("No README for {}", repoName); }
                
                try {
                    // 2. Fetch Languages (Technologies)
                    String langUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/languages";
                    ResponseEntity<Map> langResponse = restTemplate.exchange(langUrl, HttpMethod.GET, entity, Map.class);
                    if (langResponse.getBody() != null && !langResponse.getBody().isEmpty()) {
                        extraMetadata += "- Technologies used: " + String.join(", ", langResponse.getBody().keySet()) + "\n";
                    }
                } catch (Exception e) { logger.warn("No languages for {}", repoName); }
                
                try {
                    // 3. Fetch Contributors
                    String contribUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/contributors";
                    ResponseEntity<List> contribResponse = restTemplate.exchange(contribUrl, HttpMethod.GET, entity, List.class);
                    if (contribResponse.getBody() != null && !contribResponse.getBody().isEmpty()) {
                        List<Map<String, Object>> contributors = contribResponse.getBody();
                        int totalCommits = 0;
                        for (Map<String, Object> c : contributors) {
                            totalCommits += (Integer) c.get("contributions");
                        }
                        if (totalCommits > 0) {
                            Map<String, Object> topContributor = contributors.get(0);
                            String topName = (String) topContributor.get("login");
                            int topCommits = (Integer) topContributor.get("contributions");
                            int percentage = (int) Math.round((topCommits * 100.0) / totalCommits);
                            extraMetadata += "- Top Contributor: " + topName + " with " + percentage + "% of the commits.\n";
                        }
                    }
                } catch (Exception e) { logger.warn("No contributors for {}", repoName); }
            }

            String prompt;
            if (!readmeContent.isEmpty() || !extraMetadata.isEmpty()) {
                prompt = "Please write a comprehensive 3-4 sentence summary about the following GitHub project: " + repoName + ".\n\n" +
                         "Use the following metadata and README content to construct the summary.\n" +
                         "You MUST explicitly mention:\n" +
                         "1. What the project does (based on the README).\n" +
                         "2. The technologies used (from metadata).\n" +
                         "3. Who the top contributor is and their percentage of work (from metadata).\n\n" +
                         "METADATA:\n" + extraMetadata + "\n\n" +
                         "README CONTENT (truncated):\n" + readmeContent;
            } else {
                prompt = "I have a GitHub repository link: " + githubLink + ".\n" +
                         "Can you guess or briefly summarize what a project with this name might do?";
            }

            logger.info("Trimitere prompt Github avansat către Ollama pentru {}", githubLink);
            
            // Folosim metoda normală de text, deoarece rezumatul este text descriptiv, nu JSON
            String llmSummary = ollamaService.generateResponse(prompt);
            
            String cleanSummary = llmSummary.trim();
            if (cleanSummary.isEmpty()) {
                cleanSummary = "Proiect adăugat. Nu s-a putut genera automat o descriere.";
            }

            student.getGithubProjects().put(githubLink, cleanSummary);
            logger.info("Proiect Github procesat: {}", cleanSummary);

        } catch (Exception e) {
            logger.error("Eroare la procesarea linkului de GitHub.", e);
            student.getGithubProjects().put(githubLink, "Eroare la generarea rezumatului avansat.");
        }
    }

    public void extractAndSaveSkills(Student student, MultipartFile file) {
        try {
            String fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            String contentToSend = fileContent.length() > 3000 ? fileContent.substring(0, 3000) : fileContent;

            String prompt = "Extract the skills from the following recommendation letter or CV.\n\n" +
                    "Return ONLY a JSON array of strings containing the skills using these rules:\n" +
                    "- Use standard skill names (e.g., \"JavaScript\", \"Teamwork\", \"Excel\")\n" +
                    "- Do not invent new skills\n" +
                    "- Avoid duplicates\n" +
                    "- DO NOT return any text outside of the JSON array (no markdown code blocks, just the raw json array).\n\n" +
                    "Text:\n\"\"\"\n" + contentToSend + "\n\"\"\"";

            // Folosim noua metodă ce forțează formatul JSON!
            String jsonResponse = ollamaService.generateJsonResponse(prompt);

            ObjectMapper mapper = new ObjectMapper();
            String cleanJson = jsonResponse.trim();
            Matcher arrayMatcher = Pattern.compile("\\[.*?\\]", Pattern.DOTALL).matcher(cleanJson);
            Matcher objectMatcher = Pattern.compile("\\{.*?\\}", Pattern.DOTALL).matcher(cleanJson);

            if (arrayMatcher.find()) {
                cleanJson = arrayMatcher.group(0);
            } else if (objectMatcher.find()) {
                cleanJson = objectMatcher.group(0);
            }
            
            try {
                List<String> extractedSkills = new ArrayList<>();
                if (cleanJson.startsWith("{")) {
                    Map<String, Object> skillsMap = mapper.readValue(cleanJson, new TypeReference<Map<String, Object>>() {});
                    extractedSkills.addAll(skillsMap.keySet());
                } else if (cleanJson.startsWith("[")) {
                    extractedSkills = mapper.readValue(cleanJson, new TypeReference<List<String>>() {});
                } else {
                     throw new RuntimeException("Format invalid");
                }
                
                if (student.getSkills() == null) {
                    student.setSkills(new ArrayList<>());
                }
                
                for (String skill : extractedSkills) {
                    if (!student.getSkills().contains(skill)) {
                        student.getSkills().add(skill);
                    }
                }
            } catch (Exception parseEx) {
                logger.error("Nu s-a putut parsa: " + cleanJson, parseEx);
            }
        } catch (Exception e) {
            logger.error("Eroare la procesarea fișierului", e);
        }
    }
}
