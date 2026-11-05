package com.hoonjay.filmroom.core;

/** Bounded still-capture AF handshake. Values follow Camera2 AF state constants.
 * A result from before the START request must never authorize the still capture.
 */
public final class CaptureFocusGate {
  public enum Decision {WAIT,READY,RETRY,UNREPORTED,FAILED}
  public static final long TIMEOUT_MS=1800;
  private final long deadline;
  private long triggerFrame=Long.MAX_VALUE;
  private long lastFrame=-1;
  private int retries,lastAf=-1;
  private boolean observedAf,moving;
  private Decision terminal;
  public CaptureFocusGate(long now){deadline=now+TIMEOUT_MS;}
  public static boolean required(boolean portrait,float manualFocus,boolean far,boolean locked,
      boolean tracking,boolean lost,boolean heldFocus,boolean autoLens){
    return !portrait&&manualFocus<0&&!far&&!(locked&&(!tracking||lost||heldFocus))&&autoLens;
  }
  public void triggered(long frame){if(terminal==null)triggerFrame=frame;}
  public Decision observe(long now,long frame,Integer af,boolean lensMoving){
    if(terminal!=null)return terminal;
    if(frame<triggerFrame||frame<=lastFrame)return poll(now);
    lastFrame=frame;
    moving=lensMoving;
    if(af!=null){observedAf=true;lastAf=af;}
    if(lastAf==4&&!moving)return terminal=Decision.READY;
    if(af!=null&&af==5){
      if(retries++==0&&now+250<deadline){triggerFrame=Long.MAX_VALUE;return Decision.RETRY;}
      return terminal=Decision.FAILED;
    }
    return poll(now);
  }
  public Decision poll(long now){
    if(terminal!=null)return terminal;
    if(now<deadline)return Decision.WAIT;
    if(triggerFrame==Long.MAX_VALUE)return terminal=Decision.FAILED;
    if(!moving&&lastAf==2)return terminal=Decision.READY;
    if(!moving&&!observedAf)return terminal=Decision.UNREPORTED;
    return terminal=Decision.FAILED;
  }
  public boolean complete(){return terminal!=null;}
}
