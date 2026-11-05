package com.hoonjay.filmroom.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.*;
import android.view.*;
import com.hoonjay.filmroom.core.*;
import java.util.function.Supplier;

/** Last camera-layer child; outside the clone its touch handler returns false. */
public final class FloatingShutterView extends View {
  private final FloatingShutterState state=new FloatingShutterState();
  private final Paint paint=new Paint(3);
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final SharedPreferences prefs;
  private final Runnable shutter;
  private final Supplier<CameraLayout> layout;
  private final float density;
  private float downX,downY;
  private boolean moved,held,busy;
  private int sl=0,st=0,sr=0,sb=0;
  private final Runnable hold=()->{
    if(!moved){held=true;state.begin(state.x,state.y);performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);invalidate();}
  };
  public FloatingShutterView(Context c,SharedPreferences p,Runnable capture,Supplier<CameraLayout> layout) {
    super(c);setAlpha(.5f);prefs=p;shutter=capture;this.layout=layout;
    density=getResources().getDisplayMetrics().density;
    state.visible=p.getBoolean("floating_shutter",false);
    state.nx=p.getFloat("floating_x",.8f);state.ny=p.getFloat("floating_y",.65f);
    setElevation(24*density);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    setContentDescription("이동식 촬영 버튼. 길게 눌러 이동, 원래 셔터 위에 놓아 삭제.");
  }
  public void insets(int left,int top,int right,int bottom){sl=left;st=top;sr=right;sb=bottom;updateBounds();}
  public void busy(boolean value){busy=value;invalidate();}
  private void updateBounds(){state.bounds(sl+35*density,st+35*density,getWidth()-sr-35*density,getHeight()-sb-35*density);invalidate();}
  protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);updateBounds();}
  public void fromBase(int phase,float x,float y) {
    if(phase==0)state.begin(x,y);
    else if(phase==1)state.move(x,y);
    else if(phase==2){CameraLayout l=layout.get();state.finish(l.shutterX,l.shutterY,31*density);save();}
    else if(phase==3)state.cancel();
    invalidate();
  }
  private void save(){prefs.edit().putBoolean("floating_shutter",state.visible).putFloat("floating_x",state.nx).putFloat("floating_y",state.ny).apply();}
  protected void onDraw(Canvas canvas) {
    if(!state.visible)return;
    float radius=29*density;
    paint.setStyle(Paint.Style.FILL);paint.setColor(0xf51b1a1c);
    paint.setShadowLayer(8*density,0,3*density,0x99000000);canvas.drawCircle(state.x,state.y,radius+5*density,paint);paint.clearShadowLayer();
    paint.setColor(busy?0xff856048:Ui.ACCENT);canvas.drawCircle(state.x,state.y,radius,paint);
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f*density);paint.setColor(0xff473329);canvas.drawCircle(state.x,state.y,radius-5*density,paint);
    paint.setStyle(Paint.Style.FILL);paint.setColor(0xff33231c);canvas.drawCircle(state.x,state.y,3*density,paint);
    if(state.dragging){
      CameraLayout l=layout.get();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(Ui.ACCENT);
      canvas.drawCircle(l.shutterX,l.shutterY,38*density,paint);paint.setStyle(Paint.Style.FILL);
      paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(11*density);paint.setColor(Ui.TEXT);
      canvas.drawText("원래 셔터에 놓으면 보조 셔터 삭제",getWidth()/2f,l.bottomTop-10*density,paint);
    }
  }
  public boolean onTouchEvent(MotionEvent event) {
    float x=event.getX(),y=event.getY();
    switch(event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        if(!state.hit(x,y,35*density))return false;
        downX=x;downY=y;moved=false;held=false;handler.postDelayed(hold,500);return true;
      case MotionEvent.ACTION_MOVE:
        if(Math.hypot(x-downX,y-downY)>9*density)moved=true;
        if(held)state.move(x,y);else if(moved)handler.removeCallbacks(hold);
        invalidate();return true;
      case MotionEvent.ACTION_UP:
        handler.removeCallbacks(hold);
        if(held){CameraLayout l=layout.get();state.finish(l.shutterX,l.shutterY,31*density);save();}
        else if(!moved){shutter.run();performClick();}
        invalidate();return true;
      case MotionEvent.ACTION_CANCEL:
        handler.removeCallbacks(hold);if(held)state.cancel();invalidate();return true;
      default:return true;
    }
  }
  protected void onDetachedFromWindow(){handler.removeCallbacks(hold);super.onDetachedFromWindow();}
  public boolean performClick(){super.performClick();return true;}
}
