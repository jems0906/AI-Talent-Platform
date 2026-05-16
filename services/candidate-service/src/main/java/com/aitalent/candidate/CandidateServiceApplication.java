package com.aitalent.candidate;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@SpringBootApplication
public class CandidateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CandidateServiceApplication.class, args);
    }

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    S3Client s3Client(
            @Value("${storage.s3.region:us-east-1}") String region,
            @Value("${storage.s3.endpoint:}") String endpoint,
            @Value("${storage.s3.access-key:}") String accessKey,
            @Value("${storage.s3.secret-key:}") String secretKey,
            @Value("${storage.s3.path-style-access:false}") boolean pathStyleAccess
    ) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build());

        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
            );
        }
        return builder.build();
    }
}

@RestController
@RequestMapping("/api/candidates")
@CrossOrigin(origins = "*")
@Validated
class CandidateController {

    private final RestTemplate restTemplate;
    private final String aiUrl;
    private final String notificationUrl;
    private final CandidateJwtService candidateJwtService;
    private final CandidateRepository candidateRepository;
    private final ResumeStorageService resumeStorageService;
    private final ResumeTextExtractionService resumeTextExtractionService;

    CandidateController(
            RestTemplate restTemplate,
            @Value("${services.ai-url}") String aiUrl,
            @Value("${services.notification-url}") String notificationUrl,
            CandidateJwtService candidateJwtService,
            CandidateRepository candidateRepository,
            ResumeStorageService resumeStorageService,
            ResumeTextExtractionService resumeTextExtractionService
    ) {
        this.restTemplate = restTemplate;
        this.aiUrl = aiUrl;
        this.notificationUrl = notificationUrl;
        this.candidateJwtService = candidateJwtService;
        this.candidateRepository = candidateRepository;
        this.resumeStorageService = resumeStorageService;
        this.resumeTextExtractionService = resumeTextExtractionService;
    }

    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CandidateResponse apply(
            @RequestParam String tenantId,
            @RequestParam String name,
            @RequestParam @Email String email,
            @RequestParam String jobId,
            @RequestPart("resume") MultipartFile resume
    ) throws IOException {
        if (resume.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume is required");
        }
        String id = UUID.randomUUID().toString();
        String original = StringUtils.hasText(resume.getOriginalFilename()) ? resume.getOriginalFilename() : "resume.txt";
        byte[] resumeBytes = resume.getBytes();
        String resumePath = resumeStorageService.storeResume(tenantId, id, original, resumeBytes);
        String resumeText = resumeTextExtractionService.extractText(original, resumeBytes);

        CandidateEntity candidate = new CandidateEntity();
        candidate.setId(id);
        candidate.setTenantId(tenantId.trim().toLowerCase(Locale.ROOT));
        candidate.setJobId(jobId);
        candidate.setName(name);
        candidate.setEmail(email);
        candidate.setResumePath(resumePath);
        candidate.setResumeText(resumeText);
        candidate.setStatus("APPLIED");
        candidate.setScore(0.0);
        candidate.setCreatedAt(Instant.now().toString());
        candidateRepository.save(candidate);
        return CandidateResponse.from(candidate);
    }

    @GetMapping
        public List<CandidateResponse> list(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(name = "Authorization", required = false) String authHeader
    ) {
        candidateJwtService.authorizeRecruiter(authHeader, tenantId);
        return candidateRepository.findByTenantIdIgnoreCaseOrderByScoreDescCreatedAtDesc(tenantId)
            .stream()
            .map(CandidateResponse::from)
            .toList();
    }

    @PostMapping("/{candidateId}/rank")
        public CandidateResponse rank(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @PathVariable String candidateId,
            @RequestBody RankRequest rankRequest
    ) throws IOException {
        candidateJwtService.authorizeRecruiter(authHeader, tenantId);
        CandidateEntity candidate = findCandidate(tenantId, candidateId);
        String resumeText = candidate.getResumeText();
        if (!StringUtils.hasText(resumeText)) {
            byte[] resumeBytes = resumeStorageService.readResume(candidate.getResumePath());
            resumeText = resumeTextExtractionService.extractText(candidate.getResumePath(), resumeBytes);
            candidate.setResumeText(resumeText);
            candidateRepository.save(candidate);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("jobDescription", rankRequest.jobDescription());
        payload.put("resumeText", resumeText);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            aiUrl + "/rank",
            Objects.requireNonNull(HttpMethod.POST),
            new HttpEntity<>(payload),
            new ParameterizedTypeReference<>() {
            }
        );
        Map<String, Object> body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI ranking service unavailable");
        }

        Object scoreObj = body.get("score");
        double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0;
        candidate.setScore(score);
        candidate.setStatus("SCREENED");
        candidateRepository.save(candidate);
        return CandidateResponse.from(candidate);
    }

    @PostMapping("/{candidateId}/invite")
    public Map<String, Object> invite(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @PathVariable String candidateId,
            @RequestBody InviteRequest request
    ) {
        candidateJwtService.authorizeRecruiter(authHeader, tenantId);
        CandidateEntity candidate = findCandidate(tenantId, candidateId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("to", candidate.getEmail());
        payload.put("subject", request.subject());
        payload.put("body", request.message());

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            notificationUrl + "/api/notifications/email",
            Objects.requireNonNull(HttpMethod.POST),
            new HttpEntity<>(payload),
            new ParameterizedTypeReference<>() {
            }
        );

        candidate.setStatus("INTERVIEW_INVITED");
        candidateRepository.save(candidate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidate", CandidateResponse.from(candidate));
        result.put("notification", response.getBody());
        return result;
    }

    @DeleteMapping("/test-data")
    public Map<String, Object> clearTestData(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader(name = "Authorization", required = false) String authHeader
    ) {
        candidateJwtService.authorizeRecruiter(authHeader, tenantId);

        int deletedCount = candidateRepository.deleteTestCandidatesForTenant(
                tenantId,
                "@test.local",
                "smoke"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", tenantId);
        result.put("deletedCount", deletedCount);
        return result;
    }

    private CandidateEntity findCandidate(String tenantId, String id) {
        return candidateRepository.findByTenantIdIgnoreCaseAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
    }
}

@Service
class ResumeStorageService {

    private final String provider;
    private final String basePath;
    private final String bucket;
    private final S3Client s3Client;

    ResumeStorageService(
            @Value("${storage.provider:local}") String provider,
            @Value("${storage.base-path:./storage}") String basePath,
            @Value("${storage.s3.bucket:}") String bucket,
            S3Client s3Client
    ) {
        this.provider = provider;
        this.basePath = basePath;
        this.bucket = bucket;
        this.s3Client = s3Client;
    }

    String storeResume(String tenantId, String candidateId, String originalFilename, byte[] resumeBytes) throws IOException {
        String normalizedTenant = tenantId.trim().toLowerCase(Locale.ROOT);
        String safeFilename = sanitizeFilename(originalFilename);
        String storedName = candidateId + "-" + safeFilename;

        if ("s3".equalsIgnoreCase(provider)) {
            ensureBucketConfigured();
            String objectKey = normalizedTenant + "/" + storedName;
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey);

            String contentType = URLConnection.guessContentTypeFromName(safeFilename);
            if (StringUtils.hasText(contentType)) {
                requestBuilder.contentType(contentType);
            }

            s3Client.putObject(requestBuilder.build(), software.amazon.awssdk.core.sync.RequestBody.fromBytes(resumeBytes));
            return "s3://" + bucket + "/" + objectKey;
        }

        Path tenantDir = Path.of(basePath).resolve(normalizedTenant).normalize();
        Files.createDirectories(tenantDir);
        Path resumePath = tenantDir.resolve(storedName);
        Files.write(resumePath, resumeBytes);
        return resumePath.toString();
    }

    byte[] readResume(String resumeLocation) throws IOException {
        if (resumeLocation.startsWith("s3://")) {
            S3Location location = parseS3Location(resumeLocation);
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(location.bucket()).key(location.key()).build()
            );
            return response.asByteArray();
        }
        return Files.readAllBytes(Path.of(resumeLocation));
    }

    private void ensureBucketConfigured() {
        if (!StringUtils.hasText(bucket)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 bucket is not configured");
        }
    }

    private S3Location parseS3Location(String resumeLocation) {
        String withoutPrefix = resumeLocation.substring("s3://".length());
        int slashIndex = withoutPrefix.indexOf('/');
        if (slashIndex < 0) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid S3 resume path");
        }
        return new S3Location(withoutPrefix.substring(0, slashIndex), withoutPrefix.substring(slashIndex + 1));
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record S3Location(String bucket, String key) {}
}

@Service
class ResumeTextExtractionService {

    private static final int MAX_EXTRACTED_TEXT_LENGTH = 20000;

    private final Tika tika = new Tika();

    String extractText(String filename, byte[] resumeBytes) {
        if (resumeBytes.length == 0) {
            return "";
        }

        try {
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            return normalizeExtractedText(tika.parseToString(new ByteArrayInputStream(resumeBytes), metadata));
        } catch (Exception ex) {
            return normalizeExtractedText(new String(resumeBytes, StandardCharsets.UTF_8));
        }
    }

    private String normalizeExtractedText(String rawText) {
        String normalized = rawText == null ? "" : rawText.replaceAll("\\s+", " ").trim();
        if (normalized.length() > MAX_EXTRACTED_TEXT_LENGTH) {
            return normalized.substring(0, MAX_EXTRACTED_TEXT_LENGTH);
        }
        return normalized;
    }
}

@org.springframework.stereotype.Service
class CandidateJwtService {

    private final SecretKey key;

    CandidateJwtService(@Value("${app.jwt.secret}") String secret) {
        String normalized = secret;
        if (normalized.length() < 32) {
            normalized = normalized + "-secure-key-padding-2026";
        }
        this.key = Keys.hmacShaKeyFor(normalized.getBytes(StandardCharsets.UTF_8));
    }

    void authorizeRecruiter(String authHeader, String expectedTenant) {
        Claims claims = parseBearer(authHeader);
        String tenantId = claims.get("tenantId", String.class);
        String role = claims.get("role", String.class);
        if (!Objects.equals(expectedTenant, tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant mismatch");
        }
        if (!"RECRUITER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Recruiter role required");
        }
    }

    private Claims parseBearer(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        String token = authHeader.substring("Bearer ".length());
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }
}

record RankRequest(@NotBlank String jobDescription) {}
record InviteRequest(@NotBlank String subject, @NotBlank String message) {}

record CandidateResponse(
        String id,
        String tenantId,
        String jobId,
        String name,
        String email,
        String resumePath,
        String status,
        double score,
        String createdAt
) {
    static CandidateResponse from(CandidateEntity c) {
        return new CandidateResponse(
                c.getId(),
                c.getTenantId(),
                c.getJobId(),
                c.getName(),
                c.getEmail(),
                c.getResumePath(),
                c.getStatus(),
                c.getScore(),
                c.getCreatedAt()
        );
    }
}

@Entity
@Table(name = "candidates")
class CandidateEntity {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "resume_path", nullable = false)
    private String resumePath;

    @Column(name = "resume_text")
    private String resumeText;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "created_at", nullable = false)
    private String createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getResumePath() {
        return resumePath;
    }

    public void setResumePath(String resumePath) {
        this.resumePath = resumePath;
    }

    public String getResumeText() {
        return resumeText;
    }

    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}

interface CandidateRepository extends JpaRepository<CandidateEntity, String> {
    List<CandidateEntity> findByTenantIdIgnoreCaseOrderByScoreDescCreatedAtDesc(String tenantId);
    Optional<CandidateEntity> findByTenantIdIgnoreCaseAndId(String tenantId, String id);

        @Modifying
        @Transactional
        @Query("""
                        delete from CandidateEntity c
                        where lower(c.tenantId) = lower(:tenantId)
                            and (
                                lower(c.email) like lower(concat('%', :emailSuffix))
                                or lower(c.name) like lower(concat(:namePrefix, '%'))
                            )
                        """)
        int deleteTestCandidatesForTenant(
                        @Param("tenantId") String tenantId,
                        @Param("emailSuffix") String emailSuffix,
                        @Param("namePrefix") String namePrefix
        );
}
