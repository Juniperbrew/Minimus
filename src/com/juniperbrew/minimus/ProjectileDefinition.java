package com.juniperbrew.minimus;


import com.badlogic.gdx.graphics.Color;

/**
 * Created by Juniperbrew on 3.8.2015.
 */
public class ProjectileDefinition {
    public boolean hitscan;
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


    public ProjectileDefinition(){

    }
    public ProjectileDefinition(ProjectileDefinition p) {
        this.hitscan = p.hitscan;
        this.duration = p.duration;
        this.damage = p.damage;
        this.range = p.range;
        this.velocity = p.velocity;
        this.shape = p.shape;
        this.color = p.color;
        this.width = p.width;
        this.length = p.length;
        this.image = p.image;
        this.animation = p.animation;
        this.frameDuration = p.frameDuration;
        this.onDestroy = p.onDestroy;
    }
}
