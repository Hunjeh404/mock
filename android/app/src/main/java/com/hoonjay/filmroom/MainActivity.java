package com.hoonjay.filmroom;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.*;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.*;
import android.util.Range;
import android.view.*;
import android.widget.*;
import com.hoonjay.filmroom.camera.*;
import com.hoonjay.filmroom.core.*;
import com.hoonjay.filmroom.guides.*;
import com.hoonjay.filmroom.storage.*;
import com.hoonjay.filmroom.ui.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/**
 * Capture-only press camera. See AGENTS.md and docs/ARCHITECTURE.md before changing capture flow.
 */
public final class MainActivity extends Activity implements SensorEventListener {
  private FrameLayout root, cameraLayer;
  private View galleryScreen;
  private CameraPreview preview;
  private CameraOverlay overlay;
  private FloatingShutterView floating;
  private CameraEngine engine;
  private Stores stores;
  private ShotProcessor processor;
  private SharedPreferences prefs;
  private GuideCatalog catalog;
  private GuideCatalog.Selection guideSelection;
  private GuidePicker guidePicker;
  private Recipe pressRecipe;
  private android.graphics.SurfaceTexture texture;
  private boolean resumed, cameraOpen, timerRunning, destroyed;
  private int timerSeconds, timerGeneration;
  private int proIso, proEv, proAperture = -1;
  private long proTime;
  private float proFocus = -1;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService images = Executors.newSingleThreadExecutor();
  private final CaptureOrientation orientation = new CaptureOrientation();
  private OrientationEventListener orientationListener;
  private SubjectTracker tracker;
  private long meteringSelection;
  private final TouchMotion touchMotion = new TouchMotion();
  private SensorManager sensors;
  private DisplayManager displays;
  private final float[] gravity = new float[3];
  private CaptureChoice capture;
  private Bitmap galleryBitmap, thumbBitmap;
  private int galleryToken;
  private ScrollView galleryScrollView;
  private int galleryScrollY;
  private boolean gallerySelecting, deleting;
  private final LinkedHashSet<String> gallerySelected = new LinkedHashSet<>();
  private String highlightText, shadowText;
  private final DisplayManager.DisplayListener displayListener =
      new DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) {}

        public void onDisplayRemoved(int id) {}

        public void onDisplayChanged(int id) {
          updateRotation();
        }
      };

  private static final class CaptureChoice {
    String id, mode;
    Recipe recipe;
    JSONObject guide;
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    immersive();
    prefs = getSharedPreferences("press-camera-v1", MODE_PRIVATE);
    stores = new Stores(this);
    try {
      PressConfig config = new PressConfig(this);
      pressRecipe = config.recipe;
      highlightText = config.highlightWarning;
      shadowText = config.shadowWarning;
      catalog = new GuideCatalog(this);
      guideSelection = catalog.restore(prefs.getString("guide", ""));
    } catch (Exception error) {
      new AlertDialog.Builder(this)
          .setTitle("설정 데이터 오류")
          .setMessage(error.getMessage())
          .setPositiveButton("닫기", (d, w) -> finish())
          .show();
      return;
    }
    root = new FrameLayout(this);
    root.setBackgroundColor(Ui.BG);
    cameraLayer = new FrameLayout(this);
    root.addView(cameraLayer, new FrameLayout.LayoutParams(-1, -1));
    setContentView(root);
    processor =
        new ShotProcessor(
            stores,
            new ShotProcessor.Listener() {
              public void progress(String id, int percent, int queued) {
                if (destroyed) return;
                overlay.jobs = queued;
                overlay.status = "JPG 저장 " + percent + "%";
                overlay.invalidate();
              }

              public void complete(JSONObject shot) {
                if (destroyed) return;
                refreshThumb();
                notice(
                    "RAW + JPG · "
                        + shot.optInt("width")
                        + " × "
                        + shot.optInt("height")
                        + " 저장 완료");
              }

              public void error(String id, String message) {
                if (!destroyed) notice(message);
              }
            });
    tracker = new SubjectTracker(this::trackingResult);
    preview =
        new CameraPreview(
            this,
            new CameraPreview.Listener() {
              public void clipping(boolean high, boolean low) {
                if (destroyed || overlay == null) return;
                overlay.highlightRisk = high;
                overlay.shadowRisk = low;
                overlay.invalidate();
              }

              public void surface(android.graphics.SurfaceTexture t) {
                if (texture != t && cameraOpen) {
                  engine.close();
                  cameraOpen = false;
                }
                texture = t;
                startCamera();
              }

              public void meter(MeteringStats s, long selection) {
                if (engine != null) engine.metering(s, selection);
              }

              public void analysis(long selection, float[] gray, int w, int h) {
                tracker.frame(selection, gray, w, h);
              }

              public void failure(String message) {
                if (overlay != null) {
                  overlay.error = message;
                  overlay.invalidate();
                }
              }
            });
    cameraLayer.addView(preview, new FrameLayout.LayoutParams(-1, -1));
    overlay =
        new CameraOverlay(
            this,
            new CameraOverlay.Listener() {
              public void shutter() {
                takePhoto();
              }

              public void gallery() {
                showGallery();
              }

              public void openGuides() {
                showGuides();
              }

              public void preset(String id) {}

              public void createPreset() {}

              public void managePresets() {}

              public void focusOptions() {
                showFocusOptions();
              }

              public void openModePicker() {
                chooseMode(overlay.mode.equals("자동") ? "프로" : "자동");
              }

              public void guideAppearance(String key) {
                showGuideAppearance(key);
              }

              public void mode(String mode) {
                chooseMode(mode);
              }

              public void lens(CameraEngine.Lens lens) {
                if (busy()) return;
                resetTouch();
                engine.switchLens(lens);
              }

              public void manual(String key, int steps) {
                changeManual(key, steps);
              }

              public void dial(String key, int steps) {
                changeDial(key, steps);
              }

              public void toggle(String key) {
                if (key.equals("flash")) {
                  if (busy() || !overlay.flashAvailable) return;
                  engine.setFlash(!overlay.flashOn);
                  overlay.flashOn = engine.flash();
                } else if (key.equals("grid")) {
                  overlay.grid = !overlay.grid;
                  prefs.edit().putBoolean("grid", overlay.grid).apply();
                } else if (key.equals("level")) {
                  overlay.level = !overlay.level;
                  prefs.edit().putBoolean("level", overlay.level).apply();
                } else if (key.equals("meter")) {
                  overlay.meterOn = !overlay.meterOn;
                  prefs.edit().putBoolean("meter", overlay.meterOn).apply();
                }
                overlay.invalidate();
              }

              public void flashOptions() {
                showFlashOptions();
              }

              public void floating(int phase, float x, float y) {
                if (floating != null) floating.fromBase(phase, x, y);
              }

              public void help(String mode) {
                Ui.dialog(
                    MainActivity.this,
                    "보도사진 카메라",
                    "자동 모드에서는 ISO·셔터·노출·초점 다이얼을 숨깁니다. 프로 모드에서는 다이얼을 왼쪽으로 굴리면 증가하고 오른쪽으로 굴리면"
                        + " 감소합니다.\n\n"
                        + "화면 터치로 초점·측광, 길게 누르면 피사체 추적과 노출 고정을 사용할 수 있습니다.\n\n"
                        + "가이드에서 보도자료 분류와 키워드를 함께 선택할 수 있습니다. 가이드 선은 저장 사진에 포함되지 않습니다.\n\n"
                        + "저장은 DNG RAW 원본과 보도 클린 JPG 한 쌍이며, 사진 구도는 3:2로 고정됩니다.");
              }

              public void focus(float x, float y, boolean lock) {
                if (!busy() && engine.active() != null) beginPoint(x, y, lock);
              }

              public void brightness(float delta) {
                if (busy()) return;
                if (overlay.iso > 0 && overlay.shutter > 0) {
                  notice("ISO 또는 셔터를 AUTO로 바꾸면 노출을 조절할 수 있습니다.");
                  return;
                }
                engine.brightness(delta);
                main.postDelayed(
                    () -> {
                      overlay.ev = engine.exposureSteps();
                      overlay.invalidate();
                    },
                    80);
              }

              public void frame(Rect frame) {
                preview.setFrame(frame);
              }
            });
    overlay.highlightWarning = highlightText;
    overlay.shadowWarning = shadowText;
    overlay.recipe = pressRecipe.copy();
    overlay.recipes = Collections.singletonList(pressRecipe);
    overlay.mode = "자동";
    overlay.ratio = PressConfig.RATIO;
    overlay.outputFormat = OutputFormat.JPEG_MAX;
    overlay.guideCatalog = catalog;
    overlay.guideRenderer = new GuideRenderer(catalog);
    overlay.guideSelection = guideSelection.copy();
    overlay.grid = prefs.getBoolean("grid", false);
    overlay.level = prefs.getBoolean("level", false);
    overlay.meterOn = prefs.getBoolean("meter", false);
    timerSeconds = prefs.getInt("timer", 0);
    overlay.timerSeconds = timerSeconds;
    preview.setRecipe(pressRecipe);
    cameraLayer.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    floating = new FloatingShutterView(this, prefs, this::takePhoto, overlay::controls);
    cameraLayer.addView(floating, new FrameLayout.LayoutParams(-1, -1));
    cameraLayer.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.graphics.Insets bars =
              insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
          overlay.safeTop = bars.top;
          overlay.safeBottom = bars.bottom;
          overlay.safeLeft = bars.left;
          overlay.safeRight = bars.right;
          floating.insets(bars.left, bars.top, bars.right, bars.bottom);
          overlay.invalidate();
          return insets;
        });
    engine =
        new CameraEngine(
            this,
            new CameraEngine.Listener() {
              public void point(boolean active, boolean locked, boolean pending, long selection) {
                if (selection != meteringSelection) return;
                overlay.pointActive = active;
                overlay.lockPending = pending;
                overlay.locked = locked;
                if (!active) {
                  touchMotion.clear();
                  overlay.resetFocusPoint();
                }
                overlay.invalidate();
              }

              public void ready(List<CameraEngine.Lens> lenses, CameraEngine.Lens lens) {
                if (destroyed) return;
                preview.setColorModel(null);
                resetTouch();
                overlay.lenses = lenses;
                overlay.active = lens;
                overlay.pendingLens = -1;
                overlay.flashAvailable = engine.flashAvailable();
                overlay.flashOn = engine.flash();
                overlay.evStep = engine.evStep();
                overlay.portrait = false;
                overlay.error = "";
                overlay.status = "RAW+JPG · 3:2";
                engine.setPortrait(false);
                engine.setExposure(overlay.iso, overlay.shutter, overlay.ev, overlay.aperture);
                engine.setManualFocus(
                    overlay.mode.equals("프로") && overlay.focusPosition >= 0
                        ? overlay.focusPosition * lens.maxFocus
                        : -1);
                preview.setLens(
                    lens.sensorOrientation,
                    displayDegrees(),
                    lens.previewSize.getWidth(),
                    lens.previewSize.getHeight());
                updatePeaking();
                prefs.edit().putString("lens", lens.physicalId).apply();
                updateRotation();
                overlay.invalidate();
              }

              public void state(
                  int iso, long time, float aperture, float meter, boolean locked, float gain) {
                if (destroyed) return;
                overlay.currentIso = iso;
                overlay.currentShutter = time;
                overlay.actualF = aperture;
                overlay.meterEV = meter;
                overlay.locked = locked;
                preview.setSimulation(gain);
                overlay.invalidate();
              }

              public void previewColor(PreviewColorModel model) {
                if (!destroyed) preview.setColorModel(model);
              }

              public void error(String message) {
                if (destroyed) return;
                if (!engine.isCapturing()) {
                  overlay.capturing = false;
                  floating.busy(timerRunning);
                }
                if (engine.active() == null) overlay.error = message;
                notice(message);
                overlay.invalidate();
              }

              public void busy(boolean value) {
                if (destroyed) return;
                overlay.capturing = value;
                floating.busy(value || timerRunning);
                overlay.invalidate();
              }

              public void captured(File dng, JSONObject meta, Bitmap portrait) {
                CaptureChoice c = capture;
                capture = null;
                if (c == null) {
                  notice("RAW는 보존됐지만 촬영 설정을 연결하지 못했습니다.");
                  return;
                }
                try {
                  meta.put("app", "보도사진 카메라");
                  meta.put("pressGuide", c.guide);
                  meta.put("presetId", pressRecipe.id);
                  meta.put("catalogVersion", catalog.version);
                  processor.accept(
                      c.id,
                      dng,
                      meta,
                      null,
                      c.recipe,
                      PressConfig.RATIO,
                      c.mode,
                      OutputFormat.JPEG_MAX.id);
                } catch (Exception e) {
                  notice("RAW 원본은 보존됩니다. JPG 대기 저장 실패: " + e.getMessage());
                }
              }
            });
    orientationListener =
        new OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
          public void onOrientationChanged(int degrees) {
            int previous = orientation.quadrant();
            orientation.update(degrees);
            overlay.deviceOrientation = orientation.quadrant();
            if (previous != orientation.quadrant()) {
              if (!orientation.isLandscape() && timerRunning) {
                timerRunning = false;
                timerGeneration++;
                overlay.countdown = 0;
                floating.busy(overlay.capturing);
              }
              resetTouch();
              updateRotation();
              overlay.invalidate();
            }
          }
        };
    sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
    displays = (DisplayManager) getSystemService(DISPLAY_SERVICE);
    refreshThumb();
    processor.executor.execute(() -> processor.recover());
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.CAMERA}, 10);
  }

  private void immersive() {
    getWindow().setStatusBarColor(Ui.BG);
    getWindow().setNavigationBarColor(Ui.BG);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (engine == null) return;
    if (orientationListener.canDetectOrientation()) orientationListener.enable();
    preview.onResume();
    displays.registerDisplayListener(displayListener, main);
    for (int type :
        new int[] {
          Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_LINEAR_ACCELERATION
        }) {
      Sensor s = sensors.getDefaultSensor(type);
      if (s != null) sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
    }
    updateRotation();
    startCamera();
  }

  @Override
  protected void onPause() {
    resumed = false;
    if (engine != null) {
      if (guidePicker != null && guidePicker.isShowing()) guidePicker.dismiss();
      orientationListener.disable();
      sensors.unregisterListener(this);
      displays.unregisterDisplayListener(displayListener);
      resetTouch();
      engine.close();
      cameraOpen = false;
      preview.onPause();
      timerRunning = false;
      overlay.countdown = 0;
      floating.busy(false);
    }
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    destroyed = true;
    if (images != null) images.shutdown();
    if (processor != null) processor.executor.shutdown();
    main.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  @Override
  public void onConfigurationChanged(Configuration c) {
    super.onConfigurationChanged(c);
    if (overlay != null) {
      if (guidePicker != null && guidePicker.isShowing()) guidePicker.dismiss();
      updateRotation();
      overlay.requestLayout();
      overlay.invalidate();
    }
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] result) {
    super.onRequestPermissionsResult(code, permissions, result);
    if (code == 10 && engine != null) {
      if (result.length > 0 && result[0] == PackageManager.PERMISSION_GRANTED) startCamera();
      else {
        overlay.error = "카메라 권한이 필요합니다. 앱 설정에서 허용해주세요.";
        overlay.invalidate();
      }
    }
  }

  private void startCamera() {
    if (engine == null
        || texture == null
        || !resumed
        || cameraOpen
        || galleryScreen != null
        || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
      return;
    cameraOpen = true;
    engine.setRotation(orientation.engineRotation());
    engine.open(texture, prefs.getString("lens", ""));
  }

  private int displayDegrees() {
    return getWindowManager().getDefaultDisplay().getRotation() * 90;
  }

  private void updateRotation() {
    if (engine == null) return;
    engine.setRotation(orientation.engineRotation());
    if (engine.active() != null) {
      CameraEngine.Lens l = engine.active();
      preview.setLens(
          l.sensorOrientation,
          displayDegrees(),
          l.previewSize.getWidth(),
          l.previewSize.getHeight());
    }
  }

  private boolean busy() {
    return timerRunning || overlay.capturing;
  }

  private void chooseMode(String mode) {
    if (busy() || (!mode.equals("자동") && !mode.equals("프로"))) return;
    if (overlay.mode.equals("프로")) {
      proIso = overlay.iso;
      proTime = overlay.shutter;
      proEv = overlay.ev;
      proAperture = overlay.aperture;
      proFocus = overlay.focusPosition;
    }
    resetTouch();
    overlay.mode = mode;
    boolean pro = mode.equals("프로");
    overlay.iso = pro ? proIso : 0;
    overlay.shutter = pro ? proTime : 0;
    overlay.ev = pro ? proEv : 0;
    overlay.aperture = pro ? proAperture : -1;
    overlay.focusPosition = pro ? proFocus : -1;
    overlay.ndStops = 0;
    engine.setNd(0);
    engine.setPortrait(false);
    engine.setExposure(overlay.iso, overlay.shutter, overlay.ev, overlay.aperture);
    CameraEngine.Lens l = engine.active();
    engine.setManualFocus(
        pro && overlay.focusPosition >= 0 && l != null ? overlay.focusPosition * l.maxFocus : -1);
    updatePeaking();
    overlay.invalidate();
  }

  private void saveGuideAppearance() {
    catalog.normalize(guideSelection);
    overlay.guideSelection = guideSelection.copy();
    prefs.edit().putString("guide", guideSelection.json().toString()).apply();
    overlay.invalidate();
  }

  private void showGuideAppearance(String key) {
    if (busy()) return;
    if (key.equals("color")) {
      Dialog palette = new Dialog(this);
      palette.requestWindowFeature(Window.FEATURE_NO_TITLE);
      LinearLayout card = Ui.column(this);
      Ui.title(card, "가이드 색상", palette::dismiss);
      for (int i = 0; i < GuideAppearance.NAMES.length; i++) {
        final int color = GuideAppearance.COLORS[i];
        boolean selected = guideSelection.color == color;
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        android.graphics.drawable.GradientDrawable background =
            new android.graphics.drawable.GradientDrawable();
        background.setColor(selected ? 0xff382a22 : Ui.PANEL);
        background.setCornerRadius(Ui.dp(this, 11));
        background.setStroke(Ui.dp(this, 1), selected ? Ui.ACCENT : 0xff3c383c);
        row.setBackground(
            new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x2ef5a36b), background, null));
        View swatch = new View(this);
        android.graphics.drawable.GradientDrawable dot =
            new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(color);
        dot.setStroke(Ui.dp(this, 1), 0xff77716c);
        swatch.setBackground(dot);
        row.addView(swatch, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        TextView label =
            Ui.text(this, GuideAppearance.NAMES[i], 14, selected ? Ui.ACCENT : Ui.TEXT);
        label.setPadding(Ui.dp(this, 12), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView check = Ui.text(this, selected ? "✓" : "", 18, Ui.ACCENT);
        row.addView(check);
        row.setContentDescription(GuideAppearance.NAMES[i] + (selected ? ", 선택됨" : ""));
        row.setSelected(selected);
        row.setOnClickListener(
            v -> {
              guideSelection.color = color;
              saveGuideAppearance();
              palette.dismiss();
            });
        LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(-1, Ui.dp(this, 46));
        space.setMargins(0, 0, 0, Ui.dp(this, 6));
        card.addView(row, space);
      }
      Ui.modal(this, palette, card);
      return;
    }
  }

  private void showGuides() {
    if (busy() || catalog == null) return;
    if (guidePicker != null && guidePicker.isShowing()) {
      guidePicker.dismiss();
      return;
    }
    guidePicker =
        new GuidePicker(
            this,
            catalog,
            guideSelection,
            new GuidePicker.Listener() {
              public void preview(GuideCatalog.Selection s) {
                overlay.guideSelection = s;
                overlay.invalidate();
              }

              public void apply(GuideCatalog.Selection s) {
                guideSelection = s.copy();
                overlay.guideSelection = s.copy();
                prefs.edit().putString("guide", s.json().toString()).apply();
                overlay.invalidate();
              }
            });
    guidePicker.show(root, (int) overlay.controls().topBottom + Ui.dp(this, 3));
  }

  private void takePhoto() {
    if (engine == null
        || !resumed
        || galleryScreen != null
        || busy()
        || guidePicker != null && guidePicker.isShowing()) return;
    if (!orientation.isLandscape()) {
      notice("휴대폰을 가로로 들어 촬영해주세요.");
      return;
    }
    if (engine.active() == null) {
      notice("RAW 카메라가 준비되지 않았습니다.");
      return;
    }
    if (processor.size() >= 2) {
      notice("두 장을 저장 중입니다. 완료 후 촬영해주세요.");
      return;
    }
    if (timerSeconds > 0) {
      timerRunning = true;
      tick(timerSeconds, ++timerGeneration);
    } else shoot();
  }

  private void tick(int left, int generation) {
    if (!timerRunning || !resumed || generation != timerGeneration) return;
    overlay.countdown = left;
    floating.busy(left > 0 || overlay.capturing);
    overlay.invalidate();
    if (left == 0) {
      timerRunning = false;
      shoot();
    } else main.postDelayed(() -> tick(left - 1, generation), 1000);
  }

  private void shoot() {
    // Recheck after the timer: portrait must never reach Camera2 capture.
    if (!orientation.isLandscape()) {
      overlay.countdown = 0;
      floating.busy(overlay.capturing);
      overlay.invalidate();
      notice("휴대폰을 가로로 들어 촬영해주세요.");
      return;
    }
    CaptureChoice c = new CaptureChoice();
    c.id = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
    c.mode = overlay.mode;
    c.recipe = pressRecipe.copy();
    c.guide = guideSelection.json();
    capture = c;
    overlay.capturing = true;
    overlay.countdown = 0;
    floating.busy(true);
    overlay.invalidate();
    engine.setRotation(orientation.engineRotation());
    engine.capture(new File(stores.createShot(c.id), "negative.dng"), c.id);
  }

  private void changeDial(String key, int steps) {
    if (key.equals("opacity")) {
      guideSelection.opacity = GuideAppearance.opacity(guideSelection.opacity + steps);
      saveGuideAppearance();
      return;
    }
    if (busy()) return;
    if (key.equals("lens")) {
      if (overlay.lenses.size() < 2) return;
      int at = overlay.selectedLens(),
          next = Math.max(0, Math.min(overlay.lenses.size() - 1, at + steps));
      if (next != at) {
        overlay.pendingLens = next;
        resetTouch();
        engine.switchLens(overlay.lenses.get(next));
      }
    } else if (key.equals("timer")) {
      int[] values = {0, 2, 5, 10};
      int at = 0;
      for (int i = 0; i < values.length; i++) if (values[i] == timerSeconds) at = i;
      timerSeconds = values[Math.floorMod(at + steps, values.length)];
      overlay.timerSeconds = timerSeconds;
      prefs.edit().putInt("timer", timerSeconds).apply();
    }
    overlay.invalidate();
  }

  private void changeManual(String key, int steps) {
    if (busy() || !overlay.mode.equals("프로")) return;
    CameraEngine.Lens l = engine.active();
    if (l == null) return;
    if (key.equals("focus")) {
      if (!l.manualFocus) return;
      int at = overlay.focusPosition < 0 ? -1 : Math.round(overlay.focusPosition * 50);
      at = Math.max(-1, Math.min(50, at + steps));
      overlay.focusPosition = at < 0 ? -1 : at / 50f;
      engine.setManualFocus(overlay.focusPosition < 0 ? -1 : overlay.focusPosition * l.maxFocus);
      updatePeaking();
      overlay.invalidate();
      return;
    }
    if (key.equals("nd")) {
      overlay.ndStops = NdFilter.step(l.ndDensities, overlay.ndStops, steps);
      engine.setNd(overlay.ndStops);
      overlay.invalidate();
      return;
    }
    if (key.equals("iso")) {
      List<Integer> values = DialOptions.iso(l);
      overlay.iso =
          values.get(
              Math.max(
                  0,
                  Math.min(values.size() - 1, DialOptions.nearest(values, overlay.iso) + steps)));
    } else if (key.equals("shutter")) {
      List<Long> values = DialOptions.shutter(l);
      overlay.shutter =
          values.get(
              Math.max(
                  0,
                  Math.min(
                      values.size() - 1, DialOptions.nearest(values, overlay.shutter) + steps)));
    } else if (key.equals("ev")) {
      if (overlay.iso > 0 && overlay.shutter > 0) {
        notice("수동 ISO·셔터에서 EV는 자동 보정되지 않습니다.");
        return;
      }
      Range<Integer> r = l.chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
      overlay.ev =
          Math.max(
              r == null ? -9 : r.getLower(),
              Math.min(r == null ? 9 : r.getUpper(), overlay.ev + steps));
    } else if (key.equals("aperture") && l.apertures != null && l.apertures.length > 1)
      overlay.aperture = Math.max(-1, Math.min(l.apertures.length - 1, overlay.aperture + steps));
    engine.setExposure(overlay.iso, overlay.shutter, overlay.ev, overlay.aperture);
    overlay.invalidate();
  }

  private void updatePeaking() {
    preview.setPeaking(
        overlay.mode.equals("프로")
            && overlay.focusPosition >= 0
            && overlay.active != null
            && overlay.active.manualFocus,
        prefs.getInt("peak_color", 0xffff3b30),
        prefs.getFloat("peak_opacity", .75f));
  }

  private void showFocusOptions() {
    if (!overlay.mode.equals("프로")) return;
    Ui.dialog(this, "수동 초점", "초점 다이얼을 AUTO로 두면 자동 초점을 사용합니다. 수동 초점에서는 선명한 윤곽에 빗금 표시가 나타납니다.");
  }

  private void showFlashOptions() {
    if (busy() || !overlay.flashAvailable) return;
    new AlertDialog.Builder(this)
        .setTitle("플래시")
        .setItems(
            new String[] {"끔", "켬"},
            (d, i) -> {
              engine.setFlash(i == 1);
              overlay.flashOn = engine.flash();
              overlay.invalidate();
            })
        .show();
  }

  private void resetTouch() {
    if (overlay == null) return;
    if (tracker != null) tracker.stop();
    preview.setTrackingAnalysis(0);
    overlay.clearTracking();
    touchMotion.clear();
    overlay.resetFocusPoint();
    long selection = ++meteringSelection;
    preview.setMeteringPoint(.5f, .5f, false, selection);
    if (engine != null) engine.resetTouchPoint(selection);
  }

  private float[] mapFocus(float x, float y) {
    CameraEngine.Lens l = engine.active();
    int rotation = (l.sensorOrientation - displayDegrees() + 360) % 360;
    float aspect =
        (rotation == 90 || rotation == 270)
            ? l.rawSize.getHeight() / (float) l.rawSize.getWidth()
            : l.rawSize.getWidth() / (float) l.rawSize.getHeight();
    float frame = overlay.frame().width() / (float) Math.max(1, overlay.frame().height());
    if (aspect > frame) x = (x - .5f) * frame / aspect + .5f;
    else y = (y - .5f) * aspect / frame + .5f;
    switch (rotation) {
      case 90:
        return new float[] {y, 1 - x};
      case 180:
        return new float[] {1 - x, 1 - y};
      case 270:
        return new float[] {1 - y, x};
      default:
        return new float[] {x, y};
    }
  }

  private void beginPoint(float x, float y, boolean lock) {
    resetTouch();
    long selection = ++meteringSelection;
    float[] q = mapFocus(x, y);
    touchMotion.select(lock);
    overlay.selectPoint(x, y, lock);
    preview.setMeteringPoint(x, y, true, selection);
    if (lock) {
      overlay.tracking = true;
      overlay.trackingPending = true;
      overlay.trackingFollowExposure = false;
      overlay.trackingX = x;
      overlay.trackingY = y;
      engine.track(q[0], q[1], false, selection);
      tracker.start(selection, x, y);
      preview.setTrackingAnalysis(selection);
    } else engine.focus(q[0], q[1], false, selection);
    overlay.invalidate();
  }

  private void trackingResult(long selection, RegionTracker.Result result) {
    if (selection != meteringSelection
        || !resumed
        || galleryScreen != null
        || !overlay.tracking
        || engine.active() == null) return;
    overlay.trackingPending = false;
    overlay.trackingLost = result.lost;
    overlay.trackingX = result.x;
    overlay.trackingY = result.y;
    overlay.trackingRx = result.rx;
    overlay.trackingRy = result.ry;
    float[] q = mapFocus(result.x, result.y);
    engine.trackingPoint(q[0], q[1], result.lost, selection);
    if (result.lost) {
      preview.setTrackingAnalysis(0);
      tracker.stop();
    } else preview.setMeteringPoint(result.x, result.y, true, selection);
    overlay.invalidate();
  }

  public void onSensorChanged(SensorEvent e) {
    if (overlay == null) return;
    int type = e.sensor.getType();
    if (type == Sensor.TYPE_GYROSCOPE || type == Sensor.TYPE_LINEAR_ACCELERATION) {
      boolean reset =
          type == Sensor.TYPE_GYROSCOPE
              ? touchMotion.rotation(e.values[0], e.values[1], e.values[2], e.timestamp)
              : touchMotion.acceleration(e.values[0], e.values[1], e.values[2], e.timestamp);
      if (reset) resetTouch();
      return;
    }
    for (int i = 0; i < 3; i++) gravity[i] = gravity[i] * .8f + e.values[i] * .2f;
    float roll = (float) Math.toDegrees(Math.atan2(-gravity[0], gravity[1])) + displayDegrees();
    while (roll > 180) roll -= 360;
    while (roll < -180) roll += 360;
    overlay.roll = roll;
    if (overlay.level) overlay.invalidate();
  }

  public void onAccuracyChanged(Sensor s, int accuracy) {}

  private void refreshThumb() {
    images.execute(
        () -> {
          List<JSONObject> shots = stores.shots();
          Bitmap next = null;
          for (JSONObject s : shots)
            try {
              File f = new File(stores.createShot(s.optString("id")), "thumb.jpg");
              if (f.exists()) {
                next = BitmapFactory.decodeFile(f.getPath());
                break;
              }
            } catch (Exception ignored) {
            }
          final Bitmap b = next;
          main.post(
              () -> {
                if (destroyed) {
                  if (b != null) b.recycle();
                  return;
                }
                Bitmap old = thumbBitmap;
                thumbBitmap = b;
                overlay.thumbnail = b;
                overlay.invalidate();
                if (old != null && old != b) old.recycle();
              });
        });
  }

  private void showGallery() {
    if (busy()) return;
    if (galleryScrollView != null) galleryScrollY = galleryScrollView.getScrollY();
    resetTouch();
    engine.close();
    cameraOpen = false;
    galleryToken++;
    if (galleryScreen != null) root.removeView(galleryScreen);
    LinearLayout column = Ui.column(this);
    LinearLayout header = Ui.row(this);
    Button back = Ui.action(this, "‹", false);
    back.setContentDescription("촬영으로 돌아가기");
    header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 44)));
    back.setOnClickListener(
        v -> {
          gallerySelecting = false;
          gallerySelected.clear();
          closeGallery();
        });
    TextView title = Ui.text(this, gallerySelecting ? "사진 선택" : "촬영 사진", 17, Ui.TEXT);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    Button select = Ui.action(this, gallerySelecting ? "취소" : "선택", gallerySelecting);
    select.setSelected(gallerySelecting);
    select.setEllipsize(null);
    select.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
    select.setMinWidth(Ui.dp(this, 88));
    header.addView(select, new LinearLayout.LayoutParams(-2, Ui.dp(this, 40)));
    select.setEnabled(!deleting);
    select.setOnClickListener(
        v -> {
          gallerySelecting = !gallerySelecting;
          gallerySelected.clear();
          showGallery();
        });
    column.addView(header);
    List<JSONObject> shots = stores.shots();
    Set<String> available = new HashSet<>();
    for (JSONObject shot : shots) available.add(shot.optString("id"));
    gallerySelected.retainAll(available);
    TextView count =
        Ui.text(
            this,
            deleting ? "사진을 삭제하고 있습니다." : gallerySelecting ? gallerySelected.size() + "장 선택됨" : "",
            11,
            Ui.MUTED);
    column.addView(count);
    if (gallerySelecting) {
      LinearLayout actions = Ui.row(this);
      Button all = Ui.action(this, "전체 선택", false),
          remove = Ui.action(this, "삭제 (" + gallerySelected.size() + ")", true);
      actions.addView(all, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
      actions.addView(remove, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
      all.setEnabled(!deleting);
      remove.setEnabled(!deleting && !gallerySelected.isEmpty());
      all.setOnClickListener(
          v -> {
            for (JSONObject shot : shots)
              if (deletable(shot)) gallerySelected.add(shot.optString("id"));
            showGallery();
          });
      remove.setOnClickListener(
          v -> {
            List<JSONObject> selected = new ArrayList<>();
            for (JSONObject shot : shots)
              if (gallerySelected.contains(shot.optString("id"))) selected.add(shot);
            confirmDelete(selected);
          });
      column.addView(actions);
    }
    ScrollView scroll = new ScrollView(this);
    galleryScrollView = scroll;
    scroll.post(() -> scroll.scrollTo(0, galleryScrollY));
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(1);
    scroll.addView(list);
    column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    if (shots.isEmpty()) list.addView(Ui.text(this, "아직 촬영한 사진이 없습니다.", 14, Ui.TEXT));
    for (JSONObject shot : shots) {
      String id = shot.optString("id");
      LinearLayout row = Ui.row(this);
      CheckBox check = null;
      if (gallerySelecting) {
        check = new CheckBox(this);
        check.setChecked(gallerySelected.contains(id));
        check.setEnabled(!deleting && deletable(shot));
        row.addView(check, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 60)));
        final CheckBox checkbox = check;
        checkbox.setOnCheckedChangeListener(
            (button, on) -> {
              if (on) gallerySelected.add(id);
              else gallerySelected.remove(id);
              showGallery();
            });
      }
      ImageView image = new ImageView(this);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      row.addView(image, new LinearLayout.LayoutParams(Ui.dp(this, 90), Ui.dp(this, 60)));
      LinearLayout words = new LinearLayout(this);
      words.setOrientation(1);
      words.addView(Ui.text(this, id, 12, Ui.TEXT));
      String status = shot.optString("status");
      words.addView(
          Ui.text(
              this,
              status.equals("ready")
                  ? shot.optInt("width") + " × " + shot.optInt("height")
                  : status.equals("error") ? "저장 오류 · 원본 보존" : "사진 저장 중",
              10,
              Ui.MUTED));
      row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
      list.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(this, 74)));
      File f = new File(stores.createShot(id), "thumb.jpg");
      images.execute(
          () -> {
            Bitmap bitmap = BitmapFactory.decodeFile(f.getPath());
            main.post(
                () -> {
                  if (!destroyed && galleryScreen == column) image.setImageBitmap(bitmap);
                  else if (bitmap != null) bitmap.recycle();
                });
          });
      row.setOnClickListener(
          v -> {
            if (deleting) return;
            if (gallerySelecting) {
              if (!deletable(shot)) return;
              if (!gallerySelected.add(id)) gallerySelected.remove(id);
              showGallery();
            } else showPhoto(shot);
          });
      row.setOnLongClickListener(
          v -> {
            if (deleting || !deletable(shot)) return true;
            gallerySelecting = true;
            gallerySelected.add(id);
            showGallery();
            return true;
          });
    }
    galleryScreen = column;
    cameraLayer.setVisibility(View.INVISIBLE);
    root.addView(column, new FrameLayout.LayoutParams(-1, -1));
  }

  private boolean deletable(JSONObject shot) {
    String status = shot.optString("status");
    return !processor.isQueued(shot.optString("id"))
        && !status.equals("raw_saved")
        && !status.equals("processing");
  }

  private void confirmDelete(List<JSONObject> rows) {
    if (deleting || rows.isEmpty()) return;
    List<JSONObject> selected = new ArrayList<>(rows);
    for (JSONObject shot : selected)
      if (!deletable(shot)) {
        notice("사진 저장이 끝난 뒤 삭제해주세요.");
        return;
      }
    new AlertDialog.Builder(this)
        .setTitle(selected.size() == 1 ? "사진 삭제" : "선택한 사진 삭제")
        .setMessage(selected.size() + "장의 RAW와 JPG를 함께 삭제할까요?")
        .setNegativeButton("취소", null)
        .setPositiveButton(
            "삭제",
            (dialog, which) -> {
              if (deleting) return;
              deleting = true;
              showGallery();
              images.execute(
                  () -> {
                    int failed = 0;
                    for (JSONObject shot : selected)
                      try {
                        if (processor.isQueued(shot.optString("id")))
                          throw new IOException("사진 저장 중");
                        stores.deleteShot(shot);
                      } catch (Exception e) {
                        failed++;
                      }
                    final int failures = failed;
                    main.post(
                        () -> {
                          deleting = false;
                          if (destroyed) return;
                          gallerySelected.clear();
                          gallerySelecting = false;
                          showGallery();
                          refreshThumb();
                          notice(
                              failures == 0
                                  ? selected.size() + "장을 삭제했습니다."
                                  : (selected.size() - failures)
                                      + "장 삭제 · "
                                      + failures
                                      + "장 삭제 실패");
                        });
                  });
            })
        .show();
  }

  private void closeGallery() {
    galleryScrollView = null;
    galleryScrollY = 0;
    galleryToken++;
    if (galleryScreen != null) {
      root.removeView(galleryScreen);
      galleryScreen = null;
    }
    if (galleryBitmap != null) {
      galleryBitmap.recycle();
      galleryBitmap = null;
    }
    cameraLayer.setVisibility(View.VISIBLE);
    startCamera();
  }

  private void showPhoto(JSONObject shot) {
    String uri = shot.optString("renderUri");
    if (uri.isEmpty()) {
      notice("JPG 저장이 끝난 뒤 열어주세요.");
      return;
    }
    int token = ++galleryToken;
    LinearLayout col = Ui.column(this);
    LinearLayout header = Ui.row(this);
    Button back = Ui.action(this, "‹", false);
    back.setContentDescription("사진 목록으로 돌아가기");
    header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 44)));
    back.setOnClickListener(v -> showGallery());
    header.addView(Ui.text(this, "보도사진", 17, Ui.TEXT), new LinearLayout.LayoutParams(0, -2, 1));
    Button delete = Ui.action(this, "삭제", false);
    delete.setContentDescription("이 사진의 RAW와 JPG 삭제");
    header.addView(delete, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 40)));
    delete.setEnabled(!deleting && deletable(shot));
    delete.setOnClickListener(v -> confirmDelete(Collections.singletonList(shot)));
    col.addView(header);
    ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    col.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
    Button share = Ui.button(this, "RAW+JPG 공유", true);
    col.addView(share);
    share.setOnClickListener(
        v -> {
          ArrayList<Uri> uris = new ArrayList<>();
          for (String key : new String[] {"rawUri", "renderUri"})
            if (!shot.optString(key).isEmpty()) uris.add(Uri.parse(shot.optString(key)));
          Intent send =
              new Intent(Intent.ACTION_SEND_MULTIPLE)
                  .setType("image/*")
                  .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                  .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          if (!uris.isEmpty()) {
            ClipData clips = ClipData.newRawUri("보도사진", uris.get(0));
            for (int i = 1; i < uris.size(); i++) clips.addItem(new ClipData.Item(uris.get(i)));
            send.setClipData(clips);
          }
          startActivity(Intent.createChooser(send, "보도사진 공유"));
        });
    if (galleryScreen != null) root.removeView(galleryScreen);
    galleryScreen = col;
    root.addView(col, new FrameLayout.LayoutParams(-1, -1));
    images.execute(
        () -> {
          Bitmap b = null;
          try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            b = BitmapFactory.decodeStream(in, null, o);
          } catch (Exception ignored) {
          }
          final Bitmap bitmap = b;
          main.post(
              () -> {
                if (destroyed || token != galleryToken) {
                  if (bitmap != null) bitmap.recycle();
                  return;
                }
                if (galleryBitmap != null) galleryBitmap.recycle();
                galleryBitmap = bitmap;
                image.setImageBitmap(bitmap);
              });
        });
  }

  @Override
  public void onBackPressed() {
    if (guidePicker != null && guidePicker.isShowing()) {
      guidePicker.dismiss();
      return;
    }
    if (galleryScreen != null) {
      if (gallerySelecting) {
        gallerySelecting = false;
        gallerySelected.clear();
        showGallery();
      } else closeGallery();
      return;
    }
    super.onBackPressed();
  }

  private boolean volume(int key) {
    return (key == KeyEvent.KEYCODE_VOLUME_UP || key == KeyEvent.KEYCODE_VOLUME_DOWN)
        && resumed
        && galleryScreen == null
        && engine != null
        && getWindow().getDecorView().hasWindowFocus();
  }

  @Override
  public boolean onKeyDown(int key, KeyEvent e) {
    if (volume(key)) {
      if (e.getRepeatCount() == 0 && !e.isCanceled()) takePhoto();
      return true;
    }
    return super.onKeyDown(key, e);
  }

  @Override
  public boolean onKeyUp(int key, KeyEvent e) {
    if (volume(key)) return true;
    return super.onKeyUp(key, e);
  }

  private void notice(String s) {
    main.post(
        () -> {
          if (!destroyed) Toast.makeText(this, s, Toast.LENGTH_LONG).show();
        });
  }
}
