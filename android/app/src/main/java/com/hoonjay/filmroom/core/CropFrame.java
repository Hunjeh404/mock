package com.hoonjay.filmroom.core;

/** Pixel-aspect crop constraints; normalized coordinates remain independent of preview size. */
public final class CropFrame {
  public static final int ORIGINAL=0,FREE=1,SQUARE=6;
  public static final String[] LABELS={"원본비율","없음","2:1","16:9","4:3","3:2","1:1"};
  public static double aspect(int mode,double original,boolean portrait){
    if(mode==FREE)return 0;
    double value=mode==ORIGINAL?original:mode==2?2:mode==3?16/9.:mode==4?4/3.:mode==5?1.5:1;
    return portrait?1/value:value;
  }
  public static int[] dimensions(int w,int h,int rotation,int turns){
    if((Math.floorMod(rotation,360)/90+turns)%2!=0)return new int[]{h,w};return new int[]{w,h};
  }
  public static double original(int w,int h){return Math.max(w,h)/(double)Math.min(w,h);}
  public static void choose(EditTransform edit,int mode,boolean portrait,int w,int h,int rotation){
    if(mode<0||mode>6)throw new IllegalArgumentException("잘라내기 비율이 올바르지 않습니다.");
    boolean legacy=edit.cropMode<0;edit.cropMode=mode;edit.cropPortrait=portrait;if(legacy)edit.fullCrop();
    if(mode==FREE)return;
    int[] size=dimensions(w,h,rotation,edit.turns);
    double normalized=aspect(mode,original(w,h),portrait)*size[1]/size[0];
    double width=Math.sqrt((edit.right-edit.left)*(edit.bottom-edit.top)*normalized),height=width/normalized;
    if(width>1){width=1;height=1/normalized;}if(height>1){height=1;width=normalized;}
    set(edit,(edit.left+edit.right)/2.,(edit.top+edit.bottom)/2.,width,height);
  }
  /** Fit a copied/reopened locked crop inside its existing bounds on this source. */
  public static void enforce(EditTransform edit,int frameWidth,int frameHeight,double original){
    if(edit.cropMode<0||edit.cropMode==FREE)return;
    double target=aspect(edit.cropMode,original,edit.cropPortrait)*frameHeight/frameWidth;
    double w=edit.right-edit.left,h=edit.bottom-edit.top;
    if(Math.abs(w/h-target)<=Math.max(1,target)*1e-6)return;
    if(w/h>target)w=h*target;else h=w/target;
    set(edit,(edit.left+edit.right)/2.,(edit.top+edit.bottom)/2.,w,h);
  }
  private static void set(EditTransform edit,double cx,double cy,double w,double h){
    double left=Math.max(0,Math.min(1-w,cx-w/2)),top=Math.max(0,Math.min(1-h,cy-h/2));
    edit.left=(float)left;edit.top=(float)top;edit.right=(float)Math.min(1,left+w);edit.bottom=(float)Math.min(1,top+h);edit.validate();
  }
  public static int infer(double aspect,double original){
    double value=Math.max(aspect,1/aspect);for(int i=0;i<LABELS.length;i++)if(i!=FREE&&Math.abs(value-aspect(i,original,false))<.003)return i;return FREE;
  }
  /** Opposite corner stays fixed; drag never changes a positive locked aspect or exits the image. */
  public static float[] corner(float left,float top,float right,float bottom,int corner,float dx,float dy,double normalized){
    int sx=corner%2==0?-1:1,sy=corner<2?-1:1;
    if(normalized<=0){
      if(sx<0)left=Math.max(0,Math.min(right-.02f,left+dx));else right=Math.min(1,Math.max(left+.02f,right+dx));
      if(sy<0)top=Math.max(0,Math.min(bottom-.02f,top+dy));else bottom=Math.min(1,Math.max(top+.02f,bottom+dy));
    }else{
      double ax=sx<0?right:left,ay=sy<0?bottom:top;
      double desired=Math.abs(dx)>=Math.abs(dy*normalized)?right-left+sx*dx:(bottom-top+sy*dy)*normalized;
      double maximum=Math.min(sx<0?ax:1-ax,(sy<0?ay:1-ay)*normalized);
      double minimum=Math.min(maximum,Math.min(.02,.02*normalized));
      double w=Math.max(minimum,Math.min(maximum,desired)),h=w/normalized;
      left=(float)(sx<0?ax-w:ax);right=(float)(sx<0?ax:ax+w);top=(float)(sy<0?ay-h:ay);bottom=(float)(sy<0?ay:ay+h);
    }
    return new float[]{left,top,right,bottom};
  }
  private CropFrame(){}
}
