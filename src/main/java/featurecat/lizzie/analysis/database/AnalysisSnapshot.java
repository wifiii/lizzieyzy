package featurecat.lizzie.analysis.database;

import featurecat.lizzie.analysis.MoveData;
import java.util.*;
import org.json.*;

/** One coherent ordinary snapshot and an independently replaceable pass result. */
public final class AnalysisSnapshot {
  public int visits, passVisits;
  public double winrate, score, stdev, passScore;
  public List<MoveData> normal = new ArrayList<>(), supplemental = new ArrayList<>();
  public List<Double> ownership;
  public long savedAt;

  private static JSONObject move(MoveData m, PositionKey key, boolean canonical) {
    JSONObject j = new JSONObject();
    j.put("coord", key.coordinate(m.coordinate, canonical));
    j.put("visits", m.playouts);
    j.put("rank", m.order);
    j.put("winrate", m.winrate);
    j.put("score", m.scoreMean);
    j.put("stdev", m.scoreStdev);
    j.put("policy", m.policy);
    j.put("lcb", m.lcb);
    j.put("symmetry", m.isSymmetry);
    if (m.symmetryOf != null) j.put("symmetryOf", key.coordinate(m.symmetryOf, canonical));
    JSONArray pv = new JSONArray();
    if (m.variation != null) for (String c : m.variation) pv.put(key.coordinate(c, canonical));
    j.put("pv", pv);
    if (m.pvVisits != null) j.put("pvVisits", new JSONArray(m.pvVisits));
    List<Double> own = key.ownership(m.movesEstimateArray, canonical);
    if (own != null) j.put("ownership", new JSONArray(own));
    return j;
  }

  private static MoveData move(JSONObject j, PositionKey key) {
    MoveData m = new MoveData();
    m.coordinate = key.coordinate(j.getString("coord"), false);
    m.playouts = j.getInt("visits");
    m.order = j.getInt("rank");
    m.winrate = j.getDouble("winrate");
    m.scoreMean = j.getDouble("score");
    m.scoreStdev = j.getDouble("stdev");
    m.policy = j.optDouble("policy", 0);
    m.lcb = j.optDouble("lcb", 0);
    m.isSymmetry = j.optBoolean("symmetry", false);
    m.isKataData = true;
    if (j.has("symmetryOf")) m.symmetryOf = key.coordinate(j.getString("symmetryOf"), false);
    m.variation = new ArrayList<>();
    JSONArray pv = j.getJSONArray("pv");
    for (int i = 0; i < pv.length(); i++) m.variation.add(key.coordinate(pv.getString(i), false));
    if (j.has("pvVisits")) {
      m.pvVisits = new ArrayList<>();
      JSONArray a = j.getJSONArray("pvVisits");
      for (int i = 0; i < a.length(); i++) m.pvVisits.add(a.getString(i));
    }
    if (j.has("ownership"))
      m.movesEstimateArray =
          new ArrayList<>(key.ownership(doubles(j.getJSONArray("ownership")), false));
    return m;
  }

  private static List<Double> doubles(JSONArray a) {
    List<Double> out = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) out.add(a.getDouble(i));
    return out;
  }

  public String ordinaryJson(PositionKey key) {
    JSONObject j = new JSONObject();
    j.put("visits", visits);
    j.put("winrate", winrate);
    j.put("score", score);
    j.put("stdev", stdev);
    JSONArray rows = new JSONArray(), extras = new JSONArray();
    for (MoveData m : normal) rows.put(move(m, key, true));
    for (MoveData m : supplemental) extras.put(move(m, key, true));
    j.put("normal", rows);
    j.put("supplemental", extras);
    List<Double> own = key.ownership(ownership, true);
    if (own != null) j.put("ownership", new JSONArray(own));
    return j.toString();
  }

  public void readOrdinary(String json, PositionKey key) {
    JSONObject j = new JSONObject(json);
    visits = j.getInt("visits");
    winrate = j.getDouble("winrate");
    score = j.getDouble("score");
    stdev = j.getDouble("stdev");
    JSONArray a = j.getJSONArray("normal"), b = j.getJSONArray("supplemental");
    for (int i = 0; i < a.length(); i++) normal.add(move(a.getJSONObject(i), key));
    for (int i = 0; i < b.length(); i++) supplemental.add(move(b.getJSONObject(i), key));
    MoveData.resolveSymmetryOrders(normal);
    if (j.has("ownership")) ownership = key.ownership(doubles(j.getJSONArray("ownership")), false);
  }

  public static List<MoveData> copy(List<MoveData> moves) {
    List<MoveData> result = new ArrayList<>();
    for (MoveData m : moves) result.add(m.copy());
    return result;
  }
}
