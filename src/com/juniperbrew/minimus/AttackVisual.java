package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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
    Sprite sprite;
    //float originX;
    //float originY;
    Color color;

    //Half the width
    float playerOriginX = -25;
    EarClippingTriangulator t = new EarClippingTriangulator();

    static Pixmap pixmap = new Pixmap(1,1,Pixmap.Format.RGBA8888);

    public AttackVisual(Rectangle rectangle, int rotation, float originX, float originY, Color color) {
        this.bounds = Tools.getRotatedRectangle(rectangle,rotation,originX,originY);
        this.rotation = rotation;
        this.color = color;
        //pixmap.drawPixel(0,0,color.toIntBits());
        //createSprite(new Texture(pixmap),rectangle,originX,originY);
    }
    public AttackVisual(Rectangle rectangle, Texture texture, int rotation, float originX, float originY, Color color) {
        this.bounds = Tools.getRotatedRectangle(rectangle,rotation,originX,originY);
        this.rotation = rotation;
        this.color = color;
        createSprite(texture,rectangle,originX,originY);
    }

    private void createSprite(Texture texture, Rectangle rectangle, float originX, float originY){
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
        if(sprite!=null){
            sprite.translate(x,y);
        }
    }

    public Polygon getHitbox(){
        return bounds;
    }

    public void render(ShapeRenderer renderer){
        renderer.begin(ShapeRenderer.ShapeType.Filled);
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
        renderer.end();
    }

    public void render(SpriteBatch batch){
        batch.begin();
        sprite.draw(batch);
        batch.end();
    }
}
