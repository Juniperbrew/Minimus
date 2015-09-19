package com.juniperbrew.minimus.server;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.ScreenUtils;
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

    private final float KNOCKBACK_VELOCITY = 200;

    Set<Integer> playerList = new HashSet<>();
    //Map<Integer,Map<Integer,Double>> attackCooldown = new HashMap<>();
    //Map<Integer,HashMap<Integer,Integer>> entityAmmo = new HashMap<>();
    //Map<Integer,HashMap<Integer,Boolean>> entityWeapons = new HashMap<>();
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
    private ConcurrentLinkedQueue<Particle> particles = new ConcurrentLinkedQueue<>();

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

    HashMap<String,EnemyDefinition> enemyList;
    HashMap<Integer,Weapon> weaponList;
    int primaryWeaponCount;
    HashMap<String,ProjectileDefinition> projectileList;

    //This should not be more than 400 which is the max distance entities can look for a destination
    final int SPAWN_AREA_WIDTH = 200;
    public boolean spawnWaves;
    WorldChangeListener listener;

    Timer timer = new Timer();

    TextureAtlas atlas;
    TmxMapLoader mapLoader;

    HashMap<String,TiledMap> mapList = new HashMap<>();
    HashMap<TiledMap,ArrayList<RectangleMapObject>> mapObjects = new HashMap<>();
    OrthogonalTiledMapRenderer mapRenderer;
    SpriteBatch batch;

    ArrayList<Rectangle> enemySpawnZones;

    TextureRegion cachedMap;
    boolean redrawMap;

    public World(WorldChangeListener listener, TmxMapLoader mapLoader, SpriteBatch batch){
        this.listener = listener;
        this.mapLoader = mapLoader;
        this.batch = batch;
        loadMaps();
        projectileList = SharedMethods.readSeperatedProjectileList();
        weaponList = readWeaponSlots(SharedMethods.readSeperatedWeaponList(projectileList));
        GlobalVars.weaponList = weaponList;
        enemyList = SharedMethods.readEnemyList(weaponList);
        loadImages();

        changeMap(ConVars.get("sv_map"));
    }

    private TiledMap loadMap(String mapName){
        GlobalVars.consoleLogger.log("Loading map: " + mapName);
        return mapLoader.load(GlobalVars.mapFolder+File.separator+mapName+File.separator+mapName+".tmx");
    }

    public HashMap<Integer,NetworkEntity> getNetworkedEntities(){
        HashMap<Integer,NetworkEntity> n = new HashMap<>();
        for(int id : entities.keySet()){
            n.put(id,entities.get(id).getNetworkEntity());
        }
        return n;
    }

    public void changeMap(String mapName){
        map = mapList.get(mapName);
        cachedMap = null;
        this.mapName = mapName;
        float mapScale = SharedMethods.getMapScale(map);

        GlobalVars.mapScale = mapScale;
        GlobalVars.mapWidthTiles = map.getProperties().get("width",Integer.class);
        GlobalVars.mapHeightTiles = map.getProperties().get("height",Integer.class);
        GlobalVars.tileWidth = (int) (map.getProperties().get("tilewidth",Integer.class)* mapScale);
        GlobalVars.tileHeight = (int) (map.getProperties().get("tileheight",Integer.class)* mapScale);

        mapHeight = GlobalVars.mapHeightTiles * GlobalVars.tileHeight;
        mapWidth = GlobalVars.mapWidthTiles * GlobalVars.tileWidth;
        GlobalVars.mapWidth = mapWidth;
        GlobalVars.mapHeight = mapHeight;

        GlobalVars.collisionMap = SharedMethods.createCollisionMap(map, GlobalVars.mapWidthTiles, GlobalVars.mapHeightTiles);
        movePlayersToSpawn();

        removeAllNpcs();
        powerups.clear();
        projectiles.clear();

        enemySpawnZones = getEnemySpawnZones(map);
        spawnMapPowerups(map);

        Network.MapChange mapChange = new Network.MapChange();
        mapChange.mapName=mapName;
        mapChange.powerups = new HashMap<>(powerups);
        listener.mapChanged(mapName);

        spawnMapEnemies(map);

        mapRenderer = new OrthogonalTiledMapRenderer(map,mapScale,batch);
    }

    private void spawnMapPowerups(TiledMap map){
        GlobalVars.consoleLogger.log("\nSpawning map powerups");
        for(RectangleMapObject o : mapObjects.get(map)){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("powerup")){
                Rectangle r = o.getRectangle();
                MapProperties p = o.getProperties();
                if(p.containsKey("weapon")){
                    String weaponName = p.get("weapon",String.class);
                    System.out.println(weaponName);
                    int weaponID = SharedMethods.getWeaponID(weaponList,weaponName);
                    if(weaponID==-1){
                        GlobalVars.consoleLogger.log("ERROR Cannot find weapon named "+weaponName);
                    }
                    spawnPowerup(new Powerup(r.x, r.y, r.width, r.height, Powerup.WEAPON, weaponID, -1));
                }else if(p.containsKey("health")){
                    int value = Integer.parseInt(p.get("value",String.class));
                    spawnPowerup(new Powerup(r.x, r.y, r.width, r.height, Powerup.HEALTH, -1, value));
                }else if(p.containsKey("ammo")){
                    String weaponName = p.get("ammo",String.class);
                    int weaponID = SharedMethods.getWeaponID(weaponList, weaponName);
                    if(weaponID==-1){
                        GlobalVars.consoleLogger.log("ERROR Cannot find weapon named "+weaponName);
                    }
                    int value = Integer.parseInt(p.get("value",String.class));
                    spawnPowerup(new Powerup(r.x, r.y, r.width, r.height, Powerup.AMMO, weaponID, value));
                }
            }
        }
    }

    private void spawnMapEnemies(TiledMap map){
        GlobalVars.consoleLogger.log("\nSpawning map enemies");
        for(RectangleMapObject o : mapObjects.get(map)){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("enemy")){
                Rectangle r = o.getRectangle();
                MapProperties p = o.getProperties();
                String enemy = p.get("enemy", String.class);
                addNPC(enemyList.get(enemy), r);
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

    public void setPlayerWeapon(int id, int weaponID){
        ServerEntity player = entities.get(id);
        if(weaponID<=GlobalVars.primaryWeaponCount){
            player.setSlot1Weapon(weaponID);
        }else{
            player.setSlot2Weapon(weaponID);
        }
    }

    public void updateWorld(float delta){

        for(int id: pendingEntityRemovals){
            removeEntity(id);
        }
        pendingEntityRemovals.clear();

        updateGameState();
        updateEntities(delta);
        updateProjectiles(delta);
        updateParticles(delta);
        //checkPlayerEntityCollisions();
        checkEntityCollisions();
        checkPowerupCollisions();
    }

    private void updateParticles(float delta){
        Iterator<Particle> iter = particles.iterator();
        while(iter.hasNext()){
            Particle p = iter.next();
            p.update(delta);
            if(p.destroyed){
                iter.remove();
            }
        }
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

    private void movePlayerToSpawn(int id){
        Rectangle spawnZone = getSpawnZone(map);
        ServerEntity e = entities.get(id);
        if(spawnZone!=null){
            e.moveTo(MathUtils.random(spawnZone.getX(),spawnZone.getX()+spawnZone.getWidth()-e.getWidth()),MathUtils.random(spawnZone.getY(),spawnZone.getY()+spawnZone.getHeight()-e.getHeight()));
        }else{
            e.moveTo(MathUtils.random(mapWidth-e.getWidth()),MathUtils.random(mapHeight-e.getHeight()));
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
                GlobalVars.consoleLogger.log("Spawn at:" + o.getRectangle());
                return o.getRectangle();
            }
        }
        return null;
    }

    private ArrayList<Rectangle> getEnemySpawnZones(TiledMap map){
        ArrayList<Rectangle> zones = new ArrayList<>();
        for(RectangleMapObject o : mapObjects.get(map)){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type",String.class).equals("enemySpawn")){
                zones.add(o.getRectangle());
            }
        }
        return zones;
    }

    private void updateProjectiles(float delta){
        ArrayList<Projectile> destroyedProjectiles = new ArrayList<>();
        Rectangle intersection = new Rectangle();
        for(Projectile projectile:projectiles){
            Rectangle projectileBounds = projectile.getBoundingRectangle();
            projectile.update(delta);
            if(!projectile.ignoreEntityCollision){
                //TODO hit detection no longer is the line projectile has travelled so its possible to go through thin objects
                for(int id:entities.keySet()){
                    if(projectile.ownerID==id&&!projectile.explosionKnockback){
                        continue;
                    }
                    if(projectile.entitiesHit.contains(id)){
                        continue;
                    }
                    ServerEntity target = entities.get(id);
                    Rectangle entityBounds = target.getGdxBounds();
                    if(Intersector.intersectRectangles(projectileBounds, entityBounds, intersection)){
                        if(Intersector.overlapConvexPolygons(projectile.getBoundingPolygon(), target.getPolygonBounds())){
                            if(!projectile.dontDestroyOnCollision){
                                projectile.destroyed = true;
                            }
                            //Explosion self knockbacks apply on player but dont damage him
                            if(projectile.knockback>0&&(projectile.ownerID!=id||projectile.explosionKnockback)){
                                Vector2 knockback;
                                if(projectile.explosionKnockback){
                                    Vector2 projectileCenter = new Vector2();
                                    projectileBounds.getCenter(projectileCenter);
                                    //Knockback is scaled to how far into the explosion the entity is
                                    //Since you can only be hit once by an explosion if you walk into one you will only be hit with very minor knockback
                                    float scale = intersection.area()/entityBounds.area();
                                    Vector2 i = new Vector2(target.getCenterX()-projectileCenter.x,target.getCenterY()-projectileCenter.y);
                                    knockback = new Vector2(projectile.knockback*scale,0);
                                    knockback.setAngle(i.angle());
                                    //Scale the damage too
                                    projectile.damage *= scale;
                                    GlobalVars.consoleLogger.log(target.getID()+" lost "+projectile.damage+" from explosion");
                                }else{
                                    knockback = new Vector2(projectile.knockback,0);
                                    knockback.setAngle(projectile.rotation);
                                }
                                knockbacks.add(new Knockback(target.getID(), knockback));
                                if(projectile.team!=target.getTeam()){
                                    target.reduceHealth(projectile.damage,projectile.ownerID);
                                    if(entityAIs.containsKey(target.getID())){
                                        ServerEntity attacker = entities.get(projectile.ownerID);
                                        if(attacker!=null){
                                            entityAIs.get(target.getID()).setTarget(attacker.getCenterX(),attacker.getCenterY());
                                        }
                                    }
                                }
                            }
                        }
                        projectile.entitiesHit.add(target.getID());
                    }
                }
            }

            if(projectile.destroyed){
                destroyedProjectiles.add(projectile);
                if(projectile.onDestroy!=null){
                    Vector2 center = new Vector2();
                    projectile.getBoundingRectangle().getCenter(center);
                    createStationaryThing(projectile.onDestroy,center.x,center.y,projectile.ownerID,projectile.team);
                }
            }
        }
        projectiles.removeAll(destroyedProjectiles);
    }

    private void checkEntityCollisions(){

        for(ServerEntity e1 : entities.values()){
            for(ServerEntity e2: entities.values()){
                if(e1.getID() == e2.getID()){
                    continue;
                }
                Rectangle bounds1 = e1.getGdxBounds();
                Rectangle bounds2 = e2.getGdxBounds();
                //if(bounds1.overlaps(bounds2)){
                Rectangle intersection = new Rectangle();
                if(Intersector.intersectRectangles(bounds1, bounds2, intersection)){
                    float scale = intersection.area()/bounds1.area();
                    Vector2 i = new Vector2(e1.getCenterX()-e2.getCenterX(),e1.getCenterY()-e2.getCenterY());
                    Vector2 knockback = new Vector2(KNOCKBACK_VELOCITY*scale,0);
                    knockback.setAngle(i.angle());
                    knockbacks.add(new Knockback(e1.getID(), knockback));
                    if(playerList.contains(e1.getID())&&!isInvulnerable(e1)) {
                        if(e1.getTeam()!=e2.getTeam()) {
                            e1.lastContactDamageTaken = System.nanoTime();
                            e1.reduceHealth(ConVars.getInt("sv_contact_damage"), e2.getID());
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
                if(e.getID() == playerID){
                    continue;
                }
                if(e.getTeam()!=player.getTeam()) {
                    Rectangle intersection = new Rectangle();
                    if(Intersector.intersectRectangles(player.getGdxBounds(),e.getGdxBounds(),intersection)){
                        float scale = intersection.area()/player.getGdxBounds().area();
                        Vector2 i = new Vector2(player.getCenterX()-e.getCenterX(),player.getCenterY()-e.getCenterY());
                        Vector2 knockback = new Vector2(KNOCKBACK_VELOCITY*scale,0);
                        knockback.setAngle(i.angle());
                        knockbacks.add(new Knockback(player.getID(), knockback));
                        if(!isInvulnerable(player)) {
                            player.lastContactDamageTaken = System.nanoTime();
                            player.reduceHealth(ConVars.getInt("sv_contact_damage"), e.getID());
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
                        if(player.getHealth()<player.getMaxHealth()){
                            player.addHealth(p.value);
                            despawnPowerup(powerupID);
                        }
                    }else if(p.type == Powerup.AMMO){
                        entities.get(playerID).addAmmo(p.typeModifier,p.value);
                        listener.ammoAddedChanged(playerID, p.typeModifier, p.value);
                        despawnPowerup(powerupID);
                    }else if(p.type == Powerup.WEAPON){
                        int weapon = p.typeModifier;
                        if(!entities.get(playerID).hasWeapon(weapon)){
                            entities.get(playerID).setWeapon(weapon,true);
                            listener.weaponAdded(playerID,weapon);
                            despawnPowerup(powerupID);
                        }
                    }
                }
            }
        }
    }

    public void processInput(ServerEntity e, Network.UserInput input){
        //TODO figure out some way to share this function with client
        if(e.invulnerable&&input.buttons.size()>0){
            e.invulnerable = false;
        }
        SharedMethods.applyInput(e, input);
        if(input.buttons.contains(Enums.Buttons.NUM1)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.setSlot2Weapon(1);
            }else{
                e.setSlot1Weapon(1);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM2)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.setSlot2Weapon(2);
            }else{
                e.setSlot1Weapon(2);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM3)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.setSlot2Weapon(3);
            }else{
                e.setSlot1Weapon(3);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM4)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.setSlot2Weapon(4);
            }else{
                e.setSlot1Weapon(4);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM5)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.setSlot2Weapon(5);
            }else{
                e.setSlot1Weapon(5);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM6)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.setSlot2Weapon(6);
            }else{
                e.setSlot1Weapon(6);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM7)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.setSlot2Weapon(7);
            }else{
                e.setSlot1Weapon(7);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM8)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.setSlot2Weapon(8);
            }else{
                e.setSlot1Weapon(8);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM9)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.setSlot2Weapon(9);
            }else{
                e.setSlot1Weapon(9);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM0)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                e.setSlot2Weapon(10);
            }else{
                e.setSlot1Weapon(10);
            }
        }

        if(input.buttons.contains(Enums.Buttons.MOUSE1)){
            attack(e,e.getSlot1Weapon(),input.msec);
        }else if(input.buttons.contains(Enums.Buttons.MOUSE2)){
            attack(e,e.getSlot2Weapon(),input.msec);
        }else if(e.chargeMeter>0){
            launchAttack(e,e.chargeWeapon);
        }

        e.updateCooldowns(input.msec / 1000d);
    }

    public void attack(ServerEntity e, int weaponID, short msec){
        if(weaponList.get(weaponID)==null){
            return;
        }
        Weapon weapon = weaponList.get(weaponID);
        if(weapon.chargeDuration>0&&e.chargeMeter<weapon.chargeDuration) {
            chargeAttack(e, weaponID, msec);
        }else{
            //FIXME This weaponID parameter is a bit missleading because if we are firing due to full charge meter it will actually use e.chargeweapon but this should be same as weaponID in all cases
            launchAttack(e, weaponID);
        }
    }

    private void chargeAttack(ServerEntity e, int weaponID, short msec){
        if(weaponList.get(weaponID)==null){
            return;
        }
        if(e.weaponCooldown(weaponID) || !e.hasAmmo(weaponID) || !e.hasWeapon(weaponID)){
            return;
        }
        e.chargeWeapon = weaponID;
        e.chargeMeter += (msec/1000d);
    }

    private void launchAttack(ServerEntity e, int weaponID){
        if(weaponList.get(weaponID)==null){
            return;
        }
        if(e.weaponCooldown(weaponID) || !e.hasAmmo(weaponID) || !e.hasWeapon(weaponID)){
            return;
        }
        Weapon weapon = weaponList.get(weaponID);
        e.setWeaponCooldown(weaponID, weaponList.get(weaponID).cooldown);
        e.addAmmo(weaponID,-1);

        Network.EntityAttacking entityAttacking = new Network.EntityAttacking();
        entityAttacking.x = e.getCenterX();
        entityAttacking.y = e.getCenterY();
        entityAttacking.deg = e.getRotation();
        entityAttacking.id = e.getID();
        entityAttacking.weapon = weaponID;

        if(e.chargeMeter>0&&e.chargeWeapon==weaponID){
            entityAttacking.projectileModifiers = new HashMap<>();
            float charge = e.chargeMeter/weapon.chargeDuration;
            if(charge>1) charge = 1;
            float velocity = weapon.minChargeVelocity+(weapon.maxChargeVelocity-weapon.minChargeVelocity)*charge;
            entityAttacking.projectileModifiers.put("velocity", velocity);
            if(weapon.projectile.duration>0){
                entityAttacking.projectileModifiers.put("duration",weapon.projectile.duration-e.chargeMeter);
            }
            e.chargeMeter = 0;
        }
        if(e.chargeWeapon!=weaponID){
            e.chargeMeter = 0;
        }
        createAttack(entityAttacking);
        listener.attackCreated(entityAttacking, weapon);
    }

    private void createAttack(Network.EntityAttacking attack){

        Weapon weapon = weaponList.get(attack.weapon);
        ProjectileDefinition projectileDefinition = weapon.projectile;
        ServerEntity e = entities.get(attack.id);

        if(weapon==null){
            return;
        }
        if(projectileDefinition.type == ProjectileDefinition.HITSCAN){
            for(Line2D.Float hitscan :SharedMethods.createHitscan(weapon,e.getCenterX(),e.getCenterY(),e.getRotation())){
                Vector2 intersection = SharedMethods.findLineIntersectionPointWithTile(hitscan.x1,hitscan.y1,hitscan.x2,hitscan.y2);
                if(intersection!=null){
                    hitscan.x2 = intersection.x;
                    hitscan.y2 = intersection.y;
                }
                ArrayList<ServerEntity> targetsHit = new ArrayList<>();
                for(int targetId:entities.keySet()) {
                    if(targetId==e.getID()){
                        continue;
                    }
                    ServerEntity target = entities.get(targetId);
                    if(target.getJavaBounds().intersectsLine(hitscan)) {
                        targetsHit.add(target);
                    }
                }
                if(!targetsHit.isEmpty()){
                    Vector2 closestTarget = new Vector2(0,Float.POSITIVE_INFINITY);
                    for(ServerEntity t:targetsHit){
                        float squaredDistance = Tools.getSquaredDistance(e.getCenterX(),e.getCenterY(),t.getCenterX(),t.getCenterY());
                        if(closestTarget.y>squaredDistance){
                            closestTarget.set(t.getID(), squaredDistance);
                        }
                    }
                    ServerEntity t = entities.get((int)closestTarget.x);
                    Vector2 i = SharedMethods.getLineIntersectionWithRectangle(hitscan,t.getGdxBounds());
                    //FIXME i should never be null
                    if(i!=null) {
                        hitscan.x2 = i.x;
                        hitscan.y2 = i.y;
                    }
                    if(projectileDefinition.knockback>0){
                        float angle = Tools.getAngle(e.getCenterX(), e.getCenterY(), t.getCenterX(), t.getCenterY());
                        Vector2 knockback = new Vector2(weapon.projectile.knockback,0);
                        knockback.setAngle(angle);
                        knockbacks.add(new Knockback(t.getID(), knockback));
                    }
                    if(t.getTeam() != e.getTeam()){
                        t.reduceHealth(projectileDefinition.damage, e.getID());
                        if(entityAIs.containsKey(t.getID())){
                            entityAIs.get(t.getID()).setTarget(e.getCenterX(),e.getCenterY());
                        }
                    }
                }
                if(projectileDefinition.onDestroy!=null){
                    createStationaryThing(projectileDefinition.onDestroy,hitscan.x2,hitscan.y2,e.getID(),e.getTeam());
                }
                if(weapon.projectile.tracer !=null){
                    particles.add(SharedMethods.createTracer(projectileList.get(weapon.projectile.tracer), hitscan));
                }
            }
        }else if (projectileDefinition.type == ProjectileDefinition.PROJECTILE){
            //TODO projectiles with no duration or range will never get removed
            ArrayList<Projectile> newProjectiles = SharedMethods.createProjectiles(weapon, e.getCenterX(), e.getCenterY(), e.getRotation(), e.getID(), e.getTeam(),attack.projectileModifiers);
            projectiles.addAll(newProjectiles);
        }
    }

    private void createStationaryThing(String name, float x, float y, int id, int team){
        ProjectileDefinition def = projectileList.get(name);
        if(def.type==ProjectileDefinition.PROJECTILE){
            projectiles.add(SharedMethods.createStationaryProjectile(def, x, y, id, team));
            listener.networkedProjectileSpawned(def.name,x,y,id,team);
        }else if(def.type==ProjectileDefinition.PARTICLE){
            particles.add(SharedMethods.createStationaryParticle(def, x, y));
        }
    }



    private HashMap<Integer,Weapon> readWeaponSlots(HashMap<String,Weapon> weapons){
        File file = new File(Tools.getUserDataDirectory()+ File.separator+"weaponslots.txt");
        if(!file.exists()){
            file = new File("resources"+File.separator+"defaultweaponslots.txt");
        }
        GlobalVars.consoleLogger.log("\nLoading weapon slots from file:" + file);
        HashMap<Integer,Weapon> weaponList = new HashMap<>();
        HashMap<Integer,String> primaries = new HashMap<>();
        HashMap<Integer,String> secondaries = new HashMap<>();
        String type = null;

        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ' ') {
                    continue;
                }
                if(line.equals("Primary")){
                    type = "Primary";
                }else if(line.equals("Secondary")){
                    type = "Secondary";
                }else if(type != null){
                    String[] splits = line.split(":");
                    if(type.equals("Primary")){
                        primaries.put(Integer.parseInt(splits[0]),splits[1]);
                    }else if(type.equals("Secondary")){
                        secondaries.put(Integer.parseInt(splits[0]),splits[1]);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        GlobalVars.consoleLogger.log("Populating weaponlist");
        GlobalVars.consoleLogger.log("Primary weapons");
        int id = 1;
        for(int slot: primaries.keySet()){
            GlobalVars.consoleLogger.log("ID:" + id + "| Slot " + slot + ": " + weapons.get(primaries.get(slot)));
            weaponList.put(id,weapons.get(primaries.get(slot)));
            id++;
        }
        GlobalVars.consoleLogger.log("Secondary weapons");
        for(int slot: secondaries.keySet()){
            GlobalVars.consoleLogger.log("ID:" + id + "| Slot " + slot + ": " + weapons.get(secondaries.get(slot)));
            weaponList.put(id,weapons.get(secondaries.get(slot)));
            id++;
        }
        GlobalVars.consoleLogger.log("Storing primary weapon count: " + primaries.size());
        primaryWeaponCount = primaries.size();
        GlobalVars.primaryWeaponCount = primaryWeaponCount;
        return weaponList;
    }

    private void loadImages() {
        GlobalVars.consoleLogger.log("\nLoading texture atlas");
        atlas =  new TextureAtlas("resources"+File.separator+"images"+File.separator+"sprites.atlas");
        GlobalVars.atlas = atlas;
    }

    private void addEntity(ServerEntity e){
        entities.put(e.getID(), e);
        listener.entityAdded(e.getNetworkEntity());
    }

    public void addPlayer(Connection c){

        int networkID = getNextNetworkID();
        playerList.add(networkID);
        int width = (int) (ConVars.getInt("sv_npc_default_size")*GlobalVars.mapScale);
        int height = (int) (ConVars.getInt("sv_npc_default_size")*GlobalVars.mapScale);
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

        HashMap<Integer,Boolean> entityWeapons = new HashMap<>();
        HashMap<Integer,Integer> entityAmmo = new HashMap<>();
        for(int weaponID : weaponList.keySet()){
            if(ConVars.getBool("sv_idkfa")){
                entityWeapons.put(weaponID, true);
                entityAmmo.put(weaponID, 999999999);
            }else{
                entityWeapons.put(weaponID, false);
                entityAmmo.put(weaponID, 0);
            }
        }
        //Lots of ammo for primary weapon
        entityWeapons.put(0, true);
        entityAmmo.put(0, 999999999);

        playerLives.put(networkID, ConVars.getInt("sv_start_lives"));
        ServerEntity newPlayer = new ServerEntity(networkID,x,y,width,height,
                ConVars.getInt("sv_player_max_health"),ConVars.getInt("sv_player_default_team"),
                "rambo",entityWeapons,entityAmmo,this);
        newPlayer.invulnerable = true;
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
        assign.primaryWeaponCount = primaryWeaponCount;
        assign.projectileList = new HashMap<>(projectileList);
        assign.ammo = entityAmmo;
        assign.weapons = entityWeapons;

        listener.playerAdded(c,assign);
    }

    public void removePlayer(int id){
        pendingEntityRemovals.add(id);
    }

    public void addNPC(EnemyDefinition def){
        int width = (int) (ConVars.getInt("sv_npc_default_size")*GlobalVars.mapScale);
        int height = (int) (ConVars.getInt("sv_npc_default_size")*GlobalVars.mapScale);
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
        addNPC(def, new Rectangle(x, y, width, height));
    }

    public void addNPC(EnemyDefinition def, Rectangle bounds){

        GlobalVars.consoleLogger.log("Adding npc:" + def + " at " + bounds);
        int networkID = getNextNetworkID();

        HashMap<Integer,Boolean> entityWeapons = new HashMap<>();
        HashMap<Integer,Integer> entityAmmo = new HashMap<>();

        for(int weaponslot: weaponList.keySet()){
            entityAmmo.put(weaponslot, 0);
            entityWeapons.put(weaponslot, false);
        }

        //Lots of ammo for weapon
        entityWeapons.put(def.weapon, true);
        entityAmmo.put(def.weapon, 999999999);

        ServerEntity npc = new ServerEntity(networkID,bounds.x,bounds.y,bounds.width,bounds.height,def.health,-1,
                def.image,entityWeapons,entityAmmo,this);
        entityAIs.put(networkID, new EntityAI(npc, EntityAI.FOLLOWING_AND_SHOOTING, def.weapon, this));


        addEntity(npc);
    }

    public void addRandomNPC(){
        EnemyDefinition def = enemyList.values().toArray(new EnemyDefinition[enemyList.size()])[MathUtils.random(enemyList.size()-1)];
        addNPC(def);
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
        GlobalVars.consoleLogger.log("Loading maps from: " + mapFolder);
        for (final File file : mapFolder.listFiles()) {
            if (file.isDirectory()) {
                TiledMap map = loadMap(file.getName());
                mapList.put(file.getName(), map);
                mapObjects.put(map, getScaledMapobjects(map));
                GlobalVars.consoleLogger.log("Loaded: " + file.getName());
            }
        }
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

    private int getNextNetworkID(){
        return networkIDCounter++;
    }

    public void setWave(int wave){
        this.wave = wave;
        listener.waveChanged(wave);
    }

    public void removeAllNpcs(){
        for(int id:entityAIs.keySet()) {
            pendingEntityRemovals.add(id);
        }
    }

    public void render(float delta, ShapeRenderer shapeRenderer, SpriteBatch batch, OrthographicCamera camera){

        batch.setColor(1, 1, 1, 1);
        if(cachedMap==null||redrawMap){
            mapRenderer.setView(camera);
            mapRenderer.render();
            cachedMap = ScreenUtils.getFrameBufferTexture();
            redrawMap = false;
        }else{
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            //FIXME why cant i just draw the map at (0,0) this probably has something to do with how i center the map to window
            //FIXME if window height is greater than width the map is no longer centered
            batch.draw(cachedMap, -(camera.viewportWidth - GlobalVars.mapWidth) / 2f, 0, camera.viewportWidth, camera.viewportHeight);
            batch.end();
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for(ServerEntity e : entities.values()) {
            if(playerList.contains(e.getID())){
                shapeRenderer.setColor(0,0,1,1);
            }else{
                shapeRenderer.setColor(1,1,1,1);
            }
            shapeRenderer.rect(e.getX(), e.getY(), e.getWidth() / 2, e.getHeight() / 2, e.getWidth(), e.getHeight(), 1, 1, e.getRotation());
            int healthWidth = (int) (e.getWidth()*e.getHealthPercent());
            shapeRenderer.setColor(1, 0, 0, 1); //red
            shapeRenderer.rect(e.getX(),e.getY(),e.getWidth()/2,e.getHeight()/2,healthWidth,e.getHeight(),1,1,e.getRotation());
        }
        shapeRenderer.end();
        batch.begin();
        for(Powerup p : powerups.values()){
            batch.setColor(1, 0.4f, 0, 1); //safety orange
            batch.draw(atlas.findRegion("blank"),p.bounds.x,p.bounds.y,p.bounds.width,p.bounds.height);
        }
        batch.end();

        SharedMethods.renderParticles(delta, batch, projectiles);
        SharedMethods.renderParticles(delta, batch, particles);

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
        public void attackCreated(Network.EntityAttacking entityAttacking, Weapon weapon);
        public void mapChanged(String mapName);
        public void ammoAddedChanged(int id, int weapon, int value);
        public void weaponAdded(int id, int weapon);
        public void networkedProjectileSpawned(String projectileName, float x, float y, int ownerID, int team);
    }
}
