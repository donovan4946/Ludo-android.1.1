package fr.ludorum.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageLoader {
    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(4);

    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(32 * 1024) {
                @Override
                protected int sizeOf(
                        String key,
                        Bitmap value
                ) {
                    return value.getByteCount() / 1024;
                }
            };

    private static final Map<
            String,
            List<WeakReference<ImageView>>
            > WAITING =
            new ConcurrentHashMap<>();

    static void load(
            String url,
            ImageView view
    ) {
        if (url == null ||
                url.trim().isEmpty() ||
                view == null) {
            return;
        }

        view.setTag(url);

        Bitmap cached =
                CACHE.get(url);

        if (cached != null) {
            view.setAlpha(1f);
            view.setImageBitmap(cached);
            return;
        }

        synchronized (WAITING) {
            List<WeakReference<ImageView>> waiting =
                    WAITING.get(url);

            if (waiting != null) {
                waiting.add(
                        new WeakReference<>(view)
                );
                return;
            }

            waiting = new ArrayList<>();
            waiting.add(
                    new WeakReference<>(view)
            );
            WAITING.put(
                    url,
                    waiting
            );
        }

        EXECUTOR.execute(
                () -> download(url)
        );
    }

    private static void download(
            String url
    ) {
        HttpURLConnection connection = null;
        Bitmap bitmap = null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(url)
                                    .openConnection();

            connection.setConnectTimeout(6500);
            connection.setReadTimeout(9000);
            connection.setUseCaches(true);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty(
                    "Connection",
                    "keep-alive"
            );
            connection.setRequestProperty(
                    "User-Agent",
                    "LudorumAndroid/1.1.13"
            );

            try (
                    InputStream input =
                            connection.getInputStream();
                    ByteArrayOutputStream output =
                            new ByteArrayOutputStream()
            ) {
                byte[] buffer =
                        new byte[16 * 1024];

                int read;

                while ((read = input.read(buffer)) != -1) {
                    output.write(
                            buffer,
                            0,
                            read
                    );
                }

                byte[] bytes =
                        output.toByteArray();

                BitmapFactory.Options bounds =
                        new BitmapFactory.Options();

                bounds.inJustDecodeBounds = true;

                BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.length,
                        bounds
                );

                BitmapFactory.Options options =
                        new BitmapFactory.Options();

                options.inPreferredConfig =
                        Bitmap.Config.ARGB_8888;

                options.inSampleSize =
                        sampleSize(
                                bounds.outWidth,
                                bounds.outHeight,
                                640,
                                640
                        );

                bitmap =
                        BitmapFactory.decodeByteArray(
                                bytes,
                                0,
                                bytes.length,
                                options
                        );
            }

            if (bitmap != null) {
                CACHE.put(
                        url,
                        bitmap
                );
            }

        } catch (Exception ignored) {
            // Le placeholder Ludorum reste visible.

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        final Bitmap result = bitmap;

        MAIN.post(
                () -> {
                    List<WeakReference<ImageView>> waiting;

                    synchronized (WAITING) {
                        waiting =
                                WAITING.remove(url);
                    }

                    if (waiting == null) {
                        return;
                    }

                    for (WeakReference<ImageView> reference : waiting) {
                        ImageView target =
                                reference.get();

                        if (target == null) {
                            continue;
                        }

                        Object tag =
                                target.getTag();

                        if (result != null &&
                                url.equals(tag)) {
                            target.setAlpha(1f);
                            target.setImageBitmap(result);
                        }
                    }
                }
        );
    }

    private static int sampleSize(
            int width,
            int height,
            int requestedWidth,
            int requestedHeight
    ) {
        if (width <= 0 ||
                height <= 0) {
            return 1;
        }

        int sample = 1;

        while ((width / (sample * 2)) >= requestedWidth &&
                (height / (sample * 2)) >= requestedHeight) {
            sample *= 2;
        }

        return Math.max(
                1,
                sample
        );
    }
}
