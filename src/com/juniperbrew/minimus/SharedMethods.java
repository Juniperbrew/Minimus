package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.juniperbrew.minimus.server.ServerEntity;

import javax.sound.sampled.Line;
import java.awt.geom.Line2D;
import java.awt.image.ConvolveOp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class SharedMethods {

    static Timer timer = new Timer();

    public static ArrayList<Line2D.Float> createHitscanAttack(Weapon weapon, float x, float y, int deg, final ConcurrentLinkedQueue<Line2D.Float> attackVisuals){
        ArrayList<Line2D.Float> hitscans = new ArrayList<>();
        int range = weapon.range;
        int startDistanceX = 25;
        int startDistanceY = 25;

        deg -= weapon.spread/2f;
        for (int i = 0; i < weapon.projectileCount; i++) {
            float sina = MathUtils.sinDeg(deg);
            float cosa = MathUtils.cosDeg(deg);

            float startX = x + cosa*startDistanceX;
            float startY = y + sina*startDistanceY;
            float targetX = startX + cosa*range;
            float targetY = startY + sina*range;

            final Line2D.Float hitScan = new Line2D.Float(startX,startY,targetX,targetY);
            hitscans.add(hitScan);
            attackVisuals.add(hitScan);
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    attackVisuals.remove(hitScan);
                }
            };
            timer.schedule(task,Tools.secondsToMilli(weapon.visualDuration));

            if(weapon.projectileCount>1){
                deg += weapon.spread/(weapon.projectileCount-1);
            }
        }
        return hitscans;
    }

    public static ArrayList<Projectile> createProjectileAttack(Weapon weapon, float x, float y, int deg, int entityId, int team){
        ArrayList<Projectile> projectiles = new ArrayList<>();
        int projectileStartDistanceX = 25;
        int projectileStartDistanceY = 25;

        deg -= weapon.spread/2f;
        for (int i = 0; i < weapon.projectileCount; i++) {
            float sina = MathUtils.sinDeg(deg);
            float cosa = MathUtils.cosDeg(deg);

            float startX = x + cosa*projectileStartDistanceX;
            float startY = y + sina*projectileStartDistanceY;

            projectiles.add(new Projectile(startX,startY,weapon.range,weapon.velocity,deg,entityId,team,weapon.damage));
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

    public static void renderAttackVisuals(ShapeRenderer shapeRenderer, ConcurrentLinkedQueue<Line2D.Float> attackVisuals){
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1,0,0,1); //red
        for(Line2D.Float line:attackVisuals){
            //TODO null exception should be fixed here
            shapeRenderer.line(line.x1, line.y1, line.x2, line.y2);
        }
        shapeRenderer.end();
    }

    public static void renderProjectiles(ShapeRenderer shapeRenderer, ConcurrentLinkedQueue<Projectile> projectiles){
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0,1,0,1);
        for(Projectile projectile: projectiles){
            if(projectile!=null){
                shapeRenderer.circle(projectile.getX(),projectile.getY(),5);
            }
        }
        shapeRenderer.end();
    }
}
