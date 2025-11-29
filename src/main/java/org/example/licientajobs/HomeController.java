package org.example.licientajobs;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class HomeController {

    private final StudentRepository studentRepository;

    public HomeController(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    // Pagina principală cu butoane
    @GetMapping("/")
    public String home() {
        return "index"; // index.html
    }

    // Formular adăugare student
    @GetMapping("/students/add")
    public String showAddStudentForm(Model model) {
        model.addAttribute("student", new Student());
        return "add-student";
    }

    // Salvează studentul
    @PostMapping("/students/add")
    public String addStudent(@ModelAttribute Student student) {
        studentRepository.save(student); // ID-ul va fi generat automat
        return "redirect:/students/all";
    }


    // Afișează studenții
    @GetMapping("/students/all")
    public String listStudents(Model model) {
        List<Student> students = studentRepository.findAll();
        model.addAttribute("students", students);
        return "list-students";
    }

    @GetMapping("/test")
    @ResponseBody
    public String test() {
        return "Spring Boot funcționează!";
    }

}
