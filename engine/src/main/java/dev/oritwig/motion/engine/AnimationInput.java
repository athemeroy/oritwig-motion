package dev.oritwig.motion.engine;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;
import org.json.*;

/** Bounded input/resource policy, not an animation parser or renderer. GPL-3.0-or-later. */
public final class AnimationInput {
  public static final int MAX_BYTES = 2 * 1024 * 1024, MAX_DEPTH = 32, MAX_NODES = 80000;
  public static final int MAX_ASSETS = 128,
      MAX_REFERENCES = 4096,
      MAX_EXPANDED_INSTANCES = 4096,
      MAX_EXPANDED_VALUES = 80000;

  private AnimationInput() {}

  public static byte[] read(InputStream source, BooleanSupplier cancel) throws IOException {
    byte[] raw = bounded(source, MAX_BYTES, cancel);
    byte[] json =
        (raw.length > 1 && (raw[0] & 255) == 31 && (raw[1] & 255) == 139)
            ? bounded(new GZIPInputStream(new ByteArrayInputStream(raw)), MAX_BYTES, cancel)
            : raw;
    validate(json);
    return json;
  }

  private static byte[] bounded(InputStream in, int max, BooleanSupplier cancel)
      throws IOException {
    try (in;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        if (cancel.getAsBoolean()) throw new CancellationException();
        if (out.size() + n > max) throw new IOException("File or decompressed JSON exceeds 2 MiB");
        out.write(b, 0, n);
      }
      return out.toByteArray();
    }
  }

  public static void validate(byte[] data) {
    if (data == null || data.length < 2 || data.length > MAX_BYTES)
      throw bad("JSON must be 2 bytes–2 MiB");
    String s;
    try {
      s =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(data))
              .toString();
    } catch (CharacterCodingException e) {
      throw bad("JSON must be valid UTF-8");
    }
    int depth = 0;
    boolean quoted = false, escape = false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == 0) throw bad("NUL bytes are unsupported");
      if (quoted) {
        if (escape) escape = false;
        else if (c == '\\') escape = true;
        else if (c == '"') quoted = false;
      } else if (c == '"') quoted = true;
      else if (c == '[' || c == '{') {
        if (++depth > MAX_DEPTH) throw bad("JSON nesting exceeds 32");
      } else if (c == ']' || c == '}') {
        if (--depth < 0) throw bad("Unbalanced JSON");
      }
    }
    if (quoted || depth != 0) throw bad("Incomplete JSON");
    try {
      JSONTokener tok = new JSONTokener(s);
      Object value = tok.nextValue();
      if (!(value instanceof JSONObject) || tok.nextClean() != 0)
        throw bad("Expected one JSON object");
      JSONObject root = (JSONObject) value;
      double w = number(root, "w"),
          h = number(root, "h"),
          ip = number(root, "ip"),
          op = number(root, "op"),
          fps = number(root, "fr");
      if (w < 1 || h < 1 || w > 4096 || h > 4096 || w != Math.floor(w) || h != Math.floor(h))
        throw bad("Canvas must be 1–4096 pixels per side");
      if (ip < 0
          || ip != Math.floor(ip)
          || op != Math.floor(op)
          || op - ip < 1
          || op - ip > 1800
          || fps < 1
          || fps > 120
          || (op - ip) / fps > 120)
        throw bad("Use 1–1800 whole frames, 1–120 fps and at most 120 seconds");
      if (!root.has("layers") || root.getJSONArray("layers").length() == 0)
        throw bad("Animation has no layers");
      int[] counts = {0, 0, 0};
      walk(root, counts);
      Map<String, RefNode> graph = new LinkedHashMap<>();
      int[] referenceCount = {0};
      JSONArray assets = root.optJSONArray("assets");
      if (assets != null) {
        if (assets.length() > MAX_ASSETS) throw bad("Animation exceeds 128 vector assets");
        for (int i = 0; i < assets.length(); i++) {
          JSONObject a = assets.getJSONObject(i);
          if (a.has("p") || a.has("u") || a.has("e"))
            throw bad("Raster or external assets are unsupported; use vector-only JSON/TGS");
          String id = a.getString("id");
          if (id.equals("$root")) throw bad("Reserved asset identifier");
          if (graph.containsKey(id)) throw bad("Duplicate asset identifier");
          graph.put(id, refs(a, referenceCount));
        }
      }
      graph.put("$root", refs(root.getJSONArray("layers"), referenceCount));
      Map<String, RefCost> completed = new HashMap<>();
      Set<String> active = new HashSet<>();
      for (String id : graph.keySet()) checkRefs(id, graph, active, completed, 0);
    } catch (JSONException e) {
      throw bad("Invalid animation JSON: " + e.getMessage());
    }
  }

  private static double number(JSONObject o, String k) throws JSONException {
    double n = o.getDouble(k);
    if (!Double.isFinite(n)) throw bad("Non-finite metadata");
    return n;
  }

  private static void walk(Object o, int[] counts) throws JSONException {
    if (++counts[0] > MAX_NODES) throw bad("Animation complexity exceeds 80,000 values");
    if (o instanceof JSONObject j) {
      if (j.has("ty")) {
        Object type = j.get("ty");
        if (("st".equals(type) || "gs".equals(type)) && j.has("d")) {
          JSONArray dash = j.optJSONArray("d");
          if (dash == null || dash.length() != 0)
            throw bad("Dashed strokes are unsupported; use solid strokes or explicit paths");
        }
        if ("sr".equals(type))
          throw bad("Polystar and polygon shapes are unsupported; use bounded explicit paths");
        if ("rp".equals(type))
          throw bad("Repeater shapes are unsupported; expand them into bounded vector shapes");
        if (type instanceof Number) {
          int t = ((Number) type).intValue();
          if (t == 2 || t == 5 || t == 6 || t == 13)
            throw bad("Raster, text, audio and camera layers are unsupported");
          if (++counts[1] > 128) throw bad("Animation exceeds 128 layers");
        } else if (++counts[2] > 2048) throw bad("Animation exceeds 2,048 shape objects");
      }
      if (j.opt("x") instanceof String) throw bad("Expressions are unsupported");
      if (j.has("fonts") || j.has("chars")) throw bad("Font and text content is unsupported");
      if (j.optInt("ddd", 0) != 0) throw bad("3D layers are unsupported");
      Iterator<String> it = j.keys();
      while (it.hasNext()) walk(j.get(it.next()), counts);
    } else if (o instanceof JSONArray a) {
      if (a.length() > 12000) throw bad("Array exceeds 12,000 items");
      for (int i = 0; i < a.length(); i++) walk(a.get(i), counts);
    } else if (o instanceof Number n
        && (!Double.isFinite(n.doubleValue()) || Math.abs(n.doubleValue()) > 1000000))
      throw bad("Numeric value exceeds limits");
    else if (o instanceof String str && str.length() > 65536) throw bad("String exceeds 64 KiB");
  }

  private static final class RefNode {
    final Map<String, Integer> references = new LinkedHashMap<>();
    int localValues;
  }

  private static final class RefCost {
    final int height;
    final long instances, values;

    RefCost(int height, long instances, long values) {
      this.height = height;
      this.instances = instances;
      this.values = values;
    }
  }

  private static RefNode refs(Object o, int[] total) throws JSONException {
    RefNode node = new RefNode();
    collectRefs(o, node, total);
    return node;
  }

  private static void collectRefs(Object o, RefNode node, int[] total) throws JSONException {
    node.localValues++;
    if (o instanceof JSONObject j) {
      if (j.has("refId")) {
        if (++total[0] > MAX_REFERENCES) throw bad("Animation exceeds 4,096 asset references");
        String id = j.getString("refId");
        node.references.put(id, node.references.getOrDefault(id, 0) + 1);
      }
      Iterator<String> it = j.keys();
      while (it.hasNext()) collectRefs(j.get(it.next()), node, total);
    } else if (o instanceof JSONArray a)
      for (int i = 0; i < a.length(); i++) collectRefs(a.get(i), node, total);
  }

  private static RefCost checkRefs(
      String id,
      Map<String, RefNode> graph,
      Set<String> active,
      Map<String, RefCost> completed,
      int depth) {
    if (depth > 8 || active.contains(id))
      throw bad("Recursive or deeply nested precompositions are unsupported");
    RefCost cached = completed.get(id);
    if (cached != null) {
      if (depth + cached.height > 8)
        throw bad("Recursive or deeply nested precompositions are unsupported");
      return cached;
    }
    RefNode node = graph.get(id);
    if (node == null) throw bad("Missing embedded vector asset: " + id);
    active.add(id);
    int height = 0;
    long instances = 1, values = node.localValues;
    for (Map.Entry<String, Integer> edge : node.references.entrySet()) {
      RefCost child = checkRefs(edge.getKey(), graph, active, completed, depth + 1);
      height = Math.max(height, child.height + 1);
      int copies = edge.getValue();
      // Count every native scene instance, including duplicate references. Check before
      // multiplication so a small DAG cannot expand into unbounded native work.
      if (copies > (MAX_EXPANDED_INSTANCES - instances) / child.instances
          || copies > (MAX_EXPANDED_VALUES - values) / child.values)
        throw bad("Expanded precomposition exceeds 4,096 instances or 80,000 values");
      instances += copies * child.instances;
      values += copies * child.values;
    }
    RefCost cost = new RefCost(height, instances, values);
    active.remove(id);
    completed.put(id, cost);
    return cost;
  }

  private static IllegalArgumentException bad(String s) {
    return new IllegalArgumentException(s);
  }
}
