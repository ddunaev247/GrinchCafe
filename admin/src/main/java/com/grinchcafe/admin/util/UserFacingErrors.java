package com.grinchcafe.admin.util;

import android.content.Context;

import com.grinchcafe.admin.R;

import java.util.Locale;

public final class UserFacingErrors {

    private UserFacingErrors() {
    }

    public static String format(Context context, Throwable error) {
        String msg = error == null ? null : error.getMessage();
        return format(context, msg);
    }

    public static String format(Context context, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return context.getString(R.string.error_connection);
        }
        String lower = raw.toLowerCase(Locale.getDefault());

        if (contains(lower, "timeout") || contains(lower, "timed out")) {
            return context.getString(R.string.error_timeout);
        }
        if (contains(lower, "failed to connect")
                || contains(lower, "connection refused")
                || contains(lower, "network is unreachable")
                || contains(lower, "no address associated")
                || contains(lower, "unknownhost")
                || contains(lower, "unable to resolve")) {
            return context.getString(R.string.error_connection);
        }
        if (contains(lower, "not_found") || contains(lower, "http 404")) {
            return context.getString(R.string.error_not_found);
        }
        if (contains(lower, "http 500")) {
            return context.getString(R.string.error_server);
        }
        if (contains(lower, "http 400")) {
            return context.getString(R.string.error_bad_request);
        }
        if (contains(lower, "http ")) {
            return context.getString(R.string.error_server);
        }
        return raw;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && needle != null && haystack.contains(needle);
    }
}
