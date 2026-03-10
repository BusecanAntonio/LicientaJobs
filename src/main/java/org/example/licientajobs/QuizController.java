package org.example.licientajobs;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    public String startQuiz(@PathVariable Long studentId, HttpSession session, Model model) {
        session.setAttribute("currentQuestionIndex", 0);
        session.setAttribute("answers", new ArrayList<String>());
        return "redirect:/students/" + studentId + "/quiz/question";
    }

    @GetMapping("/question")
    public String showQuestion(@PathVariable Long studentId, HttpSession session, Model model) {
        Integer index = (Integer) session.getAttribute("currentQuestionIndex");
        if (index == null) {
            return "redirect:/students/" + studentId + "/quiz";
        }

        List<QuizQuestion> questions = quizService.getQuestions();
        if (index >= questions.size()) {
            return "redirect:/students/" + studentId + "/quiz/result";
        }

        model.addAttribute("question", questions.get(index));
        model.addAttribute("studentId", studentId);
        model.addAttribute("currentQuestionNumber", index + 1);
        model.addAttribute("totalQuestions", questions.size());
        return "quiz";
    }

    @PostMapping("/answer")
    public String submitAnswer(@PathVariable Long studentId, @RequestParam String answer, HttpSession session) {
        List<String> answers = (List<String>) session.getAttribute("answers");
        if (answers == null) {
            answers = new ArrayList<>();
        }
        answers.add(answer);
        session.setAttribute("answers", answers);

        Integer index = (Integer) session.getAttribute("currentQuestionIndex");
        session.setAttribute("currentQuestionIndex", index + 1);

        return "redirect:/students/" + studentId + "/quiz/question";
    }

    @GetMapping("/result")
    public String showResult(@PathVariable Long studentId, HttpSession session, Model model) {
        List<String> answers = (List<String>) session.getAttribute("answers");
        if (answers == null || answers.isEmpty()) {
            return "redirect:/students/" + studentId + "/quiz";
        }

        String result = quizService.calculateResult(answers);
        String description = quizService.getResultDescription(result);
        
        Optional<Student> studentOpt = studentService.findStudentById(studentId);
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            student.setQuizResult(result);
            studentService.saveStudent(student);
            model.addAttribute("student", student);
        }

        model.addAttribute("result", result);
        model.addAttribute("description", description);
        
        // Clear session
        session.removeAttribute("currentQuestionIndex");
        session.removeAttribute("answers");

        return "quiz-result";
    }
}