package com.hoonjay.filmroom.storage;

import android.net.Uri;
import com.hoonjay.filmroom.core.*;
import java.io.*;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Develops an external image into a new MediaStore item; the source is read-only. */
public final class GalleryExporter {
  public static Uri write(Stores stores,Uri input,Recipe recipe,double ratio,EditTransform edit,OutputFormat format,int longEdge,IntConsumer progress)throws Exception {
    PresetRules.validate(recipe);edit.validate();
    if(format!=OutputFormat.JPEG_MAX&&format!=OutputFormat.PNG16)throw new IOException("JPG 또는 PNG를 선택해주세요.");
    if(longEdge<0||longEdge>16000)throw new IOException("출력 크기가 올바르지 않습니다.");
    Developer.Source source=GalleryImages.source(stores.context,input,0);
    source=EditedSource.resize(new EditedSource(source,0,ratio,edit),longEdge);
    double outputRatio=EditedSource.ratio(source);
    File temp=new File(stores.context.getCacheDir(),"gallery-"+UUID.randomUUID()+".jpg");Uri pending=null;
    try {
      if(format==OutputFormat.JPEG_MAX)JpegExporter.write(source,recipe,0,outputRatio,format,temp,p->progress.accept(p));
      pending=stores.pending("FR_EDIT_"+System.currentTimeMillis()+"_"+UUID.randomUUID().toString().substring(0,8)+"."+format.extension,format.mime);
      try(OutputStream output=stores.context.getContentResolver().openOutputStream(pending)){
        if(output==null)throw new IOException("저장할 파일을 열지 못했습니다.");
        if(format==OutputFormat.PNG16)Developer.render(source,recipe,0,outputRatio,output,p->progress.accept(p));
        else try(InputStream encoded=new BufferedInputStream(new FileInputStream(temp))){Stores.copy(encoded,output);}
      }
      stores.publish(pending);Uri result=pending;pending=null;return result;
    } finally {temp.delete();if(pending!=null)stores.deleteUri(pending);}
  }
  private GalleryExporter(){}
}
