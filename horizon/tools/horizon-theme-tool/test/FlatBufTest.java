// Offline check (desktop JVM, flatbuffers-java on the classpath): the hand-written builder must be
// byte-identical to the real FlatBufferBuilder for every request the tool can send, equal to the
// bytes documented in RECUPERACAO.md, and the decoder must read what the OEM-side builder writes.
//   java -cp build/test:flatbuffers-java.jar FlatBufTest
import com.google.flatbuffers.FlatBufferBuilder;
import java.util.Arrays;
import java.util.Random;

public class FlatBufTest {
    static int failures;

    static byte[] reference(int cmd, int sub, int mode, String name) {   // same code as ClusterThemeProtocol.kt
        FlatBufferBuilder b = new FlatBufferBuilder(256);
        int str = name.isEmpty() ? 0 : b.createString(name);
        b.startTable(13);
        b.addInt(0, cmd, 0); b.addInt(1, sub, 0); b.addInt(2, mode, 0);
        if (str != 0) b.addOffset(7, str, 0);
        b.finish(b.endTable());
        return b.sizedByteArray();
    }

    static void check(String what, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) failures++; }

    static String hex(byte[] b) { StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format("%02x", x & 255)); return sb.toString(); }

    public static void main(String[] a) {
        check("query == documented", hex(FlatBuf.query()).equals("0c00000008000c0008000400080000000400000001000000"));
        check("restore == documented", hex(FlatBuf.restoreDefault()).equals("1000000000000a000c000800000004000a0000000100000005000000"));
        String example = Policy.NAME_PREFIX + "0123456789abcdef0123456789abcdef.rcc";
        check("load(example) matches real builder", Arrays.equals(FlatBuf.load(example), reference(5, 0, 0, example)));
        check("load(example) is 128 bytes", FlatBuf.load(example).length == 128);
        String dashcast = "Di3F_DynastyOceanTranslate_V0001_TranslateDashCast_0123456789abcdef0123456789abcdef.rcc";
        check("load(DashCast-style name) matches real builder", Arrays.equals(FlatBuf.request(5, 0, 0, dashcast), reference(5, 0, 0, dashcast)));
        check("query matches real builder", Arrays.equals(FlatBuf.query(), reference(1, 4, 0, "")));
        check("restore matches real builder", Arrays.equals(FlatBuf.restoreDefault(), reference(5, 0, 1, "")));
        Random rnd = new Random(7);
        boolean all = true;
        for (int i = 0; i < 2000; i++) {
            int cmd = rnd.nextInt(9), sub = rnd.nextInt(6), mode = rnd.nextInt(3);
            StringBuilder sb = new StringBuilder();
            int n = rnd.nextInt(3) == 0 ? 0 : 1 + rnd.nextInt(300);
            for (int k = 0; k < n; k++) sb.append((char) ('a' + rnd.nextInt(26)));
            all &= Arrays.equals(FlatBuf.request(cmd, sub, mode, sb.toString()), reference(cmd, sub, mode, sb.toString()));
        }
        check("2000 random requests identical to the real builder", all);
        check("name regex accepts the example", Policy.NAME.matcher(example).matches());
        check("name regex refuses the DashCast name", !Policy.NAME.matcher(dashcast).matches());

        // The decoder against a response built like the OEM side builds it (cmd 1001/sub 4, ints 2..6, strings 7..11).
        FlatBufferBuilder b = new FlatBufferBuilder(256);
        int[] s = new int[5];
        String[] str = {Policy.UI_VERSION, "", Policy.HOST_DIR + example, "", "default"};
        for (int i = 0; i < 5; i++) s[i] = b.createString(str[i]);
        b.startTable(13);
        b.addInt(0, 1001, 0); b.addInt(1, 4, 0);
        int[] ints = {1, 0, 0, 0, 0};
        for (int i = 0; i < 5; i++) if (ints[i] != 0) b.addInt(2 + i, ints[i], 0);
        for (int i = 0; i < 5; i++) if (!str[i].isEmpty()) b.addOffset(7 + i, s[i], 0);
        b.finish(b.endTable());
        FlatBuf.Response r = FlatBuf.decode(b.sizedByteArray());
        check("decode: cmd/sub", r.cmd == 1001 && r.sub == 4);
        check("decode: ints", Arrays.equals(r.ints, ints));
        check("decode: strings", Arrays.equals(r.strings, str));
        check("decode: selected(example)", r.selected(example));
        check("decode: isDefault false when custom", !r.isDefault());
        check("decode: ownsSelection", r.ownsSelection());
        // a default report
        b = new FlatBufferBuilder(256);
        String[] d = {Policy.UI_VERSION, "", "default", "", "default"};
        for (int i = 0; i < 5; i++) s[i] = b.createString(d[i]);
        b.startTable(13); b.addInt(0, 1001, 0); b.addInt(1, 4, 0); b.addInt(2, 1, 0); b.addInt(5, 1, 0);
        for (int i = 0; i < 5; i++) if (!d[i].isEmpty()) b.addOffset(7 + i, s[i], 0);
        b.finish(b.endTable());
        r = FlatBuf.decode(b.sizedByteArray());
        check("decode: factory default recognised", r.isDefault() && r.isFactoryConfigured());
        // ACK2 like DashCast's loadAckMatches
        b = new FlatBufferBuilder(256);
        int hp = b.createString(Policy.HOST_DIR + example);
        b.startTable(13); b.addInt(0, 1005, 0); b.addInt(2, 2, 0); b.addOffset(7, hp, 0); b.finish(b.endTable());
        r = FlatBuf.decode(b.sizedByteArray());
        check("decode: ACK2 matches the load", r.loadAck(example) && !r.isFailureAck());
        // ACK3 with RESOURCE_IS_SAME
        b = new FlatBufferBuilder(256);
        b.startTable(13); b.addInt(0, 1005, 0); b.addInt(2, 3, 0); b.addInt(4, 0x2330, 0); b.finish(b.endTable());
        r = FlatBuf.decode(b.sizedByteArray());
        check("decode: ACK3 is a failure with 0x2330", r.isFailureAck() && r.ints[2] == 0x2330 && Policy.errorName(0x2330).startsWith("RESOURCE_IS_SAME"));
        // hostile input must throw, never loop or read outside
        boolean safe = true;
        byte[] good = FlatBuf.load(example);
        for (int i = 0; i < 3000; i++) {
            byte[] m = good.clone();
            for (int k = 0; k < 1 + rnd.nextInt(4); k++) m[rnd.nextInt(m.length)] = (byte) rnd.nextInt(256);
            try { FlatBuf.decode(m); } catch (IllegalArgumentException expected) { } catch (RuntimeException other) { safe = false; System.out.println(other); }
        }
        check("3000 corrupted buffers: only IllegalArgumentException", safe);
        check("error table: 0x2332 GEAR / 0x2333 frequency", Policy.errorName(0x2332).startsWith("GEAR") && Policy.errorName(0x2333).startsWith("SET_SKIN_FREQUENCY"));
        System.out.println(failures == 0 ? "ALL PASS" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
