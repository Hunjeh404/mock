package com.hoonjay.filmroom.core;

/** Small floating-point preview source retains linear precision until final screen display. */
public final class SampledSource implements Developer.Source {
  private final int w, h,nativeW,nativeH;
  private final float[] data;

  public SampledSource(Developer.Source src, int maxWidth) {
    nativeW=src.nativeWidth();nativeH=src.nativeHeight();
    w = Math.min(maxWidth, src.width());
    h = Math.max(1, (int) Math.round(src.height() * w / (double) src.width()));
    data = new float[w * h * 3];
    float[] rgb = new float[3];
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        src.rgb(
            Math.min(src.width() - 1, x * src.width() / w),
            Math.min(src.height() - 1, y * src.height() / h),
            rgb);
        System.arraycopy(rgb, 0, data, (y * w + x) * 3, 3);
      }
  }

  public int nativeWidth(){return nativeW;}public int nativeHeight(){return nativeH;}
  public int width() {
    return w;
  }

  public int height() {
    return h;
  }

  public void rgb(int x, int y, float[] out) {
    System.arraycopy(data, (y * w + x) * 3, out, 0, 3);
  }
}
