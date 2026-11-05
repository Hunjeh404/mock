package com.hoonjay.filmroom.storage;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Size;
import com.hoonjay.filmroom.core.Developer;
import com.hoonjay.filmroom.core.ColorMath;
import java.nio.*;
import java.io.IOException;
import java.util.*;

/** Permission-aware, paged view of system photos. Never writes to an input URI. */
public final class GalleryImages {
  public enum Access { NONE, SELECTED, ALL }
  public static String[] permissions(int sdk) {
    if (sdk >= 34) return new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
    return new String[]{sdk >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE};
  }
  public static Access access(Context c) {
    int sdk=Build.VERSION.SDK_INT;
    String full=sdk>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE;
    if(c.checkSelfPermission(full)==PackageManager.PERMISSION_GRANTED)return Access.ALL;
    if(sdk>=34&&c.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED)return Access.SELECTED;
    return Access.NONE;
  }
  public static final class Item {
    public final Uri uri;
    public final String name,mime;
    public final long id,date,bytes,modified;
    public Item(Uri uri,String name,String mime,long id,long date,long bytes){this(uri,name,mime,id,date,bytes,0);}
    public Item(Uri uri,String name,String mime,long id,long date,long bytes,long modified){this.uri=uri;this.name=name;this.mime=mime;this.id=id;this.date=date;this.bytes=bytes;this.modified=modified;}
    public String thumbnailKey(){return uri+"|"+modified+"|"+bytes;}
  }
  public static final class Page {
    public final List<Item> items=new ArrayList<>();
    public boolean more;
  }
  /** A physical photo folder; storage volume and path keep equal names/bucket hashes apart. */
  public static final class Album {
    public final String volume,bucket,path,name,key;
    public final Item cover;
    public int count;
    Album(String volume,String bucket,String path,String name,Item cover){
      this.volume=volume;this.bucket=bucket;this.path=path;this.name=name;this.cover=cover;
      key=part(volume)+part(bucket)+part(path);count=1;
    }
    private static String part(String value){return value==null?"-1:":value.length()+":"+value;}
    public long stableId(){long hash=0xcbf29ce484222325L;for(int i=0;i<key.length();i++){hash^=key.charAt(i);hash*=0x100000001b3L;}return hash;}
  }
  public static Page page(Context c,Item after,int count) throws IOException {
    return page(c,null,after,count);
  }
  private static final String[] PHOTO_COLUMNS={MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME,MediaStore.Images.Media.MIME_TYPE,MediaStore.Images.Media.DATE_ADDED,MediaStore.Images.Media.SIZE,MediaStore.Images.Media.GENERATION_MODIFIED};
  private static Item item(Uri collection,Cursor cursor){
    String name=cursor.getString(1),mime=cursor.getString(2);long id=cursor.getLong(0);
    return new Item(ContentUris.withAppendedId(collection,id),name==null?"사진":name,mime==null?"image/*":mime,id,cursor.getLong(3),cursor.getLong(4),cursor.getLong(5));
  }
  private static void filter(StringBuilder where,List<String> args,String column,String value){
    where.append(" AND ").append(column);if(value==null)where.append(" IS NULL");else{where.append(" = ?");args.add(value);}
  }
  public static Page page(Context c,Album album,Item after,int count) throws IOException {
    if(access(c)==Access.NONE)throw new SecurityException("사진 접근 권한이 필요합니다.");
    Uri collection=MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
    StringBuilder selection=new StringBuilder("is_pending=0 AND is_trashed=0");List<String> args=new ArrayList<>();
    if(album!=null){filter(selection,args,MediaStore.Images.Media.VOLUME_NAME,album.volume);filter(selection,args,MediaStore.Images.Media.BUCKET_ID,album.bucket);filter(selection,args,MediaStore.Images.Media.RELATIVE_PATH,album.path);}
    if(after!=null){selection.append(" AND (date_added < ? OR (date_added = ? AND _id < ?))");args.add(""+after.date);args.add(""+after.date);args.add(""+after.id);}
    Bundle query=new Bundle();query.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,selection.toString());query.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,args.isEmpty()?null:args.toArray(new String[0]));
    query.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,"date_added DESC, _id DESC");query.putInt(ContentResolver.QUERY_ARG_LIMIT,count+1);
    Page result=new Page();
    try(Cursor cursor=c.getContentResolver().query(collection,PHOTO_COLUMNS,query,null)){
      if(cursor==null)throw new IOException("사진 목록을 읽지 못했습니다.");
      while(cursor.moveToNext()){
        if(result.items.size()==count){result.more=true;break;}
        result.items.add(item(collection,cursor));
      }
    }
    return result;
  }
  public static List<Album> albums(Context c)throws IOException {
    if(access(c)==Access.NONE)throw new SecurityException("사진 접근 권한이 필요합니다.");
    Uri collection=MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
    String[] projection=Arrays.copyOf(PHOTO_COLUMNS,10);
    projection[6]=MediaStore.Images.Media.VOLUME_NAME;projection[7]=MediaStore.Images.Media.BUCKET_ID;projection[8]=MediaStore.Images.Media.RELATIVE_PATH;projection[9]=MediaStore.Images.Media.BUCKET_DISPLAY_NAME;
    Bundle query=new Bundle();query.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,"is_pending=0 AND is_trashed=0");query.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,"date_added DESC, _id DESC");
    LinkedHashMap<String,Album> folders=new LinkedHashMap<>();
    try(Cursor cursor=c.getContentResolver().query(collection,projection,query,null)){
      if(cursor==null)throw new IOException("앨범 목록을 읽지 못했습니다.");
      while(cursor.moveToNext()){
        String volume=cursor.getString(6),bucket=cursor.getString(7),path=cursor.getString(8);
        String key=Album.part(volume)+Album.part(bucket)+Album.part(path);Album existing=folders.get(key);
        if(existing!=null){existing.count++;continue;}
        String name=cursor.getString(9);
        if(name==null||name.trim().isEmpty()){String clean=path==null?"":path.replaceAll("/+$","");name=clean.isEmpty()?"기타 사진":clean.substring(clean.lastIndexOf('/')+1);}
        folders.put(key,new Album(volume,bucket,path,name,item(collection,cursor)));
      }
    }
    return new ArrayList<>(folders.values());
  }
  public static Bitmap thumbnail(Context c,Uri uri,int edge,CancellationSignal cancel)throws IOException {
    return c.getContentResolver().loadThumbnail(uri,new Size(edge,edge),cancel);
  }
  public static Bitmap bitmap(Context c,Uri uri,int maxEdge)throws IOException {
    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(),uri),(decoder,info,source)->{
      decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
      decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
      int w=info.getSize().getWidth(),h=info.getSize().getHeight();
      if(maxEdge>0&&Math.max(w,h)>maxEdge){double scale=maxEdge/(double)Math.max(w,h);decoder.setTargetSize(Math.max(1,(int)Math.round(w*scale)),Math.max(1,(int)Math.round(h*scale)));}
    });
  }
  public static Developer.Source source(Context c,Uri uri,int maxEdge)throws IOException {
    return source(ImageDecoder.createSource(c.getContentResolver(),uri),maxEdge);
  }
  public static Developer.Source source(java.io.File file,int maxEdge)throws IOException{return source(ImageDecoder.createSource(file),maxEdge);}
  private static Developer.Source source(ImageDecoder.Source inputSource,int maxEdge)throws IOException{
    final int[] nativeSize=new int[2];
    Bitmap bitmap=ImageDecoder.decodeBitmap(inputSource,(decoder,info,input)->{
      nativeSize[0]=info.getSize().getWidth();nativeSize[1]=info.getSize().getHeight();
      decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
      int edge=Math.max(nativeSize[0],nativeSize[1]);if(maxEdge>0&&edge>maxEdge){double scale=maxEdge/(double)edge;decoder.setTargetSize(Math.max(1,(int)Math.round(nativeSize[0]*scale)),Math.max(1,(int)Math.round(nativeSize[1]*scale)));}
    });
    try {
      Developer.Source pixels;
      if(bitmap.getConfig()==Bitmap.Config.RGBA_F16){
        int width=bitmap.getWidth(),height=bitmap.getHeight(),stride=bitmap.getRowBytes()/2;
        ShortBuffer rgba=ByteBuffer.allocate(bitmap.getByteCount()).order(ByteOrder.nativeOrder()).asShortBuffer();bitmap.copyPixelsToBuffer(rgba);
        final boolean premultiplied=bitmap.isPremultiplied();
        pixels=new Developer.Source(){public int width(){return width;}public int height(){return height;}public void rgb(int x,int y,float[] out){int at=y*stride+x*4;float alpha=android.util.Half.toFloat(rgba.get(at+3));for(int channel=0;channel<3;channel++){float value=android.util.Half.toFloat(rgba.get(at+channel));if(premultiplied&&alpha>0)value/=alpha;out[channel]=ColorMath.toLinear(value);}}};
      }else pixels=Stores.bitmap(bitmap);
      final Developer.Source decoded=pixels;
      return new Developer.Source(){public int width(){return decoded.width();}public int height(){return decoded.height();}public int nativeWidth(){return nativeSize[0];}public int nativeHeight(){return nativeSize[1];}public void rgb(int x,int y,float[] rgb){decoded.rgb(x,y,rgb);}};
    }finally{bitmap.recycle();}
  }
  private GalleryImages(){}
}
