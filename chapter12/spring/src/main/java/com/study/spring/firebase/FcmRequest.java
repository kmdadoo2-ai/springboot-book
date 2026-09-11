package com.study.spring.firebase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FcmRequest(
                @NotBlank(message = "FCM 토큰을 입력하세요.") @Size(max = 4096) String token,
                @NotBlank(message = "제목을 입력하세요.") @Size(max = 100) String title,
                @NotBlank(message = "본문을 입력하세요.") @Size(max = 500) String body) {
}