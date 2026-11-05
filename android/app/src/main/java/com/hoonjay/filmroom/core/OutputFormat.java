package com.hoonjay.filmroom.core;

/** Persisted separately from the non-destructive film recipe. */
public enum OutputFormat {
  JPEG_MAX("jpeg_max", "JPG", "jpg", "image/jpeg"),
  PNG16("png16", "PNG · 16비트 무손실", "png", "image/png"),
  JPEG_SMALL("jpeg_10mb", "JPG", "jpg", "image/jpeg"),
  RAW_ONLY("raw", "RAW · 원본 DNG", "dng", "image/x-adobe-dng");

  public final String id, label, extension, mime;
  public static final long MAX_BYTES = 10_000_000L;

  OutputFormat(String id, String label, String extension, String mime) {
    this.id = id; this.label = label; this.extension = extension; this.mime = mime;
  }

  /** Hidden legacy capped JPEG is retained solely to recover already queued old jobs. */
  public static OutputFormat[] captureOptions(){return new OutputFormat[]{JPEG_MAX,PNG16,RAW_ONLY};}
  public static OutputFormat forCapture(String id){OutputFormat value=fromId(id);return value==JPEG_SMALL?JPEG_MAX:value;}

  public static OutputFormat fromId(String id) {
    for (OutputFormat format : values()) if (format.id.equals(id)) return format;
    return JPEG_MAX;
  }
}
