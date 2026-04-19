package org.example.licientajobs;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final StudentService studentService;

    public HomeController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        if (session.getAttribute("loggedInUser") != null) {
            return "redirect:/students";
        }
        return "redirect:/login"; // Acum redirecționează către login direct
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
        // Log in the user directly after registration
        session.setAttribute("loggedInUser", username);
        // Redirect to add personal data (student form)
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

        List<Student> userStudents = studentService.findAllStudents().stream()
                .filter(s -> loggedInUser.equals(s.getAddedBy()))
                .collect(Collectors.toList());

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

        Optional<Student> studentOpt = studentService.findStudentById(id);
        if (studentOpt.isPresent() && loggedInUser.equals(studentOpt.get().getAddedBy())) {
            studentService.deleteStudent(id);
        }
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
