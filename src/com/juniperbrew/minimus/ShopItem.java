package com.juniperbrew.minimus;

/**
 * Created by Juniperbrew on 27.9.2015.
 */
public class ShopItem {
    public String type;
    public String name;
    public int value;

    public ShopItem() {
    }

    public ShopItem(String type, String name, int value) {
        this.type = type;
        this.name = name;
        this.value = value;
    }

    @Override
    public String toString() {
        return "ShopItem{" +
                "type='" + type + '\'' +
                ", name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}
