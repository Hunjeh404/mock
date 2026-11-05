package com.hoonjay.filmroom.core;

/** One draggable clone, normalized across rotation and clamped away from cutouts. */
public final class FloatingShutterState {
  public boolean visible,dragging;
  public float nx=.8f,ny=.65f,x,y;
  private float left,top,right,bottom,oldNx,oldNy;
  private boolean oldVisible;
  public void bounds(float l,float t,float r,float b) {
    left=l;top=t;right=Math.max(l,r);bottom=Math.max(t,b);
    placeNormalized();
  }
  private void placeNormalized(){x=left+(right-left)*nx;y=top+(bottom-top)*ny;}
  public void begin(float px,float py){oldVisible=visible;oldNx=nx;oldNy=ny;visible=true;dragging=true;move(px,py);}
  public void move(float px,float py) {
    x=Math.max(left,Math.min(right,px));y=Math.max(top,Math.min(bottom,py));
    nx=(x-left)/Math.max(1,right-left);ny=(y-top)/Math.max(1,bottom-top);
  }
  public void finish(float homeX,float homeY,float homeRadius) {
    if(Math.hypot(x-homeX,y-homeY)<homeRadius)visible=false;
    dragging=false;
  }
  public void cancel(){visible=oldVisible;nx=oldNx;ny=oldNy;dragging=false;placeNormalized();}
  public boolean hit(float px,float py,float radius){return visible&&Math.hypot(x-px,y-py)<=radius;}
}
