package com.hoonjay.filmroom.core;

/** Linear Bayer samples. No 8-bit intermediate and no JPEG-based RAW development. */
public final class RawFrame {
  public final int width, height;
  public final short[] pixels;
  // CFA index at (0,0),(1,0),(0,1),(1,1); R=0, green even=1, green odd=2, B=3.
  public int[] cfa = {0, 1, 2, 3};
  public float[] black = {0, 0, 0, 0},
      gains = {2, 1, 1, 1.6f},
      matrix = {1, 0, 0, 0, 1, 0, 0, 0, 1};
  public int white = 4095, activeLeft = 0, activeTop = 0, activeWidth, activeHeight;
  public int shadeWidth = 0, shadeHeight = 0;
  public float[] shading;
  public float postRawBoost = 1;
  public float[][] toneCurves;
  public boolean dngCalibrated=false,dngShading=false;
  private float[] greens;

  public RawFrame(int w, int h, short[] data) {
    if (w <= 0 || h <= 0 || (long) w * h != data.length)
      throw new IllegalArgumentException("Invalid RAW dimensions");
    width = w;
    height = h;
    pixels = data;
    activeWidth = w;
    activeHeight = h;
  }

  private int boundX(int x) {
    return x < 0 ? (-x % 2) : x >= width ? width - 1 - ((width - 1 - x) & 1) : x;
  }

  private int boundY(int y) {
    return y < 0 ? (-y % 2) : y >= height ? height - 1 - ((height - 1 - y) & 1) : y;
  }

  public int channel(int x, int y) {
    return cfa[((y & 1) << 1) | (x & 1)];
  }

  public float sample(int xx, int yy) {
    int x = boundX(xx), y = boundY(yy), pos = ((y & 1) << 1) | (x & 1), ch = cfa[pos];
    float v =
        Math.max(0, (pixels[y * width + x] & 65535) - black[pos]) / Math.max(1, white - black[pos]);
    v *= gains[ch];
    if (shading != null && shadeWidth > 1 && shadeHeight > 1) {
      float
          fx =
              Math.max(
                  0,
                  Math.min(
                      shadeWidth - 1,
                      (x - activeLeft) / (float) Math.max(1, activeWidth - 1) * (shadeWidth - 1))),
          fy =
              Math.max(
                  0,
                  Math.min(
                      shadeHeight - 1,
                      (y - activeTop) / (float) Math.max(1, activeHeight - 1) * (shadeHeight - 1)));
      int sx = Math.min(shadeWidth - 2, (int) fx), sy = Math.min(shadeHeight - 2, (int) fy);
      float a = fx - sx, b = fy - sy;
      int i = (sy * shadeWidth + sx) * 4 + ch;
      float upper = shading[i] * (1 - a) + shading[i + 4] * a,
          lower = shading[i + shadeWidth * 4] * (1 - a) + shading[i + (shadeWidth + 1) * 4] * a;
      v *= upper * (1 - b) + lower * b;
    }
    return v;
  }

  public void prepare() {
    if (greens != null) return;
    greens = new float[pixels.length];
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        int ch = channel(x, y);
        if (ch == 1 || ch == 2) {
          greens[y * width + x] = sample(x, y);
          continue;
        }
        float c = sample(x, y),
            l = sample(x - 1, y),
            r = sample(x + 1, y),
            u = sample(x, y - 1),
            d = sample(x, y + 1),
            ll = sample(x - 2, y),
            rr = sample(x + 2, y),
            uu = sample(x, y - 2),
            dd = sample(x, y + 2);
        float gh = Math.abs(l - r) + Math.abs(2 * c - ll - rr),
            gv = Math.abs(u - d) + Math.abs(2 * c - uu - dd),
            h = (l + r) * .5f + (2 * c - ll - rr) * .25f,
            v = (u + d) * .5f + (2 * c - uu - dd) * .25f;
        greens[y * width + x] = Math.max(0, gh < gv ? h : gv < gh ? v : (h + v) * .5f);
      }
  }

  private float green(int x, int y) {
    return greens[boundY(y) * width + boundX(x)];
  }

  private float diff(int x, int y) {
    return sample(x, y) - green(x, y);
  }

  private float sensorLevel(int x,int y){x=boundX(x);y=boundY(y);int pos=((y&1)<<1)|(x&1);return Math.max(0,(pixels[y*width+x]&65535)-black[pos])/Math.max(1,white-black[pos]);}
  private float sensorGreen(int x,int y){int ch=channel(x,y);return ch==1||ch==2?sensorLevel(x,y):(sensorLevel(x-1,y)+sensorLevel(x+1,y)+sensorLevel(x,y-1)+sensorLevel(x,y+1))*.25f;}
  private float sensorColor(int x,int y,int target){float sum=0;int count=0;for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if(channel(x+dx,y+dy)==target){sum+=sensorLevel(x+dx,y+dy);count++;}return sum/Math.max(1,count);}

  public void rgb(int ax, int ay, float[] out) {
    if (greens == null) prepare();
    int x = ax + activeLeft, y = ay + activeTop, ch = channel(x, y);
    float g = green(x, y), r, b;
    if (ch == 0) {
      r = sample(x, y);
      b =
          g
              + (diff(x - 1, y - 1) + diff(x + 1, y - 1) + diff(x - 1, y + 1) + diff(x + 1, y + 1))
                  * .25f;
    } else if (ch == 3) {
      b = sample(x, y);
      r =
          g
              + (diff(x - 1, y - 1) + diff(x + 1, y - 1) + diff(x - 1, y + 1) + diff(x + 1, y + 1))
                  * .25f;
    } else if (channel(x + 1, y) == 0) {
      r = g + (diff(x - 1, y) + diff(x + 1, y)) * .5f;
      b = g + (diff(x, y - 1) + diff(x, y + 1)) * .5f;
    } else {
      r = g + (diff(x, y - 1) + diff(x, y + 1)) * .5f;
      b = g + (diff(x - 1, y) + diff(x + 1, y)) * .5f;
    }
    if(dngCalibrated){
      // A clipped green photosite cannot record the extra light still present in R/B.
      // Rebuild only the green deficit of near-neutral highlights, before the color matrix.
      // Saturated colored lights with a low green signal are deliberately excluded.
      float greenLevel=sensorGreen(x,y),clipped=ColorMath.clamp((greenLevel-.97f)/.03f);
      if(clipped>0&&r>=g*.95f&&b>=g*.95f){
        g+=(Math.max(g,Math.min(r,b))-g)*clipped;
        float redLevel=sensorColor(x,y,0),blueLevel=sensorColor(x,y,3);
        float second=Math.max(Math.min(redLevel,greenLevel),Math.max(Math.min(redLevel,blueLevel),Math.min(greenLevel,blueLevel)));
        float neutral=ColorMath.clamp((second-.97f)/.03f)*clipped,peak=Math.max(r,Math.max(g,b));
        r+=(peak-r)*neutral;g+=(peak-g)*neutral;b+=(peak-b)*neutral;
      }
    }
    out[0] = Math.max(0, matrix[0] * r + matrix[1] * g + matrix[2] * b);
    out[1] = Math.max(0, matrix[3] * r + matrix[4] * g + matrix[5] * b);
    out[2] = Math.max(0, matrix[6] * r + matrix[7] * g + matrix[8] * b);
    if(dngCalibrated){
      for(int c=0;c<3;c++)out[c]*=postRawBoost;
      ToneMapping.neutralRaw(out);
    }else if (toneCurves != null || postRawBoost != 1)
      for (int c = 0; c < 3; c++)
        out[c] = ToneMapping.workingLinear(out[c], postRawBoost,
            toneCurves == null ? null : toneCurves[c]);
  }

  public void release() {
    greens = null;
  }
}
