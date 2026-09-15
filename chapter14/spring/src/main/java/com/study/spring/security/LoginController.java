package com.study.spring.security;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    @GetMapping("/")
    public String home() {
        return "redirect:/jpa/boards";
    }

    @GetMapping("/login")
    public String login() {
        return "security/login";
    }

    @GetMapping("/admin")
    public String admin() {
        return "security/admin";
    }
}
