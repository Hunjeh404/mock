package com.hoonjay.filmroom.storage;

import android.graphics.Bitmap;
import com.hoonjay.filmroom.core.*;
import java.io.*;

/** RAW/float development -> final 8-bit sRGB bitmap -> JPEG. No PNG/JPEG intermediate decode. */
public final class JpegExporter {
  public static final class Result {
    public final int width, height, quality;
    Result(int width, int height, int quality) {
      this.width=width; this.height=height; this.quality=quality;
    }
  }

  private JpegExporter() {}

  public static Result write(Developer.Source source, Recipe recipe, int rotation,
      double ratio, OutputFormat format, File file, Developer.Progress progress) throws IOException {
    if(format!=OutputFormat.JPEG_MAX&&format!=OutputFormat.JPEG_SMALL)throw new IOException("JPG 저장 형식이 아닙니다.");
    Geometry geo = new Geometry(source.width(), source.height(), rotation, ratio);
    Bitmap bitmap = Bitmap.createBitmap(geo.width, geo.height, Bitmap.Config.ARGB_8888);
    try {
      int[] argb = new int[geo.width];
      final Bitmap target = bitmap;
      Developer.renderRows(source, recipe, rotation, ratio, (y, row) -> {
        for (int x=0;x<geo.width;x++) argb[x]=0xff000000
            | (Math.round(ColorMath.clamp(row[x*3])*255)<<16)
            | (Math.round(ColorMath.clamp(row[x*3+1])*255)<<8)
            | Math.round(ColorMath.clamp(row[x*3+2])*255);
        target.setPixels(argb,0,geo.width,0,y,geo.width,1);
      }, p -> {if(progress!=null)progress.update(p*80/100);});
      if(progress!=null)progress.update(85);
      if (format == OutputFormat.JPEG_MAX) {
        encode(bitmap, 100, file);
        if(progress!=null)progress.update(100);
        return new Result(bitmap.getWidth(), bitmap.getHeight(), 100);
      }
      while (true) {
        final Bitmap current = bitmap;
        int quality = JpegBudget.selectQuality(q -> encode(current,q,file), OutputFormat.MAX_BYTES);
        if (quality >= 0) {
          if (file.length() > OutputFormat.MAX_BYTES) throw new IOException("JPG가 10MB를 초과했습니다.");
          if(progress!=null)progress.update(100);
          return new Result(bitmap.getWidth(),bitmap.getHeight(),quality);
        }
        if (bitmap.getWidth()==1 && bitmap.getHeight()==1)
          throw new IOException("10MB 이하 JPG를 만들 수 없습니다.");
        int[] size=JpegBudget.smaller(bitmap.getWidth(),bitmap.getHeight(),file.length(),OutputFormat.MAX_BYTES);
        Bitmap smaller=Bitmap.createScaledBitmap(bitmap,size[0],size[1],true);
        bitmap.recycle(); bitmap=smaller;
      }
    } finally {
      bitmap.recycle();
    }
  }

  private static long encode(Bitmap bitmap, int quality, File file) throws IOException {
    if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
    try (OutputStream out=new BufferedOutputStream(new FileOutputStream(file))) {
      if (!bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out)) throw new IOException("JPG 저장 실패");
    }
    return file.length();
  }
}
