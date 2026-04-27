package org.example.licientajobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class QuizController {

    @Autowired
    private StudentRepository studentRepository;

    private List<Question> allQuestions;

    public QuizController() {
        // Load questions from JSON
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            allQuestions = objectMapper.readValue(new File("questions.json"), new TypeReference<List<Question>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            // In a real app, handle this more gracefully
            allQuestions = List.of();
        }
    }

    @GetMapping("/quiz")
    public String showQuiz(@RequestParam("studentId") Long studentId, Model model) {
        Optional<Student> student = studentRepository.findById(studentId);
        if (student.isEmpty()) {
            return "redirect:/"; // Or an error page
        }

        // Make a copy of the questions to shuffle them
        List<Question> randomQuestions = new ArrayList<>(allQuestions);
        Collections.shuffle(randomQuestions);

        // Pick the first 5 questions (or less if there aren't enough)
        int numberOfQuestionsToShow = Math.min(5, randomQuestions.size());
        List<Question> selectedQuestions = randomQuestions.subList(0, numberOfQuestionsToShow);

        // Shuffle the options within each selected question
        for (Question q : selectedQuestions) {
            List<Option> shuffledOptions = new ArrayList<>(q.getOptions());
            Collections.shuffle(shuffledOptions);
            q.setOptions(shuffledOptions);
        }

        model.addAttribute("student", student.get());
        model.addAttribute("questions", selectedQuestions);
        return "quiz";
    }

    @PostMapping("/quiz/submit")
    public String submitQuiz(@RequestParam("studentId") Long studentId, @RequestParam Map<String, String> answers) {
        Optional<Student> optionalStudent = studentRepository.findById(studentId);
        if (optionalStudent.isEmpty()) {
            return "redirect:/"; // Or an error page
        }

        Map<String, Integer> domainScores = new HashMap<>();

        for (Map.Entry<String, String> entry : answers.entrySet()) {
            if (entry.getKey().startsWith("question_")) {
                int questionId = Integer.parseInt(entry.getKey().replace("question_", ""));
                
                // Opțiunea e salvată ca string-ul opțiunii, dar noi am trimis index-ul în value în HTML.
                // În versiunea cu opțiuni amestecate, indexul din HTML corespunde cu ordinea AMECSTECATĂ, 
                // dar noi trebuie să citim opțiunea efectivă, așa că cel mai bine modificăm HTML-ul 
                // să trimită numele domeniului sau să o gestionăm diferit. 
                // Pentru a păstra logica simplă, HTML-ul va trebui să trimită domeniul ca valoare (vezi modificarea din quiz.html).
                
                String selectedDomain = entry.getValue(); // Aici value va fi direct domeniul
                domainScores.merge(selectedDomain, 2, Integer::sum); // Acordăm 2 puncte direct
            }
        }

        // Find the domain with the highest score
        String bestDomain = domainScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Generalist");

        Student student = optionalStudent.get();
        student.setQuizResult(bestDomain);
        studentRepository.save(student);

        // Redirect back to the main list of students instead of a profile page
        return "redirect:/";
    }
}
