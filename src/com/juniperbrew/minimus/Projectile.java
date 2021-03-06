package com.juniperbrew.minimus;

import com.badlogic.gdx.math.Rectangle;

import java.util.HashSet;


/**
 * Created by Juniperbrew on 28.6.2015.
 */
public class Projectile extends Particle{

    public int ownerID;
    public int team;
    public int damage;

    public float knockback;
    public String onDestroy;


    public boolean explosionKnockback;

    public HashSet<Integer> entitiesHit = new HashSet<>();

    public Projectile(ProjectileDefinition def, Rectangle bounds, int ownerID, int team) {
        super(def,bounds);
        init(def,ownerID,team);
    }
    public Projectile(ProjectileDefinition def, Rectangle bounds, float rotation, float originX, float originY, int ownerID, int team) {
        super(def,bounds,rotation,originX,originY);
        init(def,ownerID,team);
    }

    protected void init(ProjectileDefinition def, int ownerID, int team) {
        this.ownerID = ownerID;
        this.team = team;
        this.damage = def.damage;
        knockback = def.knockback;
        onDestroy = def.onDestroy;
        explosionKnockback = def.explosionKnockback;
    }

    @Override
    public String toString() {
        return "Projectile{" +
                ", destroyed=" + destroyed +
                ", ownerID=" + ownerID +
                ", team=" + team +
                ", damage=" + damage +
                ", range=" + range +
                ", movement=" + velocity +
                ", totalDistanceTraveled=" + totalDistanceTraveled +
                ", rotation=" + rotation +
                ", sprite=" + sprite +
                ", animation=" + animation +
                ", stateTime=" + stateTime +
                ", duration=" + duration +
                ", spawnTime=" + spawnTime +
                ", ignoreMapCollision=" + ignoreMapCollision +
                '}';
    }
}
