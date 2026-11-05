import com.hoonjay.filmroom.core.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Compare the actual live GLSL against the CPU exporter over encoded ISP test colors. */
public final class ShaderFixtures {
  public static void main(String[] args) throws Exception {
    Locale.setDefault(Locale.US);
    Path dir = Paths.get(args[0]); Files.createDirectories(dir);
    Files.writeString(dir.resolve("live.frag"), FilmShader.fragment());
    List<Recipe> recipes = Recipe.builtIns();
    for (float exposure : new float[] {-2, -.5f, .75f, 2})
      for (float strength : new float[] {0, .4f, 1}) {
        Recipe r = recipes.get(4).copy();
        r.exposure = exposure; r.strength = strength;
        r.warmth = 25; r.tint = -12; r.shadows = .7f; r.highlights = -.4f;
        recipes.add(r);
      }
    for(float strength:new float[]{0,.4f,1}){
      Recipe r=new Recipe();r.strength=strength;r.hue=new float[]{12,-15,20,-25,8,-10,17,-12};
      r.hslSaturation=new float[]{.2f,-.3f,.1f,-.2f,.3f,-.1f,-.2f,.2f};r.luminance=new float[]{.1f,-.1f,.15f,-.2f,.1f,0,-.1f,.1f};
      r.curves[0]=new float[]{.01f,.12f,.23f,.41f,.57f,.68f,.79f,.90f,.99f};
      r.curves[1][4]=.53f;r.curves[2][2]=.23f;r.curves[3][6]=.78f;recipes.add(r);
    }
    for(float value:new float[]{-1,-.4f,.4f,1})for(float strength:new float[]{0,1}){Recipe skin=new Recipe();skin.strength=strength;skin.skinBrightness=value;skin.skinColor=-value;skin.skinBlush=value*.6f;recipes.add(skin);}
    Recipe skinMono=new Recipe();skinMono.saturation=0;skinMono.skinBrightness=.7f;recipes.add(skinMono);
    int w = 64, h = 64;
    byte[] input = new byte[w*h*4];
    for (int i = 0; i < w*h; i++) {
      input[i*4] = (byte) ((i * 17) % 256);
      input[i*4+1] = (byte) ((i * 53 + i / 17) % 256);
      input[i*4+2] = (byte) ((i * 119 + i / 7) % 256);
      input[i*4+3] = (byte) 255;
    }
    Files.write(dir.resolve("input.rgba"), input);
    try (PrintWriter json = new PrintWriter(Files.newBufferedWriter(dir.resolve("cases.json")))) {
      json.print("{\"width\":64,\"height\":64,\"cases\":[");
      for (int n = 0; n < recipes.size(); n++) {
        Recipe r = recipes.get(n); r.grain = 0; r.bloom = 0;
        byte[] expected = new byte[w*h*4];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
          int i = (y*w+x)*4;
          float[] rgb = {ColorMath.toLinear((input[i]&255)/255f),
              ColorMath.toLinear((input[i+1]&255)/255f), ColorMath.toLinear((input[i+2]&255)/255f)};
          ColorMath.grade(rgb, r, (x+.5f)/w, (y+.5f)/h, 0);
          for (int c = 0; c < 3; c++) expected[i+c] = (byte) Math.round(rgb[c]*255);
          expected[i+3] = (byte)255;
        }
        Files.write(dir.resolve("expected-"+n+".rgba"), expected);
        if(AdvancedColor.active(r))Files.write(dir.resolve("lut-"+n+".rgba"),AdvancedColor.lut(r));
        if (n > 0) json.print(',');
        json.print("{\"index\":"+n+",\"uniforms\":{");
        int count = 0;
        for (String field : new String[] {"strength","exposure","warmth","tint","contrast",
            "saturation","fade","shadows","highlights","grain","bloom","vignette","skinBrightness","skinColor","skinBlush","skinTexture"}) {
          if (count++ > 0) json.print(',');
          json.print("\""+field+"\":"+Recipe.class.getField(field).getFloat(r));
        }
        json.print(",\"advanced\":"+(AdvancedColor.active(r)?1:0));
        json.print(",\"balance\":"+Arrays.toString(r.balance)+",\"shadowColor\":"+Arrays.toString(r.shadowColor)+"}}");
      }
      json.print("]}");
    }
    Recipe nr=new Recipe();nr.noiseLuma=.6f;nr.noiseChroma=.8f;
    Developer.Source source=new Developer.Source(){public int width(){return w;}public int height(){return h;}public void rgb(int x,int y,float[] rgb){int i=(y*w+x)*4;for(int c=0;c<3;c++)rgb[c]=ColorMath.toLinear((input[i+c]&255)/255f);}};
    int[] dims=new int[2],pixels=Developer.preview(source,nr,0,1,w,dims);byte[] expected=new byte[w*h*4];
    for(int i=0;i<pixels.length;i++){expected[i*4]=(byte)(pixels[i]>>16);expected[i*4+1]=(byte)(pixels[i]>>8);expected[i*4+2]=(byte)pixels[i];expected[i*4+3]=(byte)255;}
    Files.write(dir.resolve("expected-noise.rgba"),expected);
    for(int mode=0;mode<2;mode++){Recipe press=new Recipe();press.detail=.16f;press.contrast=1.015f;press.saturation=1.02f;press.highlights=-.08f;press.shadows=.04f;if(mode==1){press.noiseLuma=.025f;press.noiseChroma=.14f;}int[] px=Developer.preview(source,press,0,1,w,dims);for(int i=0;i<px.length;i++){expected[i*4]=(byte)(px[i]>>16);expected[i*4+1]=(byte)(px[i]>>8);expected[i*4+2]=(byte)px[i];expected[i*4+3]=(byte)255;}Files.write(dir.resolve("expected-press-"+mode+".rgba"),expected);}
    for(int mode=0;mode<2;mode++){Recipe skin=new Recipe();skin.skinTexture=mode==0?-.8f:.7f;skin.skinBrightness=.3f;skin.skinColor=.4f;skin.skinBlush=-.2f;int[] px=Developer.preview(source,skin,0,1,w,dims);for(int i=0;i<px.length;i++){expected[i*4]=(byte)(px[i]>>16);expected[i*4+1]=(byte)(px[i]>>8);expected[i*4+2]=(byte)px[i];expected[i*4+3]=(byte)255;}Files.write(dir.resolve("expected-skin-texture-"+mode+".rgba"),expected);}

  }
}
