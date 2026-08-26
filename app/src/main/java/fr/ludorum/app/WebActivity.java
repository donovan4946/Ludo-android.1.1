package fr.ludorum.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class WebActivity extends Activity {
    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_ALLOW_PRODUCT_PAGE = "allow_product_page";

    private static final String BASE = "https://ludorum.fr";
    private static final String ACCOUNT = BASE + "/mon-compte/";
    private static final String FAVORITES = BASE + "/favoris/";
    private static final String CART = BASE + "/panier/";
    private static final String LUDOMATCH = BASE + "/ludomatch/";
    private static final String LUDOMATCH_GROUP = BASE + "/ludomatch-groupe/";
    private static final String CONTACT = BASE + "/contact/";
    private static final String SUGGEST_PRODUCT = BASE + "/suggerer-un-produit/";
    private static final String LEGAL = BASE + "/mentions-legales/";
    private static final String PRIVACY = BASE + "/politique-de-confidentialite/";

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
        getWindow().setStatusBarColor(
                Color.WHITE
        );
        getWindow().setNavigationBarColor(
                Ui.NAVY
        );

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            getWindow().setNavigationBarDividerColor(
                    Ui.NAVY
            );
        }

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(
                    false
            );
        }

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

        LinearLayout top =
                new LinearLayout(this);
        top.setOrientation(
                LinearLayout.HORIZONTAL
        );
        top.setGravity(
                Gravity.CENTER_VERTICAL
        );
        top.setPadding(
                Ui.dp(this, 8),
                Ui.dp(this, 4),
                Ui.dp(this, 8),
                Ui.dp(this, 4)
        );
        top.setBackgroundColor(
                Color.WHITE
        );
        top.setElevation(
                Ui.dp(this, 3)
        );

        ImageView menuButton =
                new ImageView(this);
        menuButton.setImageResource(
                R.drawable.ic_menu
        );
        menuButton.setColorFilter(
                Ui.BLUE
        );
        menuButton.setPadding(
                Ui.dp(this, 9),
                Ui.dp(this, 9),
                Ui.dp(this, 9),
                Ui.dp(this, 9)
        );
        menuButton.setContentDescription(
                "Ouvrir le menu"
        );
        menuButton.setOnClickListener(
                this::openTopMenu
        );

        top.addView(
                menuButton,
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 42),
                        Ui.dp(this, 42)
                )
        );

        title =
                Ui.text(
                        this,
                        "Ludorum",
                        1,
                        Color.TRANSPARENT,
                        false
                );

        FrameLayout logoHost =
                new FrameLayout(this);

        ImageView logo =
                new ImageView(this);
        logo.setImageResource(
                R.drawable.ludorum_logo
        );
        logo.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        FrameLayout.LayoutParams logoParams =
                new FrameLayout.LayoutParams(
                        Ui.dp(this, 112),
                        Ui.dp(this, 34),
                        Gravity.CENTER
                );
        logoHost.addView(
                logo,
                logoParams
        );
        logoHost.setClickable(true);
        logoHost.setFocusable(true);
        logoHost.setContentDescription(
                "Retour à l'accueil"
        );
        logoHost.setOnClickListener(
                view ->
                        openNativeScreen(
                                "home",
                                null,
                                null
                        )
        );

        top.addView(
                logoHost,
                new LinearLayout.LayoutParams(
                        0,
                        Ui.dp(this, 42),
                        1f
                )
        );

        ImageView searchButton =
                new ImageView(this);
        searchButton.setImageResource(
                R.drawable.ic_search
        );
        searchButton.setColorFilter(
                Ui.BLUE
        );
        searchButton.setPadding(
                Ui.dp(this, 9),
                Ui.dp(this, 9),
                Ui.dp(this, 9),
                Ui.dp(this, 9)
        );
        searchButton.setContentDescription(
                "Rechercher"
        );
        searchButton.setOnClickListener(
                this::openTopSearch
        );

        top.addView(
                searchButton,
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 42),
                        Ui.dp(this, 42)
                )
        );

        ImageView cartButton =
                new ImageView(this);
        cartButton.setImageResource(
                R.drawable.ic_cart
        );
        cartButton.setColorFilter(
                Ui.BLUE
        );
        cartButton.setPadding(
                Ui.dp(this, 9),
                Ui.dp(this, 9),
                Ui.dp(this, 9),
                Ui.dp(this, 9)
        );
        cartButton.setContentDescription(
                "Ouvrir le panier"
        );
        cartButton.setOnClickListener(
                view ->
                        openNativeScreen(
                                "cart",
                                null,
                                null
                        )
        );

        top.addView(
                cartButton,
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 42),
                        Ui.dp(this, 42)
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
                " LudorumAndroid/1.1.19"
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

        View systemNavigationShield =
                new View(this);
        systemNavigationShield.setBackgroundColor(
                Ui.NAVY
        );

        root.addView(
                systemNavigationShield,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(
                                Ui.dp(this, 1),
                                Ui.bottomSystemSpace(this)
                        )
                )
        );

        setContentView(root);
    }

    private void openTopMenu(
            View anchor
    ) {
        ScrollView scroller =
                new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);

        LinearLayout panel =
                new LinearLayout(this);
        panel.setOrientation(
                LinearLayout.VERTICAL
        );
        panel.setPadding(
                Ui.dp(this, 12),
                Ui.dp(this, 12),
                Ui.dp(this, 12),
                Ui.dp(this, 12)
        );
        panel.setBackgroundColor(
                Color.WHITE
        );

        scroller.addView(
                panel,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        int popupWidth =
                Math.min(
                        getResources()
                                .getDisplayMetrics()
                                .widthPixels -
                        Ui.dp(this, 24),
                        Ui.dp(this, 310)
                );

        int popupHeight =
                Math.min(
                        (int) (
                                getResources()
                                        .getDisplayMetrics()
                                        .heightPixels *
                                0.76f
                        ),
                        Ui.dp(this, 640)
                );

        PopupWindow popup =
                new PopupWindow(
                        scroller,
                        popupWidth,
                        popupHeight,
                        true
                );
        popup.setBackgroundDrawable(
                new ColorDrawable(
                        Color.WHITE
                )
        );
        popup.setOutsideTouchable(true);
        popup.setElevation(
                Ui.dp(this, 14)
        );

        // 1) BOUTIQUE & ARTICLES
        LinearLayout shopBlock =
                topMenuSectionBlock(
                        "Boutique & articles",
                        Ui.BLUE
                );

        TextView shop =
                topMenuItem(
                        "Boutique",
                        Ui.BLUE
                );
        shop.setOnClickListener(
                view -> {
                    popup.dismiss();
                    openNativeScreen(
                            "shop",
                            null,
                            null
                    );
                }
        );
        shopBlock.addView(
                shop,
                topMenuItemParams()
        );

        TextView loadingCategories =
                topMenuItem(
                        "Chargement des catégories…",
                        Ui.MUTED
                );
        shopBlock.addView(
                loadingCategories,
                topMenuItemParams()
        );

        ApiClient.getTopCategories(
                new ApiClient.Callback<List<ProductCategory>>() {
                    @Override
                    public void onSuccess(
                            List<ProductCategory> categories
                    ) {
                        shopBlock.removeView(
                                loadingCategories
                        );

                        if (categories == null ||
                                categories.isEmpty()) {
                            TextView none =
                                    topMenuItem(
                                            "Aucune catégorie",
                                            Ui.MUTED
                                    );
                            shopBlock.addView(
                                    none,
                                    topMenuItemParams()
                            );
                            return;
                        }

                        int[] accents =
                                new int[]{
                                        Ui.BLUE,
                                        Ui.RED,
                                        Ui.YELLOW,
                                        Ui.NAVY
                                };

                        int index = 0;

                        for (ProductCategory category :
                                categories) {
                            int accent =
                                    accents[
                                            index++ %
                                            accents.length
                                    ];

                            addExpandableTopCategory(
                                    shopBlock,
                                    category,
                                    accent,
                                    popup
                            );
                        }
                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                        loadingCategories.setText(
                                "Catégories indisponibles"
                        );
                        loadingCategories.setTextColor(
                                Ui.RED
                        );
                    }
                }
        );

        panel.addView(
                shopBlock,
                topMenuSectionParams()
        );

        // 2) LUDOMATCH
        LinearLayout ludoMatchBlock =
                topMenuSectionBlock(
                        "LudoMatch",
                        Ui.YELLOW
                );

        TextView ludoMatch =
                topMenuItem(
                        "LudoMatch",
                        Ui.BLUE
                );

        ludoMatch.setOnClickListener(
                view -> {
                    popup.dismiss();

                    web.loadUrl(
                            LUDOMATCH
                    );
                }
        );

        ludoMatchBlock.addView(
                ludoMatch,
                topMenuItemParams()
        );

        TextView ludoMatchGroup =
                topMenuItem(
                        "LudoMatch Groupe",
                        Ui.RED
                );

        ludoMatchGroup.setOnClickListener(
                view -> {
                    popup.dismiss();

                    web.loadUrl(
                            LUDOMATCH_GROUP
                    );
                }
        );

        ludoMatchBlock.addView(
                ludoMatchGroup,
                topMenuItemParams()
        );

        panel.addView(
                ludoMatchBlock,
                topMenuSectionParams()
        );

        // 3) VOTRE ESPACE
        LinearLayout accountBlock =
                topMenuSectionBlock(
                        "Votre espace",
                        Ui.RED
                );

        TextView favorites =
                topMenuItem(
                        "Favoris",
                        Ui.RED
                );
        favorites.setOnClickListener(
                view -> {
                    popup.dismiss();
                    openNativeScreen(
                            "favorites",
                            null,
                            null
                    );
                }
        );
        accountBlock.addView(
                favorites,
                topMenuItemParams()
        );

        TextView account =
                topMenuItem(
                        "Mon compte",
                        Ui.YELLOW
                );
        account.setOnClickListener(
                view -> {
                    popup.dismiss();

                    if (isCurrentLudorumPath(
                            "/mon-compte"
                    )) {
                        web.scrollTo(0, 0);
                    } else {
                        web.stopLoading();
                        cartMode = false;
                        accountMode = true;
                        title.setText(
                                "Mon compte"
                        );
                        progress.setProgress(0);
                        progress.setVisibility(
                                View.VISIBLE
                        );
                        web.loadUrl(
                                ACCOUNT
                        );
                    }
                }
        );
        accountBlock.addView(
                account,
                topMenuItemParams()
        );

        panel.addView(
                accountBlock,
                topMenuSectionParams()
        );

        // 4) AIDE & INFORMATIONS
        LinearLayout infoBlock =
                topMenuSectionBlock(
                        "Aide & informations",
                        Ui.NAVY
                );

        TextView bug =
                topMenuItem(
                        "Signaler un bug",
                        Ui.RED
                );
        bug.setOnClickListener(
                view -> {
                    popup.dismiss();
                    web.loadUrl(
                            CONTACT
                    );
                }
        );
        infoBlock.addView(
                bug,
                topMenuItemParams()
        );

        TextView contact =
                topMenuItem(
                        "Nous contacter",
                        Ui.BLUE
                );
        contact.setOnClickListener(
                view -> {
                    popup.dismiss();
                    web.loadUrl(
                            CONTACT
                    );
                }
        );
        infoBlock.addView(
                contact,
                topMenuItemParams()
        );

        TextView suggest =
                topMenuItem(
                        "Suggérer un produit",
                        Ui.YELLOW
                );
        suggest.setOnClickListener(
                view -> {
                    popup.dismiss();
                    web.loadUrl(
                            SUGGEST_PRODUCT
                    );
                }
        );
        infoBlock.addView(
                suggest,
                topMenuItemParams()
        );

        TextView legal =
                topMenuItem(
                        "Mentions légales",
                        Ui.NAVY
                );
        legal.setOnClickListener(
                view -> {
                    popup.dismiss();
                    web.loadUrl(
                            LEGAL
                    );
                }
        );
        infoBlock.addView(
                legal,
                topMenuItemParams()
        );

        TextView privacy =
                topMenuItem(
                        "Politique de confidentialité",
                        Ui.NAVY
                );
        privacy.setOnClickListener(
                view -> {
                    popup.dismiss();
                    web.loadUrl(
                            PRIVACY
                    );
                }
        );
        infoBlock.addView(
                privacy,
                topMenuItemParams()
        );

        panel.addView(
                infoBlock,
                topMenuSectionParams()
        );

        popup.showAsDropDown(
                anchor,
                0,
                Ui.dp(this, 3)
        );
    }

    private LinearLayout topMenuSectionBlock(
            String title,
            int accent
    ) {
        LinearLayout block =
                new LinearLayout(this);
        block.setOrientation(
                LinearLayout.VERTICAL
        );
        block.setPadding(
                Ui.dp(this, 10),
                Ui.dp(this, 10),
                Ui.dp(this, 10),
                Ui.dp(this, 5)
        );
        block.setBackground(
                Ui.roundedStroke(
                        Ui.softAccent(accent),
                        Ui.BORDER,
                        1,
                        16,
                        this
                )
        );

        TextView heading =
                Ui.text(
                        this,
                        title.toUpperCase(
                                java.util.Locale.ROOT
                        ),
                        10,
                        accent == Ui.YELLOW
                                ? Ui.NAVY
                                : accent,
                        true
                );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        headingParams.bottomMargin =
                Ui.dp(this, 8);

        block.addView(
                heading,
                headingParams
        );

        return block;
    }

    private LinearLayout.LayoutParams topMenuSectionParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.bottomMargin =
                Ui.dp(this, 10);
        return params;
    }

    private void addExpandableTopCategory(
            LinearLayout host,
            ProductCategory category,
            int accent,
            PopupWindow popup
    ) {
        LinearLayout wrapper =
                new LinearLayout(this);
        wrapper.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout parentRow =
                new LinearLayout(this);
        parentRow.setOrientation(
                LinearLayout.HORIZONTAL
        );
        parentRow.setGravity(
                Gravity.CENTER_VERTICAL
        );
        parentRow.setPadding(
                Ui.dp(this, 13),
                0,
                Ui.dp(this, 9),
                0
        );
        parentRow.setBackground(
                Ui.roundedStroke(
                        Ui.softAccent(accent),
                        Color.TRANSPARENT,
                        0,
                        13,
                        this
                )
        );

        TextView label =
                Ui.text(
                        this,
                        category.name,
                        15,
                        accent == Ui.YELLOW
                                ? Ui.NAVY
                                : accent,
                        true
                );
        parentRow.addView(
                label,
                new LinearLayout.LayoutParams(
                        0,
                        Ui.dp(this, 44),
                        1f
                )
        );

        TextView arrow =
                Ui.text(
                        this,
                        "›",
                        22,
                        accent == Ui.YELLOW
                                ? Ui.NAVY
                                : accent,
                        true
                );
        arrow.setGravity(
                Gravity.CENTER
        );
        parentRow.addView(
                arrow,
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 28),
                        Ui.dp(this, 44)
                )
        );

        LinearLayout children =
                new LinearLayout(this);
        children.setOrientation(
                LinearLayout.VERTICAL
        );
        children.setVisibility(
                View.GONE
        );
        children.setPadding(
                Ui.dp(this, 18),
                Ui.dp(this, 6),
                Ui.dp(this, 4),
                Ui.dp(this, 2)
        );

        final boolean[] loaded =
                new boolean[]{false};
        final boolean[] loading =
                new boolean[]{false};

        Runnable toggle =
                () -> {
                    if (children.getVisibility() ==
                            View.VISIBLE) {
                        children.setVisibility(
                                View.GONE
                        );
                        arrow.setText("›");
                        return;
                    }

                    children.setVisibility(
                            View.VISIBLE
                    );
                    arrow.setText("⌄");

                    if (loaded[0] ||
                            loading[0]) {
                        return;
                    }

                    loading[0] = true;
                    children.removeAllViews();

                    TextView loadingText =
                            topCategorySubMenuItem(
                                    "Chargement…",
                                    Ui.MUTED,
                                    false
                            );
                    children.addView(
                            loadingText,
                            topCategorySubMenuItemParams()
                    );

                    ApiClient.getChildCategories(
                            category.id,
                            new ApiClient.Callback<List<ProductCategory>>() {
                                @Override
                                public void onSuccess(
                                        List<ProductCategory> subcategories
                                ) {
                                    loading[0] = false;
                                    loaded[0] = true;
                                    children.removeAllViews();

                                    TextView all =
                                            topCategorySubMenuItem(
                                                    "Voir tout " +
                                                    category.name,
                                                    accent == Ui.YELLOW
                                                            ? Ui.NAVY
                                                            : accent,
                                                    true
                                            );
                                    all.setOnClickListener(
                                            childView -> {
                                                popup.dismiss();
                                                openNativeScreen(
                                                        "category",
                                                        "category_slug",
                                                        category.slug
                                                );
                                            }
                                    );
                                    children.addView(
                                            all,
                                            topCategorySubMenuItemParams()
                                    );

                                    if (subcategories == null ||
                                            subcategories.isEmpty()) {
                                        TextView none =
                                                topCategorySubMenuItem(
                                                        "Aucune sous-catégorie",
                                                        Ui.MUTED,
                                                        false
                                                );
                                        children.addView(
                                                none,
                                                topCategorySubMenuItemParams()
                                        );
                                        return;
                                    }

                                    for (ProductCategory child :
                                            subcategories) {
                                        TextView childItem =
                                                topCategorySubMenuItem(
                                                        child.name,
                                                        Ui.TEXT,
                                                        false
                                                );
                                        childItem.setOnClickListener(
                                                childView -> {
                                                    popup.dismiss();
                                                    openNativeScreen(
                                                            "category",
                                                            "category_slug",
                                                            child.slug
                                                    );
                                                }
                                        );
                                        children.addView(
                                                childItem,
                                                topCategorySubMenuItemParams()
                                        );
                                    }
                                }

                                @Override
                                public void onError(
                                        Exception error
                                ) {
                                    loading[0] = false;
                                    children.removeAllViews();

                                    TextView retry =
                                            topCategorySubMenuItem(
                                                    "Réessayer",
                                                    Ui.RED,
                                                    true
                                            );
                                    retry.setOnClickListener(
                                            childView -> {
                                                loaded[0] = false;
                                                children.setVisibility(
                                                        View.GONE
                                                );
                                                parentRow.performClick();
                                            }
                                    );
                                    children.addView(
                                            retry,
                                            topCategorySubMenuItemParams()
                                    );
                                }
                            }
                    );
                };

        parentRow.setOnClickListener(
                view -> toggle.run()
        );

        wrapper.addView(
                parentRow,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 44)
                )
        );

        wrapper.addView(
                children,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        // CRITICAL FIX: do not constrain wrapper to 44dp.
        LinearLayout.LayoutParams wrapperParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        wrapperParams.bottomMargin =
                Ui.dp(this, 7);

        host.addView(
                wrapper,
                wrapperParams
        );
    }

    private TextView topCategorySubMenuItem(
            String label,
            int color,
            boolean bold
    ) {
        TextView item =
                Ui.text(
                        this,
                        label,
                        13,
                        color,
                        bold
                );
        item.setGravity(
                Gravity.CENTER_VERTICAL
        );
        item.setPadding(
                Ui.dp(this, 12),
                0,
                Ui.dp(this, 10),
                0
        );
        item.setBackground(
                Ui.roundedStroke(
                        Color.WHITE,
                        Ui.BORDER,
                        1,
                        10,
                        this
                )
        );
        return item;
    }

    private LinearLayout.LayoutParams topCategorySubMenuItemParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 38)
                );
        params.bottomMargin =
                Ui.dp(this, 5);
        return params;
    }

    private TextView topMenuItem(
            String label,
            int accent
    ) {
        int textColor =
                accent == Ui.YELLOW
                        ? Ui.NAVY
                        : accent;

        TextView item =
                Ui.text(
                        this,
                        label,
                        15,
                        textColor,
                        true
                );
        item.setGravity(
                Gravity.CENTER_VERTICAL
        );
        item.setPadding(
                Ui.dp(this, 13),
                0,
                Ui.dp(this, 13),
                0
        );
        item.setBackground(
                Ui.roundedStroke(
                        Ui.softAccent(accent),
                        Color.TRANSPARENT,
                        0,
                        13,
                        this
                )
        );
        return item;
    }

    private LinearLayout.LayoutParams topMenuItemParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 44)
                );
        params.bottomMargin =
                Ui.dp(this, 7);
        return params;
    }

    private void openTopSearch(
            View anchor
    ) {
        LinearLayout panel =
                new LinearLayout(this);
        panel.setOrientation(
                LinearLayout.VERTICAL
        );
        panel.setPadding(
                Ui.dp(this, 13),
                Ui.dp(this, 12),
                Ui.dp(this, 13),
                Ui.dp(this, 13)
        );
        panel.setBackground(
                Ui.roundedStroke(
                        Color.WHITE,
                        Ui.BORDER,
                        1,
                        18,
                        this
                )
        );

        EditText field =
                new EditText(this);
        field.setSingleLine(true);
        field.setHint(
                "Jeu, carte, accessoire…"
        );
        field.setTextSize(15);
        field.setTextColor(
                Ui.TEXT
        );
        field.setHintTextColor(
                Color.rgb(
                        145,
                        154,
                        166
                )
        );
        field.setInputType(
                InputType.TYPE_CLASS_TEXT
        );
        field.setImeOptions(
                EditorInfo.IME_ACTION_SEARCH
        );
        field.setPadding(
                Ui.dp(this, 12),
                0,
                Ui.dp(this, 12),
                0
        );
        field.setBackground(
                Ui.roundedStroke(
                        Ui.SOFT,
                        Ui.BORDER,
                        1,
                        22,
                        this
                )
        );

        panel.addView(
                field,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 46)
                )
        );

        TextView action =
                Ui.pill(
                        this,
                        "Rechercher",
                        Color.WHITE,
                        Ui.BLUE,
                        Ui.BLUE
                );

        LinearLayout.LayoutParams actionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 44)
                );
        actionParams.topMargin =
                Ui.dp(this, 9);

        panel.addView(
                action,
                actionParams
        );

        int popupWidth =
                Math.min(
                        getResources()
                                .getDisplayMetrics()
                                .widthPixels -
                        Ui.dp(this, 28),
                        Ui.dp(this, 350)
                );

        PopupWindow popup =
                new PopupWindow(
                        panel,
                        popupWidth,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        true
                );
        popup.setBackgroundDrawable(
                new ColorDrawable(
                        Color.TRANSPARENT
                )
        );
        popup.setOutsideTouchable(true);
        popup.setElevation(
                Ui.dp(this, 12)
        );

        Runnable submit =
                () -> {
                    String query =
                            field.getText()
                                    .toString()
                                    .trim();
                    if (query.isEmpty()) {
                        return;
                    }

                    popup.dismiss();
                    openNativeScreen(
                            "search",
                            "search_query",
                            query
                    );
                };

        action.setOnClickListener(
                view -> submit.run()
        );

        field.setOnEditorActionListener(
                (view, actionId, event) -> {
                    boolean enter =
                            event != null &&
                            event.getKeyCode() ==
                            KeyEvent.KEYCODE_ENTER;

                    if (actionId ==
                            EditorInfo.IME_ACTION_SEARCH ||
                            enter) {
                        submit.run();
                        return true;
                    }
                    return false;
                }
        );

        popup.showAsDropDown(
                anchor,
                -popupWidth +
                Ui.dp(this, 42),
                Ui.dp(this, 3)
        );
        field.requestFocus();
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

    private void refreshTickerWithoutCompetingWithAccount(
            boolean force,
            long delayMs
    ) {
        if (accountMode) {
            try {
                CartService.CartSnapshot cached =
                        CartService.getCachedSnapshot();

                if (cached != null &&
                        cartTicker != null) {
                    cartTicker.applySnapshot(
                            cached
                    );
                }
            } catch (Throwable ignored) {}

            return;
        }

        safeRefreshTicker(
                force,
                delayMs
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

    private void sendLudoMatchCartSuccess(
            String slug
    ) {
        try {
            final String safeSlug =
                    JSONObject.quote(
                            slug == null
                                    ? ""
                                    : slug
                    );

            runOnUiThread(
                    () -> {
                        try {
                            if (web != null) {
                                web.evaluateJavascript(
                                        "window.__ludorumMatchCartSuccess && " +
                                        "window.__ludorumMatchCartSuccess(" +
                                        safeSlug +
                                        ");",
                                        null
                                );
                            }
                        } catch (Throwable ignored) {}
                    }
            );
        } catch (Throwable ignored) {}
    }

    private void sendLudoMatchCartError(
            String slug,
            String message
    ) {
        try {
            final String safeSlug =
                    JSONObject.quote(
                            slug == null
                                    ? ""
                                    : slug
                    );

            final String safeMessage =
                    JSONObject.quote(
                            message == null
                                    ? "Ajout impossible."
                                    : message
                    );

            runOnUiThread(
                    () -> {
                        try {
                            if (web != null) {
                                web.evaluateJavascript(
                                        "window.__ludorumMatchCartError && " +
                                        "window.__ludorumMatchCartError(" +
                                        safeSlug +
                                        "," +
                                        safeMessage +
                                        ");",
                                        null
                                );
                            }
                        } catch (Throwable ignored) {}
                    }
            );
        } catch (Throwable ignored) {}
    }

    private final class AppBridge {
        @JavascriptInterface
        public void addProductIdToCart(
                int productId
        ) {
            if (productId <= 0) {
                return;
            }

            try {
                CartService.addOne(
                        productId,
                        new CartService.Callback() {
                            @Override
                            public void onSuccess(
                                    CartService.CartSnapshot snapshot
                            ) {
                                try {
                                    if (cartTicker != null) {
                                        cartTicker.applySnapshot(
                                                snapshot
                                        );
                                    }

                                    if (web != null) {
                                        web.evaluateJavascript(
                                                "window.__ludorumMatchCartSuccessId && " +
                                                "window.__ludorumMatchCartSuccessId(" +
                                                productId +
                                                ");",
                                                null
                                        );
                                    }
                                } catch (Throwable ignored) {}
                            }

                            @Override
                            public void onError(
                                    String message
                            ) {
                                try {
                                    final String safeMessage =
                                            JSONObject.quote(
                                                    message == null ||
                                                    message.trim().isEmpty()
                                                            ? "Ajout impossible."
                                                            : message
                                            );

                                    if (web != null) {
                                        web.evaluateJavascript(
                                                "window.__ludorumMatchCartErrorId && " +
                                                "window.__ludorumMatchCartErrorId(" +
                                                productId +
                                                "," +
                                                safeMessage +
                                                ");",
                                                null
                                        );
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                );
            } catch (Throwable ignored) {}
        }

        @JavascriptInterface
        public void addProductSlugToCart(
                String slug
        ) {
            if (slug == null ||
                    slug.trim().isEmpty()) {
                return;
            }

            final String safeSlug =
                    slug.trim();

            try {
                ApiClient.getProductBySlug(
                        safeSlug,
                        new ApiClient.Callback<Product>() {
                            @Override
                            public void onSuccess(
                                    Product product
                            ) {
                                if (product == null ||
                                        product.id <= 0) {
                                    sendLudoMatchCartError(
                                            safeSlug,
                                            "Produit introuvable."
                                    );
                                    return;
                                }

                                if (!product.inStock ||
                                        !product.purchasable) {
                                    sendLudoMatchCartError(
                                            safeSlug,
                                            "Produit indisponible."
                                    );
                                    return;
                                }

                                if (!"simple".equalsIgnoreCase(
                                        product.type
                                )) {
                                    runOnUiThread(
                                            () -> openNativeScreen(
                                                    "product",
                                                    product.slug,
                                                    null
                                            )
                                    );
                                    return;
                                }

                                CartService.addOne(
                                        product.id,
                                        new CartService.Callback() {
                                            @Override
                                            public void onSuccess(
                                                    CartService.CartSnapshot snapshot
                                            ) {
                                                runOnUiThread(
                                                        () -> {
                                                            try {
                                                                if (cartTicker != null) {
                                                                    cartTicker.applySnapshot(
                                                                            snapshot
                                                                    );
                                                                }

                                                                sendLudoMatchCartSuccess(
                                                                        safeSlug
                                                                );
                                                            } catch (Throwable ignored) {}
                                                        }
                                                );
                                            }

                                            @Override
                                            public void onError(
                                                    String message
                                            ) {
                                                sendLudoMatchCartError(
                                                        safeSlug,
                                                        message == null ||
                                                        message.trim().isEmpty()
                                                                ? "Ajout impossible."
                                                                : message
                                                );
                                            }
                                        }
                                );
                            }

                            @Override
                            public void onError(
                                    Exception error
                            ) {
                                sendLudoMatchCartError(
                                        safeSlug,
                                        "Produit introuvable."
                                );
                            }
                        }
                );

            } catch (Throwable ignored) {
                sendLudoMatchCartError(
                        safeSlug,
                        "Ajout impossible."
                );
            }
        }

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
                refreshTickerWithoutCompetingWithAccount(
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

                // La page fonctionne même si le skin échoue.
                safeApplyAppPolish(view);

                // Le bandeau ne se rafraîchit qu'après la page,
                // et avec un léger délai.
                refreshTickerWithoutCompetingWithAccount(
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
