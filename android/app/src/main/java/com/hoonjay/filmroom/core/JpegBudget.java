package com.hoonjay.filmroom.core;

import java.io.IOException;

/** Actual encoded-byte budget, independent of a platform's JPEG encoder. */
public final class JpegBudget {
  public interface Encoder { long encode(int quality) throws IOException; }

  private JpegBudget() {}

  /** Highest tested quality in 70..100 that fits; -1 requests a smaller image. */
  public static int selectQuality(Encoder encoder, long maxBytes) throws IOException {
    if (encoder.encode(100) <= maxBytes) return 100;
    if (encoder.encode(70) > maxBytes) return -1;
    int lo = 70, hi = 99;
    while (lo < hi) {
      int mid = (lo + hi + 1) / 2;
      if (encoder.encode(mid) <= maxBytes) lo = mid; else hi = mid - 1;
    }
    // Leave the final candidate on disk, never the last over-budget trial.
    if (encoder.encode(lo) > maxBytes) throw new IOException("JPG 용량 제한 검증 실패");
    return lo;
  }

  public static int[] smaller(int width, int height, long encodedBytes, long maxBytes) {
    double scale = Math.min(.9, Math.sqrt(maxBytes / (double)Math.max(1, encodedBytes)) * .95);
    return new int[] {Math.max(1, (int)Math.floor(width*scale)),
        Math.max(1, (int)Math.floor(height*scale))};
  }
}
