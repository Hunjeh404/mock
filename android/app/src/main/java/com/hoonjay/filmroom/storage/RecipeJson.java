package com.hoonjay.filmroom.storage;

import com.hoonjay.filmroom.core.*;
import org.json.*;

public final class RecipeJson {
  public static JSONObject write(Recipe p) {
    JSONObject o = new JSONObject();
    try {
      o.put("id", p.id);
      o.put("name", p.name);
      o.put("english", p.english);
      o.put("custom", p.custom);
      o.put("strength", p.strength);
      o.put("exposure", p.exposure);
      o.put("warmth", p.warmth);
      o.put("tint", p.tint);
      o.put("contrast", p.contrast);
      o.put("saturation", p.saturation);
      o.put("fade", p.fade);
      o.put("highlights", p.highlights);
      o.put("shadows", p.shadows);
      o.put("grain", p.grain);
      o.put("bloom", p.bloom);
      o.put("vignette", p.vignette);
      o.put("hue",arr(p.hue));o.put("hslSaturation",arr(p.hslSaturation));o.put("luminance",arr(p.luminance));
      JSONArray curves=new JSONArray();for(float[] curve:p.curves)curves.put(arr(curve));o.put("curves",curves);
      o.put("skinBrightness",p.skinBrightness);o.put("skinColor",p.skinColor);o.put("skinBlush",p.skinBlush);o.put("skinTexture",p.skinTexture);
      o.put("detail",p.detail);o.put("noiseLuma",p.noiseLuma);o.put("noiseChroma",p.noiseChroma);
      o.put("balance", arr(p.balance));
      o.put("shadowColor", arr(p.shadowColor));
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
    return o;
  }

  public static Recipe read(JSONObject o) {
    Recipe p = new Recipe();
    p.id = o.optString("id", "custom");
    p.name = o.optString("name", "커스텀");
    p.english = o.optString("english", "CUSTOM FILM");
    p.custom = o.optBoolean("custom", false);
    p.strength = (float) o.optDouble("strength", 1);
    p.exposure = (float) o.optDouble("exposure", 0);
    p.warmth = (float) o.optDouble("warmth", 0);
    p.tint = (float) o.optDouble("tint", 0);
    p.contrast = (float) o.optDouble("contrast", 1);
    p.saturation = (float) o.optDouble("saturation", 1);
    p.fade = (float) o.optDouble("fade", 0);
    p.highlights = (float) o.optDouble("highlights", 0);
    p.shadows = (float) o.optDouble("shadows", 0);
    p.grain = (float) o.optDouble("grain", 0);
    p.bloom = (float) o.optDouble("bloom", 0);
    p.vignette = (float) o.optDouble("vignette", 0);
    p.balance = floats(o.optJSONArray("balance"), p.balance);
    p.shadowColor = floats(o.optJSONArray("shadowColor"), p.shadowColor);
    p.hue=floats(o.optJSONArray("hue"),p.hue);p.hslSaturation=floats(o.optJSONArray("hslSaturation"),p.hslSaturation);p.luminance=floats(o.optJSONArray("luminance"),p.luminance);
    JSONArray curves=o.optJSONArray("curves");if(curves!=null&&curves.length()==4)for(int i=0;i<4;i++)p.curves[i]=floats(curves.optJSONArray(i),p.curves[i]);
    p.skinBrightness=(float)o.optDouble("skinBrightness",0);p.skinColor=(float)o.optDouble("skinColor",0);p.skinBlush=(float)o.optDouble("skinBlush",0);p.skinTexture=(float)o.optDouble("skinTexture",0);
    p.detail=(float)o.optDouble("detail",0);p.noiseLuma=(float)o.optDouble("noiseLuma",0);p.noiseChroma=(float)o.optDouble("noiseChroma",0);
    return p;
  }

  public static JSONArray arr(float[] f) throws JSONException {
    JSONArray a = new JSONArray();
    for (float v : f) a.put(v);
    return a;
  }

  public static float[] floats(JSONArray a, float[] fallback) {
    if (a == null) return fallback;
    float[] f = new float[a.length()];
    for (int i = 0; i < f.length; i++) f[i] = (float) a.optDouble(i, 0);
    return f.length == fallback.length ? f : fallback;
  }
}
