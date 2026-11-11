package featurecat.lizzie.analysis;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import featurecat.lizzie.util.Utils;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** A persistent, private GTP engine. Its protocol and search never touch the main engine. */
public final class PassAnalysisEngine implements AutoCloseable {
  public interface Listener {
    void result(int visits, double score);
    void failed(String message);
  }

  public static final class Settings {
    final String command, ip, port, user, password, key;
    final boolean ssh, useKey;
    public Settings(String command, boolean ssh, String ip, String port, String user,
        String password, boolean useKey, String key) {
      this.command = command; this.ssh = ssh; this.ip = ip; this.port = port;
      this.user = user; this.password = password; this.useKey = useKey; this.key = key;
    }
  }

  private static final class Request {
    final Object node;
    final String context;
    final List<String> commands;
    final int minimum, interval;
    final boolean black;
    final Listener listener;
    Request(Object node, String context, List<String> commands, boolean black,
        int minimum, int interval, Listener listener) {
      this.node = node; this.context = context; this.commands = new ArrayList<>(commands);
      this.black = black; this.minimum = minimum; this.interval = interval; this.listener = listener;
    }
  }

  private final Settings settings;
  private volatile Request desired;
  private volatile boolean closed;
  private volatile Process process;
  private volatile Connection connection;
  private volatile Session session;
  private BufferedWriter writer;
  private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
  private static final String EOF = new String("EOF");
  private int id;
  private final Thread worker;
  private final Thread shutdownHook;

  public PassAnalysisEngine(Settings settings) {
    this.settings = settings;
    shutdownHook = new Thread(this::destroy, "pass-value-shutdown");
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    worker = new Thread(this::run, "pass-value-engine");
    worker.setDaemon(true);
    worker.start();
  }

  public synchronized void request(Object node, String context, List<String> commands,
      boolean black, int minimum, int interval, Listener listener) {
    if (closed) return;
    Request old = desired;
    if (old != null && old.node == node && old.context.equals(context)
        && old.minimum == minimum) return;
    desired = new Request(node, context, commands, black, minimum, interval, listener);
    notifyAll();
  }

  public synchronized void cancel() { desired = null; notifyAll(); }

  private void start() throws IOException {
    InputStream stdout, stderr;
    OutputStream stdin;
    if (settings.ssh) {
      Connection conn = new Connection(settings.ip, Integer.parseInt(settings.port));
      connection = conn;
      conn.connect(null, 3000, 3000);
      boolean ok = settings.useKey
          ? conn.authenticateWithPublicKey(settings.user, new File(settings.key), null)
          : conn.authenticateWithPassword(settings.user, Utils.doDecrypt(settings.password));
      if (!ok) throw new IOException("SSH authentication failed");
      session = conn.openSession();
      session.execCommand(settings.command);
      stdout = session.getStdout(); stderr = session.getStderr(); stdin = session.getStdin();
    } else {
      process = new ProcessBuilder(Utils.splitCommand(settings.command)).start();
      stdout = process.getInputStream(); stderr = process.getErrorStream(); stdin = process.getOutputStream();
    }
    writer = new BufferedWriter(new OutputStreamWriter(stdin, java.nio.charset.StandardCharsets.UTF_8));
    pump(stdout, true);
    pump(stderr, false);
    if (closed) throw new IOException("closed");
  }

  private void pump(InputStream stream, boolean output) {
    Thread thread = new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
          java.nio.charset.StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) if (output) lines.offer(line);
      } catch (IOException ignored) {
        // Closing a local process or SSH session also closes its streams.
      } finally { if (output) lines.offer(EOF); }
    }, output ? "pass-value-stdout" : "pass-value-stderr");
    thread.setDaemon(true);
    thread.start();
  }

  private String poll() throws IOException, InterruptedException {
    String line = lines.poll(50, TimeUnit.MILLISECONDS);
    if (closed || line == EOF) throw new IOException("engine closed its output");
    return line;
  }

  /** Numbered reply is also a barrier: all older analysis output has been drained. */
  private void command(String command) throws IOException, InterruptedException {
    int number = id = id >= 999999 ? 1 : id + 1;
    writer.write(number + " " + command + "\n"); writer.flush();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
    while (!closed && System.nanoTime() < deadline) {
      String line = poll();
      if (line == null) continue;
      if (line.equals("=" + number) || line.startsWith("=" + number + " ")) return;
      if (line.equals("?" + number) || line.startsWith("?" + number + " ")
          || line.startsWith("? ")) throw new IOException("GTP command rejected: " + command.split(" ")[0]);
    }
    throw new IOException("timed out waiting for GTP reply");
  }

  private void run() {
    Request processed = null;
    try {
      while (!closed) {
        Request request;
        synchronized (this) {
          while (!closed && (desired == null || desired == processed)) wait();
          if (closed) break;
          request = desired;
        }
        if (writer == null) start();
        command("stop");
        for (String cmd : request.commands) {
          if (closed || desired != request) break;
          command(cmd);
        }
        if (desired != request) continue;
        command("play " + (request.black ? "B" : "W") + " pass");
        if (desired != request) { command("stop"); continue; }
        command("kata-analyze " + (request.black ? "W" : "B") + " " + request.interval);
        while (!closed && desired == request) {
          String line = poll();
          if (line == null || !line.startsWith("info ")) continue;
          int visits = 0;
          double weightedScore = 0;
          for (String info : line.substring(5).split("ownership")[0].split(" info ")) {
            MoveData move = MoveData.fromInfoKatago(info);
            if (move.isSymmetry || move.playouts <= 0) continue;
            visits += move.playouts;
            weightedScore += move.scoreMean * move.playouts;
          }
          if (visits > 0 && desired == request) request.listener.result(visits, -weightedScore / visits);
          if (visits >= request.minimum) break;
        }
        command("stop");
        processed = request;
      }
    } catch (Exception e) {
      Request request = desired;
      // A failed helper is not repeatedly relaunched on every main-engine update.
      closed = true;
      if (request != null) request.listener.failed(e.getMessage());
    } finally {
      destroy();
      try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
      catch (IllegalStateException ignored) { /* JVM shutdown is already in progress. */ }
    }
  }

  private void destroy() {
    Process local = process;
    if (local != null) local.destroy();
    Session remote = session;
    if (remote != null) remote.close();
    Connection conn = connection;
    if (conn != null) conn.close();
  }

  @Override public synchronized void close() {
    closed = true;
    desired = null;
    notifyAll();
    destroy();
    worker.interrupt();
  }
}
