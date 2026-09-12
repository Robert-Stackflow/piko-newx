/** Isolated ART preflight for this exact frozen APK. Does not open history or play media. */
public final class AndroidMediaProbe {
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        String[] names = {
            "com.x.video.tab.v", "com.x.postdetail.n", "com.x.video.tab.g0", "com.x.media.l", "com.x.media.j0",
            "app.morphe.extension.newx.misc.InlineDownloadButton",
            "app.morphe.extension.newx.settings.SettingsRenderer",
            "app.morphe.extension.newx.mediatools.WatchSession",
            "app.morphe.extension.newx.mediatools.MediaHistoryStore",
            "app.morphe.extension.newx.mediatools.MediaHistoryRuntime",
            "app.morphe.extension.newx.mediatools.HistoryFragment",
            "app.morphe.extension.newx.mediatools.DownloadTaskStore",
            "app.morphe.extension.newx.mediatools.DownloadsFragment",
            "app.morphe.extension.newx.mediatools.ResumePolicy",
            "app.morphe.extension.newx.mediatools.PlaybackStore",
            "app.morphe.extension.newx.mediatools.VideoToolsRuntime",
            "app.morphe.extension.newx.mediatools.MediaToolsUi",
            "app.morphe.extension.newx.mediatools.MediaToolsUi$Icon",
            "app.morphe.extension.newx.mediatools.MediaPreviewLoader"
            ,"app.morphe.extension.newx.mediatools.HeaderToolsRuntime",
            "app.morphe.extension.newx.mediatools.HeaderToolsRuntime$HomeFactory",
            "app.morphe.extension.newx.mediatools.HeaderToolsRuntime$VideoFactory",
            "app.morphe.extension.newx.mediatools.HeaderToolsRuntime$HeaderUpdate",
            "app.morphe.extension.newx.mediatools.HeaderToolsRuntime$VideoActions",
            "app.morphe.extension.newx.mediatools.HistoryFragment$HistoryRow",
            "app.morphe.extension.newx.mediatools.HistoryFragment$MediaGrid",
            "app.morphe.extension.newx.mediatools.HistoryPresentation",
            "app.morphe.extension.newx.mediatools.DownloadsFragment$TaskRow"
        };
        String[] targets = args.length == 0 ? names : args;
        int failures = 0;
        for (String name : targets) {
            System.out.println("ART checking: " + name);
            try {
            Class<?> type = Class.forName(name, true, AndroidMediaProbe.class.getClassLoader());
            type.getDeclaredMethods(); type.getDeclaredConstructors();
            System.out.println("ART verified: " + name);
            } catch (Throwable error) {
                failures++;
                error.printStackTrace(System.out);
            }
        }
        if (failures > 0) { System.out.println("ART_MEDIA_FAIL " + failures); System.exit(1); }
        if (args.length == 0) {
            String header = "app.morphe.extension.newx.mediatools.HeaderToolsRuntime";
            for (String suffix : new String[]{"HomeFactory", "VideoFactory", "HeaderUpdate", "VideoActions"}) {
                Class<?> callback = Class.forName(header + "$" + suffix);
                Class<?>[] parameters = suffix.equals("VideoActions")
                        ? new Class<?>[]{Object.class, Object.class, Object.class} : new Class<?>[]{Object.class};
                java.lang.reflect.Method invoke = callback.getDeclaredMethod("invoke", parameters);
                if (java.lang.reflect.Modifier.isAbstract(invoke.getModifiers())) throw new AssertionError("Abstract callback: " + suffix);
            }
            Object updater = Class.forName(header).getMethod("updater").invoke(null);
            updater.getClass().getMethod("invoke", Object.class).invoke(updater, new Object[]{null});
            System.out.println("ART_HEADER_CALLBACK_PASS 4; update invoked");
            Class<?> function2 = Class.forName("kotlin.jvm.functions.Function2");
            int[] invocations = {0};
            Object nativeCallback = java.lang.reflect.Proxy.newProxyInstance(function2.getClassLoader(), new Class<?>[]{function2},
                    (proxy, method, values) -> { if (method.getName().equals("invoke")) invocations[0]++; return null; });
            Object wrapper = Class.forName(header).getMethod("wrapVideoActions", Object.class).invoke(null, nativeCallback);
            if (wrapper == null) throw new AssertionError("Null native actions wrapper");
            java.lang.reflect.Method forward = Class.forName(header).getDeclaredMethod("invokeNativeActions", Object.class, Object.class);
            forward.setAccessible(true); forward.invoke(null, nativeCallback, new Object());
            if (invocations[0] != 1) throw new AssertionError("Native action was not forwarded once");
            System.out.println("ART_NATIVE_ACTION_FORWARD_PASS 1");
            // Exact frozen-APK user interface: verifies name/avatar forwarding without real user data.
            Class<?> user = Class.forName("com.x.models.mh");
            Object publicAuthor = java.lang.reflect.Proxy.newProxyInstance(user.getClassLoader(), new Class<?>[]{user},
                    (proxy, method, values) -> method.getName().equals("getName") ? "Probe Name"
                            : method.getName().equals("c") ? "https://pbs.twimg.com/profile_images/probe.jpg" : null);
            Class<?> history = Class.forName("app.morphe.extension.newx.mediatools.MediaHistoryRuntime");
            for (String property : new String[]{"authorName", "authorAvatar"}) {
                java.lang.reflect.Method getter = history.getDeclaredMethod(property, Object.class); getter.setAccessible(true);
                String expected = property.equals("authorName") ? "Probe Name" : "https://pbs.twimg.com/profile_images/probe.jpg";
                if (!expected.equals(getter.invoke(null, publicAuthor))) throw new AssertionError("Wrong author property: " + property);
            }
            System.out.println("ART_AUTHOR_PRESENTATION_PASS 2");
        }
        System.out.println("ART_MEDIA_PASS " + targets.length);
    }
}
