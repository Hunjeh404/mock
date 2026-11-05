package com.hoonjay.filmroom.core;

import java.util.*;

/** Conservative, local statistical estimate; not an exact reconstruction of an unknown edit. */
public final class ReferenceAnalyzer {
  public static final class Result {
    public Recipe recipe;
    public String note;
    public float confidence;
  }

  public static Result analyze(List<int[]> images, int representative) {
    if (images.isEmpty()) throw new IllegalArgumentException("No references");
    List<float[]> stats = new ArrayList<>();
    for (int[] pixels : images) {
      float[] hist = new float[256];
      double sat = 0, nr = 0, ng = 0, nb = 0;
      int neutral = 0, count = 0;
      for (int p : pixels) {
        float r = ((p >>> 16) & 255) / 255f,
            g = ((p >>> 8) & 255) / 255f,
            b = (p & 255) / 255f,
            l = ColorMath.luma(r, g, b),
            max = Math.max(r, Math.max(g, b)),
            min = Math.min(r, Math.min(g, b)),
            s = max <= 0 ? 0 : (max - min) / max;
        hist[Math.min(255, Math.round(l * 255))]++;
        sat += s;
        count++;
        if (s < .32f && l > .15f && l < .88f) {
          nr += r;
          ng += g;
          nb += b;
          neutral++;
        }
      }
      if (count == 0) throw new IllegalArgumentException("Empty reference");
      float q05 = q(hist, count, .05f), q10 = q(hist, count, .10f), q90 = q(hist, count, .90f);
      float
          warmth = neutral > count * .025 ? limit((float) ((nr - nb) / neutral) * 130, -40, 40) : 0,
          tint =
              neutral > count * .025
                  ? limit((float) (((nr + nb) * .5 - ng) / neutral) * 100, -25, 25)
                  : 0;
      stats.add(
          new float[] {
            warmth,
            tint,
            limit((q90 - q10) / .64f, .72f, 1.28f),
            limit((float) sat / count / .32f, .5f, 1.4f),
            limit(q05 * .25f, 0, .09f),
            neutral / (float) count
          });
    }
    representative = Math.max(0, Math.min(stats.size() - 1, representative));
    float[] merged = new float[6];
    for (int i = 0; i < 6; i++) {
      float[] v = new float[stats.size()];
      for (int j = 0; j < v.length; j++) v[j] = stats.get(j)[i];
      Arrays.sort(v);
      float med =
          v.length % 2 == 0 ? (v[v.length / 2 - 1] + v[v.length / 2]) * .5f : v[v.length / 2];
      merged[i] = med * .65f + stats.get(representative)[i] * .35f;
    }
    Recipe p = new Recipe();
    p.custom = true;
    p.name = "나만의 필름";
    p.warmth = Math.round(merged[0]);
    p.tint = Math.round(merged[1]);
    p.contrast = merged[2];
    p.saturation = merged[3];
    p.fade = merged[4];
    double variance = 0;
    for (float[] s : stats) variance += (s[0] - merged[0]) * (s[0] - merged[0]);
    boolean mixed = Math.sqrt(variance / stats.size()) > 12;
    Result result = new Result();
    result.recipe = p;
    result.confidence = merged[5];
    result.note =
        "참조 "
            + images.size()
            + "장 · 대표 사진 "
            + (representative + 1)
            + "번\n색감과 명암을 통계적으로 추정한 값입니다. 원래 필터의 정확한 복원값은 아닙니다."
            + (mixed ? "\n사진별 색조 차이가 큽니다. 같은 분위기의 사진으로 좁혀보세요." : "")
            + (merged[5] < .05f ? "\n중성색이 부족해 색온도 추정이 제한적입니다." : "");
    return result;
  }

  private static float q(float[] h, int n, float f) {
    float a = 0;
    for (int i = 0; i < h.length; i++) {
      a += h[i];
      if (a >= n * f) return i / 255f;
    }
    return 1;
  }

  private static float limit(float x, float a, float b) {
    return Math.max(a, Math.min(b, x));
  }
}
