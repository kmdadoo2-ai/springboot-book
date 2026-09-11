package com.study.spring.etc;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/etc")
public class EtcController {

    private final TextService textService;
    private final Environment environment;

    @ModelAttribute
    public void addEnvironment(Model model) {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        model.addAttribute("profiles", String.join(", ", profiles));
        model.addAttribute("environmentName",
                environment.getProperty("app.environment-name", "미지정"));
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("textForm", new TextForm());
        return "etc/index";
    }

    @PostMapping("/text")
    public String inspect(@Valid @ModelAttribute("textForm") TextForm form,
            BindingResult bindingResult, Model model) {
        if (!bindingResult.hasErrors()) {
            model.addAttribute("textResult", textService.inspect(form.getText()));
        }
        return "etc/index";
    }
}
