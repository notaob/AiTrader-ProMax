package com.mp.aitrader.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    @Value("${resend.api-key}")
    private String resendApiKey;

    @Value("${resend.from-email:noreply@resend.dev}")
    private String fromEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendVerificationCode(String toEmail, String code) {
        String url = "https://api.resend.com/emails";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + resendApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("from", fromEmail);
        body.put("to", toEmail);
        body.put("subject", "AiTrader 验证码");
        body.put("html", "<div style=\"font-family: sans-serif; padding: 20px;\">"
                + "<h2>AiTrader 验证码</h2>"
                + "<p>您的验证码是：</p>"
                + "<div style=\"font-size: 32px; font-weight: bold; color: #4CAF50; margin: 16px 0;\">"
                + code + "</div>"
                + "<p>验证码有效期为 2 分钟，请勿泄露给他人。</p>"
                + "<p style=\"color: #999; font-size: 12px;\">如非本人操作，请忽略此邮件。</p>"
                + "</div>");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("邮件发送成功: to={}, response={}", toEmail, response.getBody());
        } catch (Exception e) {
            log.error("邮件发送失败: to={}", toEmail, e);
            throw new RuntimeException("验证码邮件发送失败，请稍后重试");
        }
    }
}
