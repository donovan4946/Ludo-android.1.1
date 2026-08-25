package fr.ludorum.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.Html;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String BASE = "https://ludorum.fr";
    private static final String ACCOUNT = BASE + "/mon-compte/";
    private static final String FAVORITES = BASE + "/favoris/";
    private static final String CART = BASE + "/panier/";
    private static final String CHECKOUT = BASE + "/commande/";
    private static final String LUDOMATCH = BASE + "/ludomatch/";
    private static final String LUDOMATCH_GROUP = BASE + "/ludomatch-groupe/";

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout categoryRow;
    private ScrollView scroll;
    private EditText search;
    private CartTicker cartTicker;

    private FavoriteStore favoriteStore;
    private final Map<Integer, List<ImageView>> favoriteHeartViews =
            new HashMap<>();

    private LinearLayout navHome;
    private LinearLayout navAccount;
    private LinearLayout navFavorites;
    private LinearLayout navCart;

    private boolean favoritesMode = false;
    private boolean productMode = false;
    private boolean catalogueMode = false;
    private boolean cartMode = false;

    private boolean cartMutationInFlight = false;
    private CartService.CartSnapshot currentCartSnapshot;

    // Empêche une ancienne requête réseau de continuer à construire
    // des vues/images après que l'utilisateur a changé d'écran.
    private int screenGeneration = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            boot();
        } catch (Exception error) {
            showStartupError(error);
        }
    }

    private void boot() {
        configureSystemBars();

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);

        favoriteStore = new FavoriteStore(this);

        buildScreen();
        loadCategories();

        Intent launchIntent =
                getIntent();

        Uri deeplink =
                launchIntent != null
                        ? launchIntent.getData()
                        : null;

        if (deeplink != null &&
                "https".equalsIgnoreCase(
                        deeplink.getScheme()
                )) {
            routeNativeUri(deeplink);
            return;
        }

        if (hasSupportedRequestedScreen(
                launchIntent
        )) {
            handleRequestedScreen(
                    launchIntent
            );
            return;
        }

        showHome();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Ui.NAVY);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
    }

    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        buildHeader();

        cartTicker =
                new CartTicker(this);

        root.addView(
                cartTicker,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 34)
                )
        );

        buildContent();

        View bottomHost = buildBottomNavHost();
        root.addView(
                bottomHost,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 70) + Ui.bottomSystemSpace(this)
                )
        );

        setContentView(root);
    }

    private void buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Color.WHITE);

        header.setPadding(
                Ui.dp(this, 14),
                Ui.topSystemSpace(this) + Ui.dp(this, 8),
                Ui.dp(this, 14),
                Ui.dp(this, 7)
        );

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ludorum_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        header.addView(
                logo,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 64)
                )
        );

        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(
                Ui.dp(this, 15),
                0,
                Ui.dp(this, 10),
                0
        );
        searchBox.setBackground(
                Ui.roundedStroke(
                        Ui.SOFT,
                        Ui.BORDER,
                        1,
                        26,
                        this
                )
        );

        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(Ui.MUTED);
        searchBox.addView(
                searchIcon,
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 22),
                        Ui.dp(this, 22)
                )
        );

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(Ui.TEXT);
        search.setHintTextColor(Color.rgb(145, 154, 166));
        search.setHint("Rechercher dans Ludorum…");
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(Ui.dp(this, 10), 0, 0, 0);

        search.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter =
                    event != null &&
                    event.getKeyCode() == KeyEvent.KEYCODE_ENTER;

            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                String query = search.getText().toString().trim();
                if (!query.isEmpty()) searchProducts(query);
                hideKeyboard();
                return true;
            }
            return false;
        });

        searchBox.addView(
                search,
                new LinearLayout.LayoutParams(
                        0,
                        Ui.dp(this, 52),
                        1f
                )
        );

        LinearLayout.LayoutParams searchParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 54)
                );
        searchParams.topMargin = Ui.dp(this, 5);
        header.addView(searchBox, searchParams);

        HorizontalScrollView categoryScroll =
                new HorizontalScrollView(this);

        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryScroll.setFillViewport(false);

        categoryRow = new LinearLayout(this);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        categoryRow.setPadding(
                0,
                Ui.dp(this, 8),
                Ui.dp(this, 8),
                Ui.dp(this, 4)
        );

        categoryScroll.addView(
                categoryRow,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        header.addView(
                categoryScroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 58)
                )
        );

        root.addView(
                header,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private void buildContent() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.WHITE);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                Ui.dp(this, 16),
                Ui.dp(this, 8),
                Ui.dp(this, 16),
                Ui.dp(this, 28)
        );

        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );
    }

    private View buildBottomNavHost() {
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
                buildBottomNav();

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

    private View buildBottomNav() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
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
                Ui.navItem(this, R.drawable.ic_home, "Accueil", true);
        navAccount =
                Ui.navItem(this, R.drawable.ic_person, "Compte", false);
        navFavorites =
                Ui.navItem(this, R.drawable.ic_heart, "Favoris", false);
        navCart =
                Ui.navItem(this, R.drawable.ic_cart, "Panier", false);

        navHome.setOnClickListener(view -> {
            if (!catalogueMode &&
                    !favoritesMode &&
                    !productMode &&
                    !cartMode &&
                    content != null &&
                    content.getChildCount() > 0) {
                scroll.smoothScrollTo(
                        0,
                        0
                );
                return;
            }

            showHome();
        });

        navAccount.setOnClickListener(
                view -> openWeb(
                        ACCOUNT,
                        "Mon compte"
                )
        );

        navFavorites.setOnClickListener(view -> {
            if (favoritesMode) {
                scroll.smoothScrollTo(
                        0,
                        0
                );
                return;
            }

            showFavorites();
        });

        navCart.setOnClickListener(
                view -> {
                    if (cartMode) {
                        scroll.smoothScrollTo(
                                0,
                                0
                        );
                        return;
                    }

                    showCart();
                }
        );

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

    private void loadCategories() {
        categoryRow.removeAllViews();

        TextView all = chip("Boutique", Ui.BLUE);
        all.setOnClickListener(
                view -> showCatalogue(
                        "Boutique",
                        "Tous les produits Ludorum.",
                        "",
                        1
                )
        );
        categoryRow.addView(all, chipParams());

        ApiClient.getTopCategories(
                new ApiClient.Callback<List<ProductCategory>>() {
                    @Override
                    public void onSuccess(List<ProductCategory> categories) {
                        int[] colors =
                                new int[]{
                                        Ui.BLUE,
                                        Ui.RED,
                                        Ui.YELLOW,
                                        Ui.NAVY
                                };
                        int index = 0;

                        for (ProductCategory category : categories) {
                            int accent = colors[index++ % colors.length];
                            TextView item = chip(category.name, accent);

                            item.setOnClickListener(
                                    view -> showCatalogue(
                                            category.name,
                                            "Parcourez cette catégorie du catalogue.",
                                            "&category=" + category.id,
                                            1
                                    )
                            );
                            categoryRow.addView(item, chipParams());
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        // "Boutique" reste disponible même hors connexion.
                    }
                }
        );
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Ui.dp(this, 44)
                );
        params.setMargins(0, 0, Ui.dp(this, 9), 0);
        return params;
    }

    private TextView chip(String label, int accent) {
        int textColor =
                accent == Ui.YELLOW ? Ui.NAVY : accent;

        TextView item =
                Ui.pill(
                        this,
                        label,
                        textColor,
                        Color.WHITE,
                        accent
                );
        item.setTextSize(14);
        return item;
    }

    private void showHome() {
        final int generation = ++screenGeneration;
        catalogueMode = false;
        favoritesMode = false;
        productMode = false;
        cartMode = false;
        setMainNavActive("home");
        favoriteHeartViews.clear();
        search.setText("");
        content.removeAllViews();
        scroll.scrollTo(0, 0);

        addHero();

        content.addView(space(16));

        new ProductSection(
                "Nouveautés",
                "TOUT JUSTE ARRIVÉS",
                "Les derniers produits ajoutés au catalogue.",
                "&orderby=date&order=desc",
                Ui.BLUE
        ).attach(content);

        new ProductSection(
                "Promotions",
                "LES BONNES AFFAIRES",
                "Une sélection de produits actuellement à prix réduit.",
                "&on_sale=true&orderby=date&order=desc",
                Ui.RED
        ).attach(content);

        new ProductSection(
                "Meilleures ventes",
                "LES PRÉFÉRÉS DES JOUEURS",
                "Les références les plus commandées sur Ludorum.",
                "&orderby=popularity&order=desc",
                Ui.YELLOW
        ).attach(content);
    }

    private void addHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(
                Ui.dp(this, 21),
                Ui.dp(this, 20),
                Ui.dp(this, 21),
                Ui.dp(this, 20)
        );
        hero.setBackground(
                Ui.gradient(Ui.NAVY, Ui.BLUE, 22, this)
        );
        hero.setElevation(Ui.dp(this, 4));

        TextView eyebrow =
                Ui.text(
                        this,
                        "BIENVENUE CHEZ LUDORUM",
                        10,
                        Color.rgb(255, 207, 58),
                        true
                );
        hero.addView(eyebrow);

        TextView title =
                Ui.text(
                        this,
                        "Le bon jeu, sans chercher pendant des heures.",
                        25,
                        Color.WHITE,
                        true
                );
        title.setLineSpacing(0, 0.98f);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        titleParams.topMargin = Ui.dp(this, 8);
        hero.addView(title, titleParams);

        TextView subtitle =
                Ui.text(
                        this,
                        "Explorez le catalogue ou laissez LudoMatch vous guider selon vos envies.",
                        14,
                        Color.rgb(222, 233, 249),
                        false
                );
        subtitle.setLineSpacing(Ui.dp(this, 3), 1f);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.topMargin = Ui.dp(this, 10);
        hero.addView(subtitle, subtitleParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView solo =
                Ui.pill(
                        this,
                        "Trouver mon jeu",
                        Ui.NAVY,
                        Color.rgb(255, 199, 43),
                        Color.rgb(255, 199, 43)
                );

        TextView group =
                Ui.pill(
                        this,
                        "Choisir à plusieurs",
                        Color.WHITE,
                        Color.TRANSPARENT,
                        Color.WHITE
                );

        solo.setTextSize(13);
        group.setTextSize(13);

        solo.setOnClickListener(
                view -> openWeb(LUDOMATCH, "LudoMatch")
        );
        group.setOnClickListener(
                view -> openWeb(LUDOMATCH_GROUP, "LudoMatch Groupe")
        );

        actions.addView(
                solo,
                new LinearLayout.LayoutParams(
                        0,
                        Ui.dp(this, 47),
                        1f
                )
        );

        LinearLayout.LayoutParams groupParams =
                new LinearLayout.LayoutParams(
                        0,
                        Ui.dp(this, 47),
                        1f
                );
        groupParams.leftMargin = Ui.dp(this, 9);
        actions.addView(group, groupParams);

        LinearLayout.LayoutParams actionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        actionParams.topMargin = Ui.dp(this, 17);
        hero.addView(actions, actionParams);

        content.addView(
                hero,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private View space(int dp) {
        View view = new View(this);
        view.setLayoutParams(
                new LinearLayout.LayoutParams(
                        1,
                        Ui.dp(this, dp)
                )
        );
        return view;
    }

    private void searchProducts(String query) {
        String encoded;
        try {
            encoded = URLEncoder.encode(query, "UTF-8");
        } catch (Exception ignored) {
            encoded = query.replace(" ", "%20");
        }

        showCatalogue(
                "Résultats pour « " + query + " »",
                "Produits correspondant à votre recherche.",
                "&search=" + encoded,
                1
        );
    }

    private void showCatalogue(
            String title,
            String subtitle,
            String query,
            int page
    ) {
        final int generation = ++screenGeneration;
        catalogueMode = true;
        favoritesMode = false;
        productMode = false;
        cartMode = false;
        setMainNavActive("home");
        favoriteHeartViews.clear();
        content.removeAllViews();
        scroll.scrollTo(0, 0);

        TextView kicker =
                Ui.text(
                        this,
                        "CATALOGUE LUDORUM",
                        10,
                        Ui.BLUE,
                        true
                );
        content.addView(kicker);

        TextView heading =
                Ui.text(
                        this,
                        title,
                        27,
                        Ui.NAVY,
                        true
                );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        headingParams.topMargin = Ui.dp(this, 5);
        content.addView(heading, headingParams);

        TextView sub =
                Ui.text(
                        this,
                        subtitle,
                        14,
                        Ui.MUTED,
                        false
                );

        LinearLayout.LayoutParams subParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subParams.topMargin = Ui.dp(this, 6);
        subParams.bottomMargin = Ui.dp(this, 16);
        content.addView(sub, subParams);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        content.addView(grid);

        ProgressBar progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 42),
                        Ui.dp(this, 42)
                );
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = Ui.dp(this, 24);
        grid.addView(progress, progressParams);

        String finalQuery = ensureDefaultOrdering(query);

        ApiClient.getProducts(
                finalQuery,
                Math.max(1, page),
                8,
                new ApiClient.Callback<ApiClient.ProductPage>() {
                    @Override
                    public void onSuccess(ApiClient.ProductPage result) {
                        if (generation != screenGeneration) {
                            return;
                        }

                        grid.removeAllViews();
                        renderGrid(result.products, grid);

                        content.addView(
                                makePager(
                                        result.page,
                                        result.totalPages,
                                        targetPage ->
                                                showCatalogue(
                                                        title,
                                                        subtitle,
                                                        query,
                                                        targetPage
                                                )
                                )
                        );
                    }

                    @Override
                    public void onError(Exception error) {
                        if (generation != screenGeneration) {
                            return;
                        }

                        grid.removeAllViews();
                        grid.addView(
                                retryView(
                                        "Impossible de charger le catalogue.",
                                        view ->
                                                showCatalogue(
                                                        title,
                                                        subtitle,
                                                        query,
                                                        page
                                                )
                                )
                        );
                    }
                }
        );
    }

    private String ensureDefaultOrdering(String query) {
        String value = query == null ? "" : query;
        if (value.contains("orderby=")) return value;
        return value + "&orderby=date&order=desc";
    }

    private interface PageHandler {
        void go(int page);
    }

    private View makePager(
            int page,
            int total,
            PageHandler handler
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(
                0,
                Ui.dp(this, 18),
                0,
                Ui.dp(this, 12)
        );

        TextView previous =
                Ui.pill(
                        this,
                        "‹ Précédent",
                        page > 1 ? Ui.BLUE : Ui.MUTED,
                        Color.WHITE,
                        page > 1 ? Ui.BLUE : Ui.BORDER
                );

        previous.setEnabled(page > 1);
        previous.setAlpha(page > 1 ? 1f : .42f);

        if (page > 1) {
            previous.setOnClickListener(
                    view -> handler.go(page - 1)
            );
        }

        TextView label =
                Ui.text(
                        this,
                        "Page " + page + " / " + Math.max(page, total),
                        13,
                        Ui.NAVY,
                        true
                );
        label.setGravity(Gravity.CENTER);

        TextView next =
                Ui.pill(
                        this,
                        "Suivant ›",
                        page < total ? Ui.BLUE : Ui.MUTED,
                        Color.WHITE,
                        page < total ? Ui.BLUE : Ui.BORDER
                );

        next.setEnabled(page < total);
        next.setAlpha(page < total ? 1f : .42f);

        if (page < total) {
            next.setOnClickListener(
                    view -> handler.go(page + 1)
            );
        }

        row.addView(
                previous,
                new LinearLayout.LayoutParams(
                        0,
                        Ui.dp(this, 44),
                        1f
                )
        );

        row.addView(
                label,
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 104),
                        Ui.dp(this, 44)
                )
        );

        row.addView(
                next,
                new LinearLayout.LayoutParams(
                        0,
                        Ui.dp(this, 44),
                        1f
                )
        );

        return row;
    }

    private void renderGrid(
            List<Product> products,
            LinearLayout holder
    ) {
        if (products == null || products.isEmpty()) {
            holder.addView(
                    messageView("Aucun produit à afficher.")
            );
            return;
        }

        for (int index = 0; index < products.size(); index += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);

            row.addView(
                    productCard(products.get(index)),
                    new LinearLayout.LayoutParams(
                            0,
                            Ui.dp(this, 302),
                            1f
                    )
            );

            if (index + 1 < products.size()) {
                LinearLayout.LayoutParams secondParams =
                        new LinearLayout.LayoutParams(
                                0,
                                Ui.dp(this, 302),
                                1f
                        );
                secondParams.leftMargin = Ui.dp(this, 10);

                row.addView(
                        productCard(products.get(index + 1)),
                        secondParams
                );
            } else {
                View empty = new View(this);
                LinearLayout.LayoutParams emptyParams =
                        new LinearLayout.LayoutParams(
                                0,
                                1,
                                1f
                        );
                emptyParams.leftMargin = Ui.dp(this, 10);
                row.addView(empty, emptyParams);
            }

            LinearLayout.LayoutParams rowParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            rowParams.bottomMargin = Ui.dp(this, 10);
            holder.addView(row, rowParams);
        }
    }

    private View productCard(Product product) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                Ui.dp(this, 9),
                Ui.dp(this, 9),
                Ui.dp(this, 9),
                Ui.dp(this, 10)
        );
        card.setBackground(
                Ui.roundedStroke(
                        Color.WHITE,
                        Ui.BORDER,
                        1,
                        18,
                        this
                )
        );
        card.setElevation(Ui.dp(this, 2));

        FrameLayout imageWrap = new FrameLayout(this);
        imageWrap.setBackground(
                Ui.rounded(Ui.SOFT, 14, this)
        );

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.ludorum_logo);
        image.setAlpha(.12f);
        image.setPadding(
                Ui.dp(this, 12),
                Ui.dp(this, 12),
                Ui.dp(this, 12),
                Ui.dp(this, 12)
        );
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);

        imageWrap.addView(
                image,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 126)
                )
        );

        ImageLoader.load(product.imageUrl, image);

        ImageView favoriteHeart = new ImageView(this);
        favoriteHeart.setScaleType(ImageView.ScaleType.CENTER);
        favoriteHeart.setPadding(
                Ui.dp(this, 8),
                Ui.dp(this, 8),
                Ui.dp(this, 8),
                Ui.dp(this, 8)
        );
        favoriteHeart.setBackground(
                Ui.rounded(Color.WHITE, 22, this)
        );
        favoriteHeart.setElevation(Ui.dp(this, 7));
        favoriteHeart.setClickable(true);
        favoriteHeart.setFocusable(true);

        registerFavoriteHeart(product.id, favoriteHeart);
        updateFavoriteHeart(product.id);

        favoriteHeart.setOnClickListener(view -> {
            boolean nowFavorite = favoriteStore.toggle(product);
            updateFavoriteHeart(product.id);

            Toast.makeText(
                    MainActivity.this,
                    nowFavorite
                            ? "Ajouté aux favoris ♥"
                            : "Retiré des favoris",
                    Toast.LENGTH_SHORT
            ).show();

            if (favoritesMode && !nowFavorite) {
                showFavorites();
            }
        });

        FrameLayout.LayoutParams favoriteParams =
                new FrameLayout.LayoutParams(
                        Ui.dp(this, 40),
                        Ui.dp(this, 40),
                        Gravity.TOP | Gravity.START
                );
        favoriteParams.setMargins(
                Ui.dp(this, 7),
                Ui.dp(this, 7),
                0,
                0
        );
        imageWrap.addView(favoriteHeart, favoriteParams);

        if (product.onSale) {
            TextView badge =
                    Ui.text(
                            this,
                            "PROMO",
                            10,
                            Color.WHITE,
                            true
                    );
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(
                    Ui.dp(this, 8),
                    Ui.dp(this, 5),
                    Ui.dp(this, 8),
                    Ui.dp(this, 5)
            );
            badge.setBackground(
                    Ui.rounded(Ui.RED, 12, this)
            );

            FrameLayout.LayoutParams badgeParams =
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP | Gravity.END
                    );
            badgeParams.setMargins(
                    0,
                    Ui.dp(this, 7),
                    Ui.dp(this, 7),
                    0
            );
            imageWrap.addView(badge, badgeParams);
        }

        card.addView(
                imageWrap,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 126)
                )
        );

        TextView name =
                Ui.text(
                        this,
                        product.name,
                        14,
                        Ui.TEXT,
                        true
                );
        name.setMaxLines(2);
        name.setMinLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.TOP | Gravity.START);

        LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 43)
                );
        nameParams.topMargin = Ui.dp(this, 9);
        card.addView(name, nameParams);

        LinearLayout priceRow = new LinearLayout(this);
        priceRow.setOrientation(LinearLayout.HORIZONTAL);
        priceRow.setGravity(Gravity.CENTER_VERTICAL);
        priceRow.setMinimumHeight(Ui.dp(this, 30));

        TextView currentPrice =
                Ui.text(
                        this,
                        formatPrice(
                                product.currentPrice,
                                product.currencyCode,
                                product.currencyMinorUnit
                        ),
                        16,
                        product.onSale ? Ui.RED : Ui.BLUE,
                        true
                );
        currentPrice.setSingleLine(true);
        currentPrice.setEllipsize(android.text.TextUtils.TruncateAt.END);
        priceRow.addView(
                currentPrice,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Ui.dp(this, 30)
                )
        );

        if (product.onSale &&
                product.regularPrice != null &&
                !product.regularPrice.isEmpty() &&
                !product.regularPrice.equals(product.currentPrice)) {

            TextView regularPrice =
                    Ui.text(
                            this,
                            formatPrice(
                                    product.regularPrice,
                                    product.currencyCode,
                                    product.currencyMinorUnit
                            ),
                            11,
                            Ui.MUTED,
                            false
                    );

            regularPrice.setPaintFlags(
                    regularPrice.getPaintFlags() |
                    Paint.STRIKE_THRU_TEXT_FLAG
            );
            regularPrice.setSingleLine(true);
            regularPrice.setEllipsize(android.text.TextUtils.TruncateAt.END);
            regularPrice.setMaxWidth(Ui.dp(this, 64));

            LinearLayout.LayoutParams regularParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            regularParams.leftMargin = Ui.dp(this, 6);
            priceRow.addView(regularPrice, regularParams);
        }

        card.addView(
                priceRow,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 30)
                )
        );

        TextView stock =
                Ui.text(
                        this,
                        product.inStock ? "● En stock" : "● Rupture de stock",
                        11,
                        product.inStock ? Ui.GREEN : Ui.RED,
                        true
                );

        LinearLayout.LayoutParams stockParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 25)
                );
        stockParams.topMargin = Ui.dp(this, 3);
        card.addView(stock, stockParams);

        TextView add =
                Ui.pill(
                        this,
                        product.inStock ? "Ajouter au panier" : "Voir",
                        Color.WHITE,
                        product.inStock ? Ui.BLUE : Ui.NAVY,
                        product.inStock ? Ui.BLUE : Ui.NAVY
                );
        add.setTextSize(13);

        add.setOnClickListener(
                view -> {
                    if (product.inStock && product.purchasable) {
                        addToCart(product, add);
                    } else {
                        showProductDetail(product);
                    }
                }
        );

        LinearLayout.LayoutParams addParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 42)
                );
        addParams.topMargin = Ui.dp(this, 2);
        card.addView(add, addParams);

        card.setOnClickListener(
                view -> showProductDetail(product)
        );

        return card;
    }

    private boolean hasSupportedRequestedScreen(
            Intent intent
    ) {
        if (intent == null) {
            return false;
        }

        String screen =
                intent.getStringExtra(
                        "screen"
                );

        if (screen == null ||
                screen.trim().isEmpty()) {
            return false;
        }

        if ("home".equals(screen) ||
                "accueil".equals(screen) ||
                "shop".equals(screen) ||
                "boutique".equals(screen) ||
                "continue_shopping".equals(screen) ||
                "favorites".equals(screen) ||
                "cart".equals(screen) ||
                "panier".equals(screen)) {
            return true;
        }

        if ("product".equals(screen)) {
            String slug =
                    intent.getStringExtra(
                            "product_slug"
                    );

            return slug != null &&
                    !slug.trim().isEmpty();
        }

        if ("category".equals(screen)) {
            String slug =
                    intent.getStringExtra(
                            "category_slug"
                    );

            return slug != null &&
                    !slug.trim().isEmpty();
        }

        if ("search".equals(screen)) {
            String query =
                    intent.getStringExtra(
                            "search_query"
                    );

            return query != null &&
                    !query.trim().isEmpty();
        }

        return false;
    }

    private void handleRequestedScreen(
            Intent intent
    ) {
        if (intent == null) return;

        String requestedScreen =
                intent.getStringExtra("screen");

        if ("home".equals(requestedScreen) ||
                "accueil".equals(requestedScreen)) {
            showHome();
            return;
        }

        if ("shop".equals(requestedScreen) ||
                "boutique".equals(requestedScreen) ||
                "continue_shopping".equals(requestedScreen)) {
            showCatalogue(
                    "Boutique",
                    "Tous les produits Ludorum.",
                    "",
                    1
            );
            return;
        }

        if ("favorites".equals(requestedScreen)) {
            showFavorites();
            return;
        }

        if ("cart".equals(requestedScreen) ||
                "panier".equals(requestedScreen)) {
            showCart();
            return;
        }

        if ("product".equals(requestedScreen)) {
            String slug =
                    intent.getStringExtra(
                            "product_slug"
                    );

            if (slug != null &&
                    !slug.trim().isEmpty()) {
                loadProductBySlug(slug);
            }
            return;
        }

        if ("category".equals(requestedScreen)) {
            String slug =
                    intent.getStringExtra(
                            "category_slug"
                    );

            if (slug != null &&
                    !slug.trim().isEmpty()) {
                loadCategoryBySlug(slug);
            }
            return;
        }

        if ("search".equals(requestedScreen)) {
            String query =
                    intent.getStringExtra(
                            "search_query"
                    );

            if (query != null &&
                    !query.trim().isEmpty()) {
                search.setText(query);
                searchProducts(query);
            }
        }
    }

    private void routeNativeUri(
            Uri uri
    ) {
        if (uri == null) {
            showHome();
            return;
        }

        String host =
                uri.getHost() == null
                        ? ""
                        : uri.getHost()
                                .toLowerCase(Locale.ROOT);

        if (!host.equals("ludorum.fr") &&
                !host.equals("www.ludorum.fr")) {
            showHome();
            return;
        }

        String path =
                uri.getPath() == null
                        ? "/"
                        : uri.getPath()
                                .toLowerCase(Locale.ROOT);

        if (path.equals("/") ||
                path.isEmpty()) {
            showHome();
            return;
        }

        if (path.equals("/favoris") ||
                path.equals("/favoris/") ||
                path.startsWith("/favoris/")) {
            showFavorites();
            return;
        }

        if (path.contains("/panier") ||
                path.contains("/cart")) {
            showCart();
            return;
        }

        if (path.contains("/mon-compte")) {
            openWeb(ACCOUNT, "Mon compte");
            return;
        }

        if (path.contains("/commande") ||
                path.contains("/checkout") ||
                path.contains("/order-pay") ||
                path.contains("/order-received")) {
            openWeb(uri.toString(), "Commande");
            return;
        }

        String searchQuery =
                uri.getQueryParameter("s");

        if (searchQuery != null &&
                !searchQuery.trim().isEmpty()) {
            search.setText(searchQuery);
            searchProducts(searchQuery);
            return;
        }

        String productSlug =
                slugAfter(
                        path,
                        "/produit/"
                );

        if (productSlug == null) {
            productSlug =
                    slugAfter(
                            path,
                            "/product/"
                    );
        }

        if (productSlug != null) {
            loadProductBySlug(productSlug);
            return;
        }

        String categorySlug =
                slugAfter(
                        path,
                        "/categorie-produit/"
                );

        if (categorySlug == null) {
            categorySlug =
                    slugAfter(
                            path,
                            "/product-category/"
                    );
        }

        if (categorySlug != null) {
            loadCategoryBySlug(categorySlug);
            return;
        }

        // Boutique, Shop, catalogue technique ou tout autre lien commercial :
        // destination unique = boutique native Ludorum.
        if (path.contains("/boutique") ||
                path.contains("/shop") ||
                path.contains("catalogue-woocommerce") ||
                path.contains("woocommerce-technique") ||
                path.contains("boutique-technique")) {
            showCatalogue(
                    "Boutique",
                    "Tous les produits Ludorum.",
                    "",
                    1
            );
            return;
        }

        showHome();
    }

    private String slugAfter(
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
            tail = tail.substring(1);
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

    private void loadProductBySlug(
            String slug
    ) {
        final int generation =
                ++screenGeneration;
        productMode = true;
        catalogueMode = false;
        favoritesMode = false;
        cartMode = false;
        setMainNavActive("home");
        favoriteHeartViews.clear();
        content.removeAllViews();
        scroll.scrollTo(0, 0);

        ProgressBar progress =
                new ProgressBar(this);

        LinearLayout.LayoutParams pp =
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 42),
                        Ui.dp(this, 42)
                );
        pp.gravity =
                Gravity.CENTER_HORIZONTAL;
        pp.topMargin =
                Ui.dp(this, 40);

        content.addView(
                progress,
                pp
        );

        ApiClient.getProductBySlug(
                slug,
                new ApiClient.Callback<Product>() {
                    @Override
                    public void onSuccess(
                            Product product
                    ) {
                        if (generation != screenGeneration) {
                            return;
                        }

                        showProductDetail(product);
                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                        if (generation != screenGeneration) {
                            return;
                        }

                        showCatalogue(
                                "Boutique",
                                "Tous les produits Ludorum.",
                                "",
                                1
                        );
                    }
                }
        );
    }

    private void loadCategoryBySlug(
            String slug
    ) {
        final int categoryRequestGeneration =
                ++screenGeneration;

        ApiClient.getCategoryBySlug(
                slug,
                new ApiClient.Callback<ProductCategory>() {
                    @Override
                    public void onSuccess(
                            ProductCategory category
                    ) {
                        if (categoryRequestGeneration !=
                                screenGeneration) {
                            return;
                        }

                        showCatalogue(
                                category.name,
                                "Parcourez cette catégorie du catalogue Ludorum.",
                                "&category=" +
                                category.id,
                                1
                        );
                    }

                    @Override
                    public void onError(
                            Exception error
                    ) {
                        if (categoryRequestGeneration !=
                                screenGeneration) {
                            return;
                        }

                        showCatalogue(
                                "Boutique",
                                "Tous les produits Ludorum.",
                                "",
                                1
                        );
                    }
                }
        );
    }

    private void showProductDetail(
            Product product
    ) {
        if (product == null) return;

        ++screenGeneration;

        productMode = true;
        catalogueMode = false;
        favoritesMode = false;
        cartMode = false;
        setMainNavActive("home");
        favoriteHeartViews.clear();
        content.removeAllViews();
        scroll.scrollTo(0, 0);

        TextView eyebrow =
                Ui.text(
                        this,
                        "FICHE PRODUIT LUDORUM",
                        10,
                        Ui.BLUE,
                        true
                );
        content.addView(eyebrow);

        FrameLayout imageCard =
                new FrameLayout(this);
        imageCard.setBackground(
                Ui.roundedStroke(
                        Ui.SOFT,
                        Ui.BORDER,
                        1,
                        22,
                        this
                )
        );

        ImageView image =
                new ImageView(this);
        image.setImageResource(
                R.drawable.ludorum_logo
        );
        image.setAlpha(.14f);
        image.setPadding(
                Ui.dp(this, 22),
                Ui.dp(this, 22),
                Ui.dp(this, 22),
                Ui.dp(this, 22)
        );
        image.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        imageCard.addView(
                image,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 260)
                )
        );

        ImageLoader.load(
                product.imageUrl,
                image
        );

        ImageView heart =
                new ImageView(this);
        heart.setScaleType(
                ImageView.ScaleType.CENTER
        );
        heart.setPadding(
                Ui.dp(this, 10),
                Ui.dp(this, 10),
                Ui.dp(this, 10),
                Ui.dp(this, 10)
        );
        heart.setBackground(
                Ui.rounded(
                        Color.WHITE,
                        24,
                        this
                )
        );
        heart.setElevation(
                Ui.dp(this, 8)
        );

        registerFavoriteHeart(
                product.id,
                heart
        );
        updateFavoriteHeart(
                product.id
        );

        heart.setOnClickListener(
                view -> {
                    boolean nowFavorite =
                            favoriteStore.toggle(
                                    product
                            );

                    updateFavoriteHeart(
                            product.id
                    );

                    Toast.makeText(
                            MainActivity.this,
                            nowFavorite
                                    ? "Ajouté aux favoris ♥"
                                    : "Retiré des favoris",
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );

        FrameLayout.LayoutParams hp =
                new FrameLayout.LayoutParams(
                        Ui.dp(this, 46),
                        Ui.dp(this, 46),
                        Gravity.TOP |
                        Gravity.START
                );
        hp.setMargins(
                Ui.dp(this, 12),
                Ui.dp(this, 12),
                0,
                0
        );
        imageCard.addView(
                heart,
                hp
        );

        if (product.onSale) {
            TextView promo =
                    Ui.text(
                            this,
                            "PROMO",
                            11,
                            Color.WHITE,
                            true
                    );
            promo.setGravity(
                    Gravity.CENTER
            );
            promo.setPadding(
                    Ui.dp(this, 11),
                    Ui.dp(this, 6),
                    Ui.dp(this, 11),
                    Ui.dp(this, 6)
            );
            promo.setBackground(
                    Ui.rounded(
                            Ui.RED,
                            14,
                            this
                    )
            );

            FrameLayout.LayoutParams pr =
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP |
                            Gravity.END
                    );
            pr.setMargins(
                    0,
                    Ui.dp(this, 12),
                    Ui.dp(this, 12),
                    0
            );
            imageCard.addView(
                    promo,
                    pr
            );
        }

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 260)
                );
        imageParams.topMargin =
                Ui.dp(this, 10);
        content.addView(
                imageCard,
                imageParams
        );

        TextView title =
                Ui.text(
                        this,
                        product.name,
                        25,
                        Ui.NAVY,
                        true
                );
        title.setLineSpacing(
                Ui.dp(this, 2),
                1f
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        titleParams.topMargin =
                Ui.dp(this, 18);
        content.addView(
                title,
                titleParams
        );

        LinearLayout priceRow =
                new LinearLayout(this);
        priceRow.setOrientation(
                LinearLayout.HORIZONTAL
        );
        priceRow.setGravity(
                Gravity.CENTER_VERTICAL
        );

        TextView price =
                Ui.text(
                        this,
                        formatPrice(
                                product.currentPrice,
                                product.currencyCode,
                                product.currencyMinorUnit
                        ),
                        22,
                        product.onSale
                                ? Ui.RED
                                : Ui.BLUE,
                        true
                );
        priceRow.addView(price);

        if (product.onSale &&
                product.regularPrice != null &&
                !product.regularPrice.isEmpty() &&
                !product.regularPrice.equals(
                        product.currentPrice
                )) {
            TextView regular =
                    Ui.text(
                            this,
                            formatPrice(
                                    product.regularPrice,
                                    product.currencyCode,
                                    product.currencyMinorUnit
                            ),
                            14,
                            Ui.MUTED,
                            false
                    );

            regular.setPaintFlags(
                    regular.getPaintFlags() |
                    Paint.STRIKE_THRU_TEXT_FLAG
            );

            LinearLayout.LayoutParams rp =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            rp.leftMargin =
                    Ui.dp(this, 9);

            priceRow.addView(
                    regular,
                    rp
            );
        }

        LinearLayout.LayoutParams priceParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        priceParams.topMargin =
                Ui.dp(this, 9);
        content.addView(
                priceRow,
                priceParams
        );

        TextView stock =
                Ui.text(
                        this,
                        product.inStock
                                ? "● En stock"
                                : "● Rupture de stock",
                        13,
                        product.inStock
                                ? Ui.GREEN
                                : Ui.RED,
                        true
                );

        LinearLayout.LayoutParams stockParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        stockParams.topMargin =
                Ui.dp(this, 8);
        content.addView(
                stock,
                stockParams
        );

        if (product.shortDescription != null &&
                !product.shortDescription.trim().isEmpty()) {
            TextView description =
                    Ui.text(
                            this,
                            "",
                            14,
                            Ui.MUTED,
                            false
                    );

            description.setText(
                    Html.fromHtml(
                            product.shortDescription,
                            Html.FROM_HTML_MODE_LEGACY
                    )
            );
            description.setLineSpacing(
                    Ui.dp(this, 3),
                    1f
            );

            LinearLayout.LayoutParams descParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            descParams.topMargin =
                    Ui.dp(this, 15);

            content.addView(
                    description,
                    descParams
            );
        }

        TextView action =
                Ui.pill(
                        this,
                        "simple".equalsIgnoreCase(product.type) &&
                        product.inStock &&
                        product.purchasable
                                ? "Ajouter au panier"
                                : "Choisir les options",
                        Color.WHITE,
                        Ui.BLUE,
                        Ui.BLUE
                );
        action.setTextSize(15);

        action.setOnClickListener(
                view -> {
                    if ("simple".equalsIgnoreCase(product.type) &&
                            product.inStock &&
                            product.purchasable) {
                        addToCart(
                                product,
                                action
                        );
                    } else {
                        openProductOptions(
                                product
                        );
                    }
                }
        );

        LinearLayout.LayoutParams actionParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 50)
                );
        actionParams.topMargin =
                Ui.dp(this, 20);
        content.addView(
                action,
                actionParams
        );

        TextView back =
                Ui.pill(
                        this,
                        "Retour à la boutique",
                        Ui.BLUE,
                        Color.WHITE,
                        Ui.BLUE
                );

        back.setOnClickListener(
                view -> showCatalogue(
                        "Boutique",
                        "Tous les produits Ludorum.",
                        "",
                        1
                )
        );

        LinearLayout.LayoutParams backParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(this, 46)
                );
        backParams.topMargin =
                Ui.dp(this, 10);
        content.addView(
                back,
                backParams
        );
    }

    private void openProductOptions(
            Product product
    ) {
        if (product == null ||
                product.permalink == null ||
                product.permalink.trim().isEmpty()) {
            return;
        }

        Intent intent =
                new Intent(
                        this,
                        WebActivity.class
                );

        intent.putExtra(
                WebActivity.EXTRA_URL,
                product.permalink
        );

        intent.putExtra(
                WebActivity.EXTRA_TITLE,
                product.name
        );

        intent.putExtra(
                WebActivity.EXTRA_ALLOW_PRODUCT_PAGE,
                true
        );

        startActivity(intent);
    }

    private void registerFavoriteHeart(
            int productId,
            ImageView heart
    ) {
        List<ImageView> views = favoriteHeartViews.get(productId);

        if (views == null) {
            views = new ArrayList<>();
            favoriteHeartViews.put(productId, views);
        }

        views.add(heart);
    }

    private void updateFavoriteHeart(int productId) {
        boolean favorite =
                favoriteStore != null &&
                favoriteStore.contains(productId);

        List<ImageView> views =
                favoriteHeartViews.get(productId);

        if (views == null) return;

        for (ImageView heart : views) {
            if (heart == null) continue;
            heart.setImageResource(
                    favorite
                            ? R.drawable.ic_heart_filled
                            : R.drawable.ic_heart_outline
            );
            heart.setContentDescription(
                    favorite
                            ? "Retirer des favoris"
                            : "Ajouter aux favoris"
            );
        }
    }

    private void setMainNavActive(String active) {
        Ui.setNavActive(navHome, "home".equals(active));
        Ui.setNavActive(navAccount, "account".equals(active));
        Ui.setNavActive(navFavorites, "favorites".equals(active));
        Ui.setNavActive(navCart, "cart".equals(active));
    }

    private void showCart() {
        final int generation =
                ++screenGeneration;

        catalogueMode = false;
        favoritesMode = false;
        productMode = false;
        cartMode = true;
        cartMutationInFlight = false;

        setMainNavActive(
                "cart"
        );

        favoriteHeartViews.clear();
        search.setText("");
        content.removeAllViews();
        scroll.scrollTo(0, 0);

        TextView kicker =
                Ui.text(
                        this,
                        "PANIER LUDORUM",
                        10,
                        Ui.BLUE,
                        true
                );

        content.addView(
                kicker
        );

        TextView heading =
                Ui.text(
                        this,
                        "Votre panier",
                        28,
                        Ui.NAVY,
                        true
                );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        headingParams.topMargin =
                Ui.dp(
                        this,
                        5
                );

        content.addView(
                heading,
                headingParams
        );

        TextView subtitle =
                Ui.text(
                        this,
                        "Quantités et prix synchronisés directement avec WooCommerce.",
                        14,
                        Ui.MUTED,
                        false
                );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.topMargin =
                Ui.dp(
                        this,
                        7
                );

        subtitleParams.bottomMargin =
                Ui.dp(
                        this,
                        18
                );

        content.addView(
                subtitle,
                subtitleParams
        );

        CartService.CartSnapshot cached =
                currentCartSnapshot;

        if (cached == null) {
            cached =
                    CartService.getCachedSnapshot();
        }

        if (cached != null) {
            renderCart(
                    cached,
                    null
            );
        } else {
            ProgressBar progress =
                    new ProgressBar(
                            this
                    );

            LinearLayout.LayoutParams progressParams =
                    new LinearLayout.LayoutParams(
                            Ui.dp(
                                    this,
                                    42
                            ),
                            Ui.dp(
                                    this,
                                    42
                            )
                    );

            progressParams.gravity =
                    Gravity.CENTER_HORIZONTAL;

            progressParams.topMargin =
                    Ui.dp(
                            this,
                            22
                    );

            content.addView(
                    progress,
                    progressParams
            );
        }

        CartService.getCart(
                new CartService.Callback() {
                    @Override
                    public void onSuccess(
                            CartService.CartSnapshot snapshot
                    ) {
                        if (generation !=
                                screenGeneration ||
                                !cartMode) {
                            return;
                        }

                        currentCartSnapshot =
                                snapshot;

                        renderCart(
                                snapshot,
                                null
                        );
                    }

                    @Override
                    public void onError(
                            String message
                    ) {
                        if (generation !=
                                screenGeneration ||
                                !cartMode) {
                            return;
                        }

                        // Si on avait déjà un snapshot confirmé par l'ajout,
                        // on le conserve à l'écran plutôt que d'afficher
                        // à tort "panier vide".
                        if (currentCartSnapshot != null) {
                            renderCart(
                                    currentCartSnapshot,
                                    "Synchronisation momentanément indisponible."
                            );
                            return;
                        }

                        content.removeAllViews();

                        content.addView(
                                retryView(
                                        "Impossible de charger le panier.",
                                        view ->
                                                showCart()
                                )
                        );
                    }
                }
        );
    }

    private void renderCart(
            CartService.CartSnapshot snapshot,
            String inlineError
    ) {
        if (!cartMode ||
                snapshot == null) {
            return;
        }

        currentCartSnapshot =
                snapshot;

        cartMutationInFlight =
                false;

        if (cartTicker != null) {
            cartTicker.applySnapshot(
                    snapshot
            );
        }

        content.removeAllViews();
        scroll.scrollTo(0, 0);

        TextView kicker =
                Ui.text(
                        this,
                        "PANIER LUDORUM",
                        10,
                        Ui.BLUE,
                        true
                );

        content.addView(
                kicker
        );

        TextView heading =
                Ui.text(
                        this,
                        snapshot.items.isEmpty()
                                ? "Votre panier"
                                : "Votre panier • " +
                                snapshot.itemsCount +
                                (snapshot.itemsCount > 1
                                        ? " articles"
                                        : " article"),
                        28,
                        Ui.NAVY,
                        true
                );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        headingParams.topMargin =
                Ui.dp(
                        this,
                        5
                );

        content.addView(
                heading,
                headingParams
        );

        if (inlineError != null &&
                !inlineError.trim().isEmpty()) {
            TextView error =
                    Ui.text(
                            this,
                            inlineError,
                            13,
                            Ui.RED,
                            true
                    );

            error.setPadding(
                    Ui.dp(
                            this,
                            13
                    ),
                    Ui.dp(
                            this,
                            11
                    ),
                    Ui.dp(
                            this,
                            13
                    ),
                    Ui.dp(
                            this,
                            11
                    )
            );

            error.setBackground(
                    Ui.roundedStroke(
                            Color.rgb(
                                    255,
                                    247,
                                    247
                            ),
                            Color.rgb(
                                    244,
                                    202,
                                    202
                            ),
                            1,
                            15,
                            this
                    )
            );

            LinearLayout.LayoutParams errorParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            errorParams.topMargin =
                    Ui.dp(
                            this,
                            12
                    );

            content.addView(
                    error,
                    errorParams
            );
        }

        if (snapshot.items.isEmpty()) {
            renderEmptyCart();
            return;
        }

        TextView subtitle =
                Ui.text(
                        this,
                        "Le panier ci-dessous est l’état réel renvoyé par WooCommerce.",
                        13,
                        Ui.MUTED,
                        false
                );

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtitleParams.topMargin =
                Ui.dp(
                        this,
                        8
                );

        subtitleParams.bottomMargin =
                Ui.dp(
                        this,
                        16
                );

        content.addView(
                subtitle,
                subtitleParams
        );

        for (CartService.CartItem item :
                snapshot.items) {
            content.addView(
                    cartItemCard(
                            item,
                            snapshot
                    )
            );
        }

        content.addView(
                cartSummary(
                        snapshot
                )
        );
    }

    private void renderEmptyCart() {
        LinearLayout empty =
                new LinearLayout(
                        this
                );

        empty.setOrientation(
                LinearLayout.VERTICAL
        );

        empty.setGravity(
                Gravity.CENTER
        );

        empty.setPadding(
                Ui.dp(
                        this,
                        22
                ),
                Ui.dp(
                        this,
                        34
                ),
                Ui.dp(
                        this,
                        22
                ),
                Ui.dp(
                        this,
                        34
                )
        );

        empty.setBackground(
                Ui.roundedStroke(
                        Ui.SOFT,
                        Ui.BORDER,
                        1,
                        20,
                        this
                )
        );

        ImageView icon =
                new ImageView(
                        this
                );

        icon.setImageResource(
                R.drawable.ic_cart
        );

        icon.setColorFilter(
                Ui.BLUE
        );

        empty.addView(
                icon,
                new LinearLayout.LayoutParams(
                        Ui.dp(
                                this,
                                54
                        ),
                        Ui.dp(
                                this,
                                54
                        )
                )
        );

        TextView title =
                Ui.text(
                        this,
                        "Votre panier est vide",
                        20,
                        Ui.NAVY,
                        true
                );

        title.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        titleParams.topMargin =
                Ui.dp(
                        this,
                        13
                );

        empty.addView(
                title,
                titleParams
        );

        TextView text =
                Ui.text(
                        this,
                        "Ajoutez un produit depuis la boutique Ludorum.",
                        14,
                        Ui.MUTED,
                        false
                );

        text.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        textParams.topMargin =
                Ui.dp(
                        this,
                        8
                );

        empty.addView(
                text,
                textParams
        );

        TextView back =
                Ui.pill(
                        this,
                        "Retour à la boutique",
                        Color.WHITE,
                        Ui.BLUE,
                        Ui.BLUE
                );

        back.setOnClickListener(
                view ->
                        showCatalogue(
                                "Boutique",
                                "Tous les produits Ludorum.",
                                "",
                                1
                        )
        );

        LinearLayout.LayoutParams backParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(
                                this,
                                48
                        )
                );

        backParams.topMargin =
                Ui.dp(
                        this,
                        20
                );

        empty.addView(
                back,
                backParams
        );

        LinearLayout.LayoutParams emptyParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        emptyParams.topMargin =
                Ui.dp(
                        this,
                        18
                );

        content.addView(
                empty,
                emptyParams
        );
    }

    private View cartItemCard(
            CartService.CartItem item,
            CartService.CartSnapshot snapshot
    ) {
        LinearLayout card =
                new LinearLayout(
                        this
                );

        card.setOrientation(
                LinearLayout.HORIZONTAL
        );

        card.setGravity(
                Gravity.TOP
        );

        card.setPadding(
                Ui.dp(
                        this,
                        12
                ),
                Ui.dp(
                        this,
                        12
                ),
                Ui.dp(
                        this,
                        10
                ),
                Ui.dp(
                        this,
                        12
                )
        );

        card.setBackground(
                Ui.roundedStroke(
                        Color.WHITE,
                        Ui.BORDER,
                        1,
                        19,
                        this
                )
        );

        ImageView image =
                new ImageView(
                        this
                );

        image.setImageResource(
                R.drawable.ludorum_logo
        );

        image.setAlpha(
                .14f
        );

        image.setScaleType(
                ImageView.ScaleType.FIT_CENTER
        );

        image.setPadding(
                Ui.dp(
                        this,
                        6
                ),
                Ui.dp(
                        this,
                        6
                ),
                Ui.dp(
                        this,
                        6
                ),
                Ui.dp(
                        this,
                        6
                )
        );

        ImageLoader.load(
                item.imageUrl,
                image
        );

        card.addView(
                image,
                new LinearLayout.LayoutParams(
                        Ui.dp(
                                this,
                                84
                        ),
                        Ui.dp(
                                this,
                                84
                        )
                )
        );

        LinearLayout details =
                new LinearLayout(
                        this
                );

        details.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams detailsParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        detailsParams.leftMargin =
                Ui.dp(
                        this,
                        12
                );

        card.addView(
                details,
                detailsParams
        );

        TextView name =
                Ui.text(
                        this,
                        item.name,
                        16,
                        Ui.NAVY,
                        true
                );

        name.setMaxLines(
                2
        );

        details.addView(
                name
        );

        TextView subtotal =
                Ui.text(
                        this,
                        "Sous-total TTC : " +
                        formatMinor(
                                item.lineTotalTtc,
                                snapshot.currencyCode,
                                snapshot.currencyMinorUnit
                        ),
                        14,
                        Ui.BLUE,
                        true
                );

        LinearLayout.LayoutParams subtotalParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        subtotalParams.topMargin =
                Ui.dp(
                        this,
                        7
                );

        details.addView(
                subtotal,
                subtotalParams
        );

        LinearLayout controls =
                new LinearLayout(
                        this
                );

        controls.setOrientation(
                LinearLayout.HORIZONTAL
        );

        controls.setGravity(
                Gravity.CENTER_VERTICAL
        );

        LinearLayout.LayoutParams controlsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        controlsParams.topMargin =
                Ui.dp(
                        this,
                        10
                );

        details.addView(
                controls,
                controlsParams
        );

        TextView minus =
                cartControl(
                        "−",
                        Ui.BLUE
                );

        TextView quantity =
                cartControl(
                        String.valueOf(
                                item.quantity
                        ),
                        Ui.NAVY
                );

        quantity.setClickable(
                false
        );

        quantity.setBackground(
                Ui.roundedStroke(
                        Ui.SOFT,
                        Ui.BORDER,
                        1,
                        10,
                        this
                )
        );

        TextView plus =
                cartControl(
                        "+",
                        Ui.BLUE
                );

        boolean canEdit =
                item.editable;

        boolean canDecrease =
                canEdit &&
                item.quantity > 0;

        boolean canIncrease =
                canEdit &&
                (item.maximum <= 0 ||
                 item.quantity <
                 item.maximum);

        minus.setEnabled(
                canDecrease
        );

        plus.setEnabled(
                canIncrease
        );

        minus.setAlpha(
                canDecrease
                        ? 1f
                        : .4f
        );

        plus.setAlpha(
                canIncrease
                        ? 1f
                        : .4f
        );

        minus.setOnClickListener(
                view ->
                        mutateCartItem(
                                item,
                                item.quantity - 1
                        )
        );

        plus.setOnClickListener(
                view ->
                        mutateCartItem(
                                item,
                                item.quantity + 1
                        )
        );

        controls.addView(
                minus,
                new LinearLayout.LayoutParams(
                        Ui.dp(
                                this,
                                38
                        ),
                        Ui.dp(
                                this,
                                38
                        )
                )
        );

        LinearLayout.LayoutParams quantityParams =
                new LinearLayout.LayoutParams(
                        Ui.dp(
                                this,
                                43
                        ),
                        Ui.dp(
                                this,
                                38
                        )
                );

        quantityParams.leftMargin =
                Ui.dp(
                        this,
                        5
                );

        quantityParams.rightMargin =
                Ui.dp(
                        this,
                        5
                );

        controls.addView(
                quantity,
                quantityParams
        );

        controls.addView(
                plus,
                new LinearLayout.LayoutParams(
                        Ui.dp(
                                this,
                                38
                        ),
                        Ui.dp(
                                this,
                                38
                        )
                )
        );

        TextView removeOne =
                cartControl(
                        "×",
                        Ui.RED
                );

        removeOne.setTextSize(
                25
        );

        removeOne.setContentDescription(
                "Retirer un exemplaire"
        );

        removeOne.setBackground(
                Ui.roundedStroke(
                        Color.rgb(
                                255,
                                244,
                                244
                        ),
                        Color.rgb(
                                244,
                                202,
                                202
                        ),
                        1,
                        13,
                        this
                )
        );

        removeOne.setOnClickListener(
                view ->
                        mutateCartItem(
                                item,
                                item.quantity - 1
                        )
        );

        LinearLayout.LayoutParams removeParams =
                new LinearLayout.LayoutParams(
                        Ui.dp(
                                this,
                                44
                        ),
                        Ui.dp(
                                this,
                                44
                        )
                );

        removeParams.leftMargin =
                Ui.dp(
                        this,
                        8
                );

        card.addView(
                removeOne,
                removeParams
        );

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin =
                Ui.dp(
                        this,
                        12
                );

        card.setLayoutParams(
                cardParams
        );

        return card;
    }

    private TextView cartControl(
            String label,
            int color
    ) {
        TextView view =
                Ui.text(
                        this,
                        label,
                        19,
                        color,
                        true
                );

        view.setGravity(
                Gravity.CENTER
        );

        view.setBackground(
                Ui.roundedStroke(
                        Color.WHITE,
                        Ui.BORDER,
                        1,
                        10,
                        this
                )
        );

        view.setClickable(
                true
        );

        view.setFocusable(
                true
        );

        return view;
    }

    private void mutateCartItem(
            CartService.CartItem item,
            int targetQuantity
    ) {
        if (!cartMode ||
                item == null ||
                cartMutationInFlight) {
            return;
        }

        cartMutationInFlight =
                true;

        CartService.Callback callback =
                new CartService.Callback() {
                    @Override
                    public void onSuccess(
                            CartService.CartSnapshot snapshot
                    ) {
                        if (!cartMode) {
                            cartMutationInFlight =
                                    false;
                            return;
                        }

                        currentCartSnapshot =
                                snapshot;

                        renderCart(
                                snapshot,
                                null
                        );
                    }

                    @Override
                    public void onError(
                            String message
                    ) {
                        cartMutationInFlight =
                                false;

                        if (!cartMode) {
                            return;
                        }

                        renderCart(
                                currentCartSnapshot,
                                message == null ||
                                message.trim().isEmpty()
                                        ? "Modification du panier impossible."
                                        : message
                        );
                    }
                };

        if (targetQuantity <= 0) {
            CartService.removeItem(
                    item.key,
                    callback
            );

        } else {
            CartService.setQuantity(
                    item.key,
                    targetQuantity,
                    callback
            );
        }
    }

    private View cartSummary(
            CartService.CartSnapshot snapshot
    ) {
        LinearLayout summary =
                new LinearLayout(
                        this
                );

        summary.setOrientation(
                LinearLayout.VERTICAL
        );

        summary.setPadding(
                Ui.dp(
                        this,
                        17
                ),
                Ui.dp(
                        this,
                        17
                ),
                Ui.dp(
                        this,
                        17
                ),
                Ui.dp(
                        this,
                        17
                )
        );

        summary.setBackground(
                Ui.gradient(
                        Ui.IVORY,
                        Color.WHITE,
                        20,
                        this
                )
        );

        TextView title =
                Ui.text(
                        this,
                        "Résumé de la commande",
                        21,
                        Ui.NAVY,
                        true
                );

        summary.addView(
                title
        );

        addCartSummaryRow(
                summary,
                "Sous-total produits TTC",
                formatMinor(
                        snapshot.subtotalProductsTtc,
                        snapshot.currencyCode,
                        snapshot.currencyMinorUnit
                ),
                false
        );

        if (snapshot.discountTtc > 0L) {
            addCartSummaryRow(
                    summary,
                    "Remises TTC",
                    "− " +
                    formatMinor(
                            snapshot.discountTtc,
                            snapshot.currencyCode,
                            snapshot.currencyMinorUnit
                    ),
                    false
            );
        }

        if (snapshot.feesTtc > 0L) {
            addCartSummaryRow(
                    summary,
                    "Frais TTC",
                    formatMinor(
                            snapshot.feesTtc,
                            snapshot.currencyCode,
                            snapshot.currencyMinorUnit
                    ),
                    false
            );
        }

        addCartSummaryRow(
                summary,
                "Livraison",
                "Calculée à l’étape suivante",
                false
        );

        addCartSummaryRow(
                summary,
                "Total TTC hors livraison",
                formatMinor(
                        snapshot.totalTtcExcludingShipping,
                        snapshot.currencyCode,
                        snapshot.currencyMinorUnit
                ),
                true
        );

        TextView checkout =
                Ui.pill(
                        this,
                        "Passer à la commande",
                        Color.WHITE,
                        Ui.BLUE,
                        Ui.BLUE
                );

        checkout.setTextSize(
                16
        );

        checkout.setOnClickListener(
                view ->
                        openWeb(
                                CHECKOUT,
                                "Commande"
                        )
        );

        LinearLayout.LayoutParams checkoutParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Ui.dp(
                                this,
                                51
                        )
                );

        checkoutParams.topMargin =
                Ui.dp(
                        this,
                        16
                );

        summary.addView(
                checkout,
                checkoutParams
        );

        LinearLayout.LayoutParams summaryParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        summaryParams.topMargin =
                Ui.dp(
                        this,
                        7
                );

        summaryParams.bottomMargin =
                Ui.dp(
                        this,
                        18
                );

        summary.setLayoutParams(
                summaryParams
        );

        return summary;
    }

    private void addCartSummaryRow(
            LinearLayout parent,
            String label,
            String value,
            boolean total
    ) {
        LinearLayout row =
                new LinearLayout(
                        this
                );

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        row.setPadding(
                0,
                Ui.dp(
                        this,
                        total
                                ? 13
                                : 10
                ),
                0,
                Ui.dp(
                        this,
                        total
                                ? 5
                                : 10
                )
        );

        TextView labelView =
                Ui.text(
                        this,
                        label,
                        total
                                ? 14
                                : 13,
                        total
                                ? Ui.NAVY
                                : Ui.MUTED,
                        total
                );

        TextView valueView =
                Ui.text(
                        this,
                        value,
                        total
                                ? 20
                                : 14,
                        total
                                ? Ui.RED
                                : Ui.NAVY,
                        true
                );

        valueView.setGravity(
                Gravity.END
        );

        row.addView(
                labelView,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        row.addView(
                valueView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        parent.addView(
                row
        );
    }

    private String formatMinor(
            long minor,
            String currencyCode,
            int minorUnits
    ) {
        return formatPrice(
                String.valueOf(
                        Math.max(
                                0L,
                                minor
                        )
                ),
                currencyCode,
                minorUnits
        );
    }

    private void showFavorites() {
        ++screenGeneration;
        catalogueMode = false;
        favoritesMode = true;
        productMode = false;
        cartMode = false;
        setMainNavActive("favorites");
        favoriteHeartViews.clear();
        search.setText("");
        content.removeAllViews();
        scroll.scrollTo(0, 0);

        TextView kicker =
                Ui.text(
                        this,
                        "VOTRE SÉLECTION",
                        10,
                        Ui.RED,
                        true
                );
        content.addView(kicker);

        TextView heading =
                Ui.text(
                        this,
                        "Mes favoris",
                        28,
                        Ui.NAVY,
                        true
                );

        LinearLayout.LayoutParams headingParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        headingParams.topMargin = Ui.dp(this, 5);
        content.addView(heading, headingParams);

        TextView subtitle =
                Ui.text(
                        this,
                        "Gardez ici les jeux, cartes et accessoires qui vous font envie.",
                        14,
                        Ui.MUTED,
                        false
                );
        subtitle.setLineSpacing(Ui.dp(this, 3), 1f);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.topMargin = Ui.dp(this, 7);
        subtitleParams.bottomMargin = Ui.dp(this, 18);
        content.addView(subtitle, subtitleParams);

        List<Product> favorites =
                favoriteStore == null
                        ? new ArrayList<>()
                        : favoriteStore.getAll();

        if (favorites.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(
                    Ui.dp(this, 22),
                    Ui.dp(this, 34),
                    Ui.dp(this, 22),
                    Ui.dp(this, 34)
            );
            empty.setBackground(
                    Ui.roundedStroke(
                            Color.rgb(255, 249, 249),
                            Color.rgb(246, 216, 216),
                            1,
                            20,
                            this
                    )
            );

            ImageView heart = new ImageView(this);
            heart.setImageResource(R.drawable.ic_heart_outline);
            empty.addView(
                    heart,
                    new LinearLayout.LayoutParams(
                            Ui.dp(this, 52),
                            Ui.dp(this, 52)
                    )
            );

            TextView emptyTitle =
                    Ui.text(
                            this,
                            "Votre liste est vide",
                            19,
                            Ui.NAVY,
                            true
                    );
            emptyTitle.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams emptyTitleParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            emptyTitleParams.topMargin = Ui.dp(this, 13);
            empty.addView(emptyTitle, emptyTitleParams);

            TextView emptyText =
                    Ui.text(
                            this,
                            "Touchez le cœur d’un produit pour le retrouver ici.",
                            13,
                            Ui.MUTED,
                            false
                    );
            emptyText.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams emptyTextParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            emptyTextParams.topMargin = Ui.dp(this, 7);
            empty.addView(emptyText, emptyTextParams);

            TextView browse =
                    Ui.pill(
                            this,
                            "Découvrir les produits",
                            Color.WHITE,
                            Ui.BLUE,
                            Ui.BLUE
                    );
            browse.setOnClickListener(view -> showHome());

            LinearLayout.LayoutParams browseParams =
                    new LinearLayout.LayoutParams(
                            Ui.dp(this, 210),
                            Ui.dp(this, 46)
                    );
            browseParams.gravity = Gravity.CENTER_HORIZONTAL;
            browseParams.topMargin = Ui.dp(this, 20);
            empty.addView(browse, browseParams);

            content.addView(
                    empty,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
            return;
        }

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        content.addView(grid);

        renderGrid(favorites, grid);

        TextView count =
                Ui.text(
                        this,
                        favorites.size() == 1
                                ? "1 produit enregistré"
                                : favorites.size() + " produits enregistrés",
                        12,
                        Ui.MUTED,
                        false
                );
        count.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams countParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        countParams.topMargin = Ui.dp(this, 8);
        content.addView(count, countParams);
    }

    private String formatPrice(
            String raw,
            String currencyCode,
            int minorUnits
    ) {
        if (raw == null || raw.isEmpty()) return "—";

        try {
            BigDecimal value =
                    new BigDecimal(raw)
                            .movePointLeft(Math.max(0, minorUnits));

            NumberFormat format =
                    NumberFormat.getCurrencyInstance(Locale.FRANCE);

            try {
                format.setCurrency(
                        Currency.getInstance(currencyCode)
                );
            } catch (Exception ignored) {}

            return format.format(value);
        } catch (Exception ignored) {
            return raw + " €";
        }
    }

    private void addToCart(
            Product product,
            TextView button
    ) {
        if (product == null ||
                product.id <= 0) {
            if (button != null) {
                button.setText(
                        "Indisponible"
                );
                button.setEnabled(
                        false
                );
            }
            return;
        }

        if (!"simple".equalsIgnoreCase(
                product.type
        )) {
            showProductDetail(
                    product
            );
            return;
        }

        if (!product.inStock ||
                !product.purchasable) {
            showProductDetail(
                    product
            );
            return;
        }

        if (button != null) {
            button.setEnabled(
                    false
            );

            button.setAlpha(
                    .72f
            );

            button.setText(
                    "Ajout…"
            );
        }

        try {
            CartService.addOne(
                    product.id,
                    new CartService.Callback() {
                        @Override
                        public void onSuccess(
                                CartService.CartSnapshot snapshot
                        ) {
                            if (button != null) {
                                button.setText(
                                        "✓ Ajouté"
                                );

                                button.setAlpha(
                                        1f
                                );

                                button.postDelayed(
                                        () -> {
                                            if (button.isAttachedToWindow()) {
                                                button.setText(
                                                        "Ajouter au panier"
                                                );

                                                button.setEnabled(
                                                        true
                                                );
                                            }
                                        },
                                        800
                                );
                            }

                            currentCartSnapshot =
                                    snapshot;

                            if (cartTicker != null) {
                                cartTicker.applySnapshot(
                                        snapshot
                                );
                            }
                        }

                        @Override
                        public void onError(
                                String message
                        ) {
                            if (button != null) {
                                button.setText(
                                        "Réessayer"
                                );

                                button.setEnabled(
                                        true
                                );

                                button.setAlpha(
                                        1f
                                );

                                button.postDelayed(
                                        () -> {
                                            if (button.isAttachedToWindow()) {
                                                button.setText(
                                                        "Ajouter au panier"
                                                );
                                            }
                                        },
                                        1400
                                );
                            }
                        }
                    }
            );

        } catch (Throwable error) {
            if (button != null) {
                button.setText(
                        "Réessayer"
                );

                button.setEnabled(
                        true
                );

                button.setAlpha(
                        1f
                );
            }
        }
    }

    private TextView messageView(String message) {
        TextView view =
                Ui.text(
                        this,
                        message,
                        14,
                        Ui.MUTED,
                        false
                );
        view.setGravity(Gravity.CENTER);
        view.setPadding(
                Ui.dp(this, 16),
                Ui.dp(this, 28),
                Ui.dp(this, 16),
                Ui.dp(this, 28)
        );
        return view;
    }

    private View retryView(
            String message,
            View.OnClickListener retry
    ) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(
                Ui.dp(this, 18),
                Ui.dp(this, 24),
                Ui.dp(this, 18),
                Ui.dp(this, 24)
        );

        TextView text = messageView(message);
        box.addView(text);

        TextView button =
                Ui.pill(
                        this,
                        "Réessayer",
                        Ui.BLUE,
                        Color.WHITE,
                        Ui.BLUE
                );
        button.setOnClickListener(retry);

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        Ui.dp(this, 150),
                        Ui.dp(this, 44)
                );
        buttonParams.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(button, buttonParams);

        return box;
    }

    private void openWeb(
            String url,
            String title
    ) {
        if (url == null ||
                url.trim().isEmpty()) {
            return;
        }

        try {
            Intent intent =
                    new Intent(
                            this,
                            WebActivity.class
                    );

            intent.putExtra(
                    WebActivity.EXTRA_URL,
                    url
            );

            intent.putExtra(
                    WebActivity.EXTRA_TITLE,
                    title
            );

            startActivity(intent);

        } catch (Throwable ignored) {
            // Une erreur d'ouverture d'une page Web ne doit jamais
            // faire tomber l'écran natif principal.
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager keyboard =
                    (InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);

            keyboard.hideSoftInputFromWindow(
                    search.getWindowToken(),
                    0
            );
            search.clearFocus();
        } catch (Exception ignored) {}
    }

    private void showStartupError(Exception error) {
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

        TextView title =
                Ui.text(
                        this,
                        "Ludorum n’a pas pu démarrer correctement.",
                        20,
                        Ui.NAVY,
                        true
                );
        title.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        titleParams.topMargin = Ui.dp(this, 22);
        page.addView(title, titleParams);

        TextView text =
                Ui.text(
                        this,
                        "Cette version protège le lancement : prends une capture de cet écran et je pourrai identifier précisément le problème.",
                        14,
                        Ui.MUTED,
                        false
                );
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(Ui.dp(this, 3), 1f);

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        textParams.topMargin = Ui.dp(this, 12);
        page.addView(text, textParams);

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
        technicalParams.topMargin = Ui.dp(this, 18);
        page.addView(technical, technicalParams);

        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (cartTicker != null) {
            cartTicker.refresh(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        Uri uri =
                intent != null
                        ? intent.getData()
                        : null;

        if (uri != null &&
                "https".equalsIgnoreCase(
                        uri.getScheme()
                )) {
            routeNativeUri(uri);
            return;
        }

        handleRequestedScreen(intent);
    }

    @Override
    public void onBackPressed() {
        if (productMode) {
            showCatalogue(
                    "Boutique",
                    "Tous les produits Ludorum.",
                    "",
                    1
            );
        } else if (catalogueMode ||
                favoritesMode ||
                cartMode) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private final class ProductSection {
        final String title;
        final String kicker;
        final String subtitle;
        final String query;
        final int accent;
        final int generation;

        int page = 1;

        LinearLayout grid;
        TextView pageLabel;
        TextView previous;
        TextView next;

        ProductSection(
                String title,
                String kicker,
                String subtitle,
                String query,
                int accent
        ) {
            this.title = title;
            this.kicker = kicker;
            this.subtitle = subtitle;
            this.query = query;
            this.accent = accent;
            this.generation = screenGeneration;
        }

        void attach(LinearLayout parent) {
            LinearLayout wrapper = new LinearLayout(MainActivity.this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setPadding(
                    Ui.dp(MainActivity.this, 14),
                    Ui.dp(MainActivity.this, 15),
                    Ui.dp(MainActivity.this, 14),
                    Ui.dp(MainActivity.this, 14)
            );

            int background =
                    accent == Ui.RED
                            ? Color.rgb(255, 248, 248)
                            : accent == Ui.YELLOW
                            ? Ui.IVORY
                            : Color.rgb(248, 251, 255);

            wrapper.setBackground(
                    Ui.roundedStroke(
                            background,
                            Ui.BORDER,
                            1,
                            20,
                            MainActivity.this
                    )
            );

            TextView kickerView =
                    Ui.text(
                            MainActivity.this,
                            kicker,
                            10,
                            accent == Ui.YELLOW ? Ui.YELLOW : accent,
                            true
                    );
            wrapper.addView(kickerView);

            LinearLayout headingRow =
                    new LinearLayout(MainActivity.this);
            headingRow.setOrientation(LinearLayout.HORIZONTAL);
            headingRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView heading =
                    Ui.text(
                            MainActivity.this,
                            title,
                            22,
                            Ui.NAVY,
                            true
                    );
            headingRow.addView(
                    heading,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    )
            );

            TextView all =
                    Ui.pill(
                            MainActivity.this,
                            "Tout voir",
                            accent == Ui.YELLOW ? Ui.NAVY : accent,
                            Ui.softAccent(accent),
                            Color.TRANSPARENT
                    );
            all.setTextSize(11);
            all.setOnClickListener(
                    view -> showCatalogue(
                            title,
                            subtitle,
                            query,
                            1
                    )
            );

            headingRow.addView(
                    all,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Ui.dp(MainActivity.this, 36)
                    )
            );

            LinearLayout.LayoutParams headingParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            headingParams.topMargin = Ui.dp(MainActivity.this, 4);
            wrapper.addView(headingRow, headingParams);

            TextView description =
                    Ui.text(
                            MainActivity.this,
                            subtitle,
                            13,
                            Ui.MUTED,
                            false
                    );

            LinearLayout.LayoutParams descriptionParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            descriptionParams.topMargin = Ui.dp(MainActivity.this, 5);
            descriptionParams.bottomMargin = Ui.dp(MainActivity.this, 12);
            wrapper.addView(description, descriptionParams);

            grid = new LinearLayout(MainActivity.this);
            grid.setOrientation(LinearLayout.VERTICAL);
            wrapper.addView(grid);

            LinearLayout pager = new LinearLayout(MainActivity.this);
            pager.setOrientation(LinearLayout.HORIZONTAL);
            pager.setGravity(Gravity.CENTER);
            pager.setPadding(
                    0,
                    Ui.dp(MainActivity.this, 9),
                    0,
                    0
            );

            previous =
                    Ui.pill(
                            MainActivity.this,
                            "‹",
                            Ui.BLUE,
                            Color.WHITE,
                            Ui.BORDER
                    );

            pageLabel =
                    Ui.text(
                            MainActivity.this,
                            "Page 1",
                            12,
                            Ui.NAVY,
                            true
                    );
            pageLabel.setGravity(Gravity.CENTER);

            next =
                    Ui.pill(
                            MainActivity.this,
                            "›",
                            Ui.BLUE,
                            Color.WHITE,
                            Ui.BORDER
                    );

            previous.setOnClickListener(view -> {
                if (page > 1) {
                    page--;
                    load();
                }
            });

            next.setOnClickListener(view -> {
                page++;
                load();
            });

            pager.addView(
                    previous,
                    new LinearLayout.LayoutParams(
                            Ui.dp(MainActivity.this, 48),
                            Ui.dp(MainActivity.this, 42)
                    )
            );

            pager.addView(
                    pageLabel,
                    new LinearLayout.LayoutParams(
                            0,
                            Ui.dp(MainActivity.this, 42),
                            1f
                    )
            );

            pager.addView(
                    next,
                    new LinearLayout.LayoutParams(
                            Ui.dp(MainActivity.this, 48),
                            Ui.dp(MainActivity.this, 42)
                    )
            );

            wrapper.addView(pager);

            LinearLayout.LayoutParams wrapperParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
            wrapperParams.bottomMargin =
                    Ui.dp(MainActivity.this, 16);

            parent.addView(wrapper, wrapperParams);
            load();
        }

        void load() {
            grid.removeAllViews();

            ProgressBar progress =
                    new ProgressBar(MainActivity.this);

            LinearLayout.LayoutParams progressParams =
                    new LinearLayout.LayoutParams(
                            Ui.dp(MainActivity.this, 34),
                            Ui.dp(MainActivity.this, 34)
                    );
            progressParams.gravity = Gravity.CENTER_HORIZONTAL;
            progressParams.topMargin = Ui.dp(MainActivity.this, 8);
            progressParams.bottomMargin = Ui.dp(MainActivity.this, 8);
            grid.addView(progress, progressParams);

            ApiClient.getProducts(
                    query,
                    page,
                    4,
                    new ApiClient.Callback<ApiClient.ProductPage>() {
                        @Override
                        public void onSuccess(ApiClient.ProductPage result) {
                            if (generation != screenGeneration) {
                                return;
                            }

                            grid.removeAllViews();

                            if (result.products.isEmpty() && page > 1) {
                                page--;
                                load();
                                return;
                            }

                            renderGrid(result.products, grid);

                            pageLabel.setText(
                                    "Page " + page +
                                    " / " + result.totalPages
                            );

                            previous.setEnabled(page > 1);
                            previous.setAlpha(page > 1 ? 1f : .35f);

                            next.setEnabled(page < result.totalPages);
                            next.setAlpha(
                                    page < result.totalPages ? 1f : .35f
                            );
                        }

                        @Override
                        public void onError(Exception error) {
                            if (generation != screenGeneration) {
                                return;
                            }

                            grid.removeAllViews();
                            grid.addView(
                                    retryView(
                                            "Chargement impossible.",
                                            view -> load()
                                    )
                            );
                            previous.setEnabled(false);
                            next.setEnabled(false);
                            previous.setAlpha(.35f);
                            next.setAlpha(.35f);
                        }
                    }
            );
        }
    }
}
