package dev.oritwig.motion;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Checkerboard is UI decoration. Animation pixels come only from rlottie. */
final class PreviewView extends View {
  private final Paint paint = new Paint(3);
  private final RectF destination = new RectF();
  private Bitmap bitmap;
  private boolean checker = true;

  PreviewView(Context c) {
    super(c);
    setContentDescription("Animation preview");
  }

  void setBitmap(Bitmap b) {
    Bitmap old = bitmap;
    bitmap = b;
    invalidate();
    if (old != null && old != b) old.recycle();
  }

  void checker(boolean b) {
    checker = b;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    int s = 24;
    for (int y = 0; y < getHeight(); y += s)
      for (int x = 0; x < getWidth(); x += s) {
        paint.setColor(checker && ((x / s + y / s) % 2 == 0) ? 0xff253544 : 0xff202e3c);
        c.drawRect(x, y, x + s, y + s, paint);
      }
    if (bitmap != null) {
      float scale =
          Math.min(
              (float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
      float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
      destination.set(
          (getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
      c.drawBitmap(bitmap, null, destination, paint);
    } else {
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setColor(0xff9db0bf);
      paint.setTextSize(16 * getResources().getDisplayMetrics().scaledDensity);
      c.drawText("Your animation, frame by frame", getWidth() / 2f, getHeight() / 2f, paint);
    }
  }
}
