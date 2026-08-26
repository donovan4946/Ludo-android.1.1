package fr.ludorum.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ApiClient {
    static final String BASE = "https://ludorum.fr";
    static final String STORE = BASE + "/wp-json/wc/store/v1";

    interface Callback<T> {
        void onSuccess(T value);
        void onError(Exception error);
    }

    static final class ProductPage {
        final List<Product> products;
        final int page;
        final int totalPages;

        ProductPage(List<Product> products, int page, int totalPages) {
            this.products = products;
            this.page = page;
            this.totalPages = Math.max(1, totalPages);
        }
    }

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(4);

    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    private static final long PRODUCTS_CACHE_MS = 30000L;
    private static final long PRODUCT_CACHE_MS = 30000L;
    private static final long CATEGORY_CACHE_MS = 300000L;

    /*
     * Caches volontairement TYPÉS.
     * On n'utilise plus un Map<String,Object> générique : cela évite tout
     * risque de conversion ProductPage <-> List<ProductCategory>.
     */
    private static final Map<String, ProductPage> PRODUCT_PAGE_CACHE =
            new ConcurrentHashMap<>();

    private static final Map<String, Long> PRODUCT_PAGE_CACHE_TIME =
            new ConcurrentHashMap<>();

    private static final Map<String, Product> PRODUCT_BY_SLUG_CACHE =
            new ConcurrentHashMap<>();

    private static final Map<String, Long> PRODUCT_BY_SLUG_CACHE_TIME =
            new ConcurrentHashMap<>();

    private static final Map<String, ProductCategory> CATEGORY_BY_SLUG_CACHE =
            new ConcurrentHashMap<>();

    private static final Map<String, Long> CATEGORY_BY_SLUG_CACHE_TIME =
            new ConcurrentHashMap<>();

    private static final Map<Integer, ProductCategory> CATEGORY_BY_ID_CACHE =
            new ConcurrentHashMap<>();

    private static final Map<Integer, Long> CATEGORY_BY_ID_CACHE_TIME =
            new ConcurrentHashMap<>();

    private static final Map<Integer, List<ProductCategory>> CHILD_CATEGORIES_CACHE =
            new ConcurrentHashMap<>();

    private static final Map<Integer, Long> CHILD_CATEGORIES_CACHE_TIME =
            new ConcurrentHashMap<>();

    private static volatile List<ProductCategory> topCategoriesCache = null;
    private static volatile long topCategoriesCacheTime = 0L;

    private static boolean fresh(
            Long timestamp,
            long ttl
    ) {
        return timestamp != null &&
                System.currentTimeMillis() - timestamp <= ttl;
    }

    private static ProductPage getCachedProductPage(
            String key
    ) {
        Long time = PRODUCT_PAGE_CACHE_TIME.get(key);

        if (!fresh(time, PRODUCTS_CACHE_MS)) {
            PRODUCT_PAGE_CACHE.remove(key);
            PRODUCT_PAGE_CACHE_TIME.remove(key);
            return null;
        }

        return PRODUCT_PAGE_CACHE.get(key);
    }

    private static void putCachedProductPage(
            String key,
            ProductPage value
    ) {
        if (key == null || value == null) return;

        PRODUCT_PAGE_CACHE.put(key, value);
        PRODUCT_PAGE_CACHE_TIME.put(
                key,
                System.currentTimeMillis()
        );
    }

    private static Product getCachedProduct(
            String key
    ) {
        Long time = PRODUCT_BY_SLUG_CACHE_TIME.get(key);

        if (!fresh(time, PRODUCT_CACHE_MS)) {
            PRODUCT_BY_SLUG_CACHE.remove(key);
            PRODUCT_BY_SLUG_CACHE_TIME.remove(key);
            return null;
        }

        return PRODUCT_BY_SLUG_CACHE.get(key);
    }

    private static void putCachedProduct(
            String key,
            Product value
    ) {
        if (key == null || value == null) return;

        PRODUCT_BY_SLUG_CACHE.put(key, value);
        PRODUCT_BY_SLUG_CACHE_TIME.put(
                key,
                System.currentTimeMillis()
        );
    }

    private static ProductCategory getCachedCategory(
            String key
    ) {
        Long time = CATEGORY_BY_SLUG_CACHE_TIME.get(key);

        if (!fresh(time, CATEGORY_CACHE_MS)) {
            CATEGORY_BY_SLUG_CACHE.remove(key);
            CATEGORY_BY_SLUG_CACHE_TIME.remove(key);
            return null;
        }

        return CATEGORY_BY_SLUG_CACHE.get(key);
    }

    private static void putCachedCategory(
            String key,
            ProductCategory value
    ) {
        if (key == null || value == null) return;

        CATEGORY_BY_SLUG_CACHE.put(key, value);
        CATEGORY_BY_SLUG_CACHE_TIME.put(
                key,
                System.currentTimeMillis()
        );
    }

    private static ProductCategory getCachedCategoryById(
            int id
    ) {
        Long time = CATEGORY_BY_ID_CACHE_TIME.get(id);
        if (!fresh(time, CATEGORY_CACHE_MS)) {
            CATEGORY_BY_ID_CACHE.remove(id);
            CATEGORY_BY_ID_CACHE_TIME.remove(id);
            return null;
        }
        return CATEGORY_BY_ID_CACHE.get(id);
    }

    private static void putCachedCategoryById(
            ProductCategory value
    ) {
        if (value == null || value.id <= 0) return;
        CATEGORY_BY_ID_CACHE.put(value.id, value);
        CATEGORY_BY_ID_CACHE_TIME.put(value.id, System.currentTimeMillis());
    }

    private static List<ProductCategory> getCachedChildCategories(
            int parentId
    ) {
        Long time = CHILD_CATEGORIES_CACHE_TIME.get(parentId);
        if (!fresh(time, CATEGORY_CACHE_MS)) {
            CHILD_CATEGORIES_CACHE.remove(parentId);
            CHILD_CATEGORIES_CACHE_TIME.remove(parentId);
            return null;
        }
        List<ProductCategory> cached = CHILD_CATEGORIES_CACHE.get(parentId);
        return cached == null ? null : new ArrayList<>(cached);
    }

    private static void putCachedChildCategories(
            int parentId,
            List<ProductCategory> categories
    ) {
        if (parentId <= 0 || categories == null) return;
        CHILD_CATEGORIES_CACHE.put(parentId, new ArrayList<>(categories));
        CHILD_CATEGORIES_CACHE_TIME.put(parentId, System.currentTimeMillis());
    }

    private static List<ProductCategory> getCachedTopCategories() {
        if (topCategoriesCache == null ||
                System.currentTimeMillis() -
                topCategoriesCacheTime >
                CATEGORY_CACHE_MS) {
            topCategoriesCache = null;
            topCategoriesCacheTime = 0L;
            return null;
        }

        return new ArrayList<>(topCategoriesCache);
    }

    private static void putCachedTopCategories(
            List<ProductCategory> categories
    ) {
        if (categories == null) return;

        topCategoriesCache =
                new ArrayList<>(categories);

        topCategoriesCacheTime =
                System.currentTimeMillis();
    }

    static void getProducts(
            String query,
            int page,
            int perPage,
            Callback<ProductPage> callback
    ) {
        String q =
                query == null
                        ? ""
                        : query;

        String url =
                STORE +
                "/products?per_page=" +
                perPage +
                "&page=" +
                page +
                q;

        String cacheKey =
                "products:" + url;

        ProductPage cached =
                getCachedProductPage(
                        cacheKey
                );

        if (cached != null) {
            MAIN.post(
                    () -> callback.onSuccess(cached)
            );
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(url);

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new Exception("HTTP " + status);
                }

                JSONArray array = new JSONArray(read(connection.getInputStream()));
                List<Product> products = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    if (array.optJSONObject(i) != null) {
                        products.add(Product.fromJson(array.optJSONObject(i)));
                    }
                }

                int pages = parseInt(connection.getHeaderField("X-WP-TotalPages"), 0);
                if (pages <= 0) {
                    pages = array.length() < perPage ? page : page + 1;
                }

                ProductPage result =
                        new ProductPage(
                                products,
                                page,
                                pages
                        );

                putCachedProductPage(
                        cacheKey,
                        result
                );

                MAIN.post(
                        () -> callback.onSuccess(result)
                );
            } catch (Exception error) {
                MAIN.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    static void getProductBySlug(
            String slug,
            Callback<Product> callback
    ) {
        String safeSlug =
                slug == null
                        ? ""
                        : slug.trim();

        String cacheKey =
                "product:" + safeSlug;

        Product cached =
                getCachedProduct(
                        cacheKey
                );

        if (cached != null) {
            MAIN.post(
                    () -> callback.onSuccess(cached)
            );
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String encoded =
                        URLEncoder.encode(
                                safeSlug,
                                "UTF-8"
                        );

                connection = open(
                        STORE +
                        "/products?slug=" +
                        encoded +
                        "&per_page=1"
                );

                int status =
                        connection.getResponseCode();

                if (status < 200 ||
                        status >= 300) {
                    throw new Exception(
                            "HTTP " + status
                    );
                }

                JSONArray array =
                        new JSONArray(
                                read(
                                        connection.getInputStream()
                                )
                        );

                if (array.length() < 1 ||
                        array.optJSONObject(0) == null) {
                    throw new Exception(
                            "Produit introuvable"
                    );
                }

                Product product =
                        Product.fromJson(
                                array.optJSONObject(0)
                        );

                putCachedProduct(
                        cacheKey,
                        product
                );

                MAIN.post(
                        () -> callback.onSuccess(product)
                );

            } catch (Exception error) {
                MAIN.post(
                        () -> callback.onError(error)
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    static void getCategoryBySlug(
            String slug,
            Callback<ProductCategory> callback
    ) {
        String safeSlug =
                slug == null
                        ? ""
                        : slug.trim();

        String cacheKey =
                "category:" + safeSlug;

        ProductCategory cached =
                getCachedCategory(
                        cacheKey
                );

        if (cached != null) {
            MAIN.post(
                    () -> callback.onSuccess(cached)
            );
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String encoded =
                        URLEncoder.encode(
                                safeSlug,
                                "UTF-8"
                        );

                connection = open(
                        STORE +
                        "/products/categories?slug=" +
                        encoded +
                        "&per_page=1"
                );

                int status =
                        connection.getResponseCode();

                if (status < 200 ||
                        status >= 300) {
                    throw new Exception(
                            "HTTP " + status
                    );
                }

                JSONArray array =
                        new JSONArray(
                                read(
                                        connection.getInputStream()
                                )
                        );

                if (array.length() < 1 ||
                        array.optJSONObject(0) == null) {
                    throw new Exception(
                            "Catégorie introuvable"
                    );
                }

                ProductCategory category =
                        ProductCategory.fromJson(
                                array.optJSONObject(0)
                        );

                putCachedCategory(
                        cacheKey,
                        category
                );

                putCachedCategoryById(
                        category
                );

                MAIN.post(
                        () -> callback.onSuccess(category)
                );

            } catch (Exception error) {
                MAIN.post(
                        () -> callback.onError(error)
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    static void getCategoryById(
            int id,
            Callback<ProductCategory> callback
    ) {
        if (id <= 0) {
            MAIN.post(() -> callback.onError(new Exception("Catégorie invalide")));
            return;
        }

        ProductCategory cached = getCachedCategoryById(id);
        if (cached != null) {
            MAIN.post(() -> callback.onSuccess(cached));
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(STORE + "/products/categories/" + id);
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new Exception("HTTP " + status);

                ProductCategory category = ProductCategory.fromJson(
                        new org.json.JSONObject(read(connection.getInputStream()))
                );
                if (category.id <= 0) throw new Exception("Catégorie introuvable");

                putCachedCategoryById(category);
                if (category.slug != null && !category.slug.trim().isEmpty()) {
                    putCachedCategory("category:" + category.slug.trim(), category);
                }
                MAIN.post(() -> callback.onSuccess(category));
            } catch (Exception error) {
                MAIN.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    static void getChildCategories(
            int parentId,
            Callback<List<ProductCategory>> callback
    ) {
        if (parentId <= 0) {
            MAIN.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        List<ProductCategory> cached = getCachedChildCategories(parentId);
        if (cached != null) {
            MAIN.post(() -> callback.onSuccess(cached));
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(
                        STORE + "/products/categories?per_page=100" +
                        "&hide_empty=true&parent=" + parentId +
                        "&orderby=name&order=asc"
                );
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new Exception("HTTP " + status);

                JSONArray array = new JSONArray(read(connection.getInputStream()));
                List<ProductCategory> categories = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    if (array.optJSONObject(i) == null) continue;
                    ProductCategory category = ProductCategory.fromJson(array.optJSONObject(i));
                    if (category.id <= 0 || isHiddenCategory(category.name) || isHiddenCategory(category.slug)) continue;
                    categories.add(category);
                    putCachedCategoryById(category);
                    if (category.slug != null && !category.slug.trim().isEmpty()) {
                        putCachedCategory("category:" + category.slug.trim(), category);
                    }
                }

                categories.sort((a,b) -> {
                    int pa = childCategoryPriority(a.name);
                    int pb = childCategoryPriority(b.name);
                    if (pa != pb) return Integer.compare(pa,pb);
                    return a.name.compareToIgnoreCase(b.name);
                });

                putCachedChildCategories(parentId, categories);
                MAIN.post(() -> callback.onSuccess(new ArrayList<>(categories)));
            } catch (Exception error) {
                MAIN.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    static void getTopCategories(
            Callback<List<ProductCategory>> callback
    ) {
        List<ProductCategory> cached =
                getCachedTopCategories();

        if (cached != null) {
            MAIN.post(
                    () -> callback.onSuccess(cached)
            );
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(
                        STORE +
                        "/products/categories?per_page=100&hide_empty=true&parent=0" +
                        "&orderby=count&order=desc"
                );

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new Exception("HTTP " + status);
                }

                JSONArray array = new JSONArray(read(connection.getInputStream()));
                List<ProductCategory> categories = new ArrayList<>();

                for (int i = 0; i < array.length(); i++) {
                    if (array.optJSONObject(i) == null) continue;

                    ProductCategory category =
                            ProductCategory.fromJson(array.optJSONObject(i));

                    if (isHiddenCategory(category.name) ||
                            isHiddenCategory(category.slug)) {
                        continue;
                    }
                    categories.add(category);
                    putCachedCategoryById(category);
                }

                categories.sort(
                        (a, b) -> Integer.compare(
                                categoryPriority(a.name),
                                categoryPriority(b.name)
                        )
                );

                putCachedTopCategories(
                        categories
                );

                MAIN.post(
                        () -> callback.onSuccess(categories)
                );
            } catch (Exception error) {
                MAIN.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static boolean isHiddenCategory(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty()
                || normalized.contains("jeton")
                || normalized.contains("compteur")
                || normalized.equals("non classe")
                || normalized.equals("non classee")
                || normalized.equals("uncategorized");
    }

    private static int categoryPriority(String value) {
        String normalized = normalize(value);
        if (normalized.contains("jeux de societe")) return 0;
        if (normalized.contains("jeux de cartes")) return 1;
        if (normalized.contains("accessoire")) return 2;
        if (normalized.contains("pack")) return 3;
        return 10;
    }

    private static int childCategoryPriority(String value) {
        String normalized = normalize(value);
        if (normalized.contains("famille") || normalized.contains("familial")) return 0;
        if (normalized.contains("enfant")) return 1;
        if (normalized.contains("ambiance")) return 2;
        if (normalized.contains("strategie")) return 3;
        if (normalized.contains("expert")) return 4;
        if (normalized.contains("cooperatif")) return 5;
        if (normalized.contains("deux joueurs") || normalized.contains("2 joueurs")) return 6;
        if (normalized.contains("solo")) return 7;
        if (normalized.contains("enquete") || normalized.contains("escape")) return 8;
        if (normalized.contains("lorcana")) return 20;
        if (normalized.contains("one piece")) return 21;
        if (normalized.contains("pokemon")) return 22;
        if (normalized.contains("riftbound") || normalized.contains("rift bound")) return 23;
        return 100;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(url).openConnection();

        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");
        connection.setUseCaches(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("User-Agent", "LudorumAndroid/1.1.14");
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    private static String read(InputStream input) throws Exception {
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(input, StandardCharsets.UTF_8)
                )
        ) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
