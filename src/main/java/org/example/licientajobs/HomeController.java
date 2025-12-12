package org.example.licientajobs;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
public class HomeController {

    private final StudentRepository studentRepository;
    private final JobApplicationRepository jobApplicationRepository;

    public HomeController(StudentRepository studentRepository, JobApplicationRepository jobApplicationRepository) {
        this.studentRepository = studentRepository;
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
        studentRepository.save(student);
        return "redirect:/students";
    }

    @GetMapping("/students")
    public String listStudents(Model model) {
        model.addAttribute("students", studentRepository.findAll());
        return "list-students";
    }

    @GetMapping("/students/{studentId}/apply")
    public String showJobApplicationForm(@PathVariable Long studentId, Model model) {
        Optional<Student> student = studentRepository.findById(studentId);
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
        Optional<Student> studentOptional = studentRepository.findById(studentId);
        if (studentOptional.isPresent()) {
            Student student = studentOptional.get();
            JobApplication savedApplication = jobApplicationRepository.save(application);
            student.getJobApplications().add(savedApplication);
            studentRepository.save(student);
        }
        return "redirect:/students";
    }
}