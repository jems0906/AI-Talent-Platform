package com.aitalent.user;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@Validated
class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    AuthController(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        // Auto-generate tenantId if not provided (for new recruiters)
        String tenantId = req.tenantId() != null && !req.tenantId().isBlank() 
            ? req.tenantId().trim().toLowerCase(Locale.ROOT)
            : UUID.randomUUID().toString();
        
        if (userRepository.existsByTenantIdIgnoreCaseAndEmailIgnoreCase(tenantId, req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists in tenant");
        }
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID().toString());
        user.setTenantId(tenantId);
        user.setName(req.name());
        user.setEmail(req.email().trim().toLowerCase(Locale.ROOT));
        user.setPassword(req.password());
        user.setRole(req.role() != null && !req.role().isBlank() ? req.role() : "RECRUITER");
        user.setCreatedAt(Instant.now().toString());
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        UserEntity user = userRepository.findByTenantIdIgnoreCaseAndEmailIgnoreCase(req.tenantId(), req.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!Objects.equals(user.getPassword(), req.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return buildAuthResponse(user);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader(name = "Authorization", required = false) String authHeader) {
        Claims claims = jwtService.parseBearer(authHeader);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", claims.get("uid", String.class));
        body.put("tenantId", claims.get("tenantId", String.class));
        body.put("email", claims.getSubject());
        body.put("role", claims.get("role", String.class));
        body.put("name", claims.get("name", String.class));
        return ResponseEntity.ok(body);
    }

    private AuthResponse buildAuthResponse(UserEntity user) {
        String token = jwtService.generate(user);
        return new AuthResponse(token, user.getTenantId(), user.getRole(), user.getEmail(), user.getName());
    }
}

@org.springframework.stereotype.Service
class JwtService {

    private final SecretKey key;

    JwtService(@Value("${app.jwt.secret}") String secret) {
        String normalized = secret;
        if (normalized.length() < 32) {
            normalized = normalized + "-secure-key-padding-2026";
        }
        this.key = Keys.hmacShaKeyFor(normalized.getBytes(StandardCharsets.UTF_8));
    }

    String generate(UserEntity user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("tenantId", user.getTenantId())
                .claim("role", user.getRole())
                .claim("name", user.getName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60L * 60L * 8L)))
                .signWith(key)
                .compact();
    }

    Claims parseBearer(String authHeader) {
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

record RegisterRequest(
        String tenantId,
        @NotBlank String name,
        @Email String email,
        @NotBlank @Size(min = 8) String password,
        String role
) {}

record LoginRequest(
        @NotBlank String tenantId,
        @Email String email,
        @NotBlank String password
) {}

record AuthResponse(String token, String tenantId, String role, String email, String name) {}

@Entity
@Table(name = "app_users")
class UserEntity {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "role", nullable = false)
    private String role;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}

interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByTenantIdIgnoreCaseAndEmailIgnoreCase(String tenantId, String email);
    boolean existsByTenantIdIgnoreCaseAndEmailIgnoreCase(String tenantId, String email);
}
