package com.hoonjay.filmroom.camera;

import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.util.*;
import com.hoonjay.filmroom.core.*;
import com.hoonjay.filmroom.storage.*;
import org.json.*;

public final class RawMetadata {
  /** Metadata bridge for display only. Does not modify a Camera2 request or saved RAW. */
  public static PreviewColorModel previewModel(CameraCharacteristics c,CaptureResult r,TonemapCurve requested){
    try{
      RggbChannelVector gains=r.get(CaptureResult.COLOR_CORRECTION_GAINS);
      ColorSpaceTransform isp=r.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
      TonemapCurve curve=r.get(CaptureResult.TONEMAP_CURVE);if(curve==null)curve=requested;
      if(gains==null||isp==null||curve==null)return null;
      double green=(gains.getGreenEven()+gains.getGreenOdd())*.5;
      double[] wb={gains.getRed(),green,gains.getBlue()},neutral=new double[3];
      Rational[] point=r.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
      if(point!=null&&point.length==3){for(int i=0;i<3;i++)neutral[i]=point[i].doubleValue();}
      else{for(int i=0;i<3;i++)neutral[i]=green/wb[i];}
      Number light1=c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1),light2=c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2);
      DngColorProfile profile=new DngColorProfile(neutral,
          coefficients(c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)),coefficients(c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)),
          coefficients(c.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1)),coefficients(c.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)),
          coefficients(c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)),coefficients(c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)),null,
          light1==null?0:light1.intValue(),light2==null?0:light2.intValue(),0);
      Integer boost=r.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
      float[] matrix=profile.previewTransform(coefficients(isp),wb,boost==null?1:boost/100.);
      float[][] curves=new float[3][];
      for(int channel=0;channel<3;channel++){curves[channel]=new float[curve.getPointCount(channel)*2];curve.copyColorCurve(channel,curves[channel],0);}
      return new PreviewColorModel(curves,matrix);
    }catch(IllegalArgumentException|NullPointerException e){return null;}
  }
  private static double[] coefficients(ColorSpaceTransform transform){if(transform==null)return null;double[] a=new double[9];for(int y=0;y<3;y++)for(int x=0;x<3;x++)a[y*3+x]=transform.getElement(x,y).doubleValue();return a;}

  public static JSONObject capture(
      CameraCharacteristics c, CaptureResult r, int width, int height, int rotation,
      TonemapCurve requestedCurve, int requestedBoost)
      throws JSONException {
    JSONObject o = new JSONObject();
    o.put("width", width);
    o.put("height", height);
    o.put("rotation", rotation);
    Float focusDistance=r.get(CaptureResult.LENS_FOCUS_DISTANCE);
    Integer afState=r.get(CaptureResult.CONTROL_AF_STATE),lensState=r.get(CaptureResult.LENS_STATE);
    if(focusDistance!=null&&Float.isFinite(focusDistance)&&focusDistance>=0)o.put("focusDiopters",focusDistance);
    if(afState!=null)o.put("afState",afState);
    if(lensState!=null)o.put("lensState",lensState);
    Integer flash=r.get(CaptureResult.FLASH_STATE);if(flash!=null)o.put("flashState",flash);
    Float nd=r.get(CaptureResult.LENS_FILTER_DENSITY);
    if(nd!=null)o.put("ndStops",nd);
    Integer arrangement = c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
    int a = arrangement == null ? 0 : arrangement;
    int[][] patterns = {{0, 1, 2, 3}, {1, 0, 3, 2}, {1, 3, 0, 2}, {3, 1, 2, 0}};
    if (a < 0 || a > 3) throw new IllegalArgumentException("지원하지 않는 RAW 센서 배열입니다.");
    JSONArray ca = new JSONArray();
    for (int v : patterns[a]) ca.put(v);
    o.put("cfa", ca);
    float[] black = r.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
    if (black == null) {
      black = new float[4];
      BlackLevelPattern b = c.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
      for (int y = 0; y < 2; y++)
        for (int x = 0; x < 2; x++) black[y * 2 + x] = b == null ? 0 : b.getOffsetForIndex(x, y);
    }
    o.put("black", RecipeJson.arr(black));
    Integer white = r.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
    if (white == null) white = c.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
    if (white == null) throw new IllegalArgumentException("RAW 화이트 레벨이 없습니다.");
    o.put("white", white);
    RggbChannelVector gains = r.get(CaptureResult.COLOR_CORRECTION_GAINS);
    ColorSpaceTransform transform = r.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
    if (gains == null || transform == null)
      throw new IllegalArgumentException("이 렌즈는 RAW 현상에 필요한 색상 보정 정보를 제공하지 않습니다. 원본은 보존됩니다.");
    o.put(
        "gains",
        RecipeJson.arr(
            new float[] {
              gains.getRed(), gains.getGreenEven(), gains.getGreenOdd(), gains.getBlue()
            }));
    float[] matrix = new float[9];
    for (int y = 0; y < 3; y++)
      for (int x = 0; x < 3; x++) matrix[y * 3 + x] = transform.getElement(x, y).floatValue();
    o.put("matrix", RecipeJson.arr(matrix));
    Integer boost = r.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
    o.put("postRawBoost", (boost == null ? requestedBoost : boost) / 100.0);
    TonemapCurve curve = r.get(CaptureResult.TONEMAP_CURVE);
    if (curve == null) curve = requestedCurve;
    if (curve != null) {
      JSONArray curves = new JSONArray();
      for (int channel = 0; channel < 3; channel++) {
        float[] points = new float[curve.getPointCount(channel) * 2];
        curve.copyColorCurve(channel, points, 0);
        curves.put(RecipeJson.arr(points));
      }
      o.put("toneCurves", curves);
    }
    o.put("colorPipeline", 3);
    Size pixel = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
    Rect active = c.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
    if (active == null) active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    int l = 0, t = 0, w = width, h = height;
    if (active != null && pixel != null) {
      float sx = width / (float) pixel.getWidth(), sy = height / (float) pixel.getHeight();
      l = Math.max(0, Math.round(active.left * sx));
      t = Math.max(0, Math.round(active.top * sy));
      w = Math.min(width - l, Math.round(active.width() * sx));
      h = Math.min(height - t, Math.round(active.height() * sy));
    }
    o.put("activeLeft", l);
    o.put("activeTop", t);
    o.put("activeWidth", w);
    o.put("activeHeight", h);
    LensShadingMap shading = r.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
    if (shading != null) {
      float[] map = new float[shading.getGainFactorCount()];
      shading.copyGainFactors(map, 0);
      o.put("shadeWidth", shading.getColumnCount());
      o.put("shadeHeight", shading.getRowCount());
      o.put("shading", RecipeJson.arr(map));
    }
    Integer iso = r.get(CaptureResult.SENSOR_SENSITIVITY);
    Long time = r.get(CaptureResult.SENSOR_EXPOSURE_TIME);
    Float f = r.get(CaptureResult.LENS_APERTURE);
    o.put("iso", iso);
    o.put("shutterNs", time);
    o.put("aperture", f);
    return o;
  }

  public static void apply(RawFrame f, JSONObject o) throws JSONException {
    // Native DNG calibration is authoritative. A HAL's display-only tonemap curve
    // and post-RAW gain must not be interpreted as part of the sensor calibration.
    // This also repairs old captures when re-developed, without changing their files.
    if (!f.dngCalibrated) {
    JSONArray a = o.getJSONArray("cfa");
    for (int i = 0; i < 4; i++) f.cfa[i] = a.getInt(i);
    f.black = RecipeJson.floats(o.optJSONArray("black"), f.black);
    f.gains = RecipeJson.floats(o.optJSONArray("gains"), f.gains);
    f.matrix = RecipeJson.floats(o.optJSONArray("matrix"), f.matrix);
    f.white = o.getInt("white");
    f.postRawBoost = (float) o.optDouble("postRawBoost", 1);
    JSONArray curves = o.optJSONArray("toneCurves");
    if (curves != null && curves.length() == 3) {
      f.toneCurves = new float[3][];
      for (int c = 0; c < 3; c++) {
        JSONArray curve = curves.getJSONArray(c);
        f.toneCurves[c] = new float[curve.length()];
        for (int i = 0; i < curve.length(); i++) f.toneCurves[c][i] = (float) curve.getDouble(i);
      }
    }
    f.activeLeft = o.optInt("activeLeft", 0);
    f.activeTop = o.optInt("activeTop", 0);
    f.activeWidth = o.optInt("activeWidth", f.width);
    f.activeHeight = o.optInt("activeHeight", f.height);
    }
    if(f.dngShading)return;
    f.shadeWidth = o.optInt("shadeWidth", 0);
    f.shadeHeight = o.optInt("shadeHeight", 0);
    JSONArray sh = o.optJSONArray("shading");
    if (sh != null) {
      f.shading = new float[sh.length()];
      for (int i = 0; i < sh.length(); i++) f.shading[i] = (float) sh.getDouble(i);
    }
  }
}
