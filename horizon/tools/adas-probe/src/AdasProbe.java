// AdasProbe: READ-ONLY dump of the ADAS target signals the head unit receives, to learn which
// targets the camera reports and when (the cluster only shows a slot whose TYPE is 1..4).
//
//   CLASSPATH=/data/local/tmp/adas-probe.dex app_process / AdasProbe [seconds]
//
// It only calls BYDAutoManager.getInt (the same read every BYD app makes). The native service
// does not restrict reads by caller (BYDAutoService::checkGetPermission always allows); the
// permission checks live in the Java device classes, which this bypasses by using the manager.
// Signal ids are BYD's own (CarSetting's com.byd.feature: ADAS_TSR_TARGET_*_0N on CAN 0x295,
// SETTING_TARGET_*_0N on CAN 0x374, BODYWORK_ONLINE_HAS_0x02950N).
import android.content.Context;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AdasProbe {
    private static final int ADAS = 1038, SETTING = 1023, BODYWORK = 1001;
    private static final int ERROR = 0x800003e8;

    private AdasProbe() {}

    public static void main(String[] args) throws Exception {
        int seconds = args.length > 0 && !args[0].equals("get") ? Integer.parseInt(args[0]) : 10;
        if (android.os.Looper.getMainLooper() == null) android.os.Looper.prepareMainLooper();
        Context ctx = systemContext();
        Class<?> mc = Class.forName("android.hardware.BYDAutoManager");
        Object manager = manager(mc, ctx);
        Method getInt = mc.getDeclaredMethod("getInt", int.class, int.class);
        getInt.setAccessible(true);

        // "get <device> <hex id> [<device> <hex id> ...]": read single signals once.
        if (args.length > 0 && args[0].equals("get")) {
            for (int i = 1; i + 1 < args.length; i += 2) {
                int device = Integer.parseInt(args[i]);
                int id = (int) Long.parseLong(args[i + 1].replace("0x", ""), 16);
                System.out.println(device + " 0x" + Integer.toHexString(id) + " = " + v(getInt, manager, device, id));
            }
            System.exit(0);
        }

        System.out.println("t_ms | fcw(0x138): status dist speed accel level | lights(0x09B): count t1 t2 t3"
                + " | 0x295 slot n: on type state x y | old F1(0x374): type state x y");
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < seconds * 1000L) {
            StringBuilder sb = new StringBuilder();
            sb.append(System.currentTimeMillis() - start);
            sb.append(" | fcw ").append(v(getInt, manager, ADAS, 0x1380000A))       // FRONT_VEHICLE_STATUS
              .append(' ').append(v(getInt, manager, ADAS, 0x1380000B))             // FRONT_VEHICLE_DISTANCE
              .append(' ').append(v(getInt, manager, ADAS, 0x13800018))             // FRONT_VEHICLE_SPEED
              .append(' ').append(v(getInt, manager, ADAS, 0x13800020))             // FRONT_VEHICLE_ACCELERATION
              .append(' ').append(v(getInt, manager, ADAS, 0x13800008));            // FCW_LEVEL_STATUS
            sb.append(" | lights ").append(v(getInt, manager, ADAS, 0x09B00408))    // LIGHTS_TARGET_COUNT
              .append(' ').append(v(getInt, manager, ADAS, 0x09B00410))             // LIGHTS_TARGET_1_INFO
              .append(' ').append(v(getInt, manager, ADAS, 0x09B00438))
              .append(' ').append(v(getInt, manager, ADAS, 0x09B00460));
            for (int n = 1; n <= 4; n++) {
                int base = 0x29500000 | (n << 12);
                sb.append(" | ").append(n).append(':')
                  .append(' ').append(v(getInt, manager, BODYWORK, base))          // ONLINE_HAS_0x02950N
                  .append(' ').append(v(getInt, manager, ADAS, base | 0x01C))      // TYPE
                  .append(' ').append(v(getInt, manager, ADAS, base | 0x01B))      // STATE
                  .append(' ').append(v(getInt, manager, ADAS, base | 0x010))      // X_AXIS
                  .append(' ').append(v(getInt, manager, ADAS, base | 0x030));     // Y_AXIS
            }
            int old = 0x37400000 | (2 << 12);
            sb.append(" | old ").append(v(getInt, manager, SETTING, old | 0x034))
              .append(' ').append(v(getInt, manager, SETTING, old | 0x030))
              .append(' ').append(v(getInt, manager, SETTING, old | 0x010))
              .append(' ').append(v(getInt, manager, SETTING, old | 0x018));
            System.out.println(sb);
            Thread.sleep(250);
        }
        System.exit(0);
    }

    private static String v(Method getInt, Object manager, int device, int id) {
        try {
            int r = (Integer) getInt.invoke(manager, device, id);
            return r == ERROR ? "E" : Integer.toString(r);
        } catch (Throwable t) {
            return "X";
        }
    }

    // The way scrcpy gets a Context inside app_process: a bare ActivityThread's system context.
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

    // The manager's constructor also registers with BYD's strategy service; if that refuses a
    // shell caller, build the object bare and run only its native init.
    private static Object manager(Class<?> mc, Context ctx) throws Exception {
        try {
            Constructor<?> c = mc.getDeclaredConstructor(Context.class);
            c.setAccessible(true);
            Object m = c.newInstance(ctx);
            System.out.println("manager: constructed");
            return m;
        } catch (Throwable t) {
            System.out.println("manager: constructor failed (" + t.getCause() + "), using native init only");
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field f = unsafeClass.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object unsafe = f.get(null);
            Object m = unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, mc);
            Field context = mc.getDeclaredField("mContext");
            context.setAccessible(true);
            context.set(m, ctx);
            Method init = mc.getDeclaredMethod("nativeInit");
            init.setAccessible(true);
            init.invoke(m);
            return m;
        }
    }
}
