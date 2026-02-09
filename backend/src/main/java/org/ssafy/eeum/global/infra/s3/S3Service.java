package org.ssafy.eeum.global.infra.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.ssafy.eeum.global.error.exception.CustomException;
import org.ssafy.eeum.global.error.model.ErrorCode;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    
    private final Map<String, CachedUrl> urlCache = new ConcurrentHashMap<>();

    private static class CachedUrl {
        final String url;
        final long expiryTime;

        CachedUrl(String url, long expiryTime) {
            this.url = url;
            this.expiryTime = expiryTime;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expiryTime;
        }
    }
    
    public String uploadFile(MultipartFile file, String dirName) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String fileName = dirName + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("S3 파일 업로드 성공: {}", fileName);
            return fileName;
        } catch (IOException e) {
            log.error("S3 파일 업로드 중 IO 에러 발생: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public String uploadImageFromUrl(String imageUrl, String dirName) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        try {
            
            URL url = java.net.URI.create(imageUrl).toURL();

            
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setConnectTimeout(5000); 
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                log.error("Kakao image download failed. HTTP Code: {}, URL: {}", responseCode, imageUrl);
                return null;
            }

            String originalFileName = "kakao_profile.jpg";
            String fileName = dirName + "/" + UUID.randomUUID() + "-" + originalFileName;

            
            String contentType = connection.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "image/jpeg";
            }

            try (InputStream inputStream = connection.getInputStream()) {
                byte[] bytes = inputStream.readAllBytes();
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(fileName)
                        .contentType(contentType)
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));
            }

            log.info("S3 URL 이미지 업로드 성공: {}", fileName);
            return fileName; 
        } catch (IOException e) {
            log.error("URL 이미지 업로드 실패: {}", e.getMessage());
            return null;
        }
    }

    public void deleteFile(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return;
        }

        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("S3 파일 삭제 성공: {}", fileKey);
        } catch (Exception e) {
            log.error("S3 파일 삭제 중 에러 발생: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public String generatePresignedUrl(String fileName, String contentType) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileName)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    public String getPresignedUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        
        CachedUrl cached = urlCache.get(key);
        if (cached != null && cached.isValid()) {
            return cached.url;
        }

        
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10)) 
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest);
        String newUrl = presignedGetObjectRequest.url().toString();

        
        long expiryTime = System.currentTimeMillis() + Duration.ofMinutes(9).toMillis();
        urlCache.put(key, new CachedUrl(newUrl, expiryTime));

        return newUrl;
    }
}