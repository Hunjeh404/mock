package com.hoonjay.filmroom.core;
/** Display-only zoom. Image/export coordinates and edit settings never change. */
public final class PhotoViewport {
  public float zoom=1,x,y;private float width,height,fitWidth,fitHeight;
  public void frame(float w,float h,float imageW,float imageH){width=w;height=h;float fit=Math.min(w/Math.max(1,imageW),h/Math.max(1,imageH));fitWidth=imageW*fit;fitHeight=imageH*fit;bound();}
  public void pan(float dx,float dy){x+=dx;y+=dy;bound();}
  public void pinch(float factor,float oldX,float oldY,float nextX,float nextY){if(!Float.isFinite(factor)||factor<=0)return;float before=zoom;zoom=Math.max(1,Math.min(8,zoom*factor));float scale=zoom/before;x=nextX-width/2-(oldX-width/2-x)*scale;y=nextY-height/2-(oldY-height/2-y)*scale;bound();}
  public void reset(){zoom=1;x=y=0;}
  private void bound(){float maxX=Math.max(0,(fitWidth*zoom-width)/2),maxY=Math.max(0,(fitHeight*zoom-height)/2);x=Math.max(-maxX,Math.min(maxX,x));y=Math.max(-maxY,Math.min(maxY,y));if(zoom==1)x=y=0;}
}
