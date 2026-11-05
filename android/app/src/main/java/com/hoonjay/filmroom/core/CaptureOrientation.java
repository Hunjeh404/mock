package com.hoonjay.filmroom.core;

/** Physical handset attitude, independent of the portrait-locked menu/display. */
public final class CaptureOrientation {
  private int quadrant;

  public int quadrant() {
    return quadrant;
  }

  public boolean isLandscape() {
    return isLandscape(quadrant);
  }

  public static boolean isLandscape(int degrees) {
    int q = PreviewGeometry.normalize(degrees);
    return q == 90 || q == 270;
  }

  public void update(int degrees) {
    if (degrees < 0) return;
    int angle = PreviewGeometry.normalize(degrees);
    int difference = Math.abs(angle - quadrant);
    difference = Math.min(difference, 360 - difference);
    // Hysteresis prevents flipping near 45-degree boundaries. Flat/unknown holds last orientation.
    if (difference >= 60) quadrant = ((angle + 45) / 90 * 90) % 360;
  }

  public int engineRotation() {
    return PreviewGeometry.normalize(-quadrant);
  }

  public int jpegRotation(int sensor) {
    return PreviewGeometry.normalize(sensor + quadrant);
  }
}
