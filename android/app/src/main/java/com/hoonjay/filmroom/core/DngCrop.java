package com.hoonjay.filmroom.core;

import java.io.*;
import java.util.*;

/**
 * Set DNG DefaultCropOrigin/Size without rewriting or dropping any sensor samples. TIFF entry/pixel
 * offsets stay absolute. Only the selected IFD link is replaced.
 */
public final class DngCrop {
  private DngCrop() {}

  private static final class Entry {
    int tag, type;
    long count, value, position;
    byte[] bytes;
  }

  private static final class Ifd {
    long offset, parent, next;
    List<Entry> entries = new ArrayList<>();
  }

  public static void apply(File file) throws IOException {
    try (RandomAccessFile f = new RandomAccessFile(file, "rw")) {
      int a = f.readUnsignedByte(), b = f.readUnsignedByte();
      boolean le = a == 'I' && b == 'I';
      if (!le && !(a == 'M' && b == 'M')) throw new IOException("DNG byte order");
      if (u16(f, le) != 42) throw new IOException("Unsupported DNG TIFF");
      long first = u32(f, le);
      List<Ifd> found = new ArrayList<>();
      walk(f, le, first, 4, new HashSet<>(), found);
      Ifd raw = null;
      for (Ifd i : found) {
        Entry photo = entry(i, 262);
        if (photo != null && values(f, le, photo)[0] == 32803) {
          raw = i;
          break;
        }
      }
      if (raw == null) throw new IOException("CFA RAW IFD not found");
      long width = scalar(f, le, raw, 256), height = scalar(f, le, raw, 257);
      long aw = width, ah = height;
      Entry active = entry(raw, 50829);
      if (active != null) {
        long[] area = values(f, le, active);
        if (area.length == 4) {
          aw = area[3] - area[1];
          ah = area[2] - area[0];
        }
      }
      if (aw < 6 || ah < 6 || aw > width || ah > height)
        throw new IOException("Invalid DNG ActiveArea");
      long unit = aw >= ah ? Math.min(aw / 3, ah / 2) : Math.min(aw / 2, ah / 3);
      long cw = (aw >= ah ? 3 : 2) * unit,
          ch = (aw >= ah ? 2 : 3) * unit,
          x = (aw - cw) / 2,
          y = (ah - ch) / 2;
      long start = (f.length() + 3) & ~3L;
      if (start > 0xffffff00L) throw new IOException("DNG too large");
      f.seek(start);
      write32(f, x, le);
      write32(f, y, le);
      write32(f, cw, le);
      write32(f, ch, le);
      List<Entry> entries = new ArrayList<>();
      for (Entry e : raw.entries) if (e.tag != 50719 && e.tag != 50720) entries.add(e);
      entries.add(cropEntry(50719, start, le));
      entries.add(cropEntry(50720, start + 8, le));
      entries.sort((l, r) -> Integer.compare(l.tag, r.tag));
      long next = f.getFilePointer();
      write16(f, entries.size(), le);
      for (Entry e : entries) f.write(e.bytes);
      write32(f, raw.next, le);
      f.getFD().sync();
      f.seek(raw.parent);
      write32(f, next, le);
      f.getFD().sync();
    }
  }

  private static Entry cropEntry(int tag, long offset, boolean le) throws IOException {
    Entry e = new Entry();
    e.tag = tag;
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    put16(out, tag, le);
    put16(out, 4, le);
    put32(out, 2, le);
    put32(out, offset, le);
    e.bytes = bytes.toByteArray();
    return e;
  }

  private static Entry entry(Ifd i, int tag) {
    for (Entry e : i.entries) if (e.tag == tag) return e;
    return null;
  }

  private static long scalar(RandomAccessFile f, boolean le, Ifd i, int tag) throws IOException {
    Entry e = entry(i, tag);
    if (e == null) throw new IOException("Missing DNG dimension");
    return values(f, le, e)[0];
  }

  private static void walk(
      RandomAccessFile f, boolean le, long offset, long parent, Set<Long> seen, List<Ifd> result)
      throws IOException {
    if (offset == 0 || seen.contains(offset)) return;
    if (seen.size() > 32 || offset < 8 || offset > f.length() - 6)
      throw new IOException("Invalid DNG IFD");
    seen.add(offset);
    f.seek(offset);
    int count = u16(f, le);
    if (count > 4096 || offset + 2 + 12L * count + 4 > f.length())
      throw new IOException("Invalid DNG entries");
    Ifd ifd = new Ifd();
    ifd.offset = offset;
    ifd.parent = parent;
    for (int n = 0; n < count; n++) {
      Entry e = new Entry();
      e.position = f.getFilePointer();
      e.bytes = new byte[12];
      f.readFully(e.bytes);
      f.seek(e.position);
      e.tag = u16(f, le);
      e.type = u16(f, le);
      e.count = u32(f, le);
      e.value = u32(f, le);
      ifd.entries.add(e);
    }
    long nextPointer = f.getFilePointer();
    ifd.next = u32(f, le);
    result.add(ifd);
    Entry subs = entry(ifd, 330);
    if (subs != null) {
      if (subs.type != 4 || subs.count > 16) throw new IOException("Invalid SubIFD");
      long[] refs = values(f, le, subs);
      for (int n = 0; n < refs.length; n++)
        walk(
            f, le, refs[n], subs.count == 1 ? subs.position + 8 : subs.value + n * 4, seen, result);
    }
    walk(f, le, ifd.next, nextPointer, seen, result);
  }

  private static long[] values(RandomAccessFile f, boolean le, Entry e) throws IOException {
    int size = e.type == 3 ? 2 : e.type == 4 ? 4 : 0;
    if (size == 0 || e.count < 1 || e.count > 16) throw new IOException("Unsupported DNG field");
    long pos = e.count * size <= 4 ? e.position + 8 : e.value;
    if (pos < 0 || pos + e.count * size > f.length()) throw new IOException("DNG field bounds");
    f.seek(pos);
    long[] values = new long[(int) e.count];
    for (int n = 0; n < values.length; n++) values[n] = size == 2 ? u16(f, le) : u32(f, le);
    return values;
  }

  private static int u16(RandomAccessFile f, boolean le) throws IOException {
    int a = f.readUnsignedByte(), b = f.readUnsignedByte();
    return le ? a | b << 8 : a << 8 | b;
  }

  private static long u32(RandomAccessFile f, boolean le) throws IOException {
    long a = u16(f, le), b = u16(f, le);
    return le ? a | b << 16 : a << 16 | b;
  }

  private static void write16(RandomAccessFile f, long n, boolean le) throws IOException {
    if (le) {
      f.write((int) n);
      f.write((int) (n >> 8));
    } else {
      f.write((int) (n >> 8));
      f.write((int) n);
    }
  }

  private static void write32(RandomAccessFile f, long n, boolean le) throws IOException {
    if (le) {
      write16(f, n, le);
      write16(f, n >> 16, le);
    } else {
      write16(f, n >> 16, le);
      write16(f, n, le);
    }
  }

  private static void put16(DataOutputStream f, long n, boolean le) throws IOException {
    if (le) {
      f.write((int) n);
      f.write((int) (n >> 8));
    } else f.writeShort((int) n);
  }

  private static void put32(DataOutputStream f, long n, boolean le) throws IOException {
    if (le) {
      put16(f, n, le);
      put16(f, n >> 16, le);
    } else f.writeInt((int) n);
  }
}
