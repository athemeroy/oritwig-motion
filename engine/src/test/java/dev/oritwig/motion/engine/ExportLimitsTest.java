package dev.oritwig.motion.engine;

import static org.junit.Assert.*;

import org.junit.Test;

public final class ExportLimitsTest {
  @Test
  public void inclusiveRange() {
    assertEquals(60, FrameExport.count(0, 59, 1, 60, 512));
  }

  @Test
  public void stepPreservesIndices() {
    assertEquals(30, FrameExport.count(0, 59, 2, 60, 512));
    assertEquals(20, FrameExport.count(1, 59, 3, 60, 256));
  }

  @Test
  public void lastFrame() {
    assertEquals(1, FrameExport.count(59, 59, 1, 60, 512));
  }

  @Test
  public void maximum() {
    assertEquals(120, FrameExport.count(0, 119, 1, 1800, 512));
  }

  @Test(expected = IllegalArgumentException.class)
  public void tooMany() {
    FrameExport.count(0, 120, 1, 1800, 512);
  }

  @Test(expected = IllegalArgumentException.class)
  public void pastLast() {
    FrameExport.count(0, 60, 1, 60, 256);
  }

  @Test(expected = IllegalArgumentException.class)
  public void noStep() {
    FrameExport.count(0, 59, 0, 60, 256);
  }

  @Test(expected = IllegalArgumentException.class)
  public void descending() {
    FrameExport.count(20, 10, 1, 60, 256);
  }

  @Test(expected = IllegalArgumentException.class)
  public void negative() {
    FrameExport.count(-1, 2, 1, 60, 256);
  }

  @Test(expected = IllegalArgumentException.class)
  public void oversized() {
    FrameExport.count(0, 59, 1, 60, 1024);
  }
}
