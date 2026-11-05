package com.hoonjay.filmroom.storage;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import com.hoonjay.filmroom.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class Stores {
  public final Context context;
  public final File root, presetFile;

  public Stores(Context c) {
    context = c;
    root = new File(c.getFilesDir(), "shots");
    root.mkdirs();
    presetFile = new File(c.getFilesDir(), "presets.json");
  }

  public void copySettings(EditSettings settings)throws Exception{atomic(new File(context.getFilesDir(),"edit-clipboard.json"),EditJson.write(settings).toString());}
  public EditSettings copiedSettings(){try{return EditJson.settings(json(new File(context.getFilesDir(),"edit-clipboard.json")));}catch(Exception e){return null;}}

  public static void atomic(File file, String s) throws IOException {
    File temp = new File(file.getPath() + ".tmp");
    try (FileOutputStream out = new FileOutputStream(temp)) {
      out.write(s.getBytes(StandardCharsets.UTF_8));
      out.getFD().sync();
    }
    if (!temp.renameTo(file)) throw new IOException("파일 저장에 실패했습니다.");
  }

  public static JSONObject json(File f) throws IOException, JSONException {
    return new JSONObject(
        new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
  }

  public List<Recipe> presets() {
    List<Recipe> list = Recipe.builtIns();
    try {
      JSONArray a =
          new JSONArray(
              new String(
                  java.nio.file.Files.readAllBytes(presetFile.toPath()), StandardCharsets.UTF_8));
      for (int i = 0; i < a.length(); i++) list.add(RecipeJson.read(a.getJSONObject(i)));
    } catch (Exception ignored) {
    }
    return list;
  }

  public synchronized void saveRecipe(Recipe r) throws Exception {
    List<Recipe> all = presets();
    JSONArray a = new JSONArray();
    boolean found = false;
    for (Recipe p : all)
      if (p.custom) {
        if (p.id.equals(r.id)) {
          a.put(RecipeJson.write(r));
          found = true;
        } else a.put(RecipeJson.write(p));
      }
    if (!found) a.put(RecipeJson.write(r));
    atomic(presetFile, a.toString(2));
  }

  public synchronized void importRecipes(List<Recipe> incoming)throws Exception{
    JSONArray all=new JSONArray();for(Recipe r:presets())if(r.custom)all.put(RecipeJson.write(r));
    for(Recipe r:incoming){PresetRules.validate(r);all.put(RecipeJson.write(r));}
    atomic(presetFile,all.toString(2));
  }

  public synchronized void deleteRecipe(String id) throws Exception {
    JSONArray a = new JSONArray();
    for (Recipe p : presets()) if (p.custom && !p.id.equals(id)) a.put(RecipeJson.write(p));
    atomic(presetFile, a.toString(2));
  }

  public File createShot(String id) {
    File d = new File(root, id);
    d.mkdirs();
    return d;
  }

  public JSONObject currentShot(JSONObject reference)throws Exception{
    File dir=new File(root,reference.getString("id"));if(new File(dir,"deleted").exists())throw new IOException("삭제된 사진입니다.");
    return json(new File(dir,"shot.json"));
  }

  public synchronized void saveShot(String id, JSONObject o) throws Exception {
    File dir=new File(root,id);if(new File(dir,"deleted").exists())throw new IOException("삭제된 작업입니다.");
    atomic(new File(createShot(id), "shot.json"), o.toString(2));
  }

  public List<JSONObject> shots() {
    List<JSONObject> r = new ArrayList<>();
    File[] dirs = root.listFiles();
    if (dirs != null)
      for (File d : dirs)
        try {
          JSONObject shot=reconcile(json(new File(d,"shot.json")));
          if(shot!=null)r.add(shot);
        } catch (Exception ignored) {
        }
    r.sort((a,b)->{int time=Long.compare(b.optLong("updated",b.optLong("created")),a.optLong("updated",a.optLong("created")));return time!=0?time:b.optString("id").compareTo(a.optString("id"));});
    return r;
  }

  private ShotAvailability.Presence presence(String value){
    if(value.isEmpty())return ShotAvailability.Presence.MISSING;
    String state=Environment.getExternalStorageState();
    if(!Environment.MEDIA_MOUNTED.equals(state)&&!Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))return ShotAvailability.Presence.UNKNOWN;
    // Gallery trash is logically deleted even while its underlying file still exists.
    try(android.database.Cursor cursor=context.getContentResolver().query(Uri.parse(value),new String[]{MediaStore.MediaColumns.IS_TRASHED},null,null,null)){
      if(cursor!=null){if(!cursor.moveToFirst())return ShotAvailability.Presence.MISSING;
        int column=cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED);if(column>=0&&cursor.getInt(column)!=0)return ShotAvailability.Presence.MISSING;}
    }catch(SecurityException e){return ShotAvailability.Presence.UNKNOWN;}
    catch(IllegalArgumentException e){/* A provider without this column can still be probed by opening. */}
    catch(Exception e){return ShotAvailability.Presence.UNKNOWN;}
    try(ParcelFileDescriptor file=context.getContentResolver().openFileDescriptor(Uri.parse(value),"r")){
      return file==null?ShotAvailability.Presence.UNKNOWN:ShotAvailability.Presence.PRESENT;
    }catch(FileNotFoundException e){return ShotAvailability.Presence.MISSING;}
    catch(Exception e){return ShotAvailability.Presence.UNKNOWN;}
  }
  /** Derive gallery state without deleting metadata or remaining source files. */
  public JSONObject reconcile(JSONObject saved){
    try{
      JSONObject shot=new JSONObject(saved.toString());File dir=new File(root,shot.getString("id"));
      boolean local=new File(dir,"gallery".equals(shot.optString("sourceKind"))?"source.image":"negative.dng").isFile(),deleted=new File(dir,"deleted").exists();
      ShotAvailability.Presence raw="gallery".equals(shot.optString("sourceKind"))?(local?ShotAvailability.Presence.PRESENT:ShotAvailability.Presence.MISSING):presence(shot.optString("rawUri")),render=presence(shot.optString("renderUri"));
      boolean unfinished=Arrays.asList("raw_saved","processing").contains(shot.optString("status"));
      // Pending rows with an actual private source remain recoverable. Stale ready/error thumbnails are not files.
      if(!ShotAvailability.visible(deleted,local,raw,render,unfinished&&local))return null;
      if(raw==ShotAvailability.Presence.MISSING&&!local){shot.put("rawMissing",true);shot.remove("rawUri");}
      else shot.put("rawMissing",false);
      shot.put("renderMissing",false);
      if(render==ShotAvailability.Presence.MISSING&&!shot.optString("renderUri").isEmpty()){
        shot.remove("renderUri");shot.put("renderMissing",true);
      }
      return shot;
    }catch(Exception e){return null;}
  }

  public Bitmap thumbnail(String id) {
    return BitmapFactory.decodeFile(new File(new File(root, id), "thumb.jpg").getPath());
  }

  public void thumbnail(String id, Bitmap bmp) throws IOException {
    try (OutputStream out = new FileOutputStream(new File(createShot(id), "thumb.jpg"))) {
      bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
    }
  }

  public Uri pending(String filename, String mime) throws IOException {
    ContentValues v = new ContentValues();
    v.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
    v.put(MediaStore.Images.Media.MIME_TYPE, mime);
    v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PressCamera");
    v.put(MediaStore.Images.Media.IS_PENDING, 1);
    v.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
    Uri u = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
    if (u == null) throw new IOException("사진 저장 공간을 만들 수 없습니다.");
    return u;
  }

  public void publish(Uri u) {
    ContentValues v = new ContentValues();
    v.put(MediaStore.Images.Media.IS_PENDING, 0);
    context.getContentResolver().update(u, v, null, null);
  }

  public long byteSize(Uri uri) {
    try (ParcelFileDescriptor file=context.getContentResolver().openFileDescriptor(uri,"r")) {
      return file==null ? 0 : Math.max(0,file.getStatSize());
    } catch (Exception ignored) { return 0; }
  }

  public Uri export(File file, String name, String mime) throws Exception {
    Uri u = pending(name, mime);
    try (InputStream in = new FileInputStream(file);
        OutputStream out = context.getContentResolver().openOutputStream(u)) {
      if (out == null) throw new IOException("사진을 저장할 수 없습니다.");
      copy(in, out);
      publish(u);
      return u;
    } catch (Exception e) {
      deleteUri(u);
      throw e;
    }
  }

  public static void copy(InputStream in, OutputStream out) throws IOException {
    byte[] b = new byte[65536];
    for (int n; (n = in.read(b)) >= 0; ) out.write(b, 0, n);
  }

  public void deleteUri(Uri u) {
    if (u != null)
      try {
        context.getContentResolver().delete(u, null, null);
      } catch (Exception ignored) {
      }
  }

  public File rawFile(JSONObject shot) throws Exception {
    String id = shot.getString("id");
    File local = new File(createShot(id), "negative.dng");
    if (local.exists()) return local;
    String uri = shot.optString("rawUri");
    if (uri.isEmpty()) throw new IOException("RAW 원본을 찾을 수 없습니다.");
    File tmp = new File(context.getCacheDir(), "raw-" + id + ".dng");
    try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(uri));
        OutputStream out = new FileOutputStream(tmp)) {
      if (in == null) throw new IOException("RAW 원본에 접근할 수 없습니다.");
      copy(in, out);
    }
    return tmp;
  }

  public synchronized void deleteShot(JSONObject shot) throws Exception {
    String id=shot.getString("id");File dir=new File(root,id);
    // Use original recorded URIs even if a gallery snapshot has reconciled missing entries away.
    JSONObject stored=shot;File metadata=new File(dir,"shot.json");if(metadata.isFile())stored=json(metadata);
    if(Arrays.asList("raw_saved","processing").contains(stored.optString("status")))throw new IOException("현상이 끝난 후 삭제해주세요.");
    for(String key:new String[]{"rawUri","renderUri","pendingUri"}){
      String uri=stored.optString(key);if(uri.isEmpty()||presence(uri)==ShotAvailability.Presence.MISSING)continue;
      try{context.getContentResolver().delete(Uri.parse(uri),null,null);}
      catch(IllegalArgumentException e){if(presence(uri)!=ShotAvailability.Presence.MISSING)throw e;}
    }
    if(dir.exists()){
      // Once public deletion succeeded, leftover cache/metadata cannot resurrect this gallery row.
      atomic(new File(dir,"deleted"),"deleted");
      File[] files=dir.listFiles();if(files!=null)for(File file:files)if(!file.getName().equals("deleted"))file.delete();
      // Keep the tiny tombstone: an already-started worker must never recreate this project.
    }
    new File(context.getCacheDir(),"raw-"+id+".dng").delete();
  }

  public static Developer.Source bitmap(Bitmap b) {
    int w = b.getWidth(), h = b.getHeight();
    int[] px = new int[w * h];
    b.getPixels(px, 0, w, 0, 0, w, h);
    float[] lut = new float[256];
    for (int i = 0; i < 256; i++) lut[i] = ColorMath.toLinear(i / 255f);
    return new Developer.Source() {
      public int width() {
        return w;
      }

      public int height() {
        return h;
      }

      public void rgb(int x, int y, float[] out) {
        int v = px[y * w + x];
        out[0] = lut[(v >>> 16) & 255];
        out[1] = lut[(v >>> 8) & 255];
        out[2] = lut[v & 255];
      }
    };
  }
}
