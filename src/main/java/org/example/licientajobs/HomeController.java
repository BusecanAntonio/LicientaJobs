package org.example.licientajobs;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
public class HomeController {

    private final StudentService studentService;
    private final JobApplicationRepository jobApplicationRepository;

    public HomeController(StudentService studentService, JobApplicationRepository jobApplicationRepository) {
        this.studentService = studentService;
        this.jobApplicationRepository = jobApplicationRepository;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/students/add")
    public String showAddStudentForm(Model model) {
        model.addAttribute("student", new Student());
        return "add-student";
    }

    @PostMapping("/students/add")
    public String addStudent(@ModelAttribute Student student) {
        studentService.saveStudent(student);
        return "redirect:/students";
    }

    @GetMapping("/students")
    public String listStudents(Model model) {
        model.addAttribute("students", studentService.findAllStudents());
        return "list-students";
    }

    @GetMapping("/students/{studentId}/apply")
    public String showJobApplicationForm(@PathVariable Long studentId, Model model) {
        Optional<Student> student = studentService.findStudentById(studentId);
        if (student.isPresent()) {
            JobApplication application = new JobApplication();
            model.addAttribute("student", student.get());
            model.addAttribute("application", application);
            return "apply-job";
        }
        return "redirect:/students";
    }

    @PostMapping("/students/{studentId}/apply")
    public String submitJobApplication(@PathVariable Long studentId,
                                       @ModelAttribute JobApplication application) {
        Optional<Student> studentOptional = studentService.findStudentById(studentId);
        if (studentOptional.isPresent()) {
            Student student = studentOptional.get();
            student.getJobApplications().add(application);
            studentService.saveStudent(student);
        }
        return "redirect:/students";
    }

    @PostMapping("/students/{studentId}/applications/{applicationId}/update-status")
    public String updateApplicationStatus(@PathVariable Long studentId,
                                          @PathVariable Long applicationId,
                                          @RequestParam String status) {
        studentService.updateJobApplicationStatus(studentId, applicationId, status);
        return "redirect:/students";
    }
}