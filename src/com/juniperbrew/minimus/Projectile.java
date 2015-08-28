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
public class Projectile{

    public boolean hitscan;
    public boolean destroyed;
    public int ownerID;
    public int team;
    public int damage;

    public float knockback;
    public String onDestroy;

    float range;
    float velocity;

    float totalDistanceTraveled;

    public int rotation;
    Polygon bounds;
    Sprite sprite;
    Animation animation;
    float stateTime;

    float duration;
    long spawnTime;
    public boolean ignoreMapCollision;
    public boolean explosionKnockback;
    public boolean dontDestroyOnCollision;
    public boolean noCollision;

    float originX;
    float originY;

    public HashSet<Integer> entitiesHit = new HashSet<>();

    public Projectile(Rectangle bounds, int ownerID, int team, int damage) {
        init(bounds, 0, bounds.x+bounds.width/2, bounds.y+bounds.height/2, 0,0,ownerID,team,damage);
    }
    public Projectile(Rectangle bounds, int rotation, float originX, float originY, float range, float velocity, int ownerID, int team, int damage) {
        init(bounds, rotation, originX, originY, range,velocity,ownerID,team,damage);
    }

    public void setTexture(TextureRegion texture,Rectangle bounds){
        createSprite(texture,bounds);
    }
    public void setTexture(TextureRegion texture,Rectangle bounds,Color color){
        createSprite(texture,bounds);
        sprite.setColor(color);
    }
    public void setAnimation(Animation animation,Rectangle bounds){
        this.animation = animation;
        createSprite(animation.getKeyFrame(0),bounds);
    }

    private void init(Rectangle rectangle, int rotation, float originX, float originY, float range, float velocity, int ownerID, int team, int damage) {
        spawnTime = System.nanoTime();
        this.bounds = Tools.getRotatedRectangle(rectangle, rotation, originX, originY);
        this.originX = originX;
        this.originY = originY;
        this.rotation = rotation;
        this.range = range;
        this.velocity = velocity;
        this.ownerID = ownerID;
        this.team = team;
        this.damage = damage;
    }

    private void createSprite(TextureRegion texture, Rectangle rectangle){
        sprite = new Sprite(texture);
        sprite.setPosition(rectangle.x,rectangle.y);
        sprite.setOrigin(originX-rectangle.x,originY-rectangle.y);
        sprite.setSize(rectangle.width,rectangle.height);
        sprite.setRotation(rotation);
    }

    public void setDuration(float duration){
        this.duration = duration;
    }

    public Polygon getHitbox(){
        return bounds;
    }

    public void render(SpriteBatch batch, float delta){
        if(animation!=null){
            stateTime += delta;
            sprite.setRegion(animation.getKeyFrame(stateTime, true));
        }
        sprite.draw(batch);
    }

    public void update(float delta){
        if(!hitscan){
            float distance = velocity * delta;
            if(totalDistanceTraveled+distance>range){
                moveDistance(range-totalDistanceTraveled);
                totalDistanceTraveled = range;
                destroyed = true;
            }else{
                moveDistance(distance);
                totalDistanceTraveled += distance;
            }
        }
        if(duration>0&&System.nanoTime()-spawnTime > Tools.secondsToNano(duration)){
            destroyed = true;
        }
    }
    private void moveDistance(float distance){
        float x = MathUtils.cosDeg(rotation)*distance;
        float y = MathUtils.sinDeg(rotation)*distance;
        bounds.translate(x,y);
        sprite.translate(x,y);
    }

    @Override
    public String toString() {
        return "Projectile{" +
                "hitscan=" + hitscan +
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
