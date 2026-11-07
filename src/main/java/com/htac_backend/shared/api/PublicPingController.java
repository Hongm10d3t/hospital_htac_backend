package com.htac_backend.shared.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicPingController {
    @GetMapping("/api/v1/public/ping")
    public Map<String, String> ping() {
        return Map.of("status", "UP");
    }

    @GetMapping("/api/v1/public/health")
    public String health() {
        return "Hệ thống đã sẵn sàng hoạt động thuy xinh gai qua";
    }

}
