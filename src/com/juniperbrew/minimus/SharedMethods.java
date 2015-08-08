package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

import java.util.ArrayList;
import java.util.Timer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class SharedMethods {

    static Timer timer = new Timer();

    public static ArrayList<Projectile> createProjectile(TextureAtlas atlas, Weapon weapon, float centerX, float centerY, int deg, int entityId, int team){
        ProjectileDefinition projectileDefinition = weapon.projectile;
        final ArrayList<Projectile> projectiles = new ArrayList<>();
        int length = projectileDefinition.length;
        int width = projectileDefinition.width;
        int startDistanceX = 25;

        deg -= weapon.spread/2f;
        for (int i = 0; i < weapon.projectileCount; i++) {

            Rectangle bounds = new Rectangle(centerX+startDistanceX,centerY-width/2,length,width);

            final Projectile projectile;
            if(projectileDefinition.image!=null){
                TextureRegion texture = atlas.findRegion(projectileDefinition.image);
                projectile = new Projectile(bounds,texture,deg,centerX,centerY,projectileDefinition.range,projectileDefinition.velocity,entityId,team,projectileDefinition.damage);
            }else if(projectileDefinition.animation!=null) {
                Animation animation = new Animation(projectileDefinition.frameDuration,atlas.findRegions(projectileDefinition.animation));
                projectile = new Projectile(bounds,animation,deg,centerX,centerY,projectileDefinition.range,projectileDefinition.velocity,entityId,team,projectileDefinition.damage);
            }else{
                    TextureRegion texture = atlas.findRegion("white");
                    Color color;
                    if(projectileDefinition.color==null){
                        color = new Color(MathUtils.random(),MathUtils.random(),MathUtils.random(),1);
                    }else{
                        color = projectileDefinition.color;
                    }
                    projectile = new Projectile(bounds,texture,deg,centerX,centerY,color,projectileDefinition.range,projectileDefinition.velocity,entityId,team,projectileDefinition.damage);
            }
            if(projectileDefinition.duration>0){
                projectile.setDuration(projectileDefinition.duration);
            }
            projectile.hitscan = projectileDefinition.hitscan;
            projectiles.add(projectile);

            if(weapon.projectileCount>1){
                deg += weapon.spread/(weapon.projectileCount-1);
            }
        }
        return projectiles;
    }

    public static void applyInput(Entity e, Network.UserInput input){
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
            if (e.getX() + e.width + deltaX > GlobalVars.mapWidth) {
                deltaX = GlobalVars.mapWidth - e.getX() - e.width;
            }
            if (e.getX() + deltaX < 0) {
                deltaX = 0 - e.getX();
            }
            if (e.getY() + e.height + deltaY > GlobalVars.mapHeight) {
                deltaY = GlobalVars.mapHeight - e.getY() - e.height;
            }
            if (e.getY() + deltaY < 0) {
                deltaY = 0 - e.getY();
            }
        }
        Rectangle bounds = e.getGdxBounds();
        bounds.setX(bounds.getX()+deltaX);
        if(SharedMethods.checkMapCollision(bounds)){
            bounds.setX(bounds.getX()-deltaX);
            deltaX = 0;
        }
        bounds.setY(bounds.getY()+deltaY);
        if(SharedMethods.checkMapCollision(bounds)){
            deltaY = 0;
        }
        e.move(deltaX,deltaY);
    }


    public static boolean checkMapCollision(Rectangle bounds){

        if(bounds.x<0||(bounds.x+bounds.width)>=GlobalVars.mapWidth||bounds.y<0|bounds.y+bounds.height>=GlobalVars.mapHeight){
            return false;
        }

        int minX = (int) Math.floor(bounds.x / GlobalVars.tileWidth);
        int maxX = (int) Math.floor((bounds.x + bounds.width) / GlobalVars.tileWidth);
        int minY = (int) Math.floor(bounds.y / GlobalVars.tileHeight);
        int maxY = (int) Math.floor((bounds.y+bounds.height) / GlobalVars.tileHeight);


        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if(GlobalVars.collisionMap[x][y])return true;
            }
        }
        return false;
    }

    public static boolean[][] createCollisionMap(TiledMap map, int mapWidth, int mapHeight){
        boolean[][] collisions = new boolean[mapWidth][mapHeight];
        for(TiledMapTileLayer layer :map.getLayers().getByType(TiledMapTileLayer.class)){
            for (int x = 0; x < layer.getWidth(); x++) {
                for (int y = 0; y < layer.getHeight(); y++) {
                    TiledMapTileLayer.Cell cell = layer.getCell(x,y);
                    if(cell!=null){
                        if(cell.getTile().getProperties().containsKey("solid")) {
                            collisions[x][y] = true;
                            System.out.println("Collision at ("+x+","+y+")");
                        }
                    }
                }
            }
        }
        return collisions;
    }

    public static void setRotation(NetworkEntity e, Network.UserInput input){
        float mouseX = input.mouseX;
        float mouseY = input.mouseY;
        float playerX = e.getX() + e.width/2;
        float playerY = e.getY() + e.height/2;
        float deltaX = mouseX - playerX;
        float deltaY = mouseY - playerY;
        int degrees = (int) (MathUtils.radiansToDegrees*MathUtils.atan2(deltaY,deltaX));
        e.setRotation(degrees);
    }

    public static void renderAttackBoundingBox(ShapeRenderer renderer, ConcurrentLinkedQueue<Projectile> projectiles) {
        renderer.begin(ShapeRenderer.ShapeType.Line);
        for(Projectile projectile:projectiles){
            Rectangle rect = projectile.getHitbox().getBoundingRectangle();
            renderer.rect(rect.x,rect.y,rect.width,rect.height);
        }
        renderer.end();
    }

    public static void renderAttackPolygon(ShapeRenderer renderer, ConcurrentLinkedQueue<Projectile> projectiles) {
        renderer.begin(ShapeRenderer.ShapeType.Line);
        for(Projectile projectile:projectiles){
            renderer.polygon(projectile.getHitbox().getTransformedVertices());
        }
        renderer.end();
    }

    public static void renderAttack(float delta, SpriteBatch batch, ConcurrentLinkedQueue<Projectile> projectiles){
        batch.begin();
        for(Projectile projectile:projectiles){
            projectile.render(batch, delta);
        }
        batch.end();
    }
}
