package com.hoonjay.filmroom.storage;
import android.graphics.*;import android.net.Uri;import com.hoonjay.filmroom.camera.RawMetadata;import com.hoonjay.filmroom.core.*;import org.json.*;import java.io.*;import java.util.function.BooleanSupplier;
/** Full-resolution display raster from the exact export geometry/grade path. No public output. */
public final class FullPhotoRenderer {
  public static Bitmap display(Stores stores,JSONObject shot,boolean original,BooleanSupplier canceled)throws Exception{
    if(!original&&!shot.optBoolean("hasDraft")&&!shot.optString("renderUri").isEmpty()){Bitmap bitmap=GalleryImages.bitmap(stores.context,Uri.parse(shot.getString("renderUri")),0);if(canceled.getAsBoolean()){bitmap.recycle();throw new IOException("Cancelled");}return checked(bitmap);}
    boolean neutral=original||!shot.optBoolean("hasDraft")&&"raw".equals(shot.optString("renderFormat",shot.optString("outputFormat")));
    return render(stores,shot,neutral?new Recipe():RecipeJson.read(shot.getJSONObject("recipe")),shot.optDouble("ratio",1.5),neutral?new EditTransform():EditJson.read(shot.optJSONObject("edit")),neutral?0:shot.optInt("outputLongEdge",0),neutral,canceled);
  }
  public static Bitmap render(Stores stores,JSONObject shot,Recipe recipe,double ratio,EditTransform edit,int edge,boolean original,BooleanSupplier canceled)throws Exception{
    RawFrame raw=null;File rawFile=null;try{
      if(canceled.getAsBoolean())throw new IOException("Cancelled");Developer.Source source;int rotation=shot.optJSONObject("rawMeta")==null?0:shot.getJSONObject("rawMeta").optInt("rotation");
      if(WorkProjects.isGallery(shot))source=GalleryImages.source(new WorkProjects(stores).sourceFile(shot),0);
      else if(!original&&"인물".equals(shot.optString("mode"))){File file=new File(new File(stores.root,shot.getString("id")),"portrait-source.png");Bitmap bitmap=BitmapFactory.decodeFile(file.getPath());if(bitmap==null)throw new IOException("인물 원본을 찾을 수 없습니다.");try{source=Stores.bitmap(bitmap);}finally{bitmap.recycle();}}
      else{rawFile=stores.rawFile(shot);raw=DngReader.read(rawFile);RawMetadata.apply(raw,shot.getJSONObject("rawMeta"));source=Developer.raw(raw);}
      if(canceled.getAsBoolean())throw new IOException("Cancelled");
      if(original){ratio=EditedSource.ratio(source);source=new EditedSource(source,rotation,ratio,new EditTransform());}
      else source=EditedSource.resize(new EditedSource(source,rotation,ratio,edit),edge);
      return bitmap(source,recipe,canceled);
    }finally{if(raw!=null)raw.release();if(rawFile!=null&&rawFile.getParentFile().equals(stores.context.getCacheDir()))rawFile.delete();}
  }
  public static Bitmap bitmap(Developer.Source source,Recipe recipe,BooleanSupplier canceled)throws IOException{
    if(canceled.getAsBoolean())throw new IOException("Cancelled");int width=source.width(),height=source.height();if((long)width*height*4>96L*1024*1024||width>16384||height>16384)throw new IOException("사진이 커서 전체 해상도 표시가 어렵습니다.");Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);boolean complete=false;
    try{int[] pixels=new int[width];Developer.renderRows(source,recipe,0,EditedSource.ratio(source),(y,row)->{if(canceled.getAsBoolean())throw new IOException("Cancelled");for(int x=0;x<width;x++)pixels[x]=0xff000000|(Math.round(ColorMath.clamp(row[x*3])*255)<<16)|(Math.round(ColorMath.clamp(row[x*3+1])*255)<<8)|Math.round(ColorMath.clamp(row[x*3+2])*255);bitmap.setPixels(pixels,0,width,0,y,width,1);},null);complete=true;return bitmap;}finally{if(!complete)bitmap.recycle();}
  }
  public static Bitmap checked(Bitmap bitmap)throws IOException{if(bitmap.getByteCount()>96L*1024*1024||bitmap.getWidth()>16384||bitmap.getHeight()>16384){bitmap.recycle();throw new IOException("사진이 커서 전체 해상도 표시가 어렵습니다.");}return bitmap;}
  private FullPhotoRenderer(){}
}
