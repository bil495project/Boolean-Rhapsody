package com.roadrunner.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonProperty;

@Service
public class RecaptchaService {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    @Value("${app.recaptcha.secret-key}")
    private String secretKey;

    @Value("${app.recaptcha.enabled:true}")
    private boolean enabled;

    @Value("${app.recaptcha.min-score:0.5}")
    private double minScore;

    private final RestTemplate restTemplate;

    public RecaptchaService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void verify(String recaptchaToken, String expectedAction) {
        if (!enabled) {
            return;
        }

        if (recaptchaToken == null || recaptchaToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reCAPTCHA token is missing");
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", secretKey);
        params.add("response", recaptchaToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        RecaptchaResponse response = restTemplate.postForObject(VERIFY_URL, request, RecaptchaResponse.class);
        if (response == null || !response.success()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reCAPTCHA verification failed");
        }

        if (response.score() < minScore) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reCAPTCHA score too low");
        }

        if (expectedAction != null && !expectedAction.equals(response.action())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reCAPTCHA action mismatch");
        }
    }

    record RecaptchaResponse(
            boolean success,
            double score,
            String action,
            @JsonProperty("error-codes") List<String> errorCodes) {
    }
}
