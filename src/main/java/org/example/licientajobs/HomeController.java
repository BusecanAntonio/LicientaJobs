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
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final StudentService studentService;
    private final StorageService storageService;

    public HomeController(StudentService studentService, StorageService storageService) {
        this.studentService = studentService;
        this.storageService = storageService;
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
                    // 1. Partea pe care o ai deja (Salvarea fizică pe disc)
                    // Aici poți lăsa codul tău care salvează fișierul real în folder...
                    // (Dacă nu o aveai, adaug-o aici)

                    // Numele pe care vrem să-l afișăm (ex: "CV_fisierulmeu.pdf")
                    String fileName = file.getOriginalFilename();

                    // 2. CHEIA PROBLEMEI: Salvarea în lista studentului
                    // Verificăm să nu fie lista null, apoi adăugăm numele fișierului
                    if (student.getDocuments() == null) {
                        student.setDocuments(new ArrayList<>());
                    }
                    student.getDocuments().add(fileName); // Adăugăm în memorie

                    // 3. Salvăm studentul în baza de date ca să "țină minte" fișierul
                    studentService.saveStudent(student, loggedInUser);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        return "redirect:/students";
    }
    @GetMapping("/students/{id}/documents/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable Long id, @PathVariable String filename) {
        try {
            // ⚠️ ATENȚIE AICI: Modifică calea în funcție de cum ai salvat fișierul.
            // Dacă le-ai salvat într-un folder "uploads" (ex: uploads/CV_Andrei.pdf), lasă așa:
            // Path filePath = Paths.get("uploads").resolve(filename).normalize();

            // Dacă le-ai salvat direct în folderul principal al proiectului (root), folosește:
            Path filePath = Paths.get(filename).normalize();

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

    @GetMapping("/files/{studentId}/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable Long studentId, @PathVariable String filename, HttpSession session) {
        String loggedInUser = (String) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return ResponseEntity.status(403).build();
        }
        
        // Security check moved to controller
        Optional<Student> studentOpt = studentService.findStudentById(studentId);
        if (studentOpt.isEmpty() || !loggedInUser.equals(studentOpt.get().getAddedBy())) {
            return ResponseEntity.status(403).build();
        }

        Resource file = storageService.loadAsResource(studentId, filename);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + file.getFilename() + "\"").body(file);
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
            model.addAttribute("availableJobs", studentService.findAllAvailableJobs());
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
