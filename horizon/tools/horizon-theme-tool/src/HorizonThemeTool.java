// HorizonThemeTool: select / query / restore the cluster translate-RCC (theme carrier) on the BYD
// Song Pro 8.8" (DiLink 3.0, SX309). Runs as the adb shell user:
//
//   CLASSPATH=/data/local/tmp/horizon-theme-tool.dex app_process / HorizonThemeTool <command> ...
//
// Commands
//   query                                        read-only: compatibility check + the decoded OEM selection
//   load <basename> --sha256 <hex> --assert-parked [--replace-own]
//                                                select the carrier in /sdcard/.cluster/translate/;
//                                                --replace-own also over another Horizon carrier
//                                                (on SX309 `restore` answers RESOURCE_IS_SAME)
//   restore --assert-parked                      ask the OEM for its default selection (cmd 5, mode 1)
//   payloads                                     offline: print the request bytes (no device access)
//
// Rules: one pinned Binder connection and at most ONE mutating request per run; a timeout is
// AMBIGUOUS, never "failed" and never retried; every refusal happens before anything is sent.
// Exit codes: 0 settled, 2 ambiguous (observe, do not repeat), 3 refused before sending, 4 failed.
// The OEM protocol (type-8 FlatBuffer over AutoContainer) follows the DashCast project, MIT licence
// (https://github.com/Kiroha/byd-dashcast): ClusterThemeIviHelper.kt / ClusterThemeProtocol.kt /
// ClusterThemeIviExchange.kt.
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.List;

public final class HorizonThemeTool {
    static final int SETTLED = 0, AMBIGUOUS = 2, REFUSED = 3, FAILED = 4;
    private static final long TOTAL_MS = 150_000;
    private static final List<String> trace = new ArrayList<>();

    private HorizonThemeTool() {}

    static void line(String s) { String t = "HORIZON: " + s; trace.add(t); System.out.println(t); System.out.flush(); }

    private static class Refused extends Exception { Refused(String m) { super(m); } }

    public static void main(String[] args) {
        final long deadline = System.currentTimeMillis() + TOTAL_MS;
        Thread guard = new Thread(new Runnable() {
            @Override public void run() {
                try { while (System.currentTimeMillis() < deadline) Thread.sleep(100); } catch (InterruptedException ignored) { }
                System.out.println("HORIZON_RESULT={\"status\":\"AMBIGUOUS\",\"detail\":\"tool deadline; observe the panel, do not repeat\"}");
                Runtime.getRuntime().halt(AMBIGUOUS);
            }
        }, "deadline");
        guard.setDaemon(true);
        guard.start();
        int code;
        try {
            code = run(args, deadline);
        } catch (Refused r) {
            line("REFUSED: " + r.getMessage());
            result("REFUSED", r.getMessage());
            code = REFUSED;
        } catch (Throwable t) {
            line("FAILED: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            result("FAILED", t.getClass().getSimpleName() + ": " + t.getMessage());
            code = FAILED;
        }
        System.out.flush();
        Runtime.getRuntime().halt(code);
    }

    private static void result(String status, String detail) {
        System.out.println("HORIZON_RESULT={\"status\":\"" + status + "\",\"detail\":\"" + detail.replace("\\", "\\\\").replace("\"", "'") + "\"}");
    }

    private static boolean flag(String[] a, String f) { for (String s : a) if (s.equals(f)) return true; return false; }

    private static String option(String[] a, String f) {
        for (int i = 0; i < a.length - 1; i++) if (a[i].equals(f)) return a[i + 1];
        return null;
    }

    static int run(String[] args, long deadline) throws Exception {
        if (args.length == 0) throw new Refused("usage: query | load <basename> --sha256 <hex> --assert-parked [--replace-own] | restore --assert-parked | payloads");
        String cmd = args[0];
        if (cmd.equals("payloads")) { payloads(); return SETTLED; }
        if (!cmd.equals("query") && !cmd.equals("load") && !cmd.equals("restore")) throw new Refused("unknown command " + cmd);
        if (android.os.Process.myUid() != 2000) throw new Refused("must run as the adb shell user (uid 2000), not " + android.os.Process.myUid());
        boolean mutating = !cmd.equals("query");
        String name = "";
        String sha = null;
        if (mutating && !flag(args, "--assert-parked"))
            throw new Refused("--assert-parked is required: the car must be stationary, in P, parking brake on (the tool cannot read the gear; the OEM also refuses a switch outside P with 0x2332)");
        if (cmd.equals("load")) {
            if (args.length < 2) throw new Refused("load needs the carrier basename");
            name = args[1];
            sha = option(args, "--sha256");
            if (!Policy.NAME.matcher(name).matches()) throw new Refused("basename must match " + Policy.NAME.pattern());
            if (sha == null || !sha.matches("[0-9a-f]{64}")) throw new Refused("--sha256 <64 hex> of the carrier is required");
        }
        Compatibility.Log log = new Compatibility.Log() { @Override public void line(String s) { HorizonThemeTool.line(s); } };
        String why = Compatibility.verify(log);
        if (why != null) throw new Refused("this head unit's AutoContainer is not the one this tool knows: " + why);
        if (cmd.equals("load")) checkCarrier(name, sha, deadline);
        if (mutating) checkState(flag(args, "--ack-ambiguous"));

        Bridge bridge = new Bridge(deadline);
        try {
            bridge.connect();
            line("AutoContainer bound; descriptor " + Policy.DESCRIPTOR);
            int reg = bridge.register();
            line("callback registered native=" + reg);
            if (reg != 0) throw new IllegalStateException("callback registration refused: " + reg);
            bridge.accept(true);
            FlatBuf.Response baseline = query(bridge);
            if (cmd.equals("query")) { result("SETTLED", baseline.toString()); return SETTLED; }
            if (!Policy.UI_VERSION.equals(baseline.strings[0])) throw new Refused("UI contract differs: " + baseline.strings[0]);
            return cmd.equals("load") ? load(bridge, baseline, name, flag(args, "--replace-own")) : restore(bridge, baseline);
        } finally {
            bridge.accept(false);
            bridge.close();
        }
    }

    // ---- preconditions --------------------------------------------------------------------------
    private static void checkCarrier(String name, String sha, long deadline) throws Exception {
        File dir = new File(Policy.ANDROID_DIR);
        File[] rcc = dir.listFiles();
        if (rcc == null) throw new Refused(Policy.ANDROID_DIR + " is missing or unreadable (0x2329)");
        int count = 0;
        for (File f : rcc) if (f.getName().endsWith(".rcc")) count++;
        if (count != 1) throw new Refused(count + " .rcc files in " + Policy.ANDROID_DIR + " (exactly one is required: 0x232a/0x232b)");
        File file = new File(dir, name);
        if (!file.exists()) throw new Refused(file.getPath() + " does not exist");
        if (Files.isSymbolicLink(file.toPath()) || !Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) throw new Refused("carrier is not a regular file");
        if (file.length() < 1 || file.length() > Policy.MAX_CARRIER) throw new Refused("carrier size " + file.length() + " outside 1.." + Policy.MAX_CARRIER);
        String got = Compatibility.sha256(file.getPath(), deadline);
        if (!got.equals(sha)) throw new Refused("carrier SHA-256 " + got + " != expected " + sha);
        line("carrier ok: " + file.getPath() + " " + file.length() + " bytes sha256=" + got);
    }

    private static void checkState(boolean ackAmbiguous) throws Refused {
        File f = new File(Policy.STATE_FILE);
        if (!f.exists()) return;
        try {
            String[] p = new String(Files.readAllBytes(f.toPath()), "UTF-8").trim().split(" ");
            long at = Long.parseLong(p[0]);
            long ago = System.currentTimeMillis() - at;
            if (ago < Policy.MIN_GAP_MS) throw new Refused("last mutation was " + ago / 1000 + " s ago; wait " + (Policy.MIN_GAP_MS / 1000) + " s (native throttle)");
            if (p.length > 2 && !p[2].equals("SETTLED") && !p[2].equals("FAILED") && !ackAmbiguous)
                throw new Refused("the previous run (" + p[1] + ") did not settle (AMBIGUOUS/PENDING): look at the panel and query first; pass --ack-ambiguous to send again");
        } catch (Refused r) { throw r; } catch (Exception e) { throw new Refused("unreadable state file " + Policy.STATE_FILE + ": " + e.getMessage()); }
    }

    private static void saveState(String op, String outcome) {
        try { Files.write(new File(Policy.STATE_FILE).toPath(), (System.currentTimeMillis() + " " + op + " " + outcome + "\n").getBytes("UTF-8")); }
        catch (Exception e) { line("warning: could not write " + Policy.STATE_FILE + ": " + e.getMessage()); }
    }

    // ---- exchanges ------------------------------------------------------------------------------
    private static FlatBuf.Response query(Bridge b) throws Exception {
        int floor = b.eventCount();
        int nat = b.send(FlatBuf.query());
        line("query sent native=" + nat);
        if (nat != 0) throw new IllegalStateException("query native=" + nat);
        sleepWhile(b, 800);
        FlatBuf.Response sel = null;
        for (byte[] raw : b.eventsFrom(floor)) {
            FlatBuf.Response r = FlatBuf.decode(raw);
            if (!r.isSelectionReport()) continue;
            if (sel != null && !sel.toString().equals(r.toString())) throw new IllegalStateException("conflicting selection reports");
            sel = r;
        }
        if (!b.invalid().isEmpty()) throw new IllegalStateException(b.invalid());
        if (sel == null) throw new IllegalStateException("no fresh selection report from the OEM");
        line("selection: UI=" + sel.strings[0] + " source=" + sel.ints[3] + " path=" + sel.strings[2] + " resource=" + sel.strings[4]
                + (sel.isDefault() ? "  [FACTORY DEFAULT]" : sel.ownsSelection() ? "  [HORIZON CARRIER]" : "  [OTHER]"));
        return sel;
    }

    private static int load(Bridge b, FlatBuf.Response baseline, String name, boolean replaceOwn) throws Exception {
        boolean ownOther = replaceOwn && baseline.ownsSelection() && !baseline.selected(name);
        if (!baseline.isDefault() && !ownOther)
            throw new Refused("baseline is not the factory default (" + baseline.strings[2] + "): restore first, or --replace-own over another Horizon carrier; nothing overwritten");
        int floor = b.eventCount();
        saveState("load", "PENDING");
        int nat = b.send(FlatBuf.load(name));
        line("LOAD sent (one request) native=" + nat + " name=" + name);
        if (nat != 0) { saveState("load", "FAILED"); throw new IllegalStateException("load native=" + nat + "; remote completion unknown"); }
        long until = System.currentTimeMillis() + 2500;
        boolean ack2 = false, ack3 = false;
        while (System.currentTimeMillis() < until && b.alive()) {
            for (byte[] raw : b.eventsFrom(floor)) {
                FlatBuf.Response r = FlatBuf.decode(raw);
                if (r.loadAck(name)) ack2 = true;
                if (r.isFailureAck()) ack3 = true;
            }
            if (ack2 || ack3) break;
            Thread.sleep(25);
        }
        reportAcks(b, floor);
        FlatBuf.Response after = query(b);
        boolean selected = after.selected(name);
        if (ack3) { saveState("load", "FAILED"); result("FAILED", "OEM refused the load (see ACK3 line); selection=" + after.strings[2]); return FAILED; }
        if (selected && ack2) { saveState("load", "SETTLED"); line("SETTLED: selection and registration ACK match. Pixels UNKNOWN: look at the cluster."); result("SETTLED", "selected+ACK2; look at the panel"); return SETTLED; }
        saveState("load", "AMBIGUOUS");
        line("AMBIGUOUS: selected=" + selected + " ack2=" + ack2 + ". Do NOT repeat. Look at the panel, then run query.");
        result("AMBIGUOUS", "selected=" + selected + " ack2=" + ack2);
        return AMBIGUOUS;
    }

    private static int restore(Bridge b, FlatBuf.Response baseline) throws Exception {
        if (baseline.isFactoryConfigured()) { line("already the factory default: no request sent"); result("SETTLED", "already default"); return SETTLED; }
        if (!baseline.ownsSelection()) throw new Refused("the current selection is not ours (" + baseline.strings[2] + "): not touching it");
        int floor = b.eventCount();
        saveState("restore", "PENDING");
        int nat = b.send(FlatBuf.restoreDefault());
        line("RESTORE sent (cmd 5, mode 1) native=" + nat);
        if (nat != 0) { saveState("restore", "FAILED"); throw new IllegalStateException("restore native=" + nat); }
        sleepWhile(b, 1200);
        boolean ack3 = reportAcks(b, floor);
        FlatBuf.Response after = query(b);
        if (after.isFactoryConfigured() && !ack3) { saveState("restore", "SETTLED"); line("SETTLED: default selected. Confirm the factory instruments on the panel."); result("SETTLED", "default selected; confirm visually"); return SETTLED; }
        saveState("restore", ack3 ? "FAILED" : "AMBIGUOUS");
        line((ack3 ? "FAILED" : "AMBIGUOUS") + ": still " + after.strings[2] + ". Go to RECUPERACAO.md section 5 (move the file, ONE reboot fission-all).");
        result(ack3 ? "FAILED" : "AMBIGUOUS", "selection=" + after.strings[2]);
        return ack3 ? FAILED : AMBIGUOUS;
    }

    private static boolean reportAcks(Bridge b, int floor) {
        boolean ack3 = false;
        for (byte[] raw : b.eventsFrom(floor)) {
            FlatBuf.Response r = FlatBuf.decode(raw);
            if (r.cmd != 1005) continue;
            String kind = r.ints[0] == 2 ? "ACK2 registration" : r.ints[0] == 4 ? "ACK4 config" : r.ints[0] == 3 ? "ACK3 FAILURE" : "ack " + r.ints[0];
            StringBuilder sb = new StringBuilder("callback " + kind + " ints=" + java.util.Arrays.toString(r.ints) + " path=" + r.strings[0]);
            if (r.ints[0] == 3) {
                ack3 = true;
                for (int v : r.ints) { String n = Policy.errorName(v); if (n != null) sb.append(" error=0x").append(Integer.toHexString(v)).append(' ').append(n); }
            }
            line(sb.toString());
        }
        return ack3;
    }

    private static void sleepWhile(Bridge b, long ms) throws Exception {
        long until = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < until) { if (!b.alive()) throw new IllegalStateException("bridge lost"); Thread.sleep(25); }
    }

    private static void payloads() {
        line("query            " + hex(FlatBuf.query()));
        line("restore default  " + hex(FlatBuf.restoreDefault()));
        line("load (example)   " + hex(FlatBuf.load(Policy.NAME_PREFIX + "0123456789abcdef0123456789abcdef.rcc")));
    }

    static String hex(byte[] b) { StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format("%02x", x & 255)); return sb.toString(); }
}
