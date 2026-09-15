package com.study.spring.firebase;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FirebasePageController {

    @GetMapping("/admin/firebase")
    public String index() {
        return "firebase/index";
    }
}
