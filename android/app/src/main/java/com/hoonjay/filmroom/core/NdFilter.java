package com.hoonjay.filmroom.core;
import java.util.*;
/** Hardware filter stops only. No simulated brightness gain and no changes to RAW samples. */
public final class NdFilter {
  private NdFilter(){}
  public static float[] supported(float[] reported,boolean writable){
    if(!writable||reported==null)return new float[]{0};
    TreeSet<Float> values=new TreeSet<>();values.add(0f);
    for(float v:reported)if(Float.isFinite(v)&&v>=0)values.add(v);
    float[] result=new float[values.size()];int i=0;for(float v:values)result[i++]=v;return result;
  }
  public static boolean available(float[] values){return values!=null&&values.length>1;}
  public static float step(float[] values,float current,int steps){
    if(!available(values))return 0;
    int at=0;for(int i=1;i<values.length;i++)if(Math.abs(values[i]-current)<Math.abs(values[at]-current))at=i;
    return values[Math.max(0,Math.min(values.length-1,at+steps))];
  }
}
