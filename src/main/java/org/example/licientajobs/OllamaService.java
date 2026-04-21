package org.example.licientajobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class OllamaService {

    private static final Logger logger = LoggerFactory.getLogger(OllamaService.class);

    // Poți schimba acest URL din application.properties folosind proprietatea ollama.api.url
    @Value("${ollama.api.url:http://localhost:11434/api/generate}")
    private String ollamaUrl;

    // Modelul implicit pe care îl vei folosi (ex. llama2, mistral)
    @Value("${ollama.model:llama2}")
    private String defaultModel;

    private final RestTemplate restTemplate;

    public OllamaService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Trimite un prompt către Ollama și returnează răspunsul.
     * 
     * @param prompt Textul/întrebarea pentru LLM
     * @return Răspunsul generat de model
     */
    public String generateResponse(String prompt) {
        return generateResponse(prompt, defaultModel);
    }

    /**
     * Trimite un prompt către un model specific Ollama.
     * 
     * @param prompt Textul/întrebarea
     * @param model  Numele modelului (ex: "mistral")
     * @return Răspunsul generat
     */
    public String generateResponse(String prompt, String model) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false); // Setat pe false pentru a primi tot răspunsul deodată

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(ollamaUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("response");
            } else {
                logger.error("Failed to get response from Ollama. Status code: {}", response.getStatusCode());
                return "Eroare la comunicarea cu LLM.";
            }

        } catch (Exception e) {
            logger.error("Exception occurred while calling Ollama API.", e);
            return "A apărut o eroare la conectarea cu Ollama. Asigură-te că serviciul rulează local pe portul 11434.";
        }
    }
}
