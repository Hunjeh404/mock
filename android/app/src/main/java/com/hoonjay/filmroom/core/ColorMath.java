package com.hoonjay.filmroom.core;

public final class ColorMath {
  private ColorMath() {}

  public static float clamp(float x) {
    return Math.max(0, Math.min(1, x));
  }

  public static float toLinear(float x) {
    return x <= .04045f ? x / 12.92f : (float) Math.pow((x + .055f) / 1.055f, 2.4);
  }

  public static float toSrgb(float x) {
    return x <= .0031308f ? 12.92f * x : 1.055f * (float) Math.pow(Math.max(0, x), 1 / 2.4) - .055f;
  }

  public static float luma(float r, float g, float b) {
    return .2126f * r + .7152f * g + .0722f * b;
  }

  public static void grade(float[] rgb, Recipe p, float x, float y, int seed) {
    grade(rgb,p,x,y,seed,AdvancedColor.active(p));
  }
  public static void grade(float[] rgb,Recipe p,float x,float y,int seed,boolean advanced){
    float skin=SkinColor.active(p)?SkinColor.mask(rgb[0],rgb[1],rgb[2]):0;
    float ev = (float) Math.pow(2, p.exposure);
    float r = rgb[0] * ev * (1 + p.warmth * .002f),
        g = rgb[1] * ev * (1 - p.tint * .001f),
        b = rgb[2] * ev * (1 - p.warmth * .002f);
    float l = luma(r, g, b);
    float lift = p.shadows * (1 - clamp(l)) * Math.max(0, .35f - l) * .65f;
    float high = p.highlights * Math.max(0, l - .4f) * .6f;
    r += lift + high;
    g += lift + high;
    b += lift + high;
    r = toSrgb(Math.max(0, r));
    g = toSrgb(Math.max(0, g));
    b = toSrgb(Math.max(0, b));
    float weight = (1 - clamp(luma(r, g, b)));
    weight *= weight;
    float fr = (r * p.balance[0] + p.shadowColor[0] * weight - .5f) * p.contrast + .5f;
    float fg = (g * p.balance[1] + p.shadowColor[1] * weight - .5f) * p.contrast + .5f;
    float fb = (b * p.balance[2] + p.shadowColor[2] * weight - .5f) * p.contrast + .5f;
    float fl = luma(fr, fg, fb);
    fr = (fl + (fr - fl) * p.saturation) * (1 - p.fade) + p.fade;
    fg = (fl + (fg - fl) * p.saturation) * (1 - p.fade) + p.fade;
    fb = (fl + (fb - fl) * p.saturation) * (1 - p.fade) + p.fade;
    if(advanced){rgb[0]=fr;rgb[1]=fg;rgb[2]=fb;AdvancedColor.apply(rgb,p);fr=rgb[0];fg=rgb[1];fb=rgb[2];}
    float dist = (float) Math.sqrt((x - .5f) * (x - .5f) + (y - .5f) * (y - .5f));
    float t = clamp((dist - .14f) / .61f), v = 1 - t * t * (3 - 2 * t) * p.vignette * .5f;
    int h = seed;
    h ^= h >>> 16;
    h *= 0x45d9f3b;
    h ^= h >>> 16;
    h *= 0x45d9f3b;
    h ^= h >>> 16;
    float n =
        ((h & 0x7fffffff) / (float) Integer.MAX_VALUE - .5f)
            * p.grain
            * .16f
            * (.45f + .55f * weight);
    rgb[0] = clamp(r + (fr * v + n - r) * p.strength);
    rgb[1] = clamp(g + (fg * v + n - g) * p.strength);
    rgb[2] = clamp(b + (fb * v + n - b) * p.strength);
    SkinColor.apply(rgb,p,skin);
  }

  /** The live shader uses the same transfer functions and operation order as grade(). */
  public static String glsl() {
    return "uniform float strength,exposure,warmth,tint,contrast,saturation,fade,shadows,highlights,grain,bloom,vignette;"
        + "uniform vec3 balance,shadowColor;"+AdvancedColor.glsl()
        + "float luma(vec3 c){return dot(c,vec3(.2126,.7152,.0722));}"
        + "float lin(float x){return x<=.04045?x/12.92:pow((x+.055)/1.055,2.4);}"
        + "float enc(float x){return x<=.0031308?12.92*x:1.055*pow(max(0.,x),1./2.4)-.055;}"
        + SkinColor.glsl()
        + "vec3 filmGrade(vec3 inputColor,vec2 pos,float noise,float vi){"
        + "vec3 c=vec3(lin(inputColor.r),lin(inputColor.g),lin(inputColor.b));"
        + "c*=pow(2.,exposure)*vec3(1.+warmth*.002,1.-tint*.001,1.-warmth*.002);"
        + "float l=luma(c);float lift=shadows*(1.-clamp(l,0.,1.))*max(0.,.35-l)*.65;"
        + "float high=highlights*max(0.,l-.4)*.6;c+=vec3(lift+high);"
        + "c=max(c,vec3(0.));c=vec3(enc(c.r),enc(c.g),enc(c.b));"
        + "float weight=pow(1.-clamp(luma(c),0.,1.),2.);"
        + "vec3 f=(c*balance+shadowColor*weight-.5)*contrast+.5;"
        + "f=mix(vec3(luma(f)),f,saturation)*(1.-fade)+fade;"
        + "if(advanced>.5)f=advancedGrade(f);"
        + "float t=clamp((length(pos-.5)-.14)/.61,0.,1.);"
        + "float v=1.-t*t*(3.-2.*t)*vi*.5;"
        + "float n=noise*grain*.16*(.45+.55*weight);"
        + "return skinGrade(clamp(mix(c,f*v+n,strength),0.,1.),skinMask(inputColor));}";
  }
}
