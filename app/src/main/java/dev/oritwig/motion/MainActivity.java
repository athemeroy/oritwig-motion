package dev.oritwig.motion;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import dev.oritwig.motion.engine.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Account-free native Android shell around Samsung/Telegram rlottie. GPL-3.0-or-later. */
public final class MainActivity extends Activity {
  private static final int IMPORT = 10, EXPORT = 11;
  private static final int BG = 0xff101b27,
      FG = 0xffe7f0f4,
      MUTED = 0xff9eb2c1,
      ACCENT = 0xff70e3cb;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler ui = new Handler(Looper.getMainLooper());
  private PreviewView preview;
  private TextView title, meta, time, status;
  private SeekBar scrub;
  private Button play, export, sequence, cancel;
  private LinearLayout main;
  private MotionAnimation animation;
  private byte[] currentJson;
  private String name = "No animation open";
  private int frame = 0, desiredFrame = 0, side = 512;
  private boolean playing = false,
      busy = false,
      renderBusy = false,
      dead = false,
      loop = true,
      checkers = true;
  private float speed = 1;
  private long playStart;
  private int playFrame;
  private AtomicBoolean cancellation = new AtomicBoolean();
  private File pendingExport;
  private String pendingMime, pendingName;
  private int generation = 0;
  private android.content.SharedPreferences prefs;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("motion", MODE_PRIVATE);
    side = prefs.getInt("side", 512);
    loop = prefs.getBoolean("loop", true);
    checkers = prefs.getBoolean("checker", true);
    speed = prefs.getFloat("speed", 1);
    buildUi();
    File saved = new File(getFilesDir(), "animation.json");
    if (saved.isFile())
      load(
          () -> new FileInputStream(saved),
          prefs.getString("name", "Last animation"),
          prefs.getInt("frame", 0));
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  private GradientDrawable panel(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(14));
    return d;
  }

  private Button button(String s, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextColor(FG);
    b.setTextSize(14);
    b.setMinHeight(dp(46));
    b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff263b4b));
    b.setOnClickListener(v -> r.run());
    return b;
  }

  private void row(LinearLayout row, View v) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
    p.setMargins(dp(3), 0, dp(3), 0);
    row.addView(v, p);
  }

  private LinearLayout horizontal() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    return l;
  }

  private void buildUi() {
    main = new LinearLayout(this);
    main.setOrientation(LinearLayout.VERTICAL);
    main.setBackgroundColor(BG);
    main.setPadding(dp(18), dp(14), dp(18), dp(12));
    main.setFitsSystemWindows(true);
    setContentView(main);
    LinearLayout head = horizontal();
    TextView brand = text("MOTION", 24, FG);
    brand.setTypeface(null, Typeface.BOLD);
    head.addView(brand, new LinearLayout.LayoutParams(0, dp(42), 1));
    Button menu = button("•••", this::menu);
    head.addView(menu, new LinearLayout.LayoutParams(dp(60), dp(42)));
    main.addView(head);
    main.addView(text("VECTOR-ONLY ANIMATION WORKBENCH", 10, ACCENT));
    title = text(name, 18, FG);
    title.setSingleLine(true);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setPadding(0, dp(12), 0, dp(4));
    main.addView(title);
    meta = text("JSON / TGS  ·  private on this device", 12, MUTED);
    main.addView(meta);
    preview = new PreviewView(this);
    preview.checker(checkers);
    LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, 0, 1);
    pp.setMargins(0, dp(14), 0, dp(10));
    main.addView(preview, pp);
    time = text("Open a vector animation to begin", 13, FG);
    main.addView(time);
    scrub = new SeekBar(this);
    scrub.setContentDescription("Frame scrubber");
    scrub.setEnabled(false);
    main.addView(scrub, new LinearLayout.LayoutParams(-1, dp(36)));
    scrub.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onStartTrackingTouch(SeekBar s) {
            pause();
          }

          public void onStopTrackingTouch(SeekBar s) {
            savePosition();
          }

          public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (user && !busy) requestFrame(p);
          }
        });
    LinearLayout controls = horizontal();
    row(controls, button("‹ Frame", () -> step(-1)));
    play = button("Play", this::togglePlay);
    row(controls, play);
    row(controls, button("Frame ›", () -> step(1)));
    main.addView(controls);
    LinearLayout outputs = horizontal();
    export = button("Save PNG", () -> prepareExport(false, frame, frame, 1));
    sequence = button("Sequence ZIP", this::sequenceDialog);
    row(outputs, export);
    row(outputs, sequence);
    main.addView(outputs);
    LinearLayout files = horizontal();
    row(files, button("Open JSON / TGS", this::pick));
    row(files, button("Try sample", this::sample));
    main.addView(files);
    status = text("Vector pixels rendered with rlottie", 12, MUTED);
    status.setPadding(0, dp(8), 0, 0);
    main.addView(status);
    cancel =
        button(
            "Cancel operation",
            () -> {
              cancellation.set(true);
              status.setText(R.string.cancelling);
            });
    cancel.setVisibility(View.GONE);
    main.addView(cancel);
    update();
  }

  private void update() {
    boolean has = animation != null;
    play.setEnabled(has && !busy);
    export.setEnabled(has && !busy);
    sequence.setEnabled(has && !busy);
    scrub.setEnabled(has && !busy);
    play.setText(playing ? "Pause" : "Play");
    cancel.setVisibility(busy ? View.VISIBLE : View.GONE);
    if (has) {
      meta.setText(
          String.format(
              Locale.ROOT,
              "%d × %d  ·  %.2f fps  ·  %.2f s",
              animation.width,
              animation.height,
              animation.frameRate,
              animation.durationSeconds()));
      time.setText(
          String.format(
              Locale.ROOT,
              "Frame %d / %d     %.3f s",
              frame,
              animation.frameCount - 1,
              frame / animation.frameRate));
      scrub.setMax(animation.frameCount - 1);
      scrub.setProgress(frame);
    }
    title.setText(name);
  }

  private void message(String s) {
    status.setText(s);
  }

  private void error(Throwable e) {
    String s = e.getMessage();
    message(s == null ? e.getClass().getSimpleName() : s);
    new AlertDialog.Builder(this)
        .setTitle("Couldn’t complete that")
        .setMessage(status.getText())
        .setPositiveButton("OK", null)
        .show();
  }

  private void pause() {
    playing = false;
    ui.removeCallbacks(tick);
    if (play != null) play.setText(R.string.play);
  }

  private void step(int d) {
    if (animation != null && !busy) {
      pause();
      requestFrame(Math.max(0, Math.min(animation.frameCount - 1, frame + d)));
    }
  }

  private void togglePlay() {
    if (animation == null || busy) return;
    if (playing) {
      pause();
      savePosition();
      return;
    }
    if (frame == animation.frameCount - 1) requestFrame(0);
    playing = true;
    playFrame = frame;
    playStart = SystemClock.uptimeMillis();
    update();
    ui.post(tick);
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (!playing || animation == null || busy) return;
          int f =
              playFrame
                  + (int)
                      ((SystemClock.uptimeMillis() - playStart)
                          * animation.frameRate
                          * speed
                          / 1000);
          if (f >= animation.frameCount) {
            if (loop) f %= animation.frameCount;
            else {
              f = animation.frameCount - 1;
              pause();
            }
          }
          requestFrame(f);
          if (playing) ui.postDelayed(this, 25);
        }
      };

  private void requestFrame(int f) {
    if (animation == null || busy || dead) return;
    frame = desiredFrame = f;
    update();
    if (renderBusy) return;
    renderBusy = true;
    int request = f, token = generation;
    MotionAnimation target = animation;
    worker.execute(
        () -> {
          try {
            Bitmap bitmap = target.render(request, side);
            ui.post(
                () -> {
                  renderBusy = false;
                  if (dead || token != generation) {
                    bitmap.recycle();
                    return;
                  }
                  preview.setBitmap(bitmap);
                  if (desiredFrame != request) requestFrame(desiredFrame);
                });
          } catch (Throwable e) {
            ui.post(
                () -> {
                  renderBusy = false;
                  pause();
                  if (!dead && token == generation) error(e);
                });
          }
        });
  }

  interface Input {
    InputStream open() throws IOException;
  }

  private void load(Input input, String label, int position) {
    if (busy) return;
    pause();
    busy = true;
    cancellation = new AtomicBoolean();
    AtomicBoolean stop = cancellation;
    int token = ++generation;
    message("Opening vector animation…");
    update();
    worker.execute(
        () -> {
          MotionAnimation next = null;
          try {
            byte[] bytes = AnimationInput.read(input.open(), stop::get);
            next = new MotionAnimation(bytes);
            int f = Math.max(0, Math.min(next.frameCount - 1, position));
            Bitmap first = next.render(f, side);
            if (stop.get()) {
              first.recycle();
              throw new CancellationException();
            }
            File tmp = new File(getFilesDir(), "animation-new.json");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
              out.write(bytes);
            }
            Files.move(
                tmp.toPath(),
                new File(getFilesDir(), "animation.json").toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            MotionAnimation ready = next;
            next = null;
            ui.post(
                () -> {
                  if (dead || token != generation) {
                    first.recycle();
                    ready.close();
                    return;
                  }
                  MotionAnimation old = animation;
                  animation = ready;
                  if (old != null) worker.execute(old::close);
                  currentJson = bytes;
                  name = label;
                  frame = desiredFrame = f;
                  busy = false;
                  preview.setBitmap(first);
                  prefs.edit().putString("name", name).putInt("frame", frame).apply();
                  message("Ready · transparent PNGs stay transparent");
                  update();
                });
          } catch (Throwable e) {
            if (next != null) next.close();
            ui.post(
                () -> {
                  if (dead) return;
                  busy = false;
                  update();
                  if (e instanceof CancellationException)
                    message("Opening cancelled. Your previous animation is kept.");
                  else error(e);
                });
          }
        });
  }

  private void sample() {
    if (busy) return;
    load(() -> getAssets().open("precomp.tgs"), "Orbit · sample animation", 0);
  }

  private void pick() {
    if (busy) return;
    pause();
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i, IMPORT);
  }

  private String displayName(Uri uri) {
    try (Cursor c =
        getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst()) {
        String s = c.getString(0);
        return s.length() > 100 ? s.substring(0, 100) : s;
      }
    } catch (RuntimeException ignored) {
    }
    return "Imported animation";
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) {
      if (request == EXPORT) clearPending();
      message(
          request == IMPORT
              ? "Import cancelled. Current animation kept."
              : "Save cancelled. No output was written.");
      return;
    }
    Uri uri = data.getData();
    if (request == IMPORT)
      load(
          () -> {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new IOException("Document is unavailable");
            return in;
          },
          displayName(uri),
          0);
    else if (request == EXPORT) copyExport(uri);
  }

  private void sequenceDialog() {
    if (animation == null || busy) return;
    pause();
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(22), 0, dp(22), 0);
    box.addView(
        text(
            "Transparent PNGs + timing manifest. Up to 120 frames, 512 px and 64 MiB. Frame numbers"
                + " start at 0.",
            14,
            MUTED));
    EditText start = input(box, "First frame", 0),
        end = input(box, "Last frame", animation.frameCount - 1),
        step = input(box, "Every N frames", Math.max(1, (animation.frameCount + 119) / 120));
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("Export a sequence")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Prepare ZIP", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      try {
                        int first = Integer.parseInt(start.getText().toString()),
                            last = Integer.parseInt(end.getText().toString()),
                            every = Integer.parseInt(step.getText().toString());
                        FrameExport.count(
                            first, last, every, animation.frameCount, Math.min(side, 512));
                        d.dismiss();
                        prepareExport(true, first, last, every);
                      } catch (IllegalArgumentException e) {
                        start.setError(e.getMessage());
                      }
                    }));
    d.show();
  }

  private EditText input(LinearLayout box, String label, int value) {
    box.addView(text(label, 13, MUTED));
    EditText e = new EditText(this);
    e.setText(String.valueOf(value));
    e.setTextColor(FG);
    e.setSingleLine();
    e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    box.addView(e);
    return e;
  }

  private void prepareExport(boolean zip, int first, int last, int step) {
    if (animation == null || busy) return;
    pause();
    busy = true;
    cancellation = new AtomicBoolean();
    AtomicBoolean stop = cancellation;
    MotionAnimation target = animation;
    File file = new File(getCacheDir(), zip ? "sequence-ready.zip" : "frame-ready.png");
    int outputSide = zip ? Math.min(side, 512) : side;
    message(zip ? "Rendering sequence…" : "Rendering PNG…");
    update();
    worker.execute(
        () -> {
          try (OutputStream out = new FileOutputStream(file)) {
            if (zip)
              FrameExport.zip(
                  target,
                  first,
                  last,
                  step,
                  outputSide,
                  out,
                  stop::get,
                  n -> ui.post(() -> message("Rendered " + n + " frames…")));
            else FrameExport.png(target, first, outputSide, out);
            if (stop.get()) throw new CancellationException();
            ui.post(
                () -> {
                  if (dead) {
                    file.delete();
                    return;
                  }
                  busy = false;
                  pendingExport = file;
                  pendingMime = zip ? "application/zip" : "image/png";
                  pendingName =
                      zip
                          ? "motion-sequence.zip"
                          : String.format(Locale.ROOT, "motion-frame-%05d.png", first);
                  update();
                  message("Prepared. Choose where to save.");
                  Intent intent =
                      new Intent(Intent.ACTION_CREATE_DOCUMENT)
                          .setType(pendingMime)
                          .addCategory(Intent.CATEGORY_OPENABLE)
                          .putExtra(Intent.EXTRA_TITLE, pendingName);
                  startActivityForResult(intent, EXPORT);
                });
          } catch (Throwable e) {
            file.delete();
            ui.post(
                () -> {
                  if (dead) return;
                  busy = false;
                  update();
                  if (e instanceof CancellationException)
                    message("Export cancelled. No destination file was created.");
                  else error(e);
                });
          }
        });
  }

  private void copyExport(Uri uri) {
    File file = pendingExport;
    if (file == null || !file.isFile()) {
      error(new IOException("Prepared export expired. Please export again."));
      return;
    }
    String savedName = displayName(uri);
    busy = true;
    cancellation = new AtomicBoolean();
    AtomicBoolean stop = cancellation;
    update();
    message("Saving local document…");
    worker.execute(
        () -> {
          try (InputStream in = new FileInputStream(file);
              OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Could not open destination");
            byte[] buffer = new byte[32768];
            int n;
            while ((n = in.read(buffer)) != -1) {
              if (stop.get()) throw new CancellationException();
              out.write(buffer, 0, n);
            }
            out.flush();
            ui.post(
                () -> {
                  if (dead) return;
                  busy = false;
                  clearPending();
                  update();
                  message("Saved " + savedName + " · transparent pixels preserved");
                });
          } catch (Throwable e) {
            try {
              DocumentsContract.deleteDocument(getContentResolver(), uri);
            } catch (Exception ignored) {
            }
            ui.post(
                () -> {
                  if (dead) return;
                  busy = false;
                  clearPending();
                  update();
                  if (e instanceof CancellationException)
                    message("Save cancelled. Incomplete destination removed when supported.");
                  else error(e);
                });
          }
        });
  }

  private void clearPending() {
    if (pendingExport != null) {
      pendingExport.delete();
      pendingExport = null;
    }
  }

  private void menu() {
    new AlertDialog.Builder(this)
        .setTitle("Oritwig Motion")
        .setItems(
            new String[] {
              "Playback & export settings", "About, licenses & limits", "Clear last animation"
            },
            (d, w) -> {
              if (w == 0) settings();
              else if (w == 1) about();
              else clearDialog();
            })
        .show();
  }

  private void settings() {
    pause();
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(20), 0, dp(20), 0);
    CheckBox l = new CheckBox(this);
    l.setText(R.string.loop);
    l.setChecked(loop);
    box.addView(l);
    CheckBox c = new CheckBox(this);
    c.setText(R.string.checker);
    c.setChecked(checkers);
    box.addView(c);
    box.addView(text("Preview / single PNG maximum side", 14, MUTED));
    Spinner sizes = new Spinner(this);
    sizes.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"256 px", "512 px", "1024 px"}));
    sizes.setSelection(side == 256 ? 0 : side == 1024 ? 2 : 1);
    box.addView(sizes);
    box.addView(text("Playback speed (exports use source timing)", 14, MUTED));
    Spinner speeds = new Spinner(this);
    speeds.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"0.5×", "1×", "2×"}));
    speeds.setSelection(speed == 0.5f ? 0 : speed == 2 ? 2 : 1);
    box.addView(speeds);
    new AlertDialog.Builder(this)
        .setTitle("Settings")
        .setView(box)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Save",
            (d, w) -> {
              loop = l.isChecked();
              checkers = c.isChecked();
              side = new int[] {256, 512, 1024}[sizes.getSelectedItemPosition()];
              speed = new float[] {0.5f, 1, 2}[speeds.getSelectedItemPosition()];
              prefs
                  .edit()
                  .putBoolean("loop", loop)
                  .putBoolean("checker", checkers)
                  .putInt("side", side)
                  .putFloat("speed", speed)
                  .apply();
              preview.checker(checkers);
              requestFrame(frame);
              message("Settings saved on this device");
            })
        .show();
  }

  private static byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int n;
    while ((n = in.read(buffer)) != -1) {
      if (b.size() + n > 2 * 1024 * 1024) throw new IOException("Notice is too large");
      b.write(buffer, 0, n);
    }
    return b.toByteArray();
  }

  private void about() {
    pause();
    String notices;
    try (InputStream in = getAssets().open("NOTICE.txt")) {
      notices = new String(readAll(in), StandardCharsets.UTF_8);
    } catch (IOException e) {
      notices = "License text unavailable";
    }
    TextView t =
        text(
            "Oritwig Motion 0.1\n\n"
                + "Open local vector JSON / gzipped TGS, inspect timing and export transparent"
                + " frames. No account, network permission, ads or telemetry.\n\n"
                + "Limits: 2 MiB compressed and decoded JSON; 1,800 frames; 120 s; 128 layers;"
                + " 4,096 px source canvas. Precompositions are limited to 128 assets, 4,096"
                + " references, 4,096 expanded instances and 80,000 expanded values. Repeaters,"
                + " polystar/polygon generators, dashed strokes, raster assets, text, expressions"
                + " and 3D are explicitly unsupported. Historical rlottie support differs from"
                + " modern Lottie. Imported JSON and settings stay in app-private storage. Preview"
                + " may skip frames on slower devices; exports render selected source frames."
                + " Cancel takes effect between native calls.\n\n"
                + notices,
            13,
            FG);
    t.setPadding(dp(18), dp(12), dp(18), dp(12));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(t);
    new AlertDialog.Builder(this)
        .setTitle("About & licenses")
        .setNeutralButton("Full license texts", (d, w) -> licenseList())
        .setView(scroll)
        .setPositiveButton("Close", null)
        .show();
  }

  private void licenseList() {
    try {
      String[] names = getAssets().list("licenses");
      new AlertDialog.Builder(this)
          .setTitle("Full license texts")
          .setItems(
              names,
              (d, index) -> {
                try (InputStream in = getAssets().open("licenses/" + names[index])) {
                  licensePage(names[index], new String(readAll(in), StandardCharsets.UTF_8), 0);
                } catch (IOException e) {
                  error(e);
                }
              })
          .setNegativeButton("Close", null)
          .show();
    } catch (IOException e) {
      error(e);
    }
  }

  private void licensePage(String label, String body, int page) {
    int pageSize = 8000, pages = (body.length() + pageSize - 1) / pageSize;
    TextView content =
        text(
            body.substring(page * pageSize, Math.min(body.length(), (page + 1) * pageSize)),
            12,
            FG);
    content.setTextIsSelectable(true);
    content.setPadding(dp(16), dp(12), dp(16), dp(12));
    ScrollView scroll = new ScrollView(this);
    scroll.addView(content);
    AlertDialog.Builder dialog =
        new AlertDialog.Builder(this)
            .setTitle(label + " · " + (page + 1) + "/" + pages)
            .setView(scroll)
            .setNegativeButton("Close", null);
    if (page + 1 < pages)
      dialog.setPositiveButton("Next", (d, w) -> licensePage(label, body, page + 1));
    if (page > 0) dialog.setNeutralButton("Previous", (d, w) -> licensePage(label, body, page - 1));
    dialog.show();
  }

  private void clearDialog() {
    if (busy) return;
    new AlertDialog.Builder(this)
        .setTitle("Clear last animation?")
        .setMessage(
            "Removes this app’s saved copy and position. Your original document and exported files"
                + " stay available.")
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Clear",
            (d, w) -> {
              pause();
              generation++;
              MotionAnimation old = animation;
              animation = null;
              if (old != null) worker.execute(old::close);
              new File(getFilesDir(), "animation.json").delete();
              prefs.edit().remove("name").remove("frame").apply();
              currentJson = null;
              name = "No animation open";
              frame = 0;
              preview.setBitmap(null);
              meta.setText(R.string.private_device);
              time.setText(R.string.begin);
              message("Last animation cleared");
              update();
            })
        .show();
  }

  private void savePosition() {
    if (animation != null) prefs.edit().putInt("frame", frame).apply();
  }

  @Override
  protected void onPause() {
    super.onPause();
    pause();
    savePosition();
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (busy) cancellation.set(true);
  }

  @Override
  protected void onDestroy() {
    dead = true;
    generation++;
    pause();
    cancellation.set(true);
    MotionAnimation old = animation;
    if (old != null) worker.execute(old::close);
    worker.shutdown();
    clearPending();
    preview.setBitmap(null);
    super.onDestroy();
  }
}
