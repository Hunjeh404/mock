import com.hoonjay.filmroom.core.*;
import java.awt.image.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class CoreTests {
  private static int checks = 0;

  static void check(boolean b, String message) {
    checks++;
    if (!b) throw new AssertionError(message);
  }

  static void near(float a, float b, float tolerance, String message) {
    check(Math.abs(a - b) < tolerance, message + " " + a + " vs " + b);
  }

  public static void main(String[] args) throws Exception {
    File dir = new File(args.length == 0 ? "build/test-output" : args[0]);
    dir.mkdirs();
    png16(dir);
    geometry();
    previewGeometry();
    cameraControls();
    colorPipeline(dir);
    bayer(dir);
    dng(dir);
    references();
    exposure();
    touchAndTransfers();
    largeOutput(dir);
    System.out.println(
        "PASS: "
            + checks
            + " assertions; RAW invariance, 16-bit PNG, CFA patterns, TIFF strips, crop/rotation,"
            + " reference analysis, >4096px export, preview transforms/layout/gestures, matched tone/boost and studio/export.");
  }

  static void cameraControls() {
    for(int[] screen:new int[][]{{320,640},{385,835},{835,385},{1024,600}})
      for(float density:new float[]{1,2,3}) {
        CameraLayout layout=new CameraLayout(Math.round(screen[0]*density),Math.round(screen[1]*density),density,0,0,0,0);
        check(layout.topBottom<=31*density,"Single compact dial row; status inside image frame");
        check(layout.format.top==layout.ratio.top&&layout.ratio.top==layout.preset.top&&layout.preset.top==layout.zoom.top,"All top dials share one row");
        check(layout.format.height()<=26*density&&layout.manual.height()<=28*density,"Minimal-height wheels");
        check(layout.format.right<layout.ratio.left,"Format and aspect wheels separated");
        check(layout.meter.right<layout.level.left && layout.level.right<layout.grid.left,"Indicator controls separated");
        check(layout.manual.bottom<=layout.shutter.top,"Exposure controls stay above shutter");
        check(!layout.shutter.contains(layout.gallery.cx(),layout.gallery.cy()),"Gallery and shutter separated");
        check(layout.topPanel.bottom<layout.bottomPanel.top,"Portrait layout remains top/bottom; no landscape reflow");
        check(layout.meter.cy()==layout.timer.cy()&&layout.level.cy()==layout.timer.cy()&&layout.grid.cy()==layout.timer.cy(),"Meter/level/grid sit beside timer");
        check(layout.meter.top>=layout.bottomTop,"Indicators removed from header");
        for(int count:new int[]{4,5,6})for(int i=0;i<count;i++) {
          CameraLayout.Box b=layout.manualSlot(i,count);
          check(b.left>=0&&b.right<=layout.width&&b.top>=0&&b.bottom<=layout.height,"Manual wheel stays inside screen");
          if(i+1<count)check(b.right<layout.manualSlot(i+1,count).left,"Manual wheels are distinct hit targets");
        }
      }
    for(int sensor:new int[]{0,90,180,270})for(int angle:new int[]{0,90,180,270}){
      CaptureOrientation orientation=new CaptureOrientation();orientation.update(angle);
      check(orientation.jpegRotation(sensor)==(sensor+angle)%360,"Saved rotation follows physical attitude independently of fixed display");
      check(PreviewGeometry.normalize(sensor-orientation.engineRotation())==orientation.jpegRotation(sensor),"CameraEngine orientation handoff matches saved pixels");
    }
    CaptureOrientation orientation=new CaptureOrientation();orientation.update(46);check(orientation.quadrant()==0,"Hysteresis retains near boundary");
    orientation.update(65);check(orientation.quadrant()==90,"Rotation commits beyond hysteresis");orientation.update(-1);check(orientation.quadrant()==90,"Unknown/flat orientation retains last attitude");
    orientation.update(40);check(orientation.quadrant()==90,"No rapid flip near diagonal");orientation.update(0);check(orientation.quadrant()==0,"Returns to portrait");
    check(Arrays.equals(NdFilter.supported(null,true),new float[]{0}),"Absent ND reports off only");
    check(!NdFilter.available(NdFilter.supported(new float[]{0,2},false)),"Unwritable ND never exposed");
    float[] nd=NdFilter.supported(new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,4,0,2,2},true);
    check(Arrays.equals(nd,new float[]{0,2,4}),"Only actual sorted unique ND stops accepted");
    near(NdFilter.step(nd,0,1),2,.0001f,"Hardware ND selects reported stop");near(NdFilter.step(nd,2,-1),0,.0001f,"Hardware ND can be switched off");
    near(NdFilter.step(nd,4,10),4,.0001f,"Hardware ND cannot exceed supported range");
    List<Recipe> all=Recipe.builtIns();List<String> order=Arrays.asList("classic-chrome","rose","deleted","rose");
    List<Recipe> ordered=PresetCatalog.ordered(all,order);
    check(ordered.size()==all.size()&&ordered.get(0).id.equals("classic-chrome")&&ordered.get(1).id.equals("rose"),"Saved order tolerates duplicates/deleted IDs and appends missing recipes");
    List<Recipe> visible=PresetCatalog.visible(all,order,new HashSet<>(Arrays.asList("classic-chrome")));
    check(visible.get(0).id.equals("rose")&&visible.size()==all.size()-1,"Hide affects presentation without deleting recipes");
    check(OutputFormat.fromId("raw")==OutputFormat.RAW_ONLY,"RAW-only format round trips");
    FloatingShutterState state=new FloatingShutterState();
    state.bounds(35,35,350,800);
    check(!state.hit(100,100,35),"Hidden clone never intercepts touches");
    state.begin(100,200);state.move(-200,1000);
    near(state.x,35,.001f,"Floating shutter clamps at safe left");near(state.y,800,.001f,"Floating shutter clamps at safe bottom");
    state.finish(190,780,30);check(state.visible&&!state.dragging,"Clone remains after drop");
    check(state.hit(35,800,35)&&!state.hit(75,760,35),"Clone uses circular priority hit area");
    state.begin(300,300);state.cancel();near(state.x,35,.001f,"Canceled move restores prior position");
    state.bounds(35,35,800,350);near(state.x,35,.001f,"Rotation preserves normalized x");near(state.y,350,.001f,"Rotation preserves normalized y");
    state.begin(200,300);state.finish(200,300,30);check(!state.visible,"Drop on original shutter removes clone");
    state.begin(100,100);state.cancel();check(!state.visible,"Canceling new clone restores hidden state");
  }

  static void colorPipeline(File dir) throws Exception {
    float[] curve = ToneMapping.srgbCurve(64);
    near(curve[0], 0, .000001f, "Tone curve starts at black");
    near(curve[curve.length-1], 1, .000001f, "Tone curve ends at white");
    for (int i = 1; i < curve.length/2; i++) {
      check(curve[i*2] > curve[(i-1)*2], "Tone inputs strictly increase");
      check(curve[i*2+1] >= curve[(i-1)*2+1], "Tone output monotonic");
    }
    near(ToneMapping.map(.25f, new float[] {0,0,.5f,.8f,1,1}), .4f, .00001f, "Camera curve linear interpolation");
    near(ToneMapping.map(5, curve), 1, .00001f, "Tone handles highlight clipping");
    near(ToneMapping.workingLinear(.1f, 2, curve), .2f, .001f, "RAW digital boost reaches matched working brightness");
    near(ToneMapping.workingLinear(.8f, 2, curve), 1.6f, .001f, "RAW over-white retained for exposure edits");
    short[] samples = new short[64*48];
    for (int y=0;y<48;y++) for (int x=0;x<64;x++)
      samples[y*64+x]=(short)(64+(x%2==0 && y%2==0 ? 700 : x%2==1 && y%2==1 ? 260 : 500));
    short[] original=samples.clone();
    RawFrame raw=new RawFrame(64,48,samples);
    raw.black=new float[] {64,64,64,64};
    raw.gains=new float[] {1.5f,1,1,1.8f};
    raw.matrix=new float[] {1.1f,-.1f,0,-.05f,1.1f,-.05f,0,-.1f,1.1f};
    raw.postRawBoost=2;
    raw.toneCurves=new float[][] {curve,curve,curve};
    Developer.Source source=Developer.raw(raw);
    for (Recipe recipe : Recipe.builtIns()) {
      float[] linear=new float[3]; source.rgb(30,24,linear);
      float[] fromPreview=new float[3];
      for(int c=0;c<3;c++) {
        float encoded=ColorMath.toSrgb(linear[c]);
        fromPreview[c]=ColorMath.toLinear(Math.round(encoded*255)/255f);
      }
      Recipe stable=recipe.copy(); stable.grain=0; stable.bloom=0;
      ColorMath.grade(linear,stable,.5f,.5f,0);
      ColorMath.grade(fromPreview,stable,.5f,.5f,0);
      for(int c=0;c<3;c++) near(linear[c],fromPreview[c],.006f,"Matched RAW and 8-bit ISP recipe input");

      ByteArrayOutputStream out=new ByteArrayOutputStream();
      Developer.render(source,recipe,90,1.5,out,null);
      BufferedImage image=ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
      int[] dim=new int[2]; int[] preview=Developer.preview(source,recipe,90,1.5,100,dim);
      check(image.getWidth()==dim[0] && image.getHeight()==dim[1],"Studio/export geometry");
      for(int y=0;y<dim[1];y++)for(int x=0;x<dim[0];x++)for(int c=0;c<3;c++) {
        int expected=Math.round(image.getRaster().getSample(x,y,c)*255/65535f);
        int actual=(preview[y*dim[0]+x]>>(16-8*c))&255;
        check(Math.abs(expected-actual)<=1,"Studio/export match including grain and bloom");
      }
    }
    check(Arrays.equals(original,samples),"Matched display processing preserves exact RAW samples");
    raw.release();
  }

  static void previewGeometry() {
    // Producer matrices include vertical flip, arbitrary 90-degree camera rotation,
    // translation and asymmetric crop. Verify composition against an independent
    // top-left sensor-coordinate oracle for all display rotations and all corners.
    for (int sensor : new int[] {0, 90, 180, 270})
      for (int embedded : new int[] {0, 90, 180, 270})
        for (int display : new int[] {0, 90, 180, 270}) {
          float[] st = new float[16];
          double angle = Math.toRadians(embedded);
          float co = Math.round(Math.cos(angle)), si = Math.round(Math.sin(angle));
          st[0] = .96f * co; st[4] = -.96f * si;
          st[1] = -.92f * si; st[5] = -.92f * co;
          st[12] = .5f - .5f * (st[0] + st[4]);
          st[13] = .5f - .5f * (st[1] + st[5]);
          check(PreviewGeometry.textureRotation(st) == embedded, "Producer rotation detection");
          int extra = PreviewGeometry.additionalRotation(sensor, display, st);
          for (float[] point : new float[][] {{0,0}, {1,0}, {0,1}, {1,1}, {.2f,.7f}}) {
            float[] uv = rotateUv(point[0], 1 - point[1], extra);
            float actualX = st[0] * uv[0] + st[4] * uv[1] + st[12];
            float actualY = st[1] * uv[0] + st[5] * uv[1] + st[13];
            float x = point[0], y = point[1], expectedX = x, expectedY = y;
            switch (PreviewGeometry.normalize(sensor - display)) {
              case 90: expectedX = y; expectedY = 1 - x; break;
              case 180: expectedX = 1 - x; expectedY = 1 - y; break;
              case 270: expectedX = 1 - y; expectedY = x; break;
            }
            near(actualX, .02f + .96f * expectedX, .00001f, "Preview/focus X agreement");
            near(actualY, .04f + .92f * expectedY, .00001f, "Preview/focus Y agreement");
          }
        }
    for (int[] screen : new int[][] {{1536,709}, {709,1536}, {2400,1080}, {1080,2400}})
      for (double ratio : new double[] {1, 4/3., 1.5, 16/9., 2}) {
        int[] b = PreviewGeometry.fit(screen[0], screen[1], ratio);
        int w = b[2] - b[0], h = b[3] - b[1];
        check(b[0] >= 0 && b[1] >= 0 && b[2] <= screen[0] && b[3] <= screen[1], "Frame fits screen");
        check(w == screen[0] || h == screen[1], "Frame reaches at least one full dimension");
        near(w/(float)h, (float)(screen[0]>screen[1] ? ratio : 1/ratio), .003f, "No aspect distortion");
      }
    check(PreviewGeometry.fit(1536,709,1.5)[3] == 709, "Reported landscape fills full height");
    check(PreviewGeometry.filmSwipe(0,-70)==1, "Upward swipe opens in either orientation");
    check(PreviewGeometry.filmSwipe(0,70)==-1, "Downward swipe closes even after scrolling films");
    check(PreviewGeometry.filmSwipe(-90,3)==0, "Left swipe scrolls, never opens/closes films");
    check(PreviewGeometry.filmSwipe(90,3)==0, "Right swipe scrolls, never opens/closes films");
    check(PreviewGeometry.filmSwipe(15,-20)==0, "Short drag does not open films");
    check(PreviewGeometry.filmSwipe(80,-50)==0, "Horizontal-dominant diagonal stays a scroll");

  }

  static float[] rotateUv(float x, float y, int rotation) {
    switch (rotation) {
      case 90: return new float[] {1-y,x};
      case 180: return new float[] {1-x,1-y};
      case 270: return new float[] {y,1-x};
      default: return new float[] {x,y};
    }
  }

  static void png16(File dir) throws Exception {
    File f = new File(dir, "precision.png");
    float[] row = {0, 1 / 65535f, 32768 / 65535f, 1, .23456f, .9999f};
    try (Png16Writer p = new Png16Writer(new FileOutputStream(f), 2, 1)) {
      p.row(row);
    }
    BufferedImage b = ImageIO.read(f);
    check(
        b.getRaster().getDataBuffer().getDataType() == DataBuffer.TYPE_USHORT,
        "PNG must use 16-bit samples");
    for (int x = 0; x < 2; x++)
      for (int c = 0; c < 3; c++)
        check(
            Math.abs(b.getRaster().getSample(x, 0, c) - Math.round(row[x * 3 + c] * 65535)) <= 1,
            "PNG sample must survive losslessly");
    byte[] bytes = Files.readAllBytes(f.toPath());
    check(
        new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains("sRGB"),
        "PNG must declare color profile");
  }

  static void geometry() {
    for (int r : new int[] {0, 90, 180, 270})
      for (double a : new double[] {1, 1.5, 16 / 9., 2}) {
        Geometry g = new Geometry(120, 80, r, a);
        double target = g.orientedWidth >= g.orientedHeight ? a : 1 / a;
        check(Math.abs(g.width / (double) g.height - target) < .04, "Crop ratio must match frame");
        Set<Integer> pixels = new HashSet<>();
        for (int y = 0; y < g.height; y++)
          for (int x = 0; x < g.width; x++) {
            int sx = g.sourceX(x, y), sy = g.sourceY(x, y);
            check(sx >= 0 && sx < 120 && sy >= 0 && sy < 80, "Rotation maps inside sensor");
            check(pixels.add(sy * 120 + sx), "No duplicated or rescaled pixels");
          }
      }
    Geometry g = new Geometry(6, 4, 90, 1.5);
    check(g.width == 4 && g.height == 6, "Portrait rotation keeps native resolution");
    check(g.sourceX(0, 0) == 0 && g.sourceY(0, 0) == 3, "90-degree origin");
  }

  static void bayer(File dir) throws Exception {
    int[][] cfas = {{0, 1, 2, 3}, {1, 0, 3, 2}, {1, 3, 0, 2}, {3, 1, 2, 0}};
    for (int n = 0; n < 4; n++) {
      short[] data = new short[64 * 48];
      float[] channels = {.35f, .2f, .2f, .08f};
      for (int y = 0; y < 48; y++)
        for (int x = 0; x < 64; x++)
          data[y * 64 + x] =
              (short) (64 + Math.round(channels[cfas[n][(y % 2) * 2 + x % 2]] * (4095 - 64)));
      short[] before = data.clone();
      RawFrame r = new RawFrame(64, 48, data);
      r.cfa = cfas[n];
      r.black = new float[] {64, 64, 64, 64};
      r.gains = new float[] {1, 1, 1, 1};
      r.prepare();
      float[] rgb = new float[3];
      for (int y : new int[] {0, 1, 23, 46, 47})
        for (int x : new int[] {0, 1, 31, 62, 63}) {
          r.rgb(x, y, rgb);
          near(rgb[0], .35f, .002f, "Bayer red");
          near(rgb[1], .2f, .002f, "Bayer green");
          near(rgb[2], .08f, .002f, "Bayer blue");
        }
      Recipe p = Recipe.builtIns().get(9);
      try (OutputStream out = new FileOutputStream(new File(dir, "raw-pattern-" + n + ".png"))) {
        Developer.render(Developer.raw(r), p, 90, 1.5, out, null);
      }
      check(Arrays.equals(before, data), "Developing must never mutate the RAW samples");
      BufferedImage b = ImageIO.read(new File(dir, "raw-pattern-" + n + ".png"));
      check(b.getWidth() == 42 && b.getHeight() == 63, "Press portrait output must be exact 2:3, without rescaling");
      r.release();
    }
  }

  static File fixture(File dir, int compression) throws Exception {
    int w = 8,
        h = 6,
        count = 9,
        ifdOffset = 8,
        extra = 8 + 2 + count * 12 + 4,
        offsetTable = extra,
        countTable = extra + 8,
        strip0 = extra + 16,
        strip1 = strip0 + w * 3 * 2;
    ByteBuffer b = ByteBuffer.allocate(strip1 + w * 3 * 2).order(ByteOrder.LITTLE_ENDIAN);
    b.put((byte) 'I')
        .put((byte) 'I')
        .putShort((short) 42)
        .putInt(ifdOffset)
        .putShort((short) count);
    tag(b, 256, 4, 1, w);
    tag(b, 257, 4, 1, h);
    tag(b, 258, 3, 1, 16);
    tag(b, 259, 3, 1, compression);
    tag(b, 262, 3, 1, 32803);
    tag(b, 273, 4, 2, offsetTable);
    tag(b, 278, 4, 1, 3);
    tag(b, 279, 4, 2, countTable);
    tag(b, 33422, 1, 4, 0x02010100);
    b.putInt(0);
    b.position(offsetTable);
    b.putInt(strip0).putInt(strip1).putInt(w * 3 * 2).putInt(w * 3 * 2);
    b.position(strip0);
    for (int i = 0; i < w * h; i++) b.putShort((short) (i * 97));
    File f = new File(dir, "fixture-" + compression + ".dng");
    Files.write(f.toPath(), b.array());
    return f;
  }

  static void tag(ByteBuffer b, int id, int type, int n, int val) {
    b.putShort((short) id).putShort((short) type).putInt(n).putInt(val);
  }

  static void dng(File dir) throws Exception {
    RawFrame r = DngReader.read(fixture(dir, 1));
    check(r.width == 8 && r.height == 6, "TIFF dimensions");
    for (int i = 0; i < r.pixels.length; i++)
      check((r.pixels[i] & 65535) == i * 97, "RAW strips preserve every 16-bit sample");
    boolean rejected = false;
    try {
      DngReader.read(fixture(dir, 7));
    } catch (IOException e) {
      rejected = true;
    }
    check(rejected, "Unsupported DNG must not use an embedded JPEG fallback");
  }

  static void references() {
    int[] gray = new int[1024], warm = new int[1024], cool = new int[1024];
    for (int i = 0; i < 1024; i++) {
      int v = 20 + i % 220;
      gray[i] = 0xff000000 | (v << 16) | (v << 8) | v;
      warm[i] = 0xff000000 | (Math.min(255, v + 25) << 16) | (v << 8) | Math.max(0, v - 15);
      cool[i] = 0xff000000 | (Math.max(0, v - 15) << 16) | (v << 8) | Math.min(255, v + 25);
    }
    ReferenceAnalyzer.Result a = ReferenceAnalyzer.analyze(Arrays.asList(warm, gray, cool), 0),
        b = ReferenceAnalyzer.analyze(Arrays.asList(cool, gray, warm), 2);
    near(
        a.recipe.warmth,
        b.recipe.warmth,
        .001f,
        "Reference ordering must not change same representative result");
    ReferenceAnalyzer.Result c = ReferenceAnalyzer.analyze(Arrays.asList(warm, gray, cool), 2);
    check(a.recipe.warmth > c.recipe.warmth, "Representative must influence estimate");
    check(
        a.note.contains("参照") == false && a.note.contains("3장"),
        "Analysis must identify reference count");
  }

  static void exposure() {
    near(ExposureMath.previewGain(100,200000000L,100,200000000L),1,.00001f,"Uncapped auto preview is not boosted twice");
    near(ExposureMath.previewGain(200,100000000L,200,10000000000L),100,.00001f,"10s photo preview is not limited to 16x");
    near(ExposureMath.previewGain(400,100000000L,200,1000000000L),5,.00001f,"Preview uses actual ISO and shutter together");
    float[] uniform = new float[32*32]; Arrays.fill(uniform,.08f);
    near(ExposureMath.sceneMean(uniform,32,32),.08f,.00001f,"Meter retains uniform scene luminance");
    float[] brightEdge = new float[32*32], brightCenter = new float[32*32];
    Arrays.fill(brightEdge,.05f); Arrays.fill(brightCenter,.05f);
    for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
      brightEdge[y*32+x]=1;
      brightCenter[(y+12)*32+x+12]=1;
    }
    near(ExposureMath.sceneMean(brightEdge,32,32),ExposureMath.sceneMean(brightCenter,32,32),.000001f,"Whole-frame metering weights edges and center equally");
    MeteringStats dark=flatStats(.045f);
    int correction=ExposureMath.autoCorrection(dark,0,1/3f,0,-6,6);
    check(correction==2,"Dark scene increases real compensation gradually");
    for(int i=0;i<10;i++)correction=ExposureMath.autoCorrection(dark,0,1/3f,correction,-6,6);
    check(correction==6,"Automatic correction capped at +2EV");
    check(ExposureMath.autoCorrection(flatStats(.48f),0,1/3f,3,-6,6)==3,"Diffuse white target stops correction");
    check(ExposureMath.autoCorrection(flatStats(.96f),3,1/3f,2,-6,6)==2,"User +1EV is retained");
    check(ExposureMath.autoCorrection(flatStats(.24f),-3,1/3f,2,-6,6)==2,"User -1EV is retained");
    check(ExposureMath.autoCorrection(dark,5,1/3f,0,-6,6)==1,"Automatic bias respects remaining hardware range");
    check(ExposureMath.autoCorrection(flatStats(.46f),0,1/3f,0,-6,6)==0,"Small errors don't hunt");
    check(ExposureMath.autoCorrection(flatStats(.7f),0,1/3f,0,-6,6)==0,"Never auto-darken below native baseline");
    check(ExposureMath.autoCorrection(flatStats(.7f),0,1/3f,4,-6,6)==2,"Withdraw boost when lighting brightens");
    check(ExposureMath.autoCorrection(MeteringStats.analyze(new float[]{Float.NaN},null),0,1/3f,2,-6,6)==2,"Invalid statistics ignored");
    check(ExposureMath.autoCorrection(dark,0,Float.NaN,2,-6,6)==2,"Invalid hardware EV step ignored");
    float[] wall=new float[1000],channel=new float[1000];
    for(int i=0;i<wall.length;i++)wall[i]=i<200?.08f:i<600?.22f:i<900?.26f:i<990?.44f:1;
    for(int i=0;i<channel.length;i++)channel[i]=wall[i];
    MeteringStats scene=MeteringStats.analyze(wall,channel);
    check(scene.mean>.18f,"Regression wall scene would not trigger old average-only guard");
    check(ExposureMath.brightnessError(scene,0)>.8,"Gray-looking wall still receives positive correction");
    int simulated=0;
    for(int i=0;i<12;i++)simulated=ExposureMath.autoCorrection(scaledStats(wall,channel,simulated/3f),0,1/3f,simulated,-6,6);
    check(simulated==3,"Wall plus small lamps converges at +1EV in this model");
    for(int i=0;i<6;i++)check(ExposureMath.autoCorrection(scaledStats(wall,channel,simulated/3f),0,1/3f,simulated,-6,6)==simulated,"Converged scene doesn't oscillate");
    check(ExposureMath.autoCorrection(scaledStats(wall,channel,(simulated-3)/3f),-3,1/3f,simulated,-6,6)==simulated,"Explicit negative EV does not get canceled by automatic profile");
    for(int i=0;i<1000;i++)channel[i]=i<970?wall[i]:1;
    check(ExposureMath.autoCorrection(MeteringStats.analyze(wall,channel),0,1/3f,0,-6,6)==0,"Broad clipped highlights prevent automatic brightening");
    Arrays.fill(channel,.98f);
    check(ExposureMath.autoCorrection(MeteringStats.analyze(wall,channel),0,1/3f,0,-6,6)==0,"Strong single-channel highlights protected even when luma is low");
    near(MeteringStats.analyze(brightEdge,null).upper,MeteringStats.analyze(brightCenter,null).upper,.000001f,"All spatial positions have equal histogram influence");
    MeteringStats dirty=MeteringStats.analyze(new float[]{.2f,Float.NaN,-1,.6f},null);
    check(dirty.samples==2,"Invalid pixels excluded");near(dirty.median,.4f,.0001f,"Quantiles interpolate valid pixels");
    check(!MeteringStats.analyze(new float[1],new float[2]).valid(),"Mismatched channel arrays rejected");
    double energy=ExposureMath.targetEnergy(100,10000000L,flatStats(.24f),0);
    check(Math.abs(energy/100-20000000)<10,"Mixed AUTO plans real energy from histogram target");
    double lowLight=ExposureMath.targetEnergy(800,100000000L,flatStats(.048f),0);
    check(Math.abs(lowLight/800-1000000000L)<1000,"Preview cap doesn't cap planned still exposure");
    check(Math.abs(ExposureMath.meter(.018f,800,100000000L,800,1000000000L))<.001,"Approximate gray-reference meter compensates preview duration");
    check(Math.abs(ExposureMath.targetEnergy(100,10000000L,flatStats(.48f),1)/10000000L-200)<.001,"User +1EV doubles mixed-AUTO target energy");
    AeConvergence gate=new AeConvergence();
    gate.observe(100,10000000L,0);gate.observe(100,10000000L,300);gate.observe(100,10000000L,600);gate.observe(100,10000000L,900);
    check(gate.ready(false,true,900,0),"Missing AE state may use sustained stable physical exposure");
    check(!gate.ready(false,false,900,0),"Explicit searching is not overridden by missing-state fallback");
    check(!gate.ready(true,false,900,100),"Converged state still waits after a request change");
    gate.observe(200,10000000L,1000);check(!gate.ready(false,true,1100,0),"Changing ISO resets stability interval");
    gate.reset();check(!gate.ready(false,true,5000,0),"Lens/session reset invalidates stale stability");
    for(int i=0;i<10;i++)gate.observe(100+i*4,10000000L,i*150L);
    check(!gate.ready(false,true,1400,0),"Slow cumulative AE drift is not mistaken for convergence");
    for(int direction:new int[]{0,90,180,270}){
      FrameLabel label=FrameLabel.of(0,80,400,680,direction,12);
      check(PreviewGeometry.normalize(Math.round(label.rotation)+direction)==0,"Frame label remains upright for physical handset attitude");
      double angle=Math.toRadians(direction),dx=label.x-200,dy=label.y-380;
      double userX=dx*Math.cos(angle)-dy*Math.sin(angle),userY=dx*Math.sin(angle)+dy*Math.cos(angle);
      near((float)userX,0,.0001f,"Frame label is centered for user");check(userY<0,"Frame label stays on user upper frame edge");
    }
  }
  static MeteringStats flatStats(float value){float[] a=new float[1000];Arrays.fill(a,value);return MeteringStats.analyze(a,null);}
  static MeteringStats scaledStats(float[] y,float[] h,float stops){
    float[] a=y.clone(),b=h.clone();float gain=(float)Math.pow(2,stops);
    for(int i=0;i<a.length;i++){a[i]=Math.min(1,a[i]*gain);b[i]=Math.min(1,b[i]*gain);}return MeteringStats.analyze(a,b);
  }

  static void touchAndTransfers(){
    check(OutputFormat.captureOptions().length==3&&!Arrays.asList(OutputFormat.captureOptions()).contains(OutputFormat.JPEG_SMALL),"Capture offers only JPG/PNG/RAW");
    check(OutputFormat.forCapture("jpeg_10mb")==OutputFormat.JPEG_MAX,"Legacy capture preference migrates to JPG");
    check(OutputFormat.JPEG_MAX.label.equals("JPG"),"JPG no longer has a maximum-quality suffix");
    float[] image=new float[32*32];Arrays.fill(image,.6f);for(int y=22;y<29;y++)for(int x=2;x<9;x++)image[y*32+x]=.06f;
    MeteringStats local=MeteringStats.region(image,null,32,32,.16f,.8f);
    near(local.mean,.06f,.00001f,"Selected patch is metered instead of full-frame brightness");
    check(MeteringStats.analyze(image,null).mean>.5f,"Test patch sits inside bright full-frame scene");
    check(MeteringStats.region(image,null,32,32,0,0).samples>0,"Touch patch clamps at image edge");
    near((float)(ExposureMath.pointEnergy(100,10000000,local,0)/100),30000000,10,"Point exposure plans selected gray reference");
    for(int fixedIso:new int[]{0,200})for(long fixedTime:new long[]{0,10000000L}){
      TouchExposure adjusted=TouchExposure.adjust(fixedIso,fixedTime,100,20000000L,1,50,6400,100000L,30000000000L);
      if(fixedIso>0)check(adjusted.iso==fixedIso,"Brightness preserves manual ISO");
      if(fixedTime>0)check(adjusted.time==fixedTime,"Brightness preserves manual shutter");
      if(fixedIso==0||fixedTime==0){double before=(double)(fixedIso>0?fixedIso:100)*(fixedTime>0?fixedTime:20000000L);
        near((float)((double)adjusted.iso*adjusted.time/before),2,.001f,"Only AUTO energy changes on explicit +1EV");}
    }
    TouchExposure capped=TouchExposure.adjust(0,0,6400,10000000L,1,50,6400,100000L,30000000000L);
    check(capped.iso==6400&&capped.time==20000000L,"AUTO shutter takes remaining gain at ISO limit");
    TouchMotion movement=new TouchMotion();movement.select(false);boolean reset=false;
    for(int i=1;i<=4;i++)reset|=movement.rotation(1,0,0,i*50000000L);
    check(reset&&!movement.active(),"Temporary point resets after enough rotation");
    movement.select(true);for(int i=1;i<=20;i++)check(!movement.rotation(2,1,1,i*100000000L),"Pinned point ignores rotation");
    check(!movement.acceleration(4,0,0,1000000000L)&&!movement.acceleration(4,0,0,2000000000L),"Pinned point ignores translation cues");
    movement.select(false);check(!movement.acceleration(2,0,0,1000000000L),"Brief acceleration doesn't reset");
    check(movement.acceleration(2,0,0,1200000000L),"Sustained acceleration resets temporary point");
    movement.select(false);for(int i=1;i<=10;i++)check(!movement.rotation(.01f,0,0,i*50000000L),"Small hand tremor retained");
    for(Recipe r:Recipe.builtIns())PresetRules.validate(r);
    Recipe incoming=Recipe.builtIns().get(1);String originalId=incoming.id;
    List<Recipe> imported=PresetRules.imported(Arrays.asList(incoming,incoming),Recipe.builtIns());
    check(imported.get(0).custom&&imported.get(1).custom&&!imported.get(0).id.equals(originalId)&&!imported.get(0).id.equals(imported.get(1).id),"Imports receive distinct custom identities without overwrite");
    check(!imported.get(0).name.equals(incoming.name)&&!imported.get(0).name.equals(imported.get(1).name),"Colliding import names become distinguishable");
    check(incoming.id.equals(originalId)&&!incoming.custom,"Import preparation does not mutate source/built-ins");
    Recipe bad=incoming.copy();bad.contrast=Float.NaN;boolean rejected=false;
    try{PresetRules.imported(Arrays.asList(incoming,bad),Recipe.builtIns());}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Entire import rejects invalid values before a write");
    bad=incoming.copy();bad.balance=new float[2];rejected=false;try{PresetRules.validate(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Malformed color arrays rejected");
  }

  static void largeOutput(File dir) throws Exception {
    Developer.Source s =
        new Developer.Source() {
          public int width() {
            return 5002;
          }

          public int height() {
            return 2501;
          }

          public void rgb(int x, int y, float[] out) {
            out[0] = x / 5002f * .4f;
            out[1] = y / 2501f * .3f;
            out[2] = .12f;
          }
        };
    File f = new File(dir, "native-large.png");
    try (OutputStream out = new FileOutputStream(f)) {
      Developer.render(s, new Recipe(), 0, 2, out, null);
    }
    BufferedImage b = ImageIO.read(f);
    check(
        b.getWidth() == 5002 && b.getHeight() == 2501, "Native output must not be capped to 4096");
    check(
        b.getRaster().getDataBuffer().getDataType() == DataBuffer.TYPE_USHORT,
        "Large render must also retain 16-bit output");
  }
}
