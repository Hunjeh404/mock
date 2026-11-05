package com.hoonjay.filmroom.core;

import java.util.*;
/** Edge-aware luminance/chroma smoothing in linear RGB. Never writes the input source.
 * A bounded tile cache amortizes demosaicing and avoids a full-resolution RGB allocation. */
public final class NoiseReduction implements Developer.Source {
  private static final int TILE=64,HALO=2,SIDE=TILE+2*HALO;
  private final Developer.Source input;
  private final float luma,chroma,sigma,skinTexture,skinSigma;
  private final int capacity;
  private final LinkedHashMap<Long,float[]> tiles=new LinkedHashMap<>(16,.75f,true);
  public static Developer.Source wrap(Developer.Source source,Recipe p){
    return p.skinTexture!=0||p.strength>0&&(p.noiseLuma>0||p.noiseChroma>0)?new NoiseReduction(source,p.noiseLuma*p.strength,p.noiseChroma*p.strength,p.skinTexture):source;
  }
  public NoiseReduction(Developer.Source input,float luma,float chroma){
    this(input,luma,chroma,0);
  }
  public NoiseReduction(Developer.Source input,float luma,float chroma,float skinTexture){
    this.input=input;this.luma=ColorMath.clamp(luma);this.chroma=ColorMath.clamp(chroma);
    this.skinTexture=skinTexture;skinSigma=.02f+.08f*Math.abs(skinTexture);
    sigma=.012f+.055f*Math.max(luma,chroma);capacity=Math.max(4,Math.min(256,(Math.max(width(),height())+63)/64+2));
  }
  public int width(){return input.width();}public int height(){return input.height();}
  private float[] tile(int tx,int ty){
    long key=((long)ty<<32)|(tx&0xffffffffL);float[] a=tiles.get(key);if(a!=null)return a;
    a=new float[SIDE*SIDE*3];float[] sample=new float[3];
    for(int y=0;y<SIDE;y++)for(int x=0;x<SIDE;x++){
      input.rgb(Math.max(0,Math.min(width()-1,tx*TILE+x-HALO)),Math.max(0,Math.min(height()-1,ty*TILE+y-HALO)),sample);
      int at=(y*SIDE+x)*3;for(int c=0;c<3;c++)a[at+c]=sample[c];
    }
    tiles.put(key,a);if(tiles.size()>capacity)tiles.remove(tiles.keySet().iterator().next());return a;
  }
  public void rgb(int x,int y,float[] out){
    float[] a=tile(x/TILE,y/TILE);int cx=x%TILE+HALO,cy=y%TILE+HALO,at=(cy*SIDE+cx)*3;
    float r=a[at],g=a[at+1],b=a[at+2],center=ColorMath.luma(r,g,b);
    float skin=skinTexture==0?0:SkinColor.mask(r,g,b)*Math.abs(skinTexture);
    if(luma==0&&chroma==0&&skin==0){out[0]=r;out[1]=g;out[2]=b;return;}
    float sy=0,sr=0,sb=0,total=0,skinY=0,skinR=0,skinB=0,skinTotal=0;int radius=skinTexture==0?1:2;
    for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){
      int i=((cy+dy)*SIDE+cx+dx)*3;float yy=ColorMath.luma(a[i],a[i+1],a[i+2]);
      float diff=yy-center;
      if(Math.abs(dx)<=1&&Math.abs(dy)<=1){float weight=(dx==0&&dy==0?2:dx==0||dy==0?1:.7f)/(1+diff*diff/(sigma*sigma));sy+=yy*weight;sr+=(a[i]-yy)*weight;sb+=(a[i+2]-yy)*weight;total+=weight;}
      if(skin>0){float weight=1/((1+(dx*dx+dy*dy)*.35f)*(1+diff*diff/(skinSigma*skinSigma)));skinY+=yy*weight;skinR+=(a[i]-yy)*weight;skinB+=(a[i+2]-yy)*weight;skinTotal+=weight;}
    }
    float yy=center+(sy/total-center)*luma;
    float cr=r-center+(sr/total-(r-center))*chroma,cb=b-center+(sb/total-(b-center))*chroma;
    if(skin>0&&skinTotal>0){if(skinTexture<0){yy+=(skinY/skinTotal-yy)*skin*.9f;cr+=(skinR/skinTotal-cr)*skin*.25f;cb+=(skinB/skinTotal-cb)*skin*.25f;}else yy+=(center-skinY/skinTotal)*skin*.8f;}
    out[0]=Math.max(0,yy+cr);out[2]=Math.max(0,yy+cb);out[1]=Math.max(0,(yy-.2126f*out[0]-.0722f*out[2])/.7152f);
  }
}
