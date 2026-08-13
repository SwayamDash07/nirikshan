package com.nirikshan.security;
import com.nirikshan.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
@Service public class JwtService {
 private final SecretKey key; private final long expiryMs;
 public JwtService(@Value("${nirikshan.auth.jwt-secret}") String secret,@Value("${nirikshan.auth.jwt-expiry-hours:24}") long hours,Environment environment){boolean production=java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");if(production&&(secret==null||secret.startsWith("change-this-demo-secret")||secret.length()<32))throw new IllegalStateException("NIRIKSHAN_JWT_SECRET must be a random value of at least 32 characters in prod");if(hours<=0)throw new IllegalStateException("nirikshan.auth.jwt-expiry-hours must be greater than zero");key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));expiryMs=hours*3600000;}
 public String create(User user){Instant now=Instant.now();return Jwts.builder().subject(user.getEmail()).claim("role",user.getRole().name()).issuedAt(Date.from(now)).expiration(Date.from(now.plusMillis(expiryMs))).signWith(key).compact();}
 public String email(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();}
}
