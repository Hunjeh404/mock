package com.hoonjay.filmroom.core;

/** Crop after rotation: output frame and saved result share the exact center crop. */
public final class Geometry {
  public final int sourceWidth,
      sourceHeight,
      rotation,
      orientedWidth,
      orientedHeight,
      width,
      height,
      left,
      top;

  public Geometry(int sourceWidth, int sourceHeight, int rotation, double longShortRatio) {
    this.sourceWidth = sourceWidth;
    this.sourceHeight = sourceHeight;
    this.rotation = ((rotation % 360) + 360) % 360;
    boolean swap = this.rotation == 90 || this.rotation == 270;
    orientedWidth = swap ? sourceHeight : sourceWidth;
    orientedHeight = swap ? sourceWidth : sourceHeight;
    double ratio = orientedWidth >= orientedHeight ? longShortRatio : 1 / longShortRatio;
    int w = orientedWidth, h = orientedHeight;
    if(Math.abs(w/(double)h-ratio)<1e-9){} // exact full frame must not lose a row to rounding
    else if (w / (double) h > ratio) w = (int) Math.floor(h * ratio);
    else h = (int) Math.floor(w / ratio);
    if(Math.abs(longShortRatio-1.5)<1e-9){int unit=orientedWidth>=orientedHeight?Math.min(w/3,h/2):Math.min(w/2,h/3);if(unit>=1){w=(orientedWidth>=orientedHeight?3:2)*unit;h=(orientedWidth>=orientedHeight?2:3)*unit;}}
    width = Math.max(1, w);
    height = Math.max(1, h);
    left = (orientedWidth - width) / 2;
    top = (orientedHeight - height) / 2;
  }

  public int sourceX(int ox, int oy) {
    int x = left + ox, y = top + oy;
    switch (rotation) {
      case 90:
        return y;
      case 180:
        return sourceWidth - 1 - x;
      case 270:
        return sourceWidth - 1 - y;
      default:
        return x;
    }
  }

  public int sourceY(int ox, int oy) {
    int x = left + ox, y = top + oy;
    switch (rotation) {
      case 90:
        return sourceHeight - 1 - x;
      case 180:
        return sourceHeight - 1 - y;
      case 270:
        return x;
      default:
        return y;
    }
  }
}
