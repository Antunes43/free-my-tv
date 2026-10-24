package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Remote key handling and (on Fire OS only) opt-in Home redirection.
 * The system launcher remains enabled; this service only reacts when it becomes foreground.
 * Enabling the Accessibility Service is the user's explicit opt-in.
 */
public class RemoteKeyService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastRedirect;
    private boolean redirectPending;

    @Override protected boolean onKeyEvent(KeyEvent event) {
        return RemoteKeys.onKey(this, event);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!HomeMode.AMAZON || event == null
                || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null
                || !"com.amazon.tv.launcher".contentEquals(event.getPackageName())) return;

        // Amazon also hosts settings and other screens: redirect only its actual home activity.
        CharSequence cls = event.getClassName();
        if (cls == null || !cls.toString().contains("HomeActivity_vNext")) return;
        long now = SystemClock.elapsedRealtime();
        if (redirectPending || now - lastRedirect < 6500) return;
        redirectPending = true;
        // Fire OS 7 temporarily delays launches after Home. Avoid a tight restart loop.
        handler.postDelayed(() -> {
            redirectPending = false;
            lastRedirect = SystemClock.elapsedRealtime();
            Intent i = new Intent(this, FireHomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try { startActivity(i); } catch (RuntimeException ignored) {
                // Amazon home remains available if Fire OS rejects the redirect.
            }
        }, 350);
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
