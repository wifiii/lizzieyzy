package featurecat.lizzie.analysis.database;

import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.*;

/**
 * All callers submit detached snapshots; JDBC and model hashing run off the Swing/engine threads.
 */
public final class AnalysisDatabase {
  private AnalysisDatabase() {}

  public static final ExecutorService IO =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "analysis-database");
            t.setDaemon(true);
            return t;
          });
  public static final int VERSION = 1;

  static {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  IO.shutdown();
                  try {
                    if (!IO.awaitTermination(10, TimeUnit.SECONDS)) IO.shutdownNow();
                  } catch (InterruptedException e) {
                    IO.shutdownNow();
                    Thread.currentThread().interrupt();
                  }
                },
                "analysis-database-shutdown"));
  }

  private static Connection open(Path path) throws Exception {
    Class.forName("org.sqlite.JDBC");
    Path absolute = path.toAbsolutePath().normalize();
    if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
    Connection c = DriverManager.getConnection("jdbc:sqlite:" + absolute);
    try (Statement s = c.createStatement()) {
      s.execute("PRAGMA busy_timeout=3000");
      int version;
      try (ResultSet r = s.executeQuery("PRAGMA user_version")) {
        version = r.next() ? r.getInt(1) : 0;
      }
      if (version != 0 && version != VERSION)
        throw new SQLException("Unsupported database format: " + version);
      s.execute("PRAGMA journal_mode=WAL");
      s.execute(
          "CREATE TABLE IF NOT EXISTS analysis (position TEXT NOT NULL, context TEXT NOT NULL, kind"
              + " TEXT NOT NULL, visits INTEGER NOT NULL, payload TEXT NOT NULL, saved_at INTEGER"
              + " NOT NULL, PRIMARY KEY(position,context,kind))");
      s.execute("PRAGMA user_version=" + VERSION);
      return c;
    } catch (Exception e) {
      c.close();
      throw e;
    }
  }

  public static AnalysisSnapshot load(Path path, PositionKey key, String context) throws Exception {
    AnalysisSnapshot out = new AnalysisSnapshot();
    if (!Files.exists(path)) return out;
    try (Connection c = open(path);
        PreparedStatement q =
            c.prepareStatement(
                "SELECT kind,payload,saved_at FROM analysis WHERE position=? AND context=?")) {
      q.setString(1, key.hash);
      q.setString(2, context);
      try (ResultSet r = q.executeQuery()) {
        while (r.next()) {
          if (r.getString(1).equals("normal")) out.readOrdinary(r.getString(2), key);
          else if (r.getString(1).equals("pass")) {
            org.json.JSONObject j = new org.json.JSONObject(r.getString(2));
            out.passVisits = j.getInt("visits");
            out.passScore = j.getDouble("score");
          }
          out.savedAt = Math.max(out.savedAt, r.getLong(3));
        }
      }
    }
    return out;
  }

  public static void save(Path path, PositionKey key, String context, AnalysisSnapshot snapshot)
      throws Exception {
    try (Connection c = open(path)) {
      c.setAutoCommit(false);
      try (PreparedStatement q =
          c.prepareStatement(
              "INSERT INTO analysis VALUES(?,?,?,?,?,?) ON CONFLICT(position,context,kind) DO"
                  + " UPDATE SET"
                  + " visits=excluded.visits,payload=excluded.payload,saved_at=excluded.saved_at"
                  + " WHERE excluded.visits>=analysis.visits")) {
        if (snapshot.visits > 0 && !snapshot.normal.isEmpty())
          write(q, key.hash, context, "normal", snapshot.visits, snapshot.ordinaryJson(key));
        if (snapshot.passVisits > 0 && Double.isFinite(snapshot.passScore)) {
          org.json.JSONObject pass = new org.json.JSONObject();
          pass.put("visits", snapshot.passVisits);
          pass.put("score", snapshot.passScore);
          write(q, key.hash, context, "pass", snapshot.passVisits, pass.toString());
        }
        c.commit();
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  private static void write(
      PreparedStatement q, String key, String context, String kind, int visits, String payload)
      throws SQLException {
    q.setString(1, key);
    q.setString(2, context);
    q.setString(3, kind);
    q.setInt(4, visits);
    q.setString(5, payload);
    q.setLong(6, System.currentTimeMillis());
    q.executeUpdate();
  }

  public static void clear(Path path) throws Exception {
    if (!Files.exists(path)) return;
    try (Connection c = open(path);
        Statement s = c.createStatement()) {
      s.executeUpdate("DELETE FROM analysis");
    }
  }
}
