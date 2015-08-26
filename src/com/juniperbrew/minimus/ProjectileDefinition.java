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
    public float knockback;


    public ProjectileDefinition(){

    }
}
