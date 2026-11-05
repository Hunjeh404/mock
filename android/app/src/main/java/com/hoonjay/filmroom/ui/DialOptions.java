package com.hoonjay.filmroom.ui;

import com.hoonjay.filmroom.camera.CameraEngine;
import java.util.*;

/** One capability-derived list drives both wheel labels and actual capture settings. */
public final class DialOptions {
  public static List<Integer> iso(CameraEngine.Lens lens) {
    List<Integer> a = new ArrayList<>();
    a.add(0);
    a.add(lens.minIso());
    for (int n : new int[] {50, 64, 100, 200, 400, 800, 1600, 3200, 6400, 12800})
      if (n > lens.minIso() && n < lens.maxIso()) a.add(n);
    if (lens.maxIso() != lens.minIso()) a.add(lens.maxIso());
    return a;
  }

  public static List<Long> shutter(CameraEngine.Lens lens) {
    List<Long> a = new ArrayList<>();
    a.add(0L);
    a.add(lens.minTime());
    for (long n :
        new long[] {
          125000L,
          250000L,
          500000L,
          1000000L,
          2000000L,
          4000000L,
          8000000L,
          16666667L,
          33333333L,
          66666667L,
          125000000L,
          250000000L,
          500000000L,
          1000000000L,
          2000000000L,
          4000000000L,
          8000000000L,
          10000000000L,
          15000000000L,
          20000000000L,
          30000000000L
        }) if (n > lens.minTime() && n < lens.maxTime()) a.add(n);
    if (lens.maxTime() != lens.minTime()) a.add(lens.maxTime());
    return a;
  }

  public static int nearest(List<? extends Number> a, double value) {
    int index = 0;
    double best = Double.MAX_VALUE;
    for (int i = 0; i < a.size(); i++) {
      double d = Math.abs(a.get(i).doubleValue() - value);
      if (d < best) {
        best = d;
        index = i;
      }
    }
    return index;
  }

  public static String[] isoNeighbors(CameraEngine.Lens lens, int value) {
    List<Integer> a = iso(lens);
    int i = nearest(a, value);
    return new String[] {
      i > 0 ? labelIso(a.get(i - 1)) : "", i + 1 < a.size() ? labelIso(a.get(i + 1)) : ""
    };
  }

  public static String[] shutterNeighbors(CameraEngine.Lens lens, long value) {
    List<Long> a = shutter(lens);
    int i = nearest(a, value);
    return new String[] {
      i > 0 ? CameraOverlay.time(a.get(i - 1)) : "",
      i + 1 < a.size() ? CameraOverlay.time(a.get(i + 1)) : ""
    };
  }

  private static String labelIso(int n) {
    return n == 0 ? "AUTO" : String.valueOf(n);
  }
}
