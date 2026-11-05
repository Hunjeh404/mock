package android.content;
import android.content.res.AssetManager;
public final class Context {private final AssetManager assets;public Context(String root){assets=new AssetManager(root);}public AssetManager getAssets(){return assets;}}
