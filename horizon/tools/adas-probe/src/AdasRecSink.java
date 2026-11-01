// AdasRecSink: a tiny HTTP sink on the head unit for the Horizon theme's ADAS recorder
// (horizon/qml/AdasRecorder.qml). The cluster cannot reach the head unit's files for writing, so the
// recorder also POSTs its chunks to the URLs its command file lists; this appends each body to a log.
//
//   CLASSPATH=/data/local/tmp/adas-probe.dex app_process / AdasRecSink [port] [seconds] [log]
//
// Defaults: port 8765, 3600 s, /data/local/tmp/adas-rec.log. Every request is logged with its path,
// so the log also tells which of the listed URLs the cluster could reach.
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public final class AdasRecSink {
    private AdasRecSink() {}

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8765;
        long seconds = args.length > 1 ? Long.parseLong(args[1]) : 3600;
        String logPath = args.length > 2 ? args[2] : "/data/local/tmp/adas-rec.log";
        long end = System.currentTimeMillis() + seconds * 1000L;
        try (ServerSocket server = new ServerSocket(port);
             FileOutputStream log = new FileOutputStream(logPath, true)) {
            server.setSoTimeout(1000);
            System.out.println("listening on " + port + " for " + seconds + " s, log " + logPath);
            while (System.currentTimeMillis() < end) {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(5000);
                    handle(s, log);
                } catch (SocketTimeoutException idle) {
                    // nothing yet; check the deadline
                } catch (Exception e) {
                    System.out.println("request failed: " + e);
                }
            }
        }
        System.exit(0);
    }

    private static void handle(Socket s, FileOutputStream log) throws Exception {
        InputStream in = s.getInputStream();
        String requestLine = readLine(in);
        int length = 0;
        for (String h = readLine(in); h != null && !h.isEmpty(); h = readLine(in)) {
            if (h.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(h.substring(15).trim());
        }
        byte[] body = new byte[length];
        for (int read = 0; read < length; ) {
            int n = in.read(body, read, length - read);
            if (n < 0) break;
            read += n;
        }
        String from = s.getInetAddress().getHostAddress();
        String note = "## " + System.currentTimeMillis() + " " + from + " " + requestLine + " " + length + " B\n";
        log.write(note.getBytes(StandardCharsets.UTF_8));
        log.write(body);
        log.flush();
        System.out.print(note);
        OutputStream out = s.getOutputStream();
        out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int c = in.read(); c >= 0; c = in.read()) {
            if (c == '\n') return line.toString("US-ASCII").trim();
            line.write(c);
        }
        return line.size() > 0 ? line.toString("US-ASCII").trim() : null;
    }
}
