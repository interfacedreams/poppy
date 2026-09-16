package dev.tommy.daylightpilot;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;

/** Device-rendered contact sheet for reviewing the vector lifecycle at both panel sizes. */
public final class LoadingPreview extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            runOnMainSync(() -> {
                Bitmap bitmap = Bitmap.createBitmap(1500, 480, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap); canvas.drawColor(0xfff6f5ef);
                PoppyLoadingView view = new PoppyLoadingView(getTargetContext());
                float[] poses = {.85f, 2f, 3.2f, 5.6f, 20f};
                for (int i = 0; i < poses.length; i++) {
                    canvas.save(); canvas.translate(i * 300, 0);
                    view.drawScene(canvas, 300, 300, poses[i]); canvas.restore();
                    canvas.save(); canvas.translate(i * 300, 300);
                    view.drawScene(canvas, 300, 150, poses[i]); canvas.restore();
                }
                try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getCacheDir(), "loading-states.png"))) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                } catch (Exception error) { throw new RuntimeException(error); }
                bitmap.recycle();
            });
            result.putString("stream", "Rendered growth, bloom, both sway directions, and an extended wait at both panel sizes.\n"); finish(-1, result);
        } catch (Throwable error) { result.putString("stream", error.toString()); finish(0, result); }
    }
}
