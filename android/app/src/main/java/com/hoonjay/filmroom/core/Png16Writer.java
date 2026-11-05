package com.hoonjay.filmroom.core;

import java.io.*;
import java.util.zip.*;

/** Streaming, true 16-bit RGB PNG, with an explicit sRGB profile marker. */
public final class Png16Writer implements AutoCloseable {
  private final DataOutputStream output;
  private final Deflater deflater = new Deflater(6);
  private final byte[] compressed = new byte[65536];
  private final int width, height;
  private int rows;

  public Png16Writer(OutputStream out, int width, int height) throws IOException {
    this.width = width;
    this.height = height;
    output = new DataOutputStream(out);
    output.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    DataOutputStream d = new DataOutputStream(b);
    d.writeInt(width);
    d.writeInt(height);
    d.writeByte(16);
    d.writeByte(2);
    d.writeByte(0);
    d.writeByte(0);
    d.writeByte(0);
    chunk("IHDR", b.toByteArray());
    chunk("sRGB", new byte[] {0});
    chunk("gAMA", new byte[] {0, 0, (byte) 177, (byte) 143});
  }

  private void chunk(String type, byte[] data) throws IOException {
    byte[] name = type.getBytes("US-ASCII");
    output.writeInt(data.length);
    output.write(name);
    output.write(data);
    CRC32 c = new CRC32();
    c.update(name);
    c.update(data);
    output.writeInt((int) c.getValue());
  }

  public void row(float[] rgb) throws IOException {
    if (rows >= height || rgb.length != width * 3) throw new IOException("PNG row mismatch");
    byte[] b = new byte[width * 6 + 1];
    int j = 1;
    for (float f : rgb) {
      int v = Math.round(ColorMath.clamp(f) * 65535);
      b[j++] = (byte) (v >>> 8);
      b[j++] = (byte) v;
    }
    deflater.setInput(b);
    while (!deflater.needsInput()) drain(false);
    rows++;
  }

  private void drain(boolean finish) throws IOException {
    int n = deflater.deflate(compressed, 0, compressed.length);
    if (n > 0) {
      byte[] b = new byte[n];
      System.arraycopy(compressed, 0, b, 0, n);
      chunk("IDAT", b);
    } else if (finish && !deflater.finished()) throw new IOException("PNG compression stalled");
  }

  public void close() throws IOException {
    try {
      if (rows != height) throw new IOException("Incomplete PNG");
      deflater.finish();
      while (!deflater.finished()) drain(true);
      chunk("IEND", new byte[0]);
      output.flush();
    } finally {
      deflater.end();
      output.close();
    }
  }
}
