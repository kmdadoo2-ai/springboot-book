package com.study.spring.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true")
public class FirebaseConfig {

    @Bean(destroyMethod = "delete")
    public FirebaseApp firebaseApp(
            @Value("${app.firebase.project-id}") String projectId) throws IOException {
        if (projectId.isBlank()) {
            throw new IllegalStateException("FIREBASE_PROJECT_ID를 설정하세요.");
        }
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projectId)
                .build();
        return FirebaseApp.initializeApp(options, "board-firebase");
    }

    @Bean(destroyMethod = "close")
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp);
    }
}
