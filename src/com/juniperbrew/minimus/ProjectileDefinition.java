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
    public float friction;
    public boolean ignoreEntityCollision;
    public boolean networked;
    public boolean looping;
    public boolean bounce;
    public float hitboxScaling;
    public boolean stopOnCollision;

    @Override
    public String toString() {
        return "ProjectileDefinition{" +
                "name='" + name + '\'' +
                ", tracer='" + tracer + '\'' +
                ", type=" + type +
                ", duration=" + duration +
                ", damage=" + damage +
                ", range=" + range +
                ", velocity=" + velocity +
                ", color=" + color +
                ", width=" + width +
                ", length=" + length +
                ", image='" + image + '\'' +
                ", animation='" + animation + '\'' +
                ", frameDuration=" + frameDuration +
                ", onDestroy='" + onDestroy + '\'' +
                ", knockback=" + knockback +
                ", ignoreMapCollision=" + ignoreMapCollision +
                ", explosionKnockback=" + explosionKnockback +
                ", dontDestroyOnCollision=" + dontDestroyOnCollision +
                ", sound='" + sound + '\'' +
                ", friction=" + friction +
                ", ignoreEntityCollision=" + ignoreEntityCollision +
                ", networked=" + networked +
                ", looping=" + looping +
                ", bounce=" + bounce +
                ", hitboxScaling=" + hitboxScaling +
                ", stopOnCollision=" + stopOnCollision +
                '}';
    }

    public ProjectileDefinition(){

    }
}
