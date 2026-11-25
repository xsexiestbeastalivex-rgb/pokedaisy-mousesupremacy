package com.pokedaisy.mousesupremacy.probe;

import static com.pokedaisy.mousesupremacy.probe.PointerState.OUTSIDE;
import static com.pokedaisy.mousesupremacy.probe.Trace.fields;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ProbeActivity extends Activity implements InputManager.InputDeviceListener {
    private static final int MAX_DEVICES = 64;
    private final Trace trace = new Trace(Trace.CAPACITY);
    private final SparseIntArray deviceAliases = new SparseIntArray();
    private final PointerState[] devices = new PointerState[MAX_DEVICES + 1];
    private final WsaPointer[] wsaPointers = new WsaPointer[MAX_DEVICES + 1];
    private final String[] deviceIdentities = new String[MAX_DEVICES + 1];
    private final Rect aBounds = new Rect();
    private final Rect bBounds = new Rect();
    private final Rect nextA = new Rect();
    private final Rect nextB = new Rect();
    private final int[] location = new int[2];
    private LinearLayout root;
    private Region a;
    private Region b;
    private TextView status;
    private Button capture;
    private Button mark;
    private String provenance;
    private String session;
    private boolean wsaCompatibility;
    private long wsaInvalidatedUptime;
    private String active = OUTSIDE;
    private String rawHit = OUTSIDE;
    private String latest = "Waiting for pointer";
    private String captureStatus = "Capture off";
    private String scroll = "—";
    private boolean focused;
    private boolean exporting;
    private boolean refreshPending;
    private int activityMotionDepth;
    private int revision;
    private int marker;
    private int windowWidth;
    private int windowHeight;
    private int densityDpi;
    private int orientation;
    private int windowMode;
    private int inputAlias;
    private int heldButtons;
    private int rawButtons;
    private float screenX = Float.NaN;
    private float screenY = Float.NaN;
    private long startUtcMillis;
    private long startUptimeMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String label = intent.getStringExtra("provenance");
        provenance = "unspecified";
        if ("physical-wsa".equals(label) || "physical-android".equals(label)
                || "injected".equals(label)) provenance = label;
        wsaCompatibility = "physical-wsa".equals(provenance)
                && "wsa-experimental".equals(intent.getStringExtra("inputProfile"))
                && "Microsoft Corporation".equalsIgnoreCase(Build.MANUFACTURER)
                && Build.HARDWARE.startsWith("windows_");
        String requestedSession = intent.getStringExtra("session");
        session = requestedSession != null && requestedSession.matches("[0-9a-f]{32}")
                ? requestedSession : UUID.randomUUID().toString().replace("-", "");

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout buttons = new LinearLayout(this);
        capture = new Button(this);
        capture.setText("Start capture (F8)");
        capture.setOnClickListener(view -> root.post(this::toggleCapture));
        mark = new Button(this);
        mark.setText("Mark (F9)");
        mark.setEnabled(false);
        mark.setOnClickListener(view -> mark());
        buttons.addView(capture);
        buttons.addView(mark);
        root.addView(buttons);

        status = new TextView(this);
        status.setTextSize(12);
        status.setLines(5);
        status.setPadding(dp(8), 0, dp(8), 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout regions = new LinearLayout(this);
        regions.setLayoutDirection(LinearLayout.LAYOUT_DIRECTION_LTR);
        a = new Region("A", 0xff17354b);
        b = new Region("B", 0xff30384b);
        regions.addView(a, new LinearLayout.LayoutParams(0, -1, 1));
        regions.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(regions, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> layout("global-layout"));
        root.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(android.view.View view) {}
            @Override public void onViewDetachedFromWindow(android.view.View view) {
                lifecycle("view-detached");
            }
        });
        refresh();
    }

    @Override protected void onStart() {
        super.onStart();
        getSystemService(InputManager.class).registerInputDeviceListener(this, null);
    }

    @Override protected void onStop() {
        getSystemService(InputManager.class).unregisterInputDeviceListener(this);
        super.onStop();
    }

    @Override protected void onPause() {
        lifecycle("pause");
        super.onPause();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        layout("configuration");
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        focused = hasFocus;
        layout("focus");
        note("window-focus", fields("hasFocus", hasFocus));
        if (!hasFocus) cancelAll("focus-loss");
        refresh();
    }

    @Override public void onInputDeviceAdded(int deviceId) {}
    @Override public void onInputDeviceChanged(int deviceId) {}

    @Override public void onInputDeviceRemoved(int deviceId) {
        int alias = deviceAliases.get(deviceId);
        if (alias == 0) return;
        cancel(alias, "device-removed");
        deviceAliases.delete(deviceId);
        if (alias == inputAlias) {
            resetDisplay();
            refresh();
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        Map<String, Object> observation = motion(event, "activity.touch", null);
        activityMotionDepth++;
        try {
            return handled(observation, super.dispatchTouchEvent(event));
        } finally {
            activityMotionDepth--;
        }
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        Map<String, Object> observation = motion(event, "activity.generic", null);
        activityMotionDepth++;
        try {
            return handled(observation, super.dispatchGenericMotionEvent(event));
        } finally {
            activityMotionDepth--;
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean control = code == KeyEvent.KEYCODE_F8 || code == KeyEvent.KEYCODE_F9;
        String key = switch (code) {
            case KeyEvent.KEYCODE_BACK -> "BACK";
            case KeyEvent.KEYCODE_ESCAPE -> "ESCAPE";
            case KeyEvent.KEYCODE_F8 -> "F8";
            case KeyEvent.KEYCODE_F9 -> "F9";
            default -> "other";
        };
        Map<String, Object> observation = fields("key", key,
                "keyCode", "other".equals(key) ? null : code, "action", event.getAction(),
                "repeatCount", event.getRepeatCount(), "eventUptimeMs", event.getEventTime(),
                "source", event.getSource(), "device", alias(event.getDeviceId()));
        note("activity.key", observation);
        if (control) {
            handled(observation, true);
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                if (code == KeyEvent.KEYCODE_F8) toggleCapture();
                if (code == KeyEvent.KEYCODE_F9) mark();
            }
            return true;
        }
        return handled(observation, super.dispatchKeyEvent(event));
    }

    @Override public void onBackPressed() {
        // Otherwise a secondary click delivered as Back can close the probe before export.
        note("activity.back", fields());
        latest = "Android Back";
        refresh();
    }

    private Map<String, Object> motion(MotionEvent event, String point, String receiver) {
        layout("dispatch");
        long receipt = SystemClock.uptimeMillis();
        long receiptNanos = SystemClock.elapsedRealtimeNanos();
        int alias = alias(event.getDeviceId());
        int action = event.getActionMasked();
        int tool = event.getToolType(0);
        int source = event.getSource();
        int buttons = event.getButtonState();
        boolean mouse = tool == MotionEvent.TOOL_TYPE_MOUSE
                && event.isFromSource(InputDevice.SOURCE_MOUSE);
        float x = event.getRawX();
        float y = event.getRawY();
        String hit = contains(aBounds, x, y) ? "A" : contains(bBounds, x, y) ? "B" : OUTSIDE;
        float h = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
        float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        PointerState.Action kind = action(action);
        boolean authority = receiver == null || activityMotionDepth == 0;
        PointerState.Result result = null;
        String interpretation = null;
        if (authority) {
            if (WsaPointer.accepts(wsaCompatibility, source, tool, alias)) {
                long downTime = event.getDownTime();
                boolean start = kind == PointerState.Action.DOWN || kind == PointerState.Action.BUTTON_PRESS;
                if (!start || downTime > wsaInvalidatedUptime || event.getPointerCount() != 1) {
                    result = wsaPointers[alias].observe(new WsaPointer.Sample(kind, hit, x, y,
                            downTime, event.getEventTime(), event.getPointerId(0), event.getPointerCount(),
                            event.getActionButton(), buttons, focused, event.getHistorySize() != 0));
                }
                interpretation = "wsa-experimental";
            } else if (alias != 0 && (mouse || kind == PointerState.Action.CANCEL)) {
                result = devices[alias].observe(kind, hit, event.getActionButton(), buttons, focused);
                interpretation = "android-mouse";
            }
            if (focused && event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)
                    && kind != PointerState.Action.OTHER) {
                if (kind == PointerState.Action.CANCEL || kind == PointerState.Action.HOVER_EXIT) {
                    rawHit = OUTSIDE;
                    screenX = screenY = Float.NaN;
                } else {
                    screenX = x;
                    screenY = y;
                    rawHit = hit;
                }
                inputAlias = alias;
                rawButtons = buttons;
                if (kind == PointerState.Action.SCROLL) scroll = String.format(Locale.ROOT, "%.2f / %.2f", h, v);
                latest = kind + " source=0x" + Integer.toHexString(source) + " tool=" + tool;
                scheduleRefresh();
            }
            if (result != null && focused) {
                active = result.region();
                heldButtons = result.held();
                latest = interpretation + " " + result.phase();
            }
        }

        if (!trace.recording()) return null;
        Map<String, Object> raw = fields("kind", point, "receiptUptimeMs", receipt,
                "receiptElapsedNanos", receiptNanos, "eventUptimeMs", event.getEventTime(),
                "downUptimeMs", event.getDownTime(), "device", alias,
                "deviceIdentity", alias == 0 ? "unaliased" : deviceIdentities[alias], "source", source,
                "toolType", tool, "action", event.getAction(), "actionMasked", action,
                "actionButton", event.getActionButton(), "buttonState", buttons,
                "pointerId", event.getPointerId(0), "pointerCount", event.getPointerCount(),
                "actionIndex", event.getActionIndex(), "historySize", event.getHistorySize(),
                "x", finite(event.getX()), "y", finite(event.getY()), "rawX", finite(x), "rawY", finite(y),
                "hScroll", finite(h), "vScroll", finite(v), "flags", event.getFlags(),
                "focused", focused, "receiverRegion", receiver, "hitRegion", hit,
                "layoutRevision", revision, "handled", null,
                "normalized", normalized(result, interpretation));
        boolean move = (kind == PointerState.Action.MOVE || kind == PointerState.Action.HOVER_MOVE)
                && (result == null || (!result.regionChanged() && !result.firstUnpressedMove()
                && result.cancelled() == 0 && !"drag-start".equals(result.phase())));
        String contact = kind == PointerState.Action.MOVE ? ":" + event.getDownTime() : "";
        trace.add(raw, move ? point + ":" + kind + contact : null);
        return raw;
    }

    private static PointerState.Action action(int masked) {
        return switch (masked) {
            case MotionEvent.ACTION_DOWN -> PointerState.Action.DOWN;
            case MotionEvent.ACTION_UP -> PointerState.Action.UP;
            case MotionEvent.ACTION_MOVE -> PointerState.Action.MOVE;
            case MotionEvent.ACTION_CANCEL -> PointerState.Action.CANCEL;
            case MotionEvent.ACTION_HOVER_ENTER -> PointerState.Action.HOVER_ENTER;
            case MotionEvent.ACTION_HOVER_MOVE -> PointerState.Action.HOVER_MOVE;
            case MotionEvent.ACTION_HOVER_EXIT -> PointerState.Action.HOVER_EXIT;
            case MotionEvent.ACTION_SCROLL -> PointerState.Action.SCROLL;
            case MotionEvent.ACTION_BUTTON_PRESS -> PointerState.Action.BUTTON_PRESS;
            case MotionEvent.ACTION_BUTTON_RELEASE -> PointerState.Action.BUTTON_RELEASE;
            default -> PointerState.Action.OTHER;
        };
    }

    private static Map<String, Object> normalized(PointerState.Result result, String interpretation) {
        if (result == null) return null;
        return fields("interpretation", interpretation, "phase", result.phase(), "region", result.region(),
                "pressed", result.pressed(), "dragging", result.dragging(), "click", result.click(),
                "released", result.released(), "cancelled", result.cancelled(), "held", result.held(),
                "beforeFirstPress", result.beforeFirstPress(),
                "firstUnpressedMove", result.firstUnpressedMove(), "regionChanged", result.regionChanged());
    }

    private int nextAlias;

    private int alias(int deviceId) {
        int alias = deviceAliases.get(deviceId);
        if (alias != 0 || nextAlias == MAX_DEVICES) return alias;
        alias = ++nextAlias;
        deviceAliases.put(deviceId, alias);
        devices[alias] = new PointerState();
        if (wsaCompatibility) wsaPointers[alias] = new WsaPointer(ViewConfiguration.get(this).getScaledTouchSlop());
        InputDevice device = InputDevice.getDevice(deviceId);
        deviceIdentities[alias] = device == null
                ? (deviceId < 0 ? "negative-unregistered" : "unregistered")
                : (device.isVirtual() ? "virtual" : "registered");
        return alias;
    }

    private void layout(String reason) {
        if (a == null || a.getWidth() == 0 || b.getWidth() == 0) return;
        bounds(a, nextA);
        bounds(b, nextB);
        int width = getWindow().getDecorView().getWidth();
        int height = getWindow().getDecorView().getHeight();
        Configuration config = getResources().getConfiguration();
        int mode = isInMultiWindowMode() ? 1 : 0;
        if (width == windowWidth && height == windowHeight && config.densityDpi == densityDpi
                && config.orientation == orientation && mode == windowMode
                && nextA.equals(aBounds) && nextB.equals(bBounds)) return;
        if (revision != 0) cancelAll("layout-change");
        aBounds.set(nextA);
        bBounds.set(nextB);
        windowWidth = width;
        windowHeight = height;
        densityDpi = config.densityDpi;
        orientation = config.orientation;
        windowMode = mode;
        revision++;
        note("layout", geometry(reason));
        refresh();
    }

    private Map<String, Object> geometry(String reason) {
        return fields("reason", reason, "windowWidth", windowWidth, "windowHeight", windowHeight,
                "densityDpi", densityDpi, "orientation", orientation, "multiWindow", windowMode == 1,
                "regionA", rectFields(aBounds), "regionB", rectFields(bBounds));
    }

    private void bounds(Region region, Rect destination) {
        region.getLocationOnScreen(location);
        destination.set(location[0], location[1], location[0] + region.getWidth(), location[1] + region.getHeight());
    }

    private static Map<String, Object> rectFields(Rect bounds) {
        return fields("left", bounds.left, "top", bounds.top, "right", bounds.right, "bottom", bounds.bottom);
    }

    private static boolean contains(Rect region, float x, float y) {
        return x >= region.left && x < region.right && y >= region.top && y < region.bottom;
    }

    private void lifecycle(String reason) {
        note("lifecycle", fields("reason", reason));
        cancelAll(reason);
        refresh();
    }

    private void cancelAll(String reason) {
        wsaInvalidatedUptime = SystemClock.uptimeMillis();
        for (int i = 1; i <= nextAlias; i++) cancel(i, reason);
        resetDisplay();
    }

    private void cancel(int alias, String reason) {
        recordCancellation(alias, devices[alias].cancel(reason), "android-mouse");
        if (wsaPointers[alias] != null) {
            recordCancellation(alias, wsaPointers[alias].cancel(reason), "wsa-experimental");
        }
    }

    private void recordCancellation(int alias, PointerState.Result result, String interpretation) {
        if (result.cancelled() != 0 || result.regionChanged()) {
            note("cancellation", fields("device", alias, "deviceIdentity", deviceIdentities[alias],
                    "normalized", normalized(result, interpretation)));
        }
    }

    private void resetDisplay() {
        active = OUTSIDE;
        rawHit = OUTSIDE;
        latest = "Waiting for pointer";
        rawButtons = heldButtons = inputAlias = 0;
        screenX = screenY = Float.NaN;
    }

    private void note(String kind, Map<String, Object> observations) {
        if (!trace.recording()) return;
        observations.put("kind", kind);
        observations.put("receiptUptimeMs", SystemClock.uptimeMillis());
        observations.put("receiptElapsedNanos", SystemClock.elapsedRealtimeNanos());
        observations.put("focused", focused);
        observations.put("layoutRevision", revision);
        trace.add(observations, null);
    }

    private static boolean handled(Map<String, Object> record, boolean result) {
        if (record != null) record.put("handled", result);
        return result;
    }

    private static Float finite(float value) {
        return Float.isFinite(value) ? value : null;
    }

    private void toggleCapture() {
        if (exporting) return;
        if (!trace.recording()) {
            traceFile().delete();
            deviceAliases.clear();
            nextAlias = 0;
            wsaInvalidatedUptime = SystemClock.uptimeMillis();
            resetDisplay();
            scroll = "—";
            marker = 0;
            startUtcMillis = System.currentTimeMillis();
            startUptimeMillis = SystemClock.uptimeMillis();
            trace.start();
            note("start", fields("marker", marker, "utcMs", startUtcMillis));
            note("layout", geometry("capture-start"));
            captureStatus = "Recording";
            capture.setText("Stop and export (F8)");
            mark.setEnabled(true);
            refresh();
            return;
        }

        note("end", fields("marker", ++marker, "utcMs", System.currentTimeMillis()));
        Map<String, Object> snapshot = trace.stop();
        snapshot.put("session", session);
        snapshot.put("provenance", provenance);
        snapshot.put("inputProfile", inputProfile());
        snapshot.put("sourceCommit", BuildInfo.COMMIT);
        snapshot.put("appVersion", "1.0");
        snapshot.put("androidApi", Build.VERSION.SDK_INT);
        snapshot.put("startUtcMs", startUtcMillis);
        snapshot.put("startUptimeMs", startUptimeMillis);
        exporting = true;
        capture.setEnabled(false);
        mark.setEnabled(false);
        captureStatus = "Exporting";
        refresh();
        new Thread(() -> {
            boolean success = export(snapshot);
            runOnUiThread(() -> {
                exporting = false;
                capture.setEnabled(true);
                capture.setText("Start capture (F8)");
                captureStatus = success ? "Export ready" : "Export failed";
                refresh();
            });
        }, "probe-export").start();
    }

    private AtomicFile traceFile() {
        return new AtomicFile(new File(getFilesDir(), "pointer-trace.json"));
    }

    private boolean export(Map<String, Object> snapshot) {
        AtomicFile file = traceFile();
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            stream.write(new JSONObject(snapshot).toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(stream);
            return true;
        } catch (IOException | RuntimeException error) {
            file.failWrite(stream);
            return false;
        }
    }

    private void mark() {
        if (!trace.recording()) return;
        note("marker", fields("marker", ++marker, "utcMs", System.currentTimeMillis()));
        captureStatus = "Recording, marker " + marker;
        refresh();
    }

    private void scheduleRefresh() {
        if (refreshPending) return;
        refreshPending = true;
        root.postOnAnimation(() -> {
            refreshPending = false;
            refresh();
        });
    }

    private void refresh() {
        if (status == null) return;
        String coordinates = Float.isFinite(screenX)
                ? String.format(Locale.ROOT, "%.1f, %.1f", screenX, screenY) : "—";
        String text = String.format(Locale.ROOT,
                "%s · %s · Focus %s\nProfile %s\nRaw region %s · Screen %s · Layout %d\n"
                        + "Interpreted region %s · Held %s · Raw 0x%x · Scroll H/V %s\n%s",
                captureStatus, provenance, focused ? "yes" : "no", inputProfile(), rawHit, coordinates, revision, active,
                buttons(heldButtons), rawButtons, scroll, latest);
        if (!text.contentEquals(status.getText())) status.setText(text);
        a.invalidate();
        b.invalidate();
    }

    private String inputProfile() {
        return wsaCompatibility ? "wsa-experimental" : "android-standard";
    }

    private static String buttons(int mask) {
        if (mask == 0) return "—";
        return ((mask & 1) != 0 ? "L" : "") + ((mask & 2) != 0 ? "R" : "") + ((mask & 4) != 0 ? "M" : "");
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class Region extends TextView {
        private final String id;
        private final int baseColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Region(String id, int color) {
            super(ProbeActivity.this);
            this.id = id;
            baseColor = color;
            setText("Region " + id);
            setTextColor(Color.WHITE);
            setTextSize(16);
            setPadding(dp(10), dp(8), dp(8), 0);
            setClickable(true);
            setFocusable(false);
        }

        @Override protected void onDraw(Canvas canvas) {
            boolean selected = id.equals(active);
            canvas.drawColor(selected ? 0xff1e5249 : baseColor);
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setColor(0xffafc6d8);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            canvas.drawCircle(cx, cy, dp(7), paint);
            if (id.equals(rawHit) && Float.isFinite(screenX) && Float.isFinite(screenY)) {
                Rect region = "A".equals(id) ? aBounds : bBounds;
                float x = screenX - region.left;
                float y = screenY - region.top;
                paint.setColor(0xffffe18a);
                paint.setStrokeWidth(dp(2));
                canvas.drawLine(x - dp(9), y, x + dp(9), y, paint);
                canvas.drawLine(x, y - dp(9), x, y + dp(9), paint);
            }
        }

        @Override public boolean onHoverEvent(MotionEvent event) {
            Map<String, Object> observation = motion(event, "region." + id + ".hover", id);
            return handled(observation, super.onHoverEvent(event));
        }

        @Override public boolean onGenericMotionEvent(MotionEvent event) {
            Map<String, Object> observation = motion(event, "region." + id + ".generic", id);
            return handled(observation, super.onGenericMotionEvent(event));
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            Map<String, Object> observation = motion(event, "region." + id + ".touch", id);
            return handled(observation, super.onTouchEvent(event));
        }
    }
}
