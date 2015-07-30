package com.juniperbrew.minimus;

/**
 * Created by Juniperbrew on 30.7.2015.
 */
public class Weapon {
    public String name;
    public boolean hitScan;
    public float visualDuration;
    public int range;
    public int velocity;
    public int spread;
    public int projectileCount;
    public int damage;
    public String sound;

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("Name: "+name);
        b.append(" Hitscan: "+hitScan);
        b.append(" VisualDuration: "+visualDuration);
        b.append(" Range: "+range);
        b.append(" Velocity: "+velocity);
        b.append(" Spread: "+spread);
        b.append(" ProjectileCount: "+projectileCount);
        b.append(" Damage: "+damage);
        b.append(" Sound: "+sound);
        return b.toString();
    }
}
