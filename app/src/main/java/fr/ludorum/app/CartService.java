package fr.ludorum.app;

import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CartService {
    private static final String BASE =
            "https://ludorum.fr";

    private static final String CART_API =
            BASE +
            "/wp-json/wc/store/v1/cart";

    private static final String ADD_ITEM_API =
            CART_API +
            "/add-item";

    private static final String UPDATE_ITEM_API =
            CART_API +
            "/update-item";

    private static final String REMOVE_ITEM_API =
            CART_API +
            "/remove-item";

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    private static final Handler MAIN =
            new Handler(
                    Looper.getMainLooper()
            );

    private static final long SNAPSHOT_CACHE_MS =
            3500L;

    private static volatile CartSnapshot lastSnapshot;
    private static volatile long lastSnapshotAt = 0L;

    // Identité du panier headless WooCommerce.
    // Important : elle doit survivre entre l'ajout et la lecture suivante.
    private static volatile String persistentCartToken = "";

    // Snapshot issu d'une vraie mutation confirmée (ajout / quantité / retrait).
    // Une simple lecture ne doit pas pouvoir le faire régresser immédiatement.
    private static volatile CartSnapshot lastMutationSnapshot;
    private static volatile long lastMutationAt = 0L;

    private static final long MUTATION_PRIORITY_MS =
            8000L;

    interface Callback {
        void onSuccess(
                CartSnapshot snapshot
        );

        void onError(
                String message
        );
    }

    interface AmountCallback {
        void onResult(
                long minorAmount,
                int minorUnit,
                String currencyCode
        );
    }

    static final class CartItem {
        final String key;
        final int productId;
        final String name;
        final String imageUrl;
        final int quantity;
        final int minimum;
        final int maximum;
        final int multipleOf;
        final boolean editable;
        final long lineSubtotalTtc;
        final long lineTotalTtc;

        CartItem(
                String key,
                int productId,
                String name,
                String imageUrl,
                int quantity,
                int minimum,
                int maximum,
                int multipleOf,
                boolean editable,
                long lineSubtotalTtc,
                long lineTotalTtc
        ) {
            this.key = key;
            this.productId = productId;
            this.name = name;
            this.imageUrl = imageUrl;
            this.quantity = quantity;
            this.minimum = minimum;
            this.maximum = maximum;
            this.multipleOf = multipleOf;
            this.editable = editable;
            this.lineSubtotalTtc =
                    lineSubtotalTtc;
            this.lineTotalTtc =
                    lineTotalTtc;
        }
    }

    static final class CartSnapshot {
        final List<CartItem> items;
        final int itemsCount;

        final long subtotalProductsTtc;
        final long discountTtc;
        final long feesTtc;
        final long shippingTtc;
        final long totalTtcExcludingShipping;

        final String currencyCode;
        final int currencyMinorUnit;

        CartSnapshot(
                List<CartItem> items,
                int itemsCount,
                long subtotalProductsTtc,
                long discountTtc,
                long feesTtc,
                long shippingTtc,
                long totalTtcExcludingShipping,
                String currencyCode,
                int currencyMinorUnit
        ) {
            this.items =
                    new ArrayList<>(items);

            this.itemsCount =
                    itemsCount;

            this.subtotalProductsTtc =
                    subtotalProductsTtc;

            this.discountTtc =
                    discountTtc;

            this.feesTtc =
                    feesTtc;

            this.shippingTtc =
                    shippingTtc;

            this.totalTtcExcludingShipping =
                    totalTtcExcludingShipping;

            this.currencyCode =
                    currencyCode == null ||
                    currencyCode.trim().isEmpty()
                            ? "EUR"
                            : currencyCode;

            this.currencyMinorUnit =
                    Math.max(
                            0,
                            currencyMinorUnit
                    );
        }

        CartItem itemByKey(
                String key
        ) {
            if (key == null) {
                return null;
            }

            for (CartItem item : items) {
                if (key.equals(item.key)) {
                    return item;
                }
            }

            return null;
        }

        CartItem firstItemForProduct(
                int productId
        ) {
            for (CartItem item : items) {
                if (item.productId ==
                        productId) {
                    return item;
                }
            }

            return null;
        }

        List<CartItem> itemsForProduct(
                int productId
        ) {
            List<CartItem> result =
                    new ArrayList<>();

            for (CartItem item : items) {
                if (item.productId ==
                        productId) {
                    result.add(item);
                }
            }

            return result;
        }

        int quantityForKey(
                String key
        ) {
            CartItem item =
                    itemByKey(key);

            return item == null
                    ? 0
                    : item.quantity;
        }

        int totalQuantityForProduct(
                int productId
        ) {
            int total = 0;

            for (CartItem item : items) {
                if (item.productId ==
                        productId) {
                    total +=
                            Math.max(
                                    0,
                                    item.quantity
                            );
                }
            }

            return total;
        }
    }

    private static final class Session {
        final CookieManager cookieManager;

        String cookies;
        String cartToken = "";

        final List<String> setCookies =
                new ArrayList<>();

        Session(
                CookieManager cookieManager,
                String cookies,
                String initialCartToken
        ) {
            this.cookieManager =
                    cookieManager;

            this.cookies =
                    cookies == null
                            ? ""
                            : cookies;

            this.cartToken =
                    initialCartToken == null
                            ? ""
                            : initialCartToken.trim();
        }

        void absorb(
                HttpURLConnection connection
        ) {
            String token =
                    connection.getHeaderField(
                            "Cart-Token"
                    );

            if (token == null ||
                    token.trim().isEmpty()) {
                token =
                        connection.getHeaderField(
                                "cart-token"
                        );
            }

            if (token != null &&
                    !token.trim().isEmpty()) {
                cartToken =
                        token.trim();

                persistentCartToken =
                        cartToken;
            }

            List<String> incoming =
                    collectSetCookies(
                            connection.getHeaderFields()
                    );

            if (!incoming.isEmpty()) {
                setCookies.addAll(
                        incoming
                );

                cookies =
                        mergeCookieHeader(
                                cookies,
                                incoming
                        );
            }
        }
    }

    static CartSnapshot getCachedSnapshot() {
        return lastSnapshot;
    }

    static boolean hasFreshCachedSnapshot() {
        CartSnapshot snapshot =
                lastSnapshot;

        if (snapshot == null) {
            return false;
        }

        return System.currentTimeMillis() -
                lastSnapshotAt <=
                5000L;
    }

    static void rememberOptimistic(
            CartSnapshot snapshot
    ) {
        if (snapshot == null) {
            return;
        }

        lastSnapshot =
                snapshot;

        lastSnapshotAt =
                System.currentTimeMillis();
    }

    static CartSnapshot optimisticQuantity(
            CartSnapshot source,
            String itemKey,
            int quantity
    ) {
        if (source == null ||
                itemKey == null ||
                itemKey.trim().isEmpty()) {
            return source;
        }

        CartItem target =
                source.itemByKey(
                        itemKey
                );

        if (target == null) {
            return source;
        }

        int desired =
                Math.max(
                        0,
                        quantity
                );

        List<CartItem> items =
                new ArrayList<>();

        long oldSubtotal =
                target.lineSubtotalTtc;

        long oldTotal =
                target.lineTotalTtc;

        long newSubtotal =
                0L;

        long newTotal =
                0L;

        for (CartItem item :
                source.items) {
            if (!item.key.equals(
                    itemKey
            )) {
                items.add(
                        item
                );
                continue;
            }

            if (desired <= 0) {
                continue;
            }

            newSubtotal =
                    scaledAmount(
                            item.lineSubtotalTtc,
                            item.quantity,
                            desired
                    );

            newTotal =
                    scaledAmount(
                            item.lineTotalTtc,
                            item.quantity,
                            desired
                    );

            items.add(
                    new CartItem(
                            item.key,
                            item.productId,
                            item.name,
                            item.imageUrl,
                            desired,
                            item.minimum,
                            item.maximum,
                            item.multipleOf,
                            item.editable,
                            newSubtotal,
                            newTotal
                    )
            );
        }

        int newItemsCount =
                Math.max(
                        0,
                        source.itemsCount -
                        Math.max(
                                0,
                                target.quantity
                        ) +
                        desired
                );

        long subtotal =
                Math.max(
                        0L,
                        source.subtotalProductsTtc -
                        oldSubtotal +
                        newSubtotal
                );

        long total =
                Math.max(
                        0L,
                        source.totalTtcExcludingShipping -
                        oldTotal +
                        newTotal
                );

        return new CartSnapshot(
                items,
                newItemsCount,
                subtotal,
                source.discountTtc,
                source.feesTtc,
                source.shippingTtc,
                total,
                source.currencyCode,
                source.currencyMinorUnit
        );
    }

    private static long scaledAmount(
            long amount,
            int fromQuantity,
            int toQuantity
    ) {
        if (toQuantity <= 0 ||
                amount <= 0L) {
            return 0L;
        }

        if (fromQuantity <= 0) {
            return amount;
        }

        return Math.max(
                0L,
                Math.round(
                        ((double) amount *
                        (double) toQuantity) /
                        (double) fromQuantity
                )
        );
    }

    static void getCart(
            Callback callback
    ) {
        run(
                callback,
                session ->
                        fetchCart(
                                session
                        ),
                false
        );
    }

    static void addOne(
            int productId,
            Callback callback
    ) {
        if (productId <= 0) {
            MAIN.post(
                    () -> callback.onError(
                            "Produit invalide."
                    )
            );
            return;
        }

        run(
                callback,
                session -> {
                    CartSnapshot before =
                            fetchCart(
                                    session
                            );

                    int beforeTotal =
                            before.totalQuantityForProduct(
                                    productId
                            );

                    int desiredTotal =
                            beforeTotal + 1;

                    CartItem existing =
                            before.firstItemForProduct(
                                    productId
                            );

                    CartSnapshot after;

                    if (existing != null) {
                        int target =
                                existing.quantity + 1;

                        if (existing.maximum > 0 &&
                                target >
                                existing.maximum) {
                            throw new Exception(
                                    "Quantité maximale atteinte."
                            );
                        }

                        after =
                                updateItem(
                                        session,
                                        existing.key,
                                        target
                                );

                    } else {
                        after =
                                addItem(
                                        session,
                                        productId,
                                        1
                                );
                    }

                    if (after.totalQuantityForProduct(
                            productId
                    ) != desiredTotal) {
                        try {
                            after =
                                    reconcileProduct(
                                            session,
                                            before,
                                            after,
                                            productId,
                                            desiredTotal
                                    );

                        } catch (Exception normalizationError) {
                            bestEffortRestoreProduct(
                                    session,
                                    before,
                                    productId
                            );

                            throw new Exception(
                                    "WooCommerce a renvoyé une quantité incorrecte. " +
                                    "L’ajout a été annulé pour protéger votre panier."
                            );
                        }
                    }

                    if (after.totalQuantityForProduct(
                            productId
                    ) != desiredTotal) {
                        bestEffortRestoreProduct(
                                session,
                                before,
                                productId
                        );

                        throw new Exception(
                                "Impossible de garantir un ajout de +1."
                        );
                    }

                    return after;
                },
                true
        );
    }

    static void setQuantity(
            String itemKey,
            int quantity,
            Callback callback
    ) {
        if (itemKey == null ||
                itemKey.trim().isEmpty()) {
            MAIN.post(
                    () -> callback.onError(
                            "Article panier invalide."
                    )
            );
            return;
        }

        run(
                callback,
                session -> {
                    CartSnapshot current =
                            lastSnapshot;

                    CartItem item =
                            current == null
                                    ? null
                                    : current.itemByKey(
                                            itemKey
                                    );

                    // Fast path: the native cart already knows this line.
                    // Avoid the old GET /cart round trip before UPDATE.
                    if (item == null) {
                        current =
                                fetchCart(
                                        session
                                );

                        item =
                                current.itemByKey(
                                        itemKey
                                );
                    }

                    if (item == null) {
                        return current;
                    }

                    if (quantity <= 0) {
                        return removeItem(
                                session,
                                item.key
                        );
                    }

                    int target =
                            Math.max(
                                    item.minimum,
                                    quantity
                            );

                    if (item.maximum > 0) {
                        target =
                                Math.min(
                                        target,
                                        item.maximum
                                );
                    }

                    return updateItem(
                            session,
                            item.key,
                            target
                    );
                },
                true
        );
    }

    static void removeItem(
            String itemKey,
            Callback callback
    ) {
        if (itemKey == null ||
                itemKey.trim().isEmpty()) {
            MAIN.post(
                    () -> callback.onError(
                            "Article panier invalide."
                    )
            );
            return;
        }

        run(
                callback,
                session -> {
                    CartSnapshot current =
                            lastSnapshot;

                    if (current != null &&
                            current.itemByKey(
                                    itemKey
                            ) == null) {
                        return current;
                    }

                    // No preliminary GET when the native cart already has
                    // a valid Cart-Token/session. removeItem() will only
                    // fetch if Woo genuinely requires a token.
                    return removeItem(
                            session,
                            itemKey
                    );
                },
                true
        );
    }

    static void getProductsTtc(
            AmountCallback callback
    ) {
        long now =
                System.currentTimeMillis();

        CartSnapshot cached =
                lastSnapshot;

        if (cached != null &&
                now - lastSnapshotAt <=
                SNAPSHOT_CACHE_MS) {
            MAIN.post(
                    () -> callback.onResult(
                            cached.totalTtcExcludingShipping,
                            cached.currencyMinorUnit,
                            cached.currencyCode
                    )
            );
            return;
        }

        getCart(
                new Callback() {
                    @Override
                    public void onSuccess(
                            CartSnapshot snapshot
                    ) {
                        callback.onResult(
                                snapshot.totalTtcExcludingShipping,
                                snapshot.currencyMinorUnit,
                                snapshot.currencyCode
                        );
                    }

                    @Override
                    public void onError(
                            String message
                    ) {
                        callback.onResult(
                                0L,
                                2,
                                "EUR"
                        );
                    }
                }
        );
    }

    private interface Operation {
        CartSnapshot run(
                Session session
        ) throws Exception;
    }

    private static void run(
            Callback callback,
            Operation operation,
            boolean mutation
    ) {
        final Session session;

        try {
            CookieManager manager =
                    CookieManager.getInstance();

            session =
                    new Session(
                            manager,
                            manager.getCookie(
                                    BASE
                            ),
                            persistentCartToken
                    );

        } catch (Throwable error) {
            MAIN.post(
                    () -> callback.onError(
                            "Session panier indisponible."
                    )
            );
            return;
        }

        EXECUTOR.execute(
                () -> {
                    try {
                        CartSnapshot result =
                                operation.run(
                                        session
                                );

                        CartSnapshot delivered =
                                protectFreshMutationFromReadRegression(
                                        result,
                                        mutation
                                );

                        remember(
                                delivered,
                                mutation
                        );

                        final CartSnapshot finalDelivered =
                                delivered;

                        MAIN.post(
                                () -> {
                                    syncCookies(
                                            session
                                    );

                                    callback.onSuccess(
                                            finalDelivered
                                    );
                                }
                        );

                    } catch (Exception error) {
                        String message =
                                error.getMessage();

                        if (message == null ||
                                message.trim().isEmpty()) {
                            message =
                                    "Opération panier impossible.";
                        }

                        final String finalMessage =
                                message;

                        MAIN.post(
                                () -> {
                                    syncCookies(
                                            session
                                    );

                                    callback.onError(
                                            finalMessage
                                    );
                                }
                        );
                    }
                }
        );
    }

    private static CartSnapshot fetchCart(
            Session session
    ) throws Exception {
        return request(
                session,
                CART_API,
                "GET"
        );
    }

    private static CartSnapshot addItem(
            Session session,
            int productId,
            int quantity
    ) throws Exception {
        ensureCartToken(
                session
        );

        String endpoint =
                ADD_ITEM_API +
                "?id=" +
                encode(
                        String.valueOf(
                                productId
                        )
                ) +
                "&quantity=" +
                encode(
                        String.valueOf(
                                Math.max(
                                        1,
                                        quantity
                                )
                        )
                );

        return request(
                session,
                endpoint,
                "POST"
        );
    }

    private static CartSnapshot updateItem(
            Session session,
            String key,
            int quantity
    ) throws Exception {
        ensureCartToken(
                session
        );

        String endpoint =
                UPDATE_ITEM_API +
                "?key=" +
                encode(key) +
                "&quantity=" +
                encode(
                        String.valueOf(
                                Math.max(
                                        1,
                                        quantity
                                )
                        )
                );

        return request(
                session,
                endpoint,
                "POST"
        );
    }

    private static CartSnapshot removeItem(
            Session session,
            String key
    ) throws Exception {
        ensureCartToken(
                session
        );

        String endpoint =
                REMOVE_ITEM_API +
                "?key=" +
                encode(key);

        return request(
                session,
                endpoint,
                "POST"
        );
    }

    private static void ensureCartToken(
            Session session
    ) throws Exception {
        if (session.cartToken == null ||
                session.cartToken.trim().isEmpty()) {
            fetchCart(
                    session
            );
        }

        if (session.cartToken == null ||
                session.cartToken.trim().isEmpty()) {
            throw new Exception(
                    "Token panier WooCommerce indisponible."
            );
        }
    }

    private static CartSnapshot reconcileProduct(
            Session session,
            CartSnapshot before,
            CartSnapshot current,
            int productId,
            int desiredTotal
    ) throws Exception {
        List<CartItem> currentItems =
                current.itemsForProduct(
                        productId
                );

        if (currentItems.isEmpty()) {
            throw new Exception(
                    "Produit absent après ajout."
            );
        }

        CartItem beforeTarget =
                before.firstItemForProduct(
                        productId
                );

        String targetKey = null;

        if (beforeTarget != null &&
                current.itemByKey(
                        beforeTarget.key
                ) != null) {
            targetKey =
                    beforeTarget.key;
        }

        if (targetKey == null) {
            targetKey =
                    currentItems.get(0).key;
        }

        int beforeTotal =
                before.totalQuantityForProduct(
                        productId
                );

        int extra =
                Math.max(
                        0,
                        desiredTotal -
                        beforeTotal
                );

        CartSnapshot result =
                current;

        // On retire d'abord toutes les lignes parasites créées
        // par un hook/plugin WooCommerce.
        for (CartItem item :
                new ArrayList<>(
                        currentItems
                )) {
            int baseline =
                    before.quantityForKey(
                            item.key
                    );

            int desired =
                    baseline +
                    (item.key.equals(
                            targetKey
                    )
                            ? extra
                            : 0);

            if (desired <= 0) {
                result =
                        removeItem(
                                session,
                                item.key
                        );
            }
        }

        CartItem target =
                result.itemByKey(
                        targetKey
                );

        if (target == null) {
            List<CartItem> remaining =
                    result.itemsForProduct(
                            productId
                    );

            if (remaining.isEmpty()) {
                throw new Exception(
                        "Ligne panier cible introuvable."
                );
            }

            target =
                    remaining.get(0);
        }

        int baseline =
                before.quantityForKey(
                        target.key
                );

        int desiredTarget =
                baseline + extra;

        if (desiredTarget <= 0) {
            throw new Exception(
                    "Quantité cible invalide."
            );
        }

        if (target.quantity !=
                desiredTarget) {
            result =
                    updateItem(
                            session,
                            target.key,
                            desiredTarget
                    );
        }

        if (result.totalQuantityForProduct(
                productId
        ) != desiredTotal) {
            throw new Exception(
                    "Normalisation panier impossible."
            );
        }

        return result;
    }

    private static void bestEffortRestoreProduct(
            Session session,
            CartSnapshot before,
            int productId
    ) {
        try {
            CartSnapshot current =
                    fetchCart(
                            session
                    );

            for (CartItem item :
                    new ArrayList<>(
                            current.itemsForProduct(
                                    productId
                            )
                    )) {
                int baseline =
                        before.quantityForKey(
                                item.key
                        );

                try {
                    if (baseline <= 0) {
                        current =
                                removeItem(
                                        session,
                                        item.key
                                );

                    } else if (item.quantity !=
                            baseline) {
                        current =
                                updateItem(
                                        session,
                                        item.key,
                                        baseline
                                );
                    }
                } catch (Exception ignored) {}
            }

            remember(
                    current,
                    true
            );

        } catch (Exception ignored) {}
    }

    private static CartSnapshot request(
            Session session,
            String endpoint,
            String method
    ) throws Exception {
        HttpURLConnection connection =
                null;

        try {
            connection =
                    (HttpURLConnection)
                            new URL(endpoint)
                                    .openConnection();

            connection.setConnectTimeout(
                    8000
            );

            connection.setReadTimeout(
                    10000
            );

            connection.setRequestMethod(
                    method
            );

            connection.setUseCaches(
                    false
            );

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            connection.setRequestProperty(
                    "Cache-Control",
                    "no-cache"
            );

            connection.setRequestProperty(
                    "Connection",
                    "keep-alive"
            );

            connection.setRequestProperty(
                    "User-Agent",
                    "LudorumAndroid/1.1.27"
            );

            if (session.cookies != null &&
                    !session.cookies.trim().isEmpty()) {
                connection.setRequestProperty(
                        "Cookie",
                        session.cookies
                );
            }

            // Le même Cart-Token doit identifier le même panier
            // sur les LECTURES comme sur les ÉCRITURES.
            if (session.cartToken != null &&
                    !session.cartToken.trim().isEmpty()) {
                connection.setRequestProperty(
                        "Cart-Token",
                        session.cartToken
                );
            }

            if (!"GET".equals(method)) {
                if (session.cartToken == null ||
                        session.cartToken.trim().isEmpty()) {
                    throw new Exception(
                            "Token panier manquant."
                    );
                }

                connection.setDoOutput(
                        true
                );

                try (
                        OutputStream output =
                                connection.getOutputStream()
                ) {
                    output.write(
                            new byte[0]
                    );
                }
            }

            int status =
                    connection.getResponseCode();

            InputStream stream =
                    status >= 200 &&
                    status < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            String response =
                    read(
                            stream
                    );

            session.absorb(
                    connection
            );

            if (status < 200 ||
                    status >= 300) {
                throw new Exception(
                        errorMessage(
                                status,
                                response
                        )
                );
            }

            JSONObject json =
                    new JSONObject(
                            response
                    );

            return parseSnapshot(
                    json
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static CartSnapshot parseSnapshot(
            JSONObject json
    ) {
        JSONObject totals =
                json.optJSONObject(
                        "totals"
                );

        if (totals == null) {
            totals =
                    new JSONObject();
        }

        String currencyCode =
                totals.optString(
                        "currency_code",
                        "EUR"
                );

        int minorUnit =
                totals.optInt(
                        "currency_minor_unit",
                        2
                );

        long totalItems =
                amount(
                        totals,
                        "total_items"
                );

        long totalItemsTax =
                amount(
                        totals,
                        "total_items_tax"
                );

        long totalDiscount =
                amount(
                        totals,
                        "total_discount"
                );

        long totalDiscountTax =
                amount(
                        totals,
                        "total_discount_tax"
                );

        long totalFees =
                amount(
                        totals,
                        "total_fees"
                );

        long totalFeesTax =
                amount(
                        totals,
                        "total_fees_tax"
                );

        long totalShipping =
                amount(
                        totals,
                        "total_shipping"
                );

        long totalShippingTax =
                amount(
                        totals,
                        "total_shipping_tax"
                );

        long totalPrice =
                amount(
                        totals,
                        "total_price"
                );

        long shippingTtc =
                totalShipping +
                totalShippingTax;

        long totalExcludingShipping =
                Math.max(
                        0L,
                        totalPrice -
                        shippingTtc
                );

        List<CartItem> items =
                new ArrayList<>();

        JSONArray array =
                json.optJSONArray(
                        "items"
                );

        if (array != null) {
            for (int i = 0;
                 i < array.length();
                 i++) {
                JSONObject item =
                        array.optJSONObject(i);

                if (item == null) {
                    continue;
                }

                JSONObject limits =
                        item.optJSONObject(
                                "quantity_limits"
                        );

                JSONObject itemTotals =
                        item.optJSONObject(
                                "totals"
                        );

                int minimum =
                        limits == null
                                ? 1
                                : Math.max(
                                        1,
                                        limits.optInt(
                                                "minimum",
                                                1
                                        )
                                );

                int maximum =
                        limits == null
                                ? 9999
                                : limits.optInt(
                                        "maximum",
                                        9999
                                );

                if (maximum <= 0) {
                    maximum = 9999;
                }

                int multiple =
                        limits == null
                                ? 1
                                : Math.max(
                                        1,
                                        limits.optInt(
                                                "multiple_of",
                                                1
                                        )
                                );

                boolean editable =
                        limits == null ||
                        limits.optBoolean(
                                "editable",
                                true
                        );

                String imageUrl = "";

                JSONArray images =
                        item.optJSONArray(
                                "images"
                        );

                if (images != null &&
                        images.length() > 0) {
                    JSONObject image =
                            images.optJSONObject(0);

                    if (image != null) {
                        imageUrl =
                                image.optString(
                                        "thumbnail",
                                        ""
                                );

                        if (imageUrl.trim().isEmpty()) {
                            imageUrl =
                                    image.optString(
                                            "src",
                                            ""
                                    );
                        }
                    }
                }

                long lineSubtotalTtc = 0L;
                long lineTotalTtc = 0L;

                if (itemTotals != null) {
                    lineSubtotalTtc =
                            amount(
                                    itemTotals,
                                    "line_subtotal"
                            ) +
                            amount(
                                    itemTotals,
                                    "line_subtotal_tax"
                            );

                    lineTotalTtc =
                            amount(
                                    itemTotals,
                                    "line_total"
                            ) +
                            amount(
                                    itemTotals,
                                    "line_total_tax"
                            );
                }

                items.add(
                        new CartItem(
                                item.optString(
                                        "key",
                                        ""
                                ),
                                item.optInt(
                                        "id",
                                        0
                                ),
                                item.optString(
                                        "name",
                                        "Produit Ludorum"
                                ),
                                imageUrl,
                                Math.max(
                                        0,
                                        item.optInt(
                                                "quantity",
                                                0
                                        )
                                ),
                                minimum,
                                maximum,
                                multiple,
                                editable,
                                lineSubtotalTtc,
                                lineTotalTtc
                        )
                );
            }
        }

        int itemsCount =
                json.optInt(
                        "items_count",
                        -1
                );

        if (itemsCount < 0) {
            itemsCount = 0;

            for (CartItem item : items) {
                itemsCount +=
                        Math.max(
                                0,
                                item.quantity
                        );
            }
        }

        return new CartSnapshot(
                items,
                itemsCount,
                totalItems +
                totalItemsTax,
                totalDiscount +
                totalDiscountTax,
                totalFees +
                totalFeesTax,
                shippingTtc,
                totalExcludingShipping,
                currencyCode,
                minorUnit
        );
    }

    private static long amount(
            JSONObject object,
            String key
    ) {
        if (object == null ||
                key == null) {
            return 0L;
        }

        try {
            String raw =
                    object.optString(
                            key,
                            "0"
                    );

            if (raw == null ||
                    raw.trim().isEmpty()) {
                return 0L;
            }

            return Long.parseLong(
                    raw.trim()
            );

        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static String errorMessage(
            int status,
            String response
    ) {
        String message =
                response == null
                        ? ""
                        : response.trim();

        try {
            JSONObject error =
                    new JSONObject(
                            message
                    );

            String candidate =
                    error.optString(
                            "message",
                            ""
                    );

            if (candidate != null &&
                    !candidate.trim().isEmpty()) {
                message =
                        candidate.trim();
            }

        } catch (Exception ignored) {}

        if (message.isEmpty()) {
            message =
                    "Erreur WooCommerce HTTP " +
                    status;
        }

        return message;
    }

    private static String encode(
            String value
    ) throws Exception {
        return URLEncoder.encode(
                value == null
                        ? ""
                        : value,
                "UTF-8"
        );
    }

    private static CartSnapshot protectFreshMutationFromReadRegression(
            CartSnapshot candidate,
            boolean mutation
    ) {
        if (candidate == null ||
                mutation) {
            return candidate;
        }

        CartSnapshot protectedSnapshot =
                lastMutationSnapshot;

        if (protectedSnapshot == null) {
            return candidate;
        }

        long age =
                System.currentTimeMillis() -
                lastMutationAt;

        if (age < 0L ||
                age > MUTATION_PRIORITY_MS) {
            return candidate;
        }

        // Sans nouvelle action utilisateur, une lecture 100 ms après
        // un ajout confirmé n'a aucune raison de perdre des articles.
        // C'est le symptôme typique d'une mauvaise identité panier.
        if (candidate.itemsCount <
                protectedSnapshot.itemsCount) {
            return protectedSnapshot;
        }

        return candidate;
    }

    private static void remember(
            CartSnapshot snapshot,
            boolean mutation
    ) {
        if (snapshot == null) {
            return;
        }

        long now =
                System.currentTimeMillis();

        lastSnapshot =
                snapshot;

        lastSnapshotAt =
                now;

        if (mutation) {
            lastMutationSnapshot =
                    snapshot;

            lastMutationAt =
                    now;
        }
    }

    private static void syncCookies(
            Session session
    ) {
        if (session == null ||
                session.cookieManager == null) {
            return;
        }

        for (String cookie :
                session.setCookies) {
            try {
                session.cookieManager.setCookie(
                        BASE,
                        cookie
                );
            } catch (Throwable ignored) {}
        }

        try {
            session.cookieManager.flush();
        } catch (Throwable ignored) {}
    }

    private static List<String> collectSetCookies(
            Map<String, List<String>> headers
    ) {
        List<String> result =
                new ArrayList<>();

        if (headers == null) {
            return result;
        }

        for (Map.Entry<String, List<String>> entry :
                headers.entrySet()) {
            String key =
                    entry.getKey();

            if (key == null ||
                    !"set-cookie".equalsIgnoreCase(
                            key
                    )) {
                continue;
            }

            List<String> values =
                    entry.getValue();

            if (values != null) {
                result.addAll(
                        values
                );
            }
        }

        return result;
    }

    private static String mergeCookieHeader(
            String existing,
            List<String> setCookies
    ) {
        LinkedHashMap<String, String> cookies =
                new LinkedHashMap<>();

        if (existing != null &&
                !existing.trim().isEmpty()) {
            String[] parts =
                    existing.split(";");

            for (String part : parts) {
                int equals =
                        part.indexOf("=");

                if (equals <= 0) {
                    continue;
                }

                String name =
                        part.substring(
                                0,
                                equals
                        ).trim();

                String value =
                        part.substring(
                                equals + 1
                        ).trim();

                if (!name.isEmpty()) {
                    cookies.put(
                            name,
                            value
                    );
                }
            }
        }

        if (setCookies != null) {
            for (String header :
                    setCookies) {
                if (header == null) {
                    continue;
                }

                String pair =
                        header.split(
                                ";",
                                2
                        )[0];

                int equals =
                        pair.indexOf("=");

                if (equals <= 0) {
                    continue;
                }

                String name =
                        pair.substring(
                                0,
                                equals
                        ).trim();

                String value =
                        pair.substring(
                                equals + 1
                        ).trim();

                if (!name.isEmpty()) {
                    cookies.put(
                            name,
                            value
                    );
                }
            }
        }

        StringBuilder result =
                new StringBuilder();

        for (Map.Entry<String, String> entry :
                cookies.entrySet()) {
            if (result.length() > 0) {
                result.append("; ");
            }

            result.append(
                    entry.getKey()
            );

            result.append("=");

            result.append(
                    entry.getValue()
            );
        }

        return result.toString();
    }

    private static String read(
            InputStream input
    ) throws Exception {
        if (input == null) {
            return "";
        }

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        input,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {
            StringBuilder result =
                    new StringBuilder();

            String line;

            while ((line =
                    reader.readLine()) != null) {
                result.append(line);
            }

            return result.toString();
        }
    }
}
