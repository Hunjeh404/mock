package android.content.res;
import java.io.*;
public final class AssetManager {private final File root;public AssetManager(String path){root=new File(path);}public InputStream open(String path)throws IOException{return new FileInputStream(new File(root,path));}}
