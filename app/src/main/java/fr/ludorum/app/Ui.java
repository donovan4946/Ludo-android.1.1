package fr.ludorum.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int NAVY = Color.rgb(7, 26, 51);
    static final int BLUE = Color.rgb(11, 77, 187);
    static final int RED = Color.rgb(207, 31, 31);
    static final int YELLOW = Color.rgb(242, 169, 0);

    static final int TEXT = Color.rgb(22, 34, 49);
    static final int MUTED = Color.rgb(101, 113, 128);
    static final int BORDER = Color.rgb(226, 232, 240);
    static final int SOFT = Color.rgb(247, 249, 252);
    static final int IVORY = Color.rgb(255, 250, 240);
    static final int GREEN = Color.rgb(24, 135, 84);

    // Navigation drawer / menu palette:
    // clean, calm and premium while keeping Ludorum's blue/yellow/red identity.
    static final int MENU_CANVAS = Color.rgb(245, 248, 252);
    static final int MENU_CARD = Color.rgb(255, 255, 255);
    static final int MENU_ITEM = Color.rgb(250, 252, 255);
    static final int MENU_TEXT = Color.rgb(17, 38, 68);
    static final int MENU_MUTED = Color.rgb(105, 118, 137);
    static final int MENU_BORDER = Color.rgb(218, 226, 238);
    static final int MENU_GOLD_TEXT = Color.rgb(153, 98, 0);

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /**
     * Android 15+ impose l'affichage bord à bord pour les apps récentes.
     * On réserve explicitement les zones système sans dépendre de WindowInsets,
     * ce qui évite les régressions observées sur certains Samsung.
     */
    static int topSystemSpace(Context c) {
        if (Build.VERSION.SDK_INT < 35) return 0;
        return systemDimen(c, "status_bar_height", 28);
    }

    static int bottomSystemSpace(Context c) {
        if (Build.VERSION.SDK_INT < 35) return 0;
        return systemDimen(c, "navigation_bar_height", 48);
    }

    private static int systemDimen(Context c, String name, int fallbackDp) {
        try {
            int id = c.getResources().getIdentifier(name, "dimen", "android");
            if (id > 0) {
                int px = c.getResources().getDimensionPixelSize(id);
                if (px > 0) return px;
            }
        } catch (Exception ignored) {}
        return dp(c, fallbackDp);
    }

    static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, Math.round(radiusDp)));
        return d;
    }

    static GradientDrawable roundedStroke(
            int color,
            int strokeColor,
            int strokeDp,
            float radiusDp,
            Context c
    ) {
        GradientDrawable d = rounded(color, radiusDp, c);
        d.setStroke(dp(c, strokeDp), strokeColor);
        return d;
    }

    static GradientDrawable gradient(int start, int end, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{start, end}
        );
        d.setCornerRadius(dp(c, Math.round(radiusDp)));
        return d;
    }

    static int softAccent(int color) {
        return Color.argb(
                20,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    static int menuTint(int color) {
        int red =
                (Color.red(color) * 8 +
                255 * 92) / 100;
        int green =
                (Color.green(color) * 8 +
                255 * 92) / 100;
        int blue =
                (Color.blue(color) * 8 +
                255 * 92) / 100;

        return Color.rgb(
                red,
                green,
                blue
        );
    }

    static int menuAccentText(int color) {
        if (color == YELLOW) {
            return MENU_GOLD_TEXT;
        }

        if (color == MUTED) {
            return MENU_MUTED;
        }

        return color;
    }

    static TextView text(Context c, String value, int sp, int color, boolean strong) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(
                strong ? "sans-serif-medium" : "sans-serif",
                Typeface.NORMAL
        ));
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView pill(Context c, String label, int textColor, int bg, int stroke) {
        TextView t = text(c, label, 14, textColor, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 15), dp(c, 10), dp(c, 15), dp(c, 10));
        t.setBackground(roundedStroke(bg, stroke, 1, 24, c));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    static int navAccent(
            String label
    ) {
        if ("Compte".equals(label)) {
            return YELLOW;
        }

        if ("Favoris".equals(label)) {
            return RED;
        }

        return BLUE;
    }

    static LinearLayout brandStripe(
            Context c
    ) {
        LinearLayout stripe =
                new LinearLayout(c);

        stripe.setOrientation(
                LinearLayout.HORIZONTAL
        );

        int[] colors =
                new int[]{
                        BLUE,
                        YELLOW,
                        RED
                };

        for (int color : colors) {
            View part =
                    new View(c);

            part.setBackgroundColor(
                    color
            );

            stripe.addView(
                    part,
                    new LinearLayout.LayoutParams(
                            0,
                            dp(c, 4),
                            1f
                    )
            );
        }

        return stripe;
    }

    static LinearLayout navItem(
            Context c,
            int iconRes,
            String label,
            boolean active
    ) {
        LinearLayout box =
                new LinearLayout(c);

        box.setOrientation(
                LinearLayout.VERTICAL
        );

        box.setGravity(
                Gravity.CENTER
        );

        box.setPadding(
                dp(c, 4),
                dp(c, 5),
                dp(c, 4),
                dp(c, 4)
        );

        int accent =
                navAccent(label);

        int foreground =
                active
                        ? (accent == YELLOW
                            ? NAVY
                            : Color.WHITE)
                        : NAVY;

        ImageView icon =
                new ImageView(c);

        icon.setImageResource(
                iconRes
        );

        icon.setColorFilter(
                foreground
        );

        box.addView(
                icon,
                new LinearLayout.LayoutParams(
                        dp(c, 22),
                        dp(c, 22)
                )
        );

        TextView txt =
                text(
                        c,
                        label,
                        10,
                        foreground,
                        active
                );

        txt.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams tp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        tp.topMargin =
                dp(c, 2);

        box.addView(
                txt,
                tp
        );

        View marker =
                new View(c);

        marker.setBackground(
                rounded(
                        active
                                ? (accent == YELLOW
                                    ? NAVY
                                    : Color.WHITE)
                                : accent,
                        2,
                        c
                )
        );

        LinearLayout.LayoutParams mp =
                new LinearLayout.LayoutParams(
                        dp(c, 24),
                        dp(c, 3)
                );

        mp.topMargin =
                dp(c, 4);

        box.addView(
                marker,
                mp
        );

        box.setBackground(
                active
                        ? rounded(
                                accent,
                                18,
                                c
                        )
                        : rounded(
                                Color.TRANSPARENT,
                                18,
                                c
                        )
        );

        box.setTag(
                new Object[]{
                        icon,
                        txt,
                        marker,
                        accent
                }
        );

        return box;
    }

    static void setNavActive(
            LinearLayout item,
            boolean active
    ) {
        if (item == null ||
                !(item.getTag() instanceof Object[])) {
            return;
        }

        Object[] items =
                (Object[]) item.getTag();

        if (items.length < 4) {
            return;
        }

        ImageView icon =
                (ImageView) items[0];

        TextView txt =
                (TextView) items[1];

        View marker =
                (View) items[2];

        int accent =
                (Integer) items[3];

        int foreground =
                active
                        ? (accent == YELLOW
                            ? NAVY
                            : Color.WHITE)
                        : NAVY;

        icon.setColorFilter(
                foreground
        );

        txt.setTextColor(
                foreground
        );

        txt.setTypeface(
                Typeface.create(
                        active
                                ? "sans-serif-medium"
                                : "sans-serif",
                        Typeface.NORMAL
                )
        );

        marker.setBackground(
                rounded(
                        active
                                ? (accent == YELLOW
                                    ? NAVY
                                    : Color.WHITE)
                                : accent,
                        2,
                        item.getContext()
                )
        );

        item.setBackground(
                active
                        ? rounded(
                                accent,
                                18,
                                item.getContext()
                        )
                        : rounded(
                                Color.TRANSPARENT,
                                18,
                                item.getContext()
                        )
        );

        item.setElevation(
                active
                        ? dp(
                            item.getContext(),
                            5
                        )
                        : 0f
        );
    }
}
