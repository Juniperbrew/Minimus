package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;


/**
 * Created by Juniperbrew on 04/08/15.
 */
public class AttackVisual {
    int rotation;
    Polygon bounds;
    //float originX;
    //float originY;
    Color color;

    //Half the width
    float playerOriginX = -25;

    public AttackVisual(Rectangle rectangle, int rotation, float originX, float originY, Color color) {
        this.bounds = Tools.getRotatedRectangle(rectangle,rotation,playerOriginX,rectangle.getHeight()/2);
        this.rotation = rotation;
        this.color = color;
    }

    public Polygon getHitbox(){
        return bounds;
    }

    public void render(ShapeRenderer renderer){
        renderer.setColor(color);
        renderer.polygon(bounds.getTransformedVertices());
        //renderer.rect(bounds.x,bounds.y,playerOriginX,bounds.height/2,bounds.width,bounds.height,1,1,rotation);
    }
}
