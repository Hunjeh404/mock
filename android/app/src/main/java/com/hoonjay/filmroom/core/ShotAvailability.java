package com.hoonjay.filmroom.core;
/** Missing files are hidden, not destroyed. Unknown access/volume state must not imply deletion. */
public final class ShotAvailability {
  public enum Presence {PRESENT,MISSING,UNKNOWN}
  private ShotAvailability(){}
  public static boolean visible(boolean tombstone,boolean hasLocalRaw,Presence raw,Presence render,boolean unfinished){
    return !tombstone&&(hasLocalRaw||unfinished||raw!=Presence.MISSING||render!=Presence.MISSING);
  }
}
