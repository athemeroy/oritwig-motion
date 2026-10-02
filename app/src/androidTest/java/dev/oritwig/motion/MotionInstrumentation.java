package dev.oritwig.motion;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import dev.oritwig.motion.engine.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.*;
import org.json.*;

public final class MotionInstrumentation extends Instrumentation {
  private int passed;
  private boolean functionalOnly;
  private final StringBuilder log = new StringBuilder();

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    functionalOnly = b != null && "true".equals(b.getString("functionalOnly"));
    start();
  }

  private void check(String label, boolean yes) {
    if (!yes) throw new AssertionError(label);
    passed++;
    log.append("PASS ").append(label).append('\n');
  }

  interface Action {
    void run() throws Exception;
  }

  private void rejects(String label, Action action) throws Exception {
    try {
      action.run();
      throw new AssertionError(label + " accepted");
    } catch (IllegalArgumentException | IOException | CancellationException expected) {
      check(label, true);
    }
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    byte[] block = new byte[8192];
    int n;
    while ((n = in.read(block)) != -1) b.write(block, 0, n);
    return b.toByteArray();
  }

  private static String repeat(String s, int n) {
    StringBuilder b = new StringBuilder();
    while (n-- > 0) b.append(s);
    return b.toString();
  }

  private byte[] asset(String p) throws IOException {
    try (InputStream in = getTargetContext().getAssets().open(p)) {
      return readAll(in);
    }
  }

  private byte[] edit(byte[] original, String key, Object value) throws Exception {
    JSONObject o = new JSONObject(new String(original, StandardCharsets.UTF_8));
    o.put(key, value);
    return o.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public void onStart() {
    Bundle results = new Bundle();
    try {
      byte[] raw = asset("test.json"), demo = asset("orbit.json"), tgs = asset("orbit.tgs");
      check(
          "TGS gzip import equals original JSON",
          Arrays.equals(demo, AnimationInput.read(new ByteArrayInputStream(tgs), () -> false)));
      check(
          "JSON stream import equals bytes",
          Arrays.equals(raw, AnimationInput.read(new ByteArrayInputStream(raw), () -> false)));
      if (!functionalOnly) {
        rejects(
            "Oversized compressed/input bytes",
            () ->
                AnimationInput.read(
                    new ByteArrayInputStream(new byte[AnimationInput.MAX_BYTES + 1]), () -> false));
        ByteArrayOutputStream bomb = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bomb)) {
          gz.write(new byte[AnimationInput.MAX_BYTES + 1]);
        }
        rejects(
            "Decompression bound",
            () -> AnimationInput.read(new ByteArrayInputStream(bomb.toByteArray()), () -> false));
        rejects(
            "Import cancellation",
            () -> AnimationInput.read(new ByteArrayInputStream(raw), () -> true));
        rejects("Malformed JSON", () -> AnimationInput.validate("{broken".getBytes()));
        rejects(
            "Invalid UTF8", () -> AnimationInput.validate(new byte[] {(byte) 0xc0, (byte) 0xaf}));
        rejects(
            "Deep JSON before object parsing",
            () -> AnimationInput.validate((repeat("[", 40) + "0" + repeat("]", 40)).getBytes()));
        rejects("Too many source frames", () -> AnimationInput.validate(edit(raw, "op", 1801)));
        rejects("Oversized source canvas", () -> AnimationInput.validate(edit(raw, "w", 4097)));
        rejects("Zero frame rate", () -> AnimationInput.validate(edit(raw, "fr", 0)));
        rejects("Fractional frame boundaries", () -> AnimationInput.validate(edit(raw, "ip", 0.5)));
        rejects(
            "Raster assets",
            () ->
                AnimationInput.validate(
                    edit(
                        raw,
                        "assets",
                        new JSONArray("[{\"id\":\"x\",\"p\":\"file.png\",\"u\":\"/\"}]"))));
        rejects(
            "Recursive precomposition",
            () ->
                AnimationInput.validate(
                    edit(
                        raw,
                        "assets",
                        new JSONArray(
                            "[{\"id\":\"x\",\"layers\":[{\"ty\":0,\"refId\":\"x\"}]}]"))));
        rejects(
            "Missing vector asset",
            () ->
                AnimationInput.validate(
                    edit(
                        raw,
                        "assets",
                        new JSONArray(
                            "[{\"id\":\"x\",\"layers\":[{\"ty\":0,\"refId\":\"y\"}]}]"))));
        JSONObject text = new JSONObject(new String(raw));
        text.getJSONArray("layers").getJSONObject(0).put("ty", 5);
        rejects(
            "Unsupported text layer", () -> AnimationInput.validate(text.toString().getBytes()));
        for (String path :
            new String[] {
              "/data/data/dev.oritwig.motion/files/private",
              "../private.png",
              "https://example.org/image.png",
              "data:image/png;base64,AAAA"
            }) {
          JSONObject asset = new JSONObject().put("id", "external").put("p", path);
          byte[] unsafe = edit(raw, "assets", new JSONArray().put(asset));
          rejects("Asset path blocked: " + path, () -> AnimationInput.validate(unsafe));
        }
        rejects(
            "Compact native expansion DAG rejected before JNI",
            () -> {
              try (InputStream in = getContext().getAssets().open("native-expansion-dag.json")) {
                AnimationInput.validate(readAll(in));
              }
            });
        rejects(
            "Repeater multiplication rejected before JNI",
            () -> {
              try (InputStream in = getContext().getAssets().open("repeater-expansion.json")) {
                AnimationInput.validate(readAll(in));
              }
            });
        rejects(
            "Polystar point multiplication rejected before JNI",
            () -> {
              try (InputStream in = getContext().getAssets().open("polystar-expansion.json")) {
                AnimationInput.validate(readAll(in));
              }
            });
        rejects(
            "Dash subdivision rejected before JNI",
            () -> {
              try (InputStream in = getContext().getAssets().open("dash-expansion.json")) {
                AnimationInput.validate(readAll(in));
              }
            });
        long dagStarted = SystemClock.elapsedRealtime();
        rejects("Wide DAG expansion budget", () -> AnimationInput.validate(asset("wide-dag.json")));
        check("Wide DAG rejection is bounded", SystemClock.elapsedRealtime() - dagStarted < 10000);
        rejects(
            "Reserved root asset identifier",
            () ->
                AnimationInput.validate(
                    edit(raw, "assets", new JSONArray("[{\"id\":\"$root\",\"layers\":[]}]"))));
        JSONArray tooManyAssets = new JSONArray();
        for (int i = 0; i < 129; i++)
          tooManyAssets.put(new JSONObject().put("id", "a" + i).put("layers", new JSONArray()));
        rejects(
            "Vector asset count bound",
            () -> AnimationInput.validate(edit(raw, "assets", tooManyAssets)));
        JSONArray manyRefs = new JSONArray();
        for (int i = 0; i < 4097; i++) manyRefs.put(new JSONObject().put("refId", "leaf"));
        JSONArray referenceAssets =
            new JSONArray()
                .put(new JSONObject().put("id", "leaf").put("layers", new JSONArray()))
                .put(
                    new JSONObject()
                        .put("id", "refs")
                        .put("layers", new JSONArray())
                        .put("metadata", manyRefs));
        rejects(
            "Reference count bound",
            () -> AnimationInput.validate(edit(raw, "assets", referenceAssets)));
        JSONArray deepAssets = new JSONArray();
        for (int i = 9; i >= 0; i--) {
          JSONArray layers = new JSONArray();
          if (i < 9) layers.put(new JSONObject().put("ty", 0).put("refId", "deep" + (i + 1)));
          deepAssets.put(new JSONObject().put("id", "deep" + i).put("layers", layers));
        }
        rejects(
            "Memoized shared-tail depth bound",
            () -> AnimationInput.validate(edit(raw, "assets", deepAssets)));
        JSONArray expansionAssets = new JSONArray();
        for (int i = 7; i >= 0; i--) {
          JSONArray layers = new JSONArray();
          if (i < 7)
            for (int copy = 0; copy < 4; copy++)
              layers.put(new JSONObject().put("ty", 0).put("refId", "expanded" + (i + 1)));
          expansionAssets.put(new JSONObject().put("id", "expanded" + i).put("layers", layers));
        }
        rejects(
            "Repeated references count every expanded instance",
            () -> AnimationInput.validate(edit(raw, "assets", expansionAssets)));
      } else {
        log.append("SCOPE Functional-only: adversarial input fixtures not executed in this run\n");
      }
      byte[] precomp =
          AnimationInput.read(new ByteArrayInputStream(asset("precomp.tgs")), () -> false);
      try (MotionAnimation vectors = new MotionAnimation(precomp)) {
        Bitmap first = vectors.render(0, 256), last = vectors.render(59, 256);
        int opaque = 0;
        for (int y = 0; y < 256; y++)
          for (int x = 0; x < 256; x++) if (Color.alpha(first.getPixel(x, y)) > 0) opaque++;
        check("Embedded multi-layer vector precomposition renders", opaque > 1000);
        check("Precomposition retains animated frames", !first.sameAs(last));
        first.recycle();
        last.recycle();
      }
      JSONObject reused = new JSONObject(new String(precomp, StandardCharsets.UTF_8));
      JSONArray reusedLayers = reused.getJSONArray("layers");
      JSONObject secondInstance = new JSONObject(reusedLayers.getJSONObject(0).toString());
      secondInstance.put("ind", 12);
      secondInstance.getJSONObject("ks").getJSONObject("p").put("k", new JSONArray("[356,256,0]"));
      reusedLayers.put(secondInstance);
      byte[] repeated = reused.toString().getBytes(StandardCharsets.UTF_8);
      AnimationInput.validate(repeated);
      try (MotionAnimation ordinary = new MotionAnimation(precomp);
          MotionAnimation shared = new MotionAnimation(repeated)) {
        Bitmap normal = ordinary.render(0, 256),
            twice = shared.render(0, 256),
            later = shared.render(59, 256);
        check("Valid reused precomposition renders both instances", !normal.sameAs(twice));
        check("Reused precomposition remains animated", !twice.sameAs(later));
        normal.recycle();
        twice.recycle();
        later.recycle();
      }
      try (MotionAnimation a = new MotionAnimation(raw)) {
        check(
            "Upstream metadata",
            a.width == 64
                && a.height == 64
                && a.frameCount == 60
                && a.frameRate == 30
                && a.durationSeconds() == 2);
        Bitmap first = a.render(0, 64), last = a.render(59, 64), again = a.render(0, 64);
        check("Transparent pixels", Color.alpha(first.getPixel(0, 0)) == 0);
        check("Correct red/alpha channel layout", first.getPixel(16, 32) == Color.RED);
        check(
            "Actual animated motion",
            first.getPixel(16, 32) == Color.RED
                && last.getPixel(16, 32) == 0
                && last.getPixel(48, 32) == Color.RED);
        check("Repeat rendering deterministic", first.sameAs(again));
        Bitmap scaled = a.render(0, 128);
        check(
            "Native scaled surface",
            scaled.getWidth() == 128 && scaled.getPixel(32, 64) == Color.RED);
        scaled.recycle();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        FrameExport.png(a, 0, 64, png);
        Bitmap reopened = BitmapFactory.decodeByteArray(png.toByteArray(), 0, png.size());
        check("PNG reopened pixel-identical", first.sameAs(reopened));
        reopened.recycle();
        File output = new File(getTargetContext().getFilesDir(), "native-frame.png");
        try (OutputStream out = new FileOutputStream(output)) {
          out.write(png.toByteArray());
        }
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        FrameExport.zip(a, 0, 59, 10, 64, zip, () -> false, n -> {});
        int images = 0;
        boolean timing = false;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip.toByteArray()))) {
          ZipEntry e;
          while ((e = zin.getNextEntry()) != null) {
            byte[] bytes = readAll(zin);
            if (e.getName().endsWith(".png")) {
              Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
              check("ZIP frame " + images + " decodes", b != null && b.getWidth() == 64);
              b.recycle();
              images++;
            } else {
              JSONObject m = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
              timing =
                  m.getInt("step") == 10
                      && m.getInt("count") == 6
                      && m.getDouble("sourceFrameRate") == 30;
            }
          }
        }
        check("ZIP exact frame count and source timing", images == 6 && timing);
        try (OutputStream out =
            new FileOutputStream(
                new File(getTargetContext().getFilesDir(), "native-sequence.zip"))) {
          out.write(zip.toByteArray());
        }
        rejects(
            "Sequence cancellation",
            () ->
                FrameExport.zip(a, 0, 59, 1, 64, new ByteArrayOutputStream(), () -> true, n -> {}));
        rejects("ZIP bound", () -> FrameExport.count(0, 1799, 1, 1800, 512));
        rejects("Negative frame", () -> a.render(-1, 64));
        rejects("Frame beyond end", () -> a.render(60, 64));
        rejects("Render size bound", () -> a.render(0, 1025));
        first.recycle();
        last.recycle();
        again.recycle();
        a.close();
        a.close();
        try {
          a.render(0, 64);
          throw new AssertionError("closed render");
        } catch (IllegalStateException expected) {
          check("Close idempotent and post-close rejected", true);
        }
      }
      try (MotionAnimation a = new MotionAnimation(demo);
          MotionAnimation b = new MotionAnimation(raw)) {
        Bitmap one = a.render(30, 256), two = b.render(30, 64);
        check("Independent native instances", one.getWidth() == 256 && two.getWidth() == 64);
        one.recycle();
        two.recycle();
      }
      check(
          "No permissions declared",
          getTargetContext()
                  .getPackageManager()
                  .getPackageInfo(
                      getTargetContext().getPackageName(),
                      android.content.pm.PackageManager.GET_PERMISSIONS)
                  .requestedPermissions
              == null);
      Activity activity =
          startActivitySync(
              new Intent(getTargetContext(), MainActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      check("Native app launches", activity != null);
      runOnMainSync(activity::finish);
      results.putString("stream", log + "PASS " + passed + " checks\n");
      results.putInt("passed", passed);
      finish(Activity.RESULT_OK, results);
    } catch (Throwable e) {
      results.putString(
          "stream", log + "FAIL " + e + "\n" + android.util.Log.getStackTraceString(e));
      results.putInt("passed", passed);
      finish(Activity.RESULT_CANCELED, results);
    }
  }
}
