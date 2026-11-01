// ParkProbe: READ-ONLY look at the parking sensors (PDC) and rear corner radar (BSD/RCTA/DOW/RCW)
// signals the head unit receives. Only BYDAutoManager getters (getInt, getIntArray, getBuffer).
//
// build.sh puts it in the same dex as AdasProbe:
//   CLASSPATH=/data/local/tmp/adas-probe.dex app_process / ParkProbe once [hexid ...]
//   CLASSPATH=/data/local/tmp/adas-probe.dex app_process / ParkProbe log <seconds>   (prints only changes)
//   CLASSPATH=/data/local/tmp/adas-probe.dex app_process / ParkProbe scan <file of hex ids>
// Study and results: docs/estudos/adas-em-p.md
import android.content.Context;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ParkProbe {
    private static final int RADAR = 1025, ADAS = 1038;
    private static final int ERROR = 0x800003e8;
    // PDC as the framework's BYDAutoRadarDevice reads it (virtual ids, distance 0..155, probe state)
    private static final int[] PDC_DIST = {0x99000061, 0x99000062, 0x99000063, 0x99000064, 0x99000065,
            0x99000066, 0x99000067, 0x99000068, 0x99000069};
    private static final int[] PDC_STATE = {0x99000071, 0x99000072, 0x99000073, 0x99000074, 0x99000075,
            0x99000076, 0x99000077, 0x99000078, 0x99000079};
    // CAN 0x418 (rear corner radars), the frame the cluster turns into the rear "radar ripples"
    private static final int[] BSD = {0x41800000, 0x41800008, 0x41800010, 0x41800012, 0x41800014, 0x41800015,
            0x41800016, 0x41800018, 0x4180001A, 0x4180001C, 0x4180001E, 0x41800020, 0x41800022, 0x41800028,
            0x4180002C, 0x4180002E, 0x41800038};

    // CAN 0x235 ("2in1" simplified ADAS picture: ACC interface, time gap, distance bars, text, body position, lines)
    private static final int[] TWO_IN_ONE = {0x23500008, 0x2350000A, 0x2350000D, 0x23500010, 0x23500014,
            0x23500018, 0x2350001C};
    // context: gear (0x342), reverse-radar switch, 360 camera output/work mode/state/signal
    private static final int[] CTX = {0x34200020, 0x26700038, 0x4F401010, 0x4F401014, 0x4F401018, 0x4F40101A};

    private static Method getInt, getIntArray, getBuffer;
    private static Object m;

    private ParkProbe() {}

    public static void main(String[] args) throws Exception {
        if (android.os.Looper.getMainLooper() == null) android.os.Looper.prepareMainLooper();
        Class<?> mc = Class.forName("android.hardware.BYDAutoManager");
        m = manager(mc, systemContext());
        getInt = mc.getDeclaredMethod("getInt", int.class, int.class);
        getIntArray = mc.getDeclaredMethod("getIntArray", int.class, int[].class);
        getBuffer = mc.getDeclaredMethod("getBuffer", int.class, int.class);
        getInt.setAccessible(true); getIntArray.setAccessible(true); getBuffer.setAccessible(true);
        String mode = args.length > 0 ? args[0] : "once";
        if (mode.equals("once")) {
            System.out.println("pdc dist  (getInt)      " + ints(RADAR, PDC_DIST));
            System.out.println("pdc dist  (getIntArray) " + arr(RADAR, PDC_DIST));
            System.out.println("pdc state (getInt)      " + ints(RADAR, PDC_STATE));
            System.out.println("buffer(1025,1) status   " + buf(RADAR, 1));
            System.out.println("buffer(1025,2) obstacle " + buf(RADAR, 2));
            System.out.println("bsd 0x418               " + ints(ADAS, BSD));
            for (int i = 1; i < args.length; i++) {
                int id = (int) Long.parseLong(args[i].replace("0x", ""), 16);
                System.out.println("0x" + Integer.toHexString(id) + " = " + one(ADAS, id)
                        + " / buffer " + buf(ADAS, id));
            }
        } else if (mode.equals("scan")) {
            // scan <file with one hex id per line>: prints "id value" for every id (read-only getInt)
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(args[1]));
            String l;
            while ((l = r.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty()) continue;
                int id = (int) Long.parseLong(l.replace("0x", ""), 16);
                System.out.println(l + " " + one(ADAS, id));
                Thread.sleep(2);
            }
        } else {
            int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 60;
            System.out.println("t_ms | pdc dist x9 | pdc state x9 | buf2 | bsd 0x418 x17 | 0x235 x7 | ctx x6");
            long start = System.currentTimeMillis();
            String last = "";
            while (System.currentTimeMillis() - start < seconds * 1000L) {
                String line = ints(RADAR, PDC_DIST) + " | " + ints(RADAR, PDC_STATE) + " | " + buf(RADAR, 2)
                        + " | " + ints(ADAS, BSD) + " | " + ints(ADAS, TWO_IN_ONE) + " | " + ints(ADAS, CTX);
                if (!line.equals(last)) {
                    System.out.println(new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date())
                            + " " + (System.currentTimeMillis() - start) + " | " + line);
                    System.out.flush();
                }
                last = line;
                Thread.sleep(250);
            }
            System.out.println("end " + (System.currentTimeMillis() - start));
        }
        System.exit(0);
    }

    private static String one(int dev, int id) {
        try {
            int r = (Integer) getInt.invoke(m, dev, id);
            return r == ERROR ? "E" : Integer.toString(r);
        } catch (Throwable t) { return "X"; }
    }

    private static String ints(int dev, int[] ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) sb.append(one(dev, id)).append(' ');
        return sb.toString().trim();
    }

    private static String arr(int dev, int[] ids) {
        try {
            int[] r = (int[]) getIntArray.invoke(m, dev, ids.clone());
            StringBuilder sb = new StringBuilder();
            for (int v : r) sb.append(v).append(' ');
            return sb.toString().trim();
        } catch (Throwable t) { return "X " + t.getCause(); }
    }

    private static String buf(int dev, int id) {
        try {
            byte[] b = (byte[]) getBuffer.invoke(m, dev, id);
            if (b == null) return "null";
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x & 0xff));
            return sb.toString();
        } catch (Throwable t) { return "X " + t.getCause(); }
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

    private static Object manager(Class<?> mc, Context ctx) throws Exception {
        Constructor<?> c = mc.getDeclaredConstructor(Context.class);
        c.setAccessible(true);
        return c.newInstance(ctx);
    }
}
