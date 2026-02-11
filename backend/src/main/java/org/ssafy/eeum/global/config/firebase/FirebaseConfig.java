package org.ssafy.eeum.global.config.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Firebase Cloud Messaging(FCM) 서비스를 사용하기 위한 FirebaseApp 설정 클래스입니다.
 * 
 * @summary Firebase 연동 설정 클래스
 */
@Configuration
public class FirebaseConfig {

    @Value("${firebase.config-path}")
    private String configPath;

    /**
     * JSON 설정 파일을 기반으로 FirebaseApp을 초기화하여 Bean으로 등록합니다.
     * 
     * @summary FirebaseApp 초기화 및 등록
     * @return 초기화 완료된 FirebaseApp 객체
     * @throws IOException 설정 파일 접근 실패 시 발생
     */
    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        // 이미 초기화된 앱이 있는지 확인
        List<FirebaseApp> firebaseApps = FirebaseApp.getApps();
        if (firebaseApps != null && !firebaseApps.isEmpty()) {
            for (FirebaseApp app : firebaseApps) {
                if (app.getName().equals(FirebaseApp.DEFAULT_APP_NAME)) {
                    return app;
                }
            }
        }

        InputStream serviceAccount = getClass().getResourceAsStream(configPath);
        if (serviceAccount == null) {
            // 리소스를 찾지 못했을 경우 파일 시스템에서 시도
            serviceAccount = new FileInputStream(configPath);
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        return FirebaseApp.initializeApp(options);
    }
}
