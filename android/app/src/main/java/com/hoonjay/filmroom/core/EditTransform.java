package com.hoonjay.filmroom.core;

/** Non-destructive geometry after sensor orientation and capture frame; crop is normalized. */
public final class EditTransform {
  public int turns;
  public boolean flip;
  /** -1 preserves old capture-frame edits; 0..6 are the explicit crop toolbar choices. */
  public int cropMode=-1;
  public boolean cropPortrait;
  public float angle,left,top,right=1,bottom=1;
  public EditTransform copy(){EditTransform e=new EditTransform();e.turns=turns;e.flip=flip;e.angle=angle;e.left=left;e.top=top;e.right=right;e.bottom=bottom;e.cropMode=cropMode;e.cropPortrait=cropPortrait;return e;}
  public void validate(){
    if(turns<0||turns>3||cropMode< -1||cropMode>6||!Float.isFinite(angle)||Math.abs(angle)>15||!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(right)||!Float.isFinite(bottom)||left<0||top<0||right>1||bottom>1||right<=left||bottom<=top)throw new IllegalArgumentException("크기조정 값이 올바르지 않습니다.");
  }
  public void fullCrop(){left=top=0;right=bottom=1;}
  public void rotateClockwise(){turns=(turns+(flip?3:1))%4;float l=left,t=top,r=right,b=bottom;left=1-b;top=l;right=1-t;bottom=r;if(cropMode>=0)cropPortrait=!cropPortrait;}
  public void mirror(){flip=!flip;float l=left;left=1-right;right=1-l;}
  public boolean identity(){return cropMode<0&&turns==0&&!flip&&angle==0&&left==0&&top==0&&right==1&&bottom==1;}
}
