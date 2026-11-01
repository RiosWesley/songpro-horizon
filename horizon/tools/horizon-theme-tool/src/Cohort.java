// Refuses to act unless this is exactly the firmware the Horizon carrier was built and studied for.
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class Cohort {
    private Cohort() {}

    static String sha256(String path, long deadlineMs) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[1 << 16];
        try (InputStream in = new FileInputStream(path)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                if (System.currentTimeMillis() > deadlineMs) throw new IllegalStateException("deadline while hashing " + path);
                md.update(buf, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b & 255));
        return sb.toString();
    }

    /** Returns null when the cohort matches, else a one-line reason. Prints one line per file via `log`. */
    static String verify(boolean heavy, long deadlineMs, Log log) {
        if (android.os.Build.VERSION.SDK_INT != 29) return "API " + android.os.Build.VERSION.SDK_INT + " (expected 29)";
        if (!Policy.FINGERPRINT.equals(android.os.Build.FINGERPRINT)) return "build fingerprint differs: " + android.os.Build.FINGERPRINT;
        log.line("cohort: fingerprint ok");
        List<Map<String, String>> groups = new ArrayList<>();
        groups.add(Policy.COHORT);
        if (heavy) groups.add(Policy.COHORT_HEAVY);
        for (Map<String, String> group : groups) {
            for (Map.Entry<String, String> e : group.entrySet()) {
                String path = e.getKey(), want = e.getValue();
                try {
                    String got = sha256(path, deadlineMs);
                    if (!got.equals(want)) return path + " SHA-256 " + got + " != " + want;
                    log.line("cohort: ok " + path);
                } catch (Exception ex) {
                    return path + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
                }
            }
        }
        return null;
    }

    interface Log { void line(String s); }
}
