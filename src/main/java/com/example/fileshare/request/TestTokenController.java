package com.example.fileshare.request;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/secure")
public class TestTokenController {

    @GetMapping("/test")
    public String me(Authentication auth) {
        return "hello " + auth.getName();
    }
}
