package com.hoonjay.filmroom.core;

/** Bounded fallback when both physical and logical AE status are absent. Never override SEARCHING. */
public final class AeConvergence {
  private double energy;
  private long stableSince;
  private int samples;
  public void reset(){energy=0;stableSince=0;samples=0;}
  public void observe(int iso,long nanos,long now){
    if(iso<=0||nanos<=0){reset();return;}
    double next=(double)iso*nanos;
    if(energy<=0||Math.abs(Math.log(next/energy)/Math.log(2))>.1){energy=next;stableSince=now;samples=1;}else samples++;
  }
  public boolean ready(boolean reportedSettled,boolean statusMissing,long now,long lastAdjustment){
    return now-lastAdjustment>=900 && (reportedSettled || statusMissing&&samples>=4&&now-stableSince>=900);
  }
}
