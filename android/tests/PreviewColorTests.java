import com.hoonjay.filmroom.core.*;import java.nio.file.*;import java.io.*;import java.util.*;
public final class PreviewColorTests {
 static int checks;static void ok(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 static void near(float a,float b,float e,String m){ok(Math.abs(a-b)<=e,m+" "+a+" / "+b);}
 static final float[] ID={1,0,0,0,1,0,0,0,1};
 static float[][] srgb(){float[] c=ToneMapping.srgbCurve(128);return new float[][]{c,c,c};}
 static float[] apply(double[] a,double[] v){float[] r=new float[3];for(int y=0;y<3;y++)for(int x=0;x<3;x++)r[y]+=a[y*3+x]*v[x];return r;}
 public static void main(String[] args)throws Exception{
  Locale.setDefault(Locale.US);Path root=Paths.get(args[0]);Files.createDirectories(root);
  double[] fm={.4360747,.3850649,.1430804,.2225045,.7168786,.0606169,.0139322,.0971045,.7141733},ident={1,0,0,0,1,0,0,0,1};
  DngColorProfile profile=new DngColorProfile(new double[]{.5,1,.75},ident,null,fm,null,null,null,null,21,0,0);
  double[] ccm={1.1,-.1,0,-.1,1.2,-.1,0,-.1,1.1},wb={1.2,.8,2};
  PreviewColorModel physical=new PreviewColorModel(srgb(),profile.previewTransform(ccm,wb,2));
  float[] encoded=apply(ccm,new double[]{.09*wb[0]*2,.18*wb[1]*2,.135*wb[2]*2});float[] curve=ToneMapping.srgbCurve(128);
  for(int c=0;c<3;c++)encoded[c]=ToneMapping.map(encoded[c],curve);
  physical.linear(encoded,1);for(float v:encoded)near(v,.18f,.00015f,"WB/CCM/boost inverse -> DNG neutral");
  PreviewColorModel unity=new PreviewColorModel(srgb(),ID);float[] gray={ToneMapping.map(.18f,curve),ToneMapping.map(.18f,curve),ToneMapping.map(.18f,curve)};unity.linear(gray,1);for(float v:gray)near(v,.18f,.00002f,"no double gamma");
  float[] bright={ToneMapping.map(.4f,curve),ToneMapping.map(.4f,curve),ToneMapping.map(.4f,curve)};unity.linear(bright,4);float[] expected={1.6f,1.6f,1.6f};ToneMapping.neutralRaw(expected);for(int c=0;c<3;c++)near(bright[c],expected[c],.00005f,"long-exposure simulation before shoulder");
  byte[] packed=unity.texture();ok(packed.length==1024*3*4,"RGB inverse packed 16-bit");boolean lowBits=false;for(int c=0;c<3;c++)for(int i=0;i<1024;i++){int p=(c*1024+i)*4,n=(packed[p]&255)*256+(packed[p+1]&255);near(unity.inverse(i/1023f,c),n/65535f,.000001f,"LUT precision");if((n&255)!=0)lowBits=true;}ok(lowBits,"not 8-bit linear quantization");
  float[] nonIdentity={1.4f,-.2f,-.2f,.1f,.8f,.1f,0,-.1f,1.1f};PreviewColorModel matrix=new PreviewColorModel(srgb(),nonIdentity);float[] column=matrix.columnMajor();for(int y=0;y<3;y++)for(int x=0;x<3;x++)near(column[x*3+y],nonIdentity[y*3+x],0,"column-major GPU upload");
  try{new PreviewColorModel(new float[][]{{0,0,.5f,.7f,1,.6f},curve,curve},ID);throw new AssertionError("decreasing tone accepted");}catch(IllegalArgumentException e){checks++;}
  try{profile.previewTransform(new double[9],wb,1);throw new AssertionError("singular ISP CCM accepted");}catch(IllegalArgumentException e){checks++;}
  float[][] saved=srgb();PreviewColorModel immutable=new PreviewColorModel(saved,nonIdentity);float before=immutable.inverse(.5f,0);saved[0][3]=0;nonIdentity[0]=8;near(immutable.inverse(.5f,0),before,0,"immutable metadata snapshot");
  float[] dark={0,0,.02f,.01f,.18f,.22f,.5f,.62f,1,1};
  PreviewColorModel[] models={unity,new PreviewColorModel(new float[][]{dark,dark,dark},new float[]{1.7f,-.1f,0,0,1.5f,.1f,-.1f,.2f,1.4f}),matrix};
  int w=64,h=64;byte[] input=new byte[w*h*4];for(int i=0;i<w*h;i++){input[i*4]=(byte)((i*17)%256);input[i*4+1]=(byte)((i*53+i/17)%256);input[i*4+2]=(byte)((i*119+i/7)%256);input[i*4+3]=(byte)255;}Files.write(root.resolve("preview-input.rgba"),input);
  List<Recipe> looks=new ArrayList<>();looks.add(Recipe.builtIns().get(0));Recipe film=Recipe.builtIns().get(2).copy();film.exposure=.4f;film.shadows=.3f;looks.add(film);Recipe low=Recipe.builtIns().get(4).copy();low.strength=.4f;low.exposure=-.7f;looks.add(low);
  try(PrintWriter json=new PrintWriter(Files.newBufferedWriter(root.resolve("preview-cases.json")))){json.print("{\"cases\":[");int index=0;for(int m=0;m<models.length;m++){Files.write(root.resolve("preview-tone-"+m+".rgba"),models[m].texture());for(float scale:new float[]{.5f,1,4})for(Recipe r:looks){r.grain=0;r.bloom=0;byte[] out=new byte[input.length];for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=(y*w+x)*4;float[] rgb={(input[i]&255)/255f,(input[i+1]&255)/255f,(input[i+2]&255)/255f};models[m].linear(rgb,scale);ColorMath.grade(rgb,r,(x+.5f)/w,(y+.5f)/h,0);for(int c=0;c<3;c++)out[i+c]=(byte)Math.round(rgb[c]*255);out[i+3]=(byte)255;}Files.write(root.resolve("preview-expected-"+index+".rgba"),out);if(index>0)json.print(',');json.print("{\"index\":"+index+",\"model\":"+m+",\"scale\":"+scale+",\"matrix\":"+Arrays.toString(models[m].columnMajor())+",\"uniforms\":{");int n=0;for(String field:new String[]{"strength","exposure","warmth","tint","contrast","saturation","fade","shadows","highlights","grain","bloom","vignette","skinBrightness","skinColor","skinBlush","skinTexture"}){if(n++>0)json.print(',');json.print("\""+field+"\":"+Recipe.class.getField(field).getFloat(r));}json.print(",\"advanced\":0,\"balance\":"+Arrays.toString(r.balance)+",\"shadowColor\":"+Arrays.toString(r.shadowColor)+"}}");index++;}}json.print("]}");}
  System.out.println("PASS: "+checks+" preview color assertions; 27 production GLSL fixtures");
 }
}
