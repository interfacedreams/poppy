package dev.tommy.daylightpilot;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import java.util.function.IntConsumer;

/** A short, top-anchored window resize shared by recording and answers. */
final class PanelResize {
    private PanelResize() {}

    static ValueAnimator start(int from, int to, IntConsumer setHeight, Runnable finish) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(240);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(value -> setHeight.accept((Integer) value.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) { if (!cancelled) finish.run(); }
        });
        animator.start();
        return animator;
    }
}
