package com.hoonjay.filmroom.core;
/** Explicit brightness changes may adjust AUTO values only, including while a point is locked. */
public final class TouchExposure {
  public final int iso;
  public final long time;
  private TouchExposure(int iso,long time){this.iso=iso;this.time=time;}
  public static int dragSteps(float delta,float step){if(!Float.isFinite(delta)||delta==0)return 0;return (delta>0?1:-1)*Math.max(1,Math.round(Math.abs(delta)/Math.max(.1f,step)));}
  public static TouchExposure adjust(int manualIso,long manualTime,int autoIso,long autoTime,float stops,
      int minIso,int maxIso,long minTime,long maxTime){
    int iso=manualIso>0?manualIso:autoIso;long time=manualTime>0?manualTime:autoTime;
    if(!Float.isFinite(stops)||(manualIso>0&&manualTime>0))return new TouchExposure(iso,time);
    double gain=Math.pow(2,Math.max(-16,Math.min(16,stops)));
    if(manualIso==0){
      int desired=(int)Math.max(minIso,Math.min(maxIso,Math.round(iso*gain)));
      if(manualTime==0)time=Math.max(minTime,Math.min(maxTime,Math.round(time*gain*iso/Math.max(1,desired))));
      iso=desired;
    }else time=Math.max(minTime,Math.min(maxTime,Math.round(time*gain)));
    return new TouchExposure(iso,time);
  }
}
