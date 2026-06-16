package com.bfsm.common;

public final class AvatarUrlUtils {
    private static final String AVATAR_STORAGE_PREFIX = "/uploads/avatars/";
    private static final String LEGACY_AVATAR_PREFIX = "/avatars/";

    private AvatarUrlUtils() {
    }

    public static String normalize(String avatarUrl) {
        if (avatarUrl == null) {
            return null;
        }

        String normalized = avatarUrl.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("data:")) {
            return normalized;
        }
        if (normalized.startsWith(AVATAR_STORAGE_PREFIX)) {
            return normalized;
        }
        if (normalized.startsWith(LEGACY_AVATAR_PREFIX)) {
            return AVATAR_STORAGE_PREFIX + normalized.substring(LEGACY_AVATAR_PREFIX.length());
        }
        if (normalized.startsWith("avatars/")) {
            return AVATAR_STORAGE_PREFIX + normalized.substring("avatars/".length());
        }
        if (normalized.startsWith("uploads/avatars/")) {
            return "/" + normalized;
        }
        if (!normalized.contains("/")) {
            return AVATAR_STORAGE_PREFIX + normalized;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}
