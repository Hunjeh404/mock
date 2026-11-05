package com.hoonjay.filmroom.core;

/** Display-referred HSL mixer and master/R/G/B fixed-knot curves. CPU export stays float. */
public final class AdvancedColor {
  public static final String[] NAMES={"빨강","주황","노랑","초록","청록","파랑","보라","자홍"};
  public static final float[] HUES={0,30,60,120,180,240,275,315};
  public static final int LUT_WIDTH=16,LUT_HEIGHT=8;
  private AdvancedColor(){}
  public static float[] identity(){return new float[]{0,.125f,.25f,.375f,.5f,.625f,.75f,.875f,1};}
  public static boolean active(Recipe p){
    for(int i=0;i<8;i++)if(p.hue[i]!=0||p.hslSaturation[i]!=0||p.luminance[i]!=0)return true;
    for(float[] c:p.curves)for(int i=0;i<9;i++)if(c[i]!=i/8f)return true;
    return false;
  }
  public static float curve(float[] c,float v){
    float t=ColorMath.clamp(v)*8;int at=Math.min(7,(int)t);return c[at]+(c[at+1]-c[at])*(t-at);
  }
  public static void apply(float[] rgb,Recipe p){
    float r=ColorMath.clamp(rgb[0]),g=ColorMath.clamp(rgb[1]),b=ColorMath.clamp(rgb[2]);
    float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),chroma=max-min,l=(max+min)*.5f;
    if(chroma>1e-6f){
      float s=chroma/Math.max(1e-6f,1-Math.abs(2*l-1));
      float h=(max==r?(g-b)/chroma:max==g?(b-r)/chroma+2:(r-g)/chroma+4)*60;
      if(h<0)h+=360;
      int a=7;for(int i=0;i<7;i++)if(h>=HUES[i]&&h<HUES[i+1]){a=i;break;}
      int next=(a+1)%8;float end=next==0?360:HUES[next];
      float t=(h-HUES[a])/(end-HUES[a]);t=t*t*(3-2*t);
      float mask=Math.min(1,s/.12f);
      float dh=p.hue[a]*(1-t)+p.hue[next]*t,ds=p.hslSaturation[a]*(1-t)+p.hslSaturation[next]*t,dl=p.luminance[a]*(1-t)+p.luminance[next]*t;
      h=(h+dh*mask+360)%360;s=ColorMath.clamp(s*(1+ds*mask));
      l=ColorMath.clamp(l+dl*mask*(dl>=0?1-l:l));
      float c=(1-Math.abs(2*l-1))*s,x=c*(1-Math.abs((h/60)%2-1)),m=l-c*.5f;
      if(h<60){r=c;g=x;b=0;}else if(h<120){r=x;g=c;b=0;}else if(h<180){r=0;g=c;b=x;}
      else if(h<240){r=0;g=x;b=c;}else if(h<300){r=x;g=0;b=c;}else{r=c;g=0;b=x;}
      r+=m;g+=m;b+=m;
    }
    rgb[0]=curve(p.curves[1],curve(p.curves[0],r));
    rgb[1]=curve(p.curves[2],curve(p.curves[0],g));
    rgb[2]=curve(p.curves[3],curve(p.curves[0],b));
  }
  /** Nearest-filtered packed 16-bit controls, not a sampled 3D color approximation. */
  public static byte[] lut(Recipe p){
    byte[] data=new byte[LUT_WIDTH*LUT_HEIGHT*4];
    for(int i=0;i<8;i++){pack(data,i*4,(p.hue[i]+60)/120);pack(data,i*4+2,(p.hslSaturation[i]+1)/2);pack(data,(LUT_WIDTH+i)*4,(p.luminance[i]+1)/2);}
    for(int channel=0;channel<4;channel++)for(int i=0;i<9;i++)pack(data,((channel+2)*LUT_WIDTH+i)*4,p.curves[channel][i]);
    return data;
  }
  private static void pack(byte[] data,int at,float value){int n=Math.round(ColorMath.clamp(value)*65535);data[at]=(byte)(n>>>8);data[at+1]=(byte)n;}
  public static String glsl(){return "uniform sampler2D colorLut;uniform float advanced;"
    +"vec4 control(float i,float row){return texture2D(colorLut,vec2((i+.5)/16.,(row+.5)/8.));}"
    +"float unpack16(vec2 p){return dot(p,vec2(65280.,255.))/65535.;}"
    +"float tone(float channel,float v){float t=clamp(v,0.,1.)*8.,i=min(7.,floor(t));return mix(unpack16(control(i,channel+2.).rg),unpack16(control(i+1.,channel+2.).rg),t-i);}"
    +"float center(float a){if(a<.5)return 0.;if(a<1.5)return 30.;if(a<2.5)return 60.;if(a<3.5)return 120.;if(a<4.5)return 180.;if(a<5.5)return 240.;if(a<6.5)return 275.;return 315.;}"
    +"vec3 mixer(float i){vec4 c=control(i,0.);return vec3(unpack16(c.rg)*120.-60.,unpack16(c.ba)*2.-1.,unpack16(control(i,1.).rg)*2.-1.);}"
    +"vec3 advancedGrade(vec3 inputColor){vec3 rgb=clamp(inputColor,0.,1.);float hi=max(rgb.r,max(rgb.g,rgb.b)),lo=min(rgb.r,min(rgb.g,rgb.b)),c=hi-lo,l=(hi+lo)*.5;"
    +"if(c>.000001){float s=c/max(.000001,1.-abs(2.*l-1.)),h=(hi==rgb.r?(rgb.g-rgb.b)/c:hi==rgb.g?(rgb.b-rgb.r)/c+2.:(rgb.r-rgb.g)/c+4.)*60.;if(h<0.)h+=360.;"
    +"float a=h<30.?0.:h<60.?1.:h<120.?2.:h<180.?3.:h<240.?4.:h<275.?5.:h<315.?6.:7.,b=mod(a+1.,8.);float start=center(a),end=a>6.5?360.:center(b),t=(h-start)/(end-start);t=t*t*(3.-2.*t);"
    +"vec3 edit=mix(mixer(a),mixer(b),t);float mask=min(1.,s/.12);h=mod(h+edit.x*mask+360.,360.);s=clamp(s*(1.+edit.y*mask),0.,1.);l=clamp(l+edit.z*mask*(edit.z>=0.?1.-l:l),0.,1.);"
    +"c=(1.-abs(2.*l-1.))*s;float x=c*(1.-abs(mod(h/60.,2.)-1.)),m=l-c*.5;"
    +"rgb=h<60.?vec3(c,x,0.):h<120.?vec3(x,c,0.):h<180.?vec3(0.,c,x):h<240.?vec3(0.,x,c):h<300.?vec3(x,0.,c):vec3(c,0.,x);rgb+=m;}"
    +"rgb=vec3(tone(0.,rgb.r),tone(0.,rgb.g),tone(0.,rgb.b));return vec3(tone(1.,rgb.r),tone(2.,rgb.g),tone(3.,rgb.b));}";}
}
