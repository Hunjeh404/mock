package com.hoonjay.filmroom.guides;

import android.graphics.*;
import org.json.*;

/** Minimal vector renderer. All guide geometry lives in assets, never in the camera engine. */
public final class GuideRenderer {
  private final GuideCatalog catalog;
  private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

  public GuideRenderer(GuideCatalog c) {
    catalog = c;
  }

  public void draw(
      Canvas canvas, RectF frame, GuideCatalog.Selection s, int rotation, boolean miniature) {
    if (s == null || !s.visible || (!miniature && s.opacity == 0) || frame.width() <= 0) return;
    canvas.save();
    canvas.clipRect(frame);
    int q = ((rotation % 360) + 360) % 360;
    canvas.rotate((360 - q) % 360, frame.centerX(), frame.centerY());
    boolean swap = q == 90 || q == 270;
    float w = swap ? frame.height() : frame.width(), h = swap ? frame.width() : frame.height();
    float left = frame.centerX() - w / 2, top = frame.centerY() - h / 2;
    JSONArray shapes = catalog.drawing(s, w > h);
    if (shapes == null) {
      canvas.restore();
      return;
    }
    canvas.translate(left, top);
    float unit = Math.min(w, h);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    for (int i = 0; i < shapes.length(); i++) {
      JSONObject a = shapes.optJSONObject(i);
      if (a == null) continue;
      String type = a.optString("type"), style = a.optString("style");
      if (miniature && type.equals("text")) continue;
      p.setStyle(Paint.Style.STROKE);
      p.setColor(s.color);
      p.setAlpha(
          miniature
              ? 190
              : Math.round(255 * s.opacity / 100f * (style.equals("soft") ? .32f : 1f)));
      p.setStrokeWidth(Math.max(1, unit * (miniature ? .008f : .003f)));
      p.setPathEffect(
          style.equals("soft")
              ? new DashPathEffect(new float[] {unit * .012f, unit * .018f}, 0)
              : null);
      p.setShadowLayer(miniature ? 0 : unit * .005f, 0, 1, Math.round(90 * s.opacity / 100f) << 24);
      float x = (float) a.optDouble("x") * w, y = (float) a.optDouble("y") * h;
      if (type.equals("rect")) {
        float r = (float) a.optDouble("radius") * unit;
        canvas.drawRoundRect(
            new RectF(
                x, y, x + (float) a.optDouble("width") * w, y + (float) a.optDouble("height") * h),
            r,
            r,
            p);
      } else if (type.equals("ellipse")) {
        float rx = (float) a.optDouble("rx") * w, ry = (float) a.optDouble("ry") * h;
        canvas.drawOval(new RectF(x - rx, y - ry, x + rx, y + ry), p);
      } else if (type.equals("text")) {
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(miniature ? 220 : Math.round(255 * s.opacity / 100f));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        p.setTextSize(unit * .024f);
        canvas.drawText(a.optString("text"), x, y, p);
      } else if (type.equals("path")) {
        Path path = new Path();
        JSONArray commands = a.optJSONArray("commands");
        if (commands == null) continue;
        for (int j = 0; j < commands.length(); j++) {
          JSONArray c = commands.optJSONArray(j);
          if (c == null) continue;
          switch (c.optString(0)) {
            case "M":
              path.moveTo(v(c, 1, w), v(c, 2, h));
              break;
            case "L":
              path.lineTo(v(c, 1, w), v(c, 2, h));
              break;
            case "Q":
              path.quadTo(v(c, 1, w), v(c, 2, h), v(c, 3, w), v(c, 4, h));
              break;
            case "C":
              path.cubicTo(v(c, 1, w), v(c, 2, h), v(c, 3, w), v(c, 4, h), v(c, 5, w), v(c, 6, h));
              break;
            case "Z":
              path.close();
              break;
          }
        }
        canvas.drawPath(path, p);
      }
    }
    p.clearShadowLayer();
    p.setPathEffect(null);
    canvas.restore();
  }

  private float v(JSONArray a, int i, float scale) {
    return (float) a.optDouble(i) * scale;
  }
}
