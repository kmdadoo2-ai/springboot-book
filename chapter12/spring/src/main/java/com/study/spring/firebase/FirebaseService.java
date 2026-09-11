package com.study.spring.firebase;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FirebaseService {

    private final ObjectProvider<FirebaseApp> appProvider;
    private final ObjectProvider<Firestore> firestoreProvider;

    public boolean isEnabled() {
        return appProvider.getIfAvailable() != null;
    }

    private FirebaseApp requireApp() {
        FirebaseApp app = appProvider.getIfAvailable();
        if (app == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Firebase가 비활성화되어 있습니다.");
        }
        return app;
    }

    private Firestore requireFirestore() {
        requireApp();
        Firestore firestore = firestoreProvider.getIfAvailable();
        if (firestore == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Firestore를 사용할 수 없습니다.");
        }
        return firestore;
    }

    public String send(FcmRequest request) {
        FirebaseApp app = requireApp();
        Message message = Message.builder()
                .setFid(request.token())
                .setNotification(Notification.builder()
                        .setTitle(request.title())
                        .setBody(request.body())
                        .build())
                .build();
        return await(FirebaseMessaging.getInstance(app).sendAsync(message)); // [3]
    }

    public MessageInfo saveMessage(FirestoreMessageRequest request) {
        DocumentReference document = requireFirestore()
                .collection("messages").document();
        String createdAt = Instant.now().toString();

        Map<String, Object> data = Map.of(
                "name", request.name(),
                "message", request.message(),
                "createdAt", createdAt);

        await(document.set(Objects.requireNonNull(data)));

        return new MessageInfo(document.getId(), request.name(),
                request.message(), createdAt);
    }

    public MessageInfo findMessage(String id) {
        if (!id.matches("[A-Za-z0-9]{20}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "이 예제에서 생성한 20자리 문서 ID를 입력하세요.");
        }
        DocumentSnapshot document = await(requireFirestore()
                .collection("messages").document(id).get());
        if (!document.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "메시지를 찾을 수 없습니다.");
        }
        return new MessageInfo(document.getId(), document.getString("name"),
                document.getString("message"), document.getString("createdAt"));
    }

    public UserInfo findUser(String uid) {
        if (uid.isBlank() || uid.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "UID는 1~128자로 입력하세요.");
        }
        try {
            UserRecord user = FirebaseAuth.getInstance(requireApp()).getUser(uid);
            return new UserInfo(user.getUid(), user.getEmail(), user.getDisplayName());
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Firebase 사용자를 찾을 수 없습니다.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Firebase 사용자 조회에 실패했습니다. 프로젝트와 권한을 확인하세요.");
        }
    }

    private <T> T await(ApiFuture<T> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Firebase 요청 대기가 중단되었습니다.");
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "Firebase 응답 대기 시간이 초과되었습니다.");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof FirebaseMessagingException messaging) {
                MessagingErrorCode code = messaging.getMessagingErrorCode();
                if (code == MessagingErrorCode.INVALID_ARGUMENT
                        || code == MessagingErrorCode.UNREGISTERED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "FCM 토큰이나 알림 내용을 확인하세요.");
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Firebase 호출에 실패했습니다. 서비스 설정과 권한을 확인하세요.");
        }
    }

    public record MessageInfo(String id, String name, String message, String createdAt) {
    }

    public record UserInfo(String uid, String email, String displayName) {
    }
}
