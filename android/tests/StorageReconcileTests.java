import java.nio.file.*;import java.io.*;import java.util.*;
import org.json.*;import android.content.*;import android.os.Environment;
import com.hoonjay.filmroom.storage.Stores;
import com.hoonjay.filmroom.storage.EditJson;
import com.hoonjay.filmroom.core.*;
public final class StorageReconcileTests{
 static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static JSONObject shot(Stores stores,String id,String raw,String render)throws Exception{JSONObject s=new JSONObject().put("id",id).put("status","ready").put("rawUri",raw).put("renderUri",render);stores.saveShot(id,s);Files.write(new File(stores.createShot(id),"thumb.jpg").toPath(),new byte[]{1});return s;}
 static void make(ContentResolver r,Path root,String uri)throws Exception{File f=root.resolve("media-"+r.files.size()+"-"+System.nanoTime()).toFile();Files.write(f.toPath(),new byte[]{1,2,3});r.files.put(uri,f);}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("filmroom-storage-test");ContentResolver resolver=new ContentResolver();Stores stores=new Stores(new Context(root.toFile(),resolver));
  JSONObject gone=shot(stores,"one","content://gone/raw","content://gone/render");
  check(stores.shots().isEmpty(),"Deleted external pair is absent despite cached thumbnail and shot.json");
  check(new File(stores.root,"one/shot.json").isFile(),"Reconciliation does not destructively delete metadata");
  stores.deleteShot(gone);check(stores.shots().isEmpty()&&!new File(stores.root,"one/shot.json").exists(),"Already-deleted URI does not abort internal deletion");
  make(resolver,root,"content://two/raw");JSONObject raw=shot(stores,"two","content://two/raw","content://two/deleted-render");
  JSONObject state=stores.reconcile(raw);check(state!=null&&state.optString("renderUri").isEmpty()&&state.optBoolean("renderMissing")&&!state.optBoolean("rawMissing"),"Missing render falls back to remaining RAW");
  check(stores.rawFile(state).length()==3,"Remaining RAW is readable via production copy path");
  stores.deleteShot(state);check(!resolver.files.containsKey("content://two/raw")&&stores.shots().isEmpty(),"Delete reconciled snapshot still removes original recorded RAW URI and caches");
  make(resolver,root,"content://three/render");JSONObject render=shot(stores,"three","content://three/deleted-raw","content://three/render");
  state=stores.reconcile(render);check(state!=null&&state.optBoolean("rawMissing")&&!state.optString("renderUri").isEmpty(),"Remaining render can be viewed with missing RAW flag");
  stores.deleteShot(state);check(stores.shots().isEmpty(),"Render-only row deletes without missing-RAW error");
  make(resolver,root,"content://four/raw");JSONObject denied=shot(stores,"four","content://four/raw","");resolver.denied.add("content://four/raw");
  state=stores.reconcile(denied);check(state!=null&&!state.optBoolean("rawMissing"),"Security failure remains unknown, not treated as deletion");
  boolean blocked=false;try{stores.deleteShot(state);}catch(SecurityException e){blocked=true;}
  check(blocked&&new File(stores.root,"four/shot.json").isFile(),"Denied deletion retains gallery metadata for retry");
  resolver.denied.clear();stores.deleteShot(denied);
  JSONObject unavailable=shot(stores,"five","content://unmounted/raw","");Environment.state="unmounted";
  check(stores.reconcile(unavailable)!=null,"Unmounted volume preserves record");Environment.state=Environment.MEDIA_MOUNTED;
  check(stores.reconcile(unavailable)==null,"Mounted volume with both files gone hides record");stores.deleteShot(unavailable);
  make(resolver,root,"content://trash/raw");JSONObject trash=shot(stores,"trash","content://trash/raw","");resolver.trashed.add("content://trash/raw");
  check(stores.reconcile(trash)==null,"System gallery trash hides a row even when bytes still exist");resolver.trashed.clear();
  check(stores.reconcile(trash)!=null,"Restoring from system trash makes the preserved record visible again");stores.deleteShot(trash);
  make(resolver,root,"content://stale/raw");JSONObject stale=shot(stores,"stale","content://stale/raw","");resolver.files.get("content://stale/raw").delete();
  check(stores.reconcile(stale)==null,"Stale provider row with missing underlying bytes is hidden by FD probe");stores.deleteShot(stale);
  JSONObject pending=shot(stores,"six","","");pending.put("status","processing");stores.saveShot("six",pending);Files.write(new File(stores.root,"six/negative.dng").toPath(),new byte[]{1});
  check(stores.reconcile(pending)!=null,"Private processing RAW is not hidden");
  Files.writeString(new File(stores.root,"six/deleted").toPath(),"deleted");check(stores.shots().isEmpty(),"Deletion tombstone prevents cached metadata resurrection");
  // Real production Stores deletion against an in-memory content-provider adapter.
  for(int i=0;i<3;i++){String id="batch-"+i;make(resolver,root,"content://"+id+"/raw");make(resolver,root,"content://"+id+"/render");JSONObject pair=shot(stores,id,"content://"+id+"/raw","content://"+id+"/render");stores.deleteShot(pair);check(!resolver.files.containsKey("content://"+id+"/raw")&&!resolver.files.containsKey("content://"+id+"/render"),"Batch removes each RAW/JPG pair");check(new File(stores.root,id+"/deleted").isFile(),"Batch tombstone recorded");}
  make(resolver,root,"content://busy/raw");JSONObject busy=shot(stores,"busy","content://busy/raw","");busy.put("status","processing");stores.saveShot("busy",busy);boolean busyBlocked=false;try{stores.deleteShot(busy);}catch(IOException expected){busyBlocked=true;}check(busyBlocked&&resolver.files.containsKey("content://busy/raw"),"Processing deletion cannot remove protected RAW");busy.put("status","ready");stores.saveShot("busy",busy);stores.deleteShot(busy);
  Recipe look=Recipe.builtIns().get(10).copy();look.hue[0]=12;look.noiseLuma=.3f;look.skinBrightness=.2f;look.skinColor=.3f;look.skinBlush=-.4f;look.skinTexture=-.7f;EditTransform edit=new EditTransform();edit.left=.1f;edit.right=.8f;edit.angle=4;edit.turns=1;edit.flip=true;edit.cropMode=3;edit.cropPortrait=true;
  EditSettings copied=new EditSettings(look,1.5,edit);stores.copySettings(copied);EditSettings pasted=stores.copiedSettings();
  check(pasted!=null&&pasted.transform.angle==4&&pasted.transform.turns==1&&pasted.transform.flip&&pasted.recipe.hue[0]==12&&pasted.recipe.noiseLuma==.3f&&pasted.recipe.skinTexture==-.7f&&pasted.recipe.skinBrightness==.2f&&pasted.recipe.skinColor==.3f&&pasted.recipe.skinBlush==-.4f&&pasted.transform.cropMode==3&&pasted.transform.cropPortrait,"Production settings clipboard retains color/curves/NR/geometry");
  pasted.recipe.hue[0]=55;pasted.transform.left=.2f;EditSettings again=stores.copiedSettings();check(again.recipe.hue[0]==12&&again.transform.left==.1f,"Pasting never mutates copied settings");
  check(EditJson.read(EditJson.write(edit)).right==edit.right,"Edit geometry metadata round trip");
  check(EditJson.read(new JSONObject().put("left",.1).put("right",.9)).cropMode==-1,"Old geometry metadata retains legacy capture frame");
  boolean badMode=false;try{EditJson.read(new JSONObject().put("cropMode",9));}catch(IllegalArgumentException error){badMode=true;}check(badMode,"Invalid crop choice is rejected");
  System.out.println("PASS: "+checks+" production Stores checks; missing URI deletion, paired-file fallbacks, metadata preservation, permissions, unmounted volume, recovery and tombstones. Host ContentResolver adapter, not Android MediaStore device I/O.");
 }
}
