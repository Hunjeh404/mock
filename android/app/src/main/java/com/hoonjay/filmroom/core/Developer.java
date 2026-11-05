package com.hoonjay.filmroom.core;

import java.io.*;

/** Linear source -> floating-point grade -> optional glow -> 16-bit PNG. */
public final class Developer {
  public interface Source {
    int width();

    int height();

    void rgb(int x, int y, float[] out);
    default int nativeWidth(){return width();}
    default int nativeHeight(){return height();}
  }

  public interface Progress {
    void update(int percent);
  }

  public interface RowWriter {
    void row(int y, float[] rgb) throws IOException;
  }

  public static Source raw(RawFrame r) {
    r.prepare();
    return new Source() {
      public int width() {
        return r.activeWidth;
      }

      public int height() {
        return r.activeHeight;
      }

      public void rgb(int x, int y, float[] out) {
        r.rgb(x, y, out);
      }
    };
  }

  public static void render(
      Source source, Recipe recipe, int rotation, double ratio, OutputStream out, Progress progress)
      throws IOException {
    Geometry geo = new Geometry(source.width(), source.height(), rotation, ratio);
    try (Png16Writer png = new Png16Writer(out, geo.width, geo.height)) {
      renderRows(source, recipe, rotation, ratio, (y, row) -> png.row(row), progress);
    }
  }

  /** Float rows shared by PNG16 and final-stage JPEG quantization. */
  public static void renderRows(Source source, Recipe recipe, int rotation, double ratio,
      RowWriter writer, Progress progress) throws IOException {
    source=DetailEnhancement.wrap(NoiseReduction.wrap(source,recipe),recipe);boolean advanced=AdvancedColor.active(recipe);
    Geometry geo = new Geometry(source.width(), source.height(), rotation, ratio);
    int gw = Math.min(192, geo.width),
        gh = Math.max(1, (int) Math.round(gw * geo.height / (double) geo.width));
    float[] glow = glow(source, recipe, geo, gw, gh), tmp = new float[3];
    float[] row = new float[geo.width * 3];
      for (int y = 0; y < geo.height; y++) {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
        for (int x = 0; x < geo.width; x++) {
          source.rgb(geo.sourceX(x, y), geo.sourceY(x, y), tmp);
          ColorMath.grade(
              tmp, recipe, x / (float) geo.width, y / (float) geo.height, x + y * geo.width,advanced);
          if (glow != null) {
            int gx = Math.min(gw - 1, x * gw / geo.width),
                gy = Math.min(gh - 1, y * gh / geo.height),
                i = (gy * gw + gx) * 3;
            for (int c = 0; c < 3; c++)
              tmp[c] =
                  ColorMath.clamp(tmp[c] + glow[i + c] * recipe.bloom * .22f * recipe.strength);
          }
          row[x * 3] = tmp[0];
          row[x * 3 + 1] = tmp[1];
          row[x * 3 + 2] = tmp[2];
        }
        writer.row(y, row);
        if (progress != null && y % Math.max(1, geo.height / 50) == 0)
          progress.update(y * 100 / geo.height);
      }
    if (progress != null) progress.update(100);
  }

  public static int[] preview(
      Source source, Recipe recipe, int rotation, double ratio, int maxWidth, int[] dimensions) {
    source=DetailEnhancement.wrap(NoiseReduction.wrap(source,recipe),recipe);boolean advanced=AdvancedColor.active(recipe);
    Geometry geo = new Geometry(source.width(), source.height(), rotation, ratio);
    int w = Math.min(maxWidth, geo.width),
        h = Math.max(1, (int) Math.round(geo.height * w / (double) geo.width));
    dimensions[0] = w;
    dimensions[1] = h;
    int[] argb = new int[w * h];
    int gw = Math.min(192, geo.width),
        gh = Math.max(1, (int) Math.round(gw * geo.height / (double) geo.width));
    float[] glow = glow(source, recipe, geo, gw, gh);
    float[] rgb = new float[3];
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        int ox = x * geo.width / w, oy = y * geo.height / h;
        source.rgb(geo.sourceX(ox, oy), geo.sourceY(ox, oy), rgb);
        ColorMath.grade(rgb, recipe, ox / (float) geo.width, oy / (float) geo.height, ox + oy * geo.width,advanced);
        if (glow != null) {
          int gx = Math.min(gw - 1, ox * gw / geo.width),
              gy = Math.min(gh - 1, oy * gh / geo.height), i = (gy * gw + gx) * 3;
          for (int c = 0; c < 3; c++)
            rgb[c] = ColorMath.clamp(rgb[c] + glow[i+c] * recipe.bloom * .22f * recipe.strength);
        }
        argb[y * w + x] =
            0xff000000
                | (Math.round(rgb[0] * 255) << 16)
                | (Math.round(rgb[1] * 255) << 8)
                | Math.round(rgb[2] * 255);
      }
    return argb;
  }

  private static float[] glow(Source source, Recipe recipe, Geometry geo, int gw, int gh) {
    float[] glow = null, tmp = new float[3];
    if (recipe.bloom > 0) {
      glow = new float[gw * gh * 3];
      Recipe base = recipe.copy();
      base.grain = 0;
      base.vignette = 0;
      base.bloom = 0;
      for (int y = 0; y < gh; y++)
        for (int x = 0; x < gw; x++) {
          int ox = Math.min(geo.width - 1, x * geo.width / gw),
              oy = Math.min(geo.height - 1, y * geo.height / gh);
          source.rgb(geo.sourceX(ox, oy), geo.sourceY(ox, oy), tmp);
          ColorMath.grade(tmp, base, x / (float) gw, y / (float) gh, 0);
          float l = ColorMath.luma(tmp[0], tmp[1], tmp[2]), w = ColorMath.clamp((l - .60f) / .4f);
          for (int c = 0; c < 3; c++) glow[(y * gw + x) * 3 + c] = tmp[c] * w;
        }
      glow = blur(glow, gw, gh);
    }
    return glow;
  }

  private static float[] blur(float[] a, int w, int h) {
    for (int pass = 0; pass < 3; pass++) {
      float[] b = new float[a.length];
      for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++)
          for (int c = 0; c < 3; c++) {
            float sum = 0;
            for (int i = -3; i <= 3; i++)
              sum += a[(y * w + Math.max(0, Math.min(w - 1, x + i))) * 3 + c];
            b[(y * w + x) * 3 + c] = sum / 7;
          }
      float[] d = new float[a.length];
      for (int y = 0; y < h; y++)
        for (int x = 0; x < w; x++)
          for (int c = 0; c < 3; c++) {
            float sum = 0;
            for (int i = -3; i <= 3; i++)
              sum += b[(Math.max(0, Math.min(h - 1, y + i)) * w + x) * 3 + c];
            d[(y * w + x) * 3 + c] = sum / 7;
          }
      a = d;
    }
    return a;
  }
}
