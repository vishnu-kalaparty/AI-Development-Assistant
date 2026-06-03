package com.aidevassistant.tools;

/**
 * Per-workflow sandbox prefix (relative to the global sandbox root).
 * Tool paths resolve against this prefix when set.
 */
public final class SandboxContext {

    private static final ThreadLocal<String> PREFIX = new ThreadLocal<>();

    private SandboxContext() {
    }

    public static void set(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            PREFIX.remove();
        } else {
            PREFIX.set(normalize(prefix));
        }
    }

    public static String get() {
        return PREFIX.get();
    }

    public static void clear() {
        PREFIX.remove();
    }

    public static Scope scope(String prefix) {
        return new Scope(prefix);
    }

    public static String normalize(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static final class Scope implements AutoCloseable {
        private final String previous;

        Scope(String prefix) {
            previous = get();
            set(prefix);
        }

        @Override
        public void close() {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }
}
