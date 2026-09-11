package com.mmcove.agent.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * 本地 JWT 签发/校验（HS256，jjwt）。
 *
 * <p>自建用户体系不再依赖外部 auth-center，登录成功后由此处签发 access/refresh token。
 * claims：sub=userId，name=用户名，role=角色等级，type=access|refresh。
 */
@Slf4j
@Component
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtService(
            @Value("${mmcove.security.jwt.secret}") String secret,
            @Value("${mmcove.security.jwt.access-ttl-minutes:120}") long accessTtlMinutes,
            @Value("${mmcove.security.jwt.refresh-ttl-days:7}") long refreshTtlDays) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥 >= 32 字节；不足则补零（开发便利，生产请配置足够长的随机 secret）
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
            log.warn("[JWT] secret 长度不足 32 字节，已补零至 32 字节，生产环境请配置强随机 secret");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtlMs = accessTtlMinutes * 60_000L;
        this.refreshTtlMs = refreshTtlDays * 24L * 60 * 60_000L;
    }

    public String generateAccessToken(String userId, String username, int role) {
        return build(userId, username, role, "access", accessTtlMs);
    }

    public String generateRefreshToken(String userId, String username, int role) {
        return build(userId, username, role, "refresh", refreshTtlMs);
    }

    private String build(String userId, String username, int role, String type, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("name", username)
                .claim("role", role)
                .claim("type", type)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    /** 校验并解析 token；非法/过期会抛 JwtException。 */
    public Claims parseAndVerify(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
