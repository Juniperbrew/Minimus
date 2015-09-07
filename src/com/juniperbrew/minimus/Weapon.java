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
    public String sprite;

    @Override
    public String toString() {
        return "Weapon{" +
                "name='" + name + '\'' +
                ", spread=" + spread +
                ", projectileCount=" + projectileCount +
                ", sound='" + sound + '\'' +
                ", projectile=" + projectile +
                ", cooldown=" + cooldown +
                ", image='" + image + '\'' +
                ", ammoImage='" + ammoImage + '\'' +
                ", sprite='" + sprite + '\'' +
                '}';
    }
}
