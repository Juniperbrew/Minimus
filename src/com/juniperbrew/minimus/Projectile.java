package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;

import java.awt.geom.Line2D;
import java.util.ArrayList;
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

    public boolean ignoreMapCollision;
    public boolean explosionKnockback;
    public boolean dontDestroyOnCollision;
    public boolean noCollision;

    public HashSet<Integer> entitiesHit = new HashSet<>();

    public Projectile(ProjectileDefinition def, Rectangle bounds, int ownerID, int team) {
        super(def,bounds);
        init(def,ownerID,team);
    }
    public Projectile(ProjectileDefinition def, Rectangle bounds, int rotation, float originX, float originY, int ownerID, int team) {
        super(def,bounds,rotation,originX,originY);
        init(def,ownerID,team);
    }

    protected void init(ProjectileDefinition def, int ownerID, int team) {
        this.ownerID = ownerID;
        this.team = team;
        this.damage = def.damage;

        knockback = def.knockback;
        onDestroy = def.onDestroy;
        ignoreMapCollision = def.ignoreMapCollision;
        explosionKnockback = def.explosionKnockback;
        dontDestroyOnCollision = def.dontDestroyOnCollision;
        noCollision = def.noCollision;
    }

    public Polygon getHitbox(){
        return bounds;
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
                ", bounds=" + bounds +
                ", sprite=" + sprite +
                ", animation=" + animation +
                ", stateTime=" + stateTime +
                ", duration=" + duration +
                ", spawnTime=" + spawnTime +
                ", ignoreMapCollision=" + ignoreMapCollision +
                '}';
    }
}
