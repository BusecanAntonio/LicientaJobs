package org.example.licientajobs;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
public class HomeController {

    private final StudentRepository studentRepository;

    public HomeController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
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
    public String addStudent(@ModelAttribute Student student,
                             @RequestParam("grades") String gradesString) {

        List<Integer> parsedGrades = new ArrayList<>();

        if (gradesString != null && !gradesString.isBlank()) {
            String[] parts = gradesString.split(",");

            for (String p : parts) {
                try {
                    parsedGrades.add(Integer.parseInt(p.trim()));
                } catch (Exception ignored) {
                }
            }
        }

        student.setGrades(parsedGrades);

        studentRepository.save(student);
        return "redirect:/students/all";
    }

    @GetMapping("/students/all")
    public String listStudents(Model model) {
        List<Student> students = studentRepository.findAll();
        model.addAttribute("students", students);
        return "list-students";
    }

    @GetMapping("/test")
    @ResponseBody
    public String test() {
        return "Spring Boot functioneaza!";
    }
}
