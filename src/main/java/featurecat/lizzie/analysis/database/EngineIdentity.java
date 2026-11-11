package featurecat.lizzie.analysis.database;

import featurecat.lizzie.util.Utils;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.json.*;

/** Content identity, deliberately independent of executable path, process ID and engine version. */
public final class EngineIdentity {
  private EngineIdentity() {}

  private static final Map<String, String> hashes = new HashMap<>();

  public static String create(
      String command,
      String modelOverride,
      String configOverride,
      String rules,
      double komi,
      Map<String, String> runtime,
      double pda,
      boolean remote)
      throws Exception {
    List<String> args = Utils.splitCommand(command);
    String detectedModel = option(args, "-model", "--model", "-m");
    String model =
        !remote && detectedModel != null && Files.isRegularFile(Paths.get(detectedModel))
            ? detectedModel
            : modelOverride.trim().isEmpty() ? detectedModel : modelOverride;
    if (model == null || model.trim().isEmpty()) throw new IOException("Database.modelMissing");
    if (remote && modelOverride.trim().isEmpty()) throw new IOException("Database.remoteModel");
    if (rules == null || rules.trim().isEmpty()) throw new IOException("Database.rulesMissing");
    JSONObject identity = new JSONObject();
    identity.put("model", modelHash(Paths.get(model)));
    String human = option(args, "-human-model");
    if (human != null) identity.put("humanModel", modelHash(Paths.get(human)));
    String ruleText = rules.trim();
    if (ruleText.startsWith("=")) ruleText = ruleText.substring(1).trim();
    identity.put("rules", new JSONObject(ruleText));
    identity.put("komi", java.math.BigDecimal.valueOf(komi).stripTrailingZeros().toPlainString());
    TreeMap<String, String> config = new TreeMap<>();
    String detectedConfig = option(args, "-config", "--config");
    String file =
        !remote && detectedConfig != null && Files.isRegularFile(Paths.get(detectedConfig))
            ? detectedConfig
            : configOverride.trim().isEmpty() ? detectedConfig : configOverride;
    if (file != null) {
      if (remote && configOverride.trim().isEmpty()) throw new IOException("Database.remoteConfig");
      parseConfig(
          new String(Files.readAllBytes(Paths.get(file)), java.nio.charset.StandardCharsets.UTF_8),
          config);
    }
    String overrides = option(args, "-override-config");
    if (overrides != null) parseConfig(overrides.replace(',', '\n'), config);
    // Timing, logs and worker counts do not change the evaluation's meaning.
    config
        .keySet()
        .removeIf(
            k ->
                k.startsWith("log")
                    || k.equals("numSearchThreads")
                    || k.equals("nnMaxBatchSize")
                    || k.equals("numEigenThreadsPerModel")
                    || k.equals("maxVisits")
                    || k.equals("maxPlayouts")
                    || k.equals("maxTime")
                    || k.equals("ponderingEnabled"));
    identity.put("config", new JSONObject(config));
    identity.put("runtime", new JSONObject(new TreeMap<>(runtime)));
    identity.put("pda", pda);
    return PositionKey.sha256(canonical(identity));
  }

  private static void parseConfig(String text, Map<String, String> map) {
    for (String line : text.split("\\r?\\n")) {
      int comment = line.indexOf('#');
      if (comment >= 0) line = line.substring(0, comment);
      int eq = line.indexOf('=');
      if (eq > 0) map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
    }
  }

  private static String option(List<String> args, String... names) {
    for (int i = 0; i < args.size(); i++)
      for (String name : names) {
        if (args.get(i).equals(name) && i + 1 < args.size()) return args.get(i + 1);
        if (args.get(i).startsWith(name + "=")) return args.get(i).substring(name.length() + 1);
      }
    return null;
  }

  public static synchronized String modelHash(Path model) throws Exception {
    Path path = model.toRealPath();
    String key = path + ":" + Files.size(path) + ":" + Files.getLastModifiedTime(path);
    String known = hashes.get(key);
    if (known != null) return known;
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream file = Files.newInputStream(path);
        InputStream data = path.toString().endsWith(".gz") ? new GZIPInputStream(file) : file) {
      byte[] buffer = new byte[65536];
      int count;
      while ((count = data.read(buffer)) >= 0) digest.update(buffer, 0, count);
    }
    String after = path + ":" + Files.size(path) + ":" + Files.getLastModifiedTime(path);
    if (!key.equals(after)) throw new IOException("Model changed while hashing");
    String hash = PositionKey.hex(digest.digest());
    hashes.put(key, hash);
    return hash;
  }

  public static String canonical(Object value) {
    if (value instanceof JSONObject) {
      JSONObject j = (JSONObject) value;
      List<String> keys = new ArrayList<>(j.keySet());
      Collections.sort(keys);
      StringBuilder out = new StringBuilder("{");
      for (String key : keys)
        out.append(JSONObject.quote(key)).append(':').append(canonical(j.get(key))).append(',');
      return out.append('}').toString();
    }
    if (value instanceof JSONArray) {
      JSONArray j = (JSONArray) value;
      StringBuilder out = new StringBuilder("[");
      for (int i = 0; i < j.length(); i++) out.append(canonical(j.get(i))).append(',');
      return out.append(']').toString();
    }
    return JSONObject.valueToString(value);
  }
}
