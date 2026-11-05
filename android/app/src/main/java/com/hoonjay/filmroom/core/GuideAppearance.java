package com.hoonjay.filmroom.core;

/** Stable palette shared by persistent guide selection and the appearance controls. */
public final class GuideAppearance {
  public static final String[] NAMES = {"테마색", "흰색", "검은색", "빨간색", "파란색", "노란색", "초록색"};
  public static final int[] COLORS = {
    0xfff5a36b, 0xffffffff, 0xff000000, 0xffff0000, 0xff0000ff, 0xffffff00, 0xff00ff00
  };

  private GuideAppearance() {}

  public static int opacity(int value) {
    return Math.max(0, Math.min(100, value));
  }

  public static int color(int value) {
    for (int c : COLORS) if (c == value) return value;
    return COLORS[0];
  }

  public static int index(int value) {
    for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == value) return i;
    return 0;
  }
}
