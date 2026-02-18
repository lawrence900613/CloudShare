package com.example.fileshare.request; // adjust to your project package

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestController {
    @GetMapping("/why")
    public String hello() {
        return "Hello, World!";
    }
}