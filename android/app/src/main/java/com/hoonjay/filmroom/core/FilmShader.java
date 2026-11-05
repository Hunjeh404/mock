package com.hoonjay.filmroom.core;

/** The actual live preview shader; kept Android-independent for render parity tests. */
public final class FilmShader {
  private FilmShader() {}
  public static String fragment() {
    return "#extension GL_OES_EGL_image_external : require\n"
        + "precision highp float;varying vec2 uv;uniform samplerExternalOES cam;uniform mat4 st;"
        + "uniform float rot,srcAspect,dstAspect;uniform float peaking,peakOpacity;uniform vec2 pixelStep;uniform vec3 peakColor;"
        + ColorMath.glsl()
        + PreviewColorModel.glsl()
        + "vec2 coord(vec2 p){if(srcAspect>dstAspect)p.x=(p.x-.5)*dstAspect/srcAspect+.5;"
        + "else p.y=(p.y-.5)*srcAspect/dstAspect+.5;"
        + "if(rot>269.)p=vec2(p.y,1.-p.x);else if(rot>179.)p=1.-p;"
        + "else if(rot>89.)p=vec2(1.-p.y,p.x);return (st*vec4(p,0.,1.)).xy;}"
        + "vec3 sampleCam(vec2 p){return previewBase(texture2D(cam,coord(p)).rgb);}"
        + "uniform float noiseLuma,noiseChroma,detail;"
        + "vec3 filteredCam(vec2 pos){vec3 c=sampleCam(pos);if(noiseLuma+noiseChroma+abs(skinTexture)<.0001)return c;float skin=abs(skinTexture)<.0001?0.:skinMask(c)*abs(skinTexture);if(noiseLuma+noiseChroma<.0001&&skin<=0.)return c;"
        + "vec3 linC=vec3(lin(c.r),lin(c.g),lin(c.b));float y0=luma(linC),sigma=.012+.055*max(noiseLuma,noiseChroma),skinSigma=.02+.08*abs(skinTexture),sy=0.,sr=0.,sb=0.,total=0.,skinY=0.,skinR=0.,skinB=0.,skinTotal=0.;"
        + "for(int j=-2;j<=2;j++)for(int i=-2;i<=2;i++){if((i< -1||i>1||j< -1||j>1)&&abs(skinTexture)<.0001)continue;vec3 n=sampleCam(pos+vec2(float(i),float(j))*pixelStep/1.5);n=vec3(lin(n.r),lin(n.g),lin(n.b));float yy=luma(n),d=yy-y0;"
        + "if(i>= -1&&i<=1&&j>= -1&&j<=1){float w=(i==0&&j==0?2.:i==0||j==0?1.:.7)/(1.+d*d/(sigma*sigma));sy+=yy*w;sr+=(n.r-yy)*w;sb+=(n.b-yy)*w;total+=w;}"
        + "if(skin>0.){float w=1./((1.+float(i*i+j*j)*.35)*(1.+d*d/(skinSigma*skinSigma)));skinY+=yy*w;skinR+=(n.r-yy)*w;skinB+=(n.b-yy)*w;skinTotal+=w;}}"
        + "float y=mix(y0,sy/total,noiseLuma),cr=mix(linC.r-y0,sr/total,noiseChroma),cb=mix(linC.b-y0,sb/total,noiseChroma);"
        + "if(skin>0.&&skinTotal>0.){if(skinTexture<0.){y+=(skinY/skinTotal-y)*skin*.9;cr+=(skinR/skinTotal-cr)*skin*.25;cb+=(skinB/skinTotal-cb)*skin*.25;}else y+=(y0-skinY/skinTotal)*skin*.8;}"
        + "float r=y+cr,b=y+cb,g=(y-.2126*max(0.,r)-.0722*max(0.,b))/.7152;return vec3(enc(max(0.,r)),enc(max(0.,g)),enc(max(0.,b)));}"
        + "vec3 detailCam(vec2 pos){vec3 c=filteredCam(pos);if(detail<=0.)return c;vec3 v=vec3(lin(c.r),lin(c.g),lin(c.b));vec2 e=max(pixelStep/1.5,vec2(.00001));vec3 a=sampleCam(pos-vec2(e.x,0.)),b=sampleCam(pos+vec2(e.x,0.)),d=sampleCam(pos-vec2(0.,e.y)),f=sampleCam(pos+vec2(0.,e.y));float neighbor=(luma(vec3(lin(a.r),lin(a.g),lin(a.b)))+luma(vec3(lin(b.r),lin(b.g),lin(b.b)))+luma(vec3(lin(d.r),lin(d.g),lin(d.b)))+luma(vec3(lin(f.r),lin(f.g),lin(f.b))))*.25;v=max(vec3(0.),v+clamp((luma(v)-neighbor)*detail*strength,-.012,.012));return vec3(enc(v.r),enc(v.g),enc(v.b));}"
        + "vec3 glowSample(vec2 pos){vec3 c=filmGrade(filteredCam(pos),pos,0.,0.);"
        + "return c*clamp((luma(c)-.6)/.4,0.,1.);}"
        + "void main(){float noise=fract(sin(dot(floor(uv*vec2(720.,1080.)),"
        + "vec2(12.9898,78.233)))*43758.5453)-.5;"
        + "vec3 c=filmGrade(detailCam(uv),uv,noise,vignette);"
        + "if(bloom>0.&&strength>0.){vec3 glow=(glowSample(uv+vec2(.02,0.))"
        + "+glowSample(uv-vec2(.02,0.))+glowSample(uv+vec2(0.,.02))"
        + "+glowSample(uv-vec2(0.,.02)))*.25;c+=glow*bloom*.22*strength;}"
        + "if(peaking>.5){vec2 e=max(pixelStep,vec2(.00001));"
        + "float a=luma(sampleCam(uv-vec2(e.x,0.))),b=luma(sampleCam(uv+vec2(e.x,0.)));"
        + "float d=luma(sampleCam(uv-vec2(0.,e.y))),f=luma(sampleCam(uv+vec2(0.,e.y)));"
        + "float edge=length(vec2(b-a,f-d));float fine=abs(luma(sampleCam(uv))-(a+b+d+f)*.25);"
        + "float mask=smoothstep(.055,.16,edge)*smoothstep(.008,.04,fine);"
        + "float hatch=1.-step(.48,fract((gl_FragCoord.x+gl_FragCoord.y)/8.));"
        + "c=mix(c,peakColor,mask*hatch*peakOpacity);}gl_FragColor=vec4(clamp(c,0.,1.),1.);}";
  }

}
