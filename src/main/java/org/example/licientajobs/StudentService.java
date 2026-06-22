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
import org.springframework.security.crypto.password.PasswordEncoder; // Added import

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
    private final PasswordEncoder passwordEncoder; // Added field

    public StudentService(StudentRepository studentRepository, UserRepository userRepository, JsonFallbackService jsonFallbackService, StorageService storageService, SimpMessagingTemplate messagingTemplate, OllamaService ollamaService, PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.jsonFallbackService = jsonFallbackService;
        this.storageService = storageService;
        this.messagingTemplate = messagingTemplate;
        this.ollamaService = ollamaService;
        this.passwordEncoder = passwordEncoder; // Initialized field
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

    public void deleteJobById(Long jobId) {
        FallbackData data = jsonFallbackService.readFallbackData();
        if (data.getAvailableJobs() != null) {
            boolean removed = data.getAvailableJobs().removeIf(job -> job.getId().equals(jobId));
            if (removed) {
                jsonFallbackService.writeFallbackData(data);
            }
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
                        
                        // Adaugă logica pentru decrementarea locurilor dacă statusul este "ANGAJAT"
                        if ("ANGAJAT".equalsIgnoreCase(status) && app.getOriginalJobId() != null) {
                            FallbackData data = jsonFallbackService.readFallbackData();
                            if (data.getAvailableJobs() != null) {
                                for (JobApplication job : data.getAvailableJobs()) {
                                    if (job.getId().equals(app.getOriginalJobId())) {
                                        if (job.getAvailablePositions() != null && job.getAvailablePositions() > 0) {
                                            job.setAvailablePositions(job.getAvailablePositions() - 1);
                                            jsonFallbackService.writeFallbackData(data); // Salvează modificarea în JSON
                                            logger.info("Decremented available positions for job ID {}", job.getId());
                                        }
                                        break; // Am găsit jobul, ne oprim
                                    }
                                }
                            }
                        }

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
            newApplication.setOriginalJobId(jobToApply.getId()); // Setăm referința către jobul original
            newApplication.setJobTitle(jobToApply.getJobTitle());
            newApplication.setCompany(jobToApply.getCompany());
            newApplication.setDescription(jobToApply.getDescription());
            newApplication.setWorkSchedule(jobToApply.getWorkSchedule());
            newApplication.setStatus("PENDING");

            student.getJobApplications().add(newApplication);
            saveStudent(student);
        }
    }

    public void toggleApplicationVisibility(Long studentId, Long applicationId, String loggedInUser) {
        findStudentById(studentId).ifPresent(student -> {
            if (loggedInUser.equals(student.getAddedBy()) && student.getJobApplications() != null) {
                student.getJobApplications().stream()
                    .filter(app -> app.getId().equals(applicationId))
                    .findFirst()
                    .ifPresent(app -> {
                        app.setHidden(!app.isHidden());
                        saveStudent(student);
                        String action = app.isHidden() ? "hidden" : "unhidden";
                        notifyClients("Application for " + app.getJobTitle() + " was " + action + ".");
                    });
            }
        });
    }

    public void deleteJobApplication(Long studentId, Long applicationId, String loggedInUser) {
        findStudentById(studentId).ifPresent(student -> {
            if (loggedInUser.equals(student.getAddedBy()) && student.getJobApplications() != null) {
                boolean removed = student.getJobApplications().removeIf(app -> app.getId().equals(applicationId));
                if (removed) {
                    saveStudent(student);
                    notifyClients("Application deleted for student " + student.getName() + ".");
                }
            }
        });
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

    public List<JobApplication> getJobRecommendations(Student student, boolean useLlm, String aiQuery) {
        if (useLlm && aiQuery != null && !aiQuery.trim().isEmpty()) {
            return getLlmJobRecommendations(student, aiQuery);
        }
        
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

    public List<JobApplication> getLlmJobRecommendations(Student student, String aiQuery) {
        List<JobApplication> allJobs = findAllAvailableJobs();
        
        String jobListJson = allJobs.stream()
                .map(j -> String.format("{\"id\":%d,\"title\":\"%s\",\"company\":\"%s\",\"country\":\"%s\",\"skills\":%s}", 
                        j.getId(), 
                        j.getJobTitle().replace("\"", "\\\""), 
                        j.getCompany().replace("\"", "\\\""),
                        j.getCountry() != null ? j.getCountry() : "Unknown",
                        j.getRequiredSkills() != null ? "[\"" + String.join("\",\"", j.getRequiredSkills()) + "\"]" : "[]"))
                .collect(Collectors.joining(","));

        String prompt = "You are an expert IT recruiter. You must filter the provided list of jobs based strictly on the user's query.\n\n" +
                "USER QUERY: \"" + aiQuery + "\"\n\n" +
                "AVAILABLE JOBS (JSON array):\n" +
                "[" + jobListJson + "]\n\n" +
                "INSTRUCTIONS:\n" +
                "1. Filter the jobs to ONLY include those that match the USER QUERY (e.g., location, role, or technology specified in the query).\n" +
                "2. If multiple jobs match the query, you may optionally use the student's profile (Skills: " + (student.getSkills() != null ? student.getSkills() : "None") + ") to rank them, but DO NOT include jobs that violate the USER QUERY.\n" +
                "3. Return ONLY a JSON array of integers representing the matching job IDs. Example: [2813, 2815]. Do NOT return any markdown, text, or explanations.";

        logger.info("Sending LLM Search Prompt: {}", prompt);
        String jsonResponse = ollamaService.generateJsonResponse(prompt);
        logger.info("LLM Search Response: {}", jsonResponse);

        try {
            // Robust extraction: Find the first JSON array in the response using Regex
            Matcher matcher = Pattern.compile("\\[.*?\\]", Pattern.DOTALL).matcher(jsonResponse);
            if (!matcher.find()) {
                throw new RuntimeException("No JSON array found in Ollama response");
            }
            String arrayJson = matcher.group();
            
            ObjectMapper mapper = new ObjectMapper();
            List<Long> recommendedIds = mapper.readValue(arrayJson, new TypeReference<List<Long>>(){});
            
            List<JobApplication> filteredJobs = allJobs.stream()
                    .filter(job -> recommendedIds.contains(job.getId()))
                    .collect(Collectors.toList());
                    
            // Calculate the real match score for the jobs returned by the AI
            String interestsStr = student.getApplicationAnswers().getOrDefault("UserInterests", "");
            List<String> studentInterests = Arrays.stream(interestsStr.split("\\s*,\\s*"))
                                                  .map(String::toLowerCase)
                                                  .filter(s -> !s.isEmpty())
                                                  .collect(Collectors.toList());
            List<String> studentSkills = student.getSkills() != null 
                ? student.getSkills().stream().map(String::toLowerCase).collect(Collectors.toList()) 
                : new ArrayList<>();

            filteredJobs.forEach(job -> {
                double score = calculateWeightedScore(job, studentInterests, studentSkills);
                job.setMatchScore(score);
            });
            
            return filteredJobs.stream()
                .sorted(Comparator.comparingDouble(JobApplication::getMatchScore).reversed())
                .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error parsing LLM job recommendations: {}", jsonResponse, e);
            // Fallback to standard recommendations if LLM parsing fails
            return getJobRecommendations(student, false, null);
        }
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
        double rawScore = 0.0;

        // 1. Interest-based score (10% weight)
        String jobTitle = job.getJobTitle() != null ? job.getJobTitle().toLowerCase() : "";
        String jobDescription = job.getDescription() != null ? job.getDescription().toLowerCase() : "";
        Set<String> jobTextTokens = new HashSet<>(Arrays.asList((jobTitle + " " + jobDescription).split("\\s+")));
        Set<String> interestSet = new HashSet<>(studentInterests);

        double interestJaccard = calculateJaccardSimilarity(interestSet, jobTextTokens);
        rawScore += interestJaccard * 10.0;

        // 2. Skill-based score (90% weight)
        List<String> requiredJobSkills = job.getRequiredSkills() != null
            ? job.getRequiredSkills().stream().map(String::toLowerCase).distinct().collect(Collectors.toList())
            : new ArrayList<>();

        if (!requiredJobSkills.isEmpty()) {
            long matchedSkills = requiredJobSkills.stream()
                                              .filter(studentSkills::contains)
                                              .count();

            double skillMatchPercentage = (double) matchedSkills / requiredJobSkills.size();
            rawScore += skillMatchPercentage * 90.0;
        }

        double finalScore;
        if (rawScore > 0) {
            // Apply the remapping only if there's an actual raw score
            finalScore = 20.0 + (rawScore * 0.8);
            // Ensure score does not exceed 100
            if (finalScore > 100.0) {
                finalScore = 100.0;
            }
        } else {
            finalScore = 0.0; // If rawScore is 0, finalScore is 0
        }

        return finalScore;
    }

    public void registerUser(String username, String password, String fullName, String email) {
        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(password); // Store unencrypted password temporarily for fallback logic

        // Handle fallback first, ensuring unencrypted password is saved there
        List<User> fallbackUsers = jsonFallbackService.readUsersFallbackData();
        // Remove existing user if updating, or ensure ID is unique for new user
        fallbackUsers.removeIf(u -> u.getUsername().equals(username)); // Assuming username is unique for fallback
        user.setId(System.currentTimeMillis()); // Assign ID for fallback if not already set
        fallbackUsers.add(user);
        jsonFallbackService.writeUsersFallbackData(fallbackUsers);
        logger.info("User {} saved to JSON fallback with unencrypted password.", username);

        // Now, encrypt password for Memgraph and save
        user.setPassword(passwordEncoder.encode(password)); // Encrypt password for Memgraph
        try {
            userRepository.save(user); // Save to Memgraph with encrypted password
            logger.info("User {} saved to Memgraph with encrypted password.", username);
        } catch (DataAccessException e) {
            logger.warn("Memgraph connection failed. User {} saved to JSON fallback only.", username, e);
            // If Memgraph fails, the user is already saved to JSON fallback with unencrypted password.
            // No further action needed here for fallback, as it was already handled.
        }
    }

    public boolean authenticateUser(String username, String password) {
        try {
            List<User> users = userRepository.findByUsername(username);
            if(!users.isEmpty()) {
                User user = users.get(0);
                // Try authenticating with encrypted password
                if (passwordEncoder.matches(password, user.getPassword())) {
                    return true;
                } else {
                    // If encrypted password doesn't match, check if it's an old unencrypted password
                    // A BCrypt hash always starts with "$2a$", "$2b$", "$2y$"
                    if (!user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$") && !user.getPassword().startsWith("$2y$")) {
                        if (password.equals(user.getPassword())) {
                            // Old unencrypted password matched, re-encrypt and save
                            user.setPassword(passwordEncoder.encode(password));
                            userRepository.save(user); // Save updated user with encrypted password
                            logger.info("User {} password re-encrypted and saved to Memgraph.", username);
                            return true;
                        }
                    }
                }
            }
        } catch (DataAccessException e) {
            logger.warn("Memgraph connection failed. Authenticating from JSON fallback.", e);
        }
        
        // Fallback authentication (against unencrypted password in JSON)
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
                    // Adăugăm skill-ul doar dacă nu există deja (case-insensitive)
                    if (student.getSkills().stream().noneMatch(s -> s.equalsIgnoreCase(skill))) {
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