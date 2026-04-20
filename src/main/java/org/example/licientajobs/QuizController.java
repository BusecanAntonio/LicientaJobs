package org.example.licientajobs;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
@RequestMapping("/students/{studentId}/quiz")
public class QuizController {
    private final QuizService quizService;
    private final StudentService studentService;

    public QuizController(QuizService quizService, StudentService studentService) {
        this.quizService = quizService;
        this.studentService = studentService;
    }

    @GetMapping
    public String start(@PathVariable Long studentId, HttpSession s) {
        s.setAttribute("currentQuestionIndex", 0);
        s.setAttribute("answers", new ArrayList<String>());
        return "redirect:/students/" + studentId + "/quiz/question";
    }

    @GetMapping("/question")
    public String ask(@PathVariable Long studentId, HttpSession s, Model m) {
        Integer idx = (Integer) s.getAttribute("currentQuestionIndex");
        if (idx == null) return "redirect:/students/" + studentId + "/quiz";

        List<QuizQuestion> qs = quizService.getQuestions();
        if (idx >= qs.size()) return "redirect:/students/" + studentId + "/quiz/result";

        m.addAttribute("question", qs.get(idx));
        m.addAttribute("studentId", studentId);
        m.addAttribute("currentQuestionNumber", idx + 1);
        m.addAttribute("totalQuestions", qs.size());
        return "quiz";
    }

    @PostMapping("/answer")
    public String answer(@PathVariable Long studentId, @RequestParam String answer, HttpSession s) {
        List<String> ans = (List<String>) s.getAttribute("answers");
        ans.add(answer);
        s.setAttribute("currentQuestionIndex", (Integer)s.getAttribute("currentQuestionIndex") + 1);
        return "redirect:/students/" + studentId + "/quiz/question";
    }


    @GetMapping("/result")
    public String result(@PathVariable Long studentId, HttpSession s, Model m) {
        String user = (String) s.getAttribute("loggedInUser");
        if (user == null) return "redirect:/login";

        List<String> ans = (List<String>) s.getAttribute("answers");
        String res = quizService.calculateResult(ans);

        studentService.findStudentById(studentId).ifPresent(student -> {
            student.setQuizResult(res);

            // Corecția: Apelul metodei cu un singur parametru
            studentService.saveStudent(student);

            m.addAttribute("student", student);
            m.addAttribute("recommendedJob", studentService.findRecommendedJob(res));
        });

        m.addAttribute("result", res);
        m.addAttribute("description", quizService.getResultDescription(res));
        return "quiz-result";
    }
}
