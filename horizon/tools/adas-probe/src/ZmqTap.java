// ZmqTap: READ-ONLY. Listens to a ZeroMQ publisher the way one more subscriber would (ZMTP 3,
// NULL security) and prints what it publishes for a while. A publisher serves each subscriber on
// its own and drops what a slow one cannot take, so listening never slows or changes it. Nothing
// is sent but the handshake and an empty subscription (= every topic).
//
// The cluster's data library (libBydDataSource.so) subscribes to tcp://192.168.195.2:8889 and
// tcp://192.168.195.3:6666 and publishes on tcp://127.0.0.1:49160 (docs/estudos/adas-dados.md).
//
//   CLASSPATH=/data/local/tmp/adas-probe.dex app_process / ZmqTap <host> <port> <seconds> [bytes]
//
// Prints the first 40 messages in full (each frame: size, hex, text) and then, every 5 s, how
// many messages arrived per topic (the first frame, or the first 8 bytes of a one-frame message).
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ZmqTap {
    private static final int SHOW = 40;

    private ZmqTap() {}

    public static void main(String[] args) throws Exception {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        long until = System.currentTimeMillis() + Long.parseLong(args[2]) * 1000;
        int bytes = args.length > 3 ? Integer.parseInt(args[3]) : 48;

        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 3000);
        s.setSoTimeout(1000);
        DataInputStream in = new DataInputStream(s.getInputStream());
        OutputStream out = s.getOutputStream();

        byte[] greeting = new byte[64];
        greeting[0] = (byte) 0xFF;
        greeting[9] = 0x7F;
        greeting[10] = 3;
        System.arraycopy("NULL".getBytes("US-ASCII"), 0, greeting, 12, 4);
        out.write(greeting);
        byte[] theirs = new byte[64];
        in.readFully(theirs);
        System.out.println("connected " + host + ":" + port + ", ZMTP " + theirs[10] + "." + theirs[11]
                + ", " + new String(theirs, 12, 20, "US-ASCII").trim());

        out.write(frame(4, ready()));
        // The publisher answers with its own READY, which names its socket type.
        while (true) {
            int flags = in.readUnsignedByte();
            byte[] body = read(in, flags);
            if ((flags & 4) != 0) {
                System.out.println("peer " + printable(body, body.length));
                break;
            }
        }
        out.write(frame(0, new byte[] {1}));
        out.flush();

        Map<String, Integer> counts = new TreeMap<>();
        List<byte[]> parts = new ArrayList<>();
        long shown = 0, total = 0, nextReport = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < until) {
            try {
                int flags = in.readUnsignedByte();
                byte[] body = read(in, flags);
                if ((flags & 4) == 0) {
                    parts.add(body);
                    if ((flags & 1) == 0) {
                        total++;
                        String topic = printable(parts.get(0), parts.size() > 1 ? 32 : 8);
                        counts.merge(topic, 1, Integer::sum);
                        if (shown++ < SHOW) System.out.println(describe(total, parts, bytes));
                        parts.clear();
                    }
                }
            } catch (SocketTimeoutException quiet) {
                // nothing published in the last second
            } catch (EOFException closed) {
                System.out.println("publisher closed the connection");
                break;
            }
            if (System.currentTimeMillis() >= nextReport) {
                System.out.println("after " + total + " messages: " + counts);
                nextReport += 5000;
            }
        }
        System.out.println("end: " + total + " messages " + counts);
        s.close();
        System.exit(0);
    }

    private static String describe(long n, List<byte[]> parts, int bytes) {
        StringBuilder line = new StringBuilder("msg " + n + " (" + parts.size() + " frames)");
        for (byte[] p : parts) {
            line.append("\n  [").append(p.length).append("] ").append(hex(p, bytes))
                .append("  |").append(printable(p, bytes)).append("|");
        }
        return line.toString();
    }

    // READY command: name, then the "Socket-Type" property (1-byte name length, 4-byte value length).
    private static byte[] ready() throws IOException {
        byte[] name = "Socket-Type".getBytes("US-ASCII"), value = "SUB".getBytes("US-ASCII");
        byte[] body = new byte[6 + 1 + name.length + 4 + value.length];
        int i = 0;
        body[i++] = 5;
        for (byte b : "READY".getBytes("US-ASCII")) body[i++] = b;
        body[i++] = (byte) name.length;
        for (byte b : name) body[i++] = b;
        i += 3;
        body[i++] = (byte) value.length;
        for (byte b : value) body[i++] = b;
        return body;
    }

    // A short frame (under 256 bytes): flags, size, body. Flag 4 marks a command.
    private static byte[] frame(int flags, byte[] body) {
        byte[] f = new byte[2 + body.length];
        f[0] = (byte) flags;
        f[1] = (byte) body.length;
        System.arraycopy(body, 0, f, 2, body.length);
        return f;
    }

    // Flag 2 marks an 8-byte size; flag 1 says more frames of the same message follow.
    private static byte[] read(DataInputStream in, int flags) throws IOException {
        long size = (flags & 2) != 0 ? in.readLong() : in.readUnsignedByte();
        if (size > 16 * 1024 * 1024) throw new IOException("frame of " + size + " bytes");
        byte[] body = new byte[(int) size];
        in.readFully(body);
        return body;
    }

    private static String hex(byte[] b, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(b.length, max); i++) sb.append(String.format("%02x", b[i] & 0xff));
        return b.length > max ? sb + ".." : sb.toString();
    }

    private static String printable(byte[] b, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(b.length, max); i++) {
            int c = b[i] & 0xff;
            sb.append(c >= 32 && c < 127 ? (char) c : '.');
        }
        return sb.toString();
    }
}
