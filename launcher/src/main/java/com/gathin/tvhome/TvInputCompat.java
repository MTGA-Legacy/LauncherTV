package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reflection boundary for the Android TV input framework, which starts at API 21. */
final class TvInputCompat {
    static final int INPUT_STATE_CONNECTED = 0;
    static final int INPUT_STATE_DISCONNECTED = 2;
    static final int TYPE_TUNER = 0;
    static final int TYPE_OTHER = 1000;
    static final int TYPE_HDMI = 1007;

    static final String CHANNELS_URI = "content://android.media.tv/channel";
    static final String CHANNEL_CONTENT_TYPE = "vnd.android.cursor.item/channel";
    static final String CHANNELS_CONTENT_TYPE = "vnd.android.cursor.dir/channel";

    static final class Input {
        final Object raw;
        final String id;
        final int type;
        final String parentId;
        final boolean passthrough;
        final boolean hidden;
        final boolean system;
        final String label;
        final String customLabel;

        Input(Context context, Object raw) {
            this.raw = raw;
            this.id = string(raw, "getId");
            this.type = integer(raw, "getType", TYPE_OTHER);
            this.parentId = nullableString(raw, "getParentId");
            this.passthrough = bool(raw, "isPassthroughInput");
            this.hidden = bool(raw, "isHidden", context);
            Object service = invoke(raw, "getServiceInfo");
            Object app = service == null ? null : field(service, "applicationInfo");
            this.system = app instanceof ApplicationInfo
                    && ((((ApplicationInfo) app).flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            this.label = text(raw, "loadLabel", context);
            this.customLabel = text(raw, "loadCustomLabel", context);
        }
    }

    private TvInputCompat() {
    }

    static boolean available(Context context) {
        return Build.VERSION.SDK_INT >= 21 && manager(context) != null;
    }

    static List<Input> list(Context context) {
        Object manager = manager(context);
        if (manager == null) return Collections.emptyList();
        Object value = invoke(manager, "getTvInputList");
        if (!(value instanceof List)) return Collections.emptyList();
        List<Input> result = new ArrayList<>();
        for (Object raw : (List<?>) value) {
            if (raw != null) result.add(new Input(context, raw));
        }
        return result;
    }

    static Input get(Context context, String inputId) {
        Object manager = manager(context);
        Object raw = manager == null ? null : invoke(manager, "getTvInputInfo", inputId);
        return raw == null ? null : new Input(context, raw);
    }

    static int state(Context context, String inputId) {
        Object manager = manager(context);
        Object value = manager == null ? null : invoke(manager, "getInputState", inputId);
        return value instanceof Integer ? (Integer) value : -1;
    }

    static Uri passthroughUri(String inputId) {
        if (Build.VERSION.SDK_INT >= 21) {
            Uri uri = staticUri("buildChannelUriForPassthroughInput", inputId);
            if (uri != null) return uri;
        }
        return Uri.parse(CHANNELS_URI + "/passthrough/" + Uri.encode(inputId));
    }

    static Uri channelsUri(String inputId) {
        if (Build.VERSION.SDK_INT >= 21) {
            Uri uri = staticUri("buildChannelsUriForInput", inputId);
            if (uri != null) return uri;
        }
        return Uri.parse(CHANNELS_URI).buildUpon().appendQueryParameter("input", inputId).build();
    }

    static Uri channelsRootUri() {
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                Class<?> channels = Class.forName("android.media.tv.TvContract$Channels");
                Object uri = channels.getField("CONTENT_URI").get(null);
                if (uri instanceof Uri) return (Uri) uri;
            } catch (Exception ignored) {
            }
        }
        return Uri.parse(CHANNELS_URI);
    }

    static Uri programsUri() {
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                Class<?> programs = Class.forName("android.media.tv.TvContract$Programs");
                Object uri = programs.getField("CONTENT_URI").get(null);
                if (uri instanceof Uri) return (Uri) uri;
            } catch (Exception ignored) {
            }
        }
        return Uri.parse("content://android.media.tv/program");
    }

    private static Object manager(Context context) {
        if (Build.VERSION.SDK_INT < 21) return null;
        return ApiCompat.service(context, "tv_input");
    }

    private static Uri staticUri(String method, String inputId) {
        try {
            Class<?> contract = Class.forName("android.media.tv.TvContract");
            Object value = contract.getMethod(method, String.class).invoke(null, inputId);
            return value instanceof Uri ? (Uri) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object field(Object target, String name) {
        try {
            return target.getClass().getField(name).get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String name, Object... args) {
        if (target == null) return null;
        try {
            Method method = find(target.getClass(), name, args.length);
            return method == null ? null : method.invoke(target, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Method find(Class<?> type, String name, int arity) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == arity) return method;
        }
        return null;
    }

    private static String string(Object target, String method) {
        Object value = invoke(target, method);
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullableString(Object target, String method) {
        Object value = invoke(target, method);
        return value == null ? null : String.valueOf(value);
    }

    private static int integer(Object target, String method, int fallback) {
        Object value = invoke(target, method);
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static boolean bool(Object target, String method, Object... args) {
        Object value = invoke(target, method, args);
        return value instanceof Boolean && (Boolean) value;
    }

    private static String text(Object target, String method, Context context) {
        Object value = invoke(target, method, context);
        return value == null ? "" : String.valueOf(value);
    }
}
