package com.meld.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Canonicalization rules used at the API boundary.
 *
 * <p>Emails are compared case-insensitively (DATABASE.md V10 functional unique
 * index), and slugs are restricted to lowercase ASCII letters, digits and
 * hyphens so they are safe in URLs and stable identifiers.
 */
public final class Normalizers {

    private static final int MAX_SLUG_LENGTH = 80;

    private Normalizers() {}

    public static String email(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static String slug(String input) {
        if (input == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
        }
        return slug;
    }
}
