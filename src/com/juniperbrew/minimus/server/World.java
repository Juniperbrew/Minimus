package com.juniperbrew.minimus.server;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.*;
import com.esotericsoftware.kryonet.Connection;
import com.juniperbrew.minimus.*;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.components.Health;
import com.juniperbrew.minimus.components.Position;
import com.juniperbrew.minimus.components.Rotation;
import com.juniperbrew.minimus.components.Team;

import java.awt.geom.Line2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 1.8.2015.
 */
public class World implements EntityChangeListener{

    private final float KNOCKBACK_VELOCITY = 100;

    Set<Integer> playerList = new HashSet<>();
    Map<Integer,Map<Integer,Double>> attackCooldown = new HashMap<>();
    Map<Integer,HashMap<Integer,Integer>> playerAmmo = new HashMap<>();
    Map<Integer,HashMap<Integer,Boolean>> playerWeapons = new HashMap<>();
    Map<Integer,Integer> playerLives = new HashMap<>();

    ConcurrentHashMap<Integer,ServerEntity> entities = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer,Powerup> powerups = new ConcurrentHashMap<>();
    HashMap<Integer,EntityAI> entityAIs = new HashMap<>();
    ArrayList<Knockback> knockbacks = new ArrayList<>();

    Set<Integer> posChangedEntities = new HashSet<>();
    Set<Integer> healthChangedEntities = new HashSet<>();
    Set<Integer> rotationChangedEntities = new HashSet<>();
    Set<Integer> teamChangedEntities = new HashSet<>();

    ArrayList<Integer> pendingEntityRemovals = new ArrayList<>();

    private int networkIDCounter = 1;

    private ConcurrentLinkedQueue<Projectile> projectiles = new ConcurrentLinkedQueue<>();

    int mapWidth;
    int mapHeight;

    TiledMap map;
    String mapName;
    private int wave;
    private int spawnedHealthPacksCounter;
    private int spawnedEnemiesCounter;
    private int waveEnemyCount;
    private int waveHealthPackCount;
    private long lastHealthPackSpawned;
    private long lastEnemySpawned;
    private final int HEALTHPACK_SPAWN_DELAY = 10;
    private final int ENEMY_SPAWN_DELAY = 1;

    HashMap<Integer,WaveDefinition> waveList;
    HashMap<Integer,Weapon> weaponList;
    HashMap<String,ProjectileDefinition> projectileList;

    //This should not be more than 400 which is the max distance entities can look for a destination
    final int SPAWN_AREA_WIDTH = 200;
    public boolean spawnWaves;
    WorldChangeListener listener;

    Timer timer = new Timer();

    TextureAtlas atlas;
    TmxMapLoader mapLoader;
    String pendingMap;

    HashMap<String,TiledMap> mapList = new HashMap<>();
    HashMap<TiledMap,ArrayList<RectangleMapObject>> mapObjects = new HashMap<>();
    OrthogonalTiledMapRenderer mapRenderer;
    SpriteBatch batch;

    ArrayList<Rectangle> enemySpawnZones;

   public World(WorldChangeListener listener, TmxMapLoader mapLoader, SpriteBatch batch){
        this.listener = listener;
        this.mapLoader = mapLoader;
        this.batch = batch;
        waveList = readWaveList();
        loadMaps();
        projectileList = readProjectileList();
        weaponList = readWeaponList(projectileList);
        loadImages();

        changeMap(ConVars.get("sv_map"));
    }

    private TiledMap loadMap(String mapName){
        listener.message("Loading map: "+mapName);
        return mapLoader.load(GlobalVars.mapFolder+File.separator+mapName+File.separator+mapName+".tmx");
    }

    public void changeMap(String mapName){
        map = mapList.get(mapName);
        this.mapName = mapName;
        float mapScale = SharedMethods.getMapScale(map);

        GlobalVars.mapWidthTiles = map.getProperties().get("width",Integer.class);
        GlobalVars.mapHeightTiles = map.getProperties().get("height",Integer.class);
        GlobalVars.tileWidth = (int) (map.getProperties().get("tilewidth",Integer.class)* mapScale);
        GlobalVars.tileHeight = (int) (map.getProperties().get("tileheight",Integer.class)* mapScale);

        mapHeight = GlobalVars.mapHeightTiles * GlobalVars.tileHeight;
        mapWidth = GlobalVars.mapWidthTiles * GlobalVars.tileWidth;
        GlobalVars.mapWidth = mapHeight;
        GlobalVars.mapHeight = mapWidth;

        GlobalVars.collisionMap = SharedMethods.createCollisionMap(map, GlobalVars.mapWidthTiles, GlobalVars.mapHeightTiles);
        movePlayersToSpawn();

        removeAllNpcs();
        powerups.clear();
        projectiles.clear();

        enemySpawnZones = getEnemySpawnZones(map);
        spawnMapPowerups(map);
        spawnMapEnemies(map);

        mapRenderer = new OrthogonalTiledMapRenderer(map,mapScale,batch);
        listener.mapChanged(mapName);
    }

    private void spawnMapPowerups(TiledMap map){
        for(RectangleMapObject o : mapObjects.get(map)){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("powerup")){
                Rectangle r = o.getRectangle();
                MapProperties p = o.getProperties();
                if(p.containsKey("weapon")){
                    int typeModifier = Integer.parseInt(p.get("weapon", String.class));
                    spawnPowerup(new Powerup(r.x, r.y, r.width, r.height, Powerup.WEAPON, typeModifier, -1));
                }else if(p.containsKey("health")){
                    int value = Integer.parseInt(p.get("value",String.class));
                    spawnPowerup(new Powerup(r.x, r.y, r.width, r.height, Powerup.HEALTH, -1, value));
                }else if(p.containsKey("ammo")){
                    int typeModifier = Integer.parseInt(p.get("ammo", String.class));
                    int value = Integer.parseInt(p.get("value",String.class));
                    spawnPowerup(new Powerup(r.x, r.y, r.width, r.height, Powerup.AMMO, typeModifier, value));
                }
            }
        }
    }

    private void spawnMapEnemies(TiledMap map){
        for(RectangleMapObject o : mapObjects.get(map)){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("enemy")){
                Rectangle r = o.getRectangle();
                MapProperties p = o.getProperties();
                int aiType = -1;
                int weapon = Integer.parseInt(p.get("weapon", String.class));
                String image = p.get("image", String.class);
                switch (p.get("aiType",String.class)){
                    case "moving":aiType=EntityAI.MOVING; break;
                    case "following":aiType=EntityAI.FOLLOWING; break;
                    case "movingAndShooting":aiType=EntityAI.MOVING_AND_SHOOTING; break;
                    case "followingAndShooting":aiType=EntityAI.FOLLOWING_AND_SHOOTING; break;
                }
                addNPC(r, aiType, weapon, image);
            }
        }
    }

    private ArrayList<RectangleMapObject> getScaledMapobjects(TiledMap map){
        float s = SharedMethods.getMapScale(map);
        ArrayList<RectangleMapObject> mapObjects = new ArrayList<>();
        for(MapLayer layer :map.getLayers().getByType(MapLayer.class)){
            for(MapObject o : layer.getObjects()){
                if(o instanceof RectangleMapObject){
                    RectangleMapObject rectObject = (RectangleMapObject) o;
                    Rectangle bounds = rectObject.getRectangle();
                    rectObject.getRectangle().set(bounds.x*s,bounds.y*s,bounds.width*s,bounds.height*s);
                    mapObjects.add(rectObject);
                }
            }
        }
        return mapObjects;
    }

    public void updateWorld(float delta){

        for(int id: pendingEntityRemovals){
            removeEntity(id);
        }
        pendingEntityRemovals.clear();

        updateGameState();
        updateEntities(delta);
        updateProjectiles(delta);
        //checkPlayerEntityCollisions();
        checkEntityCollisions();
        checkPowerupCollisions();
    }

    private void updateGameState(){
        if(spawnWaves) {
            if (entityAIs.isEmpty() && (spawnedEnemiesCounter == waveEnemyCount)) {
                spawnedHealthPacksCounter = 0;
                spawnedEnemiesCounter = 0;
                setWave(wave + 1);
                waveEnemyCount = 3 + 2 * wave;
                waveHealthPackCount = (int) Math.sqrt(wave);
            }
            if (spawnedHealthPacksCounter < waveHealthPackCount) {
                if (System.nanoTime() - lastHealthPackSpawned > Tools.secondsToNano(HEALTHPACK_SPAWN_DELAY)) {
                    lastHealthPackSpawned = System.nanoTime();
                    spawnedHealthPacksCounter++;
                    spawnPowerup(Powerup.HEALTH, 30, 30);
                }
            }
            if (spawnedEnemiesCounter < waveEnemyCount) {
                if (System.nanoTime() - lastEnemySpawned > Tools.secondsToNano(ENEMY_SPAWN_DELAY)) {
                    lastEnemySpawned = System.nanoTime();
                    spawnedEnemiesCounter++;
                    addRandomNPC();
                }
            }
        }else{
            if (entityAIs.isEmpty()){
                String nextMap = map.getProperties().get("nextLevel",String.class);
                if(nextMap!=null){
                    changeMap(nextMap);
                }
            }
        }
    }

        /*
    private void spawnNextCustomWave(){

        WaveDefinition waveDef = waveList.get(wave+1);
        if(waveDef!=null){
            if(waveDef.map!=null){
                changeMap(waveDef.map);
            }
            setWave(wave+1);

            for(WaveDefinition.EnemyDefinition enemy:waveDef.enemies){
                for (int i = 0; i < enemy.count; i++) {
                    switch (enemy.aiType){
                        case "a": addNPC(EntityAI.MOVING,enemy.weapon); break;
                        case "b": addNPC(EntityAI.FOLLOWING,enemy.weapon); break;
                        case "c": addNPC(EntityAI.MOVING_AND_SHOOTING,enemy.weapon); break;
                        case "d": addNPC(EntityAI.FOLLOWING_AND_SHOOTING,enemy.weapon); break;
                    }
                }
            }
        }else{
            spawnNextWave();
        }

    }
*/
    /*
    private void spawnNextWave(){
        setWave(wave + 1);
        for (int i = 0; i < (wave*2)+4; i++) {
            addRandomNPC();
        }
    }*/

    private void movePlayerToSpawn(int id){
        Rectangle spawnZone = getSpawnZone(map);
        ServerEntity e = entities.get(id);
        if(spawnZone!=null){
            e.moveTo(MathUtils.random(spawnZone.getX(),spawnZone.getX()+spawnZone.getWidth()-e.width),MathUtils.random(spawnZone.getY(),spawnZone.getY()+spawnZone.getHeight()-e.height));
        }else{
            e.moveTo(MathUtils.random(mapWidth-e.width),MathUtils.random(mapHeight-e.height));
        }
    }

    private void movePlayersToSpawn(){
        for(int id:playerList){
            movePlayerToSpawn(id);
        }
    }

    private Rectangle getSpawnZone(TiledMap map){
        for(RectangleMapObject o : mapObjects.get(map)){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("spawn")){
                listener.message("Spawn at:"+o.getRectangle());
                return o.getRectangle();
            }
        }
        return null;
    }

    private ArrayList<Rectangle> getEnemySpawnZones(TiledMap map){
        ArrayList<Rectangle> zones = new ArrayList<>();
        for(RectangleMapObject o : mapObjects.get(map)){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("enemySpawn")){
                RectangleMapObject rect = (RectangleMapObject) o;
                zones.add(rect.getRectangle());
            }
        }
        return zones;
    }

    private void updateEntities(float delta){
        for(EntityAI ai:entityAIs.values()){
            ai.act(ConVars.getDouble("sv_npc_velocity"), delta);
        }
        Iterator<Knockback> iter = knockbacks.iterator();
        while(iter.hasNext()){
            Knockback k = iter.next();
            //TODO Knockback
            if(k.isExpired()){
                iter.remove();
            }else{
                ServerEntity e = entities.get(k.id);
                if(e==null){
                    iter.remove();
                }else{
                    entities.get(k.id).addMovement(k.getMovement(delta));
                }
            }
        }

        for(ServerEntity e:entities.values()){
            e.applyMovement();
        }
    }

    private void updateProjectiles(float delta){
        ArrayList<Projectile> destroyedProjectiles = new ArrayList<>();
        for(Projectile projectile:projectiles){
            projectile.update(delta);
            if(!projectile.hitscan) {
                //TODO hit detection no longer is the line projectile has travelled so its possible to go through thin objects
                for(int id:entities.keySet()){
                    if(projectile.ownerID==id){
                        continue;
                    }
                    if(projectile.entitiesHit.contains(id)){
                        continue;
                    }
                    ServerEntity target = entities.get(id);
                    if(Intersector.overlaps(projectile.getHitbox().getBoundingRectangle(), target.getGdxBounds())){
                        if(Intersector.overlapConvexPolygons(projectile.getHitbox(), target.getPolygonBounds())){
                            if(!projectile.dontDestroyOnCollision){
                                projectile.destroyed = true;
                            }
                            if(target.getTeam() != projectile.team){
                                if(projectile.knockback>0){
                                    Vector2 knockback = new Vector2(projectile.knockback,0);
                                    if(projectile.explosionKnockback){
                                        Vector2 projectileCenter = new Vector2();
                                        projectile.getHitbox().getBoundingRectangle().getCenter(projectileCenter);
                                        float angle = Tools.getAngle(projectileCenter.x,projectileCenter.y,target.getCenterX(),target.getCenterY());
                                        knockback.setAngle(angle);
                                    }else{
                                        knockback.setAngle(projectile.rotation);
                                    }
                                    knockbacks.add(new Knockback(target.id, knockback));
                                }
                                target.reduceHealth(projectile.damage,projectile.ownerID);
                            }
                        }
                        projectile.entitiesHit.add(target.id);
                    }
                }
                if (SharedMethods.checkMapCollision(projectile.getHitbox().getBoundingRectangle())) {
                    if(!(projectile.ignoreMapCollision || projectile.dontDestroyOnCollision)){
                        projectile.destroyed = true;
                    }
                }
            }

            if(projectile.destroyed){
                destroyedProjectiles.add(projectile);
                if(projectile.onDestroy!=null){
                    ProjectileDefinition def = projectileList.get(projectile.onDestroy);
                    Vector2 center = new Vector2();
                    projectile.getHitbox().getBoundingRectangle().getCenter(center);
                    Projectile p = SharedMethods.createProjectile(atlas, def,center.x,center.y,projectile.ownerID,projectile.team);
                    projectiles.add(p);
                    if(def.networked){
                        listener.networkedProjectileSpawned(projectile.onDestroy,center.x,center.y,projectile.ownerID,projectile.team);
                    }
                }
            }
        }
        projectiles.removeAll(destroyedProjectiles);
    }

    private void checkEntityCollisions(){

        for(ServerEntity e1 : entities.values()){
            for(ServerEntity e2: entities.values()){
                if(e1.id == e2.id){
                    continue;
                }
                Rectangle bounds1 = e1.getGdxBounds();
                Rectangle bounds2 = e2.getGdxBounds();
                //if(bounds1.overlaps(bounds2)){
                    Rectangle intersection = new Rectangle();
                    if(Intersector.intersectRectangles(bounds1,bounds2,intersection)){
                        float scale = intersection.area()/bounds1.area();
                        Vector2 i = new Vector2(e1.getCenterX()-e2.getCenterX(),e1.getCenterY()-e2.getCenterY());
                        Vector2 knockback = new Vector2(KNOCKBACK_VELOCITY*scale,0);
                        knockback.setAngle(i.angle());
                        knockbacks.add(new Knockback(e1.id, knockback));
                        if(playerList.contains(e1.id)&&!isInvulnerable(e1)) {
                            if(e1.getTeam()!=e2.getTeam()) {
                                e1.lastContactDamageTaken = System.nanoTime();
                                e1.reduceHealth(ConVars.getInt("sv_contact_damage"), e2.id);
                            }
                        }
                    }
                //}
            }
        }
    }


    private void checkPlayerEntityCollisions(){

        for(int playerID : playerList){
            ServerEntity player = entities.get(playerID);
            Iterator<ServerEntity> iter = entities.values().iterator();
            while(iter.hasNext()){
                ServerEntity e = iter.next();
                if(e.id == playerID){
                    continue;
                }
                if(e.getTeam()!=player.getTeam()) {
                    Rectangle intersection = new Rectangle();
                    if(Intersector.intersectRectangles(player.getGdxBounds(),e.getGdxBounds(),intersection)){
                        float scale = intersection.area()/player.getGdxBounds().area();
                        Vector2 i = new Vector2(player.getCenterX()-e.getCenterX(),player.getCenterY()-e.getCenterY());
                        Vector2 knockback = new Vector2(KNOCKBACK_VELOCITY*scale,0);
                        knockback.setAngle(i.angle());
                        knockbacks.add(new Knockback(player.id, knockback));
                        if(!isInvulnerable(player)) {
                            player.lastContactDamageTaken = System.nanoTime();
                            player.reduceHealth(ConVars.getInt("sv_contact_damage"), e.id);
                        }
                    }
                }
            }
        }
    }


    private void checkPowerupCollisions(){
        for(int playerID : playerList){
            ServerEntity player = entities.get(playerID);
            for(int powerupID : powerups.keySet()){
                Powerup p = powerups.get(powerupID);
                if(player.getGdxBounds().overlaps(p.bounds)){
                    if(p.type == Powerup.HEALTH){
                        if(player.getHealth()<player.maxHealth){
                            player.addHealth(p.value);
                            despawnPowerup(powerupID);
                        }
                    }else if(p.type == Powerup.AMMO){
                        if(playerAmmo.get(playerID).get(p.typeModifier)!=null){
                            int ammo = playerAmmo.get(playerID).get(p.typeModifier);
                            ammo += p.value;
                            playerAmmo.get(playerID).put(p.typeModifier,ammo);
                            listener.ammoAddedChanged(playerID, p.typeModifier, p.value);
                            despawnPowerup(powerupID);
                        }
                    }else if(p.type == Powerup.WEAPON){
                        int weapon = p.typeModifier;
                        if(playerWeapons.get(playerID).get(weapon)!=null&&playerWeapons.get(playerID).get(weapon)==false){
                            playerWeapons.get(playerID).put(weapon,true);
                            listener.weaponAdded(playerID,weapon);
                            despawnPowerup(powerupID);
                        }
                    }
                }
            }
        }
    }

    public void processInput(int id, Network.UserInput input){
        ServerEntity e = entities.get(id);
        if(e.invulnerable&&input.buttons.size()>0){
            e.invulnerable = false;
        }
        SharedMethods.applyInput(e, input);
        if(input.buttons.contains(Enums.Buttons.NUM1)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.slot2Weapon = 0;
            }else{
                e.slot1Weapon = 0;
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM2)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.slot2Weapon = 1;
            }else{
                e.slot1Weapon = 1;
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM3)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.slot2Weapon = 2;
            }else{
                e.slot1Weapon = 2;
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM4)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.slot2Weapon = 3;
            }else{
                e.slot1Weapon = 3;
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM5)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.slot2Weapon = 4;
            }else{
                e.slot1Weapon = 4;
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM6)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.slot2Weapon = 5;
            }else{
                e.slot1Weapon = 5;
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM7)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.slot2Weapon = 6;
            }else{
                e.slot1Weapon = 6;
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM8)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.slot2Weapon = 7;
            }else{
                e.slot1Weapon = 7;
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM9)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.slot2Weapon = 8;
            }else{
                e.slot1Weapon = 8;
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM0)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.slot2Weapon = 9;
            }else{
                e.slot1Weapon = 9;
            }
        }

        if(input.buttons.contains(Enums.Buttons.MOUSE1)){
            attackWithPlayer(id, e.slot1Weapon);
        }
        if(input.buttons.contains(Enums.Buttons.MOUSE2)){
            attackWithPlayer(id, e.slot2Weapon);
        }

        if(attackCooldown.get(id)!=null){
            Map<Integer,Double> cooldowns = attackCooldown.get(id);
            for(int weaponslot : cooldowns.keySet()){
                double cd = cooldowns.get(weaponslot);
                cd -= (input.msec/1000d);
                cooldowns.put(weaponslot,cd);
            }
        }
    }

    private void attackWithPlayer(int id, int weaponSlot){
        if(weaponList.get(weaponSlot)==null){
            return;
        }
        if(attackCooldown.get(id).get(weaponSlot) > 0 || playerAmmo.get(id).get(weaponSlot) <= 0 || !playerWeapons.get(id).get(weaponSlot)){
            return;
        }else{
            attackCooldown.get(id).put(weaponSlot, weaponList.get(weaponSlot).cooldown);
            playerAmmo.get(id).put(weaponSlot,playerAmmo.get(id).get(weaponSlot)-1);
            createAttack(id, weaponSlot);
        }
    }

    public void createAttack(int id, int weaponSlot){

        ServerEntity e = entities.get(id);
        Network.EntityAttacking entityAttacking = new Network.EntityAttacking();
        entityAttacking.x = e.getCenterX();
        entityAttacking.y = e.getCenterY();
        entityAttacking.deg = e.getRotation();
        entityAttacking.id = e.id;
        entityAttacking.weapon = weaponSlot;
        listener.attackCreated(entityAttacking);

        Weapon weapon = weaponList.get(weaponSlot);
        ProjectileDefinition projectileDefinition = weapon.projectile;
        if(weapon==null){
            return;
        }
        if(projectileDefinition.hitscan){
            for(Line2D.Float hitscan :SharedMethods.createHitscan(weapon,e.getCenterX(),e.getCenterY(),e.getRotation())){
                Vector2 intersection = SharedMethods.findLineIntersectionPointWithTile(hitscan.x1,hitscan.y1,hitscan.x2,hitscan.y2);
                if(intersection!=null){
                    hitscan.x2 = intersection.x;
                    hitscan.y2 = intersection.y;
                }
                for(int targetId:entities.keySet()) {
                    ServerEntity target = entities.get(targetId);
                    ArrayList<ServerEntity> targetsHit = new ArrayList<>();
                    if(target.getJavaBounds().intersectsLine(hitscan)) {
                        targetsHit.add(target);
                    }
                    if(!targetsHit.isEmpty()){
                        Vector2 closestTarget = new Vector2(0,Float.POSITIVE_INFINITY);
                        for(ServerEntity t:targetsHit){
                            float squaredDistance = Tools.getSquaredDistance(e.getCenterX(),e.getCenterY(),t.getCenterX(),t.getCenterY());
                            if(closestTarget.y>squaredDistance){
                                closestTarget.set(t.id, squaredDistance);
                            }
                        }
                        ServerEntity t = entities.get((int)closestTarget.x);
                        Vector2 i = SharedMethods.getLineIntersectionWithRectangle(hitscan,t.getGdxBounds());
                        if(i!=null){ //TODO i should never be null but is in some cases
                            hitscan.x2 = i.x;
                            hitscan.y2 = i.y;
                            if(projectileDefinition.knockback>0){
                                float angle = Tools.getAngle(e.getCenterX(), e.getCenterY(), t.getCenterX(), t.getCenterY());
                                Vector2 knockback = new Vector2(weapon.projectile.knockback,0);
                                knockback.setAngle(angle);
                                knockbacks.add(new Knockback(targetId, knockback));
                            }
                            if(t.getTeam() != e.getTeam()){
                                t.reduceHealth(projectileDefinition.damage,e.id);
                            }
                        }
                    }
                }
                if(projectileDefinition.onDestroy!=null){
                    Projectile p = SharedMethods.createProjectile(atlas, projectileList.get(projectileDefinition.onDestroy),hitscan.x2,hitscan.y2,e.id,e.getTeam());
                    p.ignoreMapCollision = true;
                    projectiles.add(p);
                }
                if(weapon.projectile.duration>0){
                    projectiles.add(SharedMethods.createProjectile(atlas, hitscan, weapon.projectile));
                }
            }
        }else{
            //TODO projectiles with no duration or range will never get removed
            ArrayList<Projectile> newProjectiles = SharedMethods.createProjectile(atlas, weapon, e.getCenterX(), e.getCenterY(), e.getRotation(), e.id, e.getTeam());
            projectiles.addAll(newProjectiles);
        }
    }

    private HashMap<String,ProjectileDefinition> readProjectileList(){
        File file = new File(Tools.getUserDataDirectory()+ File.separator+"projectilelist.txt");
        if(!file.exists()){
            file = new File("resources"+File.separator+"defaultprojectilelist.txt");
        }
        System.out.println("Loading projectiles from file:"+file);
        HashMap<String,ProjectileDefinition> projectiles = new HashMap<>();
        ProjectileDefinition projectileDefinition = null;
        String projectileName = null;

        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ' ') {
                    continue;
                }
                if (line.charAt(0) == '{') {
                    projectileDefinition = new ProjectileDefinition();
                    continue;
                }
                if (line.charAt(0) == '}') {
                    projectiles.put(projectileName, projectileDefinition);
                    projectileName=null;
                    continue;
                }
                String[] splits = line.split("=");
                if(splits[0].equals("name")){
                    projectileName = splits[1];
                }
                if(splits[0].equals("type")){
                    if(splits[1].equals("hitscan")){
                        projectileDefinition.hitscan = true;
                    }
                }
                if(splits[0].equals("damage")){
                    projectileDefinition.damage = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("range")){
                    projectileDefinition.range = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("velocity")){
                    projectileDefinition.velocity = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("duration")){
                    projectileDefinition.duration = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("shape")){
                    projectileDefinition.shape = splits[1];
                }
                if(splits[0].equals("color")){
                    String strip = splits[1].substring(1,splits[1].length()-1);
                    String[] rgb = strip.split(",");
                    projectileDefinition.color = new Color(Integer.parseInt(rgb[0])/255f,Integer.parseInt(rgb[1])/255f,Integer.parseInt(rgb[2])/255f,1f);
                }
                if(splits[0].equals("width")){
                    projectileDefinition.width = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("length")){
                    projectileDefinition.length = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("image")){
                    projectileDefinition.image = splits[1];
                }
                if(splits[0].equals("animation")){
                    projectileDefinition.animation = splits[1];
                }
                if(splits[0].equals("frameDuration")){
                    projectileDefinition.frameDuration = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("onDestroy")){
                    projectileDefinition.onDestroy = splits[1];
                }
                if(splits[0].equals("knockback")){
                    projectileDefinition.knockback = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("ignoreMapCollision")){
                    projectileDefinition.ignoreMapCollision = true;
                }
                if (splits[0].equals("explosionKnockback")) {
                    projectileDefinition.explosionKnockback = true;
                }
                if (splits[0].equals("dontDestroyOnCollision")) {
                    projectileDefinition.dontDestroyOnCollision = true;
                }
                if (splits[0].equals("sound")) {
                    projectileDefinition.sound = splits[1];
                }
                if (splits[0].equals("friction")) {
                    projectileDefinition.friction = true;
                }
                if (splits[0].equals("noCollision")) {
                    projectileDefinition.noCollision = true;
                }
                if (splits[0].equals("networked")) {
                    projectileDefinition.networked = true;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return projectiles;
    }

    private HashMap<Integer,Weapon> readWeaponList(HashMap<String,ProjectileDefinition> projectileList){
        File file = new File(Tools.getUserDataDirectory()+ File.separator+"weaponlist.txt");
        if(!file.exists()){
            file = new File("resources"+File.separator+"defaultweaponlist.txt");
        }
        System.out.println("Loading weapons from file:" + file);
        HashMap<Integer,Weapon> weapons = new HashMap<>();
        Weapon weapon = null;
        int weaponSlot = 0;

        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ' ') {
                    continue;
                }
                if(line.charAt(0) == '{'){
                    weapon = new Weapon();
                    continue;
                }
                if(line.charAt(0) == '}'){
                    weapons.put(weaponSlot,weapon);
                    weaponSlot++;
                    continue;
                }
                String[] splits = line.split("=");
                if(splits[0].equals("name")){
                    weapon.name = splits[1];
                }
                if(splits[0].equals("spread")){
                    weapon.spread = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("projectileCount")){
                    weapon.projectileCount = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("sound")){
                    weapon.sound = splits[1];
                }
                if(splits[0].equals("projectile")){
                    weapon.projectile = projectileList.get(splits[1]);
                }
                if(splits[0].equals("cooldown")){
                    weapon.cooldown = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("image")){
                    weapon.image = splits[1];
                }
                if(splits[0].equals("ammoImage")){
                    weapon.ammoImage = splits[1];
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(int i: weapons.keySet()){
            Weapon w = weapons.get(i);
            System.out.println("Slot: "+i);
            System.out.println(w);
            System.out.println();
        }

        return weapons;
    }

    private void loadImages() {
        listener.message("Loading texture atlas");
        atlas =  new TextureAtlas("resources"+File.separator+"images"+File.separator+"sprites.atlas");
    }

    private void addEntity(ServerEntity e){
        entities.put(e.id, e);
        listener.entityAdded(e.getNetworkEntity());
    }

    public void addPlayer(Connection c){

        int networkID = getNextNetworkID();
        playerList.add(networkID);
        int width = ConVars.getInt("sv_npc_default_size");
        int height = ConVars.getInt("sv_npc_default_size");
        Rectangle spawnZone = getSpawnZone(map);
        float x;
        float y;
        if(spawnZone!=null){
            x = MathUtils.random(spawnZone.getX(),spawnZone.getX()+spawnZone.getWidth()-width);
            y = MathUtils.random(spawnZone.getY(),spawnZone.getY()+spawnZone.getHeight()-height);
        }else{
            x = MathUtils.random(mapWidth-width);
            y = MathUtils.random(mapHeight-height);
        }

        attackCooldown.put(networkID,new HashMap<Integer, Double>());
        playerAmmo.put(networkID,new HashMap<Integer, Integer>());
        playerWeapons.put(networkID,new HashMap<Integer, Boolean>());

        for(int weaponslot: weaponList.keySet()){
            attackCooldown.get(networkID).put(weaponslot,-1d);
            playerAmmo.get(networkID).put(weaponslot,0);
            playerWeapons.get(networkID).put(weaponslot,false);

        }
        //Lots of ammo for primary weapon
        playerWeapons.get(networkID).put(0,true);
        playerAmmo.get(networkID).put(0,999999999);
        if(ConVars.getBool("sv_idkfa")){
            for(int weaponID : weaponList.keySet()){
                playerWeapons.get(networkID).put(weaponID,true);
                playerAmmo.get(networkID).put(weaponID,999999999);
            }
        }

        playerLives.put(networkID, ConVars.getInt("sv_start_lives"));
        ServerEntity newPlayer = new ServerEntity(networkID,x,y,ConVars.getInt("sv_player_default_team"),ConVars.getInt("sv_player_max_health"),this);
        newPlayer.invulnerable = true;
        newPlayer.width = width;
        newPlayer.height = height;
        newPlayer.image = "player";
        addEntity(newPlayer);

        Network.AssignEntity assign = new Network.AssignEntity();
        assign.networkID = networkID;
        assign.lives = playerLives.get(networkID);
        assign.velocity = ConVars.getFloat("sv_player_velocity");
        assign.mapName = mapName;
        assign.playerList = new ArrayList<>(playerList);
        assign.powerups = new HashMap<>(powerups);
        assign.wave = wave;
        assign.weaponList = new HashMap<>(weaponList);
        assign.projectileList = new HashMap<>(projectileList);
        assign.ammo = playerAmmo.get(networkID);
        assign.weapons = playerWeapons.get(networkID);

        listener.playerAdded(c,assign);
    }

    public void removePlayer(int id){
        pendingEntityRemovals.add(id);
    }

    public void addNPC(Rectangle bounds, int aiType, int weapon, String image){
        System.out.println("Adding npc "+aiType+","+ weapon);

        int networkID = getNextNetworkID();
        ServerEntity npc = new ServerEntity(networkID,bounds.x,bounds.y,-1,this);
        npc.height = bounds.height;
        npc.width = bounds.width;
        npc.image = image;
        npc.reduceHealth(10,-1);
        entityAIs.put(networkID, new EntityAI(npc, aiType, weapon, this));
        addEntity(npc);
    }

    public void addNPC(int aiType, int weapon){
        int width = ConVars.getInt("sv_npc_default_size");
        int height = ConVars.getInt("sv_npc_default_size");
        float spawnPosition = MathUtils.random(SPAWN_AREA_WIDTH * -1, SPAWN_AREA_WIDTH);
        float x;
        float y;
        if(!enemySpawnZones.isEmpty()){
            Rectangle spawnZone = enemySpawnZones.get(MathUtils.random(0,enemySpawnZones.size()-1));
            x = MathUtils.random(spawnZone.getX(),spawnZone.getX()+spawnZone.getWidth()-width);
            y = MathUtils.random(spawnZone.getY(),spawnZone.getY()+spawnZone.getHeight()-height);
        }else{
            if(MathUtils.randomBoolean()){
                if(spawnPosition >= 0){
                    x = mapWidth+spawnPosition;
                }else{
                    x = spawnPosition;
                }
                y = MathUtils.random(0-SPAWN_AREA_WIDTH,mapHeight+SPAWN_AREA_WIDTH);
            }else{
                if(spawnPosition >= 0){
                    y = mapHeight+spawnPosition;
                }else{
                    y = spawnPosition;
                }
                x = MathUtils.random(0-SPAWN_AREA_WIDTH,mapWidth + SPAWN_AREA_WIDTH);
            }
        }
        addNPC(new Rectangle(x, y, width, height), aiType, weapon, "civilian");
    }

    public void addRandomNPC(){
        addNPC(MathUtils.random(0, 3), MathUtils.random(0, 2));
    }

    private boolean isInvulnerable(ServerEntity e){
        if(System.nanoTime()-e.lastContactDamageTaken > Tools.secondsToNano(ConVars.getDouble("sv_invulnerability_timer"))){
            return false;
        }else{
            return true;
        }
    }

    public void spawnPowerup(Powerup p){
        final int id = getNextNetworkID();
        powerups.put(id, p);
        listener.powerupAdded(id, p);
    }

    public void spawnPowerup(int type, int value, int duration){

        int x = MathUtils.random(0, mapWidth);
        int y = MathUtils.random(0, mapHeight);
        final int id = getNextNetworkID();
        Powerup powerup = new Powerup(x,y,type,value);
        powerups.put(id,powerup);

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                despawnPowerup(id);
            }
        };
        timer.schedule(task, Tools.secondsToMilli(duration));
        listener.powerupAdded(id, powerup);
    }

    private void despawnPowerup(int id){
        powerups.remove(id);
        listener.powerupRemoved(id);
    }

    public void removeRandomNPC(){
        Integer[] keys = entities.keySet().toArray(new Integer[entities.keySet().size()]);
        if(keys.length != 0){
            if(playerList.size() < entities.size()){
                int networkID ;
                int removeIndex;
                do{
                    removeIndex = MathUtils.random(keys.length - 1);
                }while(playerList.contains(keys[removeIndex]));
                networkID = keys[removeIndex];
                removeEntity(networkID);
            }
        }
    }

    private void loadMaps(){

        File mapFolder = new File("resources" + File.separator + "maps");
        listener.message("Loading maps from: " + mapFolder);
        for (final File file : mapFolder.listFiles()) {
            if (file.isDirectory()) {
                TiledMap map = loadMap(file.getName());
                mapList.put(file.getName(), map);
                mapObjects.put(map, getScaledMapobjects(map));
                listener.message("Loaded: " + file.getName());
            }
        }
    }

    private HashMap<Integer,WaveDefinition> readWaveList(){
        File file = new File(Tools.getUserDataDirectory()+ File.separator+"wavelist.txt");
        if(!file.exists()){
            file = new File("resources"+ File.separator+"defaultwavelist.txt");
        }
        System.out.println("Loading custom wave from file:" + file);
        HashMap<Integer,WaveDefinition> waves = new HashMap<>();
        int waveNumber = 0;
        String aiType;
        int weapon;
        int count;
        WaveDefinition wave = new WaveDefinition();

        try(BufferedReader reader = new BufferedReader(new FileReader(file))){
            for(String line;(line = reader.readLine())!=null;){
                if(line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ' '){
                    continue;
                }
                if(line.charAt(0) == '{'){
                    wave = new WaveDefinition();
                    continue;
                }
                if(line.charAt(0) == '}'){
                    waveNumber++;
                    waves.put(waveNumber,wave);
                    continue;
                }
                String[] splits = line.split("=");
                if(splits[0].equals("changeMap")){
                    wave.map = splits[1];
                    continue;
                }
                if(line.charAt(0) == '+'){
                    wave.healthPackCount = Integer.parseInt(line.substring(2));
                }
                aiType = Character.toString(line.charAt(0));
                weapon = Character.getNumericValue(line.charAt(1))-1;
                count = Integer.parseInt(line.substring(3));
                wave.addEnemy(aiType, weapon, count);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return waves;
    }

    public HashMap<Integer, Network.Position> getChangedEntityPositions(){
        HashMap<Integer, Network.Position> positions = new HashMap<>();
        for(int id: posChangedEntities){
            ServerEntity e = entities.get(id);
            positions.put(id,new Network.Position(e.getX(),e.getY()));
        }
        posChangedEntities.clear();
        return positions;
    }

    public HashMap<Integer,ArrayList<Component>> getChangedEntityComponents(){
        HashMap<Integer,ArrayList<Component>> changedComponents = new HashMap<>();
        Set<Integer> changedEntities = new HashSet<>();
        changedEntities.addAll(posChangedEntities);
        changedEntities.addAll(healthChangedEntities);
        changedEntities.addAll(rotationChangedEntities);
        changedEntities.addAll(teamChangedEntities);

        for(int id: changedEntities){
            ArrayList<Component> components = new ArrayList<>();
            changedComponents.put(id,components);
            if(posChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Position(e.getX(),e.getY()));
            }
            if(healthChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Health(e.getHealth()));
            }
            if(rotationChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Rotation(e.getRotation()));
            }
            if(teamChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Team(e.getTeam()));
            }
        }
        posChangedEntities.clear();
        healthChangedEntities.clear();
        rotationChangedEntities.clear();
        teamChangedEntities.clear();
        return changedComponents;
    }

    public HashMap<Integer,NetworkEntity> getNetworkedEntityList(){
        HashMap<Integer,NetworkEntity> networkedEntities = new HashMap<>();
        for(int id:entities.keySet()){
            networkedEntities.put(id, entities.get(id).getNetworkEntity());
        }
        return networkedEntities;
    }

    private int getNextNetworkID(){
        return networkIDCounter++;
    }

    public void setWave(int wave){
        this.wave = wave;
        listener.waveChanged(wave);
    }

    private void spawnEnemy(WaveDefinition.EnemyDefinition enemy){
        switch (enemy.aiType){
            case "a": addNPC(EntityAI.MOVING,enemy.weapon); break;
            case "b": addNPC(EntityAI.FOLLOWING,enemy.weapon); break;
            case "c": addNPC(EntityAI.MOVING_AND_SHOOTING,enemy.weapon); break;
            case "d": addNPC(EntityAI.FOLLOWING_AND_SHOOTING,enemy.weapon); break;
        }
    }

    public void removeAllNpcs(){
        for(int id:entityAIs.keySet()) {
            pendingEntityRemovals.add(id);
        }
    }

    public void render(float delta, ShapeRenderer shapeRenderer, SpriteBatch batch, OrthographicCamera camera){

        batch.setColor(1, 1, 1, 1);
        mapRenderer.setView(camera);
        mapRenderer.render();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for(ServerEntity e : entities.values()) {
            if(playerList.contains(e.id)){
                shapeRenderer.setColor(0,0,1,1);
            }else{
                shapeRenderer.setColor(1,1,1,1);
            }
            shapeRenderer.rect(e.getX(), e.getY(), e.width / 2, e.height / 2, e.width, e.height, 1, 1, e.getRotation());
            float health = 1-((float)e.getHealth()/e.maxHealth);
            int healthWidth = (int) (e.width*health);
            shapeRenderer.setColor(1, 0, 0, 1); //red
            shapeRenderer.rect(e.getX(),e.getY(),e.width/2,e.height/2,healthWidth,e.height,1,1,e.getRotation());
        }
        shapeRenderer.end();
        batch.begin();
        for(Powerup p : powerups.values()){
            batch.setColor(1, 0.4f, 0, 1); //safety orange
            batch.draw(atlas.findRegion("blank"),p.bounds.x,p.bounds.y,p.bounds.width,p.bounds.height);
        }
        batch.end();

        SharedMethods.renderAttack(delta, batch, projectiles);
    }

    private void removeEntity(int networkID) {
        entityAIs.remove(networkID);
        entities.remove(networkID);

        healthChangedEntities.remove(networkID);
        posChangedEntities.remove(networkID);
        teamChangedEntities.remove(networkID);
        rotationChangedEntities.remove(networkID);

        if(playerList.remove(networkID)){
            listener.playerRemoved(networkID);
        }
        listener.entityRemoved(networkID);
    }

    public int getEntityCount(){
        return entities.size();
    }

    public int getMapHeight(){
        return mapHeight;
    }
    public int getMapWidth(){
        return mapWidth;
    }

    public ServerEntity getEntity(int id){
        return entities.get(id);
    }

    @Override
    public void positionChanged(int id) {
        posChangedEntities.add(id);
    }

    @Override
    public void healthChanged(int id) {
        healthChangedEntities.add(id);
    }

    @Override
    public void rotationChanged(int id) {
        rotationChangedEntities.add(id);
    }

    @Override
    public void teamChanged(int id) {
        teamChangedEntities.add(id);
    }

    @Override
    public void entityDied(int id, int sourceID) {
        listener.entityKilled(id, sourceID);
        if (playerList.contains(id)) {
            Tools.addToMap(playerLives, id, -1);
            listener.playerLivesChanged(id, playerLives.get(id));
            if(playerLives.get(id) >= 0){
                //Respawn player if lives left
                ServerEntity deadPlayer = entities.get(id);
                deadPlayer.restoreMaxHealth();
                movePlayerToSpawn(id);
            }else{
                pendingEntityRemovals.add(id);
            }
        }else{
            pendingEntityRemovals.add(id);
        }
    }

    interface WorldChangeListener{
        public void playerAdded(Connection c, Network.AssignEntity assign);
        public void playerLivesChanged(int id, int lives);
        public void playerRemoved(int id);
        public void entityRemoved(int id);
        public void entityKilled(int victimID, int killerID);
        public void entityAdded(NetworkEntity e);
        public void powerupAdded(int id, Powerup powerup);
        public void powerupRemoved(int id);
        public void waveChanged(int wave);
        public void attackCreated(Network.EntityAttacking entityAttacking);
        public void message(String message);
        public void mapChanged(String mapName);
        public void ammoAddedChanged(int id, int weapon, int value);
        public void weaponAdded(int id, int weapon);
        public void networkedProjectileSpawned(String projectileName, float x, float y, int ownerID, int team);
    }
}
