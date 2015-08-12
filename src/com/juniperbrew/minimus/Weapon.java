package com.juniperbrew.minimus;

/**
 * Created by Juniperbrew on 30.7.2015.
 */
public class Weapon {
    public String name;
    public int spread;
    public int projectileCount;
    public String sound;
    public ProjectileDefinition projectile;
    public double cooldown;
    public String image;
    public String ammoImage;

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("Name: "+name);
        b.append(" Projectile: "+projectile);
        b.append(" Spread: "+spread);
        b.append(" ProjectileCount: "+projectileCount);
        b.append(" Sound: "+sound);
        b.append(" Image: "+image);
        return b.toString();
    }
}
