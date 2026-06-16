package com.bfsm.common;

import com.bfsm.security.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {
    public static SecurityUser currentUserOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityUser u)) {
            throw new BizException(401, "UNAUTHORIZED");
        }
        return u;
    }
}

