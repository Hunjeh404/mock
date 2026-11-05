package com.hoonjay.filmroom.core;

import java.io.*;
import java.util.*;
import java.nio.*;

/**
 * Reads the uncompressed 16-bit CFA strips written by Android DngCreator. Other DNG encodings are
 * rejected explicitly instead of decoding the embedded JPEG preview.
 */
public final class DngReader {
  private final RandomAccessFile file;
  private boolean little;
  private long length;
  private final Set<Long> visited = new HashSet<>();
  private Map<Integer, Tag> raw;
  private Map<Integer, Tag> root;

  private static final class Tag {
    int type;
    long count, offset;

    Tag(int t, long n, long o) {
      type = t;
      count = n;
      offset = o;
    }
  }

  private DngReader(File path) throws IOException {
    file = new RandomAccessFile(path, "r");
    length = file.length();
  }

  private int u16() throws IOException {
    int a = file.readUnsignedByte(), b = file.readUnsignedByte();
    return little ? a | (b << 8) : (a << 8) | b;
  }

  private long u32() throws IOException {
    long a = u16(), b = u16();
    return little ? a | (b << 16) : (a << 16) | b;
  }

  private long value(Tag t, int index) throws IOException {
    if (t == null || index >= t.count) return 0;
    long offset = t.offset + (long) index * (t.type == 3 ? 2 : t.type == 1 ? 1 : 4);
    if (offset < 0 || offset + 4 > length + 3) throw new IOException("Invalid DNG offset");
    file.seek(offset);
    return t.type == 3 ? u16() : t.type == 1 ? file.readUnsignedByte() : u32();
  }

  private void ifd(long offset, int depth) throws IOException {
    if (offset <= 0 || offset >= length || depth > 8 || !visited.add(offset)) return;
    file.seek(offset);
    int n = u16();
    if (n > 4096) throw new IOException("Invalid DNG IFD");
    Map<Integer, Tag> tags = new HashMap<>();
    for (int i = 0; i < n; i++) {
      int id = u16(), type = u16();
      long count = u32(), entry = file.getFilePointer(), data = u32();
      int bytes =
          type == 3
              ? 2
              : (type == 1 || type == 2 || type == 6 || type == 7)
                  ? 1
                  : (type == 5 || type == 10 || type == 12) ? 8 : 4;
      tags.put(id, new Tag(type, count, count * bytes <= 4 ? entry : data));
    }
    long next = u32();
    if(root==null)root=tags;
    int photo = (int) value(tags.get(262), 0);
    if (photo == 32803) {
      if (raw == null
          || value(tags.get(256), 0) * value(tags.get(257), 0)
              > value(raw.get(256), 0) * value(raw.get(257), 0)) raw = tags;
    }
    Tag sub = tags.get(330);
    if (sub != null)
      for (int i = 0; i < Math.min(sub.count, 64); i++) ifd(value(sub, i), depth + 1);
    ifd(next, depth + 1);
  }

  public static RawFrame read(File path) throws IOException {
    DngReader r = new DngReader(path);
    try {
      return r.read();
    } finally {
      r.file.close();
    }
  }

  private RawFrame read() throws IOException {
    int a = file.readUnsignedByte(), b = file.readUnsignedByte();
    if (a == 73 && b == 73) little = true;
    else if (a == 77 && b == 77) little = false;
    else throw new IOException("Not a DNG/TIFF");
    if (u16() != 42) throw new IOException("Unsupported DNG header");
    ifd(u32(), 0);
    if (raw == null) throw new IOException("CFA RAW data not found");
    int w = (int) value(raw.get(256), 0),
        h = (int) value(raw.get(257), 0),
        compression = (int) value(raw.get(259), 0),
        bits = (int) value(raw.get(258), 0);
    if (w < 2 || h < 2 || (long) w * h > 100000000) throw new IOException("RAW image is too large");
    if (compression != 1 || bits != 16)
      throw new IOException(
          "Only native uncompressed 16-bit DNG is supported; embedded previews are never"
              + " substituted");
    Tag offsets = raw.get(273), counts = raw.get(279);
    if (offsets == null || counts == null || offsets.count > 100000)
      throw new IOException("Invalid RAW strips");
    short[] data = new short[w * h];
    int dest = 0;
    byte[] block = new byte[65536];
    for (int i = 0; i < offsets.count; i++) {
      long pos = value(offsets, i), count = value(counts, Math.min(i, (int) counts.count - 1));
      if (pos < 0 || count < 0 || pos + count > length || count % 2 != 0)
        throw new IOException("Truncated RAW strip");
      file.seek(pos);
      while (count > 0) {
        int n = (int) Math.min(count, block.length);
        file.readFully(block, 0, n);
        for (int j = 0; j < n; j += 2) {
          if (dest >= data.length) throw new IOException("RAW strip overflow");
          data[dest++] =
              (short)
                  (little
                      ? (block[j] & 255) | ((block[j + 1] & 255) << 8)
                      : ((block[j] & 255) << 8) | (block[j + 1] & 255));
        }
        count -= n;
      }
    }
    if (dest != data.length) throw new IOException("Incomplete RAW samples");
    RawFrame frame=new RawFrame(w,h,data);
    calibration(frame);
    return frame;
  }

  private Tag tag(int id){Tag t=raw.get(id);return t==null&&root!=null?root.get(id):t;}
  private double[] numbers(int id,int max) throws IOException {
    Tag t=tag(id);if(t==null)return null;
    int size=t.type==1?1:t.type==3?2:(t.type==5||t.type==10||t.type==12)?8:4;
    if(t.count<1||t.count>max||t.offset<0||t.offset+t.count*size>length)throw new IOException("Invalid DNG calibration tag "+id);
    double[] a=new double[(int)t.count];file.seek(t.offset);
    for(int i=0;i<a.length;i++){
      if(t.type==1)a[i]=file.readUnsignedByte();
      else if(t.type==3)a[i]=u16();
      else if(t.type==4)a[i]=u32();
      else if(t.type==9)a[i]=(int)u32();
      else if(t.type==5||t.type==10){long n=u32(),d=u32();if(t.type==10){n=(int)n;d=(int)d;}if(d==0)throw new IOException("Invalid DNG rational");a[i]=n/(double)d;}
      else throw new IOException("Unsupported DNG calibration type");
      if(!Double.isFinite(a[i]))throw new IOException("Non-finite DNG calibration");
    }return a;
  }
  private double scalar(int id,double fallback)throws IOException{double[] a=numbers(id,16);return a==null?fallback:a[0];}
  private void calibration(RawFrame f)throws IOException{
    // Minimal legacy test files use a Camera2 sidecar. Native Android DNGs carry these tags.
    if(tag(50728)==null||tag(50964)==null||tag(50721)==null)return;
    try{
      double[] neutral=numbers(50728,3),black=numbers(50714,4),pattern=numbers(33422,4),area=numbers(50829,4);
      DngColorProfile profile=new DngColorProfile(neutral,numbers(50721,9),numbers(50722,9),
          numbers(50964,9),numbers(50965,9),numbers(50723,9),numbers(50724,9),numbers(50727,3),
          (int)scalar(50778,0),(int)scalar(50779,0),scalar(50730,0)+scalar(51109,0));
      int white=(int)scalar(50717,0);if(white<=0||white>65535||pattern==null||pattern.length!=4||black==null||(black.length!=1&&black.length!=4))throw new IOException("Incomplete DNG levels/CFA");
      int reds=0,blues=0,greens=0;
      for(int i=0;i<4;i++){
        if(pattern[i]==0){f.cfa[i]=0;reds++;}else if(pattern[i]==2){f.cfa[i]=3;blues++;}
        else if(pattern[i]==1){f.cfa[i]=i<2?1:2;greens++;}else throw new IOException("Unsupported DNG CFA");
        double b=black[black.length==1?0:i];if(b<0||b>=white)throw new IOException("Invalid DNG black/white levels");f.black[i]=(float)b;
      }
      if(reds!=1||blues!=1||greens!=2)throw new IOException("Unsupported DNG CFA");
      if(area!=null){int top=(int)area[0],left=(int)area[1],bottom=(int)area[2],right=(int)area[3];
        if(left<0||top<0||right>f.width||bottom>f.height||right<=left||bottom<=top)throw new IOException("Invalid DNG active area");
        f.activeLeft=left;f.activeTop=top;f.activeWidth=right-left;f.activeHeight=bottom-top;
      }
      f.white=white;f.gains=profile.gains;f.matrix=profile.matrix;f.postRawBoost=profile.baselineGain;
      f.dngCalibrated=true;
      gainMaps(f);
    }catch(IllegalArgumentException e){throw new IOException("Invalid DNG color calibration",e);}
  }
  private void gainMaps(RawFrame f)throws IOException{
    Tag t=tag(51009);if(t==null)return;
    if(t.count<4||t.count>1048576||t.offset<0||t.offset+t.count>length)throw new IOException("Invalid DNG gain map");
    byte[] data=new byte[(int)t.count];file.seek(t.offset);file.readFully(data);
    ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);int count=b.getInt();
    if(count<0||count>64)throw new IOException("Invalid DNG opcode count");
    float[] shading=null;boolean[] channels=new boolean[4];int rows=0,cols=0;
    try{for(int i=0;i<count;i++){
      int id=b.getInt();b.getInt();int flags=b.getInt(),size=b.getInt(),start=b.position();
      if(size<0||size>b.remaining())throw new IOException("Invalid DNG opcode length");
      if(id==9){
        if(size<76)throw new IOException("Invalid DNG gain map size");
        int top=b.getInt(),left=b.getInt(),bottom=b.getInt(),right=b.getInt(),plane=b.getInt(),planes=b.getInt(),rowPitch=b.getInt(),colPitch=b.getInt(),v=b.getInt(),h=b.getInt();
        double sy=b.getDouble(),sx=b.getDouble(),oy=b.getDouble(),ox=b.getDouble();int mapPlanes=b.getInt();
        if(v<2||h<2||v>256||h>256||mapPlanes!=1||size!=76L+v*h*4L)throw new IOException("Unsupported DNG gain map grid");
        if(top<f.activeTop||top>f.activeTop+1||left<f.activeLeft||left>f.activeLeft+1||bottom!=f.activeTop+f.activeHeight||right!=f.activeLeft+f.activeWidth||plane!=0||planes!=1||rowPitch!=2||colPitch!=2||Math.abs(sy-1./(v-1))>1e-6||Math.abs(sx-1./(h-1))>1e-6||oy!=0||ox!=0)throw new IOException("Unsupported DNG gain map bounds");
        if(shading==null){rows=v;cols=h;shading=new float[v*h*4];Arrays.fill(shading,1);}
        if(v!=rows||h!=cols)throw new IOException("Inconsistent DNG gain map grids");
        int ch=f.channel(left,top);if(channels[ch])throw new IOException("Duplicate DNG gain map");channels[ch]=true;
        for(int j=0;j<v*h;j++){float value=b.getFloat();if(!Float.isFinite(value)||value<=0||value>32)throw new IOException("Invalid DNG shading gain");shading[j*4+ch]=value;}
      }else if((flags&1)==0)throw new IOException("Unsupported required DNG opcode "+id);
      b.position(start+size);
    }}catch(BufferUnderflowException e){throw new IOException("Truncated DNG opcode",e);}
    if(shading!=null){for(boolean present:channels)if(!present)throw new IOException("Incomplete DNG shading map");f.shading=shading;f.shadeWidth=cols;f.shadeHeight=rows;f.dngShading=true;}
  }
}
