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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        // Ideal ar fi să verifici și dacă userul e "admin"
        
        try {
            // Executăm importul (Durează un pic)
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

        // Mapăm interesele în applicationAnswers
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith("applicationAnswers[")) {
                String questionText = entry.getKey().replace("applicationAnswers[", "").replace("]", "");
                student.getApplicationAnswers().put(questionText, entry.getValue());
            }
        }
        
        // În caz că nu era bifat niciun interest, punem un string gol pentru a nu crăpa la afișare
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
    public String editStudent(@PathVariable Long id, @ModelAttribute Student updatedStudent, HttpSession session) {
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
                existingStudent.setPreferredSeniority(updatedStudent.getPreferredSeniority());
                existingStudent.setPrefersRemote(updatedStudent.isPrefersRemote());
                
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
                    // 1. Salvarea fizică pe disc prin StorageService
                    String fileName = storageService.store(file, id);

                    // 2. Salvarea în lista studentului
                    if (student.getDocuments() == null) {
                        student.setDocuments(new ArrayList<>());
                    }
                    student.getDocuments().add(fileName);

                    // 3. Apelăm LLM-ul pentru a extrage skill-uri dacă este scrisoare de recomandare sau CV
                    studentService.extractAndSaveSkills(student, file);

                    // 4. Salvăm studentul în baza de date
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
                // LLM-ul nostru va incerca sa aduca README.md-ul folosind API-ul public Github si sa dea rezumatul
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
                student.getGithubProjects().remove(githubLink);
                studentService.saveStudent(student, loggedInUser);
            }
        });

        return "redirect:/students";
    }
    
    @GetMapping("/students/{id}/documents/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable Long id, @PathVariable String filename) {
        try {
            // Folosim StorageService pentru a găsi calea corectă a fișierului salvat
            Path filePath = storageService.load(id, filename);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                // Determinăm tipul fișierului (pentru ca browserul să știe dacă e PDF, imagine, etc.)
                String contentType = Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        // "inline" îi spune browserului să DESCHIDĂ fișierul (dacă e PDF/imagine),
                        // nu să îl descarce automat
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
                // 1. Ștergerea fizică de pe disk
                Path filePath = storageService.load(id, filename);
                Files.deleteIfExists(filePath);

                // 2. Ștergerea numelui din lista studentului
                if (student.getDocuments() != null) {
                    student.getDocuments().remove(filename);
                }

                // 3. Salvarea modificării în baza de date
                studentService.saveStudent(student, loggedInUser);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return "redirect:/students";
    }


    // --- Job Applications ---
    @GetMapping("/students/{id}/apply")
    public String showJobApplicationForm(@PathVariable Long id, HttpSession session, Model model) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        Optional<Student> student = studentService.findStudentById(id);
        if (student.isPresent() && loggedInUser.equals(student.get().getAddedBy())) {
            model.addAttribute("student", student.get());
            
            // Folosim noua logica de matching!
            List<JobApplication> recommendedJobs = studentService.getJobRecommendations(student.get());
            model.addAttribute("availableJobs", recommendedJobs);

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
        return "redirect:/students";
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
