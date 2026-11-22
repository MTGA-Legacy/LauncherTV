package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.PowerManager;
import android.view.Display;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.Locale;

/** Small API-level shims kept in one place so the launcher can still load on KitKat. */
final class ApiCompat {
    private ApiCompat() {
    }

    static Object service(Context context, String name) {
        return context.getSystemService(name);
    }

    static Drawable drawable(Context context, int id) {
        return context.getResources().getDrawable(id);
    }

    static Drawable drawableForDensity(android.content.res.Resources resources, int id, int density) {
        return resources.getDrawableForDensity(id, density);
    }

    /** Loads an app banner only where the TV banner fields and methods exist (API 20+). */
    static Drawable appBanner(Context context, PackageManager pm, android.content.pm.ResolveInfo info) {
        if (Build.VERSION.SDK_INT < 20 || info == null || info.activityInfo == null) return null;
        try {
            Object activity = info.activityInfo;
            int id = integerField(activity, "banner");
            if (id == 0) id = integerField(info.activityInfo.applicationInfo, "banner");
            Drawable banner = null;
            if (id != 0) {
                android.content.res.Resources resources = pm.getResourcesForApplication(info.activityInfo.applicationInfo);
                int density = Math.max(android.util.DisplayMetrics.DENSITY_XHIGH,
                        context.getResources().getDisplayMetrics().densityDpi);
                banner = drawableForDensity(resources, id, density);
            }
            if (banner == null) {
                Method load = activity.getClass().getMethod("loadBanner", PackageManager.class);
                Object value = load.invoke(activity, pm);
                if (value instanceof Drawable) banner = (Drawable) value;
            }
            return banner;
        } catch (Exception ignored) {
            return null;
        }
    }

    static Intent launchIntent(PackageManager pm, String packageName) {
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                Method m = PackageManager.class.getMethod("getLeanbackLaunchIntentForPackage", String.class);
                Intent leanback = (Intent) m.invoke(pm, packageName);
                if (leanback != null) return leanback;
            } catch (Exception ignored) {
            }
        }
        return pm.getLaunchIntentForPackage(packageName);
    }

    static Locale locale(Configuration configuration) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Object locales = Configuration.class.getMethod("getLocales").invoke(configuration);
                return (Locale) locales.getClass().getMethod("get", int.class).invoke(locales, 0);
            } catch (Exception ignored) {
            }
        }
        return configuration.locale;
    }

    static boolean userUnlocked(Context context) {
        if (Build.VERSION.SDK_INT < 24) return true;
        Object users = service(context, "user");
        if (users == null) return true;
        try {
            return (Boolean) users.getClass().getMethod("isUserUnlocked").invoke(users);
        } catch (Exception ignored) {
            return true;
        }
    }

    static boolean interactive(Context context) {
        Object value = service(context, Context.POWER_SERVICE);
        if (!(value instanceof PowerManager)) return true;
        PowerManager power = (PowerManager) value;
        if (Build.VERSION.SDK_INT >= 20) {
            try {
                return (Boolean) PowerManager.class.getMethod("isInteractive").invoke(power);
            } catch (Exception ignored) {
            }
        }
        try {
            return (Boolean) PowerManager.class.getMethod("isScreenOn").invoke(power);
        } catch (Exception ignored) {
            return true;
        }
    }

    static boolean displayOn(Context context, Display display) {
        if (display == null) return true;
        if (Build.VERSION.SDK_INT >= 20) {
            try {
                int state = (Integer) Display.class.getMethod("getState").invoke(display);
                return state == 2; // Display.STATE_ON
            } catch (Exception ignored) {
            }
        }
        return interactive(context);
    }

    static void setLetterSpacing(TextView view, float value) {
        if (Build.VERSION.SDK_INT < 21) return;
        try {
            TextView.class.getMethod("setLetterSpacing", float.class).invoke(view, value);
        } catch (Exception ignored) {
        }
    }

    static void setTranslationZ(View view, float value) {
        if (Build.VERSION.SDK_INT < 21) return;
        try {
            View.class.getMethod("setTranslationZ", float.class).invoke(view, value);
        } catch (Exception ignored) {
        }
    }

    static void setAnimatorTranslationZ(ViewPropertyAnimator animator, float value) {
        if (Build.VERSION.SDK_INT < 21) return;
        try {
            ViewPropertyAnimator.class.getMethod("translationZ", float.class).invoke(animator, value);
        } catch (Exception ignored) {
        }
    }

    static void setElevation(PopupWindow window, float value) {
        if (Build.VERSION.SDK_INT < 21) return;
        try {
            PopupWindow.class.getMethod("setElevation", float.class).invoke(window, value);
        } catch (Exception ignored) {
        }
    }

    /** Uses the platform tint API when available and a color filter on KitKat. */
    static void setCompoundDrawableTintList(TextView view, ColorStateList colors) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                TextView.class.getMethod("setCompoundDrawableTintList", ColorStateList.class)
                        .invoke(view, colors);
                return;
            } catch (Exception ignored) {
            }
        }
        int color = colors == null ? 0 : colors.getColorForState(view.getDrawableState(), colors.getDefaultColor());
        Drawable[] drawables = view.getCompoundDrawablesRelative();
        for (Drawable drawable : drawables) {
            if (drawable == null) continue;
            if (colors == null) drawable.clearColorFilter();
            else drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
    }

    static ColorStateList compoundDrawableTintList(TextView view) {
        if (Build.VERSION.SDK_INT < 23) return null;
        try {
            Object value = TextView.class.getMethod("getCompoundDrawableTintList").invoke(view);
            return value instanceof ColorStateList ? (ColorStateList) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static ColorStateList colorStateList(Context context, int id) {
        return context.getResources().getColorStateList(id);
    }

    static void setTint(Drawable drawable, int color) {
        if (drawable == null) return;
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                Drawable.class.getMethod("setTint", int.class).invoke(drawable, color);
                return;
            } catch (Exception ignored) {
            }
        }
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    static void forceHasOverlappingRendering(View view, boolean force) {
        if (Build.VERSION.SDK_INT < 24) return;
        try {
            View.class.getMethod("forceHasOverlappingRendering", boolean.class).invoke(view, force);
        } catch (Exception ignored) {
        }
    }

    static void disableAccessibilityService(Object service) {
        if (service == null) return;
        try {
            service.getClass().getMethod("disableSelf").invoke(service);
        } catch (Exception ignored) {
        }
    }

    private static int integerField(Object target, String name) {
        if (target == null) return 0;
        try {
            Object value = target.getClass().getField(name).get(target);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    static Intent explicitIntent(String packageName, String className) {
        return new Intent().setComponent(new ComponentName(packageName, className));
    }
}
