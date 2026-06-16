package com.bfsm.common;

public final class PhoneUtils {
    private static final String MAINLAND_PHONE_PATTERN = "^1\\d{10}$";

    private PhoneUtils() {
    }

    public static String normalize(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean isValidMainlandPhone(String phone) {
        return phone != null && phone.matches(MAINLAND_PHONE_PATTERN);
    }

    public static void validateOrThrow(String phone) {
        if (phone == null) {
            return;
        }
        if (!isValidMainlandPhone(phone)) {
            throw new BizException(4310, "Phone number must be 11 digits and start with 1");
        }
    }
}
