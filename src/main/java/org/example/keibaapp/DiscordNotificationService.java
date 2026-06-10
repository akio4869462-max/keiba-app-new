package org.example.keibaapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class DiscordNotificationService {

    @Value("${discord.webhook.url}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendMessage(String message) {
        Map<String, String> body = Map.of(
                "content", message
        );

        restTemplate.postForObject(webhookUrl, body, String.class);
    }
}