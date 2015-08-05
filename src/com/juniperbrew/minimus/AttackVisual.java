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
    //float originX;
    //float originY;
    Color color;
    AffineTransform transform = new AffineTransform();

    //Half the width
    float playerOriginX = -25;

    public AttackVisual(Rectangle2D.Float bounds, int rotation, float originX, float originY, Color color) {
        this.bounds = bounds;
        this.rotation = rotation;
        this.color = color;
    }

    public Shape getHitbox(){
        transform.rotate(playerOriginX,bounds.height/2,rotation);
        Shape hitbox = transform.createTransformedShape(bounds);
        transform.rotate(playerOriginX,bounds.height/2,-1*rotation);
        return hitbox;
    }

    public void moveDistance(float distance){
        bounds.x += distance;
        playerOriginX -= distance;
    }

    public void render(ShapeRenderer renderer){
        renderer.setColor(color);
        renderer.rect(bounds.x,bounds.y,playerOriginX,bounds.height/2,bounds.width,bounds.height,1,1,rotation);
    }
}
