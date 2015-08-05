package com.juniperbrew.minimus.server;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.Polygon;
import com.esotericsoftware.kryonet.Connection;
import com.juniperbrew.minimus.*;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.components.Heading;
import com.juniperbrew.minimus.components.Health;
import com.juniperbrew.minimus.components.Position;
import com.juniperbrew.minimus.components.Rotation;
import com.juniperbrew.minimus.components.Team;

import java.awt.*;
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

    Set<Integer> playerList = new HashSet<>();
    Map<Integer,Double> attackCooldown = new HashMap<>();
    Map<Integer,Integer> playerLives = new HashMap<>();

    ConcurrentHashMap<Integer,ServerEntity> entities = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer,Powerup> powerups = new ConcurrentHashMap<>();
    HashMap<Integer,EntityAI> entityAIs = new HashMap<>();

    Set<Integer> posChangedEntities = new HashSet<>();
    Set<Integer> healthChangedEntities = new HashSet<>();
    Set<Integer> headingChangedEntities = new HashSet<>();
    Set<Integer> rotationChangedEntities = new HashSet<>();
    Set<Integer> teamChangedEntities = new HashSet<>();

    ArrayList<Integer> pendingEntityRemovals = new ArrayList<>();

    private int networkIDCounter = 1;

    private ConcurrentLinkedQueue<AttackVisual> attackVisuals = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Projectile> projectiles = new ConcurrentLinkedQueue<>();

    int mapWidth;
    int mapHeight;

    TiledMap map;
    private int wave;
    private int spawnedHealthPacksCounter;
    private long lastHealthPackSpawned;
    private final int HEALTHPACK_SPAWN_DELAY = 10;

    HashMap<Integer,WaveDefinition> waveList;
    HashMap<Integer,Weapon> weaponList;
    HashMap<String,ProjectileDefinition> projectileList;

    //This should not be more than 400 which is the max distance entities can look for a destination
    final int SPAWN_AREA_WIDTH = 200;
    public boolean spawnWaves;
    WorldChangeListener listener;

    Timer timer = new Timer();

    public World(WorldChangeListener listener, TiledMap map){
        this.listener = listener;
        waveList = readWaveList();
        projectileList = readProjectileList();
        weaponList = readWeaponList(projectileList);

        this.map = map;
        mapHeight = (int) ((Integer) map.getProperties().get("height")*(Integer) map.getProperties().get("tileheight")* ConVars.getDouble("sv_map_scale"));
        mapWidth = (int) ((Integer) map.getProperties().get("width")*(Integer) map.getProperties().get("tilewidth")* ConVars.getDouble("sv_map_scale"));
    }

    public void updateWorld(float delta){

        for(int id: pendingEntityRemovals){
            removeEntity(id);
        }
        pendingEntityRemovals.clear();

        updateWaves();
        updateEntityAI(delta);
        updateProjectiles(delta);
        checkPlayerEntityCollisions();
        checkPowerupCollisions();
    }

    private void updateWaves(){
        if(spawnWaves){
            if(entityAIs.isEmpty()){
                spawnedHealthPacksCounter=0;
                if(ConVars.getBool("sv_custom_waves")){
                    spawnNextCustomWave();
                }else{
                    spawnNextWave();
                }
            }
            WaveDefinition waveDefinition = waveList.get(wave);
            if(waveDefinition!=null){
                if(spawnedHealthPacksCounter < waveDefinition.healthPackCount){
                    if(System.nanoTime()-lastHealthPackSpawned > Tools.secondsToNano(HEALTHPACK_SPAWN_DELAY)){
                        lastHealthPackSpawned = System.nanoTime();
                        spawnedHealthPacksCounter++;
                        spawnPowerup(Powerup.HEALTH,30,30);
                    }
                }
            }
        }
    }

    private void updateEntityAI(float delta){
        for(EntityAI ai:entityAIs.values()){
            ai.act(ConVars.getDouble("sv_npc_velocity"), delta);
        }
    }

    private void updateProjectiles(float delta){
        ArrayList<Projectile> destroyedProjectiles = new ArrayList<>();
        for(Projectile projectile:projectiles){
            projectile.move(delta);
            //TODO hit detection no longer is the line projectile has travelled so its possible to go through thin objects

            for(int id:entities.keySet()){
                if(projectile.ownerID==id){
                    continue;
                }
                ServerEntity target = entities.get(id);
                if(Intersector.overlapConvexPolygons(projectile.getHitbox(), target.getPolygonBounds())){
                    projectile.destroyed = true;
                    if(target.getTeam() != projectile.team){
                        target.reduceHealth(projectile.damage,projectile.ownerID);
                    }
                }
            }
            if(projectile.destroyed){
                destroyedProjectiles.add(projectile);
            }
        }
        projectiles.removeAll(destroyedProjectiles);
    }

    private void checkPlayerEntityCollisions(){

        for(int playerID : playerList){
            ServerEntity player = entities.get(playerID);
            Iterator<ServerEntity> iter = entities.values().iterator();
            while(iter.hasNext()){
                ServerEntity e = iter.next();
                if(e.getTeam()!=player.getTeam()){
                    if(player.getJavaBounds().intersects(e.getJavaBounds())){
                        if(!isInvulnerable(player)) {
                            player.lastContactDamageTaken = System.nanoTime();
                            player.reduceHealth(ConVars.getInt("sv_contact_damage"),e.id);
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
                if(player.getJavaBounds().contains(p.x,p.y)){
                    if(p.type == Powerup.HEALTH){
                        player.addHealth(p.value);
                        despawnPowerup(powerupID);
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
        SharedMethods.applyInput(e, input, mapWidth, mapHeight);
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
            double cd = attackCooldown.get(id);
            cd -= (input.msec/1000d);
            attackCooldown.put(id,cd);
        }
    }

    private void attackWithPlayer(int id, int weapon){
        if(attackCooldown.get(id)==null){
            attackCooldown.put(id,-1d);
        }
        if(attackCooldown.get(id) > 0){
            return;
        }else{
            attackCooldown.put(id,ConVars.getDouble("sv_attack_delay"));
            createAttack(id, weapon);
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
            ArrayList<AttackVisual> hitScans = SharedMethods.createHitscanAttack(weapon,e.getCenterX(),e.getCenterY(),e.getRotation(), attackVisuals);

            for(int targetId:entities.keySet()){
                ServerEntity target = entities.get(targetId);
                for(AttackVisual hitScan:hitScans){
                    if(Intersector.overlapConvexPolygons(hitScan.getHitbox(), target.getPolygonBounds()) && target.getTeam() != e.getTeam()){
                        target.reduceHealth(projectileDefinition.damage,e.id);
                    }
                }
            }
        }else{
            projectiles.addAll(SharedMethods.createProjectileAttack(weapon,e.getCenterX(),e.getCenterY(),e.getRotation(),e.id, e.getTeam()));
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
        System.out.println("Loading weapons from file:"+file);
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

    private void addEntity(ServerEntity e){
        entities.put(e.id, e);
        listener.entityAdded(e.getNetworkEntity());
    }

    public void addPlayer(Connection c){

        int networkID = getNextNetworkID();
        playerList.add(networkID);
        int width = 50;
        int height = 50;
        float x = MathUtils.random(mapWidth -width);
        float y = MathUtils.random(mapHeight -height);
        playerLives.put(networkID, ConVars.getInt("sv_start_lives"));
        ServerEntity newPlayer = new ServerEntity(networkID,x,y,1,this);
        newPlayer.invulnerable = true;
        newPlayer.width = width;
        newPlayer.height = height;
        addEntity(newPlayer);

        Network.AssignEntity assign = new Network.AssignEntity();
        assign.networkID = networkID;
        assign.lives = playerLives.get(networkID);
        assign.velocity = ConVars.getFloat("sv_player_velocity");
        assign.mapName = ConVars.get("sv_map_name");
        assign.mapScale = ConVars.getFloat("sv_map_scale");
        assign.playerList = new ArrayList<>(playerList);
        assign.powerups = new HashMap<>(powerups);
        assign.wave = wave;
        assign.weaponList = new HashMap<>(weaponList);

        listener.playerAdded(c,assign);
    }

    public void removePlayer(int id){
        pendingEntityRemovals.add(id);
    }

    private void addNPC(int aiType, int weapon){
        System.out.println("Adding npc "+aiType+","+weapon);
        int width = 50;
        int height = 50;
        float spawnPosition = MathUtils.random(SPAWN_AREA_WIDTH * -1, SPAWN_AREA_WIDTH);
        float x;
        float y;
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
            x = MathUtils.random(0-SPAWN_AREA_WIDTH,mapWidth+SPAWN_AREA_WIDTH);
        }
        int networkID = getNextNetworkID();
        ServerEntity npc = new ServerEntity(networkID,x,y,-1,this);
        npc.height = height;
        npc.width = width;
        npc.reduceHealth(10,-1);
        int randomHeading = MathUtils.random(Enums.Heading.values().length - 1);
        npc.setHeading(Enums.Heading.values()[randomHeading]);
        entityAIs.put(networkID,new EntityAI(npc,aiType,weapon,this));
        addEntity(npc);
    }

    public void addRandomNPC(){
        addNPC(MathUtils.random(0,3),MathUtils.random(0,2));
    }

    private boolean isInvulnerable(ServerEntity e){
        if(System.nanoTime()-e.lastContactDamageTaken > Tools.secondsToNano(ConVars.getDouble("sv_invulnerability_timer"))){
            return false;
        }else{
            return true;
        }
    }

    public void spawnPowerup(int type, int value, int duration){
        int x = MathUtils.random(0,mapWidth);
        int y = MathUtils.random(0,mapHeight);
        final int id = getNextNetworkID();
        Powerup powerup = new Powerup(x,y,type,value);
        powerups.put(id,powerup);

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                despawnPowerup(id);
            }
        };
        timer.schedule(task,Tools.secondsToMilli(duration));
        listener.powerupAdded(id,powerup);
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

    private HashMap<Integer,WaveDefinition> readWaveList(){
        File file = new File(Tools.getUserDataDirectory()+ File.separator+"wavelist.txt");
        if(!file.exists()){
            file = new File("resources"+ File.separator+"defaultwavelist.txt");
        }
        System.out.println("Loading custom wave from file:"+file);
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
        changedEntities.addAll(headingChangedEntities);
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
            if(headingChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Heading(e.getHeading()));
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
        headingChangedEntities.clear();
        healthChangedEntities.clear();
        rotationChangedEntities.clear();
        teamChangedEntities.clear();
        return changedComponents;
    }

    public HashMap<Integer,Entity> getNetworkedEntityList(){
        HashMap<Integer,Entity> networkedEntities = new HashMap<>();
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

    private void spawnNextCustomWave(){

        WaveDefinition waveDef = waveList.get(wave+1);
        if(waveDef!=null){
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

    private void spawnNextWave(){
        setWave(wave + 1);
        for (int i = 0; i < (wave*2)+4; i++) {
            addRandomNPC();
        }
    }

    public void removeAllEntities(){
        for(int id:entityAIs.keySet()) {
            pendingEntityRemovals.add(id);
        }
    }

    public void render(ShapeRenderer shapeRenderer){
        Gdx.gl.glLineWidth(3);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0,0,0,1);
        shapeRenderer.rect(0, 0, mapWidth, mapHeight);
        shapeRenderer.setColor(1,1,1,1);

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
        for(Powerup p : powerups.values()){
            shapeRenderer.setColor(1, 0.4f, 0, 1); //safety orange
            shapeRenderer.circle(p.x,p.y,5);
        }

        shapeRenderer.end();

        SharedMethods.renderAttackVisuals(shapeRenderer,attackVisuals);
        SharedMethods.renderProjectiles(shapeRenderer,projectiles);
    }

    private void removeEntity(int networkID){
        entityAIs.remove(networkID);
        entities.remove(networkID);

        healthChangedEntities.remove(networkID);
        posChangedEntities.remove(networkID);
        headingChangedEntities.remove(networkID);
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
    public void headingChanged(int id) {
        headingChangedEntities.add(id);
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
        listener.entityKilled(id,sourceID);
        if (playerList.contains(id)) {
            Tools.addToMap(playerLives, id, -1);
            listener.playerLivesChanged(id, playerLives.get(id));
            if(playerLives.get(id) >= 0){
                //Respawn player if lives left
                ServerEntity deadPlayer = entities.get(id);
                deadPlayer.restoreMaxHealth();
                float x = MathUtils.random(mapWidth - deadPlayer.width);
                float y = MathUtils.random(mapHeight - deadPlayer.height);
                deadPlayer.moveTo(x, y);
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
        public void entityAdded(Entity e);
        public void powerupAdded(int id, Powerup powerup);
        public void powerupRemoved(int id);
        public void waveChanged(int wave);
        public void attackCreated(Network.EntityAttacking entityAttacking);
    }
}
