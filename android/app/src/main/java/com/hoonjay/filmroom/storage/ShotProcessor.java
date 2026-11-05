package com.hoonjay.filmroom.storage;

import android.graphics.*;
import android.net.Uri;
import android.os.*;
import com.hoonjay.filmroom.camera.RawMetadata;
import com.hoonjay.filmroom.core.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Serialized, recoverable development queue. DNG is secured before any processing. */
public final class ShotProcessor {
  public interface Listener {
    void progress(String id, int percent, int queued);

    void complete(JSONObject shot);

    void error(String id, String message);
  }

  public final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Set<String> queued = Collections.synchronizedSet(new HashSet<>());
  private final Stores stores;
  private final Listener listener;
  private final Map<String,String> draftSessions=new HashMap<>();
  private final Map<String,Long> draftSequences=new HashMap<>();
  private final Set<String> sessionChanged=new HashSet<>();
  public synchronized String beginDraftSession(String id){String session=UUID.randomUUID().toString();draftSessions.put(id,session);draftSequences.put(id,-1L);sessionChanged.remove(id);return session;}
  public synchronized JSONObject draft(JSONObject reference,String session,long sequence,Recipe recipe,double ratio,EditTransform edit,OutputFormat format,int edge,byte[] thumbnail)throws Exception{
    String id=reference.getString("id");if(!session.equals(draftSessions.get(id))||sequence<draftSequences.getOrDefault(id,-1L)||isQueued(id))return null;
    draftSequences.put(id,sequence);WorkProjects projects=new WorkProjects(stores);JSONObject before=stores.currentShot(reference);JSONObject saved=projects.save(before,recipe,ratio,edit,format,edge,!sessionChanged.contains(id));
    if(saved.optLong("draftRevision")!=before.optLong("draftRevision"))sessionChanged.add(id);
    if(thumbnail!=null)projects.thumbnail(saved,saved.optLong("draftRevision"),thumbnail);return saved;
  }
  public synchronized JSONObject stage(JSONObject shot,Recipe recipe,double ratio,EditTransform edit,OutputFormat format,int edge)throws Exception{
    if(isQueued(shot.getString("id")))throw new IOException("현상이 끝난 후 편집해주세요.");
    JSONObject available=stores.reconcile(stores.currentShot(shot));if(available==null||available.optBoolean("rawMissing"))throw new IOException("원본이 없어 편집할 수 없습니다.");
    draftSessions.remove(shot.getString("id"));return new WorkProjects(stores).save(shot,recipe,ratio,edit,format,edge,true);
  }
  public synchronized JSONObject undo(JSONObject shot)throws Exception{if(isQueued(shot.getString("id")))throw new IOException("현상이 끝난 후 실행취소해주세요.");draftSessions.remove(shot.getString("id"));return new WorkProjects(stores).undo(shot);}
  private final Handler main = new Handler(Looper.getMainLooper());

  public ShotProcessor(Stores stores, Listener listener) {
    this.stores = stores;
    this.listener = listener;
  }

  public int size() {
    return queued.size();
  }

  public boolean isQueued(String id) {
    return queued.contains(id);
  }

  public void accept(
      String id,
      File dng,
      JSONObject meta,
      Bitmap portrait,
      Recipe recipe,
      double ratio,
      String mode, String outputFormat)
      throws Exception {
    JSONObject shot = new JSONObject();
    shot.put("id", id);
    shot.put("created", System.currentTimeMillis());
    shot.put("mode", mode);
    shot.put("rawMeta", meta);
    shot.put("recipe", RecipeJson.write(recipe));
    shot.put("ratio", ratio);
    shot.put("outputFormat", OutputFormat.forCapture(outputFormat).id);
    shot.put("status", "raw_saved");
    stores.saveShot(id, shot);
    enqueue(shot, portrait);
  }

  public void apply(JSONObject shot,Recipe recipe,double ratio,String outputFormat)throws Exception{
    JSONObject latest=stores.currentShot(shot);apply(latest,recipe,ratio,outputFormat,EditJson.read(latest.optJSONObject("edit")),latest.optInt("outputLongEdge",0));
  }
  public synchronized void apply(JSONObject shot, Recipe recipe, double ratio, String outputFormat,EditTransform edit,int longEdge) throws Exception {
    PresetRules.validate(recipe);edit.validate();if(longEdge<0||longEdge>16000)throw new IOException("출력 크기가 올바르지 않습니다.");
    if(OutputFormat.forCapture(outputFormat)==OutputFormat.RAW_ONLY)throw new IOException("보정본은 JPG 또는 PNG로 현상해주세요.");
    shot=stores.currentShot(shot);
    JSONObject available=stores.reconcile(shot);
    if(available==null||available.optBoolean("rawMissing"))throw new IOException("원본이 삭제되어 다시 현상할 수 없습니다.");
    if (queued.contains(shot.getString("id"))) throw new IOException("이 사진은 현상 중입니다.");
    draftSessions.remove(shot.getString("id"));
    JSONObject clone = new JSONObject(shot.toString());
    clone.remove("rawMissing");clone.remove("renderMissing");
    clone.put("recipe", RecipeJson.write(recipe));
    clone.put("ratio", ratio);clone.put("edit",EditJson.write(edit));clone.put("outputLongEdge",longEdge);
    clone.put("outputFormat", OutputFormat.forCapture(outputFormat).id);
    clone.put("status", "raw_saved");clone.put("hasDraft",false);clone.put("updated",System.currentTimeMillis());
    stores.saveShot(clone.getString("id"), clone);
    enqueue(clone, null);
  }

  public void recover() {
    for (JSONObject shot : stores.shots())
      if (Arrays.asList("raw_saved", "processing").contains(shot.optString("status")))
        enqueue(shot, null);
  }

  private void enqueue(JSONObject shot, Bitmap portrait) {
    String id = shot.optString("id");
    if (!queued.add(id)) return;
    main.post(() -> listener.progress(id, 0, size()));
    executor.execute(() -> process(shot, portrait));
  }

  private void process(JSONObject shot, Bitmap portrait) {
    String id = shot.optString("id");
    RawFrame raw = null;
    Uri pending = null;
    File rawFile = null;
    File jpegFile = null;
    try {
      File dir = stores.createShot(id);
      String rawUri = shot.optString("rawUri");
      File local = new File(dir, "negative.dng");
      boolean gallery=WorkProjects.isGallery(shot);
      if (!gallery && rawUri.isEmpty()) {
        if (!local.exists()) throw new IOException("RAW 원본 파일을 찾을 수 없습니다.");
        rawUri = stores.export(local, "PRESS_" + id + "_RAW.dng", "image/x-adobe-dng").toString();
        shot.put("rawUri", rawUri);
        stores.saveShot(id, shot);
      }
      if (portrait != null) {
        try (OutputStream out = new FileOutputStream(new File(dir, "portrait-source.png"))) {
          portrait.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        portrait.recycle();
        portrait = null;
      }
      String stale = shot.optString("pendingUri");
      if (!stale.isEmpty()) stores.deleteUri(Uri.parse(stale));
      shot.put("status", "processing");
      stores.saveShot(id, shot);
      OutputFormat format = OutputFormat.fromId(shot.optString("outputFormat", OutputFormat.PNG16.id));
      Developer.Source source;
      JSONObject meta = shot.optJSONObject("rawMeta");if(meta==null)meta=new JSONObject();
      File bokeh = new File(dir, "portrait-source.png");
      if(gallery){if(format==OutputFormat.RAW_ONLY)throw new IOException("갤러리 사진은 JPG 또는 PNG로 현상해주세요.");source=GalleryImages.source(new WorkProjects(stores).sourceFile(shot),0);}
      else if (shot.optString("mode").equals("인물") && format != OutputFormat.RAW_ONLY) {
        if (!bokeh.exists()) throw new IOException("인물 배경 흐림 원본을 찾을 수 없습니다. RAW 원본은 보존되어 있습니다.");
        Bitmap b = BitmapFactory.decodeFile(bokeh.getPath());
        if (b == null) throw new IOException("인물 원본을 읽을 수 없습니다.");
        source = Stores.bitmap(b);
        b.recycle();
      } else {
        rawFile = local.exists() ? local : stores.rawFile(shot);
        raw = DngReader.read(rawFile);
        RawMetadata.apply(raw, meta);
        source = Developer.raw(raw);
      }
      Recipe recipe = RecipeJson.read(shot.getJSONObject("recipe"));
      double ratio = shot.optDouble("ratio", 1.5);
      int rotation = meta.optInt("rotation", 0);
      Geometry geometry = new Geometry(source.width(), source.height(), rotation, ratio);

      if(format==OutputFormat.RAW_ONLY) {
        // DNG bytes were copied unchanged above. Only a private neutral thumbnail is developed.
        Recipe neutral=Recipe.builtIns().get(0).copy();neutral.strength=0;
        double fullRatio=Math.max(source.width(),source.height())/(double)Math.min(source.width(),source.height());
        int[] dim=new int[2];int[] pixels=Developer.preview(source,neutral,rotation,fullRatio,400,dim);
        Bitmap thumb=Bitmap.createBitmap(pixels,dim[0],dim[1],Bitmap.Config.ARGB_8888);
        stores.thumbnail(id,thumb);thumb.recycle();
        String old=shot.optString("renderUri");
        shot.remove("renderUri");shot.remove("pendingUri");
        boolean swap=rotation%180!=0;
        shot.put("width",swap?source.height():source.width());shot.put("height",swap?source.width():source.height());
        shot.put("renderFormat",format.id);shot.put("renderMime",format.mime);
        shot.put("renderBytes",stores.byteSize(Uri.parse(rawUri)));shot.put("jpegQuality",0);
        shot.put("status","ready");shot.remove("error");stores.saveShot(id,shot);
        if(!old.isEmpty())stores.deleteUri(Uri.parse(old));
        if(local.exists())local.delete();
        main.post(() -> {queued.remove(id);listener.complete(shot);});return;
      }
      if(!gallery){source=new LandscapeSource(source,rotation);rotation=0;ratio=1.5;}
      source=EditedSource.resize(new EditedSource(source,rotation,ratio,EditJson.read(shot.optJSONObject("edit"))),shot.optInt("outputLongEdge",0));
      rotation=0;ratio=EditedSource.ratio(source);
      int savedWidth=source.width(),savedHeight=source.height(),jpegQuality=0;
      if (format != OutputFormat.PNG16) {
        jpegFile = new File(dir, "render-work.jpg");
        JpegExporter.Result result = JpegExporter.write(source, recipe, rotation, ratio, format,
            jpegFile, p -> main.post(() -> listener.progress(id, p, size())));
        savedWidth=result.width; savedHeight=result.height; jpegQuality=result.quality;
      }
      pending = stores.pending("PRESS_" + id + "_JPG." + format.extension, format.mime);
      shot.put("pendingUri", pending.toString());
      stores.saveShot(id, shot);
      try (OutputStream out = stores.context.getContentResolver().openOutputStream(pending)) {
        if (out == null) throw new IOException("보정본 파일을 열 수 없습니다.");
        if (format != OutputFormat.PNG16) {
          try (InputStream in = new BufferedInputStream(new FileInputStream(jpegFile))) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n=in.read(buffer)) != -1) out.write(buffer,0,n);
          }
        } else Developer.render(
            source,
            recipe,
            rotation,
            ratio,
            out,
            p -> main.post(() -> listener.progress(id, p, size())));
      }
      stores.publish(pending);
      int[] dim = new int[2];
      int[] pixels = Developer.preview(source, recipe, rotation, ratio, 400, dim);
      Bitmap thumb = Bitmap.createBitmap(pixels, dim[0], dim[1], Bitmap.Config.ARGB_8888);
      stores.thumbnail(id, thumb);
      thumb.recycle();
      String old = shot.optString("renderUri");
      shot.put("renderUri", pending.toString());
      shot.remove("pendingUri");
      shot.put("width", savedWidth);
      shot.put("height", savedHeight);
      shot.put("renderFormat", format.id);
      shot.put("renderMime", format.mime);
      shot.put("jpegQuality", jpegQuality);
      shot.put("renderBytes", format == OutputFormat.PNG16 ? stores.byteSize(pending) : jpegFile.length());
      shot.put("status", "ready");shot.put("hasDraft",false);
      shot.remove("error");
      stores.saveShot(id, shot);
      pending = null;
      if (!old.isEmpty() && !old.equals(shot.optString("renderUri")))
        stores.deleteUri(Uri.parse(old));
      if (local.exists()) local.delete();
      main.post(() -> {queued.remove(id);listener.complete(shot);});
    } catch (Throwable e) {
      if (pending != null) stores.deleteUri(pending);
      try {
        shot.put("status", "error");shot.put("hasDraft",true);
        shot.put(
            "error",
            e instanceof OutOfMemoryError
                ? "메모리가 부족해 보정본 현상을 중단했습니다. 원본은 보존됩니다."
                : String.valueOf(e.getMessage()));
        shot.remove("pendingUri");
        stores.saveShot(id, shot);
      } catch (Exception ignored) {
      }
      String message = shot.optString("error", "현상 실패. 원본은 보존됩니다.");
      main.post(() -> listener.error(id, message));
    } finally {
      if (jpegFile != null) jpegFile.delete();
      if (raw != null) raw.release();
      if (rawFile != null && rawFile.getParentFile().equals(stores.context.getCacheDir()))
        rawFile.delete();
      queued.remove(id);
      main.post(() -> listener.progress(id, -1, size()));
    }
  }
}
