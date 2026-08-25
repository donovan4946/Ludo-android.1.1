package fr.ludorum.app;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

final class CartTicker extends FrameLayout {
    private static final long FREE_SHIPPING_MINOR = 10000L;

    private final TextView text;
    private boolean loading = false;
    private long lastRefreshAt = 0L;

    CartTicker(Context context) {
        super(context);

        setBackgroundColor(Color.rgb(248, 250, 252));
        setPadding(
                Ui.dp(context, 10),
                Ui.dp(context, 6),
                Ui.dp(context, 10),
                Ui.dp(context, 6)
        );

        text = Ui.text(
                context,
                buildMessage(-1L, 2, "EUR"),
                11,
                Ui.NAVY,
                true
        );

        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        text.setMarqueeRepeatLimit(-1);
        text.setHorizontallyScrolling(true);
        text.setSelected(true);
        text.setFocusable(false);
        text.setFocusableInTouchMode(false);

        addView(
                text,
                new FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                )
        );

        // Le premier rafraîchissement est déclenché par onResume().
        // Évite une requête doublée pendant la construction de l'écran.
    }

    void applySnapshot(
            CartService.CartSnapshot snapshot
    ) {
        if (snapshot == null) {
            return;
        }

        loading = false;
        lastRefreshAt =
                System.currentTimeMillis();

        try {
            text.setText(
                    buildMessage(
                            Math.max(
                                    0L,
                                    snapshot.totalTtcExcludingShipping
                            ),
                            snapshot.currencyMinorUnit,
                            snapshot.currencyCode
                    )
            );

            text.setSelected(false);
            text.setSelected(true);

        } catch (Throwable ignored) {}
    }

    void refresh() {
        refresh(false);
    }

    void refresh(boolean force) {
        long now = System.currentTimeMillis();

        if (loading) return;
        if (!force && now - lastRefreshAt < 900L) return;

        loading = true;
        lastRefreshAt = now;

        try {
            CartService.getProductsTtc(
                new CartService.AmountCallback() {
                    @Override
                    public void onResult(
                            long minorAmount,
                            int minorUnit,
                            String currencyCode
                    ) {
                        loading = false;

                        try {
                            if (!isAttachedToWindow()) {
                                return;
                            }

                            text.setText(
                                    buildMessage(
                                            Math.max(
                                                    0L,
                                                    minorAmount
                                            ),
                                            Math.max(
                                                    0,
                                                    minorUnit
                                            ),
                                            currencyCode
                                    )
                            );

                            text.setSelected(false);
                            text.setSelected(true);

                        } catch (Throwable ignored) {
                            // Le ticker ne doit jamais impacter
                            // le fonctionnement du panier.
                        }
                    }
                }
            );
        } catch (Throwable ignored) {
            loading = false;
        }
    }

    private String buildMessage(
            long minorAmount,
            int minorUnit,
            String currencyCode
    ) {
        String shipping;

        if (minorAmount < 0L) {
            shipping =
                    "🚚 Livraison offerte dès 100 € d’achats — calcul du panier…";
        } else if (minorAmount >= FREE_SHIPPING_MINOR) {
            shipping =
                    "✓ Félicitations ! La livraison est offerte.";
        } else {
            long remaining =
                    Math.max(
                            0L,
                            FREE_SHIPPING_MINOR - minorAmount
                    );

            shipping =
                    "🚚 Livraison offerte dès 100 € d’achats — plus que " +
                    formatMoney(
                            remaining,
                            minorUnit,
                            currencyCode
                    );
        }

        return shipping +
                "     •     " +
                "🔒 Paiement sécurisé — CB, Visa, Mastercard et PayPal" +
                "     •     " +
                "📦 Expédition sous 48 heures" +
                "          ";
    }

    private String formatMoney(
            long minor,
            int minorUnit,
            String currencyCode
    ) {
        try {
            double divisor =
                    Math.pow(
                            10d,
                            Math.max(0, minorUnit)
                    );

            NumberFormat format =
                    NumberFormat.getCurrencyInstance(
                            Locale.FRANCE
                    );

            try {
                format.setCurrency(
                        Currency.getInstance(
                                currencyCode == null ||
                                currencyCode.trim().isEmpty()
                                        ? "EUR"
                                        : currencyCode
                        )
                );
            } catch (Exception ignored) {}

            return format.format(
                    minor / divisor
            );

        } catch (Exception ignored) {
            return String.format(
                    Locale.FRANCE,
                    "%.2f €",
                    minor / 100d
            );
        }
    }
}
