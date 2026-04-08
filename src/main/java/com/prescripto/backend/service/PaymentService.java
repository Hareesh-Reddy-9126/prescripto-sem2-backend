package com.prescripto.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${app.stripe.secret-key:}")
    private String stripeSecretKey;

    public PaymentService(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> createRazorpayOrder(long amount, String currency, String receipt) {
        ensureRazorpayConfigured();

        try {
            String auth = basicAuth(razorpayKeyId, razorpayKeySecret);
            String payload = objectMapper.writeValueAsString(Map.of(
                "amount", amount,
                "currency", currency,
                "receipt", receipt
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com/v1/orders"))
                .header("Authorization", auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Failed to create Razorpay order");
            }

            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException(ex.getMessage());
        }
    }

    public Map<String, Object> fetchRazorpayOrder(String orderId) {
        ensureRazorpayConfigured();

        try {
            String auth = basicAuth(razorpayKeyId, razorpayKeySecret);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com/v1/orders/" + orderId))
                .header("Authorization", auth)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Failed to fetch Razorpay order");
            }

            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException(ex.getMessage());
        }
    }

    public String createStripeCheckoutSession(String origin, String appointmentId, long amount, String currency) {
        ensureStripeConfigured();

        try {
            Map<String, String> form = new HashMap<>();
            form.put("success_url", origin + "/verify?success=true&appointmentId=" + appointmentId);
            form.put("cancel_url", origin + "/verify?success=false&appointmentId=" + appointmentId);
            form.put("mode", "payment");
            form.put("line_items[0][price_data][currency]", currency.toLowerCase());
            form.put("line_items[0][price_data][product_data][name]", "Appointment Fees");
            form.put("line_items[0][price_data][unit_amount]", String.valueOf(amount));
            form.put("line_items[0][quantity]", "1");

            StringBuilder bodyBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : form.entrySet()) {
                if (bodyBuilder.length() > 0) {
                    bodyBuilder.append('&');
                }
                bodyBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                bodyBuilder.append('=');
                bodyBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.stripe.com/v1/checkout/sessions"))
                .header("Authorization", "Bearer " + stripeSecretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(bodyBuilder.toString()))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Failed to create Stripe checkout session");
            }

            Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<>() {});
            Object url = parsed.get("url");
            return url == null ? null : String.valueOf(url);
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException(ex.getMessage());
        }
    }

    private void ensureRazorpayConfigured() {
        if (isBlank(razorpayKeyId) || isBlank(razorpayKeySecret)) {
            throw new IllegalStateException("Razorpay credentials are not configured");
        }
    }

    private void ensureStripeConfigured() {
        if (isBlank(stripeSecretKey)) {
            throw new IllegalStateException("Stripe credentials are not configured");
        }
    }

    private String basicAuth(String username, String password) {
        String raw = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
