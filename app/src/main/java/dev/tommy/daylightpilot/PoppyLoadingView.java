package dev.tommy.daylightpilot;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A poppy blooms once, then gently sways until the answer arrives. */
final class PoppyLoadingView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Drawable flower;
    private ValueAnimator animator;
    private float phase = 2f;

    PoppyLoadingView(Context context) {
        super(context);
        flower = context.getDrawable(R.drawable.ic_poppy).mutate();
        flower.setBounds(-96, -96, 96, 96);
        setContentDescription("Poppy is thinking");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void begin(float elapsedSeconds) {
        stop();
        phase = elapsedSeconds;
        invalidate();
        start();
    }
    float elapsedSeconds() { return phase; }
    private void start() {
        if (animator != null || !isAttachedToWindow() || !isShown() || getWindowVisibility() != VISIBLE) return;
        if (!ValueAnimator.areAnimatorsEnabled()) { phase = 2f; invalidate(); return; }
        final float initialPhase = phase;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(4800);
        animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(value -> { phase = initialPhase + value.getCurrentPlayTime() / 1000f; invalidate(); });
        animator.start();
    }
    private void stop() { if (animator != null) { animator.cancel(); animator = null; } }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); start(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (isShown()) start(); else stop();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) start(); else stop();
    }
    private float ease(float value) {
        float t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t);
    }
    private void fill(Canvas canvas, int color) {
        paint.setColor(color); paint.setStyle(Paint.Style.FILL); canvas.drawPath(path, paint);
    }
    private void stroke(Canvas canvas, int color, float width) {
        paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND); canvas.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
    }
    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); drawScene(canvas, getWidth(), getHeight(), phase); }

    // A fixed coordinate space keeps the composition consistent in both panel sizes.
    void drawScene(Canvas canvas, int width, int height, float time) {
        float scale = Math.min(300 * getResources().getDisplayMetrics().density,
            Math.min(width * .86f, height * .96f)) / 300;
        canvas.save(); canvas.translate(width / 2f, height / 2f); canvas.scale(scale, scale);
        canvas.translate(-150, -150);
        float bloomTime = Math.min(time, 2f) * .725f;
        float grow = ease(bloomTime / .85f);
        float open = ease((bloomTime - .65f) / .8f);
        float waiting = Math.max(0, time - 2f);
        float sway = (float)Math.sin(waiting * Math.PI * 2 / 4.8) * 3 * ease(waiting / 1.2f);
        float headX = 150 + sway;
        float headY = 239 - 139 * grow;
        int green = 0xff667752;
        // The ground anchors the flower as it grows.
        path.reset(); path.moveTo(75, 247); path.cubicTo(112, 244, 193, 244, 228, 247);
        stroke(canvas, 0xffd9d9c9, 2);
        canvas.save();
        path.reset(); path.moveTo(150, 244);
        path.cubicTo(142, 214, 133 + sway * .5f, headY + 15, headX, headY);
        stroke(canvas, green, 4);
        // Two leaves unfurl from the stem.
        leaf(canvas, 148, 215, -52, grow, green);
        leaf(canvas, 143, 185 + 35 * (1 - grow), 47, grow, green);
        canvas.save(); canvas.translate(headX, headY);
        canvas.rotate(-9 * (1 - open) + sway * .35f);
        float flowerSize = (.29f + .71f * open) * (.3f + .7f * grow);
        canvas.scale(flowerSize, flowerSize);
        // Share the header's exact artwork, preserving its petals, shading, and center.
        // A uniform opening scale grows the bud into the icon without distorting it.
        flower.draw(canvas);
        canvas.restore(); canvas.restore();
        for (int i = 0; i < 11; i++) {
            float x = 89 + i * 12;
            float h = 13 + (i * 17 % 29);
            float bend = (i % 2 == 0 ? -1 : 1) * (7 + i % 4 * 3) + sway * .5f;
            path.reset(); path.moveTo(x - 3, 246);
            path.cubicTo(x - 4, 235, x + bend * .25f, 245 - h, x + bend, 245 - h);
            path.cubicTo(x + bend * .3f, 239 - h * .35f, x + 4, 237, x + 3, 246); path.close();
            fill(canvas, i % 3 == 0 ? 0xffa7ae87 : 0xff7d8c63);
        }
        canvas.restore();
    }
    private void leaf(Canvas canvas, float x, float y, float angle, float grow, int color) {
        canvas.save(); canvas.translate(x, y); canvas.rotate(angle);
        canvas.scale(grow, grow);
        path.reset(); path.moveTo(0, 0); path.cubicTo(-22, -15, -16, -42, 0, -52);
        path.cubicTo(13, -32, 18, -14, 0, 0); path.close(); fill(canvas, color);
        path.reset(); path.moveTo(0, -3); path.quadTo(-3, -20, 0, -43);
        stroke(canvas, 0xffb1b794, 1); canvas.restore();
    }
}
