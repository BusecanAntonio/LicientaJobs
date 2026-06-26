package org.example.licientajobs;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.core.io.UrlResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final StudentService studentService;
    private final StorageService storageService;
    private final EscoImportService escoImportService;

    public HomeController(StudentService studentService, StorageService storageService, EscoImportService escoImportService) {
        this.studentService = studentService;
        this.storageService = storageService;
        this.escoImportService = escoImportService;
    }

    @GetMapping("/change-password")
    public String showChangePasswordForm(HttpSession session) {
        if (session.getAttribute("loggedInUser") == null) {
            return "redirect:/login";
        }
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String oldPassword, @RequestParam String newPassword, HttpSession session, RedirectAttributes redirectAttributes) {
        String username = (String) session.getAttribute("loggedInUser");
        if (username == null) {
            return "redirect:/login";
        }
        boolean success = studentService.changePassword(username, oldPassword, newPassword);
        if (success) {
            redirectAttributes.addFlashAttribute("success", "Password changed successfully.");
            return "redirect:/students";
        } else {
            redirectAttributes.addFlashAttribute("error", "Incorrect old password.");
            return "redirect:/change-password";
        }
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        if (session.getAttribute("loggedInUser") != null) {
            return "redirect:/students";
        }
        return "redirect:/login";
    }

    // --- Registration ---
    @GetMapping("/register")
    public String showRegisterForm(HttpSession session) {
        if (session.getAttribute("loggedInUser") != null) {
            return "redirect:/students";
        }
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username, @RequestParam String password,
                               @RequestParam String fullName, @RequestParam String email, HttpSession session) {
        studentService.registerUser(username, password, fullName, email);
        session.setAttribute("loggedInUser", username);
        return "redirect:/students/add";
    }

    // --- Login / Logout ---
    @GetMapping("/login")
    public String showLoginForm(HttpSession session) {
        if (session.getAttribute("loggedInUser") != null) {
            return "redirect:/students";
        }
        return "login";
    }

    @PostMapping("/login")
    public String loginUser(@RequestParam String username, @RequestParam String password,
                            HttpSession session, Model model) {
        if (studentService.authenticateUser(username, password)) {
            session.setAttribute("loggedInUser", username);
            return "redirect:/students";
        } else {
            model.addAttribute("error", true);
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // --- Admin Endpoints ---
    @GetMapping("/admin/import-esco")
    @ResponseBody
    public String triggerEscoImport(HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "Neautorizat. Loghează-te mai întâi.";
        }
        
        try {
            escoImportService.importEscoData();
            return "Importul ESCO a fost pornit/finalizat cu succes! Verifică consola (log-urile) pentru detalii.";
        } catch (Exception e) {
            return "A apărut o eroare la import: " + e.getMessage();
        }
    }

    // --- Student Management ---
    @GetMapping("/students")
    public String listStudents(HttpSession session, Model model) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        List<Student> userStudents = studentService.findAllStudentsByOwner(loggedInUser);
        model.addAttribute("students", userStudents);
        model.addAttribute("username", loggedInUser);
        return "list-students";
    }

    @GetMapping("/students/add")
    public String showAddStudentForm(HttpSession session, Model model) {
        if (session.getAttribute("loggedInUser") == null) {
            return "redirect:/login";
        }
        model.addAttribute("student", new Student());
        model.addAttribute("availableJobs", studentService.findAllAvailableJobs());
        return "add-student";
    }

    @PostMapping("/students/add")
    public String addStudent(@ModelAttribute Student student, @RequestParam Map<String, String> allParams, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        
        student.setAddedBy(loggedInUser);

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith("applicationAnswers[")) {
                String questionText = entry.getKey().replace("applicationAnswers[", "").replace("]", "");
                student.getApplicationAnswers().put(questionText, entry.getValue());
            }
        }
        
        if (!student.getApplicationAnswers().containsKey("UserInterests")) {
            student.getApplicationAnswers().put("UserInterests", "");
        }

        studentService.saveStudent(student);
        return "redirect:/students";
    }

    @PostMapping("/students/delete/{id}")
    public String deleteStudent(@PathVariable Long id, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        studentService.deleteStudent(id, loggedInUser);
        return "redirect:/students";
    }

    @PostMapping("/students/edit/{id}")
    public String editStudent(@PathVariable Long id, @ModelAttribute Student updatedStudent, @RequestParam Map<String, String> allParams, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        studentService.findStudentById(id).ifPresent(existingStudent -> {
            if (loggedInUser.equals(existingStudent.getAddedBy())) {
                existingStudent.setName(updatedStudent.getName());
                existingStudent.setEmail(updatedStudent.getEmail());
                existingStudent.setPhoneNumber(updatedStudent.getPhoneNumber());
                existingStudent.setMajor(updatedStudent.getMajor());
                existingStudent.setAddress(updatedStudent.getAddress());
                existingStudent.setStartYear(updatedStudent.getStartYear());
                existingStudent.setEndYear(updatedStudent.getEndYear());
                existingStudent.setDateOfBirth(updatedStudent.getDateOfBirth());
                
                // Actualizare campuri noi preferinte
                existingStudent.setPreferredSeniority(updatedStudent.getPreferredSeniority());
                existingStudent.setPrefersRemote(updatedStudent.isPrefersRemote());
                existingStudent.setPreferredLocations(updatedStudent.getPreferredLocations());
                
                // Actualizare interese (taxonomie)
                String userInterests = allParams.entrySet().stream()
                        .filter(e -> e.getKey().equals("applicationAnswers[UserInterests]"))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse("");
                        
                existingStudent.getApplicationAnswers().put("UserInterests", userInterests);
                
                studentService.saveStudent(existingStudent, loggedInUser);
            }
        });

        return "redirect:/students";
    }

    @PostMapping("/students/edit-skills/{id}")
    public String editStudentSkills(@PathVariable Long id, @RequestParam("skills") String skillsStr, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        studentService.findStudentById(id).ifPresent(existingStudent -> {
            if (loggedInUser.equals(existingStudent.getAddedBy())) {
                if (skillsStr != null && !skillsStr.trim().isEmpty()) {
                    List<String> skillsList = Arrays.stream(skillsStr.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    existingStudent.setSkills(skillsList);
                } else {
                    existingStudent.setSkills(new ArrayList<>());
                }
                studentService.saveStudent(existingStudent, loggedInUser);
            }
        });

        return "redirect:/students";
    }

    @PostMapping("/students/{id}/upload")
    public String handleFileUpload(@PathVariable Long id,
                                   @RequestParam("type") String type,
                                   @RequestParam("file") MultipartFile file,
                                   HttpSession session) {

        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) return "redirect:/login";

        studentService.findStudentById(id).ifPresent(student -> {
            if (!file.isEmpty()) {
                try {
                    // Read the file content once
                    byte[] fileBytes = file.getBytes();
                    String originalFilename = file.getOriginalFilename();

                    // 1. Store the file on disk
                    String fileName = storageService.store(fileBytes, id, originalFilename);
                    
                    // 2. Add document reference to student
                    if (student.getDocuments() == null) {
                        student.setDocuments(new ArrayList<>());
                    }
                    student.getDocuments().add(fileName);
                    
                    // 3. Extract skills from the content
                    studentService.extractAndSaveSkills(student, file);
                    
                    // 4. Save the updated student
                    studentService.saveStudent(student, loggedInUser);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        return "redirect:/students";
    }
    
    @PostMapping("/students/{id}/github/add")
    public String addGithubProject(@PathVariable Long id, @RequestParam("githubLink") String githubLink, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) return "redirect:/login";

        studentService.findStudentById(id).ifPresent(student -> {
            if (loggedInUser.equals(student.getAddedBy())) {
                studentService.processGithubLink(student, githubLink);
                studentService.saveStudent(student, loggedInUser);
            }
        });

        return "redirect:/students";
    }

    @PostMapping("/students/{id}/github/delete")
    public String deleteGithubProject(@PathVariable Long id, @RequestParam("githubLink") String githubLink, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) return "redirect:/login";

        studentService.findStudentById(id).ifPresent(student -> {
            if (loggedInUser.equals(student.getAddedBy()) && student.getGithubProjects() != null) {
                // 1. Facem o copie a proiectelor curente
                Map<String, String> remainingProjects = new HashMap<>(student.getGithubProjects());
                
                // 2. Ștergem proiectul din copie
                remainingProjects.remove(githubLink);
                remainingProjects.keySet().removeIf(key -> key.trim().equalsIgnoreCase(githubLink.trim()));
                
                // 3. TRICK PENTRU SPRING DATA NEO4J: 
                // Goliți complet mapa originală și salvați pentru a forța ștergerea tuturor proprietăților @CompositeProperty din DB
                student.setGithubProjects(new HashMap<>());
                studentService.saveStudent(student, loggedInUser);
                
                // 4. Punem la loc doar proiectele rămase (dacă mai există) și salvăm din nou
                if (!remainingProjects.isEmpty()) {
                    student.setGithubProjects(remainingProjects);
                    studentService.saveStudent(student, loggedInUser);
                }
            }
        });

        return "redirect:/students";
    }
    
    @GetMapping("/students/{id}/documents/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable Long id, @PathVariable String filename) {
        try {
            Path filePath = storageService.load(id, filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/students/{id}/documents/delete")
    public String deleteFile(@PathVariable Long id, @RequestParam("filename") String filename, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) return "redirect:/login";

        studentService.findStudentById(id).ifPresent(student -> {
            try {
                Path filePath = storageService.load(id, filename);
                Files.deleteIfExists(filePath);

                if (student.getDocuments() != null) {
                    List<String> updatedDocs = new ArrayList<>(student.getDocuments());
                    updatedDocs.remove(filename);
                    student.setDocuments(updatedDocs);
                }
                studentService.saveStudent(student, loggedInUser);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return "redirect:/students";
    }

    // --- Job Applications ---
    @GetMapping("/students/{id}/apply")
    public String showJobApplicationForm(@PathVariable Long id, 
                                         @RequestParam(required = false) String searchQuery,
                                         HttpSession session, Model model) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        Optional<Student> studentOpt = studentService.findStudentById(id);
        if (studentOpt.isPresent() && loggedInUser.equals(studentOpt.get().getAddedBy())) {
            Student student = studentOpt.get();
            model.addAttribute("student", student);
            
            Set<String> studentSkills = student.getSkills() != null
                ? student.getSkills().stream().map(String::toLowerCase).collect(Collectors.toSet())
                : new HashSet<>();
            model.addAttribute("studentSkills", studentSkills);

            List<JobApplication> recommendedJobs;
            boolean useLlm = false;
            if (searchQuery != null && searchQuery.toLowerCase().startsWith("/ollama ")) {
                String prompt = searchQuery.substring(8).trim();
                if (!prompt.isEmpty()) {
                    recommendedJobs = studentService.getLlmJobRecommendations(student, prompt);
                    useLlm = true;
                } else {
                    recommendedJobs = studentService.getJobRecommendations(student, false, null);
                }
                model.addAttribute("aiQuery", searchQuery);
            } else {
                recommendedJobs = studentService.getJobRecommendations(student, false, searchQuery);
                model.addAttribute("searchQuery", searchQuery);
            }
            
            model.addAttribute("availableJobs", recommendedJobs);
            model.addAttribute("useLlm", useLlm);

            return "apply-job";
        }
        return "redirect:/students";
    }

    @PostMapping("/students/{id}/apply")
    public String submitJobApplication(@PathVariable Long id, @RequestParam("id") Long jobId, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        studentService.applyForJob(id, jobId, loggedInUser);
        return "redirect:/students/" + id + "/apply";
    }

    @PostMapping("/jobs/delete/{jobId}")
    public String deleteJob(@PathVariable Long jobId, @RequestParam Long studentId, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        studentService.deleteJobById(jobId);
        return "redirect:/students/" + studentId + "/apply";
    }

    @PostMapping("/students/{studentId}/applications/{applicationId}/status")
    public String updateApplicationStatus(@PathVariable Long studentId, @PathVariable Long applicationId,
                                          @RequestParam String status, HttpSession session) {
        if (session.getAttribute("loggedInUser") == null) {
            return "redirect:/login";
        }
        studentService.updateJobApplicationStatus(studentId, applicationId, status);
        return "redirect:/students";
    }

    @PostMapping("/students/{studentId}/applications/{applicationId}/toggle-visibility")
    public String toggleApplicationVisibility(@PathVariable Long studentId, @PathVariable Long applicationId, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        studentService.toggleApplicationVisibility(studentId, applicationId, loggedInUser);
        return "redirect:/students";
    }

    @PostMapping("/students/{studentId}/applications/{applicationId}/delete")
    public String deleteJobApplication(@PathVariable Long studentId, @PathVariable Long applicationId, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        studentService.deleteJobApplication(studentId, applicationId, loggedInUser);
        return "redirect:/students";
    }

    @PostMapping("/students/{studentId}/simulate-interview")
    @ResponseBody
    public ResponseEntity<Map<String, String>> simulateInterview(
            @PathVariable Long studentId,
            @RequestParam Long jobId,
            @RequestParam String answer,
            HttpSession session) {
        
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Neautorizat"));
        }
        
        Optional<JobApplication> jobOpt = studentService.findJobById(jobId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Job not found"));
        }
        
        Map<String, String> evaluation = studentService.evaluateInterviewAnswer(jobOpt.get(), answer);
        return ResponseEntity.ok(evaluation);
    }

    // --- Quiz ---
    @GetMapping("/quiz/{id}")
    public String showQuiz(@PathVariable Long id, HttpSession session, Model model) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        Optional<Student> student = studentService.findStudentById(id);
        if (student.isPresent() && loggedInUser.equals(student.get().getAddedBy())) {
            model.addAttribute("studentId", id);
            return "quiz";
        }
        return "redirect:/students";
    }

    @PostMapping("/quiz/{id}/submit")
    public String submitQuiz(@PathVariable Long id, @RequestParam Map<String, String> answers, HttpSession session, Model model) {
        if (session.getAttribute("loggedInUser") == null) {
            return "redirect:/login";
        }
        String quizResult = String.join(",", answers.values());
        studentService.updateQuizResult(id, quizResult);

        JobApplication recommendedJob = studentService.findRecommendedJob(quizResult);
        model.addAttribute("studentId", id);
        model.addAttribute("recommendedJob", recommendedJob);
        model.addAttribute("quizResult", quizResult);
        return "quiz-result";
    }
}