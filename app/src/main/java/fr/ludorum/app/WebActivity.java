package fr.ludorum.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class WebActivity extends Activity {
    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_ALLOW_PRODUCT_PAGE = "allow_product_page";

    private static final String BASE = "https://ludorum.fr";
    private static final String ACCOUNT = BASE + "/mon-compte/";
    private static final String FAVORITES = BASE + "/favoris/";
    private static final String CART = BASE + "/panier/";

    private WebView web;
    private ProgressBar progress;
    private TextView title;
    private CartTicker cartTicker;

    private String initialUrl = BASE;
    private boolean cartMode = false;
    private boolean accountMode = false;
    private boolean allowProductPage = false;
    private String appScriptCache;

    private long lastPolishAt = 0L;
    private String lastPolishUrl = "";

    private LinearLayout navHome;
    private LinearLayout navAccount;
    private LinearLayout navFavorites;
    private LinearLayout navCart;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            boot(state);
        } catch (Throwable error) {
            showStartupError(error);
        }
    }

    private void boot(Bundle state) {
        configureSystemBars();
        build();

        String url = getIntent().getStringExtra(EXTRA_URL);
        String requestedTitle = getIntent().getStringExtra(EXTRA_TITLE);

        allowProductPage =
                getIntent().getBooleanExtra(
                        EXTRA_ALLOW_PRODUCT_PAGE,
                        false
                );

        initialUrl = url == null ? BASE : url;
        String initialLower =
                initialUrl.toLowerCase(java.util.Locale.ROOT);

        cartMode =
                initialLower.contains("/panier") ||
                initialLower.contains("/cart");

        if (cartMode) {
            openNativeScreen(
                    "cart",
                    null,
                    null
            );
            return;
        }

        accountMode =
                initialLower.contains("/mon-compte");

        title.setText(
                requestedTitle == null ? "Ludorum" : requestedTitle
        );

        if (state != null) {
            web.restoreState(state);
        } else {
            web.loadUrl(url == null ? BASE : url);
        }
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Ui.NAVY);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(
                0,
                Ui.topSystemSpace(this),
                0,
                0
        );

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(
                Ui.dp(this, 8),
                Ui.dp(this, 6),
                Ui.dp(this, 10),
                Ui.dp(this, 6)
        );
        top.setBackgroundColor(Color.WHITE);
        top.setElevation(Ui.dp(this, 3));

        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(Ui.NAVY);
        back.setPadding(
                Ui.dp(this, 8),
                Ui.dp(this, 8),
                Ui.dp(this, 8),
                Ui.dp(this, 8)
        );
        back.setOnClickListener(view -> onBackPressed());

        top.addView(
                back,
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 46),
                        Ui.dp(this, 46)
                )
        );

        title =
                Ui.text(
                        this,
                        "Ludorum",
                        17,
                        Ui.NAVY,
                        true
                );
        title.setGravity(Gravity.CENTER_VERTICAL);

        top.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        Ui.dp(this, 46),
                        1f
                )
        );

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ludorum_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        top.addView(
                logo,
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 100),
                        Ui.dp(this, 44)
                )
        );

        root.addView(top);

        cartTicker =
                new CartTicker(this);

        root.addView(
                cartTicker,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 34)
                )
        );

        progress =
                new ProgressBar(
                        this,
                        null,
                        android.R.attr.progressBarStyleHorizontal
                );
        progress.setMax(100);

        root.addView(
                progress,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 2)
                )
        );

        web = new WebView(this);
        WebSettings settings = web.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        try {
            settings.setDatabaseEnabled(true);
        } catch (Throwable ignored) {}
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        // Fluidité WebView.
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);

        web.setBackgroundColor(Color.WHITE);
        try {
            web.setLayerType(
                    View.LAYER_TYPE_HARDWARE,
                    null
            );
        } catch (Throwable ignored) {}
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setScrollbarFadingEnabled(true);

        // Le geste vertical appartient au WebView.
        web.setOnTouchListener((v, event) -> {
            try {
                android.view.ViewParent parent = v.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
            } catch (Exception ignored) {}
            return false;
        });

        settings.setUserAgentString(
                settings.getUserAgentString() +
                " LudorumAndroid/1.1.1"
        );

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        try {
            cookies.setAcceptThirdPartyCookies(
                    web,
                    true
            );
        } catch (Throwable ignored) {}

        web.addJavascriptInterface(
                new AppBridge(),
                "LudorumAndroidBridge"
        );

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        root.addView(
                web,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        View bottomHost = bottomNavHost();

        root.addView(
                bottomHost,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 70) +
                        Ui.bottomSystemSpace(this)
                )
        );

        setContentView(root);
    }

    private View bottomNavHost() {
        FrameLayout host =
                new FrameLayout(this);

        host.setBackgroundColor(
                Ui.NAVY
        );

        LinearLayout stripe =
                Ui.brandStripe(this);

        FrameLayout.LayoutParams stripeParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 4),
                        Gravity.TOP
                );

        host.addView(
                stripe,
                stripeParams
        );

        View nav =
                bottomNav();

        FrameLayout.LayoutParams navParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 66),
                        Gravity.TOP
                );

        navParams.topMargin =
                Ui.dp(this, 4);

        host.addView(
                nav,
                navParams
        );

        return host;
    }

    private View bottomNav() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(
                Ui.dp(this, 7),
                Ui.dp(this, 5),
                Ui.dp(this, 7),
                Ui.dp(this, 5)
        );
        bar.setBackground(
                Ui.gradient(
                        Color.WHITE,
                        Ui.SOFT,
                        0,
                        this
                )
        );
        bar.setElevation(
                Ui.dp(this, 14)
        );

        navHome =
                Ui.navItem(
                        this,
                        R.drawable.ic_home,
                        "Accueil",
                        false
                );
        navAccount =
                Ui.navItem(
                        this,
                        R.drawable.ic_person,
                        "Compte",
                        false
                );
        navFavorites =
                Ui.navItem(
                        this,
                        R.drawable.ic_heart,
                        "Favoris",
                        false
                );
        navCart =
                Ui.navItem(
                        this,
                        R.drawable.ic_cart,
                        "Panier",
                        false
                );

        navHome.setOnClickListener(view -> {
            openNativeScreen(
                    "home",
                    null,
                    null
            );
        });

        navAccount.setOnClickListener(view -> {
            if (isCurrentLudorumPath(
                    "/mon-compte"
            )) {
                web.scrollTo(
                        0,
                        0
                );

                updateBottomNav(
                        web.getUrl()
                );
                return;
            }

            web.stopLoading();
            cartMode = false;
            accountMode = true;
            title.setText("Mon compte");
            progress.setProgress(0);
            progress.setVisibility(View.VISIBLE);
            web.loadUrl(ACCOUNT);
        });

        navFavorites.setOnClickListener(view -> {
            openNativeScreen(
                    "favorites",
                    null,
                    null
            );
        });

        navCart.setOnClickListener(view -> {
            openNativeScreen(
                    "cart",
                    null,
                    null
            );
        });

        LinearLayout[] items =
                new LinearLayout[]{
                        navHome,
                        navAccount,
                        navFavorites,
                        navCart
                };

        for (LinearLayout item : items) {
            bar.addView(
                    item,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1f
                    )
            );
        }

        return bar;
    }

    private boolean isCurrentLudorumPath(
            String... pathParts
    ) {
        if (web == null ||
                pathParts == null ||
                pathParts.length == 0) {
            return false;
        }

        String current =
                web.getUrl();

        if (current == null ||
                current.trim().isEmpty()) {
            return false;
        }

        Uri uri;

        try {
            uri =
                    Uri.parse(current);
        } catch (Exception ignored) {
            return false;
        }

        if (!isLudorumHost(uri)) {
            return false;
        }

        String path =
                pathOf(uri);

        for (String part : pathParts) {
            if (part != null &&
                    !part.isEmpty() &&
                    path.contains(part)) {
                return true;
            }
        }

        return false;
    }

    private void updateBottomNav(String url) {
        String value =
                url == null
                        ? ""
                        : url.toLowerCase(java.util.Locale.ROOT);

        Ui.setNavActive(navHome, false);
        Ui.setNavActive(
                navAccount,
                value.contains("/mon-compte")
        );
        Ui.setNavActive(
                navFavorites,
                value.contains("/favoris")
        );
        Ui.setNavActive(
                navCart,
                value.contains("/panier") ||
                value.contains("/commande")
        );
    }

    private void safeRefreshTicker(
            boolean force,
            long delayMs
    ) {
        if (cartTicker == null) {
            return;
        }

        Runnable action =
                () -> {
                    try {
                        if (cartTicker != null &&
                                !isFinishing() &&
                                !isDestroyed()) {
                            cartTicker.refresh(force);
                        }
                    } catch (Throwable ignored) {
                        // Le bandeau est décoratif :
                        // aucune erreur ne doit pouvoir fermer l'app.
                    }
                };

        if (delayMs > 0L) {
            cartTicker.postDelayed(
                    action,
                    delayMs
            );
        } else {
            action.run();
        }
    }

    private void safeApplyAppPolish(
            WebView view
    ) {
        try {
            applyAppPolish(view);
        } catch (Throwable ignored) {
            // Le skin Ludorum améliore la page, mais le panier brut
            // doit rester accessible si le skin rencontre une erreur.
        }
    }

    private void applyAppPolish(WebView view) {
        try {
            if (view == null) {
                return;
            }

            String currentUrl =
                    view.getUrl() == null
                            ? ""
                            : view.getUrl();

            long now =
                    System.currentTimeMillis();

            if (currentUrl.equals(
                    lastPolishUrl
            ) &&
                    now - lastPolishAt < 1800L) {
                return;
            }

            lastPolishUrl =
                    currentUrl;

            lastPolishAt =
                    now;

            if (appScriptCache == null) {
                StringBuilder script = new StringBuilder();

                try (BufferedReader reader =
                             new BufferedReader(
                                     new InputStreamReader(
                                             getAssets().open("ludorum_app.js"),
                                             StandardCharsets.UTF_8
                                     )
                             )) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        script.append(line).append('\n');
                    }
                }

                appScriptCache = script.toString();
            }

            view.evaluateJavascript(appScriptCache, null);
        } catch (Exception ignored) {
            // Aucun popup de diagnostic en production.
        }
    }

    private void updateJourneyMode(
            String url
    ) {
        Uri uri = null;

        try {
            uri = Uri.parse(url);
        } catch (Exception ignored) {}

        if (uri == null ||
                !isLudorumHost(uri)) {
            return;
        }

        String path =
                pathOf(uri);

        String query =
                uri.getQuery() == null
                        ? ""
                        : uri.getQuery()
                                .toLowerCase(
                                        java.util.Locale.ROOT
                                );

        if (path.contains("/panier")
                || path.contains("/cart")
                || path.contains("/commande")
                || path.contains("/checkout")
                || path.contains("/order-pay")
                || path.contains("/order-received")
                || query.contains("wc-ajax=checkout")
                || query.contains("wc-api=")) {
            cartMode = true;
        }

        if (path.contains("/mon-compte")) {
            accountMode = true;
        }
    }

    private boolean isLudorumHost(Uri uri) {
        if (uri == null || uri.getHost() == null) return false;

        String host =
                uri.getHost()
                        .toLowerCase(java.util.Locale.ROOT);

        return host.equals("ludorum.fr") ||
                host.equals("www.ludorum.fr");
    }

    private String pathOf(Uri uri) {
        if (uri == null || uri.getPath() == null) return "/";
        return uri.getPath().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isCartAllowed(
            Uri uri
    ) {
        if (!isLudorumHost(uri)) {
            return true;
        }

        String path =
                pathOf(uri);

        String query =
                uri.getQuery() == null
                        ? ""
                        : uri.getQuery()
                                .toLowerCase(
                                        java.util.Locale.ROOT
                                );

        return path.contains("/panier")
                || path.contains("/cart")
                || path.contains("/commande")
                || path.contains("/checkout")
                || path.contains("/order-pay")
                || path.contains("/order-received")
                || path.contains("/mon-compte")
                || query.contains("wc-api=")
                || query.contains("wc-ajax=checkout")
                || query.contains("pay_for_order=")
                || query.contains("key=wc_order_");
    }

    private boolean isAccountAllowed(Uri uri) {
        if (!isLudorumHost(uri)) return true;

        String path = pathOf(uri);

        return path.contains("/mon-compte")
                || path.contains("/commande")
                || path.contains("/order-pay")
                || path.contains("/order-received");
    }

    private boolean looksLikeTechnicalShop(Uri uri) {
        if (!isLudorumHost(uri)) return false;

        String value =
                uri.toString()
                        .toLowerCase(java.util.Locale.ROOT);

        return value.contains("catalogue-woocommerce")
                || value.contains("woocommerce-technique")
                || value.contains("boutique-technique")
                || value.contains("/shop/")
                || value.contains("/shop?")
                || value.contains("post_type=product");
    }

    private void openNativeScreen(
            String screen,
            String key,
            String value
    ) {
        Intent intent =
                new Intent(
                        this,
                        MainActivity.class
                );

        intent.putExtra(
                "screen",
                screen
        );

        if (key != null &&
                value != null) {
            intent.putExtra(
                    key,
                    value
            );
        }

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        startActivity(intent);
        finish();
    }

    private void returnToNativeHome() {
        openNativeScreen(
                "home",
                null,
                null
        );
    }

    private String commerceSlugAfter(
            String path,
            String marker
    ) {
        if (path == null ||
                marker == null) {
            return null;
        }

        int index =
                path.indexOf(marker);

        if (index < 0) {
            return null;
        }

        String tail =
                path.substring(
                        index + marker.length()
                );

        if (tail.startsWith("/")) {
            tail =
                    tail.substring(1);
        }

        int slash =
                tail.indexOf("/");

        if (slash >= 0) {
            tail =
                    tail.substring(0, slash);
        }

        tail = tail.trim();

        return tail.isEmpty()
                ? null
                : tail;
    }

    private boolean looksLikeReturnToShop(
            Uri uri
    ) {
        if (uri == null ||
                !isLudorumHost(uri)) {
            return false;
        }

        String path =
                pathOf(uri);

        String raw =
                uri.toString()
                        .toLowerCase(
                                java.util.Locale.ROOT
                        );

        return path.equals("/boutique") ||
                path.equals("/boutique/") ||
                path.equals("/shop") ||
                path.equals("/shop/") ||
                raw.contains("continue-shopping") ||
                raw.contains("continue_shopping") ||
                raw.contains("return-to-shop");
    }

    private boolean routeCommerceToNative(
            Uri uri
    ) {
        if (uri == null ||
                !isLudorumHost(uri)) {
            return false;
        }

        String path =
                pathOf(uri);

        if (path.equals("/") ||
                path.isEmpty()) {
            openNativeScreen(
                    "home",
                    null,
                    null
            );
            return true;
        }

        if (path.equals("/favoris") ||
                path.equals("/favoris/") ||
                path.startsWith("/favoris/")) {
            openNativeScreen(
                    "favorites",
                    null,
                    null
            );
            return true;
        }

        if (path.contains("/panier") ||
                path.contains("/cart")) {
            openNativeScreen(
                    "cart",
                    null,
                    null
            );
            return true;
        }

        if (looksLikeReturnToShop(uri)) {
            openNativeScreen(
                    "shop",
                    null,
                    null
            );
            return true;
        }

        String productSlug =
                commerceSlugAfter(
                        path,
                        "/produit/"
                );

        if (productSlug == null) {
            productSlug =
                    commerceSlugAfter(
                            path,
                            "/product/"
                    );
        }

        if (productSlug != null) {
            if (allowProductPage &&
                    uri.toString().equalsIgnoreCase(
                            initialUrl
                    )) {
                return false;
            }

            openNativeScreen(
                    "product",
                    "product_slug",
                    productSlug
            );
            return true;
        }

        String categorySlug =
                commerceSlugAfter(
                        path,
                        "/categorie-produit/"
                );

        if (categorySlug == null) {
            categorySlug =
                    commerceSlugAfter(
                            path,
                            "/product-category/"
                    );
        }

        if (categorySlug != null) {
            openNativeScreen(
                    "category",
                    "category_slug",
                    categorySlug
            );
            return true;
        }

        String query =
                uri.getQueryParameter("s");

        String postType =
                uri.getQueryParameter("post_type");

        if (query != null &&
                !query.trim().isEmpty() &&
                (postType == null ||
                 postType.isEmpty() ||
                 postType.toLowerCase(
                         java.util.Locale.ROOT
                 ).contains("product"))) {
            openNativeScreen(
                    "search",
                    "search_query",
                    query
            );
            return true;
        }

        String value =
                uri.toString()
                        .toLowerCase(
                                java.util.Locale.ROOT
                        );

        if (path.contains("/boutique")
                || path.contains("/shop")
                || value.contains("catalogue-woocommerce")
                || value.contains("woocommerce-technique")
                || value.contains("boutique-technique")
                || value.contains("post_type=product")) {
            openNativeScreen(
                    "shop",
                    null,
                    null
            );
            return true;
        }

        return false;
    }


    private boolean handle(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;

        String scheme = uri.getScheme().toLowerCase();

        if (scheme.equals("http") || scheme.equals("https")) {
            // Depuis le panier, "revenir/continuer vers la boutique" doit
            // toujours ouvrir la Boutique native Ludorum.
            if (cartMode &&
                    looksLikeReturnToShop(uri)) {
                openNativeScreen(
                        "shop",
                        null,
                        null
                );
                return true;
            }

            // Tous les liens commerciaux Ludorum sont traduits vers la
            // boutique native. Compte, Panier et Checkout restent WebView.
            if (routeCommerceToNative(uri)) {
                return true;
            }

            // Le Panier n'est pas une mini-boutique WordPress.
            // Il ne peut naviguer que vers le panier, le compte et le checkout.
            if (cartMode && isLudorumHost(uri) && !isCartAllowed(uri)) {
                returnToNativeHome();
                return true;
            }

            // Même principe pour Mon compte : pas de fuite vers la boutique technique.
            if (accountMode &&
                    isLudorumHost(uri) &&
                    !isAccountAllowed(uri)) {
                returnToNativeHome();
                return true;
            }

            // Sécurité globale : la page technique ne doit jamais apparaître dans l'app.
            if (looksLikeTechnicalShop(uri)) {
                returnToNativeHome();
                return true;
            }

            return false;
        }

        if (scheme.equals("intent")) {
            try {
                Intent intent =
                        Intent.parseUri(
                                uri.toString(),
                                Intent.URI_INTENT_SCHEME
                        );

                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                    return true;
                }

                String fallback =
                        intent.getStringExtra("browser_fallback_url");

                if (fallback != null &&
                        fallback.startsWith("https://")) {
                    web.loadUrl(fallback);
                }
            } catch (Exception ignored) {}
            return true;
        }

        if (scheme.equals("mailto") ||
                scheme.equals("tel") ||
                scheme.equals("sms") ||
                scheme.equals("geo") ||
                scheme.equals("market")) {

            try {
                startActivity(
                        new Intent(Intent.ACTION_VIEW, uri)
                );
            } catch (ActivityNotFoundException ignored) {
                // Aucun popup : on reste simplement dans Ludorum.
            }
            return true;
        }

        return true;
    }

    private final class AppBridge {
        @JavascriptInterface
        public void cartChanged() {
            try {
                runOnUiThread(
                        () -> safeRefreshTicker(
                                true,
                                850L
                        )
                );
            } catch (Throwable ignored) {}
        }
    }

    private final class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(
                WebView view,
                WebResourceRequest request
        ) {
            Uri uri = request != null ? request.getUrl() : null;
            String host = uri != null && uri.getHost() != null
                    ? uri.getHost().toLowerCase(java.util.Locale.ROOT)
                    : "";

            // On ne bloque jamais WooCommerce, Stripe, PayPal ni ludorum.fr.
            // Seulement les trackers/pixels non essentiels dans l'application.
            if (host.contains("googletagmanager.com")
                    || host.contains("google-analytics.com")
                    || host.contains("doubleclick.net")
                    || host.contains("connect.facebook.net")
                    || host.contains("facebook.com/tr")
                    || host.contains("analytics.tiktok.com")
                    || host.contains("clarity.ms")
                    || host.contains("bat.bing.com")) {
                return new WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        new ByteArrayInputStream(
                                new byte[0]
                        )
                );
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(
                WebView view,
                WebResourceRequest request
        ) {
            return handle(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(
                WebView view,
                String url
        ) {
            return handle(Uri.parse(url));
        }

        @Override
        public void onPageCommitVisible(
                WebView view,
                String url
        ) {
            try {
                // Le contenu est déjà visible avant d'activer les fonctions
                // secondaires. En cas de souci, le panier reste accessible.
                safeApplyAppPolish(view);
                safeRefreshTicker(
                        false,
                        500L
                );
            } catch (Throwable ignored) {}

            try {
                super.onPageCommitVisible(
                        view,
                        url
                );
            } catch (Throwable ignored) {}
        }

        @Override
        public void onPageFinished(
                WebView view,
                String url
        ) {
            try {
                CookieManager.getInstance().flush();

                if (progress != null) {
                    progress.setVisibility(
                            View.GONE
                    );
                }

                Uri current = null;

                try {
                    current =
                            Uri.parse(url);
                } catch (Throwable ignored) {}

                if (current != null &&
                        routeCommerceToNative(current)) {
                    return;
                }

                if ((cartMode &&
                        current != null &&
                        !isCartAllowed(current))
                        || (accountMode &&
                        current != null &&
                        !isAccountAllowed(current))) {
                    returnToNativeHome();
                    return;
                }

                updateJourneyMode(url);
                updateBottomNav(url);

                // La page fonctionne même si le skin échoue.
                safeApplyAppPolish(view);

                // Le bandeau ne se rafraîchit qu'après la page,
                // et avec un léger délai.
                safeRefreshTicker(
                        false,
                        650L
                );

            } catch (Throwable ignored) {
                // Ne jamais faire tomber l'Activity à cause
                // d'un traitement post-chargement.
            }

            try {
                super.onPageFinished(
                        view,
                        url
                );
            } catch (Throwable ignored) {}
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(
                WebView view,
                int newProgress
        ) {
            progress.setVisibility(
                    newProgress >= 100
                            ? View.GONE
                            : View.VISIBLE
            );
            progress.setProgress(newProgress);
        }

        @Override
        public boolean onCreateWindow(
                WebView view,
                boolean dialog,
                boolean gesture,
                android.os.Message message
        ) {
            WebView popup = new WebView(WebActivity.this);
            WebSettings popupSettings = popup.getSettings();
            popupSettings.setJavaScriptEnabled(true);
            popupSettings.setDomStorageEnabled(true);
            popupSettings.setJavaScriptCanOpenWindowsAutomatically(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true);

            popup.setWebViewClient(
                    new WebViewClient() {
                        @Override
                        public boolean shouldOverrideUrlLoading(
                                WebView popupView,
                                WebResourceRequest request
                        ) {
                            Uri uri = request.getUrl();

                            if ("http".equalsIgnoreCase(uri.getScheme()) ||
                                    "https".equalsIgnoreCase(uri.getScheme())) {
                                if (!handle(uri)) {
                                    web.loadUrl(
                                            uri.toString()
                                    );
                                }
                            } else {
                                handle(uri);
                            }

                            popupView.destroy();
                            return true;
                        }

                        @Override
                        public boolean shouldOverrideUrlLoading(
                                WebView popupView,
                                String url
                        ) {
                            Uri uri = Uri.parse(url);

                            if ("http".equalsIgnoreCase(uri.getScheme()) ||
                                    "https".equalsIgnoreCase(uri.getScheme())) {
                                if (!handle(uri)) {
                                    web.loadUrl(url);
                                }
                            } else {
                                handle(uri);
                            }

                            popupView.destroy();
                            return true;
                        }
                    }
            );

            WebView.WebViewTransport transport =
                    (WebView.WebViewTransport) message.obj;

            transport.setWebView(popup);
            message.sendToTarget();
            return true;
        }
    }

    private void showStartupError(Throwable error) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(
                Ui.dp(this, 24),
                Ui.topSystemSpace(this) + Ui.dp(this, 24),
                Ui.dp(this, 24),
                Ui.bottomSystemSpace(this) + Ui.dp(this, 24)
        );
        page.setBackgroundColor(Color.WHITE);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ludorum_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        page.addView(
                logo,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 72)
                )
        );

        TextView titleView =
                Ui.text(
                        this,
                        "Impossible d’ouvrir cette page Ludorum.",
                        20,
                        Ui.NAVY,
                        true
                );
        titleView.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        titleParams.topMargin = Ui.dp(this, 20);
        page.addView(titleView, titleParams);

        TextView technical =
                Ui.text(
                        this,
                        error.getClass().getSimpleName() +
                                ": " +
                                String.valueOf(error.getMessage()),
                        11,
                        Ui.RED,
                        false
                );
        technical.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams technicalParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        technicalParams.topMargin = Ui.dp(this, 14);
        page.addView(technical, technicalParams);

        setContentView(page);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // IMPORTANT :
        // onResume doit rester extrêmement léger.
        // On ne touche plus au réseau panier ici : la page WebView
        // doit d'abord pouvoir s'afficher sans qu'un service secondaire
        // puisse faire tomber l'Activity.
        try {
            if (web != null) {
                web.onResume();
            }
        } catch (Throwable ignored) {
            // Le WebView restera piloté par son chargement normal.
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        if (web != null) web.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.setWebChromeClient(null);
            web.setWebViewClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
