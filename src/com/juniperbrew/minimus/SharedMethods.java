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

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class SharedMethods {

    public static final String VERSION_NAME = "Projectiles";
    ConVars conVars;
    int mapWidth;
    int mapHeight;
    Timer timer;

    public SharedMethods(ConVars conVars, int mapWidth, int mapHeight){
        this.conVars = conVars;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        timer = new Timer();
    }

    public ArrayList<Line2D.Float> createHitscanAttack(Weapon weapon, float x, float y, int deg, final ArrayList<Line2D.Float> attackVisuals){
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

    /*public Line2D.Float createLaserAttackVisual(float x, float y, int deg, final ArrayList<Line2D.Float> attackVisuals){

        float laserLength = conVars.getFloat("sv_laser_length");
        int laserStartDistanceX = 25;
        int laserStartDistanceY = 25;

        float sina = MathUtils.sinDeg(deg);
        float cosa = MathUtils.cosDeg(deg);

        x += cosa*laserStartDistanceX;
        y += sina*laserStartDistanceY;
        float targetX = x + cosa*laserLength;
        float targetY = y + sina*laserLength;

        final Line2D.Float hitScan = new Line2D.Float(x,y,targetX,targetY);
        attackVisuals.add(hitScan);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                attackVisuals.remove(hitScan);
            }
        };
        timer.schedule(task,Tools.secondsToMilli(conVars.getDouble("sv_attack_visual_timer")));
        return hitScan;
    }*/

    public ArrayList<Projectile> createProjectileAttack(Weapon weapon, float x, float y, int deg, int entityId, int team){
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

    public String getWeaponName(int weapon){
        switch (weapon){
            case 0: return "Laser";
            case 1: return "Rifle";
            case 2: return "Shotgun";
            default: return null;
        }
    }

    public void applyInput(Entity e, Network.UserInput input){
        float deltaX = 0;
        float deltaY = 0;
        float velocity = conVars.getFloat("sv_player_velocity");

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


        if(conVars.getBool("sv_check_map_collisions")) {
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

    public void setRotation(Entity e, Network.UserInput input){
        float mouseX = input.mouseX;
        float mouseY = input.mouseY;
        float playerX = e.getX() + e.width/2;
        float playerY = e.getY() + e.height/2;
        float deltaX = mouseX - playerX;
        float deltaY = mouseY - playerY;
        int degrees = (int) (MathUtils.radiansToDegrees*MathUtils.atan2(deltaY,deltaX));
        e.setRotation(degrees);
    }

    public void setCompassHeading(Entity e, EnumSet<Enums.Buttons> buttons){

        if(buttons.contains(Enums.Buttons.UP)){
            if(buttons.contains(Enums.Buttons.LEFT)&&e.getHeading()== Enums.Heading.WEST){
                e.setHeading(Enums.Heading.WEST);
            }else if(buttons.contains(Enums.Buttons.RIGHT)&&e.getHeading()== Enums.Heading.EAST){
                e.setHeading(Enums.Heading.EAST);
            }else{
                e.setHeading(Enums.Heading.NORTH);
            }
        }

        if(buttons.contains(Enums.Buttons.DOWN)){
            if(buttons.contains(Enums.Buttons.LEFT)&&e.getHeading()== Enums.Heading.WEST){
                e.setHeading(Enums.Heading.WEST);
            }else if(buttons.contains(Enums.Buttons.RIGHT)&&e.getHeading()== Enums.Heading.EAST){
                e.setHeading(Enums.Heading.EAST);
            }else{
                e.setHeading(Enums.Heading.SOUTH);
            }
        }

        if(buttons.contains(Enums.Buttons.LEFT)){
            if(buttons.contains(Enums.Buttons.UP)&&e.getHeading()== Enums.Heading.NORTH){
                e.setHeading(Enums.Heading.NORTH);
            }else if(buttons.contains(Enums.Buttons.DOWN)&&e.getHeading()== Enums.Heading.SOUTH){
                e.setHeading(Enums.Heading.SOUTH);
            }else{
                e.setHeading(Enums.Heading.WEST);
            }
        }

        if(buttons.contains(Enums.Buttons.RIGHT)){
            if(buttons.contains(Enums.Buttons.UP)&&e.getHeading()== Enums.Heading.NORTH){
                e.setHeading(Enums.Heading.NORTH);
            }else if(buttons.contains(Enums.Buttons.DOWN)&&e.getHeading()== Enums.Heading.SOUTH){
                e.setHeading(Enums.Heading.SOUTH);
            }else{
                e.setHeading(Enums.Heading.EAST);
            }
        }
    }

    public void renderAttackVisuals(ShapeRenderer shapeRenderer, ArrayList<Line2D.Float> attackVisuals){
        Line2D.Float[] attackVisualsCopy = attackVisuals.toArray(new Line2D.Float[attackVisuals.size()]);
        if(attackVisualsCopy.length>0){
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(1,0,0,1); //red
            for(Line2D.Float line:attackVisualsCopy){
                //TODO getting nullpointers here when spamming attack for some time
                shapeRenderer.line(line.x1, line.y1, line.x2, line.y2);
            }
            shapeRenderer.end();
        }
    }

    public void renderProjectiles(ShapeRenderer shapeRenderer, ArrayList<Projectile> projectiles){
        Projectile[] projectilesCopy = projectiles.toArray(new Projectile[projectiles.size()]);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0,1,0,1);
        for(Projectile projectile: projectilesCopy){
            if(projectile!=null){
                shapeRenderer.circle(projectile.getX(),projectile.getY(),5);
            }
        }
        shapeRenderer.end();
    }
}
