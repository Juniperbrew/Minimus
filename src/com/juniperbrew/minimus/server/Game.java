package com.juniperbrew.minimus.server;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.kryonet.Connection;
import com.juniperbrew.minimus.*;
import com.juniperbrew.minimus.components.Component;
import tiled.core.Map;
import tiled.core.MapObject;
import tiled.io.TMXMapReader;

import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 1.8.2015.
 */
public class Game implements EntityChangeListener{

    private final float KNOCKBACK_VELOCITY = 200;

    Set<Integer> playerList = new HashSet<>();

    public String mapName;
    public String campaignName;

    //Rendered state
    Map map;
    //ArrayList<Rectangle> solidMapObjects;
    ConcurrentHashMap<Integer,ServerEntity> entities = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer,Powerup> powerups = new ConcurrentHashMap<>();
    //public ConcurrentLinkedQueue<Particle> projectiles = new ConcurrentLinkedQueue<>();
    public ConcurrentLinkedQueue<Particle> particles = new ConcurrentLinkedQueue<>();


    HashMap<Integer,EntityAI> entityAIs = new HashMap<>();
    ArrayList<Knockback> knockbacks = new ArrayList<>();

    Set<Integer> posChangedEntities = new HashSet<>();
    Set<Integer> healthChangedEntities = new HashSet<>();
    Set<Integer> rotationChangedEntities = new HashSet<>();
    Set<Integer> teamChangedEntities = new HashSet<>();
    Set<Integer> slot1ChangedEntities = new HashSet<>();
    Set<Integer> slot2ChangedEntities = new HashSet<>();
    Set<Integer> maxHealthChangedEntities = new HashSet<>();

    ArrayList<Integer> pendingEntityRemovals = new ArrayList<>();

    private int networkIDCounter = 1;

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
    HashSet<String> ammoList;
    int primaryWeaponCount;
    HashMap<String,ProjectileDefinition> projectileList;

    //This should not be more than 400 which is the max distance entities can look for a destination
    final int SPAWN_AREA_WIDTH = 200;
    public boolean spawnWaves;
    WorldChangeListener listener;

    Timer timer = new Timer();


    ArrayList<Rectangle> enemySpawnZones;

    private float mapEndTimer;
    boolean mapCleared;

    Rectangle mapExit;
    Rectangle playerSpawn;

    private boolean questComplete;

    public Game(WorldChangeListener listener){
        this.listener = listener;

        loadCampaign(ConVars.get("sv_campaign"));
    }

    public void loadCampaign(String campaign){
        G.console.log("Loading campaign: " + campaign);
        for(int playerID : playerList){
            removePlayer(playerID);
        }
        campaignName = campaign;
        G.console.runConsoleScript(G.campaignFolder+File.separator+campaign+File.separator+"autoexec.txt");
        projectileList = F.readSeperatedProjectileList(campaignName);
        weaponList = readWeaponSlots(F.readSeperatedWeaponList(projectileList, campaignName));
        ammoList = F.getAmmoList(weaponList);

        G.weaponList = weaponList;
        G.ammoList = ammoList;
        G.shoplist = F.readShopList(campaignName);
        enemyList = F.readEnemyList(weaponList, campaignName);
        G.weaponNameToID = F.createWeaponNameToIDMapping(G.weaponList);
        //loadImages();
        changeMap(ConVars.get("sv_map"));
        listener.reassignPlayers();
    }

    private void loadMap(String mapName){
        TMXMapReader mapReader = new TMXMapReader();
        G.console.log("Loading map: " + mapName);
        String fileName = G.campaignFolder+File.separator+campaignName+File.separator+"maps"+File.separator+mapName+File.separator+mapName+".tmx";
        try {
            map = mapReader.readMap(fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public HashMap<Integer,NetworkEntity> getNetworkedEntities(){
        HashMap<Integer,NetworkEntity> n = new HashMap<>();
        for(int id : entities.keySet()){
            n.put(id,entities.get(id).getNetworkEntity());
        }
        return n;
    }

    public void changeMap(String mapName){
        G.console.log("\nChanging map to " + mapName);
        mapExit = null;
        mapCleared = false;
        questComplete = true;
        removeAllNpcs();
        powerups.clear();
        particles.clear();

        loadMap(mapName);

        this.mapName = mapName;
        G.mapScale = 1;

        if(map.getProperties().containsKey("quest")){
            questComplete = false;
        }
        G.mapWidthTiles = map.getWidth();
        G.mapHeightTiles = map.getHeight();
        G.tileWidth = map.getTileWidth();
        G.tileHeight = map.getTileHeight();

        G.mapWidth = G.mapWidthTiles * G.tileWidth;
        G.mapHeight = G.mapHeightTiles * G.tileHeight;

        G.console.log("MapSize: "+G.mapWidth+"x"+G.mapHeight);
        G.console.log("Mapsize(tiles): "+G.mapWidthTiles+"x"+G.mapHeightTiles);

        ArrayList<MapObject> mapObjects = MapFunctions.getMapObjects(map);
        G.solidMapObjects = MapFunctions.getSolidMapObjects(mapObjects);

        G.collisionMap = MapFunctions.createCollisionMap(map);
        for (int row = 0; row < G.collisionMap[0].length; row++) {
            for (int col = 0; col < G.collisionMap.length; col++) {
                if(G.collisionMap[col][row]){
                    System.out.print("#");
                }else{
                    System.out.print("O");
                }
            }
            System.out.println();
        }
        movePlayersToSpawn();

        enemySpawnZones = MapFunctions.getEnemySpawnZones(mapObjects);
        mapExit = MapFunctions.getMapExit(mapObjects);
        playerSpawn = MapFunctions.getSpawnZone(mapObjects);

        Network.MapChange mapChange = new Network.MapChange();
        mapChange.mapName=mapName;
        listener.mapChanged(mapName);

        spawnMapPowerups(mapObjects);
        spawnMapEnemies(mapObjects);
    }

    private void spawnMapPowerups(ArrayList<MapObject> mapObjects){
        G.console.log("\nSpawning map powerups");
        for(MapObject o : mapObjects){
            if(o.getProperties().containsKey("type") && o.getProperties().get("type").equals("powerup")){
                Rectangle r = Tools.javaToGdxRectangle(o.getBounds());
                Properties p = o.getProperties();
                if(p.containsKey("weapon")){
                    String weaponName = p.getProperty("weapon");
                    int weaponID = F.getWeaponID(weaponList, weaponName);
                    if(weaponID==-1){
                        G.console.log("ERROR Cannot find weapon named "+weaponName);
                    }
                    spawnPowerup(new WeaponPickup(r.x, r.y, r.width, r.height, weaponID));
                }else if(p.containsKey("health")){
                    int value = Integer.parseInt(p.getProperty("value"));
                    spawnPowerup(new HealthPack(r.x, r.y, r.width, r.height, value));
                }else if(p.containsKey("ammo")){
                    String ammoType = p.getProperty("ammo");
                    if(!G.ammoList.contains(ammoType)){
                        G.console.log("ERROR Cannot find ammo named "+ammoType);
                    }
                    int value = Integer.parseInt(p.getProperty("value"));
                    spawnPowerup(new AmmoPickup(r.x, r.y, r.width, r.height, ammoType, value));
                }
            }
        }
    }

    private void spawnMapEnemies(ArrayList<MapObject> mapObjects){
        G.console.log("\nSpawning map enemies");
        for(MapObject o : mapObjects){
            if(o.getType().equals("enemy")){
                Rectangle r = Tools.javaToGdxRectangle(o.getBounds());
                Properties p = o.getProperties();
                String enemy = p.getProperty("enemy");
                G.console.log("Spawning: " + enemy);
                addNPC(enemyList.get(enemy), r);
            }
        }
    }

    public void setPlayerWeapon(int id, int weaponID){
        ServerEntity player = entities.get(id);
        if(weaponID<= G.primaryWeaponCount){
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

        updateGameState(delta);
        updateEntities(delta);
        updateParticles(delta);
        //updateParticles(delta);
        //checkPlayerEntityCollisions();
        checkPlayerCollisions();
        checkEntityCollisions();
        checkPowerupCollisions();
    }

    /*private void updateParticles(float delta){
        Iterator<Particle> iter = particles.iterator();
        while(iter.hasNext()){
            Particle p = iter.next();
            p.update(delta);
            if(p.destroyed){
                iter.remove();
            }
        }
    }*/

    private void updateEntities(float delta){
        for(EntityAI ai:entityAIs.values()){
            ai.act(delta);
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

        for(int id : playerList){
            PlayerServerEntity player = (PlayerServerEntity) entities.get(id);
            if(player.getRespawnTimer()>0){
                player.reduceRespawnTimer(delta);
                if(player.getRespawnTimer()<=0){
                    ServerEntity deadPlayer = entities.get(id);
                    deadPlayer.restoreMaxHealth();
                    movePlayerToSpawn(id);
                    listener.playerRespawned(id, deadPlayer.getX(), deadPlayer.getY());
                }
            }
        }
    }

    public void completeQuest(){
        questComplete = true;
    }

    private void updateGameState(float delta){
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
                    spawnHealthPack(30, 30);
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
                if(!mapCleared){
                    mapCleared = true;
                    if(mapExit==null){
                        mapEndTimer = 10f;
                        listener.mapCleared(mapEndTimer);
                    }else{
                        listener.mapCleared(0);
                    }
                }

                if(mapExit == null && questComplete){
                    if(mapEndTimer<=0){
                        String nextMap = map.getProperties().getProperty("nextLevel");
                        if(nextMap!=null){
                            changeMap(nextMap);
                        }
                        mapCleared = false;
                    }else{
                        mapEndTimer -= delta;
                    }
                }
            }
        }
    }

    private void movePlayerToSpawn(int id){
        ServerEntity e = entities.get(id);
        if(playerSpawn!=null){
            e.moveTo(Tools.rand(playerSpawn.getX(), playerSpawn.getX() + playerSpawn.getWidth() - e.getWidth()), Tools.rand(playerSpawn.getY(), playerSpawn.getY() + playerSpawn.getHeight() - e.getHeight()));
        }else{
            e.moveTo(Tools.rand(G.mapWidth-e.getWidth()),Tools.rand(G.mapHeight - e.getHeight()));
        }
    }

    private void movePlayersToSpawn(){
        for(int id:playerList){
            movePlayerToSpawn(id);
        }
    }

    private void updateParticles(float delta){
        ArrayList<Particle> destroyedParticles = new ArrayList<>();
        Rectangle intersection = new Rectangle();
        for(Particle p:particles){
            Rectangle projectileBounds = p.getBoundingRectangle();
            p.update(delta);
            if(!p.ignoreEntityCollision){
                //TODO hit detection no longer is the line projectile has travelled so its possible to go through thin objects
                for(int id:entities.keySet()){
                    if(p.ownerID==id&&!p.explosionKnockback){
                        continue;
                    }
                    if(p.entitiesHit.contains(id)){
                        continue;
                    }
                    ServerEntity target = entities.get(id);
                    Rectangle entityBounds = target.getGdxBounds();
                    if(Intersector.intersectRectangles(projectileBounds, entityBounds, intersection)){
                        if(Intersector.overlapConvexPolygons(p.getBoundingPolygon(), target.getPolygonBounds())){
                            if(p.stopOnCollision){
                                p.stopped = true;
                            }else if(!p.dontDestroyOnCollision){
                                p.destroyed = true;
                            }
                            //Explosion self knockbacks apply on player but dont damage him
                            if(p.knockback>0&&(p.ownerID!=id||p.explosionKnockback)){
                                Vector2 knockback;
                                if(p.explosionKnockback){
                                    Vector2 projectileCenter = new Vector2();
                                    projectileBounds.getCenter(projectileCenter);
                                    //Knockback is scaled to how far into the explosion the entity is
                                    //Since you can only be hit once by an explosion if you walk into one you will only be hit with very minor knockback
                                    float scale = intersection.area()/entityBounds.area();
                                    Vector2 i = new Vector2(target.getCenterX()-projectileCenter.x,target.getCenterY()-projectileCenter.y);
                                    knockback = new Vector2(p.knockback*scale,0);
                                    knockback.setAngle(i.angle());
                                    //Scale the damage too
                                    p.damage *= scale;
                                }else{
                                    knockback = new Vector2(p.knockback,0);
                                    knockback.setAngle(p.rotation);
                                }
                                knockbacks.add(new Knockback(target.getID(), knockback));
                                if(p.team!=target.getTeam()){
                                    target.reduceHealth(p.damage,p.ownerID);
                                    if(entityAIs.containsKey(target.getID())){
                                        ServerEntity attacker = entities.get(p.ownerID);
                                        if(attacker!=null){
                                            entityAIs.get(target.getID()).setTarget(attacker.getCenterX(),attacker.getCenterY());
                                        }
                                    }
                                }
                            }
                            p.entitiesHit.add(target.getID());
                        }
                    }
                }
            }

            if(p.destroyed){
                destroyedParticles.add(p);
                if(p.onDestroy!=null){
                    Vector2 center = new Vector2();
                    p.getBoundingRectangle().getCenter(center);
                    createStationaryThing(p.onDestroy, center.x, center.y, p.ownerID, p.team);
                }
            }
        }
        particles.removeAll(destroyedParticles);
    }

    private void checkPlayerCollisions(){
        for(int id : playerList){
            ServerEntity player = entities.get(id);
            if(mapCleared&&questComplete&&mapExit!=null){
                if(!player.getGdxBounds().overlaps(mapExit)){
                    String nextMap = map.getProperties().getProperty("nextLevel");
                    if(nextMap!=null){
                        changeMap(nextMap);
                    }
                    continue;
                }
            }
        }
    }

    private void checkEntityCollisions(){

        for(ServerEntity e1 : entities.values()){
            if(e1 instanceof PlayerServerEntity){
                PlayerServerEntity player = (PlayerServerEntity) e1;
                if(player.getLives()<0||player.getRespawnTimer()>0){
                    continue;
                }
            }
            for(ServerEntity e2: entities.values()){
                if(e1.getID() == e2.getID()){
                    continue;
                }
                if(e2 instanceof PlayerServerEntity){
                    PlayerServerEntity player = (PlayerServerEntity) e2;
                    if(player.getLives()<0||player.getRespawnTimer()>0){
                        continue;
                    }
                }
                Rectangle2D.Double bounds1 = e1.getJavaBounds();
                Rectangle2D.Double bounds2 = e2.getJavaBounds();
                //if(bounds1.overlaps(bounds2)){

                Rectangle2D intersection = bounds1.createIntersection(bounds2);


                if(!intersection.isEmpty()){
                    double scale = Tools.getArea(intersection)/Tools.getArea(bounds1);
                    Vector2 i = new Vector2(e1.getCenterX()-e2.getCenterX(),e1.getCenterY()-e2.getCenterY());
                    Vector2 knockback = new Vector2((float) (KNOCKBACK_VELOCITY*scale),0);
                    knockback.setAngle(i.angle());
                    knockbacks.add(new Knockback(e1.getID(), knockback));
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

                Rectangle2D.Double playerBounds = player.getJavaBounds();
                Rectangle2D.Double entityBounds = e.getJavaBounds();


                if(e.getTeam()!=player.getTeam()) {
                    Rectangle2D intersection = playerBounds.createIntersection(entityBounds);

                    if(!intersection.isEmpty()){
                        double scale = Tools.getArea(intersection)/Tools.getArea(playerBounds);
                        Vector2 i = new Vector2(player.getCenterX()-e.getCenterX(),player.getCenterY()-e.getCenterY());
                        Vector2 knockback = new Vector2((float) (KNOCKBACK_VELOCITY*scale),0);
                        knockback.setAngle(i.angle());
                        knockbacks.add(new Knockback(player.getID(), knockback));
                    }
                }
            }
        }
    }


    private void checkPowerupCollisions(){
        for(int playerID : playerList){
            PlayerServerEntity player = (PlayerServerEntity) entities.get(playerID);
            for(int powerupID : powerups.keySet()){
                Powerup p = powerups.get(powerupID);
                if(player.getGdxBounds().overlaps(p.bounds)){
                    if(p instanceof HealthPack){
                        HealthPack healthPack = (HealthPack) p;
                        if(player.getHealth()<player.getMaxHealth()){
                            player.addHealth(healthPack.value);
                            despawnPowerup(powerupID);
                        }
                    }else if(p instanceof AmmoPickup){
                        AmmoPickup ammoPickup = (AmmoPickup) p;
                        player.changeAmmo(ammoPickup.ammoType, ammoPickup.value);
                        despawnPowerup(powerupID);
                    }else if(p instanceof WeaponPickup){
                        WeaponPickup weaponPickup = (WeaponPickup) p;
                        int weapon = weaponPickup.weaponID;
                        if(!player.hasWeapon(weapon)){
                            player.setWeapon(weapon, true);
                            despawnPowerup(powerupID);
                        }
                    }
                }
            }
        }
    }

    public void processInput(PlayerServerEntity player, Network.UserInput input){
        //TODO figure out some way to share this function with client
        player.updateCooldowns(input.msec / 1000d);
        if(player.respawnTimer>0||player.lives<0){
            return;
        }
        if(player.invulnerable&&input.buttons.size()>0&&player.getRespawnTimer()<=0){
            G.console.log("Removing invulernability for " + player.getID());
            player.invulnerable = false;
        }
        F.applyInput(player, input);
        if(input.buttons.contains(Enums.Buttons.NUM1)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                player.setSlot2Weapon(1);
            }else{
                player.setSlot1Weapon(1);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM2)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                player.setSlot2Weapon(2);
            }else{
                player.setSlot1Weapon(2);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM3)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                player.setSlot2Weapon(3);
            }else{
                player.setSlot1Weapon(3);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM4)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                player.setSlot2Weapon(4);
            }else{
                player.setSlot1Weapon(4);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM5)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                player.setSlot2Weapon(5);
            }else{
                player.setSlot1Weapon(5);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM6)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                player.setSlot2Weapon(6);
            }else{
                player.setSlot1Weapon(6);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM7)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                player.setSlot2Weapon(7);
            }else{
                player.setSlot1Weapon(7);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM8)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                player.setSlot2Weapon(8);
            }else{
                player.setSlot1Weapon(8);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM9)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                player.setSlot2Weapon(9);
            }else{
                player.setSlot1Weapon(9);
            }
        }
        if(input.buttons.contains(Enums.Buttons.NUM0)){
            if(input.buttons.contains(Enums.Buttons.SHIFT)){
                player.setSlot2Weapon(10);
            }else{
                player.setSlot1Weapon(10);
            }
        }

        if(input.buttons.contains(Enums.Buttons.MOUSE1)){
            attack(player,player.getSlot1Weapon(),input.msec);
        }else if(input.buttons.contains(Enums.Buttons.MOUSE2)){
            attack(player,player.getSlot2Weapon(),input.msec);
        }else if(player.chargeMeter>0){
            launchAttack(player,player.chargeWeapon);
        }
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
        if(!e.canShoot(weaponID)){
            return;
        }
        e.chargeWeapon(weaponID, (msec / 1000f));
    }

    private void launchAttack(ServerEntity e, int weaponID){
        if(weaponList.get(weaponID)==null){
            return;
        }
        Weapon weapon = weaponList.get(weaponID);
        if(!e.canShoot(weaponID)){
            return;
        }
        e.hasfired(weaponID);

        Network.EntityAttacking entityAttacking = new Network.EntityAttacking();
        entityAttacking.x = e.getCenterX();
        entityAttacking.y = e.getCenterY();
        entityAttacking.deg = e.getRotation();
        entityAttacking.id = e.getID();
        entityAttacking.weapon = weaponID;

        if(e.isChargingWeapon(weaponID)){
            entityAttacking.projectileModifiers = new HashMap<>();
            float charge = e.chargeMeter/weapon.chargeDuration;
            if(charge>1) charge = 1;
            float velocity = weapon.minChargeVelocity+(weapon.maxChargeVelocity-weapon.minChargeVelocity)*charge;
            entityAttacking.projectileModifiers.put("velocity", velocity);
            if(weapon.projectile.duration>0){
                entityAttacking.projectileModifiers.put("duration",weapon.projectile.duration-e.chargeMeter);
            }
        }
        e.chargeMeter = 0;
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
            for(Line2D.Float hitscan : F.createHitscan(weapon, e.getCenterX(), e.getCenterY(), e.getRotation())){
                Vector2 intersection = F.findLineIntersectionPointWithTile(hitscan.x1, hitscan.y1, hitscan.x2, hitscan.y2);
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
                    Vector2 i = F.getLineIntersectionWithRectangle(hitscan, t.getGdxBounds());
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
                    particles.add(F.createTracer(projectileList.get(weapon.projectile.tracer), hitscan));
                }
            }
        }else if (projectileDefinition.type == ProjectileDefinition.PROJECTILE){
            //TODO projectiles with no duration or range will never get removed
            ArrayList<Particle> newProjectiles = F.createProjectiles(weapon, e.getCenterX(), e.getCenterY(), e.getRotation(), e.getID(), e.getTeam(), attack.projectileModifiers);
            particles.addAll(newProjectiles);
        }
    }

    private void createStationaryThing(String name, float x, float y, int id, int team){
        ProjectileDefinition def = projectileList.get(name);
        particles.add(F.createStationaryParticle(def, x, y, id, team));
        if(def.networked){
            listener.networkedProjectileSpawned(def.name,x,y,id,team);
        }
    }



    private HashMap<Integer,Weapon> readWeaponSlots(HashMap<String,Weapon> weapons){
        File file = new File(G.campaignFolder+File.separator+campaignName+File.separator+"weaponslots.txt");
        G.console.log("\nLoading weapon slots from file:" + file);
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

        G.console.log("Populating weaponlist");
        G.console.log("Primary weapons");
        int id = 1;
        for(int slot: primaries.keySet()){
            G.console.log("ID:" + id + "| Slot " + slot + ": " + weapons.get(primaries.get(slot)));
            weaponList.put(id,weapons.get(primaries.get(slot)));
            id++;
        }
        G.console.log("Secondary weapons");
        for(int slot: secondaries.keySet()){
            G.console.log("ID:" + id + "| Slot " + slot + ": " + weapons.get(secondaries.get(slot)));
            weaponList.put(id,weapons.get(secondaries.get(slot)));
            id++;
        }
        G.console.log("Storing primary weapon count: " + primaries.size());
        primaryWeaponCount = primaries.size();
        G.primaryWeaponCount = primaryWeaponCount;
        return weaponList;
    }

    private void addEntity(ServerEntity e){
        entities.put(e.getID(), e);
        listener.entityAdded(e.getNetworkEntity());
    }

    public void addPlayer(Connection c){

        int networkID = getNextNetworkID();
        playerList.add(networkID);
        int width = (int) (ConVars.getInt("sv_npc_default_size")* G.mapScale);
        int height = (int) (ConVars.getInt("sv_npc_default_size")* G.mapScale);
        float x;
        float y;
        if(playerSpawn!=null){
            x = MathUtils.random(playerSpawn.getX(), playerSpawn.getX() + playerSpawn.getWidth() - width);
            y = MathUtils.random(playerSpawn.getY(),playerSpawn.getY()+playerSpawn.getHeight()-height);
        }else{
            x = MathUtils.random(G.mapWidth-width);
            y = MathUtils.random(G.mapHeight-height);
        }

        HashMap<Integer,Boolean> entityWeapons = new HashMap<>();
        HashMap<String,Integer> entityAmmo = new HashMap<>();
        for(int weaponID : weaponList.keySet()) {
            entityWeapons.put(weaponID, false);
            if(weaponList.get(weaponID).ammo!=null) {
                entityAmmo.put(weaponList.get(weaponID).ammo, 0);
            }
        }
        if(ConVars.getBool("sv_idkfa")){
            for(int weaponID : weaponList.keySet()){
                entityWeapons.put(weaponID, true);
            }
            for(String ammoType : entityAmmo.keySet()) {
                entityAmmo.put(ammoType, 999999999);
            }
        }
        //Start with primary weapon
        entityWeapons.put(1, true);
        //Start with secondary weapon
        entityWeapons.put(G.primaryWeaponCount + 1, true);

        PlayerServerEntity newPlayer = new PlayerServerEntity(networkID,x,y,width,height,
                ConVars.getInt("sv_player_max_health"),ConVars.getInt("sv_player_default_team"),
                "rambo",entityWeapons,entityAmmo,ConVars.getFloat("sv_player_velocity"),0,this,listener);
        newPlayer.setLives(ConVars.getInt("sv_start_lives"));
        newPlayer.invulnerable = true;
        addEntity(newPlayer);

        Network.AssignEntity assign = new Network.AssignEntity();
        assign.networkID = networkID;
        assign.lives = newPlayer.getLives();
        assign.velocity = ConVars.getFloat("sv_player_velocity");
        assign.campaign = campaignName;
        assign.mapName = mapName;
        assign.playerList = new ArrayList<>(playerList);
        assign.powerups = new HashMap<>(powerups);
        assign.wave = wave;
        assign.questCompleted = questComplete;
        assign.weaponList = new HashMap<>(weaponList);
        assign.primaryWeaponCount = primaryWeaponCount;
        assign.projectileList = new HashMap<>(projectileList);
        assign.shoplist = new HashMap<>(G.shoplist);
        assign.ammo = entityAmmo;
        assign.weapons = entityWeapons;

        listener.playerAdded(c,assign);
    }

    public void removePlayer(int id){
        pendingEntityRemovals.add(id);
    }

    public void addNPC(EnemyDefinition def){
        int width = (int) (ConVars.getInt("sv_npc_default_size")* G.mapScale);
        int height = (int) (ConVars.getInt("sv_npc_default_size")* G.mapScale);
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
                    x = G.mapWidth+spawnPosition;
                }else{
                    x = spawnPosition;
                }
                y = MathUtils.random(0-SPAWN_AREA_WIDTH, G.mapHeight+SPAWN_AREA_WIDTH);
            }else{
                if(spawnPosition >= 0){
                    y = G.mapHeight+spawnPosition;
                }else{
                    y = spawnPosition;
                }
                x = MathUtils.random(0-SPAWN_AREA_WIDTH, G.mapWidth + SPAWN_AREA_WIDTH);
            }
        }
        addNPC(def, new Rectangle(x, y, width, height));
    }

    public void addNPC(EnemyDefinition def, Rectangle bounds){

        G.console.log("Adding npc:" + def + " at " + bounds);
        int networkID = getNextNetworkID();

        HashMap<Integer,Boolean> entityWeapons = new HashMap<>();
        HashMap<String,Integer> entityAmmo = new HashMap<>();
        for(int weaponID : weaponList.keySet()) {
            entityWeapons.put(weaponID, false);
            if(weaponList.get(weaponID).ammo!=null) {
                entityAmmo.put(weaponList.get(weaponID).ammo, 0);
            }
        }

        //Lots of ammo for weapon
        if(def.weapon!=-1){
            entityWeapons.put(def.weapon, true);
            if(weaponList.get(def.weapon).ammo!=null){
                entityAmmo.put(weaponList.get(def.weapon).ammo, 999999999);
            }
        }

        NpcServerEntity npc = new NpcServerEntity(networkID,bounds.x,bounds.y,bounds.width,bounds.height,-1,def,this);
        entityAIs.put(networkID, new EntityAI(npc, EntityAI.FOLLOWING_AND_SHOOTING, def.weapon, this));

        addEntity(npc);
    }

    public void addRandomNPC(){
        EnemyDefinition def = enemyList.values().toArray(new EnemyDefinition[enemyList.size()])[MathUtils.random(enemyList.size()-1)];
        addNPC(def);
    }

    public void spawnPowerup(Powerup p){
        final int id = getNextNetworkID();
        powerups.put(id, p);
        listener.powerupAdded(id, p);
    }

    public void spawnHealthPack(int value, int duration){

        Rectangle bounds = new Rectangle(0,0,40,40);
        //Try 20 times to spawn it outside a wall after that just spawn it anywhere
        for (int i = 0; i < 20; i++) {
            bounds.setX(MathUtils.random(0, G.mapWidth));
            bounds.setY(MathUtils.random(0, G.mapHeight));
            if(!F.checkMapCollision(bounds)){
                break;
            }
        }
        final int id = getNextNetworkID();
        HealthPack health = new HealthPack(bounds.x,bounds.y,40,40,value);
        powerups.put(id,health);

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                despawnPowerup(id);
            }
        };
        timer.schedule(task, Tools.secondsToMilli(duration));
        listener.powerupAdded(id, health);
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
        changedEntities.addAll(slot1ChangedEntities);
        changedEntities.addAll(slot2ChangedEntities);
        changedEntities.addAll(maxHealthChangedEntities);

        for(int id: changedEntities){
            ArrayList<Component> components = new ArrayList<>();
            changedComponents.put(id,components);
            if(posChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Component.Position(e.getX(),e.getY()));
            }
            if(healthChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Component.Health(e.getHealth()));
            }
            if(rotationChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Component.Rotation(e.getRotation()));
            }
            if(teamChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Component.Team(e.getTeam()));
            }
            if(slot1ChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Component.Slot1(e.getSlot1Weapon()));
            }
            if(slot2ChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Component.Slot2(e.getSlot2Weapon()));
            }
            if(maxHealthChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Component.MaxHealth(e.getMaxHealth()));
            }
        }
        posChangedEntities.clear();
        healthChangedEntities.clear();
        rotationChangedEntities.clear();
        teamChangedEntities.clear();
        slot1ChangedEntities.clear();
        slot2ChangedEntities.clear();
        maxHealthChangedEntities.clear();
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

    private void removeEntity(int networkID) {
        entityAIs.remove(networkID);
        entities.remove(networkID);

        healthChangedEntities.remove(networkID);
        posChangedEntities.remove(networkID);
        teamChangedEntities.remove(networkID);
        rotationChangedEntities.remove(networkID);
        slot1ChangedEntities.remove(networkID);
        slot2ChangedEntities.remove(networkID);
        maxHealthChangedEntities.remove(networkID);

        if(playerList.remove(networkID)){
            listener.playerRemoved(networkID);
        }
        listener.entityRemoved(networkID);
    }

    public int getEntityCount(){
        return entities.size();
    }

    public ServerEntity getEntity(int id){
        return entities.get(id);
    }

    public void buyItem(int playerID, int itemID, int amount){
        PlayerServerEntity player = (PlayerServerEntity) entities.get(playerID);
        ShopItem item = G.shoplist.get(itemID);
        //FIXME mapping shop item to weapon or ammo seems pretty unreliable
        if(G.ammoList.contains(item.name)){
            int price = item.value*amount;
            if(player.getCash() >= price){
                player.changeCash(-price);
            }else if(player.getCash() >= item.value) {
                //If cant buy all buy as many as can afford
                amount = player.getCash()/item.value;
                price = item.value*amount;
                player.changeCash(-price);
            }else{
                return;
            }
            player.changeAmmo(item.name, amount);
        } else if(G.weaponNameToID.containsKey(item.name)){
            int weaponID = G.weaponNameToID.get(item.name);
            if(player.hasWeapon(weaponID)){
                return;
            }
            if(player.getCash()>= item.value){
                player.changeCash(-item.value);
            }else{
                return;
            }
            player.setWeapon(weaponID,true);
        } else {
            if (item.type.equals("maxHealth")) {
                if(player.getCash()>=item.value){
                    player.changeCash(-item.value);
                    int maxHealth = Integer.parseInt(item.name);
                    player.setMaxHealth(player.getMaxHealth()+maxHealth);
                }
            }else if(item.type.equals("health")){
                if(player.getHealth()==player.getMaxHealth()){
                    return;
                }
                if(player.getCash()>=item.value){
                    player.changeCash(-item.value);
                    int health = Integer.parseInt(item.name);
                    player.addHealth(health);
                }
            }
        }
    }

    public void sellItem(int playerID, int itemID, int amount){
        PlayerServerEntity player = (PlayerServerEntity) entities.get(playerID);
        ShopItem item = G.shoplist.get(itemID);
        //FIXME mapping shop item to weapon or ammo seems pretty unreliable
        if(G.ammoList.contains(item.name)){
            int value;
            if(player.getAmmo(item.name) >= amount){
                value = item.value*amount;
            }else if(player.getAmmo(item.name)< amount && player.getAmmo(item.name) > 0){
                amount = player.getAmmo(item.name);
                value = item.value*amount;
            }else{
                return;
            }
            player.changeAmmo(item.name, -amount);
            //Full sell price for ammo
            player.changeCash(value);
        }else if(G.weaponNameToID.containsKey(item.name)){
            int weaponID = G.weaponNameToID.get(item.name);
            if(!player.hasWeapon(weaponID)){
                return;
            }
            player.setWeapon(weaponID,false);
            //Half sell price for weapons
            player.changeCash(item.value/2);
        }else{

        }
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
    public void maxHealthChanged(int id) {
        maxHealthChangedEntities.add(id);
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
            PlayerServerEntity player = (PlayerServerEntity) entities.get(id);
            player.setLives(player.getLives() - 1);
            listener.playerDied(id, player.getLives());

            if(player.getLives() >= 0){
                //Respawn player if lives left
                player.setRespawnTimer(5);
            }
            player.invulnerable = true;
        }else{
            pendingEntityRemovals.add(id);
        }
        if(playerList.contains(sourceID)){
            PlayerServerEntity player = (PlayerServerEntity) entities.get(sourceID);
            ServerEntity target = entities.get(id);
            if(target instanceof NpcServerEntity){
                NpcServerEntity npc = (NpcServerEntity) target;
                player.changeCash(npc.bounty);
            }
        }
    }

    @Override
    public void slot1WeaponChanged(int id) {
        slot1ChangedEntities.add(id);
    }

    @Override
    public void slot2WeaponChanged(int id) {
        slot2ChangedEntities.add(id);
    }

    interface WorldChangeListener{
        public void playerAdded(Connection c, Network.AssignEntity assign);
        public void playerDied(int id, int livesLeft);
        public void playerRespawned(int id, float x, float y);
        public void playerRemoved(int id);
        public void entityRemoved(int id);
        public void entityKilled(int victimID, int killerID);
        public void entityAdded(NetworkEntity e);
        public void powerupAdded(int id, Powerup powerup);
        public void powerupRemoved(int id);
        public void waveChanged(int wave);
        public void attackCreated(Network.EntityAttacking entityAttacking, Weapon weapon);
        public void mapChanged(String mapName);
        public void mapCleared(float timer);
        public void playerAmmoChanged(int id, String ammoType, int value);
        public void playerWeaponChanged(int id, int weapon, boolean state);
        public void networkedProjectileSpawned(String projectileName, float x, float y, int ownerID, int team);
        public void playerCashChanged(int id, int amount);
        public void reassignPlayers();
    }
}
