package dev.oritwig.motion.engine;

import android.graphics.Bitmap;

/**
 * Synchronous, serialized wrapper. Call off the UI thread; close when finished. GPL-3.0-or-later.
 */
public final class MotionAnimation implements AutoCloseable {
  static {
    System.loadLibrary("oritwig_motion_jni");
  }

  private long handle;
  public final int width, height, frameCount;
  public final double frameRate;

  public MotionAnimation(byte[] json) {
    AnimationInput.validate(json);
    handle = nativeOpen(json);
    if (handle == 0) throw new IllegalArgumentException("Cannot open animation");
    double[] info = nativeInfo(handle);
    width = (int) info[0];
    height = (int) info[1];
    frameCount = (int) info[2];
    frameRate = info[3];
  }

  public double durationSeconds() {
    return frameCount / frameRate;
  }

  /** A new transparent, premultiplied Android bitmap owned by the caller. */
  public synchronized Bitmap render(int frame, int maximumSide) {
    if (handle == 0) throw new IllegalStateException("Animation is closed");
    if (frame < 0 || frame >= frameCount)
      throw new IllegalArgumentException("Frame is out of range");
    if (maximumSide < 16 || maximumSide > 1024)
      throw new IllegalArgumentException("Render size must be 16–1024");
    double scale = (double) maximumSide / Math.max(width, height);
    int w = Math.max(1, (int) Math.round(width * scale)),
        h = Math.max(1, (int) Math.round(height * scale));
    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    try {
      nativeRender(handle, frame, b);
      return b;
    } catch (RuntimeException ex) {
      b.recycle();
      throw ex;
    }
  }

  @Override
  public synchronized void close() {
    if (handle != 0) {
      nativeClose(handle);
      handle = 0;
    }
  }

  private static native long nativeOpen(byte[] json);

  private static native double[] nativeInfo(long handle);

  private static native void nativeRender(long handle, int frame, Bitmap bitmap);

  private static native void nativeClose(long handle);
}
