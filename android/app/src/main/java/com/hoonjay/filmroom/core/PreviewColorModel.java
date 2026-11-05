package com.hoonjay.filmroom.core;

/** Immutable metadata-derived ISP -> native DNG preview transform.
 * Inversion cannot restore detail already clipped or processed non-globally by the ISP.
 */
public final class PreviewColorModel {
  public static final int LUT_SIZE=1024;
  private final float[][] inverse=new float[3][LUT_SIZE];
  private final float[] matrix;
  public PreviewColorModel(float[][] curves,float[] matrix){
    if(curves==null||curves.length!=3||matrix==null||matrix.length!=9)throw new IllegalArgumentException("Incomplete preview metadata");
    this.matrix=matrix.clone();for(float v:matrix)if(!Float.isFinite(v)||Math.abs(v)>256)throw new IllegalArgumentException("Invalid preview matrix");
    for(int c=0;c<3;c++){
      float[] a=curves[c];if(a==null||a.length<4||a.length%2!=0)throw new IllegalArgumentException("Invalid preview curve");
      for(int i=0;i<a.length;i+=2){if(!Float.isFinite(a[i])||!Float.isFinite(a[i+1])||a[i]<0||a[i]>1||a[i+1]<0||a[i+1]>1||i>0&&(a[i]<=a[i-2]||a[i+1]<a[i-1]))throw new IllegalArgumentException("Non-invertible preview curve");}
      if(a[0]!=0||a[a.length-2]!=1||a[a.length-1]-a[1]<.000001f)throw new IllegalArgumentException("Incomplete preview curve range");
      int segment=0;
      for(int i=0;i<LUT_SIZE;i++){
        float y=i/(float)(LUT_SIZE-1);
        while(segment<a.length-4&&a[segment+3]<y)segment+=2;
        float low=a[segment+1],high=a[segment+3],t=high>low?ColorMath.clamp((y-low)/(high-low)):0;
        float x=a[segment]*(1-t)+a[segment+2]*t;
        // Same 16-bit LUT quantization used by ES2; no float-texture extension required.
        inverse[c][i]=Math.round(ColorMath.clamp(x)*65535)/65535f;
      }
    }
  }
  public byte[] texture(){byte[] a=new byte[LUT_SIZE*3*4];for(int c=0;c<3;c++)for(int i=0;i<LUT_SIZE;i++){int n=Math.round(inverse[c][i]*65535),p=(c*LUT_SIZE+i)*4;a[p]=(byte)(n>>>8);a[p+1]=(byte)n;a[p+3]=(byte)255;}return a;}
  public float[] columnMajor(){float[] a=new float[9];for(int y=0;y<3;y++)for(int x=0;x<3;x++)a[x*3+y]=matrix[y*3+x];return a;}
  public float inverse(float encoded,int channel){float pos=ColorMath.clamp(encoded)*(LUT_SIZE-1);int i=Math.min(LUT_SIZE-2,(int)pos);float f=pos-i;return inverse[channel][i]*(1-f)+inverse[channel][i+1]*f;}
  public void linear(float[] rgb,float simulation){float r=inverse(rgb[0],0),g=inverse(rgb[1],1),b=inverse(rgb[2],2);for(int c=0;c<3;c++)rgb[c]=Math.max(0,(matrix[c*3]*r+matrix[c*3+1]*g+matrix[c*3+2]*b)*simulation);ToneMapping.neutralRaw(rgb);}
  public static String glsl(){return "uniform float previewColor,previewScale;uniform highp sampler2D toneInverse;uniform mat3 previewMatrix;"
      +"float invPoint(float x,float channel){vec2 p=texture2D(toneInverse,vec2((x+.5)/1024.,(channel+.5)/3.)).rg;return (floor(p.r*255.+.5)*256.+floor(p.g*255.+.5))/65535.;}"
      +"float invTone(float x,float channel){float p=clamp(x,0.,1.)*1023.,i=min(1022.,floor(p));return mix(invPoint(i,channel),invPoint(i+1.,channel),p-i);}"
      +"vec3 previewBase(vec3 c){if(previewColor<.5)return c;vec3 v=max(vec3(0.),previewMatrix*vec3(invTone(c.r,0.),invTone(c.g,1.),invTone(c.b,2.))*previewScale);float peak=max(v.r,max(v.g,v.b));if(peak>.8){float excess=peak-.8;v*= (.8+.2*excess/(.2+excess))/peak;}return vec3(enc(v.r),enc(v.g),enc(v.b));}";}
}
