package android.content;
import java.io.*;import java.util.*;import android.net.Uri;import android.os.ParcelFileDescriptor;
public class ContentResolver{
 public final Map<String,File> files=new HashMap<>();public final Set<String> denied=new HashSet<>(),trashed=new HashSet<>();public int deletions;
 private File file(Uri uri)throws FileNotFoundException{if(denied.contains(uri.toString()))throw new SecurityException("denied");File f=files.get(uri.toString());if(f==null||!f.isFile())throw new FileNotFoundException("Missing URI");return f;}
 public android.database.Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
  if(denied.contains(uri.toString()))throw new SecurityException("denied");
  File f=files.get(uri.toString());return new android.database.Cursor(f!=null,trashed.contains(uri.toString()));
 }
 public ParcelFileDescriptor openFileDescriptor(Uri uri,String mode)throws FileNotFoundException{return new ParcelFileDescriptor(file(uri));}
 public InputStream openInputStream(Uri uri)throws FileNotFoundException{return new FileInputStream(file(uri));}
 public OutputStream openOutputStream(Uri uri)throws FileNotFoundException{return new FileOutputStream(file(uri));}
 public Uri insert(Uri root,ContentValues values){throw new UnsupportedOperationException();}
 public int update(Uri uri,ContentValues values,String where,String[] args){return 1;}
 public int delete(Uri uri,String where,String[] args){if(denied.contains(uri.toString()))throw new SecurityException("denied");File f=files.get(uri.toString());if(f==null||!f.exists())throw new IllegalArgumentException("Row already gone");deletions++;f.delete();files.remove(uri.toString());return 1;}
}
