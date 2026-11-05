package com.hoonjay.filmroom.camera;

import android.content.Context;
import android.graphics.*;
import android.opengl.*;
import android.view.*;
import com.hoonjay.filmroom.core.*;
import java.nio.*;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** OES camera preview and lightweight GPU film preview. Still files always come from RAW. */
public final class CameraPreview extends GLSurfaceView
    implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
  public interface Listener {
    void surface(SurfaceTexture texture);

    default void clipping(boolean highlights, boolean shadows) {}

    void meter(MeteringStats stats, long selection);

    void failure(String message);

    void analysis(long selection, float[] gray, int w, int h);
  }

  private final Listener listener;
  private boolean warningSample;
  private final ClippingRisk.Tracker clippingTracker = new ClippingRisk.Tracker();
  private SurfaceTexture texture;
  private int program, tex, fbo, meterTex, lutTex, toneTex;
  private volatile PreviewColorModel colorModel;
  private PreviewColorModel uploadedColor;
  private Recipe lutRecipe;
  private boolean newFrame;
  private final float[] transform = new float[16];
  private final FloatBuffer vertices;
  private volatile Recipe recipe = Recipe.builtIns().get(1);
  private Rect frame = new Rect();
  private int width, height;
  private volatile int sensorOrientation, displayRotation;
  private volatile float sourceAspect = 4 / 3f;
  private long lastMeter, lastAnalysis;
  private int analysisFbo, analysisTex;
  private volatile long analysisSelection;
  private final ByteBuffer analysisPixels = ByteBuffer.allocateDirect(256 * 256 * 4);

  public void setTrackingAnalysis(long selection) {
    analysisSelection = selection;
    requestRender();
  }

  private static final class Area {
    final float x, y;
    final boolean active;
    final long selection;

    Area(float x, float y, boolean active, long selection) {
      this.x = x;
      this.y = y;
      this.active = active;
      this.selection = selection;
    }
  }

  private volatile Area meterArea = new Area(.5f, .5f, false, 0);

  public void setMeteringPoint(float x, float y, boolean active, long selection) {
    meterArea = new Area(x, y, active, selection);
    requestRender();
  }

  private volatile float simulation = 1;
  private volatile boolean peaking;
  private volatile int peakColor = 0xffff3b30;
  private volatile float peakOpacity = .75f;
  private final ByteBuffer meterPixels = ByteBuffer.allocateDirect(32 * 32 * 4);
  private final float[] meterLuma = new float[32 * 32], meterMax = new float[32 * 32];

  public CameraPreview(Context c, Listener listener) {
    super(c);
    this.listener = listener;
    setEGLContextClientVersion(2);
    setPreserveEGLContextOnPause(true);
    setRenderer(this);
    setRenderMode(RENDERMODE_WHEN_DIRTY);
    vertices = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    vertices.put(new float[] {-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
  }

  public void setFrame(Rect r) {
    frame = new Rect(r);
    requestRender();
  }

  public void setRecipe(Recipe r) {
    recipe = r.copy();
    requestRender();
  }

  public void setColorModel(PreviewColorModel model) {
    colorModel = model;
    requestRender();
  }

  public void setLens(int orientation, int displayRotation, int w, int h) {
    this.sensorOrientation = orientation;
    this.displayRotation = displayRotation;
    int rotation = PreviewGeometry.normalize(orientation - displayRotation);
    sourceAspect = (rotation == 90 || rotation == 270) ? h / (float) w : w / (float) h;
    requestRender();
  }

  public void setPeaking(boolean enabled, int color, float opacity) {
    peaking = enabled;
    peakColor = color;
    peakOpacity = Math.max(0, Math.min(1, opacity));
    requestRender();
  }

  public void setSimulation(float f) {
    simulation = Math.max(1 / 1024f, Math.min(1024, f));
    requestRender();
  }

  public void onSurfaceCreated(GL10 gl, EGLConfig cfg) {
    try {
      int[] t = new int[1];
      GLES20.glGenTextures(1, t, 0);
      tex = t[0];
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
      GLES20.glTexParameteri(
          GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(
          GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(
          GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(
          GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
      texture = new SurfaceTexture(tex);
      texture.setDefaultBufferSize(1440, 1080);
      texture.setOnFrameAvailableListener(this);
      program = createProgram();
      lutRecipe = null;
      GLES20.glGenTextures(1, t, 0);
      lutTex = t[0];
      GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTex);
      GLES20.glTexImage2D(
          GLES20.GL_TEXTURE_2D,
          0,
          GLES20.GL_RGBA,
          1,
          1,
          0,
          GLES20.GL_RGBA,
          GLES20.GL_UNSIGNED_BYTE,
          ByteBuffer.allocateDirect(4));
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
      GLES20.glTexParameteri(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glGenTextures(1, t, 0);
      toneTex = t[0];
      uploadedColor = null;
      GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, toneTex);
      GLES20.glTexImage2D(
          GLES20.GL_TEXTURE_2D,
          0,
          GLES20.GL_RGBA,
          1,
          1,
          0,
          GLES20.GL_RGBA,
          GLES20.GL_UNSIGNED_BYTE,
          ByteBuffer.allocateDirect(4));
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
      GLES20.glTexParameteri(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(
          GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glGenFramebuffers(1, t, 0);
      fbo = t[0];
      GLES20.glGenTextures(1, t, 0);
      meterTex = t[0];
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, meterTex);
      GLES20.glTexImage2D(
          GLES20.GL_TEXTURE_2D,
          0,
          GLES20.GL_RGBA,
          32,
          32,
          0,
          GLES20.GL_RGBA,
          GLES20.GL_UNSIGNED_BYTE,
          null);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
      GLES20.glFramebufferTexture2D(
          GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, meterTex, 0);
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
      GLES20.glGenFramebuffers(1, t, 0);
      analysisFbo = t[0];
      GLES20.glGenTextures(1, t, 0);
      analysisTex = t[0];
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, analysisTex);
      GLES20.glTexImage2D(
          GLES20.GL_TEXTURE_2D,
          0,
          GLES20.GL_RGBA,
          256,
          256,
          0,
          GLES20.GL_RGBA,
          GLES20.GL_UNSIGNED_BYTE,
          null);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, analysisFbo);
      GLES20.glFramebufferTexture2D(
          GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, analysisTex, 0);
      if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE)
        throw new IllegalStateException("Analysis framebuffer unavailable");
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
      post(() -> listener.surface(texture));
    } catch (Exception e) {
      post(() -> listener.failure("필름 미리보기 시작 실패: " + e.getMessage()));
    }
  }

  public void onSurfaceChanged(GL10 gl, int w, int h) {
    width = w;
    height = h;
  }

  public synchronized void onFrameAvailable(SurfaceTexture ignored) {
    newFrame = true;
    requestRender();
  }

  public void onDrawFrame(GL10 ignored) {
    if (program == 0 || texture == null) return;
    boolean cameraFrame;
    synchronized (this) {
      cameraFrame = newFrame;
      if (newFrame) {
        texture.updateTexImage();
        texture.getTransformMatrix(transform);
        newFrame = false;
      }
    }
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    GLES20.glViewport(0, 0, width, height);
    GLES20.glClearColor(.045f, .045f, .05f, 1);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    Rect r = frame;
    if (r.width() <= 0 || r.height() <= 0) return;
    GLES20.glViewport(r.left, height - r.bottom, r.width(), r.height());
    draw(recipe, r.width() / (float) r.height(), false);
    long now = android.os.SystemClock.elapsedRealtime();
    if (now - lastMeter > 450) {
      lastMeter = now;
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
      GLES20.glViewport(0, 0, 32, 32);
      draw(recipe, r.width() / (float) r.height(), true);
      meterPixels.position(0);
      GLES20.glReadPixels(0, 0, 32, 32, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, meterPixels);
      for (int i = 0; i < 1024; i++) {
        float rr = ColorMath.toLinear((meterPixels.get(i * 4) & 255) / 255f),
            gg = ColorMath.toLinear((meterPixels.get(i * 4 + 1) & 255) / 255f),
            bb = ColorMath.toLinear((meterPixels.get(i * 4 + 2) & 255) / 255f);
        meterLuma[i] = ColorMath.luma(rr, gg, bb);
        meterMax[i] = Math.max(rr, Math.max(gg, bb));
      }
      Area area = meterArea;
      MeteringStats stats =
          area.active
              ? MeteringStats.region(meterLuma, meterMax, 32, 32, area.x, 1 - area.y)
              : MeteringStats.analyze(meterLuma, meterMax);
      post(() -> listener.meter(stats, area.selection));
      // Check the calibrated capture signal before the preset can lift black or compress white.
      warningSample = true;
      draw(recipe, r.width() / (float) r.height(), false);
      warningSample = false;
      meterPixels.position(0);
      GLES20.glReadPixels(0, 0, 32, 32, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, meterPixels);
      int high = 0, low = 0;
      for (int i = 0; i < 1024; i++) {
        int red = meterPixels.get(i * 4) & 255,
            green = meterPixels.get(i * 4 + 1) & 255,
            blue = meterPixels.get(i * 4 + 2) & 255;
        if (ClippingRisk.high(red, green, blue)) high++;
        if (ClippingRisk.low(red, green, blue)) low++;
      }
      clippingTracker.update(high, low, 1024);
      boolean bright = clippingTracker.highlights, dark = clippingTracker.shadows;
      post(() -> listener.clipping(bright, dark));

      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }
    long selection = analysisSelection;
    if (cameraFrame && selection != 0 && now - lastAnalysis >= 120) {
      lastAnalysis = now;
      float aspect = r.width() / (float) r.height();
      int aw = aspect >= 1 ? 256 : Math.max(24, Math.round(256 * aspect)),
          ah = aspect >= 1 ? Math.max(24, Math.round(256 / aspect)) : 256;
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, analysisFbo);
      GLES20.glViewport(0, 0, aw, ah);
      draw(recipe, aspect, true);
      analysisPixels.position(0);
      GLES20.glReadPixels(0, 0, aw, ah, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, analysisPixels);
      float[] gray = new float[aw * ah];
      for (int y = 0; y < ah; y++)
        for (int x = 0; x < aw; x++) {
          int i = ((ah - 1 - y) * aw + x) * 4;
          gray[y * aw + x] =
              (.2126f * (analysisPixels.get(i) & 255)
                      + .7152f * (analysisPixels.get(i + 1) & 255)
                      + .0722f * (analysisPixels.get(i + 2) & 255))
                  / 255f;
        }
      // Listener enqueues a single worker task; never invoke camera APIs on the GL thread.
      listener.analysis(selection, gray, aw, ah);
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }
  }

  private int loc(String key) {
    return GLES20.glGetUniformLocation(program, key);
  }

  private void one(String key, float v) {
    GLES20.glUniform1f(loc(key), v);
  }

  private void draw(Recipe p, float aspect, boolean meter) {
    GLES20.glUseProgram(program);
    int a = GLES20.glGetAttribLocation(program, "a");
    vertices.position(0);
    GLES20.glEnableVertexAttribArray(a);
    GLES20.glVertexAttribPointer(a, 2, GLES20.GL_FLOAT, false, 0, vertices);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
    GLES20.glUniform1i(loc("cam"), 0);
    GLES20.glUniformMatrix4fv(loc("st"), 1, false, transform, 0);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTex);
    boolean advanced = !meter && p.strength > 0 && AdvancedColor.active(p);
    if (advanced && lutRecipe != p) {
      byte[] lut = AdvancedColor.lut(p);
      ByteBuffer data = ByteBuffer.allocateDirect(lut.length);
      data.put(lut).position(0);
      GLES20.glTexImage2D(
          GLES20.GL_TEXTURE_2D,
          0,
          GLES20.GL_RGBA,
          AdvancedColor.LUT_WIDTH,
          AdvancedColor.LUT_HEIGHT,
          0,
          GLES20.GL_RGBA,
          GLES20.GL_UNSIGNED_BYTE,
          data);
      lutRecipe = p;
    }
    GLES20.glUniform1i(loc("colorLut"), 1);
    one("advanced", advanced ? 1 : 0);
    PreviewColorModel color = colorModel;
    GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, toneTex);
    if (color != null && uploadedColor != color) {
      byte[] packed = color.texture();
      ByteBuffer data = ByteBuffer.allocateDirect(packed.length);
      data.put(packed).position(0);
      GLES20.glTexImage2D(
          GLES20.GL_TEXTURE_2D,
          0,
          GLES20.GL_RGBA,
          PreviewColorModel.LUT_SIZE,
          3,
          0,
          GLES20.GL_RGBA,
          GLES20.GL_UNSIGNED_BYTE,
          data);
      uploadedColor = color;
    }
    GLES20.glUniform1i(loc("toneInverse"), 2);
    one("previewColor", !meter && color != null ? 1 : 0);
    one("previewScale", simulation);
    if (color != null)
      GLES20.glUniformMatrix3fv(loc("previewMatrix"), 1, false, color.columnMajor(), 0);
    one("skinBrightness", meter ? 0 : p.skinBrightness);
    one("skinColor", meter ? 0 : p.skinColor);
    one("skinBlush", meter ? 0 : p.skinBlush);
    one("skinTexture", meter ? 0 : p.skinTexture);
    one("detail", meter ? 0 : p.detail);
    one("noiseLuma", meter ? 0 : p.noiseLuma * p.strength);
    one("noiseChroma", meter ? 0 : p.noiseChroma * p.strength);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    one("peaking", !meter && !warningSample && peaking ? 1 : 0);
    one("zebra", !meter && !warningSample ? 1 : 0);
    one("riskSample", !meter && warningSample ? 1 : 0);
    one("peakOpacity", peakOpacity);
    int col = peakColor;
    GLES20.glUniform3f(
        loc("peakColor"),
        ((col >> 16) & 255) / 255f,
        ((col >> 8) & 255) / 255f,
        (col & 255) / 255f);
    Rect area = frame;
    GLES20.glUniform2f(
        loc("pixelStep"), 1.5f / Math.max(1, area.width()), 1.5f / Math.max(1, area.height()));
    one("rot", PreviewGeometry.additionalRotation(sensorOrientation, displayRotation, transform));
    one("srcAspect", sourceAspect);
    one("dstAspect", aspect);
    one("strength", meter ? 0 : p.strength);
    one(
        "exposure",
        meter
            ? 0
            : p.exposure + (color == null ? (float) (Math.log(simulation) / Math.log(2)) : 0));
    one("warmth", meter ? 0 : p.warmth);
    one("tint", meter ? 0 : p.tint);
    one("shadows", meter ? 0 : p.shadows);
    one("highlights", meter ? 0 : p.highlights);
    one("contrast", p.contrast);
    one("saturation", p.saturation);
    one("fade", p.fade);
    one("grain", p.grain);
    one("bloom", p.bloom);
    one("vignette", p.vignette);
    GLES20.glUniform3fv(loc("balance"), 1, p.balance, 0);
    GLES20.glUniform3fv(loc("shadowColor"), 1, p.shadowColor, 0);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
  }

  private static int shader(int type, String code) {
    int s = GLES20.glCreateShader(type);
    GLES20.glShaderSource(s, code);
    GLES20.glCompileShader(s);
    int[] ok = new int[1];
    GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
    if (ok[0] == 0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));
    return s;
  }

  private static int createProgram() {
    String v =
        "attribute vec2 a;varying vec2 uv;void main(){uv=(a+1.0)*.5;gl_Position=vec4(a,0.,1.);}";
    String f = FilmShader.fragment();
    int p = GLES20.glCreateProgram();
    GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, v));
    GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, f));
    GLES20.glLinkProgram(p);
    int[] ok = new int[1];
    GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
    if (ok[0] == 0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(p));
    return p;
  }
}
