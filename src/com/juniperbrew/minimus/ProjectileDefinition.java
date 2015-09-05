package com.juniperbrew.minimus;


import com.badlogic.gdx.graphics.Color;

/**
 * Created by Juniperbrew on 3.8.2015.
 */
public class ProjectileDefinition {

    public static final byte PROJECTILE = 0;
    public static final byte HITSCAN = 1;
    public static final byte PARTICLE = 2;
    public static final byte TRACER = 3;

    public String name;
    public String tracer;
    public byte type;
    public float duration;
    public int damage;
    public int range;
    public int velocity;
    public String shape; //square,circle
    public Color color;
    public int width;
    public int length;
    public String image;
    public String animation;
    public float frameDuration;
    public String onDestroy;
    public float knockback;
    public boolean ignoreMapCollision;
    public boolean explosionKnockback;
    public boolean dontDestroyOnCollision;
    public String sound;
    public boolean friction;
    public boolean noCollision;
    public boolean networked;
    public boolean looping;

    public ProjectileDefinition(){

    }
}
