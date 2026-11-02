package com.smartexam.controller;

import com.smartexam.dto.Api.*;
import com.smartexam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @PostMapping("/register") public UserView register(@Valid @RequestBody RegisterRequest request) { return auth.register(request); }
    @GetMapping("/me") public UserView me(Authentication authentication) { return auth.current(authentication.getName()); }
}
