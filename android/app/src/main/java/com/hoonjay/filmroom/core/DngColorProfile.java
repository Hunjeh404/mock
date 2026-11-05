package com.hoonjay.filmroom.core;

/** DNG dual-illuminant calibration. All matrices are row-major, using column vectors.
 * ForwardMatrix maps white-balanced reference camera RGB to XYZ D50, not sRGB.
 * CCT is estimated iteratively from AsShotNeutral; interpolation is in reciprocal kelvin.
 */
public final class DngColorProfile {
  private static final double[] ID={1,0,0,0,1,0,0,0,1};
  private static final double[] XYZ_D50_TO_RGB={3.1338561,-1.6168667,-.4906146,
      -.9787684,1.9161415,.0334540,.0719453,-.2289914,1.4052427};
  public final float[] gains, matrix;
  public final float baselineGain;
  public final double temperature;

  public DngColorProfile(double[] neutral,double[] cm1,double[] cm2,double[] fm1,
      double[] fm2,double[] cc1,double[] cc2,double[] analog,int illuminant1,int illuminant2,
      double baselineExposure) {
    check(neutral,3);check(cm1,9);check(fm1,9);
    for(double v:neutral)if(v<=0)throw new IllegalArgumentException("Invalid DNG neutral");
    if(cm2==null)cm2=cm1;if(fm2==null)fm2=fm1;
    check(cm2,9);check(fm2,9);
    if(cc1==null)cc1=ID;if(cc2==null)cc2=cc1;if(analog==null)analog=new double[]{1,1,1};
    check(cc1,9);check(cc2,9);check(analog,3);
    double t1=kelvin(illuminant1),t2=kelvin(illuminant2),temp=5000,weight=1;
    if(t1>0&&t2>0&&Math.abs(t1-t2)>1){
      for(int i=0;i<32;i++){
        weight=weight(temp,t1,t2);
        double[] xyz=vec(invert(mul(diag(analog),mul(mix(cc1,cc2,weight),mix(cm1,cm2,weight)))),neutral);
        double sum=xyz[0]+xyz[1]+xyz[2];if(sum<=0)break;
        double x=xyz[0]/sum,y=xyz[1]/sum,n=(x-.3320)/(y-.1858);
        double next=-449*n*n*n+3525*n*n-6823.3*n+5520.33;
        if(!Double.isFinite(next))break;next=Math.max(2000,Math.min(25000,next));
        if(Math.abs(next-temp)<.1){temp=next;break;}temp=(temp+next)*.5;
      }
      weight=weight(temp,t1,t2);
    }
    temperature=temp;
    double[] inverseCalibration=invert(mul(diag(analog),mix(cc1,cc2,weight)));
    double[] referenceNeutral=vec(inverseCalibration,neutral);
    for(double v:referenceNeutral)if(v<=0)throw new IllegalArgumentException("Invalid reference neutral");
    double[] forward=mix(fm1,fm2,weight),white=vec(forward,new double[]{1,1,1});
    double[] d50={.96422,1,.82521};
    // Quantized matrices from phones need the same D50 row normalization as a DNG renderer.
    for(int r=0;r<3;r++){
      if(white[r]<=0)throw new IllegalArgumentException("Invalid forward matrix white");
      for(int c=0;c<3;c++)forward[r*3+c]*=d50[r]/white[r];
    }
    double[] reciprocal={1/referenceNeutral[0],1/referenceNeutral[1],1/referenceNeutral[2]};
    double[] transform=mul(XYZ_D50_TO_RGB,mul(forward,
        mul(diag(reciprocal),mul(inverseCalibration,diag(neutral)))));
    matrix=new float[9];for(int i=0;i<9;i++){
      if(!Double.isFinite(transform[i])||Math.abs(transform[i])>32)throw new IllegalArgumentException("Invalid DNG transform");
      matrix[i]=(float)transform[i];
    }
    gains=new float[]{(float)(1/neutral[0]),(float)(1/neutral[1]),
        (float)(1/neutral[1]),(float)(1/neutral[2])};
    if(!Double.isFinite(baselineExposure)||Math.abs(baselineExposure)>8)throw new IllegalArgumentException("Invalid baseline exposure");
    baselineGain=(float)Math.pow(2,baselineExposure);
  }
  private static void check(double[] a,int n){if(a==null||a.length!=n)throw new IllegalArgumentException("Missing DNG calibration");for(double v:a)if(!Double.isFinite(v))throw new IllegalArgumentException("Invalid DNG calibration");}
  /** ISP linear RGB -> this DNG's white-balanced linear sRGB. */
  public float[] previewTransform(double[] ispMatrix,double[] ispGains,double boost){
    check(ispMatrix,9);check(ispGains,3);
    if(!Double.isFinite(boost)||boost<=0||boost>64)throw new IllegalArgumentException("Invalid ISP boost");
    double[] target=new double[9],scale=new double[3];
    for(int i=0;i<9;i++)target[i]=matrix[i];
    for(int i=0;i<3;i++){
      if(ispGains[i]<=0)throw new IllegalArgumentException("Invalid ISP white balance");
      scale[i]=gains[i==2?3:i]/ispGains[i]*baselineGain/boost;
    }
    double[] out=mul(target,mul(diag(scale),invert(ispMatrix)));float[] result=new float[9];
    for(int i=0;i<9;i++){if(!Double.isFinite(out[i])||Math.abs(out[i])>256)throw new IllegalArgumentException("Invalid preview transform");result[i]=(float)out[i];}
    return result;
  }
  private static double weight(double t,double a,double b){return Math.max(0,Math.min(1,(1/t-1/b)/(1/a-1/b)));}
  private static double kelvin(int i){switch(i){case 17:case 3:return 2856;case 18:return 4874;case 19:return 6774;case 20:return 5503;case 21:return 6504;case 22:return 7504;case 23:return 5003;case 24:return 3200;case 1:case 4:case 9:return 5500;case 10:return 6500;case 11:return 7500;default:return 0;}}
  private static double[] mix(double[] a,double[] b,double w){double[] r=new double[a.length];for(int i=0;i<r.length;i++)r[i]=a[i]*w+b[i]*(1-w);return r;}
  private static double[] diag(double[] a){return new double[]{a[0],0,0,0,a[1],0,0,0,a[2]};}
  private static double[] vec(double[] m,double[] a){double[] r=new double[3];for(int y=0;y<3;y++)for(int x=0;x<3;x++)r[y]+=m[y*3+x]*a[x];return r;}
  private static double[] mul(double[] a,double[] b){double[] r=new double[9];for(int y=0;y<3;y++)for(int x=0;x<3;x++)for(int k=0;k<3;k++)r[y*3+x]+=a[y*3+k]*b[k*3+x];return r;}
  private static double[] invert(double[] m){
    double a=m[0],b=m[1],c=m[2],d=m[3],e=m[4],f=m[5],g=m[6],h=m[7],i=m[8];
    double det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
    if(!Double.isFinite(det)||Math.abs(det)<1e-10)throw new IllegalArgumentException("Singular DNG matrix");
    double[] r={e*i-f*h,c*h-b*i,b*f-c*e,f*g-d*i,a*i-c*g,c*d-a*f,d*h-e*g,b*g-a*h,a*e-b*d};for(int j=0;j<9;j++)r[j]/=det;return r;
  }
}
