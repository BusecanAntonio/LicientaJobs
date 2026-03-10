package org.example.licientajobs;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private final List<QuizQuestion> questions;
    private final Map<String, String> resultDescriptions;

    public QuizService() {
        this.questions = new ArrayList<>();
        // Question 1
        questions.add(new QuizQuestion(1, "Când te confrunți cu o sarcină complexă, primul tău pas este să:", Arrays.asList(
                new QuizAnswer("Împart sarcina în pași mai mici și creez un plan detaliat.", "ORGANIZARE"),
                new QuizAnswer("Discut cu colegii pentru a auzi perspective diferite.", "EMPATIE"),
                new QuizAnswer("Analizez toate datele disponibile pentru a înțelege problema în profunzime.", "ANALITIC")
        )));
        // Question 2
        questions.add(new QuizQuestion(2, "Într-un proiect de grup, preferi să fii cel care:", Arrays.asList(
                new QuizAnswer("Stabilește obiectivele și motivează echipa.", "LEADERSHIP"),
                new QuizAnswer("Vine cu idei neconvenționale și soluții 'out-of-the-box'.", "CREATIVITATE"),
                new QuizAnswer("Se asigură că toată lumea se simte inclusă și ascultată.", "EMPATIE")
        )));
        // Question 3
        questions.add(new QuizQuestion(3, "Ce te descrie cel mai bine la locul de muncă?", Arrays.asList(
                new QuizAnswer("Sunt o persoană meticuloasă, atentă la termene limită și detalii.", "ORGANIZARE"),
                new QuizAnswer("Sunt un bun ascultător și mediator în conflicte.", "EMPATIE"),
                new QuizAnswer("Sunt cel care pune întrebări dificile și caută dovezi.", "ANALITIC")
        )));
        // Question 4
        questions.add(new QuizQuestion(4, "Cum abordezi o sesiune de brainstorming?", Arrays.asList(
                new QuizAnswer("Vin cu cât mai multe idei, indiferent cât de 'nebunești' par.", "CREATIVITATE"),
                new QuizAnswer("Structurez sesiunea pentru a fi eficientă și a atinge un scop clar.", "LEADERSHIP"),
                new QuizAnswer("Construiesc pe ideile altora și încurajez participarea tuturor.", "EMPATIE")
        )));
        // Question 5
        questions.add(new QuizQuestion(5, "Un coleg se luptă cu o sarcină. Ce faci?", Arrays.asList(
                new QuizAnswer("Îl întreb cum se simte și dacă pot ajuta cu ceva.", "EMPATIE"),
                new QuizAnswer("Îi analizez munca pentru a găsi eroarea logică.", "ANALITIC"),
                new QuizAnswer("Îi ofer o nouă perspectivă sau o metodă alternativă de a privi problema.", "CREATIVITATE")
        )));
        // Question 6
        questions.add(new QuizQuestion(6, "Ce fel de mediu de lucru preferi?", Arrays.asList(
                new QuizAnswer("Unul dinamic, unde pot prelua inițiativa și pot influența direcția.", "LEADERSHIP"),
                new QuizAnswer("Unul bine structurat, cu sarcini clare și predictibilitate.", "ORGANIZARE"),
                new QuizAnswer("Unul flexibil, care încurajează experimentarea și inovația.", "CREATIVITATE")
        )));
        // Question 7
        questions.add(new QuizQuestion(7, "Cum iei decizii importante?", Arrays.asList(
                new QuizAnswer("Pe baza datelor, faptelor și a unei analize logice riguroase.", "ANALITIC"),
                new QuizAnswer("Mă gândesc la impactul asupra echipei și a celor din jur.", "EMPATIE"),
                new QuizAnswer("Am încredere în intuiția mea și în viziunea de ansamblu.", "LEADERSHIP")
        )));
        // Question 8
        questions.add(new QuizQuestion(8, "Ce te frustrează cel mai mult?", Arrays.asList(
                new QuizAnswer("Lipsa de planificare și haosul.", "ORGANIZARE"),
                new QuizAnswer("Rigiditatea și refuzul de a încerca lucruri noi.", "CREATIVITATE"),
                new QuizAnswer("Deciziile luate fără o bază solidă de date.", "ANALITIC")
        )));
        // Question 9
        questions.add(new QuizQuestion(9, "Cum arată spațiul tău de lucru?", Arrays.asList(
                new QuizAnswer("Curat, ordonat, cu totul la locul lui.", "ORGANIZARE"),
                new QuizAnswer("Plin de post-it-uri, schițe și prototipuri.", "CREATIVITATE"),
                new QuizAnswer("Minimalist, doar cu strictul necesar pentru a analiza informația.", "ANALITIC")
        )));
        // Question 10
        questions.add(new QuizQuestion(10, "Care este cel mai mare atu al tău?", Arrays.asList(
                new QuizAnswer("Capacitatea de a inspira și de a mobiliza oamenii.", "LEADERSHIP"),
                new QuizAnswer("Abilitatea de a înțelege și de a relaționa cu ceilalți.", "EMPATIE"),
                new QuizAnswer("Atenția mea la detalii și capacitatea de a planifica.", "ORGANIZARE")
        )));

        // Descriptions for results
        this.resultDescriptions = new HashMap<>();
        resultDescriptions.put("LEADERSHIP", "Ești un lider înnăscut. Îți place să preiei inițiativa, să motivezi oamenii și să ghidezi echipa spre succes. Te descurci excelent în roluri de management și coordonare.");
        resultDescriptions.put("CREATIVITATE", "Ești o minte creativă și inovatoare. Găsești soluții neconvenționale și aduci o perspectivă proaspătă. Te potrivești în medii care încurajează experimentarea și designul.");
        resultDescriptions.put("ANALITIC", "Gândirea ta este logică, structurată și bazată pe date. Îți place să rezolvi probleme complexe și să optimizezi procese. Rolurile tehnice, de analiză sau cercetare sunt ideale pentru tine.");
        resultDescriptions.put("EMPATIE", "Ești un coechipier excepțional. Pui accent pe colaborare, comunicare și armonie în grup. Abilitatea ta de a înțelege oamenii te face un mediator și un sprijin de nădejde.");
        resultDescriptions.put("ORGANIZARE", "Ești o persoană meticuloasă, ordonată și foarte bine organizată. Îți place să planifici, să respecți termenele limită și să te asiguri că totul funcționează perfect. Ești un project manager excelent.");
    }

    public List<QuizQuestion> getQuestions() {
        return questions;
    }

    public String getResultDescription(String result) {
        return resultDescriptions.getOrDefault(result, "Nu a fost găsită o descriere.");
    }

    public String calculateResult(List<String> answers) {
        if (answers == null || answers.isEmpty()) {
            return "NECUNOSCUT";
        }
        // Count occurrences of each trait
        Map<String, Long> counts = answers.stream()
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()));

        // Find the trait with the highest count
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NECUNOSCUT");
    }
}