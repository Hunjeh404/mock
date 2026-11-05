package com.hoonjay.filmroom.core;
import java.util.Arrays;

/** Immutable whole-frame statistics from neutral camera pixels, before film, peaking or simulation. */
public final class MeteringStats {
  public final float mean,median,upper,highlight,clipped;
  public final int samples;
  private MeteringStats(float mean,float median,float upper,float highlight,float clipped,int samples){
    this.mean=mean;this.median=median;this.upper=upper;this.highlight=highlight;this.clipped=clipped;this.samples=samples;
  }
  public boolean valid(){return samples>0;}
  public static MeteringStats analyze(float[] luma,float[] channelMax){
    if(luma==null || (channelMax!=null&&channelMax.length!=luma.length))return new MeteringStats(0,0,0,0,0,0);
    float[] ys=new float[luma.length],high=new float[luma.length];int n=0,clipped=0;double sum=0;
    for(int i=0;i<luma.length;i++){
      float y=luma[i],h=channelMax==null?y:channelMax[i];
      if(!Float.isFinite(y)||!Float.isFinite(h)||y<0||h<0)continue;
      y=Math.min(1,y);h=Math.min(1,h);ys[n]=y;high[n]=h;sum+=y;if(h>=.995f)clipped++;n++;
    }
    if(n==0)return new MeteringStats(0,0,0,0,0,0);
    Arrays.sort(ys,0,n);Arrays.sort(high,0,n);
    return new MeteringStats((float)(sum/n),quantile(ys,n,.5f),quantile(ys,n,.75f),quantile(high,n,.98f),clipped/(float)n,n);
  }
  /** Coordinates follow the metering texture (origin bottom-left), independent of camera rotation. */
  public static MeteringStats region(float[] y,float[] max,int width,int height,float cx,float cy){
    if(y==null||y.length!=width*height||width<=0||height<=0)return analyze(null,null);
    int rw=Math.max(3,Math.round(width*.15f)),rh=Math.max(3,Math.round(height*.15f));rw=Math.min(width,rw);rh=Math.min(height,rh);
    int left=Math.max(0,Math.min(width-rw,Math.round(cx*width)-rw/2));
    int top=Math.max(0,Math.min(height-rh,Math.round(cy*height)-rh/2));
    float[] ys=new float[rw*rh],hs=new float[ys.length];
    for(int j=0;j<rh;j++)for(int i=0;i<rw;i++){int dst=j*rw+i,src=(top+j)*width+left+i;ys[dst]=y[src];hs[dst]=max==null?y[src]:max[src];}
    return analyze(ys,hs);
  }
  private static float quantile(float[] sorted,int n,float p){
    float pos=(n-1)*p;int lo=(int)pos,hi=Math.min(n-1,lo+1);return sorted[lo]+(sorted[hi]-sorted[lo])*(pos-lo);
  }
}
