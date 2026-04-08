package com.prescripto.backend.util;

import java.util.HashMap;
import java.util.Map;

public final class ApiResponse {

    private ApiResponse() {
    }

    public static Map<String, Object> success() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return response;
    }

    public static Map<String, Object> success(String key, Object value) {
        Map<String, Object> response = success();
        response.put(key, value);
        return response;
    }

    public static Map<String, Object> failure(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }
}
