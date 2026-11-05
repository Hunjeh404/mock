package com.hoonjay.filmroom.core;
/** Rotation/acceleration cues for temporary touch selections; not a metric position tracker. */
public final class TouchMotion {
  private boolean active,pinned;
  private double rx,ry,rz;
  private long gyroTime,accelerationStart;
  public void select(boolean lock){active=true;pinned=lock;rx=ry=rz=0;gyroTime=accelerationStart=0;}
  public void clear(){active=false;pinned=false;rx=ry=rz=0;gyroTime=accelerationStart=0;}
  public boolean active(){return active;}
  public boolean rotation(float x,float y,float z,long nanos){
    if(!active||pinned)return false;
    if(gyroTime==0){gyroTime=nanos;return false;}
    double dt=(nanos-gyroTime)/1e9;gyroTime=nanos;
    if(dt<=0||dt>.25){rx=ry=rz=0;return false;}
    if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))return false;
    rx+=x*dt;ry+=y*dt;rz+=z*dt;
    if(Math.sqrt(rx*rx+ry*ry+rz*rz)>=Math.toRadians(8)){clear();return true;}return false;
  }
  public boolean acceleration(float x,float y,float z,long nanos){
    if(!active||pinned)return false;
    double magnitude=Math.sqrt(x*x+y*y+z*z);
    if(!Double.isFinite(magnitude)||magnitude<1.2){accelerationStart=0;return false;}
    if(accelerationStart==0)accelerationStart=nanos;
    if(nanos-accelerationStart>=160000000L){clear();return true;}return false;
  }
}
