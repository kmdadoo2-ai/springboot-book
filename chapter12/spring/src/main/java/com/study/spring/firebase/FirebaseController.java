package com.study.spring.firebase;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/firebase")
@RequiredArgsConstructor
public class FirebaseController {

    private final FirebaseService firebaseService;

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", firebaseService.isEnabled());
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/fcm/send")
    public Map<String, String> send(@Valid @RequestBody FcmRequest request) {
        return Map.of("messageId", firebaseService.send(request));
    }

    @PostMapping("/messages")
    public ResponseEntity<FirebaseService.MessageInfo> save(
            @Valid @RequestBody FirestoreMessageRequest request) {
        FirebaseService.MessageInfo saved = firebaseService.saveMessage(request);
        return ResponseEntity.created(
                URI.create("/api/firebase/messages/" + saved.id())).body(saved);
    }

    @GetMapping("/messages/{id}")
    public FirebaseService.MessageInfo findMessage(@PathVariable("id") String id) {
        return firebaseService.findMessage(id);
    }

    @GetMapping("/users/{uid}")
    public FirebaseService.UserInfo findUser(@PathVariable("uid") String uid) {
        return firebaseService.findUser(uid);
    }
}
