// Refuses to act unless this head unit has what the tool talks to, whatever its firmware version:
// the OEM AutoContainer interface with the transaction codes Bridge sends (read from the AIDL stubs
// in this firmware's framework, so a change there refuses instead of calling the wrong method).
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.MessageDigest;

final class Compatibility {
    private Compatibility() {}

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

    /** Returns null when the interface matches, else a one-line reason. Prints one line per check via `log`. */
    static String verify(Log log) {
        if (android.os.Build.VERSION.SDK_INT < 29) return "API " + android.os.Build.VERSION.SDK_INT + " (needs 29 or later)";
        log.line("compat: firmware " + android.os.Build.DISPLAY);
        String why = transaction(Policy.STUB, "TRANSACTION_sendInfo2", Policy.TX_SEND_INFO2, log);
        if (why == null) why = transaction(Policy.STUB, "TRANSACTION_registerCallback", Policy.TX_REGISTER_CALLBACK, log);
        if (why == null) why = transaction(Policy.CALLBACK_STUB, "TRANSACTION_receivedInfo2", Policy.TX_RECEIVED_INFO2, log);
        return why;
    }

    private static String transaction(String stub, String name, int expected, Log log) {
        try {
            Field field = Class.forName(stub).getDeclaredField(name);
            field.setAccessible(true);
            int got = field.getInt(null);
            if (got != expected) return stub + "." + name + " = " + got + " (expected " + expected + ")";
            log.line("compat: ok " + stub + "." + name + " = " + got);
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return stub + "." + name + ": " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    interface Log { void line(String s); }
}
