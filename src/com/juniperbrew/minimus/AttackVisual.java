package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
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
    int rotation;
    Polygon bounds;
    //float originX;
    //float originY;
    Color color;

    //Half the width
    float playerOriginX = -25;
    EarClippingTriangulator t = new EarClippingTriangulator();

    public AttackVisual(Rectangle rectangle, int rotation, float originX, float originY, Color color) {
        this.bounds = Tools.getRotatedRectangle(rectangle,rotation,originX,originY);
        this.rotation = rotation;
        this.color = color;
    }

    public Polygon getHitbox(){
        return bounds;
    }

    public void render(ShapeRenderer renderer){

        renderer.setColor(color);
        float[] verts = bounds.getTransformedVertices();
        ShortArray indices = t.computeTriangles(verts);
        for (int i = 0; i < indices.size; i += 3) {
            int v1 = indices.get(i) * 2;
            int v2 = indices.get(i + 1) * 2;
            int v3 = indices.get(i + 2) * 2;
            renderer.triangle(
                    verts[v1 + 0],
                    verts[v1 + 1],
                    verts[v2 + 0],
                    verts[v2 + 1],
                    verts[v3 + 0],
                    verts[v3 + 1]
            );
        }
        //renderer.rect(bounds.x,bounds.y,playerOriginX,bounds.height/2,bounds.width,bounds.height,1,1,rotation);
    }
}
