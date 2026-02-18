package com.example.fileshare.request;
import com.example.fileshare.dto.RequestDTO.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class RequestController {

    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO req) {
        service.register(req.email, req.password);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public TokenDTO login(@RequestBody LoginDTO req) {
        String token = service.login(req.email, req.password);
        return new TokenDTO(token);
    }
}
