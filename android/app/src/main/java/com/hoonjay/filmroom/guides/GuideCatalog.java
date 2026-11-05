package com.hoonjay.filmroom.guides;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Data-only catalog. Stable IDs connect categories, recommendations and geometry. */
public final class GuideCatalog {
  public final List<JSONObject> categories = new ArrayList<>(), guides = new ArrayList<>();
  public final Map<String, JSONObject> byId = new LinkedHashMap<>();
  public final JSONObject layouts;
  public final String version;

  public static final class Selection {
    public final LinkedHashSet<String> categoryIds = new LinkedHashSet<>();
    public String guideId = "held-prop", prop = "individual";
    public int people = 2;
    public boolean visible = true;

    public Selection copy() {
      Selection s = new Selection();
      s.categoryIds.addAll(categoryIds);
      s.guideId = guideId;
      s.prop = prop;
      s.people = people;
      s.visible = visible;
      return s;
    }

    public JSONObject json() {
      JSONObject j = new JSONObject();
      try {
        j.put("schemaVersion", 1);
        j.put("categoryIds", new JSONArray(categoryIds));
        j.put("guideId", guideId);
        j.put("people", people);
        j.put("prop", prop);
        j.put("visible", visible);
      } catch (JSONException e) {
        throw new IllegalArgumentException(e);
      }
      return j;
    }
  }

  public static final class Match {
    public JSONObject guide;
    public int score;
    public String reason = "";
    public boolean primary;
  }

  public GuideCatalog(Context c) throws Exception {
    JSONObject content = read(c, "press-camera.manifest.json").getJSONObject("content");
    JSONObject catalog = read(c, content.getString("catalog"));
    version = catalog.getString("catalogVersion");
    if (catalog.getInt("schemaVersion") != 1) throw new IOException("지원하지 않는 가이드 데이터 버전입니다.");
    JSONArray cats = catalog.getJSONArray("categories"), gs = catalog.getJSONArray("guides");
    for (int i = 0; i < gs.length(); i++) {
      JSONObject g = gs.getJSONObject(i);
      String id = g.getString("id");
      if (byId.put(id, g) != null) throw new IOException("중복 가이드 ID: " + id);
      guides.add(g);
    }
    Set<String> catIds = new HashSet<>();
    for (int i = 0; i < cats.length(); i++) {
      JSONObject r = cats.getJSONObject(i);
      if (!catIds.add(r.getString("id"))) throw new IOException("중복 분류 ID");
      for (String key : new String[] {"primary", "extra"}) {
        JSONArray ids = r.getJSONArray(key);
        for (int j = 0; j < ids.length(); j++)
          if (!byId.containsKey(ids.getString(j))) throw new IOException("분류의 가이드 참조 오류");
      }
      categories.add(r);
    }
    JSONObject data = read(c, content.getString("layouts"));
    if (data.getInt("schemaVersion") != 1) throw new IOException("구도 데이터 버전 오류");
    layouts = new JSONObject();
    JSONObject files = data.getJSONObject("layoutFiles");
    java.util.Iterator<String> it = files.keys();
    while (it.hasNext()) {
      String id = it.next();
      JSONObject layout = read(c, files.getString(id));
      if (layout.getInt("schemaVersion") != 1 || !id.equals(layout.getString("layoutId")))
        throw new IOException("구도 ID/버전 오류");
      layouts.put(id, layout.getJSONObject("variants"));
    }
    for (JSONObject g : guides)
      if (!layouts.has(g.getString("layoutId")))
        throw new IOException("구도 누락: " + g.getString("id"));
  }

  public static JSONObject read(Context c, String path) throws Exception {
    try (InputStream in = c.getAssets().open(path);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      for (int n; (n = in.read(b)) != -1; ) out.write(b, 0, n);
      return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
    }
  }

  public JSONObject guide(String id) {
    JSONObject g = byId.get(id);
    return g == null ? guides.get(0) : g;
  }

  public void normalize(Selection s) {
    if (!byId.containsKey(s.guideId)) s.guideId = guides.get(0).optString("id");
    JSONObject g = guide(s.guideId);
    JSONArray n = g.optJSONArray("peopleOptions");
    boolean found = false;
    for (int i = 0; i < n.length(); i++) if (n.optInt(i) == s.people) found = true;
    if (!found) s.people = g.optInt("defaultPeople");
    s.prop = s.guideId.equals("held-prop") && s.prop.equals("shared") ? "shared" : "individual";
    Set<String> ids = new HashSet<>();
    for (JSONObject c : categories) ids.add(c.optString("id"));
    s.categoryIds.retainAll(ids);
  }

  public Selection restore(String json) {
    Selection s = new Selection();
    s.categoryIds.add("agreement");
    try {
      JSONObject j = new JSONObject(json);
      s.guideId = j.optString("guideId", s.guideId);
      s.people = j.optInt("people", s.people);
      s.prop = j.optString("prop", s.prop);
      s.visible = j.optBoolean("visible", true);
      JSONArray a = j.optJSONArray("categoryIds");
      if (a != null) {
        s.categoryIds.clear();
        for (int i = 0; i < a.length(); i++) s.categoryIds.add(a.optString(i));
      }
    } catch (Exception ignored) {
    }
    normalize(s);
    return s;
  }

  public static String normal(String s) {
    return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
  }

  public static List<String> tokens(String q) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String t : q.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      t = normal(t);
      if (!t.isEmpty() && !Arrays.asList("사진", "보도자료", "추천", "촬영", "가이드", "관련").contains(t))
        out.add(t);
    }
    return new ArrayList<>(out);
  }

  public static int hits(JSONObject j, List<String> tokens) {
    List<String> words = new ArrayList<>();
    words.add(j.optString("name"));
    JSONArray k = j.optJSONArray("keywords");
    if (k != null) for (int i = 0; i < k.length(); i++) words.add(k.optString(i));
    int count = 0;
    for (String t : tokens) {
      boolean found = false;
      for (String v : words) {
        v = normal(v);
        if (v.contains(t) || v.length() > 1 && t.contains(v)) {
          found = true;
          break;
        }
      }
      if (found) count++;
    }
    return count;
  }

  private static int index(JSONArray a, String id) {
    if (a == null) return -1;
    for (int i = 0; i < a.length(); i++) if (id.equals(a.optString(i))) return i;
    return -1;
  }

  public List<Match> recommend(Set<String> ids, String query, boolean all) {
    List<String> tokens = tokens(query);
    List<Match> result = new ArrayList<>();
    for (JSONObject g : guides) {
      Match m = new Match();
      m.guide = g;
      String gid = g.optString("id");
      int direct = hits(g, tokens);
      if (direct > 0) {
        m.score += direct * 60;
        m.reason = "키워드 일치";
        m.primary = true;
      }
      for (JSONObject cat : categories) {
        int p = index(cat.optJSONArray("primary"), gid),
            e = index(cat.optJSONArray("extra"), gid),
            h = hits(cat, tokens);
        boolean selected = ids.contains(cat.optString("id"));
        if (selected) {
          if (p >= 0) m.score += 40 - p;
          else if (e >= 0) m.score += 12;
        }
        if (h > 0) {
          if (p >= 0) m.score += 25 * h - p;
          else if (e >= 0) m.score += 5 * h;
        }
        if ((selected || h > 0)
            && (p >= 0 || e >= 0)
            && (m.reason.isEmpty() || !m.primary && p >= 0)) {
          m.reason = cat.optString("name") + (p >= 0 ? " 추천" : " 보완");
          m.primary = p >= 0;
        }
      }
      if (m.score > 0 || all || ids.isEmpty() && tokens.isEmpty()) {
        if (m.reason.isEmpty()) m.reason = "직접 선택";
        result.add(m);
      }
    }
    Collections.sort(result, (a, b) -> Integer.compare(b.score, a.score));
    return result;
  }

  public JSONArray drawing(Selection s, boolean landscape) {
    JSONObject g = guide(s.guideId), variants = layouts.optJSONObject(g.optString("layoutId"));
    if (variants == null) return null;
    JSONObject v = variants.optJSONObject(s.people + ":" + s.prop);
    if (v == null) v = variants.optJSONObject(g.optInt("defaultPeople") + ":individual");
    return v == null ? null : v.optJSONArray(landscape ? "landscape" : "portrait");
  }
}
