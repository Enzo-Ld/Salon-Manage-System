package com.bfsm.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bfsm.jwt")
public class JwtProperties {
    private String issuer;
    private String secret;
    private long accessTokenTtlSeconds;
}

