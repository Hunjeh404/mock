package com.hoonjay.filmroom.core;

/** Display-signal clipping risk, not a guarantee that RAW sensor detail is lost. */
public final class ClippingRisk {
  public static final int HIGH = 250, LOW = 5;

  private ClippingRisk() {}

  public static boolean high(int r, int g, int b) {
    return Math.max(r, Math.max(g, b)) >= HIGH;
  }

  public static boolean low(int r, int g, int b) {
    return Math.max(r, Math.max(g, b)) <= LOW;
  }

  public static boolean significant(int clipped, int samples) {
    return samples > 0 && clipped >= Math.max(2, (int) Math.ceil(samples * .002));
  }

  public static final class Tracker {
    private int highClear, lowClear;
    public boolean highlights, shadows;

    public void update(int high, int low, int samples) {
      if (significant(high, samples)) {
        highlights = true;
        highClear = 0;
      } else if (++highClear >= 2) highlights = false;
      if (significant(low, samples)) {
        shadows = true;
        lowClear = 0;
      } else if (++lowClear >= 2) shadows = false;
    }
  }
}
