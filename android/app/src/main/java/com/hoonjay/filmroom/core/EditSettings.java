package com.hoonjay.filmroom.core;
/** Copyable look plus optional normalized geometry; output format/size remain photo-specific. */
public final class EditSettings {
  public final Recipe recipe;
  public final double ratio;
  public final EditTransform transform;
  public EditSettings(Recipe r,double ratio,EditTransform transform){PresetRules.validate(r);transform.validate();if(!Double.isFinite(ratio)||ratio<1||ratio>3)throw new IllegalArgumentException("화면 비율이 올바르지 않습니다.");this.recipe=r.copy();this.ratio=ratio;this.transform=transform.copy();}
}
