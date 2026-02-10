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
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Amazon S3를 통한 파일 업로드, 조회용 Presigned URL 생성 등의 기능을 제공하는 서비스 클래스입니다.
 * 
 * @summary S3 파일 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private static final Duration PRESIGNED_URL_DURATION = Duration.ofMinutes(10);
    private static final Duration CACHE_DURATION = Duration.ofMinutes(9);
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    // Presigned URL 캐시 (Key: fileKey, Value: CachedUrl)
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

    /**
     * 파일을 S3에 업로드합니다.
     * 
     * @summary S3 파일 업로드
     * @param file    업로드할 멀티파트 파일
     * @param dirName 업로드 경로 상위 디렉토리 명
     * @return 저장된 파일의 S3 Key (실패 시 null)
     */
    public String uploadFile(MultipartFile file, String dirName) {
        if (isInvalidFile(file)) {
            return null;
        }

        String fileName = generateUniqueFileName(dirName, file.getOriginalFilename());

        try {
            PutObjectRequest putObjectRequest = createPutObjectRequest(fileName, file.getContentType());
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("S3 파일 업로드 성공: {}", fileName);
            return fileName;
        } catch (IOException e) {
            log.error("S3 파일 업로드 중 에러 발생: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 외부 URL의 이미지를 다운로드하여 S3에 저장합니다.
     * 
     * @summary 외부 이미지 URL 업로드
     * @param imageUrl 원본 이미지 URL
     * @param dirName  저장할 디렉토리 명
     * @return 저장된 파일의 S3 Key (실패 시 null)
     */
    public String uploadImageFromUrl(String imageUrl, String dirName) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        try {
            HttpURLConnection connection = createHttpConnection(imageUrl);
            if (connection.getResponseCode() != 200) {
                log.error("이미지 다운로드 실패. HTTP Code: {}, URL: {}", connection.getResponseCode(), imageUrl);
                return null;
            }

            String fileName = dirName + "/" + UUID.randomUUID() + "-image.jpg";
            String contentType = connection.getContentType() != null ? connection.getContentType() : "image/jpeg";

            try (InputStream inputStream = connection.getInputStream()) {
                byte[] bytes = inputStream.readAllBytes();
                s3Client.putObject(createPutObjectRequest(fileName, contentType), RequestBody.fromBytes(bytes));
            }

            log.info("S3 URL 이미지 업로드 성공: {}", fileName);
            return fileName;
        } catch (IOException e) {
            log.error("URL 이미지 업로드 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * S3에서 파일을 삭제합니다.
     * 
     * @summary S3 파일 삭제
     * @param fileKey 삭제할 파일의 S3 Key
     */
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

    /**
     * 업로드용 Presigned URL을 생성합니다.
     * 
     * @summary 업로드용 Presigned URL 생성
     * @param fileName    저장될 파일명
     * @param contentType 파일 타입
     * @return 생성된 Presigned URL (10분 유효)
     */
    public String generatePresignedUrl(String fileName, String contentType) {
        PutObjectRequest objectRequest = createPutObjectRequest(fileName, contentType);
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_DURATION)
                .putObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    /**
     * 특정 파일에 대한 조회용 Presigned URL을 가져옵니다 (캐시 적용).
     * 
     * @summary 조회용 Presigned URL 조회
     * @param key S3 객체 키
     * @return Presigned URL (유효기간 만료 전까지 캐시됨)
     */
    public String getPresignedUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        CachedUrl cached = urlCache.get(key);
        if (cached != null && cached.isValid()) {
            return cached.url;
        }

        return generateAndCachePresignedUrl(key);
    }

    // --- Helper Methods ---

    private boolean isInvalidFile(MultipartFile file) {
        return file == null || file.isEmpty();
    }

    private String generateUniqueFileName(String dirName, String originalFileName) {
        String cleanDirName = (dirName != null && !dirName.isBlank()) ? dirName : "uploads";
        return cleanDirName + "/" + UUID.randomUUID() + "-" + (originalFileName != null ? originalFileName : "file");
    }

    private PutObjectRequest createPutObjectRequest(String key, String contentType) {
        return PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
    }

    private HttpURLConnection createHttpConnection(String imageUrl) throws IOException {
        URL url = URI.create(imageUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(CONNECTION_TIMEOUT);
        return connection;
    }

    private String generateAndCachePresignedUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_DURATION)
                .getObjectRequest(getObjectRequest)
                .build();

        String newUrl = s3Presigner.presignGetObject(presignRequest).url().toString();
        long expiryTime = System.currentTimeMillis() + CACHE_DURATION.toMillis();
        urlCache.put(key, new CachedUrl(newUrl, expiryTime));

        return newUrl;
    }
}
