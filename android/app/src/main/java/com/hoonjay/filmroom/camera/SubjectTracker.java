package com.hoonjay.filmroom.camera;

import android.os.Handler;
import android.os.Looper;
import com.hoonjay.filmroom.core.RegionTracker;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single in-flight preview analysis. Epochs invalidate work across lifecycle/selection changes. */
public final class SubjectTracker implements AutoCloseable {
  public interface Listener {void result(long selection,RegionTracker.Result result);}
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final Handler main=new Handler(Looper.getMainLooper());
  private final AtomicBoolean busy=new AtomicBoolean();
  private final Listener listener;
  private static final class Selection {
    final long id;final float x,y;
    Selection(long id,float x,float y){this.id=id;this.x=x;this.y=y;}
  }
  private volatile Selection selected;
  private Selection initialized;
  private RegionTracker tracker;
  public SubjectTracker(Listener listener){this.listener=listener;}
  public void start(long selection,float x,float y){selected=new Selection(selection,x,y);}
  public void stop(){selected=null;}
  public void frame(long selection,float[] gray,int w,int h){
    Selection target=selected;if(target==null||target.id!=selection||!busy.compareAndSet(false,true))return;
    try{worker.execute(()->{
      try{
        if(selected!=target)return;
        RegionTracker.Result r;
        if(initialized!=target){tracker=new RegionTracker();initialized=target;r=tracker.start(gray,w,h,target.x,target.y);}
        else r=tracker.next(gray,w,h);
        main.post(()->{if(selected==target)listener.result(target.id,r);});
      }finally{busy.set(false);}
    });}catch(RejectedExecutionException e){busy.set(false);}
  }
  public void close(){stop();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
