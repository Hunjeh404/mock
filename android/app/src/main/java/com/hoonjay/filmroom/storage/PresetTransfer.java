package com.hoonjay.filmroom.storage;
import com.hoonjay.filmroom.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Versioned portable recipe data; no file paths, code, photos or network dependencies. */
public final class PresetTransfer {
  public static final int MAX_BYTES=2*1024*1024;
  private PresetTransfer(){}
  public static String encode(List<Recipe> recipes)throws Exception{
    if(recipes.isEmpty()||recipes.size()>256)throw new IOException("프리셋 1~256개를 선택해주세요.");
    JSONArray list=new JSONArray();for(Recipe r:recipes){PresetRules.validate(r);list.put(RecipeJson.write(r));}
    JSONObject root=new JSONObject();root.put("format","filmroom-presets");root.put("version",2);root.put("presets",list);
    String encoded=root.toString(2);if(encoded.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IOException("파일이 2MB를 초과합니다. 프리셋을 적게 선택해주세요.");return encoded;
  }
  private static void validateArray(JSONArray a,int length)throws Exception{
    if(a.length()!=length)throw new IOException("조정 배열 길이가 올바르지 않습니다.");for(int i=0;i<length;i++)if(!(a.get(i) instanceof Number))throw new IOException("숫자 조정값이 올바르지 않습니다.");
  }
  public static List<Recipe> decode(InputStream input,List<Recipe> existing)throws Exception{
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
    while((n=input.read(buffer))!=-1){if(bytes.size()+n>MAX_BYTES)throw new IOException("프리셋 파일은 2MB 이하만 가져올 수 있습니다.");bytes.write(buffer,0,n);}
    JSONObject root=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
    if(!"filmroom-presets".equals(root.optString("format"))||!(root.opt("version") instanceof Number)||(root.optDouble("version",-1)!=1&&root.optDouble("version",-1)!=2))throw new IOException("지원하는 Film Room 프리셋 파일이 아닙니다.");
    JSONArray list=root.getJSONArray("presets");if(list.length()<1||list.length()>256)throw new IOException("프리셋 개수가 올바르지 않습니다.");
    List<Recipe> incoming=new ArrayList<>();
    for(int i=0;i<list.length();i++){
      JSONObject item=list.getJSONObject(i);
      if(!(item.get("name") instanceof String))throw new IOException("프리셋 이름이 없습니다.");
      for(String key:PresetRules.NUMBERS)if(!(item.get(key) instanceof Number))throw new IOException("숫자 조정값이 올바르지 않습니다: "+key);
      for(String key:new String[]{"balance","shadowColor"}){JSONArray colors=item.getJSONArray(key);if(colors.length()!=3)throw new IOException("색상 배열은 3개 값이어야 합니다.");for(int c=0;c<3;c++)if(!(colors.get(c) instanceof Number))throw new IOException("색상 값이 올바르지 않습니다.");}
      for(String key:new String[]{"noiseLuma","noiseChroma","skinBrightness","skinColor","skinBlush","skinTexture"})if(item.has(key)&&!(item.get(key) instanceof Number))throw new IOException("노이즈 조정값이 올바르지 않습니다.");
      for(String key:new String[]{"hue","hslSaturation","luminance"})if(item.has(key))validateArray(item.getJSONArray(key),8);
      if(item.has("curves")){JSONArray curves=item.getJSONArray("curves");if(curves.length()!=4)throw new IOException("커브 채널은 4개여야 합니다.");for(int c=0;c<4;c++)validateArray(curves.getJSONArray(c),9);}
      Recipe r=RecipeJson.read(item);PresetRules.validate(r);incoming.add(r);
    }
    return PresetRules.imported(incoming,existing);
  }
}
