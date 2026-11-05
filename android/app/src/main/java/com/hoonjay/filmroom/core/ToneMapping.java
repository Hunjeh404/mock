package com.hoonjay.filmroom.core;

/** Display tone curves shared by the Camera2 ISP and high precision RAW development. */
public final class ToneMapping {
  private ToneMapping() {}

  /** Neutral scene-linear view transform. A shared RGB scale preserves hue; the
   * rational shoulder retains distinctions above display white instead of hard clipping.
   * Midtones below .8 are unchanged. This is not an automatic exposure adjustment.
   */
  public static void neutralRaw(float[] rgb){
    float peak=Math.max(rgb[0],Math.max(rgb[1],rgb[2]));
    if(peak>.8f){double excess=peak-.8;float mapped=(float)(.8+.2*excess/(.2+excess)),scale=mapped/peak;
      for(int c=0;c<3;c++)rgb[c]=rgb[c]==peak?mapped:rgb[c]*scale;
    }
  }

  // More samples near black, where the sRGB transfer curve changes most quickly.
  public static float[] srgbCurve(int maxPoints) {
    int count = Math.max(2, Math.min(128, maxPoints));
    float[] curve = new float[count * 2];
    for (int i = 0; i < count; i++) {
      float t = i / (float) (count - 1), x = t * t;
      curve[i * 2] = x;
      curve[i * 2 + 1] = ColorMath.clamp(ColorMath.toSrgb(x));
    }
    return curve;
  }

  public static float map(float linear, float[] curve) {
    if (curve == null || curve.length < 4) return ColorMath.clamp(ColorMath.toSrgb(linear));
    float x = ColorMath.clamp(linear);
    int lo = 0, hi = curve.length / 2 - 1;
    while (hi - lo > 1) {
      int mid = (lo + hi) >>> 1;
      if (curve[mid * 2] <= x) lo = mid; else hi = mid;
    }
    float t = ColorMath.clamp((x - curve[lo * 2]) /
        Math.max(.0000001f, curve[hi * 2] - curve[lo * 2]));
    return curve[lo * 2 + 1] * (1 - t) + curve[hi * 2 + 1] * t;
  }

  public static float workingLinear(float sensorLinear, float boost, float[] curve) {
    // The recipe consumes display-referred linear sRGB. The ISP has already applied
    // this curve to the live image; apply it to RAW too, without any 8-bit buffer.
    float value = Math.max(0, sensorLinear) * boost;
    // Keep over-white RAW values available to negative exposure edits. The live
    // 8-bit ISP image cannot display these values, but the RAW source still can.
    if (value > 1) return ColorMath.toLinear(map(1, curve)) + value - 1;
    return ColorMath.toLinear(map(value, curve));
  }
}
