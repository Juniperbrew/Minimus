package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * Created by Juniperbrew on 5.9.2015.
 */
public class Particle {

    public float rotation;
    Sprite sprite;
    Animation animation;
    float stateTime;
    float duration;
    long spawnTime;
    float originX;
    float originY;
    float totalDistanceTraveled;
    public boolean destroyed;
    float range;
    float velocity;
    float friction;
    public boolean bounce;
    public boolean ignoreMapCollision;
    public boolean ignoreEntityCollision;
    public boolean dontDestroyOnCollision;
    public float hitboxScaling;
    public boolean stopOnCollision;
    public boolean stopped;

    public Particle(ProjectileDefinition def, Rectangle rect){
        this(def,rect,0,rect.x+rect.width/2,rect.y+rect.height/2,0);
    }

    public Particle(ProjectileDefinition def, Rectangle rect, float rotation, float originX, float originY){
        this(def,rect,rotation,originX,originY,def.velocity);
    }

    public Particle(ProjectileDefinition def, Rectangle rect, float rotation, float originX, float originY, float velocity){
        spawnTime = System.nanoTime();
        this.originX = originX;
        this.originY = originY;
        this.rotation = rotation;
        this.velocity = velocity;
        this.range = def.range;
        this.friction = def.friction;
        this.bounce = def.bounce;
        this.ignoreMapCollision = def.ignoreMapCollision;
        this.ignoreEntityCollision = def.ignoreEntityCollision;
        this.dontDestroyOnCollision = def.dontDestroyOnCollision;
        this.hitboxScaling = def.hitboxScaling;
        this.stopOnCollision = def.stopOnCollision;
        setImage(def, rect);
        setDuration(def.duration);
    }


    private void setImage(ProjectileDefinition def, Rectangle rect){
        if (def.image != null) {
            TextureRegion texture = G.atlas.findRegion(def.image);
            setTexture(texture, rect);
        } else if (def.animation != null) {
            Animation animation = new Animation(def.frameDuration, G.atlas.findRegions(def.animation));
            if(def.looping){
                animation.setPlayMode(Animation.PlayMode.LOOP);
            }
            setAnimation(animation, rect);
        } else {
            TextureRegion texture = G.atlas.findRegion("blank");
            Color color;
            if (def.color == null) {
                color = new Color(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1);
            } else {
                color = def.color;
            }
            setTexture(texture, rect, color);
        }
    }

    public void setTexture(TextureRegion texture,Rectangle rect){
        createSprite(texture, rect);
    }
    public void setTexture(TextureRegion texture,Rectangle rect,Color color){
        createSprite(texture,rect);
        sprite.setColor(color);
    }
    public void setAnimation(Animation animation,Rectangle rect){
        this.animation = animation;
        createSprite(animation.getKeyFrame(0), rect);
    }

    private void createSprite(TextureRegion texture, Rectangle rect){
        sprite = new Sprite(texture);
        sprite.setPosition(rect.x,rect.y);
        sprite.setOrigin(originX-rect.x,originY-rect.y);
        sprite.setSize(rect.width, rect.height);
        sprite.setRotation(rotation);
    }

    public void setDuration(float duration){
        this.duration = duration;
    }

    public void render(SpriteBatch batch, float delta){
        //FIXME animation statetime goes ahead twice as fast when projectile has stopped this looks good with flamethrower but will be an unpredictable effect in general
        if(animation!=null){
            if(stopped){
                stateTime += 2*delta;
            }else{
                stateTime += delta;
            }
            sprite.setRegion(animation.getKeyFrame(stateTime));
        }
        sprite.draw(batch);
    }

    public void update(float delta) {
        if(friction!=0){
            velocity -= friction*delta;
            if(velocity<0){
                velocity = 0;
            }
        }
        if(!stopped){
            float distance = velocity * delta;
            if (range>0 &&totalDistanceTraveled + distance > range) {
                moveDistance(range - totalDistanceTraveled);
                totalDistanceTraveled = range;
                destroyed = true;
            } else {
                moveDistance(distance);
                totalDistanceTraveled += distance;
            }
        }

        if(animation!=null && animation.getPlayMode() == Animation.PlayMode.NORMAL){
            if(animation.isAnimationFinished(stateTime)){
                destroyed = true;
            }
        }else{
            if(range==0&&System.nanoTime()-spawnTime > Tools.secondsToNano(duration)){
                destroyed = true;
            }
        }
    }

    private void moveDistance(float distance){
        float x = MathUtils.cosDeg(rotation)*distance;
        float y = MathUtils.sinDeg(rotation)*distance;
        if(!ignoreMapCollision){
            Rectangle r = getBoundingRectangle();
            r.setPosition(r.x + x, r.y);
            if(SharedMethods.checkMapCollision(r)){
                if(bounce){
                    rotation = 180 - rotation;
                    sprite.setRotation(rotation);
                    return;
                }else if(stopOnCollision){
                    stopped = true;
                    return;
                }else if(!dontDestroyOnCollision){
                    destroyed = true;
                    return;
                }
            }
            r.setPosition(r.x, r.y + y);
            if(SharedMethods.checkMapCollision(r)){
                if(bounce){
                    rotation = 0 - rotation;
                    sprite.setRotation(rotation);
                    return;
                }else if(stopOnCollision){
                    stopped = true;
                    return;
                }else if(!dontDestroyOnCollision){
                    destroyed = true;
                    return;
                }
            }
        }
        sprite.translate(x, y);
    }

    public Polygon getBoundingPolygon(){
        Polygon bounds = Tools.getBoundingPolygon(sprite);
        if(hitboxScaling!=0){
            Vector2 center = new Vector2();
            getBoundingRectangle().getCenter(center);
            bounds.setOrigin(center.x,center.y);
            bounds.setScale(hitboxScaling,hitboxScaling);
        }
        return bounds;
    }

    public Rectangle getBoundingRectangle(){
        Rectangle bounds = sprite.getBoundingRectangle();
        if(hitboxScaling!=0){
            float scaledWidth = bounds.width*hitboxScaling;
            float scaledHeight = bounds.height*hitboxScaling;
            float xOffset = (bounds.width-scaledWidth)/2;
            float yOffset = (bounds.height-scaledHeight)/2;
            bounds.set(bounds.x + xOffset, bounds.y + yOffset, scaledWidth, scaledHeight);
        }
        return bounds;
    }
}
