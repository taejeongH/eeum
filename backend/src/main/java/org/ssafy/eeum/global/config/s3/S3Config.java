package org.ssafy.eeum.global.config.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Amazon S3(Simple Storage Service) 연동을 위한 클라이언트 및 Presigner 설정 클래스입니다.
 * 
 * @summary Amazon S3 서비스 설정 클래스
 */
@Configuration
public class S3Config {

        @Value("${spring.cloud.aws.credentials.access-key}")
        private String accessKey;

        @Value("${spring.cloud.aws.credentials.secret-key}")
        private String secretKey;

        @Value("${spring.cloud.aws.region.static}")
        private String region;

        /**
         * S3 객체 업로드 및 관리를 위한 S3Client를 생성하여 Bean으로 등록합니다.
         * 
         * @summary S3 Client Bean 생성
         * @return 구성된 S3Client 객체
         */
        @Bean
        public S3Client s3Client() {
                return S3Client.builder()
                                .region(Region.of(region))
                                .credentialsProvider(StaticCredentialsProvider.create(
                                                AwsBasicCredentials.create(accessKey, secretKey)))
                                .build();
        }

        /**
         * S3 Presigned URL 생성을 위한 S3Presigner를 생성하여 Bean으로 등록합니다.
         * 
         * @summary S3 Presigned URL Bean 생성
         * @return 구성된 S3Presigner 객체
         */
        @Bean
        public S3Presigner s3Presigner() {
                return S3Presigner.builder()
                                .region(Region.of(region))
                                .credentialsProvider(StaticCredentialsProvider.create(
                                                AwsBasicCredentials.create(accessKey, secretKey)))
                                .build();
        }
}
