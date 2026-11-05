package com.hoonjay.filmroom.ui;

import android.animation.ValueAnimator;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import com.hoonjay.filmroom.camera.CameraEngine;
import com.hoonjay.filmroom.core.*;
import com.hoonjay.filmroom.guides.*;
import java.util.*;

/** Press camera HUD. Manual controls exist only in Pro; guides are drawn only on screen. */
public final class CameraOverlay extends View {
  public interface Listener {
    void shutter();

    default void openGuides() {}

    default void flashOptions() {}

    void gallery();

    void preset(String id);

    void createPreset();

    void managePresets();

    void focusOptions();

    void openModePicker();

    void mode(String mode);

    void lens(CameraEngine.Lens lens);

    void manual(String parameter, int steps);

    void dial(String parameter, int steps);

    void toggle(String parameter);

    void floating(int phase, float x, float y);

    void help(String mode);

    void focus(float x, float y, boolean lock);

    void brightness(float delta);

    void frame(Rect frame);
  }

  private Listener listener;
  private final Paint p = new Paint(3);
  private final float density;
  private final Map<String, RectF> hits = new LinkedHashMap<>();
  private final Map<String, Float> wheelPhases = new HashMap<>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Rect picture = new Rect();
  private Rect lastFrame = new Rect();
  public List<Recipe> recipes = Recipe.builtIns();
  public final Map<String, Bitmap> thumbs = new HashMap<>();
  public List<CameraEngine.Lens> lenses = new ArrayList<>();
  public CameraEngine.Lens active;
  public Recipe recipe = recipes.get(1);
  public OutputFormat outputFormat = OutputFormat.JPEG_MAX;
  public GuideCatalog guideCatalog;
  public GuideCatalog.Selection guideSelection;
  public GuideRenderer guideRenderer;
  public String mode = "자동", status = "카메라 준비 중", error = "";
  public boolean flashAvailable = false, flashOn = false;
  public boolean grid = false,
      level = false,
      meterOn = false,
      capturing = false,
      locked = false,
      portrait = false,
      pointActive = false,
      lockPending = false;
  public double ratio = 1.5;
  public int safeLeft = 0,
      safeTop = 0,
      safeRight = 0,
      safeBottom = 0,
      iso = 0,
      ev = 0,
      currentIso = 100,
      countdown = 0,
      jobs = 0;
  public int aperture = -1, timerSeconds = 0, pendingLens = -1, deviceOrientation = 0;
  public long shutter = 0, currentShutter = 10000000;
  public float focusPosition = -1, ndStops = 0;
  public boolean tracking, trackingLost, trackingPending, trackingFollowExposure;
  public float trackingX = .5f, trackingY = .5f, trackingRx = .1f, trackingRy = .1f;
  public float actualF = 1.8f, meterEV = 0, roll = 0, evStep = 1 / 3f;
  public Bitmap thumbnail;
  private boolean moved = false, longPressed = false, floatingDrag = false;
  private float downX, downY, lastX, lastY, wheelTravel, focusX = -1, focusY = -1;
  private String downHit;
  private long focusTime;
  private static final int ACCENT = 0xfff5a36b, TEXT = 0xffeee9df, MUTED = 0xffaaa39a;
  private final Runnable hold =
      () -> {
        if (moved) return;
        if ("shutter".equals(downHit)) {
          longPressed = true;
          performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } else if ("toggle:flash".equals(downHit)) {
          longPressed = true;
          listener.flashOptions();
        } else if ("wheel:focus".equals(downHit)) {
          longPressed = true;
          listener.focusOptions();
        } else if ("mode".equals(downHit)) {
          longPressed = true;
          listener.help(mode);
        } else if (downHit == null && inPreview(downX, downY)) {
          longPressed = true;
          pointActive = true;
          lockPending = true;
          focusX = downX;
          focusY = downY;
          focusTime = SystemClock.elapsedRealtime();
          downHit = null;
          listener.focus(
              (focusX - picture.left) / picture.width(),
              (focusY - picture.top) / picture.height(),
              true);
          performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
          invalidate();
        }
      };

  public CameraOverlay(Context c, Listener l) {
    super(c);
    listener = l;
    density = getResources().getDisplayMetrics().density;
    setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    setFocusable(true);
    setContentDescription(
        "보도사진 카메라. 자동·프로 모드. RAW와 JPG 동시 저장, 3대2 고정. 가이드 버튼에서 분류와 키워드로 구도를 선택합니다. 프로 모드에서만 촬영 다이얼을"
            + " 표시합니다.");
  }

  public CameraLayout controls() {
    return new CameraLayout(
        getWidth(), getHeight(), density, safeLeft, safeTop, safeRight, safeBottom);
  }

  private float d(float x) {
    return x * density;
  }

  private RectF rect(CameraLayout.Box b) {
    return new RectF(b.left, b.top, b.right, b.bottom);
  }

  private void color(int c) {
    p.setShader(null);
    p.clearShadowLayer();
    p.setColor(c);
    p.setStyle(Paint.Style.FILL);
    p.setStrokeWidth(d(1));
  }

  private void text(
      Canvas c, String value, float x, float y, float size, int col, boolean centered) {
    color(col);
    p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    p.setTextSize(d(size));
    p.setTextAlign(centered ? Paint.Align.CENTER : Paint.Align.LEFT);
    c.drawText(value, x, y, p);
  }

  private void round(Canvas c, RectF box, int col, float radius) {
    color(col);
    c.drawRoundRect(box, d(radius), d(radius), p);
  }

  private void hit(String key, float x, float y, float w, float h) {
    hits.put(key, new RectF(x, y, x + w, y + h));
  }

  private void hit(String key, CameraLayout.Box box) {
    hits.put(key, rect(box));
  }

  private boolean inPreview(float x, float y) {
    CameraLayout l = controls();
    return picture.contains((int) x, (int) y) && !l.inPanels(x, y);
  }

  /** Horizontal knurled roller. Text is printed directly on the metal, with no text box. */
  private void wheel(
      Canvas c,
      String key,
      CameraLayout.Box b,
      String label,
      String value,
      String[] neighbors,
      boolean enabled,
      boolean compact) {
    RectF box = rect(b);
    round(c, box, 0xff09090b, 5);
    RectF face = new RectF(box.left + d(2), box.top + d(2), box.right - d(2), box.bottom - d(2));
    color(0xffffffff);
    p.setShader(
        new LinearGradient(
            face.left,
            0,
            face.right,
            0,
            new int[] {0xff111113, 0xff3b3636, 0xff514844, 0xff373133, 0xff101012},
            new float[] {0, .18f, .5f, .82f, 1},
            Shader.TileMode.CLAMP));
    c.drawRoundRect(face, d(4), d(4), p);
    p.setShader(null);
    c.save();
    c.clipRect(face);
    float phase = wheelPhases.getOrDefault(key, 0f);
    for (int i = -12; i <= 12; i++) {
      double angle = (i + phase) * Math.PI / 24;
      float x = face.centerX() + (float) Math.sin(angle) * face.width() * .5f;
      color(i % 2 == 0 ? 0x559f9185 : 0x77000000);
      p.setStrokeWidth(d(.65f));
      c.drawLine(x, face.top + d(1), x, face.bottom - d(1), p);
    }
    float cy = box.top + box.height() * .66f;
    float size = compact ? 9 : 11;
    color(enabled ? ACCENT : 0xff676266);
    p.setTextSize(d(size));
    while (p.measureText(value) > box.width() - d(12) && size > 7) {
      size -= .5f;
      p.setTextSize(d(size));
    }
    p.setShadowLayer(d(2), 0, d(1), 0xee090809);
    p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    p.setTextAlign(Paint.Align.CENTER);
    c.drawText(value, box.centerX(), cy + d(2), p);
    p.clearShadowLayer();
    text(
        c,
        label,
        box.centerX(),
        box.top + Math.min(d(8), box.height() * .28f),
        6,
        enabled ? TEXT : 0xff827977,
        true);
    color(enabled ? ACCENT : 0xff494246);
    p.setStrokeWidth(d(1.6f));
    c.drawLine(box.centerX() - d(4), box.bottom - d(2), box.centerX() + d(4), box.bottom - d(2), p);
    color(0xff726059);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(d(.6f));
    c.drawRoundRect(face, d(4), d(4), p);
    p.setStyle(Paint.Style.FILL);
    c.restore();
    // Focus appearance remains available on fixed-focus lenses via a long press.
    if (enabled || key.equals("focus")) hit("wheel:" + key, b);
  }

  private void tool(Canvas c, String key, CameraLayout.Box b, boolean on, String label) {
    RectF box = rect(b);
    round(c, box, on ? 0xff2e241e : 0xff09090b, 6);
    color(on ? ACCENT : 0xff474247);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(d(1));
    c.drawRoundRect(box, d(6), d(6), p);
    float cx = b.cx(), y = b.top + d(11), s = d(6);
    if (key.equals("grid")) {
      for (int i = -1; i <= 1; i++) {
        float off = i * s * .65f;
        c.drawLine(cx - s, y + off, cx + s, y + off, p);
        c.drawLine(cx + off, y - s, cx + off, y + s, p);
      }
    } else if (key.equals("level")) {
      c.drawRoundRect(new RectF(cx - s - d(2), y - d(3), cx + s + d(2), y + d(3)), d(2), d(2), p);
      c.drawCircle(cx, y, d(1.5f), p);
    } else {
      c.drawLine(cx - s, y + d(3), cx + s, y + d(3), p);
      c.drawLine(cx, y - d(5), cx + d(3), y, p);
      c.drawArc(new RectF(cx - s, y - s, cx + s, y + s), 180, 180, false, p);
    }
    p.setStyle(Paint.Style.FILL);
    text(c, label, cx, b.bottom - d(4), 7, on ? ACCENT : 0xff625a60, true);
    color(on ? 0xffffbf87 : 0xff151315);
    c.drawCircle(b.right - d(4), b.top + d(4), d(1.5f), p);
    hit("toggle:" + key, b);
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    int w = getWidth(), h = getHeight();
    if (w <= 0 || h <= 0) return;
    CameraLayout l = controls();
    hits.clear();
    int[] bounds = PreviewGeometry.fit(w, h, ratio);
    picture.set(bounds[0], bounds[1], bounds[2], bounds[3]);
    if (!picture.equals(lastFrame)) {
      lastFrame = new Rect(picture);
      listener.frame(new Rect(picture));
    }
    color(0xff101012);
    c.drawRect(0, 0, w, picture.top, p);
    c.drawRect(0, picture.bottom, w, h, p);
    c.drawRect(0, picture.top, picture.left, picture.bottom, p);
    c.drawRect(picture.right, picture.top, w, picture.bottom, p);
    if (guideRenderer != null && guideSelection != null)
      guideRenderer.draw(c, new RectF(picture), guideSelection, deviceOrientation, false);
    if (grid) {
      color(0x88ffffff);
      p.setStrokeWidth(d(.5f));
      for (int i = 1; i < 3; i++) {
        float x = picture.left + picture.width() * i / 3f,
            y = picture.top + picture.height() * i / 3f;
        c.drawLine(x, picture.top, x, picture.bottom, p);
        c.drawLine(picture.left, y, picture.right, y, p);
      }
    }
    if (level) {
      float y = picture.centerY();
      color(
          Math.min(Math.abs(roll) % 90, 90 - Math.abs(roll) % 90) < 1.5 ? 0xffbce19e : 0xccffffff);
      p.setStrokeWidth(d(1.8f));
      c.drawLine(picture.centerX() - d(30), y, picture.centerX() - d(9), y, p);
      c.drawLine(picture.centerX() + d(9), y, picture.centerX() + d(30), y, p);
      c.save();
      c.rotate(-roll, picture.centerX(), y);
      c.drawLine(picture.centerX() - d(18), y, picture.centerX() + d(18), y, p);
      c.restore();
    }
    if (meterOn) {
      float y =
          l.landscape
              ? picture.bottom - d(12)
              : Math.min(picture.bottom - d(12), l.bottomTop - d(12));
      text(
          c,
          "−  ·  ·  0  ·  ·  +    ≈ " + String.format(Locale.US, "%+.1f", meterEV),
          picture.centerX(),
          y,
          10,
          TEXT,
          true);
    }
    if (!tracking
        && focusX >= 0
        && (pointActive || locked || SystemClock.elapsedRealtime() - focusTime < 2200)) {
      float z = d(22);
      color(ACCENT);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(d(1.4f));
      c.drawRect(focusX - z, focusY - z, focusX + z, focusY + z, p);
      p.setStyle(Paint.Style.FILL);
      if (locked || lockPending)
        text(c, lockPending ? "고정 중…" : "초점·노출 고정", focusX, focusY - z - d(8), 9, ACCENT, true);
    }
    color(0xf0141315);
    c.drawRect(l.topPanel.left, l.topPanel.top, l.topPanel.right, l.topPanel.bottom, p);

    round(c, rect(l.mode), 0xff30251f, 5);
    text(c, mode, l.mode.cx(), l.mode.cy() + d(3), 9, ACCENT, true);
    hit("mode", l.mode);
    round(c, rect(l.format), 0xff211f21, 5);
    text(c, "RAW+JPG", l.format.cx(), l.format.cy() + d(3), 9, TEXT, true);
    text(c, "3:2", l.ratio.cx(), l.ratio.cy() + d(3), 9, MUTED, true);
    RectF guideButton = new RectF(l.preset.left, l.preset.top, l.manage.right, l.preset.bottom);
    round(c, guideButton, 0xff30251f, 5);
    String guideName =
        guideCatalog == null || guideSelection == null
            ? "가이드 선택"
            : guideCatalog.guide(guideSelection.guideId).optString("name");
    if (guideName.length() > 10) guideName = guideName.substring(0, 9) + "…";
    text(
        c,
        "가이드 ▾ " + guideName,
        guideButton.centerX(),
        guideButton.centerY() + d(3),
        8,
        ACCENT,
        true);
    hits.put("guide", guideButton);
    int lensIndex = selectedLens();
    String lensValue = lensIndex < 0 ? "—" : lenses.get(lensIndex).label;
    wheel(c, "lens", l.zoom, "렌즈", lensValue, null, lenses.size() > 1, true);

    color(0xf0141315);
    c.drawRect(l.bottomPanel.left, l.bottomPanel.top, l.bottomPanel.right, l.bottomPanel.bottom, p);
    if (mode.equals("프로")) drawExposure(c, l);
    else {
      String tip =
          guideCatalog != null && guideSelection != null
              ? guideCatalog.guide(guideSelection.guideId).optString("tip")
              : "자동 촬영 · 보도 클린";
      if (tip.length() > 35) tip = tip.substring(0, 34) + "…";
      text(c, tip, l.manual.cx(), l.manual.cy() + d(3), 8, MUTED, true);
    }
    float sx = l.shutterX, sy = l.shutterY;
    color(capturing || countdown > 0 ? 0xff80573e : ACCENT);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(d(2));
    c.drawCircle(sx, sy, d(33), p);
    p.setStyle(Paint.Style.FILL);
    c.drawCircle(sx, sy, d(27), p);
    hit("shutter", l.shutter);
    RectF thumb = rect(l.gallery);
    round(c, thumb, 0xff343034, 7);
    if (thumbnail != null) {
      c.save();
      Path clip = new Path();
      clip.addRoundRect(thumb, d(6), d(6), Path.Direction.CW);
      c.clipPath(clip);
      c.drawBitmap(thumbnail, null, thumb, p);
      c.restore();
    } else text(c, "▧", thumb.centerX(), thumb.centerY() + d(5), 22, MUTED, true);
    hit("gallery", l.gallery);
    round(c, rect(l.flash), flashOn ? 0xff30231c : 0xff09090b, 7);
    color(!flashAvailable ? 0xff514b4e : flashOn ? ACCENT : TEXT);
    float fx = l.flash.cx(), fy = l.flash.cy();
    Path bolt = new Path();
    bolt.moveTo(fx + d(2), fy - d(12));
    bolt.lineTo(fx - d(8), fy + d(1));
    bolt.lineTo(fx - d(1), fy + d(1));
    bolt.lineTo(fx - d(3), fy + d(12));
    bolt.lineTo(fx + d(9), fy - d(3));
    bolt.lineTo(fx + d(2), fy - d(3));
    bolt.close();
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(d(1.3f));
    c.drawPath(bolt, p);
    if (!flashOn) c.drawLine(fx - d(11), fy - d(11), fx + d(11), fy + d(11), p);
    p.setStyle(Paint.Style.FILL);
    if (flashAvailable) hit("toggle:flash", l.flash);
    round(c, rect(l.timer), timerSeconds > 0 ? 0xff30231c : 0xff09090b, 6);
    text(
        c,
        timerSeconds == 0 ? "OFF" : timerSeconds + "s",
        l.timer.cx(),
        l.timer.cy() + d(1),
        8,
        timerSeconds > 0 ? ACCENT : MUTED,
        true);
    text(c, "타이머", l.timer.cx(), l.timer.bottom - d(3), 6, MUTED, true);
    hit("timer", l.timer);
    tool(c, "meter", l.meter, meterOn, "노출");
    tool(c, "level", l.level, level, "수평");
    tool(c, "grid", l.grid, grid, "격자");
    drawFrameLabel(c, l);
    if (tracking) drawTracking(c, l);
    else if (locked || lockPending) drawLockHint(c, l);
    if (!error.isEmpty()) {
      RectF box = new RectF(d(25), picture.centerY() - d(55), w - d(25), picture.centerY() + d(65));
      round(c, box, 0xee222024, 10);
      wrap(c, error, box.left + d(14), box.top + d(22), box.width() - d(28), 12, TEXT);
    }
    if (countdown > 0)
      text(
          c,
          String.valueOf(countdown),
          picture.centerX(),
          picture.centerY() + d(18),
          58,
          ACCENT,
          true);
    else if (capturing)
      text(c, "촬영 중 · 움직이지 마세요", picture.centerX(), picture.centerY() + d(5), 13, TEXT, true);
    if ((locked || lockPending || pointActive) && SystemClock.elapsedRealtime() < exposureBarUntil)
      drawExposureBar(c);
    if (locked || lockPending || focusTime > 0 && SystemClock.elapsedRealtime() - focusTime < 2200)
      postInvalidateDelayed(150);
  }

  public void selectPoint(float nx, float ny, boolean pin) {
    focusX = picture.left + nx * picture.width();
    focusY = picture.top + ny * picture.height();
    pointActive = true;
    lockPending = pin;
    locked = false;
    focusTime = SystemClock.elapsedRealtime();
    invalidate();
  }

  public void clearTracking() {
    tracking = false;
    trackingLost = false;
    trackingPending = false;
    invalidate();
  }

  private void drawTracking(Canvas c, CameraLayout l) {
    float left = Math.max(picture.left, safeLeft),
        top = Math.max(picture.top, l.topBottom),
        right = Math.min(picture.right, getWidth() - safeRight),
        bottom = Math.min(picture.bottom, l.bottomTop);
    int ink = trackingLost ? 0xffff6259 : ACCENT;
    c.save();
    c.clipRect(new RectF(left, top, right, bottom));
    float x = picture.left + trackingX * picture.width(),
        y = picture.top + trackingY * picture.height();
    float rx = Math.max(d(12), trackingRx * picture.width()),
        ry = Math.max(d(12), trackingRy * picture.height());
    color(ink);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(d(1.5f));
    c.drawRect(x - rx, y - ry, x + rx, y + ry, p);
    p.setStyle(Paint.Style.FILL);
    FrameLabel at = FrameLabel.of(left, top, right, bottom, deviceOrientation + 180, d(32));
    c.rotate((360 - deviceOrientation) % 360, at.x, at.y);
    String state =
        trackingLost
            ? "추적 놓침 · 길게 눌러 다시 선택"
            : trackingPending
                ? "추적 준비 중…"
                : "피사체 추적 · "
                    + (trackingFollowExposure ? "AUTO 노출" : lockPending ? "노출 고정 중…" : "노출 고정");
    text(c, state, at.x, at.y + d(3), 9, ink, true);
    text(
        c,
        trackingLost
            ? "화면 터치로 해제"
            : iso > 0 && shutter > 0 ? "ISO·셔터 수동 유지 · 터치로 해제" : "↔ 노출 조절 · 화면 터치로 해제",
        at.x,
        at.y - d(15),
        8,
        TEXT,
        true);
    c.restore();
  }

  public void resetFocusPoint() {
    pointActive = false;
    lockPending = false;
    locked = false;
    focusX = focusY = -1;
    focusTime = 0;
    invalidate();
  }

  private void drawLockHint(Canvas c, CameraLayout l) {
    float left = Math.max(picture.left, safeLeft),
        top = Math.max(picture.top, l.topBottom),
        right = Math.min(picture.right, getWidth() - safeRight),
        bottom = Math.min(picture.bottom, l.bottomTop);
    FrameLabel at = FrameLabel.of(left, top, right, bottom, deviceOrientation + 180, d(32));
    c.save();
    c.rotate((360 - deviceOrientation) % 360, at.x, at.y);
    text(
        c,
        iso > 0 && shutter > 0 ? "좌우 노출 조절 · ISO/셔터 AUTO 필요" : "↔ 좌우 드래그로 노출 조절",
        at.x,
        at.y + d(3),
        9,
        ACCENT,
        true);
    c.restore();
  }

  private void drawFrameLabel(Canvas c, CameraLayout l) {
    RectF visible =
        new RectF(
            Math.max(picture.left, safeLeft),
            Math.max(picture.top, l.topBottom),
            Math.min(picture.right, getWidth() - safeRight),
            Math.min(picture.bottom, l.bottomTop));
    if (visible.width() <= d(25) || visible.height() <= d(25)) return;
    FrameLabel at =
        FrameLabel.of(
            visible.left, visible.top, visible.right, visible.bottom, deviceOrientation, d(12));
    c.save();
    c.clipRect(visible);
    c.rotate(at.rotation, at.x, at.y);
    color(0xffffffff);
    p.setTextAlign(Paint.Align.CENTER);
    p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    p.setTextSize(d(9));
    p.setShadowLayer(d(2), 0, d(1), 0xee000000);
    c.drawText(
        mode + " · " + (outputFormat == OutputFormat.RAW_ONLY ? "RAW" : recipe.name),
        at.x,
        at.y + d(3),
        p);
    p.clearShadowLayer();
    c.restore();
  }

  public int selectedLens() {
    if (pendingLens >= 0 && pendingLens < lenses.size()) return pendingLens;
    for (int i = 0; i < lenses.size(); i++)
      if (active != null && lenses.get(i).physicalId.equals(active.physicalId)) return i;
    return lenses.isEmpty() ? -1 : 0;
  }

  private void drawExposure(Canvas c, CameraLayout l) {
    boolean apertureAvailable =
        active != null && active.apertures != null && active.apertures.length > 1;
    List<String> keys = new ArrayList<>(Arrays.asList("iso", "shutter", "ev", "focus"));
    List<String> labels = new ArrayList<>(Arrays.asList("ISO", "SHUTTER", "EV", "FOCUS"));
    String focus =
        active != null && !active.manualFocus
            ? "고정"
            : focusPosition < 0
                ? "AUTO"
                : focusPosition == 0
                    ? "∞"
                    : String.format(Locale.US, "%.0f%%", focusPosition * 100);
    List<String> values =
        new ArrayList<>(
            Arrays.asList(
                iso == 0 ? "AUTO" : String.valueOf(iso),
                shutter == 0 ? "AUTO" : time(shutter),
                ev == 0 ? "AUTO" : String.format(Locale.US, "%+.1f", ev * evStep),
                focus));
    if (apertureAvailable) {
      keys.add("aperture");
      labels.add("F");
      values.add(aperture < 0 ? "AUTO" : "f/" + actualF);
    }
    if (active != null && NdFilter.available(active.ndDensities)) {
      keys.add("nd");
      labels.add("ND");
      values.add(ndStops == 0 ? "OFF" : String.format(Locale.US, "%.1fEV", ndStops));
    }
    for (int i = 0; i < keys.size(); i++) {
      boolean enabled = active != null && (!keys.get(i).equals("focus") || active.manualFocus);
      wheel(
          c,
          keys.get(i),
          l.manualSlot(i, keys.size()),
          labels.get(i),
          values.get(i),
          null,
          enabled,
          false);
    }
  }

  private void wrap(Canvas c, String text, float x, float y, float width, int size, int color) {
    String line = "";
    for (char ch : text.toCharArray()) {
      p.setTextSize(d(size));
      if (ch == '\n' || p.measureText(line + ch) > width) {
        text(c, line, x, y, size, color, false);
        y += d(20);
        line = "";
        if (ch == '\n') continue;
      }
      line += ch;
    }
    if (!line.isEmpty()) text(c, line, x, y, size, color, false);
  }

  public static String time(long n) {
    double s = n / 1e9;
    if (s >= 1) return String.format(Locale.US, s == Math.rint(s) ? "%.0fs" : "%.1fs", s);
    if (s <= 0) return "AUTO";
    return "1/" + Math.round(1 / s);
  }

  private long exposureBarUntil;

  private void drawExposureBar(Canvas c) {
    float cx = tracking ? picture.left + trackingX * picture.width() : focusX,
        cy = tracking ? picture.top + (trackingY + trackingRy) * picture.height() : focusY + d(22);
    float half = d(70);
    cx = Math.max(picture.left + half, Math.min(picture.right - half, cx));
    cy = Math.max(picture.top + d(18), Math.min(picture.bottom - d(42), cy + d(22)));
    color(0xaa19191b);
    c.drawRoundRect(
        new RectF(cx - half - d(8), cy - d(10), cx + half + d(8), cy + d(27)), d(8), d(8), p);
    color(0xff726962);
    c.drawLine(cx - half, cy, cx + half, cy, p);
    color(ACCENT);
    float x = cx + Math.max(-1, Math.min(1, ev * evStep / 3)) * half;
    c.drawLine(cx, cy, x, cy, p);
    c.drawCircle(x, cy, d(4), p);
    text(
        c,
        String.format(Locale.US, "−    %+.1f EV    +", ev * evStep),
        cx,
        cy + d(19),
        10,
        TEXT,
        true);
  }

  private String hitAt(float x, float y) {
    String target = null;
    for (Map.Entry<String, RectF> e : hits.entrySet())
      if (e.getValue().contains(x, y)) target = e.getKey();
    return target;
  }

  private void step(String key, int amount) {
    if (key.equals("format") || key.equals("ratio") || key.equals("lens") || key.equals("preset"))
      listener.dial(key, amount);
    else listener.manual(key, amount);
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    invalidate();
  }

  public boolean onTouchEvent(MotionEvent event) {
    float x = event.getX(), y = event.getY();
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        downX = lastX = x;
        downY = lastY = y;
        wheelTravel = 0;
        downHit = hitAt(x, y);
        moved = false;
        longPressed = false;
        floatingDrag = false;
        handler.postDelayed(hold, 550);
        return true;
      case MotionEvent.ACTION_POINTER_DOWN:
        moved = true;
        handler.removeCallbacks(hold);
        if (floatingDrag) {
          listener.floating(3, x, y);
          floatingDrag = false;
        }
        downHit = "handled";
        return true;
      case MotionEvent.ACTION_MOVE:
        float dx = x - downX, dy = y - downY;
        if ("shutter".equals(downHit) && longPressed) {
          if (Math.hypot(dx, dy) > d(8) || floatingDrag) {
            listener.floating(floatingDrag ? 1 : 0, x, y);
            floatingDrag = true;
            moved = true;
          }
          return true;
        }
        if ("handled".equals(downHit)) return true;
        if (Math.hypot(dx, dy) > d(8)) {
          moved = true;
          handler.removeCallbacks(hold);
        }
        if (downHit != null && downHit.startsWith("wheel:")) {
          String key = downHit.substring(6);
          if (longPressed) return true;
          wheelTravel += lastX - x;
          lastX = x;
          int amount = (int) (wheelTravel / d(18));
          if (amount != 0) {
            wheelTravel -= amount * d(18);
            step(key, amount);
          }
          wheelPhases.put(key, -wheelTravel / d(18));
          invalidate();
          return true;
        }
        if (downHit == null
            && inPreview(downX, downY)
            && Math.abs(dx) > Math.abs(dy) * 1.2
            && Math.abs(x - lastX) > d(22)) {
          exposureBarUntil = SystemClock.elapsedRealtime() + 2500;
          listener.brightness(x > lastX ? 1 / 3f : -1 / 3f);
          lastX = x;
          invalidate();
        }
        return true;
      case MotionEvent.ACTION_UP:
        handler.removeCallbacks(hold);
        if (floatingDrag) {
          listener.floating(2, x, y);
          floatingDrag = false;
        } else if (!moved && !longPressed) {
          String target = hitAt(x, y);
          if (target != null && target.equals(downHit)) action(target);
          else if (target == null && downHit == null && inPreview(x, y)) {
            locked = false;
            pointActive = true;
            lockPending = false;
            focusX = x;
            focusY = y;
            focusTime = SystemClock.elapsedRealtime();
            listener.focus(
                (x - picture.left) / picture.width(), (y - picture.top) / picture.height(), false);
          }
          performClick();
        }
        settleWheel();
        invalidate();
        return true;
      case MotionEvent.ACTION_CANCEL:
        handler.removeCallbacks(hold);
        if (floatingDrag) listener.floating(3, x, y);
        floatingDrag = false;
        settleWheel();
        return true;
      default:
        return true;
    }
  }

  private void settleWheel() {
    if (downHit == null || !downHit.startsWith("wheel:")) return;
    String key = downHit.substring(6);
    ValueAnimator a = ValueAnimator.ofFloat(wheelPhases.getOrDefault(key, 0f), 0);
    a.setDuration(120);
    a.addUpdateListener(
        v -> {
          wheelPhases.put(key, (float) v.getAnimatedValue());
          invalidate();
        });
    a.start();
  }

  private void action(String target) {
    if (target.equals("shutter")) listener.shutter();
    else if (target.equals("gallery")) listener.gallery();
    else if (target.equals("mode")) listener.openModePicker();
    else if (target.equals("guide")) listener.openGuides();
    else if (target.equals("manage")) listener.managePresets();
    else if (target.equals("timer")) listener.dial("timer", 1);
    else if (target.startsWith("toggle:")) listener.toggle(target.substring(7));
    else if (target.startsWith("wheel:")) step(target.substring(6), 1);
  }

  protected void onDetachedFromWindow() {
    handler.removeCallbacks(hold);
    super.onDetachedFromWindow();
  }

  public boolean performClick() {
    super.performClick();
    return true;
  }
}
