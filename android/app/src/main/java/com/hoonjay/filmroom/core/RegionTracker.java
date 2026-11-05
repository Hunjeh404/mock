package com.hoonjay.filmroom.core;

/** Local appearance tracking of a user-selected patch; no identity or category recognition.
 * Brightness-normalized correlation uses the original template to prevent cumulative drift.
 * A lost or ambiguous target is terminal until the user selects again. Thread confined. */
public final class RegionTracker {
  private static final int N=21;
  private final float[] anchor=new float[N*N],sample=new float[N*N];
  private float x,y,radius,quality;
  private int width,height;
  private boolean started,lost;
  private String reason="";
  public static final class Result {
    public final float x,y,rx,ry,confidence;
    public final boolean lost;
    public final String reason;
    Result(float x,float y,float rx,float ry,float confidence,boolean lost,String reason){
      this.x=x;this.y=y;this.rx=rx;this.ry=ry;this.confidence=confidence;this.lost=lost;this.reason=reason;
    }
  }
  public Result start(float[] gray,int w,int h,float nx,float ny){
    valid(gray,w,h);width=w;height=h;radius=Math.max(7,Math.min(w,h)*.095f);
    x=nx*(w-1);y=ny*(h-1);started=true;lost=false;reason="";
    if(!inside(x,y,radius,w,h)){lost=true;reason="화면 가장자리";}
    else if(patch(gray,w,x,y,radius,anchor)<.018f){lost=true;reason="무늬 부족";}
    quality=lost?0:1;return result();
  }
  public Result next(float[] gray,int w,int h){
    valid(gray,w,h);if(!started)throw new IllegalStateException("Select a target first");
    if(lost)return result();
    if(w!=width||h!=height){lost=true;reason="프레임 변경";return result();}
    int travel=Math.max(10,Math.min(w,h)/8);
    float best=-2,bx=x,by=y,br=radius;
    // Three scales allow gradual distance changes without adapting to the background.
    for(float scale:new float[]{.96f,1,1.04f}){
      float r=radius*scale;
      for(int dy=-travel;dy<=travel;dy+=2)for(int dx=-travel;dx<=travel;dx+=2){
        float xx=x+dx,yy=y+dy;if(!inside(xx,yy,r,w,h))continue;
        float s=score(gray,w,xx,yy,r);if(s>best){best=s;bx=xx;by=yy;br=r;}
      }
    }
    float cx=bx,cy=by,cr=br;
    for(float scale:new float[]{.98f,1,1.02f})for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
      float xx=cx+dx,yy=cy+dy,r=cr*scale;if(!inside(xx,yy,r,w,h))continue;
      float s=score(gray,w,xx,yy,r);if(s>best){best=s;bx=xx;by=yy;br=r;}
    }
    float runner=-2;
    for(int dy=-travel;dy<=travel;dy+=2)for(int dx=-travel;dx<=travel;dx+=2){
      float xx=x+dx,yy=y+dy;
      if(Math.hypot(xx-bx,yy-by)<Math.max(5,br*.8f)||!inside(xx,yy,br,w,h))continue;
      runner=Math.max(runner,score(gray,w,xx,yy,br));
    }
    quality=best;
    // Reject matches at the motion limit, ambiguity and occlusion instead of jumping.
    if(best<.80f)reason="대상 변화·가림";
    else if(best-runner<.065f)reason="비슷한 무늬";
    else if(Math.abs(bx-x)>=travel-1||Math.abs(by-y)>=travel-1)reason="빠른 이동";
    if(!reason.isEmpty()){lost=true;return result();}
    x=bx;y=by;radius=br;return result();
  }
  private Result result(){return new Result(x/(width-1),y/(height-1),radius/(width-1),radius/(height-1),quality,lost,reason);}
  private float score(float[] gray,int w,float xx,float yy,float r){
    if(patch(gray,w,xx,yy,r,sample)<.012f)return -1;
    float dot=0;for(int i=0;i<anchor.length;i++)dot+=anchor[i]*sample[i];return dot;
  }
  private static boolean inside(float x,float y,float r,int w,int h){return x-r>=1&&y-r>=1&&x+r<w-2&&y+r<h-2;}
  private static void valid(float[] a,int w,int h){if(w<24||h<24||a==null||a.length!=w*h)throw new IllegalArgumentException("Invalid analysis image");}
  private static float patch(float[] a,int w,float x,float y,float r,float[] out){
    float mean=0;int k=0;
    for(int j=0;j<N;j++)for(int i=0;i<N;i++){
      float xx=x-r+2*r*i/(N-1),yy=y-r+2*r*j/(N-1);int ix=(int)xx,iy=(int)yy;
      float fx=xx-ix,fy=yy-iy;
      float v=(a[iy*w+ix]*(1-fx)+a[iy*w+ix+1]*fx)*(1-fy)+(a[(iy+1)*w+ix]*(1-fx)+a[(iy+1)*w+ix+1]*fx)*fy;
      out[k++]=v;mean+=v;
    }
    mean/=out.length;float energy=0;
    for(int i=0;i<out.length;i++){out[i]-=mean;energy+=out[i]*out[i];}
    float norm=(float)Math.sqrt(energy);if(norm>1e-8)for(int i=0;i<out.length;i++)out[i]/=norm;
    return norm/(float)Math.sqrt(out.length);
  }
}
