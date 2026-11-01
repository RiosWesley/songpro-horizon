// HorizonParkFeed: READ-ONLY. Hands Horizon the parking sensors (PDC) the head unit reads, as
// park.json next to the now-playing feed. The radar permission is signature-only, so a normal app
// cannot read them; the shell can (measured in reverse on 2026-11-01, docs/estudos/adas-em-p.md).
//
//   CLASSPATH=/data/local/tmp/horizon-park-feed.dex app_process / HorizonParkFeed <feed dir>
//
// Started detached by the Song Pro Horizon app; runs until killed. One at a time: a second copy
// finds the lock taken and exits. Writes (temp file + rename) when a zone changes, and every second
// while any zone sees something, so the theme can tell a live feed from a stale file:
//   { "seq": n, "dist": [9 ints, 0..155 cm, 150 = nothing], "state": [9 ints, 1 clear .. 4 closest] }
// Zones in order: front left, front middle left, front middle right, front right, rear left corner,
// rear middle left, rear middle right, rear right corner, rear middle (0x99000061..69 / 71..79).
import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.FileLock;
import java.util.Arrays;

public final class HorizonParkFeed {
    private static final int RADAR = 1025;
    private static final int[] DIST = {0x99000061, 0x99000062, 0x99000063, 0x99000064, 0x99000065,
            0x99000066, 0x99000067, 0x99000068, 0x99000069};
    private static final int[] STATE = {0x99000071, 0x99000072, 0x99000073, 0x99000074, 0x99000075,
            0x99000076, 0x99000077, 0x99000078, 0x99000079};
    private static final long POLL_MS = 250, ALIVE_MS = 1000;

    private HorizonParkFeed() {}

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "/sdcard/.cluster/horizon");
        dir.mkdirs();
        FileLock lock = new RandomAccessFile(new File(dir, ".park.lock"), "rw").getChannel().tryLock();
        if (lock == null) {
            System.out.println("HorizonParkFeed: already running");
            System.exit(0);
        }
        if (android.os.Looper.getMainLooper() == null) android.os.Looper.prepareMainLooper();
        Class<?> mc = Class.forName("android.hardware.BYDAutoManager");
        Constructor<?> c = mc.getDeclaredConstructor(Context.class);
        c.setAccessible(true);
        Object manager = c.newInstance(systemContext());
        Method getIntArray = mc.getDeclaredMethod("getIntArray", int.class, int[].class);
        getIntArray.setAccessible(true);

        File target = new File(dir, "park.json"), tmp = new File(dir, ".park.json.tmp");
        int[] lastDist = null, lastState = null;
        long seq = 0, written = 0;
        while (true) {
            int[] dist = (int[]) getIntArray.invoke(manager, RADAR, DIST.clone());
            int[] state = (int[]) getIntArray.invoke(manager, RADAR, STATE.clone());
            long now = System.currentTimeMillis();
            boolean changed = !Arrays.equals(dist, lastDist) || !Arrays.equals(state, lastState);
            if (changed || (seeing(state) && now - written >= ALIVE_MS)) {
                String json = "{\"seq\":" + (++seq) + ",\"dist\":" + Arrays.toString(dist)
                        + ",\"state\":" + Arrays.toString(state) + "}";
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(json.getBytes("UTF-8"));
                }
                if (!tmp.renameTo(target)) System.out.println("HorizonParkFeed: rename failed");
                lastDist = dist;
                lastState = state;
                written = now;
            }
            Thread.sleep(POLL_MS);
        }
    }

    private static boolean seeing(int[] state) {
        if (state == null) return false;
        for (int s : state) if (s >= 2) return true;
        return false;
    }

    private static Context systemContext() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        Constructor<?> c = at.getDeclaredConstructor();
        c.setAccessible(true);
        Object thread = c.newInstance();
        Field current = at.getDeclaredField("sCurrentActivityThread");
        current.setAccessible(true);
        current.set(null, thread);
        Method sys = at.getDeclaredMethod("getSystemContext");
        sys.setAccessible(true);
        return (Context) sys.invoke(thread);
    }
}
