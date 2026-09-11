package com.study.spring.controller;

import com.study.spring.form.MemberForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/members/new")
public class MemberController {

    @GetMapping
    public String form(Model model) {
        model.addAttribute("memberForm", new MemberForm());
        return "member-form";
    }

    @PostMapping
    public String submit(@Valid MemberForm memberForm, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "member-form";
        }

        model.addAttribute("member", memberForm);
        return "member-result";
    }
}