package org.example.licientajobs;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class HomeController {

    private final StudentService studentService;

    public HomeController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/students/add")
    public String showAddStudentForm(Model model) {
        model.addAttribute("student", new Student());
        model.addAttribute("availableJobs", studentService.findAllAvailableJobs());
        return "add-student";
    }

    @PostMapping("/students/add")
    public String addStudent(@ModelAttribute Student student, @RequestParam Map<String, String> allParams) {
        // Extract dynamically generated answers from allParams
        // They will have keys like 'applicationAnswers[...]'
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith("applicationAnswers[")) {
                String questionText = entry.getKey()
                        .replace("applicationAnswers[", "")
                        .replace("]", "");
                student.getApplicationAnswers().put(questionText, entry.getValue());
            }
        }
        studentService.saveStudent(student);
        return "redirect:/students";
    }

    @GetMapping("/students")
    public String listStudents(Model model) {
        model.addAttribute("students", studentService.findAllStudents());
        return "list-students";
    }

    @GetMapping("/students/{id}/apply")
    public String showJobApplicationForm(@PathVariable Long id, Model model) {
        Optional<Student> student = studentService.findStudentById(id);
        if (student.isPresent()) {
            model.addAttribute("student", student.get());
            model.addAttribute("availableJobs", studentService.findAllAvailableJobs());
            return "apply-job";
        }
        return "redirect:/students";
    }

    @PostMapping("/students/{id}/apply")
    public String submitJobApplication(@PathVariable Long id, @RequestParam("id") Long jobId) {
        Optional<Student> studentOptional = studentService.findStudentById(id);
        Optional<JobApplication> jobOptional = studentService.findJobById(jobId);

        if (studentOptional.isPresent() && jobOptional.isPresent()) {
            Student student = studentOptional.get();
            JobApplication jobToApply = jobOptional.get();

            // Create a copy for the student's application list
            JobApplication newApplication = new JobApplication();
            newApplication.setJobTitle(jobToApply.getJobTitle());
            newApplication.setCompany(jobToApply.getCompany());
            newApplication.setDescription(jobToApply.getDescription());
            newApplication.setWorkSchedule(jobToApply.getWorkSchedule());
            newApplication.setStatus("PENDING"); // Set initial status

            student.getJobApplications().add(newApplication);
            studentService.saveStudent(student);
        }
        return "redirect:/students";
    }

    @PostMapping("/students/{studentId}/applications/{applicationId}/status")
    public String updateApplicationStatus(@PathVariable Long studentId,
                                          @PathVariable Long applicationId,
                                          @RequestParam String status) {
        studentService.updateJobApplicationStatus(studentId, applicationId, status);
        return "redirect:/students";
    }

    // Quiz routes
    @GetMapping("/quiz/{id}")
    public String showQuiz(@PathVariable Long id, Model model) {
        model.addAttribute("studentId", id);
        return "quiz";
    }

    @PostMapping("/quiz/{id}/submit")
    public String submitQuiz(@PathVariable Long id, @RequestParam Map<String, String> answers, Model model) {
        String quizResult = String.join(",", answers.values());
        studentService.updateQuizResult(id, quizResult);

        JobApplication recommendedJob = studentService.findRecommendedJob(quizResult);
        model.addAttribute("studentId", id);
        model.addAttribute("recommendedJob", recommendedJob);
        model.addAttribute("quizResult", quizResult);
        return "quiz-result";
    }
}
