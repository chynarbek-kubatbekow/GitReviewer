package com.gitreqiever.telegramnotifier.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UptimeController {

    @GetMapping({"/", "/health", "/uptime"})
    public Map<String, Object> health() {
        return Map.of("ok", true);
    }

    @RequestMapping(method = RequestMethod.HEAD, value = {"/", "/health", "/uptime"})
    public ResponseEntity<Void> headHealth() {
        return ResponseEntity.noContent().build();
    }
}
