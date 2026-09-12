package app.morphe.extension.newx.mediatools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Visible previews only: bounded memory/work, no disk cache, credentials, URL logs or remote video reads. */
public final class MediaPreviewLoader implements AutoCloseable {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), new ThreadPoolExecutor.DiscardPolicy());
    private final LruCache<String, Bitmap> cache = new LruCache<>(8192) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return Math.max(1, bitmap.getByteCount() / 1024); }
    };
    private volatile boolean closed;
    public static String safeRemote(String value) {
        if (value == null || value.length() > 4096) return "";
        try {
            Uri uri = Uri.parse(value); String host = uri.getHost();
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443) && host != null
                    && (host.equals("twimg.com") || host.endsWith(".twimg.com")) ? value : "";
        } catch (RuntimeException ignored) { return ""; }
    }
    public void load(ImageView view, String source, boolean video) {
        String key = (video ? "v:" : "i:") + source;
        if (key.equals(view.getTag())) return;
        view.setTag(key); view.setImageDrawable(null);
        if (closed || source == null || source.isEmpty()) return;
        Bitmap hit = cache.get(key);
        if (hit != null) { view.setImageBitmap(hit); return; }
        Context context = view.getContext().getApplicationContext();
        WeakReference<ImageView> target = new WeakReference<>(view);
        io.execute(() -> {
            if (closed || !current(target, key)) return;
            Bitmap bitmap = cache.get(key);
            if (bitmap == null) bitmap = read(context, source, video);
            if (closed || bitmap == null) return;
            cache.put(key, bitmap); Bitmap ready = bitmap;
            main.post(() -> {
                ImageView image = target.get();
                if (!closed && image != null && key.equals(image.getTag())) image.setImageBitmap(ready);
            });
        });
    }
    private static boolean current(WeakReference<ImageView> target, String key) {
        ImageView view = target.get(); return view != null && key.equals(view.getTag());
    }
    private static Bitmap read(Context context, String source, boolean video) {
        try {
            Uri uri = Uri.parse(source);
            if ("content".equals(uri.getScheme())) {
                if (Build.VERSION.SDK_INT >= 29)
                    return context.getContentResolver().loadThumbnail(uri, new Size(512, 512), null);
                if (video) {
                    try (MediaMetadataRetriever reader = new MediaMetadataRetriever()) {
                        reader.setDataSource(context, uri);
                        return reader.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512);
                    }
                }
                try (InputStream input = context.getContentResolver().openInputStream(uri)) { return decode(input); }
            }
            if (safeRemote(source).isEmpty() || video) return null;
            HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
            try {
                connection.setConnectTimeout(4000); connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "image/*");
                if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > 4 * 1024 * 1024) return null;
                try (InputStream input = connection.getInputStream()) { return decode(input); }
            } finally { connection.disconnect(); }
        } catch (Exception ignored) { return null; }
    }
    private static Bitmap decode(InputStream input) throws Exception {
        if (input == null) return null;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); byte[] block = new byte[8192]; int count;
        while ((count = input.read(block)) != -1) {
            if (Thread.currentThread().isInterrupted() || buffer.size() + count > 4 * 1024 * 1024) return null;
            buffer.write(block, 0, count);
        }
        byte[] bytes = buffer.toByteArray(); BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false; return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
    public void clear() { cache.evictAll(); io.getQueue().clear(); }
    @Override public void close() { closed = true; io.shutdownNow(); main.removeCallbacksAndMessages(null); cache.evictAll(); }
}
