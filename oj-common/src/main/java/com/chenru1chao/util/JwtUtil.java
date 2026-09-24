package com.chenru1chao.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtUtil {

    public static String createJwt(String key, long ttlMillis, Map<String, Object> payload) {
        long expMillis = System.currentTimeMillis() + ttlMillis;
        Date expDate = new Date(expMillis);

        SecretKey secretKey = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder().expiration(expDate)
                .claims(payload)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public static Claims parseJwt(String key, String jwt) {
        SecretKey secretKey = Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser().
                verifyWith(secretKey)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }
}
