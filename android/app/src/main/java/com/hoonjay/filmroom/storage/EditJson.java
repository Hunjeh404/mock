package com.hoonjay.filmroom.storage;
import com.hoonjay.filmroom.core.*;import org.json.*;
public final class EditJson {
  public static JSONObject write(EditTransform e)throws JSONException{e.validate();return new JSONObject().put("turns",e.turns).put("flip",e.flip).put("angle",e.angle).put("left",e.left).put("top",e.top).put("right",e.right).put("bottom",e.bottom).put("cropMode",e.cropMode).put("cropPortrait",e.cropPortrait);}
  public static EditTransform read(JSONObject o){EditTransform e=new EditTransform();if(o!=null){e.turns=o.optInt("turns",0);e.flip=o.optBoolean("flip",false);e.angle=(float)o.optDouble("angle",0);e.left=(float)o.optDouble("left",0);e.top=(float)o.optDouble("top",0);e.right=(float)o.optDouble("right",1);e.bottom=(float)o.optDouble("bottom",1);e.cropMode=o.optInt("cropMode",-1);e.cropPortrait=o.optBoolean("cropPortrait",false);}e.validate();return e;}
  public static JSONObject write(EditSettings e)throws JSONException{return new JSONObject().put("version",1).put("recipe",RecipeJson.write(e.recipe)).put("ratio",e.ratio).put("edit",write(e.transform));}
  public static EditSettings settings(JSONObject o)throws JSONException{if(o.getInt("version")!=1)throw new IllegalArgumentException("지원하지 않는 편집 설정입니다.");return new EditSettings(RecipeJson.read(o.getJSONObject("recipe")),o.getDouble("ratio"),read(o.getJSONObject("edit")));}
}
