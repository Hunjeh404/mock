package com.hoonjay.filmroom.core;

/** Compact handset-fixed portrait controls. Physical device rotation never reflows this HUD. */
public final class CameraLayout {
  public static final class Box {
    public final float left,top,right,bottom;
    public Box(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
    public float width(){return right-left;} public float height(){return bottom-top;}
    public float cx(){return (left+right)/2;} public float cy(){return (top+bottom)/2;}
    public boolean contains(float x,float y){return x>=left&&x<right&&y>=top&&y<bottom;}
  }
  public final float width,height,density,topBottom,bottomTop,shutterX,shutterY;
  public final boolean landscape;
  public final Box topPanel,bottomPanel,format,ratio,mode,preset,manage,zoom,meter,level,grid,manual,shutter,gallery,timer,flash;
  public CameraLayout(int w,int h,float d,int left,int top,int right,int bottom){
    width=w;height=h;density=d;landscape=w>h;
    float l=left+6*d,r=w-right-6*d,t=top+2*d,b=h-bottom;
    float y=t,gap=3*d,unit=(r-l-5*gap)/318f,x=l;
    mode=new Box(x,y,x+34*unit,y+26*d);x=mode.right+gap;
    format=new Box(x,y,x+74*unit,y+26*d);x=format.right+gap;
    ratio=new Box(x,y,x+44*unit,y+26*d);x=ratio.right+gap;
    preset=new Box(x,y,x+100*unit,y+26*d);x=preset.right+gap;
    manage=new Box(x,y,x+22*unit,y+26*d);x=manage.right+gap;
    zoom=new Box(x,y,r,y+26*d);
    topBottom=y+28*d;bottomTop=b-108*d;
    topPanel=new Box(0,0,w,topBottom);bottomPanel=new Box(0,bottomTop,w,h);
    float mw=Math.min(r-l,660*d),ml=(l+r-mw)/2;
    manual=new Box(ml,bottomTop+3*d,ml+mw,bottomTop+31*d);
    shutterX=(l+r)/2;shutterY=b-36*d;
    shutter=new Box(shutterX-35*d,shutterY-35*d,shutterX+35*d,shutterY+35*d);
    gallery=new Box(l+2*d,shutterY-21*d,l+44*d,shutterY+21*d);
    float flashX=(gallery.right+shutter.left)/2,flashHalf=Math.min(22*d,Math.max(0,(shutter.left-gallery.right)/2-3*d));flash=new Box(flashX-flashHalf,shutterY-22*d,flashX+flashHalf,shutterY+22*d);
    float start=shutterX+42*d,span=(r-start-6*d)/4;
    timer=new Box(start,shutterY-15*d,start+span,shutterY+15*d);start=timer.right+2*d;
    meter=new Box(start,shutterY-15*d,start+span,shutterY+15*d);start=meter.right+2*d;
    level=new Box(start,shutterY-15*d,start+span,shutterY+15*d);start=level.right+2*d;
    grid=new Box(start,shutterY-15*d,r,shutterY+15*d);
  }
  public Box manualSlot(int index,int count){
    float gap=4*density,span=(manual.width()+gap)/count;
    return new Box(manual.left+index*span,manual.top,manual.left+(index+1)*span-gap,manual.bottom);
  }
  public boolean inPanels(float x,float y){return topPanel.contains(x,y)||bottomPanel.contains(x,y);}
}
