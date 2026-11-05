package com.hoonjay.filmroom.core;

/** Orientation/frame -> quarter rotation -> straighten with safe zoom -> horizontal flip -> crop.
 * Bilinear geometry and box-filter downsampling retain float precision; originals are read-only. */
public final class EditedSource implements Developer.Source {
  private final Developer.Source original;
  private final Geometry base;
  private final EditTransform edit;
  private final int rw,rh,w,h;
  private final double cos,sin,zoom;
  private final float[] a=new float[3],b=new float[3],c=new float[3],d=new float[3];
  public EditedSource(Developer.Source source,int rotation,double ratio,EditTransform edit){
    edit.validate();this.original=source;this.base=new Geometry(source.width(),source.height(),rotation,edit.cropMode<0?ratio:CropFrame.original(source.width(),source.height()));edit=edit.copy();
    rw=edit.turns%2==0?base.width:base.height;rh=edit.turns%2==0?base.height:base.width;
    int[] nativeFrame=CropFrame.dimensions(source.nativeWidth(),source.nativeHeight(),rotation,edit.turns);
    CropFrame.enforce(edit,nativeFrame[0],nativeFrame[1],CropFrame.original(source.nativeWidth(),source.nativeHeight()));this.edit=edit;
    w=Math.max(1,Math.round((edit.right-edit.left)*rw));h=Math.max(1,Math.round((edit.bottom-edit.top)*rh));
    cos=Math.cos(Math.toRadians(edit.angle));sin=Math.sin(Math.toRadians(edit.angle));
    zoom=Math.max(cos+Math.abs(sin)*rh/rw,cos+Math.abs(sin)*rw/rh);
  }
  public int width(){return w;}public int height(){return h;}
  public static int[] outputDimensions(Developer.Source source,int rotation,double ratio,EditTransform edit,int edge){
    Developer.Source nativeSize=new Developer.Source(){public int width(){return source.nativeWidth();}public int height(){return source.nativeHeight();}public void rgb(int x,int y,float[] out){throw new UnsupportedOperationException();}};
    Developer.Source output=resize(new EditedSource(nativeSize,rotation,ratio,edit),edge);return new int[]{output.width(),output.height()};
  }
  public static double ratio(Developer.Source s){return Math.max(s.width(),s.height())/(double)Math.min(s.width(),s.height());}
  public void rgb(int x,int y,float[] out){
    double xx=edit.left*rw+(x+.5)*(edit.right-edit.left)*rw/w-.5;
    double yy=edit.top*rh+(y+.5)*(edit.bottom-edit.top)*rh/h-.5;
    if(edit.flip)xx=rw-1-xx;
    double cx=(xx-(rw-1)*.5)/zoom,cy=(yy-(rh-1)*.5)/zoom;
    xx=cos*cx+sin*cy+(rw-1)*.5;yy=-sin*cx+cos*cy+(rh-1)*.5;
    double sx,sy;
    switch(edit.turns){case 1:sx=yy;sy=base.height-1-xx;break;case 2:sx=base.width-1-xx;sy=base.height-1-yy;break;case 3:sx=base.width-1-yy;sy=xx;break;default:sx=xx;sy=yy;}
    sx=Math.max(0,Math.min(base.width-1,sx));sy=Math.max(0,Math.min(base.height-1,sy));
    int x0=(int)Math.floor(sx),y0=(int)Math.floor(sy),x1=Math.min(base.width-1,x0+1),y1=Math.min(base.height-1,y0+1);
    float fx=(float)(sx-x0),fy=(float)(sy-y0);
    read(x0,y0,a);if(fx<1e-7&&fy<1e-7){System.arraycopy(a,0,out,0,3);return;}
    read(x1,y0,b);read(x0,y1,c);read(x1,y1,d);
    for(int i=0;i<3;i++)out[i]=(a[i]*(1-fx)+b[i]*fx)*(1-fy)+(c[i]*(1-fx)+d[i]*fx)*fy;
  }
  private void read(int x,int y,float[] out){original.rgb(base.sourceX(x,y),base.sourceY(x,y),out);}
  public static Developer.Source resize(Developer.Source input,int longEdge){
    if(longEdge<0||longEdge>16000)throw new IllegalArgumentException("출력 긴 변은 1~16000px 범위입니다.");
    if(longEdge==0||Math.max(input.width(),input.height())<=longEdge)return input;
    final double scale=longEdge/(double)Math.max(input.width(),input.height());
    final int w=Math.max(1,(int)Math.round(input.width()*scale)),h=Math.max(1,(int)Math.round(input.height()*scale));
    return new Developer.Source(){final float[] pixel=new float[3];public int width(){return w;}public int height(){return h;}
      public void rgb(int x,int y,float[] out){
        double x0=x*input.width()/(double)w,x1=(x+1)*input.width()/(double)w,y0=y*input.height()/(double)h,y1=(y+1)*input.height()/(double)h;
        out[0]=out[1]=out[2]=0;double weight=0;
        for(int yy=(int)y0;yy<Math.ceil(y1);yy++)for(int xx=(int)x0;xx<Math.ceil(x1);xx++){
          double k=(Math.min(x1,xx+1)-Math.max(x0,xx))*(Math.min(y1,yy+1)-Math.max(y0,yy));
          input.rgb(Math.min(input.width()-1,xx),Math.min(input.height()-1,yy),pixel);
          for(int c=0;c<3;c++)out[c]+=pixel[c]*k;weight+=k;
        }
        for(int c=0;c<3;c++)out[c]/=weight;
      }
    };
  }
}
