package com.hoonjay.filmroom.core;

/** Camera preview geometry, independent of Android so all display rotations can be checked. */
public final class PreviewGeometry {
  private PreviewGeometry() {}

  public static int normalize(int degrees) { return (degrees % 360 + 360) % 360; }

  // SurfaceTexture already includes the producer's camera rotation and vertical flip.
  // Inspect its up vector (q=0 ignores translation) instead of rotating by SENSOR_ORIENTATION twice.
  public static int textureRotation(float[] st) {
    float x = -st[4], y = -st[5];
    if (Math.abs(x) > Math.abs(y)) return x > 0 ? 90 : 270;
    return y >= 0 ? 0 : 180;
  }

  public static int additionalRotation(int sensor, int display, float[] st) {
    return normalize(sensor - display - textureRotation(st));
  }

  /** Largest centered frame inside the available screen, with no reserved toolbar margins. */
  public static int[] fit(int width, int height, double landscapeRatio) {
    double aspect = width > height ? landscapeRatio : 1 / landscapeRatio;
    int fw = width, fh = (int) Math.round(fw / aspect);
    if (fh > height) { fh = height; fw = (int) Math.round(fh * aspect); }
    int left = (width - fw) / 2, top = (height - fh) / 2;
    return new int[] {left, top, left + fw, top + fh};
  }

  /** 1 opens, -1 closes, 0 leaves film visibility unchanged. Distances are density-independent. */
  public static int filmSwipe(float dx, float dy) {
    if (Math.abs(dy) > 40 && Math.abs(dy) > Math.abs(dx) * 1.2f)
      return dy < 0 ? 1 : -1;
    return 0;
  }
}
