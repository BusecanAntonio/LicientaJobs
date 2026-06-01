package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        } catch (DataAccessException e) {
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
        } catch (DataAccessException e) {
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
                } catch (DataAccessException e) {
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
        List<String> studentInterests = Arrays.stream(interestsStr.split("\\s*,\\s*"))
                                              .map(String::toLowerCase)
                                              .filter(s -> !s.isEmpty())
                                              .collect(Collectors.toList());

        List<String> studentSkills = student.getSkills() != null 
            ? student.getSkills().stream().map(String::toLowerCase).collect(Collectors.toList()) 
            : new ArrayList<>();

        allJobs.forEach(job -> {
            double score = calculateWeightedScore(job, studentInterests, studentSkills);
            job.setMatchScore(score);
        });

        return allJobs.stream()
                .sorted(Comparator.comparingDouble(JobApplication::getMatchScore).reversed())
                .collect(Collectors.toList());
    }

    public JobApplication findRecommendedJob(String quizResult) {
        List<JobApplication> allJobs = findAllAvailableJobs();
        if(allJobs.isEmpty()) return null;
        
        return allJobs.get(0); 
    }
    
    private double calculateJaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() || set2.isEmpty()) return 0.0;
        
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        if (union.isEmpty()) return 0.0;

        return (double) intersection.size() / union.size();
    }

    private double calculateWeightedScore(JobApplication job, List<String> studentInterests, List<String> studentSkills) {
        double score = 0.0;
        
        // 1. Interest-based score (10% weight)
        String jobTitle = job.getJobTitle() != null ? job.getJobTitle().toLowerCase() : "";
        String jobDescription = job.getDescription() != null ? job.getDescription().toLowerCase() : "";
        Set<String> jobTextTokens = new HashSet<>(Arrays.asList((jobTitle + " " + jobDescription).split("\\s+")));
        Set<String> interestSet = new HashSet<>(studentInterests);
        
        double interestJaccard = calculateJaccardSimilarity(interestSet, jobTextTokens);
        score += interestJaccard * 10.0;

        // 2. Skill-based score (90% weight) - CORRECTED LOGIC
        List<String> requiredJobSkills = job.getRequiredSkills() != null 
            ? job.getRequiredSkills().stream().map(String::toLowerCase).collect(Collectors.toList()) 
            : new ArrayList<>();
        
        if (!requiredJobSkills.isEmpty()) {
            long matchedSkills = studentSkills.stream()
                                              .filter(s -> requiredJobSkills.contains(s))
                                              .count();
            
            double skillMatchPercentage = (double) matchedSkills / requiredJobSkills.size();
            score += skillMatchPercentage * 90.0;
        }

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
        } catch (DataAccessException e) {
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
        } catch (DataAccessException e) {
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

            String prompt = "Extract the skills from the following text.\n" +
                    "Return ONLY a JSON array of strings containing the skills. Example: [\"Java\", \"Spring\", \"Teamwork\"]\n" +
                    "Do NOT return any other text, markdown blocks, or explanations.\n" +
                    "Text:\n\"\"\"\n" + contentToSend + "\n\"\"\"";

            logger.info("Sending prompt to Ollama for skill extraction:\n{}", prompt);
            String jsonResponse = ollamaService.generateJsonResponse(prompt);
            logger.info("Ollama raw response for skill extraction:\n{}", jsonResponse);

            if (jsonResponse == null || jsonResponse.trim().isEmpty() || jsonResponse.trim().equals("{}")) {
                logger.warn("Ollama returned an empty or invalid response.");
                return;
            }

            List<String> extractedSkills = new ArrayList<>();
            Matcher matcher = Pattern.compile("\"([^\"]+)\"").matcher(jsonResponse);
            while (matcher.find()) {
                String potentialSkill = matcher.group(1).trim();
                // Exclude common JSON keys that the LLM might hallucinate
                if (!potentialSkill.isEmpty() && !potentialSkill.equalsIgnoreCase("skills") && !potentialSkill.equalsIgnoreCase("skill")) {
                    extractedSkills.add(potentialSkill);
                }
            }

            logger.info("Extracted skills: {}", extractedSkills);

            if (!extractedSkills.isEmpty()) {
                if (student.getSkills() == null) {
                    student.setSkills(new ArrayList<>());
                }

                for (String skill : extractedSkills) {
                    if (!student.getSkills().contains(skill)) {
                        student.getSkills().add(skill);
                    }
                }
            } else {
                 logger.warn("No skills could be parsed from the JSON response.");
            }

        } catch (Exception e) {
            logger.error("Error extracting skills from file", e);
        }
    }
    
    public Map<String, String> evaluateInterviewAnswer(JobApplication job, String answer) {
        String prompt = "You are an expert technical IT recruiter in Romania evaluating a candidate for the position of '" + job.getJobTitle() + "'.\n" +
                "The candidate provided the following answer to a general technical and behavioral interview question:\n\"" + answer + "\"\n\n" +
                "Evaluate the answer based on clarity, technical relevance, and problem-solving skills.\n" +
                "Return ONLY a JSON object with exactly two keys:\n" +
                "1. \"score\": an integer from 1 to 100 representing how good the answer is.\n" +
                "2. \"feedback\": a short paragraph (2-3 sentences) of constructive feedback in Romanian.\n" +
                "Do NOT return any other text or formatting.";
        
        String jsonResponse = ollamaService.generateJsonResponse(prompt);
        ObjectMapper mapper = new ObjectMapper();
        
        try {
            // Trim to handle potential markdown wrappers
            String cleanJson = jsonResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            
            JsonNode rootNode = mapper.readTree(cleanJson);
            String score = rootNode.has("score") ? rootNode.get("score").asText() : "N/A";
            String feedback = rootNode.has("feedback") ? rootNode.get("feedback").asText() : "Nu s-a putut genera feedback.";
            
            Map<String, String> result = new HashMap<>();
            result.put("score", score);
            result.put("feedback", feedback);
            return result;
        } catch(Exception e) {
            logger.error("Error parsing interview evaluation JSON: " + jsonResponse, e);
            Map<String, String> result = new HashMap<>();
            result.put("score", "Eroare");
            result.put("feedback", "Eroare la procesarea răspunsului generat de AI. Te rog încearcă din nou.");
            return result;
        }
    }
}