package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OllamaStartupRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(OllamaStartupRunner.class);
    
    private final OllamaService ollamaService;

    @Value("${ollama.model:llama3}")
    private String modelName;

    public OllamaStartupRunner(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @Override
    public void run(String... args) {
        logger.info("======================================================");
        
        startOllamaProcess();

        logger.info("Inițializare test Ollama LLM la pornirea aplicației...");
        
        String prompt = "Salut! Ești funcțional? Răspunde scurt cu da sau nu.";
        logger.info("Trimitem prompt-ul: \"{}\"", prompt);
        
        // Apelăm serviciul Ollama
        String response = ollamaService.generateResponse(prompt);
        
        logger.info("Răspuns primit de la Ollama:");
        logger.info(response);
        logger.info("======================================================");
    }

    private void startOllamaProcess() {
        try {
            logger.info("Attempting to start Ollama with model: {}", modelName);
            
            // Această comandă funcționează pe Windows/Linux/Mac presupunând că "ollama" este în PATH
            String[] command;
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("win")) {
                command = new String[]{"cmd.exe", "/c", "ollama run " + modelName};
            } else {
                command = new String[]{"sh", "-c", "ollama run " + modelName};
            }

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            
            // Opțional: redirecționăm erorile pentru a nu polua logurile aplicației de bază
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();

            // Așteptăm puțin pentru a-i da timp serverului Ollama să pornească pe portul 11434
            logger.info("Ollama process started. Waiting a few seconds for initialization...");
            Thread.sleep(5000); 

        } catch (IOException e) {
            logger.error("Failed to start Ollama process automatically. Is Ollama installed and added to PATH?", e);
        } catch (InterruptedException e) {
            logger.warn("Thread interrupted while waiting for Ollama to start.", e);
            Thread.currentThread().interrupt();
        }
    }
}
