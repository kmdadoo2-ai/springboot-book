package com.study.spring.firebase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirestoreMessageRequest(
        @NotBlank(message = "이름을 입력하세요.") @Size(max = 50) String name,
        @NotBlank(message = "메시지를 입력하세요.") @Size(max = 1000) String message) {
}
