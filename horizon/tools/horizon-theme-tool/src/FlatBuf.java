// Minimal FlatBuffers writer/reader for the OEM cluster request (PadToClusterReq) and response
// (ClusterToPadRes). Hand-written so the device needs no flatbuffers library.
//
// The wire contract (13 table slots; 0 cmd, 1 sub, 2 mode, 7 first string; responses: ints in
// slots 2..6, strings in slots 7..11) and the decoder's bounds checks follow the DashCast project's
// ClusterThemeProtocol.kt (https://github.com/Kiroha/byd-dashcast, MIT licence, (c) its authors).
// The builder is a port of the algorithm of Google's flatbuffers-java (Apache-2.0) restricted to
// what one table with int and string fields needs; FlatBufTest compares it byte for byte with the
// real flatbuffers-java.
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class FlatBuf {
    private FlatBuf() {}

    // ---- request ----------------------------------------------------------------------------
    static byte[] request(int cmd, int sub, int mode, String name) {
        Builder b = new Builder(256);
        int str = name.isEmpty() ? 0 : b.createString(name);
        b.startTable(13);
        b.addInt(0, cmd);
        b.addInt(1, sub);
        b.addInt(2, mode);
        if (str != 0) b.addOffset(7, str);
        b.finish(b.endTable());
        return b.sizedByteArray();
    }

    static byte[] query() { return request(1, 4, 0, ""); }
    static byte[] restoreDefault() { return request(5, 0, 1, ""); }
    static byte[] load(String basename) { return request(5, 0, 0, basename); }

    /** Back-to-front builder; same layout rules as flatbuffers-java (little endian, aligned). */
    static final class Builder {
        private byte[] bb;
        private int space;
        private int minalign = 1;
        private int[] vtable;
        private int objectStart;

        Builder(int initial) { bb = new byte[initial]; space = initial; }

        private int offset() { return bb.length - space; }

        private void grow() {
            byte[] n = new byte[bb.length * 2];
            int used = offset();
            System.arraycopy(bb, space, n, n.length - used, used);   // data lives at the END of the buffer
            space = n.length - used;
            bb = n;
        }

        private void pad(int n) { for (int i = 0; i < n; i++) bb[--space] = 0; }

        private void prep(int size, int additional) {
            if (size > minalign) minalign = size;
            int alignSize = ((~(offset() + additional)) + 1) & (size - 1);
            while (space < alignSize + size + additional) grow();
            pad(alignSize);
        }

        private void putInt(int x) {
            space -= 4;
            bb[space] = (byte) x; bb[space + 1] = (byte) (x >> 8); bb[space + 2] = (byte) (x >> 16); bb[space + 3] = (byte) (x >> 24);
        }

        private void putShort(int x) { space -= 2; bb[space] = (byte) x; bb[space + 1] = (byte) (x >> 8); }

        private void addShort(int x) { prep(2, 0); putShort(x); }

        private void addInt32(int x) { prep(4, 0); putInt(x); }

        int createString(String s) {
            byte[] u = s.getBytes(StandardCharsets.UTF_8);
            prep(1, 0); bb[--space] = 0;                       // terminator
            prep(4, u.length); prep(1, u.length);               // startVector(1, n, 1)
            space -= u.length;
            System.arraycopy(u, 0, bb, space, u.length);
            putInt(u.length);                                    // endVector
            return offset();
        }

        void startTable(int slots) { vtable = new int[slots]; objectStart = offset(); }

        void addInt(int slot, int value) {                      // default 0 is omitted
            if (value != 0) { addInt32(value); vtable[slot] = offset(); }
        }

        void addOffset(int slot, int off) {
            if (off != 0) { prep(4, 0); putInt(offset() - off + 4); vtable[slot] = offset(); }
        }

        int endTable() {
            addInt32(0);                                         // placeholder for the vtable offset
            int tableLoc = offset();
            int i = vtable.length - 1;
            while (i >= 0 && vtable[i] == 0) i--;
            int trimmed = i + 1;
            for (; i >= 0; i--) addShort(vtable[i] != 0 ? tableLoc - vtable[i] : 0);
            addShort(tableLoc - objectStart);                    // table size
            addShort((trimmed + 2) * 2);                         // vtable size
            int vtStart = offset();
            int pos = bb.length - tableLoc;
            int rel = vtStart - tableLoc;
            bb[pos] = (byte) rel; bb[pos + 1] = (byte) (rel >> 8); bb[pos + 2] = (byte) (rel >> 16); bb[pos + 3] = (byte) (rel >> 24);
            return tableLoc;
        }

        void finish(int root) { prep(minalign, 4); prep(4, 0); putInt(offset() - root + 4); }

        byte[] sizedByteArray() { return Arrays.copyOfRange(bb, space, bb.length); }
    }

    // ---- response ---------------------------------------------------------------------------
    static final class Response {
        final int cmd, sub;
        final int[] ints;          // slots 2..6
        final String[] strings;    // slots 7..11

        Response(int cmd, int sub, int[] ints, String[] strings) {
            this.cmd = cmd; this.sub = sub; this.ints = ints; this.strings = strings;
        }

        boolean isSelectionReport() { return cmd == 1001 && sub == 4; }

        boolean isFactoryConfigured() {
            return isSelectionReport() && ints[0] == 1 && (ints[3] == 0 || ints[3] == 1)
                    && Policy.UI_VERSION.equals(strings[0]) && "default".equals(strings[2]) && "default".equals(strings[4]);
        }

        /** Native Qt path property empty (source 1) and IVI selection default. Not a pixel proof. */
        boolean isDefault() { return isFactoryConfigured() && ints[3] == 1; }

        boolean selected(String name) {
            return isSelectionReport() && ints[0] == 1 && (ints[3] == 0 || ints[3] == 1) && Policy.UI_VERSION.equals(strings[0])
                    && strings[2].equals(Policy.HOST_DIR + name);
        }

        boolean ownsSelection() {
            return isSelectionReport() && strings[2].startsWith(Policy.HOST_DIR + Policy.NAME_PREFIX);
        }

        boolean loadAck(String name) {
            return cmd == 1005 && sub == 0 && Arrays.equals(ints, new int[] {2, 0, 0, 0, 0})
                    && strings[0].equals(Policy.HOST_DIR + name);
        }

        boolean isFailureAck() { return cmd == 1005 && sub == 0 && ints[0] == 3; }

        @Override public String toString() {
            return "cmd=" + cmd + " sub=" + sub + " ints=" + Arrays.toString(ints) + " strings=" + Arrays.toString(strings);
        }
    }

    /** Bounds-checked reader for an unsolicited OEM message. Throws IllegalArgumentException. */
    static Response decode(byte[] bytes) {
        require(bytes.length >= 8 && bytes.length <= 65536, "invalid response size");
        int root = i32(bytes, 0);
        span(bytes, root, 4);
        long vtl = (long) root - i32(bytes, root);
        require(vtl >= 0 && vtl <= Integer.MAX_VALUE, "bad vtable");
        int vt = (int) vtl;
        int len = u16(bytes, vt), obj = u16(bytes, vt + 2);
        require(len >= 4 && len <= 256 && len % 2 == 0 && obj >= 4, "bad vtable header");
        span(bytes, vt, len); span(bytes, root, obj);
        int[] ints = new int[5];
        String[] strings = new String[5];
        for (int k = 0; k < 5; k++) ints[k] = scalar(bytes, root, vt, len, obj, 2 + k);
        for (int k = 0; k < 5; k++) strings[k] = string(bytes, root, vt, len, obj, 7 + k);
        return new Response(scalar(bytes, root, vt, len, obj, 0), scalar(bytes, root, vt, len, obj, 1), ints, strings);
    }

    private static int field(byte[] b, int root, int vt, int len, int obj, int slot) {
        int at = 4 + slot * 2;
        if (at + 2 > len) return 0;
        int off = u16(b, vt + at);
        if (off == 0) return 0;
        require(off >= 4 && off + 4 <= obj, "bad field offset");
        return root + off;
    }

    private static int scalar(byte[] b, int root, int vt, int len, int obj, int slot) {
        int f = field(b, root, vt, len, obj, slot);
        return f == 0 ? 0 : i32(b, f);
    }

    private static String string(byte[] b, int root, int vt, int len, int obj, int slot) {
        int f = field(b, root, vt, len, obj, slot);
        if (f == 0) return "";
        int rel = i32(b, f);
        require(rel >= 4, "bad string offset");
        long p = (long) f + rel;
        require(p <= Integer.MAX_VALUE, "bad string offset");
        int n = i32(b, (int) p);
        require(n >= 0 && n <= 4096, "bad string length");
        span(b, (int) p + 4, n + 1);
        require(b[(int) p + 4 + n] == 0, "string not terminated");
        return new String(b, (int) p + 4, n, StandardCharsets.UTF_8);
    }

    private static void require(boolean ok, String why) { if (!ok) throw new IllegalArgumentException(why); }

    private static void span(byte[] b, int p, int n) { require(p >= 0 && n >= 0 && (long) p + n <= b.length, "out of range"); }

    private static int i32(byte[] b, int p) {
        span(b, p, 4);
        return (b[p] & 255) | (b[p + 1] & 255) << 8 | (b[p + 2] & 255) << 16 | (b[p + 3] & 255) << 24;
    }

    private static int u16(byte[] b, int p) { span(b, p, 2); return (b[p] & 255) | (b[p + 1] & 255) << 8; }
}
