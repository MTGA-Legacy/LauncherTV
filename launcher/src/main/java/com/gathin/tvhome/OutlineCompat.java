package com.gathin.tvhome;

import android.graphics.Outline;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

/** API 21 outline clipping kept in a class that is never loaded on KitKat. */
final class OutlineCompat {
    private OutlineCompat() {
    }

    static void apply(View view, final float density) {
        if (Build.VERSION.SDK_INT < 21) return;
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View target, Outline outline) {
                outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(),
                        AppAdapter.cornerRadius(target.getWidth(), density));
            }
        });
        view.setClipToOutline(true);
    }
}
