package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;


/**
 * Created by Juniperbrew on 05/06/16.
 */
public class RenderedParticle extends Particle {

    TextureRegion texture;
    float color;
    Animation animation;

    public RenderedParticle (Particle p, ProjectileDefinition def){
        spawnTime = p.spawnTime;
        bounds = p.bounds;

        originX = p.originX;
        originY = p.originY;
        rotation = p.rotation;
        velocity = p.velocity;
        this.range = p.range;
        this.friction = p.friction;
        this.bounce = p.bounce;
        this.ignoreMapCollision = p.ignoreMapCollision;
        this.ignoreEntityCollision = p.ignoreEntityCollision;
        this.dontDestroyOnCollision = p.dontDestroyOnCollision;
        this.hitboxScaling = p.hitboxScaling;
        this.stopOnCollision = p.stopOnCollision;
        this.duration = p.duration;


        //FROM PROJECTILE
        ownerID = p.ownerID;
        team = p.team;
        damage = p.damage;
        knockback = p.knockback;
        onDestroy = p.onDestroy;
        explosionKnockback = p.explosionKnockback;
        //#############
        setImage(def);
    }

    public RenderedParticle(ProjectileDefinition def, Rectangle rect,  float rotation, float originX, float originY, float velocity){
        super(def,rect,rotation,originX,originY,velocity);
        setImage(def);
    }

    private void setImage(ProjectileDefinition def){
        if (def.image != null) {
            TextureRegion texture = G.atlas.findRegion(def.image);
            setTexture(texture);
        } else if (def.animation != null) {
            float frameDuration = duration/G.atlas.findRegions(def.animation).size;
            Animation animation = new Animation(frameDuration, G.atlas.findRegions(def.animation));
            if(def.looping){
                animation.setPlayMode(Animation.PlayMode.LOOP);
            }
            setAnimation(animation);
        } else {
            TextureRegion texture = G.atlas.findRegion("blank");
            Color color;
            if (def.color == null) {
                color = new Color(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1);
            } else {
                color = def.color;
            }
            setTexture(texture, color);
        }
    }

    public void setTexture(TextureRegion texture){
        this.texture = texture;
    }
    public void setTexture(TextureRegion texture,Color color){
        setTexture(texture);
        this.color = color.toFloatBits();
    }
    public void setAnimation(Animation animation){
        this.animation = animation;
        setTexture(animation.getKeyFrame(0));
    }

    public void render(SpriteBatch batch){
        float[] vertices = new float[4*5];
        float[] vBounds = bounds.getTransformedVertices();
        int i = 0;

        vertices[i++] = vBounds[0];
        vertices[i++] = vBounds[1];
        vertices[i++] = color;
        vertices[i++] = texture.getU();
        vertices[i++] = texture.getV();

        vertices[i++] = vBounds[2];
        vertices[i++] = vBounds[3];
        vertices[i++] = color;
        vertices[i++] = texture.getU();
        vertices[i++] = texture.getV2();

        vertices[i++] = vBounds[4];
        vertices[i++] = vBounds[5];
        vertices[i++] = color;
        vertices[i++] = texture.getU2();
        vertices[i++] = texture.getV();

        vertices[i++] = vBounds[6];
        vertices[i++] = vBounds[7];
        vertices[i++] = color;
        vertices[i++] = texture.getU2();
        vertices[i++] = texture.getV2();

        batch.draw(texture.getTexture(),vertices,0,4*5);
    }

    public void update(float delta){
        super.update(delta);
        if(animation!=null){
            setTexture(animation.getKeyFrame(getLifeTime()));
        }
    }
}
