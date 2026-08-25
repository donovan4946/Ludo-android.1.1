package fr.ludorum.app;

import org.json.JSONObject;

final class ProductCategory {
    int id;
    String name = "";
    String slug = "";
    int parent;
    int count;

    static ProductCategory fromJson(JSONObject o) {
        ProductCategory c = new ProductCategory();
        c.id = o.optInt("id");
        c.name = o.optString("name", "");
        c.slug = o.optString("slug", "");
        c.parent = o.optInt("parent", 0);
        c.count = o.optInt("count", 0);
        return c;
    }
}
