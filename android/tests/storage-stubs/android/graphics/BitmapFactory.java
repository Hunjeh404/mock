package android.graphics;public class BitmapFactory{public static Bitmap decodeFile(String path){return new java.io.File(path).isFile()?new Bitmap():null;}}
