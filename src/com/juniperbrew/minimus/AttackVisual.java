package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * Created by Juniperbrew on 04/08/15.
 */
public class AttackVisual {
    Rectangle2D.Float bounds;
    int rotation;
    float originX;
    float originY;
    Color color;
    AffineTransform transform = new AffineTransform();

    public AttackVisual(Rectangle2D.Float bounds, int rotation, float originX, float originY, Color color) {
        this.bounds = bounds;
        this.rotation = rotation;
        this.originX = originX;
        this.originY = originY;
        this.color = color;
    }

    public Shape getHitbox(){
        transform.rotate(originX,originY,rotation);
        return transform.createTransformedShape(bounds);
    }

    /*
    public float getX(){
        return bounds.x;
    }

    public float getY(){
        return bounds.y;
    }

    public float getWidth(){
        return bounds.width;
    }

    public float getHeight(){
        return bounds.height;
    }
    */

    public void render(ShapeRenderer renderer){
        renderer.setColor(color);
        renderer.rect(bounds.x,bounds.y,originX,originY,bounds.width,bounds.height,1,1,rotation);
    }
}
