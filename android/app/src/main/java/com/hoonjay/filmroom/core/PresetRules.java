package com.hoonjay.filmroom.core;
import java.util.*;
/** File imports validate before writing and never reuse existing identities. */
public final class PresetRules {
  public static final String[] NUMBERS={"strength","exposure","warmth","tint","contrast","saturation","fade","highlights","shadows","grain","bloom","vignette"};
  private PresetRules(){}
  public static void validate(Recipe r){
    if(r.name==null||r.name.trim().isEmpty()||r.name.length()>120||r.name.chars().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException("프리셋 이름이 올바르지 않습니다.");
    range(r.strength,0,1);range(r.exposure,-3,3);range(r.warmth,-50,50);range(r.tint,-50,50);
    range(r.contrast,.5f,1.5f);range(r.saturation,0,1.6f);range(r.fade,0,.2f);
    range(r.highlights,-1,1);range(r.shadows,-1,1);range(r.grain,0,1);range(r.bloom,0,1);range(r.vignette,0,1);
    if(r.balance==null||r.balance.length!=3||r.shadowColor==null||r.shadowColor.length!=3)throw new IllegalArgumentException("프리셋 색상 배열이 올바르지 않습니다.");
    range(r.detail,0,.3f);range(r.noiseLuma,0,1);range(r.noiseChroma,0,1);
    range(r.skinBrightness,-1,1);range(r.skinColor,-1,1);range(r.skinBlush,-1,1);range(r.skinTexture,-1,1);
    if(r.hue==null||r.hue.length!=8||r.hslSaturation==null||r.hslSaturation.length!=8||r.luminance==null||r.luminance.length!=8||r.curves==null||r.curves.length!=4)throw new IllegalArgumentException("HSL/커브 배열이 올바르지 않습니다.");
    for(float x:r.hue)range(x,-60,60);for(float x:r.hslSaturation)range(x,-1,1);for(float x:r.luminance)range(x,-1,1);
    for(float[] curve:r.curves){if(curve==null||curve.length!=9)throw new IllegalArgumentException("커브는 9개 값이어야 합니다.");for(float x:curve)range(x,0,1);}
    for(float x:r.balance)range(x,0,4);for(float x:r.shadowColor)range(x,-1,1);
  }
  private static void range(float v,float lo,float hi){if(!Float.isFinite(v)||v<lo||v>hi)throw new IllegalArgumentException("프리셋 조정값이 지원 범위를 벗어났습니다.");}
  public static List<Recipe> imported(List<Recipe> incoming,List<Recipe> existing){
    if(incoming.isEmpty()||incoming.size()>256)throw new IllegalArgumentException("한 파일에는 프리셋 1~256개를 담을 수 있습니다.");
    for(Recipe r:incoming)validate(r);
    Set<String> names=new HashSet<>();for(Recipe r:existing)names.add(r.name);
    List<Recipe> result=new ArrayList<>();
    for(Recipe r:incoming){Recipe copy=r.copy();copy.id="custom-"+UUID.randomUUID();copy.custom=true;
      String base=r.name.trim(),name=base;int suffix=2;
      while(names.contains(name)){String end=" ("+(suffix++)+")";name=base.substring(0,Math.min(base.length(),120-end.length()))+end;}
      copy.name=name;names.add(name);result.add(copy);
    }return result;
  }
}
