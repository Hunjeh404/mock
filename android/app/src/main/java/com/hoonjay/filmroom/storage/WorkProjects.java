package com.hoonjay.filmroom.storage;

import android.graphics.Bitmap;
import com.hoonjay.filmroom.core.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Local non-destructive projects. Imported source bytes never depend on a future media grant. */
public final class WorkProjects {
  private final Stores stores;
  public WorkProjects(Stores stores){this.stores=stores;}
  public static boolean isGallery(JSONObject shot){return "gallery".equals(shot.optString("sourceKind"));}
  public File sourceFile(JSONObject shot){return new File(new File(stores.root,shot.optString("id")),"source.image");}
  public JSONObject findPhoto(GalleryImages.Item item){synchronized(stores){for(JSONObject shot:stores.shots())if(isGallery(shot)&&item.thumbnailKey().equals(shot.optString("originKey"))&&sourceFile(shot).isFile())return shot;return null;}}
  public JSONObject importPhoto(GalleryImages.Item item)throws Exception{
    JSONObject existing=findPhoto(item);if(existing!=null)return existing;
    String id=System.currentTimeMillis()+"-gallery-"+UUID.randomUUID().toString().substring(0,8);File dir=stores.createShot(id),file=new File(dir,"source.image");
    try{
      try(InputStream in=stores.context.getContentResolver().openInputStream(item.uri);FileOutputStream out=new FileOutputStream(file)){
        if(in==null)throw new IOException("사진 원본을 읽을 수 없습니다.");Stores.copy(in,out);out.getFD().sync();
      }
      Developer.Source source=GalleryImages.source(file,480);double ratio=Math.min(3,Math.max(source.nativeWidth(),source.nativeHeight())/(double)Math.min(source.nativeWidth(),source.nativeHeight()));
      EditTransform transform=new EditTransform();CropFrame.choose(transform,CropFrame.ORIGINAL,source.nativeHeight()>source.nativeWidth(),source.nativeWidth(),source.nativeHeight(),0);
      Recipe neutral=new Recipe();neutral.id="original";neutral.name="원본";
      JSONObject shot=new JSONObject().put("id",id).put("sourceKind","gallery").put("originUri",item.uri.toString()).put("originKey",item.thumbnailKey()).put("originName",item.name)
        .put("created",System.currentTimeMillis()).put("updated",System.currentTimeMillis()).put("mode","갤러리").put("status","draft").put("hasDraft",true)
        .put("recipe",RecipeJson.write(neutral)).put("ratio",ratio).put("edit",EditJson.write(transform)).put("outputFormat",OutputFormat.JPEG_MAX.id).put("outputLongEdge",0)
        .put("width",source.nativeWidth()).put("height",source.nativeHeight());
      int[] dim=new int[2];Developer.Source edited=new EditedSource(source,0,ratio,transform);int[] pixels=Developer.preview(edited,neutral,0,EditedSource.ratio(edited),360,dim);Bitmap thumb=Bitmap.createBitmap(pixels,dim[0],dim[1],Bitmap.Config.ARGB_8888);
      try{stores.thumbnail(id,thumb);}finally{thumb.recycle();}stores.saveShot(id,shot);return shot;
    }catch(Throwable error){File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();throw error;}
  }
  private JSONObject state(JSONObject shot)throws Exception{
    return new JSONObject().put("recipe",RecipeJson.write(RecipeJson.read(shot.getJSONObject("recipe")))).put("ratio",shot.optDouble("ratio",1.5))
      .put("edit",EditJson.write(EditJson.read(shot.optJSONObject("edit")))).put("outputFormat",shot.optString("outputFormat",OutputFormat.JPEG_MAX.id)).put("outputLongEdge",shot.optInt("outputLongEdge",0));
  }
  private void install(JSONObject shot,JSONObject state)throws Exception{for(String key:new String[]{"recipe","ratio","edit","outputFormat","outputLongEdge"})shot.put(key,state.get(key));}
  /** Saves settings only. No MediaStore insertion, encoder or change to source bytes. */
  public JSONObject save(JSONObject reference,Recipe recipe,double ratio,EditTransform edit,OutputFormat output,int edge,boolean remember)throws Exception{
    new EditSettings(recipe,ratio,edit);if(edge<0||edge>16000)throw new IOException("출력 크기가 올바르지 않습니다.");
    synchronized(stores){JSONObject shot=stores.currentShot(reference),previous=state(shot);JSONObject next=new JSONObject().put("recipe",RecipeJson.write(recipe)).put("ratio",ratio).put("edit",EditJson.write(edit)).put("outputFormat",output==OutputFormat.PNG16?output.id:OutputFormat.JPEG_MAX.id).put("outputLongEdge",edge);
      if(previous.toString().equals(next.toString()))return shot;
      if(remember){JSONArray history=shot.optJSONArray("undo");if(history==null)history=new JSONArray();JSONArray kept=new JSONArray();for(int i=Math.max(0,history.length()-29);i<history.length();i++)kept.put(history.getJSONObject(i));kept.put(previous);shot.put("undo",kept);}
      install(shot,next);shot.put("hasDraft",true).put("updated",System.currentTimeMillis()).put("draftRevision",shot.optLong("draftRevision")+1);shot.remove("error");stores.saveShot(shot.getString("id"),shot);return shot;
    }
  }
  public JSONObject undo(JSONObject reference)throws Exception{
    synchronized(stores){JSONObject shot=stores.currentShot(reference);JSONArray history=shot.optJSONArray("undo");if(history==null||history.length()==0)throw new IOException("실행취소할 편집이 없습니다.");
      install(shot,history.getJSONObject(history.length()-1));JSONArray kept=new JSONArray();for(int i=0;i<history.length()-1;i++)kept.put(history.getJSONObject(i));shot.put("undo",kept).put("hasDraft",true).put("updated",System.currentTimeMillis()).put("draftRevision",shot.optLong("draftRevision")+1);stores.saveShot(shot.getString("id"),shot);return shot;}
  }
  public void thumbnail(JSONObject reference,long revision,byte[] bytes)throws Exception{
    synchronized(stores){JSONObject shot=stores.currentShot(reference);if(shot.optLong("draftRevision")!=revision)return;File target=new File(new File(stores.root,shot.getString("id")),"thumb.jpg"),temp=new File(target.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);out.getFD().sync();}if(!temp.renameTo(target))throw new IOException("미리보기를 저장할 수 없습니다.");shot.put("thumbUpdated",System.nanoTime()).put("thumbRevision",revision);stores.saveShot(shot.getString("id"),shot);}
  }
}
