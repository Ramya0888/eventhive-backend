package com.eventhive.eventhive_backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * AIService — wraps Google Gemini API calls.
 *
 * We use plain RestTemplate (not a Gemini SDK) because:
 * 1. No official Java SDK exists for Gemini yet
 * 2. RestTemplate is a core Spring concept worth demonstrating
 * 3. The REST API is simple enough that an SDK would be overkill
 *
 * Interview Q: "How did you integrate AI into EventHive?"
 * Answer: "I call Google's Gemini REST API from a Spring service
 * using RestTemplate. The service is stateless — each call sends
 * the full prompt and gets a response. For the description generator
 * I construct a prompt from the event title and category; for sentiment
 * I send the feedback text and ask for a JSON classification."
 */
@Slf4j
@Service
public class AIService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1/models/" +
        "gemini-2.5-flash:generateContent?key=";
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Generates a professional event description from title and category.
     *
     * Prompt engineering: we give Gemini clear instructions and
     * constraints (2-3 sentences, professional tone, no placeholder text)
     * so the output is immediately usable without editing.
     */
    public String generateEventDescription(String title, String category) {
        log.info("Generating description for event: '{}'", title);

        String prompt = String.format(
            "Write a professional, engaging event description for an event called " +
            "'%s' in the '%s' category. " +
            "Keep it 2-3 sentences, enthusiastic but informative. " +
            "Do not include placeholder text, ticket prices, or dates. " +
            "Write only the description, nothing else.",
            title, category
        );

        String result = callGemini(prompt);
        log.info("Description generated successfully for '{}'", title);
        return result;
    }

    /**
     * Analyzes feedback text and returns sentiment + confidence.
     *
     * We ask Gemini to respond in a strict format so we can parse
     * it reliably without complex NLP libraries.
     *
     * Returns: "POSITIVE:0.92" or "NEUTRAL:0.75" or "NEGATIVE:0.88"
     */
    public String analyzeSentiment(String feedbackText) {
        log.info("Analyzing sentiment for feedback: '{}'",
                feedbackText.substring(0, Math.min(50, feedbackText.length())));

        String prompt = String.format(
            "Analyze the sentiment of this event feedback: \"%s\"\n\n" +
            "Respond with EXACTLY this format and nothing else:\n" +
            "SENTIMENT:CONFIDENCE\n" +
            "Where SENTIMENT is one of: POSITIVE, NEUTRAL, NEGATIVE\n" +
            "Where CONFIDENCE is a decimal between 0 and 1 (e.g. 0.92)\n" +
            "Example response: POSITIVE:0.87\n" +
            "Your response:",
            feedbackText
        );

        return callGemini(prompt);
    }

    /**
     * Core method — makes the HTTP call to Gemini API.
     *
     * Gemini's request format:
     * {
     *   "contents": [{
     *     "parts": [{ "text": "your prompt here" }]
     *   }]
     * }
     *
     * Response structure:
     * candidates[0].content.parts[0].text → the generated text
     */
    @SuppressWarnings("unchecked")
    private String callGemini(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build request body as nested Maps — matches Gemini's JSON structure
        Map<String, Object> part    = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> body    = Map.of("contents", List.of(content));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    GEMINI_URL + apiKey,
                    request,
                    Map.class
            );

            // Navigate the nested response: candidates[0].content.parts[0].text
            List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) response.getBody().get("candidates");
            Map<String, Object> firstCandidate = candidates.get(0);
            Map<String, Object> responseContent =
                    (Map<String, Object>) firstCandidate.get("content");
            List<Map<String, Object>> parts =
                    (List<Map<String, Object>>) responseContent.get("parts");

            return parts.get(0).get("text").toString().trim();

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("AI service unavailable: " + e.getMessage());
        }
    }
}