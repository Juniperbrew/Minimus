package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class SharedMethods {

    static Timer timer = new Timer();

    /*
    public static ArrayList<AttackVisual> createAttack(Weapon weapon, float centerX, float centerY, int deg, final ConcurrentLinkedQueue<AttackVisual> attackVisuals){

        ProjectileDefinition projectileDefinition = weapon.projectile;
        if(projectileDefinition.hitscan){
            ArrayList<AttackVisual> hitscans = new ArrayList<>();
            int length = projectileDefinition.length;
            int width = projectileDefinition.width;
            int startDistanceX = 25;
            int startDistanceY = 25;

            deg -= weapon.spread/2f;
            for (int i = 0; i < weapon.projectileCount; i++) {

                Rectangle2D.Float bounds = new Rectangle2D.Float(centerX+startDistanceX,centerY-width/2,length,width);
                final AttackVisual hitscan = new AttackVisual(bounds,deg,centerX,centerY,projectileDefinition.color);

                hitscans.add(hitscan);
                attackVisuals.add(hitscan);
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        attackVisuals.remove(hitscan);
                    }
                };
                timer.schedule(task,Tools.secondsToMilli(projectileDefinition.duration));

                if(weapon.projectileCount>1){
                    deg += weapon.spread/(weapon.projectileCount-1);
                }
            }
        }
    }*/

    public static ArrayList<AttackVisual> createHitscanAttack(HashMap<String,Texture> textureList, Weapon weapon, float centerX, float centerY, int deg, final ConcurrentLinkedQueue<AttackVisual> attackVisuals){
        ProjectileDefinition projectileDefinition = weapon.projectile;
        ArrayList<AttackVisual> hitscans = new ArrayList<>();
        int length = projectileDefinition.length;
        int width = projectileDefinition.width;
        int startDistanceX = 25;

        deg -= weapon.spread/2f;
        for (int i = 0; i < weapon.projectileCount; i++) {
            Rectangle bounds = new Rectangle(centerX+startDistanceX,centerY-width/2,length,width);
            Color color;
            if(projectileDefinition.color==null){
                color = new Color(MathUtils.random(),MathUtils.random(),MathUtils.random(),1);
            }else{
                color = projectileDefinition.color;
            }

            final AttackVisual hitscan = new AttackVisual(bounds,deg,centerX,centerY,color);

            hitscans.add(hitscan);
            attackVisuals.add(hitscan);
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    attackVisuals.remove(hitscan);
                }
            };
            timer.schedule(task,Tools.secondsToMilli(projectileDefinition.duration));

            if(weapon.projectileCount>1){
                deg += weapon.spread/(weapon.projectileCount-1);
            }
        }
        return hitscans;
    }

    public static ArrayList<Projectile> createProjectileAttack(HashMap<String,Texture> textureList, Weapon weapon, float centerX, float centerY, int deg, int entityId, int team){
        ProjectileDefinition projectileDefinition = weapon.projectile;
        ArrayList<Projectile> projectiles = new ArrayList<>();
        int length = projectileDefinition.length;
        int width = projectileDefinition.width;
        int startDistanceX = 25;

        deg -= weapon.spread/2f;
        for (int i = 0; i < weapon.projectileCount; i++) {

            Rectangle bounds = new Rectangle(centerX+startDistanceX,centerY-width/2,length,width);

            Projectile projectile;
            if(projectileDefinition.image!=null){
                Texture texture = textureList.get(projectileDefinition.image);
                projectile = new Projectile(bounds,texture,deg,centerX,centerY,projectileDefinition.range,projectileDefinition.velocity,entityId,team,projectileDefinition.damage);
            }else{
                Color color;
                if(projectileDefinition.color==null){
                    color = new Color(MathUtils.random(),MathUtils.random(),MathUtils.random(),1);
                }else{
                    color = projectileDefinition.color;
                }
                projectile = new Projectile(bounds,deg,centerX,centerY,color,projectileDefinition.range,projectileDefinition.velocity,entityId,team,projectileDefinition.damage);
            }

            projectiles.add(projectile);
            if(weapon.projectileCount>1){
                deg += weapon.spread/(weapon.projectileCount-1);
            }
        }
        return projectiles;
    }

    public static void applyInput(Entity e, Network.UserInput input, int mapWidth, int mapHeight){
        float deltaX = 0;
        float deltaY = 0;
        float velocity = ConVars.getFloat("sv_player_velocity");

        setRotation(e, input);

        float delta = input.msec/1000f;

        float distance = velocity * delta;
        int direction = e.getRotation();

        if(input.buttons.contains(Enums.Buttons.W)){
            deltaX = MathUtils.cosDeg(direction)*distance;
            deltaY = MathUtils.sinDeg(direction)*distance;
        }
        if(input.buttons.contains(Enums.Buttons.S)){
            deltaX += MathUtils.cosDeg(direction-180)*distance;
            deltaY += MathUtils.sinDeg(direction-180)*distance;
        }
        if(input.buttons.contains(Enums.Buttons.A)){
            deltaX += MathUtils.cosDeg(direction+90)*distance;
            deltaY += MathUtils.sinDeg(direction+90)*distance;
        }
        if(input.buttons.contains(Enums.Buttons.D)){
            deltaX += MathUtils.cosDeg(direction-90)*distance;
            deltaY += MathUtils.sinDeg(direction-90)*distance;
        }


        if(ConVars.getBool("sv_check_map_collisions")) {
            if (e.getX() + e.width + deltaX > mapWidth) {
                deltaX = mapWidth - e.getX() - e.width;
            }
            if (e.getX() + deltaX < 0) {
                deltaX = 0 - e.getX();
            }
            if (e.getY() + e.height + deltaY > mapHeight) {
                deltaY = mapHeight - e.getY() - e.height;
            }
            if (e.getY() + deltaY < 0) {
                deltaY = 0 - e.getY();
            }
        }

        float newX = e.getX()+deltaX;
        float newY = e.getY()+deltaY;
        e.moveTo(newX,newY);
    }

    public static void setRotation(Entity e, Network.UserInput input){
        float mouseX = input.mouseX;
        float mouseY = input.mouseY;
        float playerX = e.getX() + e.width/2;
        float playerY = e.getY() + e.height/2;
        float deltaX = mouseX - playerX;
        float deltaY = mouseY - playerY;
        int degrees = (int) (MathUtils.radiansToDegrees*MathUtils.atan2(deltaY,deltaX));
        e.setRotation(degrees);
    }

    public static void renderAttack(SpriteBatch batch, ShapeRenderer renderer, ConcurrentLinkedQueue<? extends AttackVisual> attackVisuals){
        for(AttackVisual attackVisual:attackVisuals){
            if(attackVisual.sprite!=null){
                attackVisual.render(batch);
            }else{
                attackVisual.render(renderer);
            }
        }
    }

    /*public static void renderAttackVisuals(ShapeRenderer shapeRenderer, ConcurrentLinkedQueue<AttackVisual> attackVisuals){
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for(AttackVisual attackVisual:attackVisuals){
            //TODO null exception should be fixed here
            attackVisual.render(shapeRenderer);
        }
        shapeRenderer.end();
    }

    public static void renderProjectiles(ShapeRenderer shapeRenderer, ConcurrentLinkedQueue<Projectile> projectiles){
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for(Projectile projectile: projectiles){
            if(projectile!=null){
                projectile.render(shapeRenderer);
            }
        }
        shapeRenderer.end();
    }*/
}
