package com.hoonjay.filmroom.core;

/** Rotate first, then use a landscape 3:2 center crop without resampling. */
public final class LandscapeSource implements Developer.Source {
  private final Developer.Source source;
  private final Geometry geometry;

  public LandscapeSource(Developer.Source input, int rotation) {
    source = input;
    geometry = Geometry.landscape(input.width(), input.height(), rotation, 1.5);
  }

  public int width() {
    return geometry.width;
  }

  public int height() {
    return geometry.height;
  }

  public void rgb(int x, int y, float[] out) {
    source.rgb(geometry.sourceX(x, y), geometry.sourceY(x, y), out);
  }
}
