package com.hoonjay.filmroom.core;
/** Overlay anchor at the user's upper edge of a frame on a portrait-locked handset. */
public final class FrameLabel {
  public final float x,y,rotation;
  private FrameLabel(float x,float y,float rotation){this.x=x;this.y=y;this.rotation=rotation;}
  public static FrameLabel of(float left,float top,float right,float bottom,int attitude,float inset){
    float cx=(left+right)/2,cy=(top+bottom)/2;
    switch(PreviewGeometry.normalize(attitude)){
      case 90:return new FrameLabel(left+inset,cy,270);
      case 180:return new FrameLabel(cx,bottom-inset,180);
      case 270:return new FrameLabel(right-inset,cy,90);
      default:return new FrameLabel(cx,top+inset,0);
    }
  }
}
