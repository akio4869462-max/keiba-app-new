package org.example.keibaapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class DiscordNotificationService {

    @Value("${discord.webhook.url}")
    private String webhookUrl;

    // 運用の監視通知(記録の欠けなど)の送り先。出走通知と分けたいときだけ設定する。未設定なら出走通知と同じWebhookへ送る
    @Value("${discord.monitor.webhook.url:}")
    private String monitorWebhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendMessage(String message) {
        post(webhookUrl, message);
    }

    public void sendMonitorMessage(String message) {
        String url = (monitorWebhookUrl == null || monitorWebhookUrl.isBlank()) ? webhookUrl : monitorWebhookUrl;
        post(url, message);
    }

    private void post(String url, String message) {
        Map<String, String> body = Map.of(
                "content", message
        );

        restTemplate.postForObject(url, body, String.class);
    }
}