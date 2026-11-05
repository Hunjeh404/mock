package com.hoonjay.filmroom.core;

import java.util.*;

/** Device-independent, non-destructive recipe. All adjustments retain floating precision. */
public final class Recipe {
  public String id = "custom", name = "커스텀", english = "CUSTOM FILM";
  public float strength = 1,
      exposure = 0,
      warmth = 0,
      tint = 0,
      contrast = 1,
      saturation = 1,
      fade = 0,
      highlights = 0,
      shadows = 0,
      grain = 0,
      bloom = 0,
      vignette = 0;
  public float[] balance = {1, 1, 1}, shadowColor = {0, 0, 0};
  public float[] hue=new float[8],hslSaturation=new float[8],luminance=new float[8];
  public float[][] curves={AdvancedColor.identity(),AdvancedColor.identity(),AdvancedColor.identity(),AdvancedColor.identity()};
  public float noiseLuma=0,noiseChroma=0,detail=0;
  public float skinBrightness=0,skinColor=0,skinBlush=0,skinTexture=0;
  public boolean custom = false;

  public Recipe copy() {
    Recipe r = new Recipe();
    r.id = id;
    r.name = name;
    r.english = english;
    r.strength = strength;
    r.exposure = exposure;
    r.warmth = warmth;
    r.tint = tint;
    r.contrast = contrast;
    r.saturation = saturation;
    r.fade = fade;
    r.highlights = highlights;
    r.shadows = shadows;
    r.grain = grain;
    r.bloom = bloom;
    r.vignette = vignette;
    r.balance = balance.clone();
    r.shadowColor = shadowColor.clone();
    r.hue=hue.clone();r.hslSaturation=hslSaturation.clone();r.luminance=luminance.clone();
    r.curves=new float[4][];for(int i=0;i<4;i++)r.curves[i]=curves[i].clone();
    r.noiseLuma=noiseLuma;r.noiseChroma=noiseChroma;r.detail=detail;
    r.skinBrightness=skinBrightness;r.skinColor=skinColor;r.skinBlush=skinBlush;r.skinTexture=skinTexture;
    r.custom = custom;
    return r;
  }

  public static List<Recipe> builtIns() {
    List<Recipe> a = new ArrayList<>();
    a.add(make("clean", "클린", "CLEAN", 1, 1, 1, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0));
    a.add(
        make(
            "gold",
            "골든 아워",
            "GOLDEN HOUR",
            1.07f,
            1.015f,
            .91f,
            -.016f,
            .013f,
            .026f,
            .045f,
            1.08f,
            .96f,
            .24f,
            .25f,
            .16f,
            0));
    a.add(
        make(
            "cream",
            "크림 데이",
            "CREAM DAY",
            1.04f,
            1.015f,
            .97f,
            .015f,
            .012f,
            .006f,
            .075f,
            .92f,
            .84f,
            .17f,
            .18f,
            .10f,
            0));
    a.add(
        make(
            "summer",
            "여름 바다",
            "SUMMER BLUE",
            1.02f,
            1.035f,
            1.025f,
            -.035f,
            .014f,
            .033f,
            .025f,
            1.10f,
            1.10f,
            .13f,
            .15f,
            .12f,
            0));
    a.add(
        make(
            "rose",
            "로즈 메모리",
            "ROSE MEMORY",
            1.065f,
            .97f,
            1.01f,
            .020f,
            -.005f,
            .015f,
            .065f,
            .99f,
            .86f,
            .25f,
            .23f,
            .15f,
            0));
    a.add(
        make(
            "forest",
            "포레스트",
            "FOREST WALK",
            1.025f,
            1.035f,
            .95f,
            .009f,
            .021f,
            -.005f,
            .04f,
            1.08f,
            .80f,
            .21f,
            .12f,
            .20f,
            0));
    a.add(
        make(
            "night",
            "블루 나이트",
            "BLUE NIGHT",
            1.02f,
            .99f,
            1.04f,
            -.023f,
            -.006f,
            .035f,
            .025f,
            1.17f,
            .85f,
            .35f,
            .37f,
            .27f,
            0));
    a.add(
        make(
            "retro",
            "레트로 90",
            "RETRO 90",
            1.07f,
            1.025f,
            .88f,
            .020f,
            .014f,
            .006f,
            .09f,
            1.12f,
            .87f,
            .40f,
            .27f,
            .29f,
            0));
    a.add(
        make("mono", "모노로그", "MONOLOGUE", 1, 1, 1, 0, 0, 0, .035f, 1.17f, 0, .30f, .15f, .22f, 0));
    a.add(
        make(
            "classic-chrome",
            "클래식 크롬",
            "CLASSIC CHROME",
            1.012f,
            1.025f,
            .992f,
            -.024f,
            .006f,
            .020f,
            .018f,
            1.16f,
            .68f,
            .14f,
            .06f,
            .12f,
            0));
    Recipe portrait=new Recipe();portrait.id="portrait";portrait.name="Portrait";portrait.english="PORTRAIT";
    portrait.exposure=.10f;portrait.contrast=.97f;portrait.saturation=.96f;
    portrait.highlights=-.14f;portrait.shadows=.10f;
    portrait.balance=new float[]{1.003f,1,.997f};
    portrait.hue[1]=-3;portrait.hue[2]=-2;
    portrait.hslSaturation=new float[]{-.025f,-.10f,-.16f,-.06f,-.04f,-.025f,-.04f,-.025f};
    portrait.luminance=new float[]{.012f,.045f,.015f,0,0,0,0,.008f};
    portrait.curves[0]=new float[]{0,.13f,.267f,.399f,.525f,.648f,.77f,.887f,.985f};
    a.add(portrait);
    return a;
  }

  private static Recipe make(
      String id,
      String name,
      String en,
      float r,
      float g,
      float b,
      float sr,
      float sg,
      float sb,
      float fade,
      float c,
      float sat,
      float gr,
      float bl,
      float vi,
      float exp) {
    Recipe p = new Recipe();
    p.id = id;
    p.name = name;
    p.english = en;
    p.balance = new float[] {r, g, b};
    p.shadowColor = new float[] {sr, sg, sb};
    p.fade = fade;
    p.contrast = c;
    p.saturation = sat;
    p.grain = gr;
    p.bloom = bl;
    p.vignette = vi;
    p.exposure = exp;
    return p;
  }
}
