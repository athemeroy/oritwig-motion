package dev.oritwig.motion.engine;

import android.graphics.Bitmap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.zip.*;

/** PNG/ZIP platform-file adapter. Each image is produced by unchanged rlottie. GPL-3.0-or-later. */
public final class FrameExport {
  public static final int MAX_FRAMES = 120, MAX_SIDE = 512;
  public static final long MAX_PIXELS = 32_000_000, MAX_ZIP_BYTES = 64L * 1024 * 1024;

  private FrameExport() {}

  public static int count(int first, int last, int step, int total, int side) {
    if (first < 0 || last < first || last >= total || step < 1 || side < 16 || side > MAX_SIDE)
      throw new IllegalArgumentException("Invalid range, step or export size");
    int n = (last - first) / step + 1;
    if (n > MAX_FRAMES || (long) n * side * side > MAX_PIXELS)
      throw new IllegalArgumentException("Choose at most 120 frames and 32 million total pixels");
    return n;
  }

  public static void png(MotionAnimation a, int frame, int side, OutputStream out)
      throws IOException {
    if (side < 16 || side > 1024) throw new IllegalArgumentException("Invalid export size");
    Bitmap b = a.render(frame, side);
    try {
      if (!b.compress(Bitmap.CompressFormat.PNG, 100, out))
        throw new IOException("PNG encoding failed");
    } finally {
      b.recycle();
    }
  }

  public static void zip(
      MotionAnimation a,
      int first,
      int last,
      int step,
      int side,
      OutputStream out,
      BooleanSupplier cancel,
      IntConsumer progress)
      throws IOException {
    int count = count(first, last, step, a.frameCount, side);
    long[] bytes = {0};
    OutputStream limited =
        new FilterOutputStream(out) {
          @Override
          public void write(int v) throws IOException {
            if (++bytes[0] > MAX_ZIP_BYTES) throw new IOException("ZIP exceeds 64 MiB");
            out.write(v);
          }

          @Override
          public void write(byte[] b, int o, int n) throws IOException {
            if ((bytes[0] += n) > MAX_ZIP_BYTES) throw new IOException("ZIP exceeds 64 MiB");
            out.write(b, o, n);
          }
        };
    try (ZipOutputStream zip = new ZipOutputStream(limited)) {
      zip.setLevel(0);
      int written = 0;
      for (int f = first; f <= last; f += step) {
        if (cancel.getAsBoolean()) throw new CancellationException();
        zip.putNextEntry(new ZipEntry(String.format(java.util.Locale.ROOT, "frame-%05d.png", f)));
        png(a, f, side, zip);
        zip.closeEntry();
        progress.accept(++written);
      }
      if (cancel.getAsBoolean()) throw new CancellationException();
      String manifest =
          "{\"format\":\"Oritwig Motion PNG sequence\",\"firstFrame\":"
              + first
              + ",\"lastRequestedFrame\":"
              + last
              + ",\"step\":"
              + step
              + ",\"count\":"
              + count
              + ",\"sourceFrameRate\":"
              + a.frameRate
              + ",\"sourceWidth\":"
              + a.width
              + ",\"sourceHeight\":"
              + a.height
              + ",\"outputMaxSide\":"
              + side
              + ",\"alpha\":true}";
      zip.putNextEntry(new ZipEntry("sequence.json"));
      zip.write(manifest.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
  }
}
