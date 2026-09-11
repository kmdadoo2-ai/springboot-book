package com.study.spring.controller;

import com.study.spring.form.MemberForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class MemberController {

    @GetMapping("/members/new")
    public String form(Model model) {
        model.addAttribute("memberForm", new MemberForm());
        return "member-form";
    }

    @PostMapping("/members/new")
    public String submit(@Valid MemberForm memberForm, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "member-form";
        }

        model.addAttribute("member", memberForm);
        return "member-result";
    }
}