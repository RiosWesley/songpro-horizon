// Binder link to the OEM AutoContainer service, the way DashCast's ClusterThemeIviHelper does it
// (https://github.com/Kiroha/byd-dashcast, MIT licence): AutoContainer.sendInfo2(int type, byte[])
// is transaction 3, registerCallback(IContainerCallback) is transaction 4, and the service answers
// through IContainerCallback.receivedInfo2(int, byte[]) = transaction 4 of the callback.
// Verified on this car's framework.jar (IAutoContainer$Stub: sendJson 1, sendInfo 2, sendInfo2 3,
// registerCallback 4; IContainerCallback$Stub: serviceDied 1, receivedJson 2, receivedInfo 3,
// receivedInfo2 4); Compatibility reads them again on every run. The service accepts the shell uid because it shares the platform signature
// (checkSignatures fast path); on this firmware container_comm_cfg.json only lists clusterdemo.
// One pinned connection: no reconnect, no replay.
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class Bridge implements AutoCloseable {
    private IBinder service;
    private Binder callback;
    private volatile boolean died, closed;
    private final IBinder.DeathRecipient death = new IBinder.DeathRecipient() { @Override public void binderDied() { died = true; } };
    private final long deadlineMs;
    private final Object lock = new Object();
    private final List<byte[]> events = new ArrayList<>();
    private String invalid = "";
    private boolean accepting;

    Bridge(long deadlineMs) { this.deadlineMs = deadlineMs; }

    void connect() throws Exception {
        Method m = Class.forName("android.os.ServiceManager").getMethod("checkService", String.class);
        service = (IBinder) m.invoke(null, "AutoContainer");
        if (service == null) throw new IllegalStateException("AutoContainer service not found");
        if (!Policy.DESCRIPTOR.equals(service.getInterfaceDescriptor())) throw new IllegalStateException("unexpected AutoContainer descriptor " + service.getInterfaceDescriptor());
        service.linkToDeath(death, 0);
    }

    boolean alive() { return !closed && !died && service != null && service.isBinderAlive() && System.currentTimeMillis() < deadlineMs; }

    int register() throws Exception {
        callback = new Binder() {
            { attachInterface(null, Policy.CALLBACK); }

            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(Policy.CALLBACK); return true; }
                int uid = Binder.getCallingUid();
                synchronized (lock) {
                    if (uid != 1000) { invalid = "callback from uid " + uid; throw new SecurityException("unexpected callback uid"); }
                    if (!accepting) { if (reply != null) reply.writeNoException(); return true; }
                    try {
                        data.enforceInterface(Policy.CALLBACK);
                        if (code == Policy.TX_RECEIVED_INFO2) {
                            int type = data.readInt();
                            byte[] bytes = data.createByteArray();
                            if (type == 8 && bytes != null && data.dataAvail() == 0) {
                                if (bytes.length < 8 || bytes.length > 4096) invalid = "unsupported callback size " + bytes.length;
                                else if (events.size() >= 32) invalid = "callback bound exceeded";
                                else events.add(bytes);
                            }
                        }
                    } catch (RuntimeException ex) { invalid = "malformed callback: " + ex.getMessage(); }
                }
                if (reply != null) reply.writeNoException();
                return true;
            }
        };
        return transact(Policy.TX_REGISTER_CALLBACK, new Writer() { public void write(Parcel p) { p.writeStrongBinder(callback); } });
    }

    int send(byte[] payload) throws Exception {
        return transact(Policy.TX_SEND_INFO2, new Writer() { public void write(Parcel p) { p.writeInt(8); p.writeByteArray(payload); } });
    }

    void accept(boolean on) { synchronized (lock) { accepting = on; } }

    int eventCount() { synchronized (lock) { return events.size(); } }

    List<byte[]> eventsFrom(int floor) { synchronized (lock) { return new ArrayList<>(events.subList(floor, events.size())); } }

    String invalid() { synchronized (lock) { return invalid; } }

    private interface Writer { void write(Parcel p); }

    private int transact(int code, Writer w) throws Exception {
        if (!alive()) throw new IllegalStateException("bridge not alive (died, closed or deadline)");
        Parcel d = Parcel.obtain(), r = Parcel.obtain();
        try {
            d.writeInterfaceToken(Policy.DESCRIPTOR);
            w.write(d);
            if (!service.transact(code, d, r, 0)) throw new IllegalStateException("transaction " + code + " not handled");
            r.readException();
            if (r.dataAvail() != 4) throw new IllegalStateException("missing native result");
            return r.readInt();
        } finally { r.recycle(); d.recycle(); }
    }

    @Override public void close() {
        closed = true;
        try { if (service != null) service.unlinkToDeath(death, 0); } catch (RuntimeException ignored) { }
    }
}
