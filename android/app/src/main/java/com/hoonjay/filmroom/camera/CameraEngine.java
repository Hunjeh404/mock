package com.hoonjay.filmroom.camera;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.media.*;
import android.os.*;
import android.util.*;
import android.view.Surface;
import com.hoonjay.filmroom.core.ExposureMath;
import com.hoonjay.filmroom.core.DngCrop;
import com.hoonjay.filmroom.core.MeteringStats;
import com.hoonjay.filmroom.core.AeConvergence;
import com.hoonjay.filmroom.core.TouchExposure;
import com.hoonjay.filmroom.core.NdFilter;
import com.hoonjay.filmroom.core.ToneMapping;
import com.hoonjay.filmroom.core.PreviewColorModel;
import com.hoonjay.filmroom.core.CaptureFocusGate;
import java.io.*;
import java.nio.*;
import java.util.*;
import org.json.*;

/** Camera2 engine: explicit physical outputs, real RAW stills, timestamp-matched metadata. */
public final class CameraEngine {
  public static final class Lens {
    public String logicalId, physicalId, label;
    public CameraCharacteristics chars;
    public Size rawSize, previewSize;
    public float ratio;
    public boolean portrait;
    public int sensorOrientation;
    public Range<Integer> isoRange;
    public Range<Long> exposureRange;
    public float[] apertures;
    public float[] ndDensities={0};
    public float maxFocus;
    public boolean manualFocus;
    public Rect active;

    public int minIso() {
      return isoRange == null ? 100 : isoRange.getLower();
    }

    public int maxIso() {
      return isoRange == null ? 6400 : isoRange.getUpper();
    }

    public long minTime() {
      return exposureRange == null ? 100000L : exposureRange.getLower();
    }

    public long maxTime() {
      return exposureRange == null ? 1000000000L : exposureRange.getUpper();
    }
  }

  public interface Listener {
    void ready(List<Lens> lenses, Lens active);

    void state(int iso, long nanos, float aperture, float meter, boolean locked, float previewGain);
    default void previewColor(PreviewColorModel model) {}

    void point(boolean active,boolean locked,boolean pending,long selection);

    void error(String message);

    void captured(File dng, JSONObject metadata, Bitmap portrait);

    void busy(boolean capturing);
  }

  private final Context context;
  private final CameraManager manager;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private HandlerThread thread;
  private Handler handler;
  private CameraDevice camera;
  private CameraCaptureSession session;
  private CaptureRequest.Builder preview;
  private Surface surface;
  private SurfaceTexture previewTexture;
  private ImageReader rawReader, portraitReader;
  private List<Lens> lenses = new ArrayList<>();
  private Lens active;
  private CaptureResult last;
  private volatile Pending pending;
  private volatile boolean closed = true;
  private volatile int generation = 0;
  private volatile boolean flashOn;
  private volatile int flashStrength=1;
  public boolean flashAvailable(){return active!=null&&Boolean.TRUE.equals(active.chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));}
  public boolean flash(){return flashOn&&flashAvailable();}
  public int flashMaxStrength(){if(!flashAvailable()||Build.VERSION.SDK_INT<35)return 1;java.util.List<CaptureRequest.Key<?>> keys=active.chars.getAvailableCaptureRequestKeys();if(keys==null||!keys.contains(CaptureRequest.FLASH_STRENGTH_LEVEL))return 1;Integer max=active.chars.get(CameraCharacteristics.FLASH_SINGLE_STRENGTH_MAX_LEVEL);return max==null?1:Math.max(1,max);}
  public int flashStrength(){return flashStrength;}
  public void setFlash(boolean on){flashOn=on&&flashAvailable();}
  public void setFlashStrength(int level){flashStrength=Math.max(1,Math.min(flashMaxStrength(),level));}
  private boolean locked = false, focusRequested = false, portraitMode = false;
  private long focusStart;
  private boolean regionActive;
  private long pointSelection;
  private int regionSamples;
  private float lockedFocus=-1;
  private boolean tracking,trackingLost;
  private long lastTrackingAf;
  private int wantedIso = 0, ev = 0, apertureIndex = -1, currentIso = 100;
  private long wantedTime = 0, currentTime = 10000000L, automaticTime = 10000000L;
  private int automaticIso = 100;
  private float currentAperture = 1.8f, meter = 0, frameMean = .18f;
  private float focusX = .5f, focusY = .5f;
  private volatile int rotation = 0;
  private float ndStops=0;
  private boolean portraitDisabled = false, farFocus = false;
  private volatile float manualFocus = -1;
  private TonemapCurve matchedCurve;
  private long lastPreviewColorAt;
  private volatile long previewColorEpoch;
  private int autoCorrectionSteps = 0;
  private long lastAutoAdjustment;
  private CaptureResult lastLogical;
  private MeteringStats frameStats;
  private final AeConvergence aeConvergence=new AeConvergence();

  private static final class Pending {
    String id;
    File path;
    Image image, portrait;
    CaptureResult result;
    int rotation;
    float userEv,autoEv;
    MeteringStats stats;
    boolean point,pointLocked,tracking,trackingLost;
    boolean portraitExpected;
    TonemapCurve requestedCurve;
    int requestedBoost = 100;
    long started;
    CaptureFocusGate focusGate;
    boolean waitingFocus,afTriggered,stillSubmitted;
    int afMode;
    String focusStatus="user-or-fixed";
  }

  public CameraEngine(Context context, Listener l) {
    this.context = context;
    manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    listener = l;
  }

  private void notifyError(String s) {
    main.post(() -> listener.error(s));
  }

  private void ensureThread() {
    if (thread == null) {
      thread = new HandlerThread("FilmRoomCamera");
      thread.start();
      handler = new Handler(thread.getLooper());
    }
  }

  public Lens active() {
    return active;
  }

  public List<Lens> lenses() {
    return lenses;
  }

  public int iso() {
    return wantedIso;
  }

  public long shutter() {
    return wantedTime;
  }

  public int exposureSteps() {
    return ev;
  }

  public boolean isLocked() {
    return locked;
  }

  public boolean isCapturing() {
    return pending != null;
  }

  public boolean hasPortrait() {
    return active != null && active.portrait && !portraitDisabled;
  }

  public void setRotation(int degrees) {
    rotation = degrees;
  }

  public void open(SurfaceTexture previewSurface, String selectedPhysical) {
    ensureThread();
    previewTexture = previewSurface;
    closed = false;
    handler.post(
        () -> {
          try {
            discover();
            if (lenses.isEmpty()) {
              notifyError("이 단말에는 RAW+JPG 저장을 지원하는 촬영 렌즈가 없습니다. RAW 지원 기기에서 사용할 수 있습니다.");
              return;
            }
            Lens chosen = null;
            for (Lens l : lenses) if (l.physicalId.equals(selectedPhysical)) chosen = l;
            if (chosen == null) for (Lens l : lenses) if (Math.abs(l.ratio - 1) < .05) chosen = l;
            if (chosen == null) chosen = lenses.get(0);
            openLens(chosen);
          } catch (Exception e) {
            notifyError("카메라를 열 수 없습니다: " + e.getMessage());
          }
        });
  }

  private void discover() throws CameraAccessException {
    lenses = new ArrayList<>();
    Map<String, String> owners = new HashMap<>();
    for (String id : manager.getCameraIdList()) {
      CameraCharacteristics c = manager.getCameraCharacteristics(id);
      if (Objects.equals(
          c.get(CameraCharacteristics.LENS_FACING), CameraCharacteristics.LENS_FACING_BACK))
        for (String physical : c.getPhysicalCameraIds()) owners.putIfAbsent(physical, id);
    }
    Set<String> ids = new LinkedHashSet<>(owners.keySet());
    for (String id : manager.getCameraIdList()) {
      CameraCharacteristics c = manager.getCameraCharacteristics(id);
      if (c.getPhysicalCameraIds().isEmpty()) ids.add(id);
    }
    Set<String> seen = new HashSet<>();
    for (String id : ids) {
      try {
        CameraCharacteristics c = manager.getCameraCharacteristics(id);
        if (!Objects.equals(
            c.get(CameraCharacteristics.LENS_FACING), CameraCharacteristics.LENS_FACING_BACK))
          continue;
        int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (!has(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
            || !has(caps, CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR))
          continue;
        Integer cfa = c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        if (cfa == null || cfa > 3) continue;
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null || map.getOutputSizes(ImageFormat.RAW_SENSOR) == null) continue;
        float[] focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF sensor = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if (focal == null || sensor == null) continue;
        float eq = 36 * focal[0] / sensor.getWidth();
        String key = String.format(Locale.US, "%.1f", eq);
        if (!seen.add(key)) continue;
        Lens l = new Lens();
        l.logicalId =
            Arrays.asList(manager.getCameraIdList()).contains(id)
                ? id
                : owners.getOrDefault(id, id);
        l.physicalId = id;
        l.chars = c;
        l.ratio = eq;
        l.rawSize = chooseRaw(map.getOutputSizes(ImageFormat.RAW_SENSOR));
        l.previewSize = choosePreview(map.getOutputSizes(SurfaceTexture.class), l.rawSize);
        l.sensorOrientation = get(c, CameraCharacteristics.SENSOR_ORIENTATION, 90);
        l.isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        l.exposureRange = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        l.apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
        l.ndDensities=NdFilter.supported(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FILTER_DENSITIES),
            c.getAvailableCaptureRequestKeys().contains(CaptureRequest.LENS_FILTER_DENSITY));
        l.maxFocus = get(c, CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE, 0f);
        l.manualFocus = l.maxFocus > 0 && has(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
            && has(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES), CameraMetadata.CONTROL_AF_MODE_OFF)
            && c.getAvailableCaptureRequestKeys().contains(CaptureRequest.LENS_FOCUS_DISTANCE);
        l.active = c.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
        if (l.active == null) l.active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Capability[] modes =
            c.get(CameraCharacteristics.CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES);
        if (modes != null)
          for (Capability m : modes)
            if (m.getMode() == CameraMetadata.CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE)
              l.portrait = true;
        lenses.add(l);
      } catch (Exception ignored) {
      }
    }
    float base = 26, best = Float.MAX_VALUE;
    for (Lens l : lenses) {
      float diff = Math.abs(l.ratio - 26);
      if (diff < best) {
        best = diff;
        base = l.ratio;
      }
    }
    for (Lens l : lenses) {
      l.ratio /= base;
      float rounded = Math.round(l.ratio * 10) / 10f;
      l.label =
          Math.abs(rounded - Math.round(rounded)) < .05
              ? Math.round(rounded) + "×"
              : String.format(Locale.US, "%.1f×", rounded);
    }
    lenses.sort(Comparator.comparingDouble(l -> l.ratio));
  }

  private static boolean has(int[] a, int x) {
    if (a != null) for (int v : a) if (v == x) return true;
    return false;
  }

  private static <T> T get(CameraCharacteristics c, CameraCharacteristics.Key<T> key, T fallback) {
    T v = c.get(key);
    return v == null ? fallback : v;
  }

  private static Size chooseRaw(Size[] sizes) {
    Size best = null, small = sizes[0];
    for (Size s : sizes) {
      long area = (long) s.getWidth() * s.getHeight();
      if (area < (long) small.getWidth() * small.getHeight()) small = s;
      if (area <= 24000000 && (best == null || area > (long) best.getWidth() * best.getHeight()))
        best = s;
    }
    return best == null ? small : best;
  }

  private static Size choosePreview(Size[] sizes, Size raw) {
    if (sizes == null || sizes.length == 0) return new Size(1280, 960);
    Size best = sizes[0];
    double score = Double.MAX_VALUE;
    for (Size s : sizes) {
      if (s.getWidth() > 1920 || s.getHeight() > 1440) continue;
      double d =
          Math.abs(
                      s.getWidth() / (double) s.getHeight()
                          - raw.getWidth() / (double) raw.getHeight())
                  * 100
              + Math.abs(s.getWidth() - 1440) / 1440.;
      if (d < score) {
        score = d;
        best = s;
      }
    }
    return best;
  }

  public void switchLens(Lens l) {
    if (l == null || pending != null) return;
    handler.post(
        () -> {
          portraitMode = false;
          portraitDisabled = false;
          closeHardware();
          openLens(l);
        });
  }

  private void openLens(Lens lens) {
    if (closed) return;
    final int opening = ++generation;
    if(active==null || !active.physicalId.equals(lens.physicalId))manualFocus=-1;
    if(active==null || !active.physicalId.equals(lens.physicalId))ndStops=0;
    active = lens;flashOn=false;flashStrength=flashMaxStrength();
    autoCorrectionSteps = 0;
    lastAutoAdjustment = SystemClock.elapsedRealtime();
    previewTexture.setDefaultBufferSize(lens.previewSize.getWidth(), lens.previewSize.getHeight());
    if (surface != null) surface.release();
    surface = new Surface(previewTexture);
    wantedIso = clampIso(wantedIso);
    wantedTime = clampTime(wantedTime);
    locked = false;
    focusRequested = false;regionActive=false;lockedFocus=-1;regionSamples=0;tracking=false;trackingLost=false;
    last = null;lastLogical=null;frameStats=null;aeConvergence.reset();
    try {
      if (context.checkSelfPermission(Manifest.permission.CAMERA)
          != PackageManager.PERMISSION_GRANTED) {
        notifyError("카메라 권한을 허용해주세요.");
        return;
      }
      manager.openCamera(
          lens.logicalId,
          new CameraDevice.StateCallback() {
            public void onOpened(CameraDevice d) {
              if (closed || opening != generation) {
                d.close();
                return;
              }
              camera = d;
              configure();
            }

            public void onDisconnected(CameraDevice d) {
              d.close();
              if (camera != d) return;
              camera = null;
              notifyError("카메라 연결이 끊겼습니다.");
            }

            public void onError(CameraDevice d, int e) {
              d.close();
              if (opening != generation) return;
              camera = null;
              notifyError("카메라 오류 " + e + ". 다른 앱의 카메라 사용을 종료해주세요.");
            }
          },
          handler);
    } catch (Exception e) {
      notifyError("렌즈를 열지 못했습니다: " + e.getMessage());
    }
  }

  private void configure() {
    if (camera == null || closed || surface == null) return;
    final CameraDevice configuring = camera;
    final Lens configuredLens = active;
    try {
      matchedCurve = null;
      lastPreviewColorAt=0;
      previewColorEpoch++;
      if (has(active.chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES),
          CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)) {
        Integer maxPoints = active.chars.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS);
        float[] points = ToneMapping.srgbCurve(maxPoints == null ? 32 : maxPoints);
        matchedCurve = new TonemapCurve(points, points, points);
      }
      rawReader =
          ImageReader.newInstance(
              active.rawSize.getWidth(), active.rawSize.getHeight(), ImageFormat.RAW_SENSOR, 3);
      rawReader.setOnImageAvailableListener(
          r -> {
            if (closed || r != rawReader) return;
            Image im = null;
            try {
              im = r.acquireNextImage();
              if (im == null) return;
              if (pending == null || !pending.stillSubmitted) {
                im.close();
                return;
              }
              if (pending.image != null) pending.image.close();
              pending.image = im;
              finishPending();
            } catch (Exception e) {
              if (im != null) im.close();
              failPending("RAW 수신 실패: " + e.getMessage());
            }
          },
          handler);
      List<OutputConfiguration> outputs = new ArrayList<>();
      outputs.add(output(surface));
      outputs.add(output(rawReader.getSurface()));
      if (active.portrait && !portraitDisabled) {
        Size[] ys =
            active
                .chars
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                .getOutputSizes(ImageFormat.YUV_420_888);
        if (ys != null) {
          Size y = chooseRaw(ys);
          portraitReader =
              ImageReader.newInstance(y.getWidth(), y.getHeight(), ImageFormat.YUV_420_888, 2);
          portraitReader.setOnImageAvailableListener(
              r -> {
                if (closed || r != portraitReader) return;
                try {
                  Image im = r.acquireNextImage();
                  if (im == null) return;
                  if (pending == null || !pending.stillSubmitted || !pending.portraitExpected) {
                    im.close();
                    return;
                  }
                  if (pending.portrait != null) pending.portrait.close();
                  pending.portrait = im;
                  finishPending();
                } catch (IllegalStateException e) {
                  if (!closed) failPending("인물 사진 수신 실패: " + e.getMessage());
                }
              },
              handler);
          outputs.add(output(portraitReader.getSurface()));
        }
      }
      SessionConfiguration cfg =
          new SessionConfiguration(
              SessionConfiguration.SESSION_REGULAR,
              outputs,
              handler::post,
              new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession s) {
                  if (closed || camera != configuring || active != configuredLens) {
                    s.close();
                    return;
                  }
                  session = s;
                  try {
                    preview = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    preview.addTarget(surface);
                    apply(preview, false);
                    s.setRepeatingRequest(preview.build(), results, handler);
                    main.post(() -> listener.ready(lenses, active));
                  } catch (Exception e) {
                    notifyError("미리보기를 시작할 수 없습니다: " + e.getMessage());
                  }
                }

                public void onConfigureFailed(CameraCaptureSession s) {
                  s.close();
                  if (closed || camera != configuring || active != configuredLens) return;
                  if (active.portrait && !portraitDisabled) {
                    portraitDisabled = true;
                    if (rawReader != null) rawReader.close();
                    if (portraitReader != null) {
                      portraitReader.close();
                      portraitReader = null;
                    }
                    configure();
                  } else notifyError("이 렌즈의 RAW 스트림 구성을 사용할 수 없습니다. 다른 렌즈를 선택해주세요.");
                }
              });
      camera.createCaptureSession(cfg);
    } catch (Exception e) {
      notifyError("촬영 준비 실패: " + e.getMessage());
    }
  }

  private OutputConfiguration output(Surface s) {
    OutputConfiguration o = new OutputConfiguration(s);
    if (!active.logicalId.equals(active.physicalId)) o.setPhysicalCameraId(active.physicalId);
    return o;
  }

  private final CameraCaptureSession.CaptureCallback results =
      new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(
            CameraCaptureSession s, CaptureRequest q, TotalCaptureResult total) {
          if(closed||session!=s||active==null)return;
          CaptureResult r = effective(total);
          last = r;lastLogical=total;
          Integer i = r.get(CaptureResult.SENSOR_SENSITIVITY);
          Long t = r.get(CaptureResult.SENSOR_EXPOSURE_TIME);
          Float a = r.get(CaptureResult.LENS_APERTURE);
          if (i != null) currentIso = i;
          if (t != null) currentTime = t;
          if (a != null) currentAperture = a;
          long colorNow=SystemClock.elapsedRealtime();
          if(colorNow-lastPreviewColorAt>=150){
            lastPreviewColorAt=colorNow;final Lens colorLens=active;final boolean colorPortrait=portraitMode;final long colorEpoch=previewColorEpoch;
            final PreviewColorModel color=colorPortrait?null:RawMetadata.previewModel(active.chars,r,matchedCurve);
            // A result queued for an old lens/session/mode cannot recolor the new preview.
            if(colorPortrait||color!=null)main.post(()->{if(!closed&&previewColorEpoch==colorEpoch&&session==s&&active==colorLens&&portraitMode==colorPortrait)listener.previewColor(color);});
          }
          if(i!=null&&t!=null)aeConvergence.observe(i,t,SystemClock.elapsedRealtime());
          if (wantedIso == 0 && wantedTime == 0 && !locked && !pointManualAe()) {
            automaticIso = currentIso;
            automaticTime = currentTime;
          }
          if (focusRequested && SystemClock.elapsedRealtime()-focusStart>450){
            long now=SystemClock.elapsedRealtime();Integer af=controlResult(CaptureResult.CONTROL_AF_STATE),ae=controlResult(CaptureResult.CONTROL_AE_STATE);
            boolean autoFocus=manualFocus<0&&!farFocus;
            boolean fixed=get(active.chars,CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE,0f)==0;
            boolean focused=tracking||!autoFocus||fixed||Objects.equals(af,CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED);
            boolean afFinished=focused||Objects.equals(af,CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
            boolean fullAuto=wantedIso==0&&wantedTime==0&&!pointManualAe();
            boolean exposed=wantedIso>0&&wantedTime>0 || fullAuto && (Objects.equals(ae,CameraMetadata.CONTROL_AE_STATE_CONVERGED)
                ||Objects.equals(ae,CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED)||aeConvergence.ready(false,ae==null,now,focusStart))
                ||!fullAuto&&regionSamples>0&&aeConvergence.ready(false,true,now,focusStart);
            if(afFinished&&exposed||now-focusStart>3000){
              if(fullAuto){automaticIso=currentIso;automaticTime=currentTime;}
              Float distance=last.get(CaptureResult.LENS_FOCUS_DISTANCE);
              lockedFocus=!tracking&&autoFocus&&distance!=null?distance:-1;
              focusRequested=false;locked=true;notifyPoint();repeating();
              if(!focused)notifyError("현재 초점·노출을 고정했습니다. 피사체가 선명한지 확인해주세요.");
            }
          }
          int photoIso = photoIso();
          long photoTime =
              photoTime();
          float previewGain = ExposureMath.previewGain(currentIso, currentTime, photoIso, photoTime);
          main.post(() -> listener.state(photoIso, photoTime, currentAperture, meter, locked, previewGain));
          Pending shot=pending;
          if(shot!=null&&shot.waitingFocus&&shot.focusGate!=null){
            Integer af=controlResult(CaptureResult.CONTROL_AF_STATE),lens=controlResult(CaptureResult.LENS_STATE);
            captureFocusDecision(shot,shot.focusGate.observe(SystemClock.elapsedRealtime(),total.getFrameNumber(),af,
                Objects.equals(lens,CameraMetadata.LENS_STATE_MOVING)));
          }
        }
      };

  private <T> T controlResult(CaptureResult.Key<T> key){
    T value=last==null?null:last.get(key);
    return value!=null?value:lastLogical==null?null:lastLogical.get(key);
  }

  private CaptureResult effective(TotalCaptureResult r) {
    if (!active.logicalId.equals(active.physicalId)) {
      CaptureResult p = r.getPhysicalCameraResults().get(active.physicalId);
      if (p != null) return p;
    }
    return r;
  }

  private void apply(CaptureRequest.Builder b, boolean still) {
    b.set(
        CaptureRequest.CONTROL_MODE,
        portraitMode
            ? CameraMetadata.CONTROL_MODE_USE_EXTENDED_SCENE_MODE
            : CameraMetadata.CONTROL_MODE_AUTO);
    if (portraitMode)
      b.set(
          CaptureRequest.CONTROL_EXTENDED_SCENE_MODE,
          CameraMetadata.CONTROL_EXTENDED_SCENE_MODE_BOKEH_STILL_CAPTURE);
    boolean emulateLock=emulateAeLock();
    boolean manual = wantedIso > 0 || wantedTime > 0 || emulateLock || pointManualAe();
    if (!portraitMode && manual) {
      b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
      int iso = wantedIso > 0 ? wantedIso : clampIso(automaticIso);
      long time = wantedTime > 0 ? wantedTime : clampTime(automaticTime);
      if (!still) time = Math.min(time, 100000000L);
      b.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
      b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, time);
      b.set(CaptureRequest.SENSOR_FRAME_DURATION, Math.max(time, 33333333L));
    } else {
      b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
      if (Boolean.TRUE.equals(active.chars.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)))
        b.set(CaptureRequest.CONTROL_AE_LOCK, locked);
      Range<Integer> range = active.chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
      b.set(
          CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
          range == null ? 0 : Math.max(range.getLower(), Math.min(range.getUpper(),
              ev + (portraitMode ? 0 : autoCorrectionSteps))));
    }
    int[] focusModes = active.chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
    b.set(
        CaptureRequest.CONTROL_AF_MODE,
        ((tracking&&!trackingLost)||!regionActive&&!locked) && has(focusModes, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            ? CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            : has(focusModes, CameraMetadata.CONTROL_AF_MODE_AUTO)
            ? CameraMetadata.CONTROL_AF_MODE_AUTO
            : CameraMetadata.CONTROL_AF_MODE_OFF);
    b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
    if ((farFocus || manualFocus >= 0 || locked&&lockedFocus>=0&&active.manualFocus) && !portraitMode) {
      b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
      Float minFocus = active.chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
      if (minFocus != null && minFocus > 0) b.set(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocus >= 0 ? Math.min(minFocus,manualFocus) : farFocus?0f:Math.min(minFocus,lockedFocus));
    }
    Pending focusing=pending;
    if(focusing!=null&&focusing.waitingFocus&&focusing.afTriggered&&captureNeedsAF())
      b.set(CaptureRequest.CONTROL_AF_MODE,focusing.afMode);
    Rect a = active.active;
    if (a != null) {
      int x = a.left + (int) (a.width() * focusX),
          y = a.top + (int) (a.height() * focusY),
          w = Math.max(8, a.width() / 10),
          h = Math.max(8, a.height() / 10);
      Rect r =
          new Rect(
              Math.max(a.left, x - w / 2),
              Math.max(a.top, y - h / 2),
              Math.min(a.right, x + w / 2),
              Math.min(a.bottom, y + h / 2));
      MeteringRectangle[] region = {new MeteringRectangle(r, 1000)};
      if (get(active.chars, CameraCharacteristics.CONTROL_MAX_REGIONS_AF, 0) > 0)
        b.set(CaptureRequest.CONTROL_AF_REGIONS, region);
      if (get(active.chars, CameraCharacteristics.CONTROL_MAX_REGIONS_AE, 0) > 0)
        b.set(CaptureRequest.CONTROL_AE_REGIONS,
            regionActive ? region : new MeteringRectangle[] {new MeteringRectangle(a,1000)});
    }
    if (!portraitMode
        && active.chars.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES) != null)
      b.set(
          CaptureRequest.DISTORTION_CORRECTION_MODE, CameraMetadata.DISTORTION_CORRECTION_MODE_OFF);
    b.set(
        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
        CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
    if (farFocus && wantedTime > 200000000L)
      b.set(
          CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
          CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF);
    b.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
    if (!portraitMode && matchedCurve != null) {
      b.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE);
      b.set(CaptureRequest.TONEMAP_CURVE, matchedCurve);
      // Remove vendor scene-dependent color effects from the neutral baseline.
      b.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_OFF);
    } else if (portraitMode && has(active.chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES),
        CameraMetadata.TONEMAP_MODE_FAST)) {
      b.set(CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_FAST);
      b.set(CaptureRequest.TONEMAP_CURVE, null);
    }
    Range<Integer> boostRange = active.chars.get(CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE);
    if(!portraitMode&&manual&&boostRange!=null){
      Integer held=locked&&last!=null?last.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST):null;
      b.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST,
          Math.max(boostRange.getLower(),Math.min(boostRange.getUpper(),held==null?100:held)));
    }
    if(NdFilter.available(active.ndDensities))b.set(CaptureRequest.LENS_FILTER_DENSITY,portraitMode?0f:ndStops);
    b.set(CaptureRequest.FLASH_MODE, still&&flash()?CameraMetadata.FLASH_MODE_SINGLE:CameraMetadata.FLASH_MODE_OFF);
    if(still&&flash()&&Build.VERSION.SDK_INT>=35&&flashMaxStrength()>1)b.set(CaptureRequest.FLASH_STRENGTH_LEVEL,Math.min(flashMaxStrength(),flashStrength));
    b.set(
        CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
        CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON);
    b.set(CaptureRequest.SCALER_CROP_REGION, active.active);
    if (apertureIndex >= 0
        && active.apertures != null
        && apertureIndex < active.apertures.length
        && !portraitMode) b.set(CaptureRequest.LENS_APERTURE, active.apertures[apertureIndex]);
  }

  private void repeating() {
    if (session == null || preview == null) return;
    try {
      apply(preview, false);
      session.setRepeatingRequest(preview.build(), results, handler);
    } catch (Exception e) {
      notifyError("설정을 적용할 수 없습니다: " + e.getMessage());
    }
  }

  public void setExposure(int iso, long shutter, int steps, int aperture) {
    if (active == null || handler == null) return;
    handler.post(()->updateExposure(iso,shutter,steps,aperture));
  }
  private void updateExposure(int iso,long shutter,int steps,int aperture){
    if(active==null||closed)return;
          boolean wasAuto = wantedIso == 0 && wantedTime == 0;
          wantedIso = clampIso(iso);
          wantedTime = clampTime(shutter);
          if (!wasAuto || wantedIso > 0 || wantedTime > 0) autoCorrectionSteps = 0;
          lastAutoAdjustment = SystemClock.elapsedRealtime();
          Range<Integer> range =
              active.chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
          int limit=Math.max(Math.max(1,Math.round(3/Math.max(.1f,evStep()))),range==null?0:Math.max(Math.abs(range.getLower()),Math.abs(range.getUpper())));int nextEv=locked&&!portraitMode?Math.max(-limit,Math.min(limit,steps)):range==null?0:Math.max(range.getLower(),Math.min(range.getUpper(),steps));
          if(locked&&(wantedIso>0||wantedTime>0||emulateAeLock()||pointManualAe())){
            TouchExposure adjusted=TouchExposure.adjust(wantedIso,wantedTime,automaticIso,automaticTime,(nextEv-ev)*evStep(),
                active.minIso(),active.maxIso(),active.minTime(),active.maxTime());
            if(wantedIso==0)automaticIso=adjusted.iso;if(wantedTime==0)automaticTime=adjusted.time;
          }
          ev=nextEv;
          apertureIndex = aperture;
          repeating();
  }

  private int clampIso(int x) {
    return x <= 0
        ? 0
        : active == null ? x : Math.max(active.minIso(), Math.min(active.maxIso(), x));
  }

  private long clampTime(long x) {
    return x <= 0
        ? 0
        : active == null ? x : Math.max(active.minTime(), Math.min(active.maxTime(), x));
  }

  public void setNd(float stops){
    if(handler==null)return;
    handler.post(() -> {
      if(active==null||portraitMode)return;
      ndStops=NdFilter.step(active.ndDensities,stops,0);
      autoCorrectionSteps=0;lastAutoAdjustment=SystemClock.elapsedRealtime();
      repeating();
    });
  }

  public void setManualFocus(float distance) {
    if(handler==null || active==null)return;
    handler.post(() -> {
      if(!active.manualFocus || portraitMode)return;
      manualFocus=distance<0?-1:Math.max(0,Math.min(active.maxFocus,distance));
      farFocus=false;
      repeating();
    });
  }

  public void setFarFocus(boolean far) {
    if (handler != null)
      handler.post(
          () -> {
            farFocus = far;
            manualFocus = -1;
            repeating();
          });
  }

  public void setPortrait(boolean p) {
    if (handler == null) return;
    handler.post(
        () -> {
          portraitMode = p && hasPortrait();
          previewColorEpoch++;lastPreviewColorAt=0;
          if(portraitMode)ndStops=0;
          autoCorrectionSteps = 0;
          lastAutoAdjustment = SystemClock.elapsedRealtime();
          locked = false;regionActive=false;lockedFocus=-1;tracking=false;trackingLost=false;
          focusRequested = false;notifyPoint();
          repeating();
        });
  }

  private void notifyPoint(){
    boolean a=regionActive,l=locked,p=focusRequested;long selection=pointSelection;
    main.post(() -> listener.point(a,l,p,selection));
  }
  private boolean pointManualAe(){return regionActive&&!portraitMode&&get(active.chars,CameraCharacteristics.CONTROL_MAX_REGIONS_AE,0)==0;}
  private boolean emulateAeLock(){return locked&&(!portraitMode||!Boolean.TRUE.equals(active.chars.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)));}
  private int photoIso(){return wantedIso>0?wantedIso:wantedTime>0||emulateAeLock()||pointManualAe()?automaticIso:currentIso;}
  private long photoTime(){return wantedTime>0?wantedTime:wantedIso>0||emulateAeLock()||pointManualAe()?automaticTime:currentTime;}
  public void resetTouchPoint(long selection){
    if(handler==null)return;
    handler.post(() -> {pointSelection=selection;regionActive=false;focusRequested=false;locked=false;lockedFocus=-1;tracking=false;trackingLost=false;
      regionSamples=0;autoCorrectionSteps=0;lastAutoAdjustment=SystemClock.elapsedRealtime();notifyPoint();
      if(preview!=null&&session!=null&&manualFocus<0&&!farFocus)try{
        apply(preview,false);preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        session.capture(preview.build(),results,handler);
      }catch(Exception ignored){}repeating();});
  }
  public void focus(float normalizedX,float normalizedY,boolean lock,long selection){
    focusInternal(normalizedX,normalizedY,lock,selection,false);
  }
  public void track(float x,float y,boolean ignored,long selection){
    if(handler==null)return;
    handler.post(()->{
      if(closed||active==null)return;
      // Snapshot the current AUTO values at selection; AF continues while exposure stays fixed.
      int iso=photoIso();long time=photoTime();
      pointSelection=selection;regionActive=true;regionSamples=0;tracking=true;trackingLost=false;lastTrackingAf=0;
      focusX=Math.max(0,Math.min(1,x));focusY=Math.max(0,Math.min(1,y));
      automaticIso=iso;automaticTime=time;locked=true;lockedFocus=-1;focusRequested=false;notifyPoint();
      if(preview!=null&&session!=null&&manualFocus<0&&!farFocus)try{
        apply(preview,false);preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);session.capture(preview.build(),results,handler);
      }catch(Exception e){notifyError("추적 초점을 시작하지 못했습니다.");}
      repeating();
    });
  }
  private void focusInternal(float normalizedX,float normalizedY,boolean lock,long selection,boolean follow){
    if(handler==null||active==null)return;
    handler.post(() -> {
      if(closed||active==null)return;
      pointSelection=selection;regionActive=true;regionSamples=0;lockedFocus=-1;tracking=follow;trackingLost=false;lastTrackingAf=0;
      focusX=Math.max(0,Math.min(1,normalizedX));focusY=Math.max(0,Math.min(1,normalizedY));
      locked=false;focusRequested=lock;focusStart=SystemClock.elapsedRealtime();lastAutoAdjustment=focusStart;autoCorrectionSteps=0;
      // A tap/hold never writes wantedIso, wantedTime, ev, manualFocus or farFocus.
      if(lock&&wantedIso>0&&wantedTime>0&&(manualFocus>=0||farFocus)){focusRequested=false;locked=true;}
      notifyPoint();
      if(preview==null||session==null)return;
      try{
        apply(preview,false);
        if(!tracking&&manualFocus<0&&!farFocus){
          preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);session.capture(preview.build(),results,handler);
          preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_START);session.capture(preview.build(),results,handler);
        }
        preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_IDLE);session.setRepeatingRequest(preview.build(),results,handler);
      }catch(Exception e){notifyError("선택 영역을 맞추지 못했습니다: "+e.getMessage());}
    });
  }

  /** Update only the region. Manual ISO/time/EV/focus are deliberately untouched. */
  public void trackingPoint(float x,float y,boolean lost,long selection){
    if(handler==null)return;
    handler.post(()->{
      if(closed||active==null||!tracking||selection!=pointSelection||trackingLost)return;
      if(lost){
        trackingLost=true;focusRequested=false;
        if(!locked){automaticIso=photoIso();automaticTime=photoTime();}
        locked=true;
        Float distance=last==null?null:last.get(CaptureResult.LENS_FOCUS_DISTANCE);
        lockedFocus=distance==null?-1:distance;notifyPoint();repeating();return;
      }
      focusX=Math.max(0,Math.min(1,x));focusY=Math.max(0,Math.min(1,y));
      // Continuous AF follows the region. Older AUTO-only lenses need a bounded re-trigger.
      if(preview!=null&&session!=null&&manualFocus<0&&!farFocus
          &&!has(active.chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
          &&SystemClock.elapsedRealtime()-lastTrackingAf>700){
        lastTrackingAf=SystemClock.elapsedRealtime();
        try{apply(preview,false);preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);session.capture(preview.build(),results,handler);
          preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_START);session.capture(preview.build(),results,handler);
        }catch(Exception e){notifyError("추적 초점을 갱신하지 못했습니다.");}
      }
      repeating();
    });
  }

  public void metering(MeteringStats stats,long selection) {
    if (handler == null || active == null) return;
    handler.post(
        () -> {
          if(closed||active==null||last==null||selection!=pointSelection||stats==null||!stats.valid())return;
          if(regionActive)regionSamples++;
          frameStats=stats;frameMean=Math.max(.00001f,stats.mean);
          int photoIso=photoIso();long photoTime=photoTime();
          meter = ExposureMath.meter(frameMean, currentIso, currentTime, photoIso, photoTime);
          if (locked || portraitMode || pending != null || closed) return;
          if (!regionActive && wantedIso == 0 && wantedTime == 0 && last != null) {
            long now = SystemClock.elapsedRealtime();
            Integer ae = controlResult(CaptureResult.CONTROL_AE_STATE);
            boolean settled = Objects.equals(ae, CameraMetadata.CONTROL_AE_STATE_CONVERGED)
                || Objects.equals(ae, CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED);
            Range<Integer> range = active.chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            Integer applied = controlResult(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
            int requested = range == null ? 0 : Math.max(range.getLower(),
                Math.min(range.getUpper(), ev + autoCorrectionSteps));
            if (range != null && aeConvergence.ready(settled,ae==null,now,lastAutoAdjustment)
                && (applied == null || applied == requested)) {
              int next = ExposureMath.autoCorrection(stats, ev, evStep(),
                  autoCorrectionSteps, range.getLower(), range.getUpper());
              if (next != autoCorrectionSteps) {
                autoCorrectionSteps = next;
                lastAutoAdjustment = now;
                repeating();
              }
            }
          }
          if(pointManualAe()&&wantedIso==0&&wantedTime==0){
            double energy=ExposureMath.pointEnergy(currentIso,currentTime,stats,ev*evStep());
            float delta=(float)(Math.log(energy/Math.max(1.,(double)currentIso*currentTime))/Math.log(2));
            TouchExposure plan=TouchExposure.adjust(0,0,currentIso,currentTime,delta,active.minIso(),active.maxIso(),active.minTime(),active.maxTime());
            automaticIso=plan.iso;automaticTime=plan.time;repeating();
          }
          if ((wantedIso == 0) != (wantedTime == 0)
              && !locked
              && !portraitMode
              && pending == null) {
            double energy =
                regionActive?ExposureMath.pointEnergy(currentIso,currentTime,stats,ev*evStep()):ExposureMath.targetEnergy(currentIso,currentTime,stats,ev*evStep());
            if (wantedIso == 0)
              automaticIso = clampIso((int) Math.round(energy / Math.max(1, wantedTime)));
            else automaticTime = clampTime((long) (energy / Math.max(1, wantedIso)));
            repeating();
          }
        });
  }

  public float evStep() {
    Rational step =
        active == null
            ? null
            : active.chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
    return step == null ? 1 / 3f : step.floatValue();
  }

  public void brightness(float deltaEv){
    if(handler==null)return;handler.post(()->{if(active==null||wantedIso>0&&wantedTime>0)return;int change=TouchExposure.dragSteps(deltaEv,evStep());updateExposure(wantedIso,wantedTime,ev+change,apertureIndex);});
  }

  public void capture(File path, String id) {
    if (handler == null) return;
    handler.post(
        () -> {
          if (camera == null || session == null || pending != null) {
            notifyError("촬영 준비 중입니다.");
            return;
          }
          try {
            if(focusRequested){
              if(SystemClock.elapsedRealtime()-focusStart>3500){notifyError("고정값을 확인하지 못했습니다. 미리보기를 확인한 뒤 다시 촬영해주세요.");return;}
              handler.postDelayed(()->capture(path,id),80);return;
            }
            Pending p = new Pending();
            p.id = id;
            p.path = path;
            p.rotation = (active.sensorOrientation - rotation + 360) % 360;
            p.userEv=ev*evStep();p.autoEv=autoCorrectionSteps*evStep();p.stats=frameStats;p.point=regionActive;p.pointLocked=locked;p.tracking=tracking;p.trackingLost=trackingLost;
            p.portraitExpected = portraitMode && portraitReader != null;
            p.requestedCurve = portraitMode ? null : matchedCurve;
            p.started = SystemClock.elapsedRealtime();
            pending = p;
            main.post(() -> listener.busy(true));
            prepareCaptureFocus(p);
          } catch (Exception e) {
            failPending("촬영할 수 없습니다: " + e.getMessage());
          }
        });
  }

  private void prepareCaptureFocus(Pending p)throws CameraAccessException{
    if(!captureNeedsAF()){submitStill(p);return;}
    p.focusGate=new CaptureFocusGate(SystemClock.elapsedRealtime());p.waitingFocus=true;
    triggerCaptureFocus(p,false);
    handler.postDelayed(()->{if(pending==p&&p.waitingFocus)captureFocusDecision(p,p.focusGate.poll(SystemClock.elapsedRealtime()));},CaptureFocusGate.TIMEOUT_MS+20);
  }

  private boolean captureNeedsAF(){
    int[] modes=active.chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
    boolean autoLens=get(active.chars,CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE,0f)>0
        &&(has(modes,CameraMetadata.CONTROL_AF_MODE_AUTO)||has(modes,CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE));
    return CaptureFocusGate.required(portraitMode,manualFocus,farFocus,locked,tracking,trackingLost,lockedFocus>=0,autoLens);
  }

  private void triggerCaptureFocus(Pending p,boolean retry)throws CameraAccessException{
    if(pending!=p||closed||session==null||preview==null)return;
    apply(preview,false);
    // CONTINUOUS_PICTURE may immediately lock an unfocused passive result. Retry
    // with an actual AUTO scan instead of repeatedly locking that same bad position.
    if(retry&&has(active.chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),CameraMetadata.CONTROL_AF_MODE_AUTO))
      preview.set(CaptureRequest.CONTROL_AF_MODE,CameraMetadata.CONTROL_AF_MODE_AUTO);
    p.afMode=preview.get(CaptureRequest.CONTROL_AF_MODE);
    if(retry){preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);session.capture(preview.build(),results,handler);}
    preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_START);p.afTriggered=true;
    session.capture(preview.build(),new CameraCaptureSession.CaptureCallback(){
      @Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest q,TotalCaptureResult r){
        if(pending!=p||!p.waitingFocus||closed||session!=s)return;
        p.focusGate.triggered(r.getFrameNumber());
        results.onCaptureCompleted(s,q,r);
      }
      @Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest q,CaptureFailure failure){
        if(pending==p&&p.waitingFocus)failPending("자동초점을 시작하지 못했습니다. 다시 촬영해주세요.");
      }
    },handler);
    preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
    session.setRepeatingRequest(preview.build(),results,handler);
  }

  private void captureFocusDecision(Pending p,CaptureFocusGate.Decision decision){
    if(pending!=p||!p.waitingFocus||closed)return;
    try{
      if(!captureNeedsAF()){p.waitingFocus=false;p.afTriggered=false;p.focusStatus="user-or-fixed";submitStill(p);return;}
      if(decision==CaptureFocusGate.Decision.WAIT)return;
      if(decision==CaptureFocusGate.Decision.RETRY){triggerCaptureFocus(p,true);return;}
      p.waitingFocus=false;
      if(decision==CaptureFocusGate.Decision.FAILED){failPending("초점을 맞추지 못했습니다. 피사체를 눌러 다시 촬영해주세요.");return;}
      p.focusStatus=decision==CaptureFocusGate.Decision.UNREPORTED?"af-state-unreported":"af-confirmed";
      submitStill(p);
    }catch(Exception e){failPending("초점 확인 후 촬영을 시작하지 못했습니다: "+e.getMessage());}
  }

  private void releaseCaptureFocus(Pending p){
    if(p==null||!p.afTriggered)return;p.afTriggered=false;
    if(closed||session==null||preview==null||manualFocus>=0||farFocus||locked&&!tracking)return;
    try{
      apply(preview,false);preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
      session.capture(preview.build(),results,handler);
      // AUTO-only point focus needs a new scan; continuous AF resumes on CANCEL.
      if(regionActive&&!tracking){preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_START);session.capture(preview.build(),results,handler);}
      preview.set(CaptureRequest.CONTROL_AF_TRIGGER,CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
      session.setRepeatingRequest(preview.build(),results,handler);
    }catch(CameraAccessException|IllegalStateException ignored){/* Closing/disconnected session; next open restores AF. */}
  }

  private void submitStill(Pending p){
    if(pending!=p||p.stillSubmitted||closed||camera==null||session==null)return;
    p.stillSubmitted=true;p.waitingFocus=false;
    try{
            CaptureRequest.Builder b =
                camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            apply(b, true);
            if (!portraitMode && last != null) {
              // Freeze the visible preview's neutral color/brightness for this still.
              // Manual/long exposures keep their requested photo duration.
              b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
              int photoIso=photoIso();
              long photoTime=photoTime();
              b.set(CaptureRequest.SENSOR_SENSITIVITY, clampIso(photoIso));
              b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampTime(photoTime));
              b.set(CaptureRequest.SENSOR_FRAME_DURATION, Math.max(clampTime(photoTime), 33333333L));
              Integer boost = last.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
              if (boost != null) b.set(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, boost);
              RggbChannelVector gains = last.get(CaptureResult.COLOR_CORRECTION_GAINS);
              ColorSpaceTransform matrix = last.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
              if (gains != null && matrix != null && !flash()) {
                b.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
                b.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
                b.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains);
                b.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, matrix);
              }
            }
            // Keep the AF mode that actually completed START; changing modes resets AF state.
            if(p.afTriggered)b.set(CaptureRequest.CONTROL_AF_MODE,p.afMode);
            b.addTarget(rawReader.getSurface());
            b.addTarget(surface);
            Integer requestedBoost = b.get(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST);
            if (requestedBoost != null) p.requestedBoost = requestedBoost;
            if (p.portraitExpected) b.addTarget(portraitReader.getSurface());
            session.capture(
                b.build(),
                new CameraCaptureSession.CaptureCallback() {
                  @Override
                  public void onCaptureCompleted(
                      CameraCaptureSession s, CaptureRequest q, TotalCaptureResult r) {
                    if (pending != p) return;
                    p.result = effective(r);
                    releaseCaptureFocus(p);
                    finishPending();
                  }

                  @Override
                  public void onCaptureFailed(
                      CameraCaptureSession s, CaptureRequest q, CaptureFailure f) {
                    if (pending == p) failPending("RAW 촬영 실패: " + f.getReason());
                  }
                },
                handler);
            handler.postDelayed(
                () -> {
                  if (pending == p) failPending("촬영 시간이 초과되었습니다. 원본 수신을 확인해주세요.");
                },
                Math.max(10000, wantedTime / 1000000 + 10000));
    }catch(Exception e){failPending("촬영할 수 없습니다: "+e.getMessage());}
  }

  private void finishPending() {
    Pending p = pending;
    if (p == null
        || p.image == null
        || p.result == null
        || (p.portraitExpected && p.portrait == null)) return;
    Long timestamp = p.result.get(CaptureResult.SENSOR_TIMESTAMP);
    if (timestamp != null && p.image.getTimestamp() != timestamp) {
      failPending("RAW와 촬영 정보의 시간이 일치하지 않아 저장을 중단했습니다.");
      return;
    }
    try {
      p.path.getParentFile().mkdirs();
      File tmp = new File(p.path.getPath() + ".part");
      try (DngCreator creator = new DngCreator(active.chars, p.result);
          OutputStream out = new FileOutputStream(tmp)) {
        creator.setOrientation(
            p.rotation == 90 ? 6 : p.rotation == 180 ? 3 : p.rotation == 270 ? 8 : 1);
        creator.writeImage(out, p.image);
      }
      DngCrop.applyLandscape(tmp,p.rotation);
      if (!tmp.renameTo(p.path)) throw new IOException("RAW 원본 파일 저장 실패");
      JSONObject meta =
          RawMetadata.capture(
              active.chars, p.result, p.image.getWidth(), p.image.getHeight(), p.rotation,
              p.requestedCurve, p.requestedBoost);
      meta.put("captureRatio",1.5);meta.put("dngDefaultCrop","3:2");meta.put("exposureProfile","neutral-histogram-v1");meta.put("meteringMode",p.point?"selected-region":"whole-frame");meta.put("pointLocked",p.pointLocked);meta.put("trackingMode",p.tracking?"selected-appearance":"off");meta.put("trackingLost",p.trackingLost);
      meta.put("requestedUserEV",p.userEv);meta.put("requestedAutoEV",p.autoEv);
      if(p.stats!=null){meta.put("neutralMean",p.stats.mean);meta.put("neutralMedian",p.stats.median);
        meta.put("neutralP75",p.stats.upper);meta.put("neutralHighlightP98",p.stats.highlight);meta.put("neutralClippedFraction",p.stats.clipped);}
      meta.put("captureFocus",p.focusStatus);
      meta.put("lens", active.label);
      meta.put("physicalCameraId", active.physicalId);
      Bitmap portrait = p.portrait == null ? null : yuv(p.portrait);
      p.image.close();
      p.image = null;
      if (p.portrait != null) {
        p.portrait.close();
        p.portrait = null;
      }
      pending = null;
      main.post(
          () -> {
            listener.busy(false);
            listener.captured(p.path, meta, portrait);
          });
    } catch (Throwable e) {
      failPending(
          "RAW 원본: "
              + p.path.getName()
              + " · "
              + (e instanceof OutOfMemoryError ? "메모리가 부족합니다." : e.getMessage()));
    }
  }

  private static Bitmap yuv(Image im) {
    int w = im.getWidth(), h = im.getHeight();
    Image.Plane[] p = im.getPlanes();
    ByteBuffer y = p[0].getBuffer(), u = p[1].getBuffer(), v = p[2].getBuffer();
    int[] px = new int[w * h];
    for (int j = 0; j < h; j++)
      for (int i = 0; i < w; i++) {
        int yy = (y.get(j * p[0].getRowStride() + i * p[0].getPixelStride()) & 255) - 16,
            uu = (u.get(j / 2 * p[1].getRowStride() + i / 2 * p[1].getPixelStride()) & 255) - 128,
            vv = (v.get(j / 2 * p[2].getRowStride() + i / 2 * p[2].getPixelStride()) & 255) - 128;
        int r = Math.max(0, Math.min(255, (298 * yy + 409 * vv + 128) >> 8)),
            g = Math.max(0, Math.min(255, (298 * yy - 100 * uu - 208 * vv + 128) >> 8)),
            b = Math.max(0, Math.min(255, (298 * yy + 516 * uu + 128) >> 8));
        px[j * w + i] = 0xff000000 | (r << 16) | (g << 8) | b;
      }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
  }

  private void failPending(String error) {
    if(pending!=null){pending.waitingFocus=false;releaseCaptureFocus(pending);}
    if (pending != null) {
      if (pending.image != null) pending.image.close();
      if (pending.portrait != null) pending.portrait.close();
      pending = null;
    }
    main.post(() -> listener.busy(false));
    notifyError(error);
  }

  private void closeHardware() {
    if (session != null) {
      session.close();
      session = null;
    }
    if (camera != null) {
      camera.close();
      camera = null;
    }
    if (rawReader != null) {
      rawReader.close();
      rawReader = null;
    }
    if (portraitReader != null) {
      portraitReader.close();
      portraitReader = null;
    }
    preview = null;
    last = null;lastLogical=null;frameStats=null;aeConvergence.reset();
    if (pending != null) failPending("카메라 전환으로 촬영이 중단되었습니다.");
  }

  public void shutdown() {
    close();
    if (thread != null) thread.quitSafely();
  }

  public void close() {
    closed = true;
    generation++;
    if (handler != null) handler.post(this::closeHardware);
  }
}
