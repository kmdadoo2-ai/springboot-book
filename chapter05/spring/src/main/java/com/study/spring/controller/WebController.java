package com.study.spring.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;
import com.study.spring.form.MemberForm;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class WebController {
    @GetMapping("/view")
    public String view() {
        return "view";
    }

    @GetMapping("/model")
    public String model(Model model) {
        model.addAttribute("name", "Spring Boot");
        model.addAttribute("version", "4.1.1");
        return "model";
    }

    @GetMapping("/list")
    public String list(Model model) {
        model.addAttribute("items", List.of("Java 21", "Spring Boot 4.1.1", "Thymeleaf"));
        return "list";
    }

    @GetMapping("/condition")
    public String condition(Model model) {
        model.addAttribute("login", true);
        return "condition";
    }

    @GetMapping("/member/new")
    public String memberForm(Model model) {
        model.addAttribute("memberForm", new MemberForm());
        return "member-form";
    }

    @PostMapping("/member/new")
    public String memberSubmit(MemberForm memberForm, Model model) {
        model.addAttribute("member", memberForm);
        return "member-result";
    }
}
