import com.hoonjay.filmroom.core.*;
import java.io.*;import java.nio.*;import java.nio.file.*;import java.util.*;import java.util.zip.CRC32;

/** Real DNG parsing, neutral calibration and bounded highlight behavior; no camera needed. */
public final class RawCalibrationTests {
 static int checks;static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static void near(float a,float b,float eps,String message){ok(Math.abs(a-b)<=eps,message+" "+a+" / "+b);}
 static final double[] FORWARD={.4360747,.3850649,.1430804,.2225045,.7168786,.0606169,.0139322,.0971045,.7141733};
 static byte[] longs(int...v){ByteBuffer b=ByteBuffer.allocate(v.length*4).order(ByteOrder.LITTLE_ENDIAN);for(int x:v)b.putInt(x);return b.array();}
 static byte[] shorts(int...v){ByteBuffer b=ByteBuffer.allocate(v.length*2).order(ByteOrder.LITTLE_ENDIAN);for(int x:v)b.putShort((short)x);return b.array();}
 static byte[] rationals(double...v){ByteBuffer b=ByteBuffer.allocate(v.length*8).order(ByteOrder.LITTLE_ENDIAN);for(double x:v)b.putInt((int)Math.round(x*1000000)).putInt(1000000);return b.array();}
 static class T {int id,type;byte[] data;T(int i,int t,byte[] d){id=i;type=t;data=d;}}
 static File fixture(File dir,boolean invalid)throws Exception{
  List<T> ts=new ArrayList<>();ts.add(new T(256,4,longs(8)));ts.add(new T(257,4,longs(8)));ts.add(new T(258,3,shorts(16)));ts.add(new T(259,3,shorts(1)));ts.add(new T(262,3,shorts(32803)));T strip=new T(273,4,longs(0));ts.add(strip);ts.add(new T(279,4,longs(128)));ts.add(new T(33422,1,new byte[]{0,1,1,2}));ts.add(new T(50714,5,rationals(64,64,64,64)));ts.add(new T(50717,4,longs(invalid?32:1023)));ts.add(new T(50721,10,rationals(1,0,0,0,1,0,0,0,1)));ts.add(new T(50728,5,rationals(.5,1,.75)));ts.add(new T(50778,3,shorts(21)));ts.add(new T(50829,4,longs(0,0,8,8)));ts.add(new T(50964,10,rationals(FORWARD)));
  int start=8+2+ts.size()*12+4,offset=start;for(T t:ts)if(t.data.length>4)offset+=t.data.length;strip.data=longs(offset);
  ByteBuffer b=ByteBuffer.allocate(offset+128).order(ByteOrder.LITTLE_ENDIAN);b.put((byte)'I').put((byte)'I').putShort((short)42).putInt(8).putShort((short)ts.size());int extra=start;
  for(T t:ts){int unit=t.type==3?2:(t.type==5||t.type==10)?8:t.type==1?1:4;b.putShort((short)t.id).putShort((short)t.type).putInt(t.data.length/unit);if(t.data.length<=4){b.put(t.data);for(int k=t.data.length;k<4;k++)b.put((byte)0);}else{b.putInt(extra);int save=b.position();b.position(extra);b.put(t.data);extra+=t.data.length;b.position(save);}}
  b.putInt(0);b.position(offset);for(int y=0;y<8;y++)for(int x=0;x<8;x++){double n=(y%2==0&&x%2==0)?.5:(y%2==1&&x%2==1)?.75:1;b.putShort((short)Math.round(64+.18*n*959));}
  File f=new File(dir,invalid?"invalid.dng":"native.dng");Files.write(f.toPath(),b.array());return f;
 }
 static long crc(File f)throws Exception{CRC32 crc=new CRC32();crc.update(Files.readAllBytes(f.toPath()));return crc.getValue();}
 public static void main(String[] args)throws Exception{
  File dir=new File(args[0]);dir.mkdirs();File file=fixture(dir,false);long before=crc(file);RawFrame raw=DngReader.read(file);ok(raw.dngCalibrated,"native DNG calibration");ok(!raw.dngShading,"no shading invented");near(raw.gains[0],2,.00001f,"as-shot red");near(raw.gains[3],4/3f,.00001f,"as-shot blue");
  raw.toneCurves=new float[][]{{0,0,1,1},{0,0,1,1},{0,0,1,1}};float[] rgb=new float[3];raw.rgb(4,4,rgb);for(float v:rgb)near(v,.18f,.002f,"no HAL inverse-gamma in DNG development");ColorMath.grade(rgb,Recipe.builtIns().get(0),.5f,.5f,0);for(float v:rgb)near(v,ColorMath.toSrgb(.18f),.003f,"clean midtone sRGB");ok(crc(file)==before,"DNG bytes immutable");
  try{DngReader.read(fixture(dir,true));throw new AssertionError("invalid levels accepted");}catch(IOException expected){checks++;}
  for(float peak=.01f,previous=0;peak<64;peak+=.01f){float[] a={peak,peak*.7f,peak*.3f};ToneMapping.neutralRaw(a);ok(a[0]>=previous,"monotonic shoulder");ok(a[0]<1,"no hard white clip");near(a[1]/a[0],.7f,.00001f,"shoulder hue G");near(a[2]/a[0],.3f,.00001f,"shoulder hue B");if(peak<=.8)near(a[0],peak,.00001f,"untouched midtones");previous=a[0];}
  short[] whites=new short[64];Arrays.fill(whites,(short)1023);RawFrame white=new RawFrame(8,8,whites);white.white=1023;white.black=new float[]{64,64,64,64};white.gains=new float[]{2.12f,1,1,1.74f};white.dngCalibrated=true;white.rgb(4,4,rgb);near(rgb[0],rgb[1],.00001f,"clipped neutral highlight is not magenta");near(rgb[1],rgb[2],.00001f,"clipped neutral B");ok(rgb[0]<1,"white shoulder retains headroom");
  short[] red=new short[64];for(int y=0;y<8;y++)for(int x=0;x<8;x++)red[y*8+x]=(short)((y%2==0&&x%2==0)?1023:100);RawFrame color=new RawFrame(8,8,red);color.white=1023;color.black=new float[]{64,64,64,64};color.gains=white.gains;color.dngCalibrated=true;color.rgb(4,4,rgb);ok(rgb[0]>rgb[1]*10,"red light not neutralized");
  if(args.length>1){File actual=new File(args[1]);long hash=crc(actual);RawFrame f=DngReader.read(actual);ok(f.dngCalibrated&&f.dngShading,"actual Samsung DNG native profile and gain map");ok(f.width==4080&&f.height==3060&&f.white==1023,"actual dimensions and range");ok(Arrays.equals(f.cfa,new int[]{1,3,0,2}),"actual GBRG phase");ok(f.shadeWidth==17&&f.shadeHeight==13,"actual four CFA gain maps");float[] c=new float[3];f.rgb(1600,650,c);for(float v:c)ok(Float.isFinite(v)&&v>=0&&v<1,"actual developed sample finite");try(OutputStream out=new FileOutputStream(new File(dir,"clean-native.png"))){Developer.render(Developer.raw(f),Recipe.builtIns().get(0),0,2,out,null);}ok(crc(actual)==hash,"actual DNG bytes immutable");f.release();}
  System.out.println("PASS: "+checks+" RAW calibration/shoulder assertions");
 }
}
