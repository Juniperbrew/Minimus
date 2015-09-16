package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class SharedMethods {

    public static ArrayList<Line2D.Float> createHitscan(Weapon weapon, float centerX, float centerY, int deg){
        ProjectileDefinition projectileDefinition = weapon.projectile;
        final ArrayList<Line2D.Float> hitscans = new ArrayList<>();
        //int startDistance = ConVars.getInt("sv_npc_default_size") / 2;
        int length = projectileDefinition.length;

        deg -= weapon.spread / 2f;
        for (int i = 0; i < weapon.projectileCount; i++) {
            float sina = MathUtils.sinDeg(deg);
            float cosa = MathUtils.cosDeg(deg);

            float startX = centerX;//+cosa*startDistance;
            float startY = centerY;//+sina*startDistance;
            float targetX = startX + cosa*length;
            float targetY = startY + sina*length;
            Line2D.Float line = new Line2D.Float(startX,startY,targetX,targetY);
            hitscans.add(line);

            if (weapon.projectileCount > 1) {
                deg += weapon.spread / (weapon.projectileCount - 1);
            }
        }

        return hitscans;
    }

    public static Particle createTracer(ProjectileDefinition def, Line2D.Float line){
        int rotation = (int)Tools.getAngle(line);
        int length = (int) Tools.getLength(line);
        Rectangle bounds = new Rectangle(line.x1,line.y1,length,def.width);
        return new Particle(def,bounds,rotation,line.x1,line.y1);
    }

    public static Particle createStationaryParticle(ProjectileDefinition def, float x, float y) {
        Rectangle bounds = new Rectangle(x-def.width/2, y-def.length/2, def.length, def.width);
        return new Particle(def,bounds);
    }

    public static Particle createRotatedParticle(ProjectileDefinition def, float x, float y, int rotation) {
        Rectangle bounds = new Rectangle(x-def.width/2, y-def.length/2, def.length, def.width);
        return new Particle(def,bounds,rotation,x,y,0);
    }

    public static Particle createMovingParticle(ProjectileDefinition def, float x, float y, int rotation, float velocity) {
        Rectangle bounds = new Rectangle(x-def.width/2, y-def.length/2, def.length, def.width);
        return new Particle(def,bounds,rotation,x,y, velocity);
    }

    public static Projectile createStationaryProjectile(ProjectileDefinition def, float x, float y, int entityId, int team) {
        Rectangle bounds = new Rectangle(x-def.width/2, y-def.length/2, def.length, def.width);
        return new Projectile(def, bounds, entityId, team);
    }

    public static ArrayList<Projectile> createProjectiles(Weapon weapon, float centerX, float centerY, int deg, int entityId, int team) {
        ProjectileDefinition def = weapon.projectile;
        final ArrayList<Projectile> projectiles = new ArrayList<>();
        int length = def.length;
        int width = def.width;
        int startDistanceX = ConVars.getInt("sv_npc_default_size") / 2;

        deg -= weapon.spread / 2f;
        for (int i = 0; i < weapon.projectileCount; i++) {

            Rectangle bounds = new Rectangle(centerX + startDistanceX, centerY - width / 2, length, width);

            Projectile p = new Projectile(def, bounds, deg, centerX, centerY, entityId, team);

            projectiles.add(p);

            if (weapon.projectileCount > 1) {
                deg += weapon.spread / (weapon.projectileCount - 1);
            }
        }
        return projectiles;
    }

    public static void applyInput(Entity e, Network.UserInput input) {
        float deltaX = 0;
        float deltaY = 0;
        float velocity = ConVars.getFloat("sv_player_velocity");

        setRotation(e, input);

        float delta = input.msec / 1000f;

        float distance = velocity * delta;
        int direction = e.getRotation();

        if (input.buttons.contains(Enums.Buttons.W)) {
            deltaX = MathUtils.cosDeg(direction) * distance;
            deltaY = MathUtils.sinDeg(direction) * distance;
        }
        if (input.buttons.contains(Enums.Buttons.S)) {
            deltaX += MathUtils.cosDeg(direction - 180) * distance;
            deltaY += MathUtils.sinDeg(direction - 180) * distance;
        }
        if (input.buttons.contains(Enums.Buttons.A)) {
            deltaX += MathUtils.cosDeg(direction + 90) * distance;
            deltaY += MathUtils.sinDeg(direction + 90) * distance;
        }
        if (input.buttons.contains(Enums.Buttons.D)) {
            deltaX += MathUtils.cosDeg(direction - 90) * distance;
            deltaY += MathUtils.sinDeg(direction - 90) * distance;
        }

        e.addMovement(new Vector2(deltaX,deltaY));
        e.applyMovement();
/*

        if (ConVars.getBool("sv_check_map_collisions")) {
            if (e.getX() + e.getWidth() + deltaX > GlobalVars.mapWidth) {
                deltaX = GlobalVars.mapWidth - e.getX() - e.getWidth();
            }
            if (e.getX() + deltaX < 0) {
                deltaX = 0 - e.getX();
            }
            if (e.getY() + e.getHeight() + deltaY > GlobalVars.mapHeight) {
                deltaY = GlobalVars.mapHeight - e.getY() - e.getHeight();
            }
            if (e.getY() + deltaY < 0) {
                deltaY = 0 - e.getY();
            }
        }
        Rectangle bounds = e.getGdxBounds();
        bounds.setX(bounds.getX() + deltaX);
        if (SharedMethods.checkMapCollision(bounds)) {
            bounds.setX(bounds.getX() - deltaX);
            deltaX = 0;
        }
        bounds.setY(bounds.getY() + deltaY);
        if (SharedMethods.checkMapCollision(bounds)) {
            deltaY = 0;
        }
        e.move(deltaX, deltaY);
        */
    }


    public static boolean checkMapCollision(Rectangle bounds) {

        int minX = (int) Math.floor(bounds.x / GlobalVars.tileWidth);
        int maxX = (int) Math.floor((bounds.x + bounds.width) / GlobalVars.tileWidth);
        int minY = (int) Math.floor(bounds.y / GlobalVars.tileHeight);
        int maxY = (int) Math.floor((bounds.y + bounds.height) / GlobalVars.tileHeight);


        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (isTileSolid(x, y)) return true;
            }
        }
        return false;
    }

    public static float checkMapAxisCollision(Rectangle bounds, boolean xAxis) {

        int minX = (int) Math.floor(bounds.x / GlobalVars.tileWidth);
        int maxX = (int) Math.floor((bounds.x + bounds.width) / GlobalVars.tileWidth);
        int minY = (int) Math.floor(bounds.y / GlobalVars.tileHeight);
        int maxY = (int) Math.floor((bounds.y + bounds.height) / GlobalVars.tileHeight);

        float correction = 0;
        float tempCorrection;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (isTileSolid(x, y)){
                    Rectangle tile = getTileBounds(x,y);
                    if(xAxis){
                        tempCorrection = Math.min(tile.x + tile.width, bounds.x + bounds.width) - Math.max(tile.x, bounds.x);
                    }else{
                        tempCorrection = Math.min(tile.y + tile.height, bounds.y + bounds.height) - Math.max(tile.y, bounds.y);
                    }
                    if( tempCorrection>correction){
                        correction =  tempCorrection;
                    }
                }
            }
        }
        if(correction>0){
            correction += 0.1f;
            if(xAxis){
                System.out.println("X axis correction:"+correction);
            }else{
                System.out.println("Y axis correction:"+correction);
            }
        }

        return correction;
    }

    private static Rectangle getTileBounds(int x, int y){
        return new Rectangle(x*GlobalVars.tileWidth,y*GlobalVars.tileHeight,GlobalVars.tileWidth,GlobalVars.tileHeight);
    }

    public static boolean isTileSolid(int x, int y) {
        if (x < 0 || x >= GlobalVars.mapWidthTiles || y < 0 || y >= GlobalVars.mapHeightTiles) {
            return false;
        }
        if (GlobalVars.collisionMap[x][y]) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isTileCollisionOnLine(float screenX0, float screenY0, float screenX1, float screenY1) {
        if (raytrace(screenX0, screenY0, screenX1, screenY1) != null) {
            return true;
        } else {
            return false;
        }
    }

    public static Vector2 getLineIntersectionWithRectangle(Line2D.Float line, Rectangle bounds){
        Vector2 intersection = new Vector2();
        Vector2 l1 = new Vector2(line.x1,line.y1);
        Vector2 l2 = new Vector2(line.x2,line.y2);
        Vector2 sw = new Vector2(bounds.x,bounds.y);
        Vector2 se = new Vector2(bounds.x+bounds.width,bounds.y);
        Vector2 ne = new Vector2(bounds.x+bounds.width,bounds.y+bounds.height);
        Vector2 nw = new Vector2(bounds.x,bounds.y+bounds.height);


        if(l1.y<=sw.y&&Intersector.intersectSegments(l1, l2, sw, se, intersection))return intersection;
        if(l1.x>=se.x&&Intersector.intersectSegments(l1, l2, se, ne, intersection))return intersection;
        if(l1.y>ne.y&&Intersector.intersectSegments(l1,l2,ne,nw,intersection))return intersection;
        if(l1.x<nw.x&&Intersector.intersectSegments(l1, l2, nw, sw, intersection))return intersection;

        return null;
    }

    public static Vector2 findLineIntersectionPointWithTile(float screenX0, float screenY0, float screenX1, float screenY1){
        Vector2 tile = raytrace(screenX0, screenY0, screenX1, screenY1);
        if(tile==null){
            return null;
        }
        Rectangle bounds = new Rectangle(tile.x*GlobalVars.tileWidth,tile.y*GlobalVars.tileHeight,GlobalVars.tileWidth,GlobalVars.tileHeight);
        Line2D.Float line = new Line2D.Float(screenX0,screenY0,screenX1,screenY1);
        return getLineIntersectionWithRectangle(line,bounds);
    }


    public static Vector2 raytraceInt(float screenX0, float screenY0, float screenX1, float screenY1) {
        int x0 = (int) (screenX0 / GlobalVars.tileWidth);
        int y0 = (int) (screenY0 / GlobalVars.tileHeight);
        int x1 = (int) (screenX1 / GlobalVars.tileWidth);
        int y1 = (int) (screenY1 / GlobalVars.tileHeight);

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int x = x0;
        int y = y0;
        int n = 1 + dx + dy;
        int x_inc = (x1 > x0) ? 1 : -1;
        int y_inc = (y1 > y0) ? 1 : -1;
        int error = dx - dy;
        dx *= 2;
        dy *= 2;


        for (; n > 0; --n) {
            if (isTileSolid(x, y)) {
                return new Vector2(x, y);
            }
            if (error > 0) {
                x += x_inc;
                error -= dy;
            } else {
                y += y_inc;
                error += dx;
            }
        }
        return null;
    }

    public static void debugDrawRaytrace(ShapeRenderer renderer, float screenX0, float screenY0, float screenX1, float screenY1){

        renderer.begin(ShapeRenderer.ShapeType.Line);
        renderer.setColor(0,1,0,1);
        double x0 = screenX0 / GlobalVars.tileWidth;
        double y0 = screenY0 / GlobalVars.tileHeight;
        double x1 = screenX1 / GlobalVars.tileWidth;
        double y1 = screenY1 / GlobalVars.tileHeight;

        double dx = Math.abs(x1 - x0);
        double dy = Math.abs(y1 - y0);

        int x = (int)(Math.floor(x0));
        int y = (int)(Math.floor(y0));

        int n = 1;
        int x_inc, y_inc;
        double error;

        if (dx == 0)
        {
            x_inc = 0;
            error = Double.POSITIVE_INFINITY;
        }
        else if (x1 > x0)
        {
            x_inc = 1;
            n += (int)(Math.floor(x1)) - x;
            error = (Math.floor(x0) + 1 - x0) * dy;
        }
        else
        {
            x_inc = -1;
            n += x - (int)(Math.floor(x1));
            error = (x0 - Math.floor(x0)) * dy;
        }

        if (dy == 0)
        {
            y_inc = 0;
            error -= Double.POSITIVE_INFINITY;
        }
        else if (y1 > y0)
        {
            y_inc = 1;
            n += (int)(Math.floor(y1)) - y;
            error -= (Math.floor(y0) + 1 - y0) * dx;
        }
        else
        {
            y_inc = -1;
            n += y - (int)(Math.floor(y1));
            error -= (y0 - Math.floor(y0)) * dx;
        }

        for (; n > 0; --n)
        {
            renderer.rect(x*GlobalVars.tileWidth,y*GlobalVars.tileHeight,GlobalVars.tileWidth,GlobalVars.tileHeight);

            if (error > 0)
            {
                y += y_inc;
                error -= dx;
            }
            else
            {
                x += x_inc;
                error += dy;
            }
        }
        renderer.end();
    }

    public static Vector2 debugRaytrace(float screenX0, float screenY0, float screenX1, float screenY1)
    {

        System.out.println("sX0:"+screenX0);
        System.out.println("sY0:"+screenY0);
        System.out.println("sX1:"+screenX1);
        System.out.println("sY1:"+screenY1);
        double x0 = screenX0 / GlobalVars.tileWidth;
        double y0 = screenY0 / GlobalVars.tileHeight;
        double x1 = screenX1 / GlobalVars.tileWidth;
        double y1 = screenY1 / GlobalVars.tileHeight;
        System.out.println("X0:"+x0);
        System.out.println("Y0:"+y0);
        System.out.println("X1:"+x1);
        System.out.println("Y1:"+y1);

        double dx = Math.abs(x1 - x0);
        double dy = Math.abs(y1 - y0);

        System.out.println("dx:"+dx);
        System.out.println("dy:"+dy);

        int x = (int)(Math.floor(x0));
        int y = (int)(Math.floor(y0));

        int n = 1;
        int x_inc, y_inc;
        double error;

        if (dx == 0)
        {
            x_inc = 0;
            error = Double.POSITIVE_INFINITY;
        }
        else if (x1 > x0)
        {
            x_inc = 1;
            n += (int)(Math.floor(x1)) - x;
            error = (Math.floor(x0) + 1 - x0) * dy;
        }
        else
        {
            x_inc = -1;
            n += x - (int)(Math.floor(x1));
            error = (x0 - Math.floor(x0)) * dy;
        }
        System.out.println("error(afterX):"+error);

        if (dy == 0)
        {
            y_inc = 0;
            error -= Double.POSITIVE_INFINITY;
        }
        else if (y1 > y0)
        {
            y_inc = 1;
            n += (int)(Math.floor(y1)) - y;
            error -= (Math.floor(y0) + 1 - y0) * dx;
        }
        else
        {
            y_inc = -1;
            n += y - (int)(Math.floor(y1));
            error -= (y0 - Math.floor(y0)) * dx;
        }

        System.out.println("x_inc:"+x_inc);
        System.out.println("x_inc:"+y_inc);

        for (; n > 0; --n)
        {
            System.out.println("n:"+n);
            System.out.println("x:"+x);
            System.out.println("y:"+y);
            System.out.println("error:"+error);
            if (isTileSolid(x, y)) {
                System.out.println("COLLISION");
                return new Vector2(x, y);
            }

            if (error > 0)
            {
                y += y_inc;
                error -= dx;
            }
            else
            {
                x += x_inc;
                error += dy;
            }
        }
        System.out.println("NO COLLISION");
        return null;
    }

    public static Vector2 raytrace(float screenX0, float screenY0, float screenX1, float screenY1)
    {

        double x0 = screenX0 / GlobalVars.tileWidth;
        double y0 = screenY0 / GlobalVars.tileHeight;
        double x1 = screenX1 / GlobalVars.tileWidth;
        double y1 = screenY1 / GlobalVars.tileHeight;

        double dx = Math.abs(x1 - x0);
        double dy = Math.abs(y1 - y0);

        int x = (int)(Math.floor(x0));
        int y = (int)(Math.floor(y0));

        int n = 1;
        int x_inc, y_inc;
        double error;

        if (dx == 0)
        {
            x_inc = 0;
            error = Double.POSITIVE_INFINITY;
        }
        else if (x1 > x0)
        {
            x_inc = 1;
            n += (int)(Math.floor(x1)) - x;
            error = (Math.floor(x0) + 1 - x0) * dy;
        }
        else
        {
            x_inc = -1;
            n += x - (int)(Math.floor(x1));
            error = (x0 - Math.floor(x0)) * dy;
        }

        if (dy == 0)
        {
            y_inc = 0;
            error -= Double.POSITIVE_INFINITY;
        }
        else if (y1 > y0)
        {
            y_inc = 1;
            n += (int)(Math.floor(y1)) - y;
            error -= (Math.floor(y0) + 1 - y0) * dx;
        }
        else
        {
            y_inc = -1;
            n += y - (int)(Math.floor(y1));
            error -= (y0 - Math.floor(y0)) * dx;
        }

        for (; n > 0; --n)
        {
            if (isTileSolid(x, y)) {
                return new Vector2(x, y);
            }

            if (error > 0)
            {
                y += y_inc;
                error -= dx;
            }
            else
            {
                x += x_inc;
                error += dy;
            }
        }
        return null;
    }

    public static boolean[][] createCollisionMap(TiledMap map, int mapWidth, int mapHeight) {
        boolean[][] collisions = new boolean[mapWidth][mapHeight];
        for (TiledMapTileLayer layer : map.getLayers().getByType(TiledMapTileLayer.class)) {
            for (int x = 0; x < layer.getWidth(); x++) {
                for (int y = 0; y < layer.getHeight(); y++) {
                    TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                    if (cell != null) {
                        if (cell.getTile().getProperties().containsKey("solid")) {
                            collisions[x][y] = true;
                        }
                    }
                }
            }
        }
        return collisions;
    }

    public static void setRotation(Entity e, Network.UserInput input) {
        float mouseX = input.mouseX;
        float mouseY = input.mouseY;
        float playerX = e.getX() + e.getWidth() / 2;
        float playerY = e.getY() + e.getHeight() / 2;
        float deltaX = mouseX - playerX;
        float deltaY = mouseY - playerY;
        int degrees = (int) (MathUtils.radiansToDegrees * MathUtils.atan2(deltaY, deltaX));
        e.setRotation(degrees);
    }

    public static void renderAttackBoundingBox(ShapeRenderer renderer, ConcurrentLinkedQueue<Projectile> projectiles) {
        renderer.begin(ShapeRenderer.ShapeType.Line);
        for (Projectile projectile : projectiles) {
            Rectangle rect = projectile.getHitbox().getBoundingRectangle();
            renderer.rect(rect.x, rect.y, rect.width, rect.height);
        }
        renderer.end();
    }

    public static void renderAttackPolygon(ShapeRenderer renderer, ConcurrentLinkedQueue<Projectile> projectiles) {
        renderer.begin(ShapeRenderer.ShapeType.Line);
        for (Projectile projectile : projectiles) {
            renderer.polygon(projectile.getHitbox().getTransformedVertices());
        }
        renderer.end();
    }

    public static void renderParticles(float delta, SpriteBatch batch, ConcurrentLinkedQueue<? extends Particle> particles) {
        batch.begin();
        for (Particle particle : particles) {
            particle.render(batch, delta);
        }
        batch.end();
    }

    public static float getMapScale(TiledMap map) {
        String scale = map.getProperties().get("mapScale", String.class);
        if (scale != null) {
            return Float.parseFloat(scale);
        } else {
            return 1;
        }
    }

    //TODO inefficient lookup of weapon by name
    public static int getWeaponID(HashMap<Integer,Weapon> weaponList, String name){
        for(int weaponID:weaponList.keySet()){
            Weapon w = weaponList.get(weaponID);
            if(w.name.equals(name)){
                return weaponID;
            }
        }
        return -1;
    }

    public static ArrayList<Integer> getEntitiesWithinRange(Collection<? extends Entity> entities, Entity origin, float range){
        double squaredDistance = Math.pow(range,2);
        ArrayList<Integer> entityIDs = new ArrayList<>();
        for(Entity e:entities){
            float distance = Tools.getSquaredDistance(origin.getCenterX(),origin.getCenterY(),e.getCenterX(),e.getCenterY());
            if(distance<squaredDistance){
                if(origin.getID() != e.getID()){
                    entityIDs.add(e.getID());
                }
            }
        }
        return entityIDs;
    }
}
