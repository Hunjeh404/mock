package com.hoonjay.filmroom.core;

import java.util.*;

/** Bounded luminance detail. Tile cache supports both row and rotated column traversal. */
public final class DetailEnhancement {
  private DetailEnhancement() {}

  public static Developer.Source wrap(Developer.Source src, Recipe recipe) {
    float amount = Math.max(0, Math.min(.3f, recipe.detail * recipe.strength));
    if (amount == 0) return src;
    return new Developer.Source() {
      private final int w = src.width(), h = src.height(), tileSide = 32, stride = 34;
      private final LinkedHashMap<Long, float[]> tiles =
          new LinkedHashMap<Long, float[]>(256, .75f, true) {
            protected boolean removeEldestEntry(Map.Entry<Long, float[]> e) {
              return size() > 192;
            }
          };

      public int width() {
        return w;
      }

      public int height() {
        return h;
      }

      public int nativeWidth() {
        return src.nativeWidth();
      }

      public int nativeHeight() {
        return src.nativeHeight();
      }

      private float[] tile(int tx, int ty) {
        long key = ((long) ty << 32) | (tx & 0xffffffffL);
        float[] a = tiles.get(key);
        if (a != null) return a;
        a = new float[stride * stride * 3];
        float[] rgb = new float[3];
        for (int y = 0; y < stride; y++)
          for (int x = 0; x < stride; x++) {
            src.rgb(
                Math.max(0, Math.min(w - 1, tx * tileSide + x - 1)),
                Math.max(0, Math.min(h - 1, ty * tileSide + y - 1)),
                rgb);
            System.arraycopy(rgb, 0, a, (y * stride + x) * 3, 3);
          }
        tiles.put(key, a);
        return a;
      }

      public synchronized void rgb(int x, int y, float[] out) {
        float[] a = tile(x / tileSide, y / tileSide);
        int i = ((y % tileSide + 1) * stride + (x % tileSide + 1)) * 3;
        float center = luma(a, i),
            neighbor =
                (luma(a, i - 3)
                        + luma(a, i + 3)
                        + luma(a, i - stride * 3)
                        + luma(a, i + stride * 3))
                    * .25f;
        float delta = Math.max(-.012f, Math.min(.012f, (center - neighbor) * amount));
        for (int c = 0; c < 3; c++) out[c] = Math.max(0, a[i + c] + delta);
      }

      private float luma(float[] a, int i) {
        return .2126f * a[i] + .7152f * a[i + 1] + .0722f * a[i + 2];
      }
    };
  }
}
