package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ShortArray;


/**
 * Created by Juniperbrew on 04/08/15.
 */
public class AttackVisual {
    /*int rotation;
    Polygon bounds;
    Sprite sprite;
    Animation animation;
    float stateTime;

    public AttackVisual(Rectangle rectangle, TextureRegion texture, int rotation, float originX, float originY, Color color) {
        this.bounds = Tools.getRotatedRectangle(rectangle,rotation,originX,originY);
        this.rotation = rotation;
        createSprite(texture,rectangle);
        sprite.setColor(color);
    }
    public AttackVisual(Rectangle rectangle, TextureRegion texture, int rotation, float originX, float originY) {
        this.bounds = Tools.getRotatedRectangle(rectangle,rotation,originX,originY);
        this.rotation = rotation;
        createSprite(texture,rectangle);
    }

    public AttackVisual(Rectangle rectangle, Animation animation, int rotation, float originX, float originY) {
        this.bounds = Tools.getRotatedRectangle(rectangle,rotation,originX,originY);
        this.rotation = rotation;
        this.animation = animation;
        createSprite(animation.getKeyFrame(0),rectangle);
    }

    private void createSprite(TextureRegion texture, Rectangle rectangle){
        sprite = new Sprite(texture);
        sprite.setPosition(rectangle.x,rectangle.y);
        sprite.setOrigin(-25,0);
        sprite.setSize(rectangle.width,rectangle.height);
        sprite.setRotation(rotation);
    }

    public void moveDistance(float distance){
        float x = MathUtils.cosDeg(rotation)*distance;
        float y = MathUtils.sinDeg(rotation)*distance;
        bounds.translate(x,y);
        sprite.translate(x,y);
    }

    public Polygon getHitbox(){
        return bounds;
    }

    public void render(SpriteBatch batch, float delta){
        if(animation!=null){
            stateTime += delta;
            sprite.setRegion(animation.getKeyFrame(stateTime,true));
        }
        batch.begin();
        sprite.draw(batch);
        batch.end();
    }*/
}
