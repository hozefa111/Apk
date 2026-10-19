package com.hozefa.threepattipro.nativeapp;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicBoolean;

/** Espresso does not automatically wait for our property-animation engine. */
final class NativeTestUi {
    private NativeTestUi() {}

    static void awaitMotionIdle(ActivityScenario<MainActivity> scenario) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        long idleSince = 0;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AtomicBoolean idle = new AtomicBoolean();
            scenario.onActivity(activity -> idle.set(activity.motionEngine().activeCount() == 0
                    && activity.motionEngine().pendingCount() == 0
                    && !activity.getWindow().getDecorView().isLayoutRequested()));
            long now = android.os.SystemClock.uptimeMillis();
            if (!idle.get()) idleSince = 0;
            else if (idleSince == 0) idleSince = now;
            else if (now - idleSince >= 500) return;
            Thread.sleep(50);
        }
        throw new AssertionError("UI motion and layout did not settle before input");
    }
}
