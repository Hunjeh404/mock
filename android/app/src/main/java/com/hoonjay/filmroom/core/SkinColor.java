package com.hoonjay.filmroom.core;
/** Feathered warm-skin chroma mask; a color heuristic, not semantic person segmentation. */
public final class SkinColor {
  private static float smooth(float a,float b,float x){float t=ColorMath.clamp((x-a)/(b-a));return t*t*(3-2*t);}
  public static boolean active(Recipe p){return p.skinBrightness!=0||p.skinColor!=0||p.skinBlush!=0;}
  public static float mask(float r,float g,float b){return maskSrgb(ColorMath.toSrgb(Math.max(0,r)),ColorMath.toSrgb(Math.max(0,g)),ColorMath.toSrgb(Math.max(0,b)));}
  public static float maskSrgb(float r,float g,float b){
    r=ColorMath.clamp(r);g=ColorMath.clamp(g);b=ColorMath.clamp(b);float hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b)),c=hi-lo;if(c<1e-6f||hi<1e-6f)return 0;
    float h=(hi==r?(g-b)/c:hi==g?(b-r)/c+2:(r-g)/c+4)*60;if(h>180)h-=360;float s=c/hi;
    return smooth(-20,-3,h)*(1-smooth(45,70,h))*smooth(.045f,.12f,s)*(1-smooth(.68f,.94f,s))*smooth(.06f,.2f,hi);
  }
  public static void apply(float[] rgb,Recipe p,float mask){
    if(mask<=0||!active(p))return;
    float r=rgb[0],g=rgb[1],b=rgb[2],hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b)),c=hi-lo,l=(hi+lo)*.5f;
    float h=c<1e-6f?0:(hi==r?(g-b)/c:hi==g?(b-r)/c+2:(r-g)/c+4)*60;
    float s=c/Math.max(1e-6f,1-Math.abs(2*l-1));
    h=(h-(p.skinColor*7+p.skinBlush*10)*mask+720)%360;
    s=ColorMath.clamp(s*(1+(-p.skinColor*.06f+p.skinBlush*.22f)*mask));
    l=ColorMath.clamp(l+p.skinBrightness*.28f*mask*(p.skinBrightness>=0?1-l:l));
    c=(1-Math.abs(2*l-1))*s;float x=c*(1-Math.abs((h/60)%2-1)),m=l-c*.5f;
    if(h<60){r=c;g=x;b=0;}else if(h<120){r=x;g=c;b=0;}else if(h<180){r=0;g=c;b=x;}else if(h<240){r=0;g=x;b=c;}else if(h<300){r=x;g=0;b=c;}else{r=c;g=0;b=x;}
    rgb[0]=ColorMath.clamp(r+m);rgb[1]=ColorMath.clamp(g+m);rgb[2]=ColorMath.clamp(b+m);
  }
  public static String glsl(){return "uniform float skinBrightness,skinColor,skinBlush,skinTexture;"
    +"float skinMask(vec3 rgb){rgb=clamp(rgb,0.,1.);float hi=max(rgb.r,max(rgb.g,rgb.b)),lo=min(rgb.r,min(rgb.g,rgb.b)),c=hi-lo;if(c<.000001||hi<.000001)return 0.;float h=(hi==rgb.r?(rgb.g-rgb.b)/c:hi==rgb.g?(rgb.b-rgb.r)/c+2.:(rgb.r-rgb.g)/c+4.)*60.;if(h>180.)h-=360.;float s=c/hi;return smoothstep(-20.,-3.,h)*(1.-smoothstep(45.,70.,h))*smoothstep(.045,.12,s)*(1.-smoothstep(.68,.94,s))*smoothstep(.06,.2,hi);}"
    +"vec3 skinGrade(vec3 rgb,float mask){if(mask<=0.||abs(skinBrightness)+abs(skinColor)+abs(skinBlush)<.000001)return rgb;float hi=max(rgb.r,max(rgb.g,rgb.b)),lo=min(rgb.r,min(rgb.g,rgb.b)),c=hi-lo,l=(hi+lo)*.5;float h=c<.000001?0.:(hi==rgb.r?(rgb.g-rgb.b)/c:hi==rgb.g?(rgb.b-rgb.r)/c+2.:(rgb.r-rgb.g)/c+4.)*60.,s=c/max(.000001,1.-abs(2.*l-1.));h=mod(h-(skinColor*7.+skinBlush*10.)*mask+720.,360.);s=clamp(s*(1.+(-skinColor*.06+skinBlush*.22)*mask),0.,1.);l=clamp(l+skinBrightness*.28*mask*(skinBrightness>=0.?1.-l:l),0.,1.);c=(1.-abs(2.*l-1.))*s;float x=c*(1.-abs(mod(h/60.,2.)-1.)),m=l-c*.5;rgb=h<60.?vec3(c,x,0.):h<120.?vec3(x,c,0.):h<180.?vec3(0.,c,x):h<240.?vec3(0.,x,c):h<300.?vec3(x,0.,c):vec3(c,0.,x);return clamp(rgb+m,0.,1.);}";}
  private SkinColor(){}
}
