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
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayList;
import java.util.Timer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 24.6.2015.
 */
public class SharedMethods {

    public static ArrayList<Projectile> createProjectile(TextureAtlas atlas, Weapon weapon, float centerX, float centerY, int deg, int entityId, int team) {
        ProjectileDefinition projectileDefinition = weapon.projectile;
        final ArrayList<Projectile> projectiles = new ArrayList<>();
        int length = projectileDefinition.length;
        int width = projectileDefinition.width;
        int startDistanceX = ConVars.getInt("sv_npc_default_size") / 2;

        deg -= weapon.spread / 2f;
        for (int i = 0; i < weapon.projectileCount; i++) {

            Rectangle bounds = new Rectangle(centerX + startDistanceX, centerY - width / 2, length, width);

            final Projectile projectile;
            if (projectileDefinition.image != null) {
                TextureRegion texture = atlas.findRegion(projectileDefinition.image);
                projectile = new Projectile(bounds, texture, deg, centerX, centerY, projectileDefinition.range, projectileDefinition.velocity, entityId, team, projectileDefinition.damage);
            } else if (projectileDefinition.animation != null) {
                Animation animation = new Animation(projectileDefinition.frameDuration, atlas.findRegions(projectileDefinition.animation));
                projectile = new Projectile(bounds, animation, deg, centerX, centerY, projectileDefinition.range, projectileDefinition.velocity, entityId, team, projectileDefinition.damage);
            } else {
                TextureRegion texture = atlas.findRegion("blank");
                Color color;
                if (projectileDefinition.color == null) {
                    color = new Color(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1);
                } else {
                    color = projectileDefinition.color;
                }
                projectile = new Projectile(bounds, texture, deg, centerX, centerY, color, projectileDefinition.range, projectileDefinition.velocity, entityId, team, projectileDefinition.damage);
            }
            if (projectileDefinition.duration > 0) {
                projectile.setDuration(projectileDefinition.duration);
            }
            projectile.hitscan = projectileDefinition.hitscan;
            projectiles.add(projectile);

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


        if (ConVars.getBool("sv_check_map_collisions")) {
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
        if (getFirstCollisionTileOnLine(screenX0, screenY0, screenX1, screenY1) != null) {
            return true;
        } else {
            return false;
        }
    }


    public static Vector2 getFirstCollisionTileOnLine(float screenX0, float screenY0, float screenX1, float screenY1) {
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

    public static void setRotation(NetworkEntity e, Network.UserInput input) {
        float mouseX = input.mouseX;
        float mouseY = input.mouseY;
        float playerX = e.getX() + e.width / 2;
        float playerY = e.getY() + e.height / 2;
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

    public static void renderAttack(float delta, SpriteBatch batch, ConcurrentLinkedQueue<Projectile> projectiles) {
        batch.begin();
        for (Projectile projectile : projectiles) {
            projectile.render(batch, delta);
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
}
