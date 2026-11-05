package com.hoonjay.filmroom.core;

/**
 * Keep photo exposure separate from the shorter preview exposure used for a responsive viewfinder.
 */
public final class ExposureMath {
  private ExposureMath() {}

  /** Entire visible frame, equally weighted, before film or exposure simulation. */
  public static float sceneMean(float[] linearLuma, int width, int height) {
    double sum = 0, weights = 0;
    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
      double weight = 1;
      sum += Math.max(0, linearLuma[y * width + x]) * weight;
      weights += weight;
    }
    return (float) (sum / Math.max(weights, .00001));
  }

  /** Neutral-frame brightness error in stops. Median protects midtones; P75 restores diffuse whites.
   * P98 of the brightest RGB channel protects broad highlights while excluding tiny lamps/speculars.
   * This is a conservative histogram heuristic, not semantic white/gray recognition or vendor HDR. */
  public static double brightnessError(MeteringStats stats,float userEv){
    if(stats==null||!stats.valid()||!Float.isFinite(userEv))return 0;
    double middle=Math.max(.25/Math.max(.00001,stats.median),.48/Math.max(.00001,stats.upper));
    double headroom=.92/Math.max(.00001,stats.highlight);
    double error=Math.log(Math.min(middle,headroom))/Math.log(2)+userEv;
    // Positive user EV may intentionally clip. Do not add more auto bias on already clipped fields.
    if(stats.clipped>=.02f)error=Math.min(0,error);
    return Math.max(-16,Math.min(16,error));
  }

  /** Real positive Camera2 exposure correction, separate from user EV and limited to +2EV.
   * A deadband and at most two hardware steps per settled update prevent visible pumping. */
  public static int autoCorrection(MeteringStats stats,int userSteps,float step,int current,
      int hardwareMin,int hardwareMax){
    if(stats==null||!stats.valid()||!Float.isFinite(step)||step<=0)return current;
    double error=brightnessError(stats,userSteps*step);
    int max=Math.max(0,Math.min((int)Math.floor(2/step+1e-5),hardwareMax-userSteps));
    int min=Math.min(max,Math.max(0,hardwareMin-userSteps));
    int move=Math.abs(error)<.18?0:Math.max(-2,Math.min(2,(int)Math.round(error/step)));
    return Math.max(min,Math.min(max,current+move));
  }

  public static float previewGain(int actualIso, long actualTime, int photoIso, long photoTime) {
    double gain = Math.max(1, photoIso) / (double)Math.max(1, actualIso)
        * Math.max(1L, photoTime) / Math.max(1., actualTime);
    return (float)Math.max(1/1024., Math.min(1024, gain));
  }

  public static double targetEnergy(int measuredIso,long measuredNanos,MeteringStats stats,float ev){
    return Math.max(1,measuredIso)*Math.max(1.,measuredNanos)*Math.pow(2,brightnessError(stats,ev));
  }

  public static double pointEnergy(int iso,long nanos,MeteringStats stats,float ev){
    if(stats==null||!stats.valid())return Math.max(1,iso)*Math.max(1.,nanos);
    return Math.max(1,iso)*Math.max(1.,nanos)*.18/Math.max(.00001,stats.mean)*Math.pow(2,ev);
  }

  public static float meter(
      float mean, int actualIso, long actualNanos, int photoIso, long photoNanos) {
    double ratio =
        Math.max(.00001, mean)
            / .18
            * Math.max(1, photoIso)
            / Math.max(1., actualIso)
            * Math.max(1, photoNanos)
            / Math.max(1., actualNanos);
    return (float) (Math.log(Math.max(.00001, ratio)) / Math.log(2));
  }
}
