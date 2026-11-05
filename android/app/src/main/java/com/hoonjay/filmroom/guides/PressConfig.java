package com.hoonjay.filmroom.guides;

import android.content.Context;
import com.hoonjay.filmroom.core.PresetRules;
import com.hoonjay.filmroom.core.Recipe;
import com.hoonjay.filmroom.storage.RecipeJson;
import java.io.IOException;
import org.json.JSONObject;

/**
 * Product policy and preset loading. Capture invariants are intentionally enforced, not user
 * options.
 */
public final class PressConfig {
  public static final double RATIO = 1.5;
  public final Recipe recipe;

  public PressConfig(Context c) throws Exception {
    JSONObject manifest = GuideCatalog.read(c, "press-camera.manifest.json");
    if (manifest.getInt("schemaVersion") != 1) throw new IOException("앱 매니페스트 버전 오류");
    JSONObject camera = manifest.getJSONObject("camera");
    if (camera.getDouble("ratio") != RATIO || !camera.getString("savePolicy").equals("RAW+JPG"))
      throw new IOException("보도사진 저장 정책 오류");
    recipe =
        RecipeJson.read(
            GuideCatalog.read(c, manifest.getJSONObject("content").getString("preset")));
    PresetRules.validate(recipe);
    if (!recipe.id.equals("press-clean")) throw new IOException("보도 클린 프리셋 ID 오류");
  }
}
