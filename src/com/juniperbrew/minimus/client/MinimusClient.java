package com.juniperbrew.minimus.client;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.kryonet.Listener;
import com.juniperbrew.minimus.*;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.components.Health;
import com.juniperbrew.minimus.components.Position;
import com.juniperbrew.minimus.components.Rotation;
import com.juniperbrew.minimus.components.Team;
import com.juniperbrew.minimus.windows.ClientStatusFrame;
import com.juniperbrew.minimus.windows.ConsoleFrame;
import com.juniperbrew.minimus.windows.StatusData;

import java.awt.geom.Line2D;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class MinimusClient implements ApplicationListener, InputProcessor,Score.ScoreChangeListener, ConVars.ConVarChangeListener {

    Client client;
    @SuppressWarnings("FieldCanBeLocal")
    private int writeBuffer = 8192; //Default 8192
    private int objectBuffer = 8192; //Default 2048
    ShapeRenderer shapeRenderer;

    //Counter to give each input request an unique id
    private int currentInputRequest = 0;
    //Input requests that haven't been sent yet
    ArrayList<Network.UserInput> pendingInputPacket = new ArrayList<>();
    private long lastInputSent;
    //Sent input requests that haven't been acknowledged on server yet
    ArrayList<Network.UserInput> inputQueue = new ArrayList<>();

    //Stored states used for interpolation, each time a state snapshot is created we remove all states older than clientTime-interpolation,
    //one older state than this is still stored in interpFrom untill we reach the next interpolation interval
    ArrayList<Network.FullEntityUpdate> stateHistory = new ArrayList<>();
    //This stores the state with latest servertime, should be same as last state in stateHistory
    Network.FullEntityUpdate authoritativeState;
    //This is basically authoritativeState.serverTime
    float lastServerTime;
    //System.nanoTime() for when authoritativeState is changed
    long lastAuthoritativeStateReceived;


    long lastUpdateDelay = 1;

    //This is what we render
    private HashMap<Integer,NetworkEntity> stateSnapshot = new HashMap<>();
    private HashMap<Integer,ClientEntity> entities = new HashMap<>();
    private PlayerClientEntity player;

    private ClientEntity ghostPlayer;
    private LinkedHashMap<Integer,Vector2> predictedPositions = new LinkedHashMap<>();

    //StateSnapshot is an interpolation between these two states
    private Network.FullEntityUpdate interpFrom;
    private Network.FullEntityUpdate interpTo;

    private ConcurrentLinkedQueue<Object> pendingPackets = new ConcurrentLinkedQueue<>();

    private HashMap<Integer,Powerup> powerups = new HashMap<>();
    ArrayList<Integer> playerList = new ArrayList<>();

    int currentWave;
    EnumSet<Enums.Buttons> buttons = EnumSet.noneOf(Enums.Buttons.class);
    private ConcurrentLinkedQueue<Projectile> projectiles = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Particle> particles = new ConcurrentLinkedQueue<>();

    long lastPingRequest;
    long renderStart;
    long logIntervalStarted;
    long clientStartTime;

    String serverIP;

    StatusData statusData;
    ClientStatusFrame statusFrame;
    ConsoleFrame consoleFrame;

    private OrthographicCamera camera;
    private OrthographicCamera hudCamera;

    int mapWidth;
    int mapHeight;

    ByteBuffer buffer = ByteBuffer.allocate(objectBuffer);

    //Animation playerAnimation;
    float animationFrameTime = 0.15f;
    SpriteBatch batch;

    Score score;

    HashMap<String,Sound> sounds = new HashMap<>();
    TextureAtlas atlas;
    HashMap<String,TextureRegion> textures = new HashMap<>();
    HashMap<String,Animation> animations = new HashMap<>();

    Music backgroundMusic;

    TiledMap map;
    OrthogonalTiledMapRenderer mapRenderer;

    BitmapFont font;
    GlyphLayout glyphLayout = new GlyphLayout();
    int windowWidth;
    int windowHeight;

    boolean mouse1Pressed;
    boolean mouse2Pressed;

    boolean autoWalk;

    int lives;
    float soundVolume;
    float musicVolume;

    HashMap<Integer,Weapon> weaponList;
    HashMap<String,ProjectileDefinition> projectileList;

    float lastMouseX = -1;
    float lastMouseY = -1;

    Line2D.Float tracer;

    public MinimusClient(String ip) throws IOException {
        serverIP = ip;
        ConVars.addListener(this);
        soundVolume = ConVars.getFloat("cl_volume_sound");
        musicVolume = ConVars.getFloat("cl_volume_music");
        consoleFrame = new ConsoleFrame(this);
        new ConsoleReader(consoleFrame);
        clientStartTime = System.nanoTime();
        score = new Score(this);
        client = new Client(writeBuffer,objectBuffer);
        statusData = new StatusData(client,clientStartTime,ConVars.getInt("cl_log_interval_seconds"));
        statusFrame = new ClientStatusFrame(statusData);
        joinServer();
    }

    @Override
    public void create() {
        if(ConVars.getBool("cl_auto_send_errorlogs")){
            Thread.setDefaultUncaughtExceptionHandler(new ExceptionLogger("client", true, serverIP));
        }else{
            Thread.setDefaultUncaughtExceptionHandler(new ExceptionLogger("client"));
        }
        windowWidth = Gdx.graphics.getWidth();
        windowHeight = Gdx.graphics.getHeight();
        showMessage("Window size: " + windowWidth + "x" + windowHeight);
        camera = new OrthographicCamera(windowWidth,windowHeight);
        hudCamera = new OrthographicCamera(windowWidth,windowHeight);
        camera.zoom = ConVars.getFloat("cl_zoom");
        hudCamera.position.set(windowWidth/2,windowHeight/2,0);
        hudCamera.update();

        Gdx.input.setInputProcessor(this);

        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        font.setColor(Color.RED);

        loadSounds();
        loadTextures();

        backgroundMusic = Gdx.audio.newMusic(Gdx.files.internal("resources"+File.separator+"taustamuusik.mp3"));
        backgroundMusic.setLooping(true);
        backgroundMusic.setVolume(musicVolume);
        backgroundMusic.play();
    }

    private void handlePacket(Object object){
        if(object instanceof Network.FullEntityUpdate){
            Network.FullEntityUpdate fullUpdate = (Network.FullEntityUpdate) object;
            addServerState(fullUpdate);
            showMessage("Received full update");
            for(int id:fullUpdate.entities.keySet()){
                if(!entities.containsKey(id)){
                    addEntity(fullUpdate.entities.get(id));
                }
            }
        }else if(object instanceof Network.EntityPositionUpdate){
            Network.EntityPositionUpdate update = (Network.EntityPositionUpdate) object;
            if(authoritativeState!=null) {
                addServerState(applyEntityPositionUpdate(update));
            }else{
                showMessage("Received entity position update but there is no complete state to apply it to");
            }
        }else if(object instanceof Network.EntityComponentsUpdate){
            Network.EntityComponentsUpdate update = (Network.EntityComponentsUpdate) object;
            if (authoritativeState != null) {
                addServerState(applyEntityComponentUpdate(update));
            }else{
                showMessage("Received entity component update but there is no complete state to apply it to");
            }
        }else if(object instanceof Network.EntityAttacking){
            Network.EntityAttacking attack = (Network.EntityAttacking) object;
            addAttack(attack);
        }else if(object instanceof Network.AddDeath){
            Network.AddDeath addDeath = (Network.AddDeath) object;
            score.addDeath(addDeath.id);
        }else if(object instanceof Network.AddPlayerKill){
            Network.AddPlayerKill addPlayerKill = (Network.AddPlayerKill) object;
            score.addPlayerKill(addPlayerKill.id);
        }else if(object instanceof Network.AddNpcKill){
            Network.AddNpcKill addNpcKill = (Network.AddNpcKill) object;
            score.addNpcKill(addNpcKill.id);
        }else if(object instanceof Network.AddPlayer){
            Network.AddPlayer addPlayer = (Network.AddPlayer) object;
            showMessage("PlayerID "+addPlayer.networkID+" added.");
            playerList.add(addPlayer.networkID);
            score.addPlayer(addPlayer.networkID);
        }else if(object instanceof Network.AddEntity){
            Network.AddEntity addEntity = (Network.AddEntity) object;
            showMessage("Adding entity " + addEntity.entity.id);
            addEntity(addEntity.entity);
        }else if(object instanceof Network.RemoveEntity){
            Network.RemoveEntity removeEntity = (Network.RemoveEntity) object;
            removeEntity(removeEntity.networkID);
        }else if(object instanceof Network.AssignEntity){
            Network.AssignEntity assign = (Network.AssignEntity) object;
            showMessage("Assigning entity "+assign.networkID+" for player.");
            Gdx.graphics.setTitle(this.getClass().getSimpleName()+"["+assign.networkID+"]");
            ConVars.set("sv_player_velocity", assign.velocity);

            loadMap(assign.mapName);
            lives = assign.lives;

            playerList = assign.playerList;
            powerups = assign.powerups;
            currentWave = assign.wave;
            weaponList = assign.weaponList;
            projectileList = assign.projectileList;
            player = new PlayerClientEntity(assign.networkID,assign.weapons,assign.ammo);
            ghostPlayer = new ClientEntity();
            for(int id : playerList){
                showMessage("Adding entity " + id + " to score");
                score.addPlayer(id);
            }

        }else if(object instanceof Network.WaveChanged){
            Network.WaveChanged waveChanged = (Network.WaveChanged) object;
            currentWave = waveChanged.wave;
        }else if(object instanceof Network.SetLives){
            Network.SetLives setLives = (Network.SetLives) object;
            lives = setLives.lives;
        }else if(object instanceof Network.AddPowerup){
            Network.AddPowerup addPowerup = (Network.AddPowerup) object;
            powerups.put(addPowerup.networkID,addPowerup.powerup);
        }else if(object instanceof Network.RemovePowerup){
            Network.RemovePowerup removePowerup = (Network.RemovePowerup) object;
            powerups.remove(removePowerup.networkID);
        }else if(object instanceof Network.GameClockCompare){
            Network.GameClockCompare gameClockCompare = (Network.GameClockCompare) object;
            showMessage("Received gameClockCompare: "+gameClockCompare.serverTime + " Delta: "+(gameClockCompare.serverTime-getClientTime()));
            sendTCP(gameClockCompare);
        }else if(object instanceof Network.MapChange){
            Network.MapChange mapChange = (Network.MapChange) object;
            changeMap(mapChange);
        }else if(object instanceof Network.AddAmmo){
            Network.AddAmmo addAmmo = (Network.AddAmmo) object;
            int a = player.ammo.get(addAmmo.weapon);
            a += addAmmo.amount;
            player.ammo.put(addAmmo.weapon,a);
        }else if(object instanceof Network.WeaponAdded){
            Network.WeaponAdded weaponAdded = (Network.WeaponAdded) object;
            player.weapons.put(weaponAdded.weapon,true);
        }else if(object instanceof Network.SpawnProjectile){
            Network.SpawnProjectile spawnProjectile = (Network.SpawnProjectile) object;
            ProjectileDefinition def = projectileList.get(spawnProjectile.projectileName);
            projectiles.add(SharedMethods.createStationaryProjectile(def, spawnProjectile.x, spawnProjectile.y,
                    spawnProjectile.ownerID, spawnProjectile.team));
            if(def.sound!=null){
                playSoundInLocation(sounds.get(def.sound),spawnProjectile.x,spawnProjectile.y);
            }
        }

        if(object instanceof String){
            String command = (String) object;
            consoleFrame.giveCommand(command);
        }
    }

    private void changeMap(Network.MapChange mapChange){
        loadMap(mapChange.mapName);
        powerups = mapChange.powerups;
        projectiles.clear();
        particles.clear();
    }

    private void doLogic(float delta){

        if(System.nanoTime()- logIntervalStarted > Tools.secondsToNano(ConVars.getInt("cl_log_interval_seconds"))){
            logIntervalStarted = System.nanoTime();
            statusData.intervalElapsed();
        }
        if(System.nanoTime()-lastPingRequest>Tools.secondsToNano(ConVars.getDouble("cl_ping_update_delay"))){
            statusData.updatePing();
            updateFakePing();
            lastPingRequest = System.nanoTime();
        }
        for(Object o;(o = pendingPackets.poll())!=null;){
            handlePacket(o);
        }

        for(ClientEntity e : entities.values()) {
            e.fireAnimationTimer -= delta;
            e.animationState += delta;
        }

        /*
        double squaredDistance = Math.pow(ConVars.getFloat("sv_npc_target_search_radius"),2);
        ArrayList<ClientEntity> entitiesInRange = new ArrayList<>();
        for(ClientEntity e : entities.values()){
            e.fireAnimationTimer -= delta;
            e.animationState += delta;
            float distance = Tools.getSquaredDistance(player.getCenterX(),player.getCenterY(),e.getCenterX(),e.getCenterY());
            if(distance<squaredDistance){
                if(!playerList.contains(e.getID())){
                    entitiesInRange.add(e);
                }
            }
        }

        //Make enemies ready their weapon when they get close
        //FIXME this vision goes through walls
        for(ClientEntity e: entitiesInRange){
            e.aimingWeapon = true;
        }
        */

        player.updateCooldowns(delta);

        sendInputPackets();
        updateProjectiles(delta);
        updateParticles(delta);

        //Dont pollInput or create snapshot if we have no state from server
        if (authoritativeState != null){
            if(player!=null){
                pollInput(delta);
            }
            createStateSnapshot(getClientTime());
            updateNetworkedState(stateSnapshot);
            correctPlayerPositionError();
            runClientSidePrediction();
            statusData.setEntityCount(entities.size());
        }
        statusData.setFps(Gdx.graphics.getFramesPerSecond());
        statusData.setServerTime(lastServerTime);
        statusData.setClientTime(getClientTime());
        statusData.currentInputRequest = currentInputRequest;
        statusData.inputQueue = inputQueue.size();
        statusFrame.update();
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

    private void pollInput(float delta){

        float mouseX = camera.position.x-(camera.viewportWidth/2)+Gdx.input.getX();
        float mouseY = camera.position.y+(camera.viewportHeight/2)-Gdx.input.getY();
        if(lastMouseX==-1&&lastMouseY==-1){
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        if(!buttons.contains(Enums.Buttons.W)
                && !buttons.contains(Enums.Buttons.A)
                && !buttons.contains(Enums.Buttons.S)
                && !buttons.contains(Enums.Buttons.D)
                && !autoWalk){
            player.animationState = 0;
        }
        if(buttons.size()>0||mouse1Pressed||mouse2Pressed||lastMouseX!=mouseX||lastMouseY!=mouseY||autoWalk){
            int inputRequestID = getNextInputRequestID();
            Network.UserInput input = new Network.UserInput();
            input.msec = (short) (delta*1000);
            input.buttons = buttons.clone();
            if(mouse1Pressed) input.buttons.add(Enums.Buttons.MOUSE1);
            if(mouse2Pressed) input.buttons.add(Enums.Buttons.MOUSE2);

            if(autoWalk) input.buttons.add(Enums.Buttons.W);

            input.inputID = inputRequestID;
            input.mouseX = mouseX;
            input.mouseY = mouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            inputQueue.add(input);
            pendingInputPacket.add(input);

            processClientInput(input);
        }
    }

    private void processClientInput(Network.UserInput input){

        //We need to move player here so we can spawn the potential projectiles at correct location
        if(player != null){
            SharedMethods.applyInput(player,input);
        }

        EnumSet<Enums.Buttons> buttons = input.buttons;

        if(buttons.contains(Enums.Buttons.NUM1)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                player.slot2Weapon = 0;
            }else{
                player.slot1Weapon = 0;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM2)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                player.slot2Weapon = 1;
            }else{
                player.slot1Weapon = 1;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM3)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                player.slot2Weapon = 2;
            }else{
                player.slot1Weapon = 2;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM4)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                player.slot2Weapon = 3;
            }else{
                player.slot1Weapon = 3;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM5)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                player.slot2Weapon = 4;
            }else{
                player.slot1Weapon = 4;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM6)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                player.slot2Weapon = 5;
            }else{
                player.slot1Weapon = 5;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM7)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                player.slot2Weapon = 6;
            }else{
                player.slot1Weapon = 6;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM8)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                player.slot2Weapon = 7;
            }else{
                player.slot1Weapon = 7;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM9)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                player.slot2Weapon = 8;
            }else{
                player.slot1Weapon = 8;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM0)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                player.slot2Weapon = 9;
            }else{
                player.slot1Weapon = 9;
            }
        }
        if(buttons.contains(Enums.Buttons.MOUSE1)){
            playerAttack(player.slot1Weapon);
            mouse1Pressed = false;
        }
        if(buttons.contains(Enums.Buttons.MOUSE2)){
            playerAttack(player.slot2Weapon);
            mouse2Pressed = false;
        }
    }

    private void playerAttack(int weaponSlot){
        player.aimingWeapon = true;
        if(weaponList.get(weaponSlot)==null){
            return;
        }

        if(player.cooldowns.get(weaponSlot)>0||player.ammo.get(weaponSlot)<=0||!player.weapons.get(weaponSlot)){
            return;
        }
        player.cooldowns.put(weaponSlot, weaponList.get(weaponSlot).cooldown);
        player.ammo.put(weaponSlot,player.ammo.get(weaponSlot)-1);
        //TODO Ignoring projectile team for now

        Network.EntityAttacking attack = new Network.EntityAttacking();
        attack.deg = player.getRotation();
        attack.id = player.id;
        attack.x = player.getCenterX();
        attack.y = player.getCenterY();
        attack.weapon = weaponSlot;

        addAttack(attack);
    }

    private void correctPlayerPositionError(){
        //Set player position to what we had earlier predicted for this inputID, effectively removing the client prediction
        ArrayList<Integer> removePredictions = new ArrayList<>();
        for(int inputID:predictedPositions.keySet()){
            if(inputID<authoritativeState.lastProcessedInputID){
                removePredictions.add(inputID);
            }else if(inputID == authoritativeState.lastProcessedInputID){
                player.setPosition(predictedPositions.get(authoritativeState.lastProcessedInputID));
            }else{
                break;
            }
        }
        predictedPositions.keySet().removeAll(removePredictions);

        //If predicted player position different than ghost player interpolate towards ghost player
        float errorX = ghostPlayer.getX()-player.getX();
        float errorY = ghostPlayer.getY()-player.getY();
        float ghostDelay = Tools.nanoToSecondsFloat(System.nanoTime()-lastAuthoritativeStateReceived);
        //I'm not sure why this works but it works
        //Alpha will increase towards 1 and the error decrease for every frame until we get a new state update
        //So we keep correcting a larger percent of the remaining error
        //There is no guarantee that we reach alpha 1 before a new state update so it's possible there is some reminder error when we get a new state
        //This error should be so small however that it doesn't matter
        //TODO Optimally we would like to interpolate all error before next state update arrives
        //FIXME This seems to work fine with 10 or less updaterate but stutters at higher rates
        float alpha = ghostDelay/Tools.nanoToSecondsFloat(lastUpdateDelay);
        if(alpha>1){
            alpha=1;
        }
        float correctionX = errorX*alpha;
        float correctionY = errorY*alpha;
        if(Math.abs(errorX)<1){
            correctionX = errorX;
        }
        if(Math.abs(errorY)<1){
            correctionY = errorY;
        }
        player.move(correctionX,correctionY);
    }

    private void runClientSidePrediction(){

        if(player==null){
            inputQueue.clear();
        }

        if(!ConVars.getBool("cl_clientside_prediction")){
            return;
        }
        Iterator<Network.UserInput> iter = inputQueue.iterator();
        while(iter.hasNext()){
            Network.UserInput input = iter.next();
            if(input.inputID <= authoritativeState.lastProcessedInputID){
                //System.out.println("Removing player inputID:"+input.inputID);
                iter.remove();
            }
        }

        Collections.sort(inputQueue);
        for(Network.UserInput input:inputQueue){
            //System.out.println("Predicting player inputID:" + input.inputID);
            SharedMethods.applyInput(player,input);
            SharedMethods.applyInput(ghostPlayer,input);
            predictedPositions.put(input.inputID,new Vector2(player.getX(),player.getY()));
        }
    }

    private void updateNetworkedState(HashMap<Integer,NetworkEntity> interpolatedStates){
        for(int id:entities.keySet()){
            if(interpolatedStates.containsKey(id)){
                if(id==player.id) {
                    if (!(entities.get(id) instanceof PlayerClientEntity)) {
                        showMessage("Took player control of entity " + id);
                        entities.put(id, player);
                        player.setNetworkedState(interpolatedStates.get(id));
                    }
                    ghostPlayer.setNetworkedState(interpolatedStates.get(id));
                }else {
                    entities.get(id).setNetworkedState(interpolatedStates.get(id));
                }
                NetworkEntity e= authoritativeState.entities.get(id);
                if(e!=null){
                    //Here we update the fields that are not interpolated
                    ClientEntity old = entities.get(id);
                    old.setHealth(e.health);
                    old.setTeam(e.team);
                }
            }else{
                showMessage("Couldn't find entity "+id+" in state update");
            }
        }
    }

    private void createStateSnapshot(double clientTime){

        //printStateHistory();
        //If interp delay is 0 we simply copy the latest authorative state and run clientside prediction on that
        if(ConVars.getDouble("cl_interp") <= 0){
            HashMap<Integer, NetworkEntity> authoritativeStateEntities = new HashMap<>();
            for(int id : authoritativeState.entities.keySet()){
                NetworkEntity e = authoritativeState.entities.get(id);
                NetworkEntity entityCopy = new NetworkEntity (e);
                authoritativeStateEntities.put(id,entityCopy);
            }
            //With no interpolation we dont need the state history
            stateHistory.clear();
            stateSnapshot = authoritativeStateEntities;
            return;
        }

        double renderTime = clientTime-ConVars.getDouble("cl_interp");

        //Find between which two states renderTime is
        ArrayList<Network.FullEntityUpdate> oldStates = new ArrayList<>();
        //The list is sorted starting with the oldest states, the first state we find that is newer than renderTime is thus our interpolation target
        for(Network.FullEntityUpdate state:stateHistory){
            if(state.serverTime>renderTime){
                interpTo = state;
                break;
            }else{
                //System.out.println("["+clientTime+"]Removing update> StateTime:"+state.serverTime+" InputID:"+state.lastProcessedInputID);
                //This will also remove the state we want to interpolate from but that state is still in the stateCopy and will be found and stored in the reversed lookup loop
                oldStates.add(state);
            }
        }
        //Look through states in reverse, the first state we find that is older than renderTime is our interpolation start point
        for (int i = stateHistory.size()-1; i >= 0; i--) {
            if(stateHistory.get(i).serverTime<renderTime){
                interpFrom = stateHistory.get(i);
                break;
            }
        }
        stateHistory.removeAll(oldStates);

        if(interpFrom != null && interpTo != null) {

            /*
            System.out.println("Render time:"+renderTime);
            System.out.println("Interp from:"+interpFrom.serverTime);
            System.out.println("Interp to:"+interpTo.serverTime);
            System.out.println("Interp interval:"+(interpTo.serverTime - interpFrom.serverTime));
            */

            double interpAlpha;
            if(interpTo.serverTime - interpFrom.serverTime != 0){
                interpAlpha = (renderTime - interpFrom.serverTime) / (interpTo.serverTime - interpFrom.serverTime);
            }else{
                interpAlpha = 1;
            }
            //System.out.println("Interp alpha:"+interpAlpha);

            HashMap<Integer, NetworkEntity> interpEntities = interpolateStates(interpFrom, interpTo, interpAlpha);
            stateSnapshot = interpEntities;
        }
    }

    private HashMap<Integer, NetworkEntity> interpolateStates(Network.FullEntityUpdate from, Network.FullEntityUpdate to, double alpha){
        HashMap<Integer, NetworkEntity> interpEntities = new HashMap<>();
        //FIXME if interpolation destination is missing entity we need to remove it from result
        for(int id: from.entities.keySet()){
            if(id != player.id) {
                NetworkEntity fromEntity = from.entities.get(id);
                NetworkEntity toEntity = to.entities.get(id);
                //System.out.println("Interpolating from ("+fromPos.x+","+fromPos.y+") to ("+toPos.x+","+toPos.y+") Result ("+interpPos.x+","+interpPos.y+")");
                //If toEntity null means entity missing from next state
                if (toEntity != null){
                    Vector2 interpPos = interpolatePosition(fromEntity, toEntity, alpha);
                    NetworkEntity interpEntity = new NetworkEntity(fromEntity);
                    interpEntity.x = interpPos.x;
                    interpEntity.y = interpPos.y;
                    interpEntity.rotation = interpolateRotation(fromEntity, toEntity, alpha);
                    interpEntities.put(id, interpEntity);
                }
            }else{
                //For player position we use latest server position and apply clientside prediction on it later
                //FIXME copy this value or else you'll be modifying the authoritative state
                NetworkEntity playerE = authoritativeState.entities.get(player.id);
                NetworkEntity playerCopy = new NetworkEntity(playerE);
                interpEntities.put(id, playerCopy);
            }
        }
        return interpEntities;
    }

    private Vector2 interpolatePosition(NetworkEntity from, NetworkEntity to, double alpha){
        Vector2 interpPos = new Vector2();
        interpPos.x = (float)(from.x+(to.x-from.x)*alpha);
        interpPos.y = (float)(from.y+(to.y-from.y)*alpha);
        return interpPos;
    }

    private int interpolateRotation(NetworkEntity from, NetworkEntity to, double alpha){
        return (int) (from.rotation+(to.rotation-from.rotation)*alpha);
    }

    private ArrayList<Network.UserInput> compressInputPacket(ArrayList<Network.UserInput> inputs){
        //System.out.println("Packing inputs:"+inputs.size());
        ArrayList<Network.UserInput> packedInputs = new ArrayList<>();
        Network.UserInput packedInput = null;
        for(Network.UserInput input:inputs){
            if(packedInput == null){
                packedInput = new Network.UserInput();
                packedInput.buttons = input.buttons;
                packedInput.inputID = input.inputID;
                packedInput.msec = input.msec;
                continue;
            }
            if(packedInput.buttons.containsAll(input.buttons)){
                packedInput.msec += input.msec;
                packedInput.inputID = input.inputID;
            }else{
                packedInputs.add(packedInput);
                packedInput = null;
            }
        }
        if(packedInput != null){
            packedInputs.add(packedInput);
        }
        //System.out.println("Packed inputs result:"+packedInputs.size());
        return packedInputs;
    }

    private void sendInputPackets(){
        //Send all gathered inputs at set interval
        if(System.nanoTime()- lastInputSent > Tools.secondsToNano(1d/ConVars.getInt("cl_input_update_rate"))){
            if(pendingInputPacket.size() > 0) {
                Network.UserInputs inputPacket = new Network.UserInputs();
                if(ConVars.getBool("cl_compress_input")){
                    inputPacket.inputs = compressInputPacket(pendingInputPacket);
                }else{
                    inputPacket.inputs = pendingInputPacket;
                }
                sendUDP(inputPacket);
                pendingInputPacket.clear();
            }
            lastInputSent = System.nanoTime();
        }
    }

    private void updateProjectiles(float delta){
        ArrayList<Projectile> destroyedProjectiles = new ArrayList<>();
        for(Projectile projectile:projectiles){
            projectile.update(delta);
            if(!projectile.noCollision) {
                for (int id : entities.keySet()) {
                    if (id == projectile.ownerID) {
                        continue;
                    }
                    if(projectile.entitiesHit.contains(id)){
                        continue;
                    }
                    ClientEntity target = entities.get(id);
                    if(Intersector.overlaps(projectile.getHitbox().getBoundingRectangle(),target.getGdxBounds())){
                        if (Intersector.overlapConvexPolygons(projectile.getHitbox(), target.getPolygonBounds())) {
                            if(!projectile.dontDestroyOnCollision){
                                projectile.destroyed = true;
                            }
                            if (id == player.id) {
                                playSoundInLocation(sounds.get("hurt.ogg"),target.getCenterX(),target.getCenterY());
                            } else {
                                playSoundInLocation(sounds.get("hit.ogg"),target.getCenterX(),target.getCenterY());
                            }
                            Vector2 center = new Vector2();
                            projectile.getHitbox().getBoundingRectangle().getCenter(center);
                            projectile.entitiesHit.add(target.getID());
                            particles.add(SharedMethods.createStationaryParticle(projectileList.get("bloodsplat"), center.x, center.y));
                            if(projectile.explosionKnockback){
                                particles.add(SharedMethods.createMovingParticle(projectileList.get("blood"),target.getCenterX(), target.getCenterY(), (int) (Tools.getAngle(center.x,center.y,target.getCenterX(),target.getCenterY())+MathUtils.random(-10,10)),MathUtils.random(60, 100)));
                            }else{
                                particles.add(SharedMethods.createMovingParticle(projectileList.get("blood"),center.x, center.y,projectile.rotation+MathUtils.random(-10,10),MathUtils.random(60,100)));
                            }
                        }
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
                //FIXME if projectile hits some entity the onDestroy projectile is never created probably want to change this at some point
                //server will create the onDestroy projectile in any case and send it to client if its networked
                if(projectile.onDestroy!=null && projectile.entitiesHit.isEmpty()){
                    ProjectileDefinition def = projectileList.get(projectile.onDestroy);
                    if(def.type == ProjectileDefinition.PARTICLE){
                        Vector2 center = new Vector2();
                        projectile.getHitbox().getBoundingRectangle().getCenter(center);
                        particles.add(SharedMethods.createStationaryParticle(def, center.x, center.y));
                    }
                }
            }
        }
        projectiles.removeAll(destroyedProjectiles);
    }

    private Rectangle getCenteredTextureSize(TextureRegion t, Rectangle bounds){
        float aspectRatio = (float)t.getRegionWidth()/t.getRegionHeight();
        if(aspectRatio>=1){
            float scaledHeight = bounds.width/aspectRatio;
            return new Rectangle(bounds.x,bounds.y+(bounds.height-scaledHeight)/2,bounds.width,scaledHeight);
        }else{
            float scaledWidth = aspectRatio*bounds.height;
            return new Rectangle(bounds.x+(bounds.width-scaledWidth)/2,bounds.y,scaledWidth,bounds.height);
        }
    }

    @Override
    public void render() {

        float delta = Gdx.graphics.getDeltaTime();
        doLogic(delta);

        renderStart = System.nanoTime();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glLineWidth(3);
        Gdx.gl.glScissor((int) (windowWidth/2-camera.position.x), (int) (windowHeight/2-camera.position.y), mapWidth, mapHeight);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);

        centerCameraOnPlayer();
        shapeRenderer.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        if(mapRenderer!=null){
            batch.setColor(1,1,1,1);
            mapRenderer.setView(camera);
            mapRenderer.render();
        }

        if(ConVars.getBool("cl_show_debug")) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(1, 0, 0, 1); //red
            for (NetworkEntity e : interpFrom.entities.values()) {
                shapeRenderer.rect(e.x, e.y, e.width / 2, e.height / 2, e.width, e.height, 1, 1, e.rotation);
            }
            shapeRenderer.setColor(0, 1, 0, 1); //green
            for (NetworkEntity e : interpTo.entities.values()) {
                shapeRenderer.rect(e.x, e.y, e.width / 2, e.height / 2, e.width, e.height, 1, 1, e.rotation);
            }
            shapeRenderer.setColor(0, 0, 1, 1); //blue
            for (NetworkEntity e : authoritativeState.entities.values()) {
                shapeRenderer.rect(e.x, e.y, e.width / 2, e.height / 2, e.width, e.height, 1, 1, e.rotation);
            }
            shapeRenderer.setColor(1, 1, 1, 1); //white
            for (NetworkEntity e : authoritativeState.entities.values()) {
                shapeRenderer.rect(e.x, e.y, e.width, e.height);
            }
            shapeRenderer.end();
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0, 1, 1, 1);
            Rectangle bounds = ghostPlayer.getGdxBounds();
            shapeRenderer.rect(bounds.x,bounds.y,bounds.width,bounds.height);
            shapeRenderer.end();
        }

        batch.begin();
        for(Powerup p : powerups.values()){
            if(p.type==Powerup.HEALTH){
                batch.setColor(1,1,1,1);
                batch.draw(getTexture("health"),p.bounds.x,p.bounds.y,p.bounds.width,p.bounds.height);
            }else if(p.type==Powerup.AMMO){
                //TODO clean up these nullchecks
                if(weaponList!=null&&weaponList.get(p.typeModifier)!=null&&weaponList.get(p.typeModifier).ammoImage!=null) {
                    TextureRegion texture = getTexture(weaponList.get(p.typeModifier).ammoImage);
                    Rectangle textureBounds = getCenteredTextureSize(texture,p.bounds);
                    batch.setColor(1, 1, 1, 1);
                    batch.draw(texture,textureBounds.x,textureBounds.y,textureBounds.width,textureBounds.height);
                    continue;
                }
                batch.setColor(1, 0, 0, 1);
                batch.draw(getTexture("blank"), p.bounds.x,p.bounds.y,p.bounds.width,p.bounds.height);
            }else if(p.type==Powerup.WEAPON){
                //TODO clean up these nullchecks
                if(weaponList!=null&&weaponList.get(p.typeModifier)!=null&&weaponList.get(p.typeModifier).image!=null){
                    TextureRegion texture = getTexture(weaponList.get(p.typeModifier).image);
                    Rectangle textureBounds = getCenteredTextureSize(texture,p.bounds);
                    batch.setColor(1,1,1,1);
                    batch.draw(texture,textureBounds.x,textureBounds.y,textureBounds.width,textureBounds.height);
                    continue;
                }
                batch.setColor(0, 0, 1, 1);
                batch.draw(getTexture("blank"), p.bounds.x,p.bounds.y,p.bounds.width,p.bounds.height);
            }
        }
        batch.end();

        //Render entities
        batch.begin();
        for(ClientEntity e: entities.values()){ //FIXME will there ever be an entity that is not in stateSnapshot, yes when adding entities on server so we get nullpointer here
            float healthbarWidth = e.getWidth()+20;
            float healthbarHeight = healthbarWidth/7;
            float healthbarXOffset =- healthbarHeight;
            float healthbarYOffset = e.getWidth()+10;
            float x = e.getX();
            float y = e.getY();
            batch.setColor(1,1,1,1);
            StringBuilder textureName = new StringBuilder(e.getImage());
            if(e.getID()==player.id) {
                //Cast player position to int because we are centering the camera using casted values too
                x = (int) e.getX();
                y = (int) e.getY();
                textureName.append("_");
                textureName.append(weaponList.get(player.slot1Weapon).sprite);
            }
            TextureRegion texture;
            if(e.fireAnimationTimer>0){
                texture = getTexture(textureName+"_attack");
            }else if(e.aimingWeapon){
                texture = getTexture(textureName+"_aim");
            }else{
                texture = getTexture(textureName.toString(),e.getID());
            }
            Rectangle textureBounds = getCenteredTextureSize(texture,e.getGdxBounds());
            batch.draw(texture,(int)textureBounds.x,(int)textureBounds.y,e.getWidth()/2,e.getHeight()/2,textureBounds.width,textureBounds.height,1,1,e.getRotation()+180,true);
            e.aimingWeapon = false;
            batch.setColor(0,1,0,1);
            batch.draw(getTexture("blank", e.getID()), x + healthbarXOffset, y + healthbarYOffset, healthbarWidth, healthbarHeight);
            batch.setColor(1,0,0,1);
            float healthWidth = healthbarWidth*e.getHealthPercent();
            batch.draw(getTexture("blank",e.getID()), x+healthbarXOffset,y+healthbarYOffset,healthWidth,healthbarHeight);
        }
        batch.end();

        SharedMethods.renderParticles(delta, batch, projectiles);
        SharedMethods.renderParticles(delta, batch, particles);
        if(ConVars.getBool("cl_show_debug")) {
            shapeRenderer.setColor(1,1,1,1);
            SharedMethods.renderAttackBoundingBox(shapeRenderer,projectiles);
            shapeRenderer.setColor(1,1,0,1);
            SharedMethods.renderAttackPolygon(shapeRenderer,projectiles);
        }

        drawTracer(shapeRenderer);

        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        //Draw HUD
        batch.begin();
        batch.setProjectionMatrix(hudCamera.combined);
        int offset = 0;
        for(int id: playerList){
            font.draw(batch, id +" | Kills: "+ score.getPlayerKills(id)+ " Civilians killed: "+score.getNpcKills(id)
                    + " Deaths: "+score.getDeaths(id) + " Team: "+entities.get(id).getTeam(), 5, windowHeight-5-offset);
            offset += 20;
        }
        font.draw(batch, "Mouse 1: "+ getWeaponLine(player.slot1Weapon), 5, 40);
        font.draw(batch, "Mouse 2: " + getWeaponLine(player.slot2Weapon), 5, 20);
        glyphLayout.setText(font, "Wave " + currentWave);
        font.draw(batch, "Wave "+currentWave,windowWidth/2-glyphLayout.width/2 ,windowHeight-5);
        glyphLayout.setText(font, "Lives: "+lives);
        font.draw(batch, "Lives: "+lives,windowWidth-glyphLayout.width ,windowHeight-5);
        batch.end();
    }

    private String getWeaponLine(int weaponSlot){
        if(weaponList==null||weaponList.get(weaponSlot)==null){
            return "N/A";
        }
        StringBuilder b = new StringBuilder(weaponSlot+".");
        if(player.weapons.get(weaponSlot)){
            b.append(weaponList.get(weaponSlot).name);
        }else{
            b.append("Empty");
        }
        b.append(" [").append(player.ammo.get(weaponSlot)).append("]");
        return b.toString();
    }

    private void createTracer(){
        float x1 = player.getX();
        float y1 = player.getY();
        float x2 = camera.position.x-(camera.viewportWidth/2)+Gdx.input.getX();
        float y2 = camera.position.y+(camera.viewportHeight/2)-Gdx.input.getY();

        SharedMethods.debugRaytrace(x1,y1,x2,y2);

        tracer = new Line2D.Float(x1,y1,x2,y2);
    }

    private void drawTracer(ShapeRenderer shapeRenderer){
        if(tracer!=null){
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(1,0,0,1);
            shapeRenderer.line(tracer.x1,tracer.y1,tracer.x2,tracer.y2);
            shapeRenderer.end();

            Vector2 intersection = SharedMethods.findLineIntersectionPointWithTile(tracer.x1,tracer.y1,tracer.x2,tracer.y2);
            SharedMethods.debugDrawRaytrace(shapeRenderer,tracer.x1,tracer.y1,tracer.x2,tracer.y2);
            if(intersection!=null){
                shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                shapeRenderer.setColor(1, 1, 0, 1);
                shapeRenderer.circle(intersection.x,intersection.y,5);
                shapeRenderer.end();
            }

            for(Entity e : entities.values()){
                Vector2 p = SharedMethods.getLineIntersectionWithRectangle(tracer,e.getGdxBounds());
                if(p!=null){
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                    shapeRenderer.setColor(1,1,1,1);
                    shapeRenderer.rect(e.getX(), e.getY(), e.getWidth(), e.getHeight());
                    shapeRenderer.end();

                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                    shapeRenderer.setColor(1, 1, 0, 1);
                    shapeRenderer.circle(p.x,p.y,5);
                    shapeRenderer.end();
                }
            }

            Vector2 tile = SharedMethods.raytrace(tracer.x1, tracer.y1, tracer.x2, tracer.y2);
            if(tile!=null){
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(1,0,0,1);
                shapeRenderer.rect(tile.x * GlobalVars.tileWidth, tile.y *GlobalVars.tileHeight, GlobalVars.tileWidth, GlobalVars.tileHeight);
                shapeRenderer.end();
            }
        }
    }

    private Network.FullEntityUpdate applyEntityPositionUpdate(Network.EntityPositionUpdate update){
        HashMap<Integer, Network.Position> changedEntityPositions = update.changedEntityPositions;
        HashMap<Integer,NetworkEntity> newEntityList = new HashMap<>();
        HashMap<Integer,? extends NetworkEntity> oldEntityList = authoritativeState.entities;
        for(int id: oldEntityList.keySet()){
            NetworkEntity e = oldEntityList.get(id);
            if(changedEntityPositions.containsKey(id)){
                Network.Position pos = changedEntityPositions.get(id);
                NetworkEntity movedEntity = new NetworkEntity(e);
                movedEntity.x = pos.x;
                movedEntity.y = pos.y;
                newEntityList.put(id,movedEntity);
            }else{
                newEntityList.put(id,e);
            }
        }
        Network.FullEntityUpdate newFullEntityUpdate = new Network.FullEntityUpdate();
        newFullEntityUpdate.lastProcessedInputID = update.lastProcessedInputID;
        newFullEntityUpdate.serverTime = update.serverTime;
        newFullEntityUpdate.entities = newEntityList;
        return newFullEntityUpdate;
    }

    private Network.FullEntityUpdate applyEntityComponentUpdate(Network.EntityComponentsUpdate update){
        HashMap<Integer, ArrayList<Component>> changedEntityComponents = update.changedEntityComponents;
        HashMap<Integer,NetworkEntity> newEntityList = new HashMap<>();
        HashMap<Integer,NetworkEntity> oldEntityList = authoritativeState.entities;
        for(int id: oldEntityList.keySet()) {
            NetworkEntity e = oldEntityList.get(id);
            if (changedEntityComponents.containsKey(id)) {
                ArrayList<Component> components = changedEntityComponents.get(id);
                NetworkEntity changedEntity = new NetworkEntity(e);
                for(Component component:components){
                    if(component instanceof Position){
                        Position pos = (Position) component;
                        changedEntity.x = pos.x;
                        changedEntity.y = pos.y;
                    }
                    if(component instanceof Health) {
                        Health health = (Health) component;
                        changedEntity.health = health.health;
                    }
                    if(component instanceof Rotation){
                        Rotation rotation = (Rotation) component;
                        changedEntity.rotation=rotation.degrees;
                    }
                    if(component instanceof Team){
                        Team team = (Team) component;
                        changedEntity.team = team.team;
                    }
                }
                newEntityList.put(id,changedEntity);
            } else {
                newEntityList.put(id, e);
            }
        }
        Network.FullEntityUpdate newFullEntityUpdate = new Network.FullEntityUpdate();
        newFullEntityUpdate.lastProcessedInputID = update.lastProcessedInputID;
        newFullEntityUpdate.serverTime = update.serverTime;
        newFullEntityUpdate.entities = newEntityList;
        return newFullEntityUpdate;
    }

    @Override
    public boolean keyDown(int keycode) {
        if(keycode == Input.Keys.F1){
            consoleFrame.showHelp();
        }
        if(keycode == Input.Keys.SHIFT_LEFT) buttons.add(Enums.Buttons.SHIFT);
        if(keycode == Input.Keys.NUM_1) buttons.add(Enums.Buttons.NUM1);
        if(keycode == Input.Keys.NUM_2) buttons.add(Enums.Buttons.NUM2);
        if(keycode == Input.Keys.NUM_3) buttons.add(Enums.Buttons.NUM3);
        if(keycode == Input.Keys.NUM_4) buttons.add(Enums.Buttons.NUM4);
        if(keycode == Input.Keys.NUM_5) buttons.add(Enums.Buttons.NUM5);
        if(keycode == Input.Keys.NUM_6) buttons.add(Enums.Buttons.NUM6);
        if(keycode == Input.Keys.NUM_7) buttons.add(Enums.Buttons.NUM7);
        if(keycode == Input.Keys.NUM_8) buttons.add(Enums.Buttons.NUM8);
        if(keycode == Input.Keys.NUM_9) buttons.add(Enums.Buttons.NUM9);
        if(keycode == Input.Keys.NUM_0) buttons.add(Enums.Buttons.NUM0);
        if(keycode == Input.Keys.LEFT) buttons.add(Enums.Buttons.LEFT);
        if(keycode == Input.Keys.RIGHT) buttons.add(Enums.Buttons.RIGHT);
        if(keycode == Input.Keys.UP)buttons.add(Enums.Buttons.UP);
        if(keycode == Input.Keys.DOWN)buttons.add(Enums.Buttons.DOWN);
        if(keycode == Input.Keys.SPACE)buttons.add(Enums.Buttons.SPACE);
        if(keycode == Input.Keys.W)buttons.add(Enums.Buttons.W);autoWalk=false;
        if(keycode == Input.Keys.A)buttons.add(Enums.Buttons.A);autoWalk=false;
        if(keycode == Input.Keys.S)buttons.add(Enums.Buttons.S);autoWalk=false;
        if(keycode == Input.Keys.D)buttons.add(Enums.Buttons.D);autoWalk=false;
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if(keycode == Input.Keys.SHIFT_LEFT) buttons.remove(Enums.Buttons.SHIFT);
        if(keycode == Input.Keys.NUM_1) buttons.remove(Enums.Buttons.NUM1);
        if(keycode == Input.Keys.NUM_2) buttons.remove(Enums.Buttons.NUM2);
        if(keycode == Input.Keys.NUM_3) buttons.remove(Enums.Buttons.NUM3);
        if(keycode == Input.Keys.NUM_4) buttons.remove(Enums.Buttons.NUM4);
        if(keycode == Input.Keys.NUM_5) buttons.remove(Enums.Buttons.NUM5);
        if(keycode == Input.Keys.NUM_6) buttons.remove(Enums.Buttons.NUM6);
        if(keycode == Input.Keys.NUM_7) buttons.remove(Enums.Buttons.NUM7);
        if(keycode == Input.Keys.NUM_8) buttons.remove(Enums.Buttons.NUM8);
        if(keycode == Input.Keys.NUM_9) buttons.remove(Enums.Buttons.NUM9);
        if(keycode == Input.Keys.NUM_0) buttons.remove(Enums.Buttons.NUM0);
        if(keycode == Input.Keys.LEFT) buttons.remove(Enums.Buttons.LEFT);
        if(keycode == Input.Keys.RIGHT) buttons.remove(Enums.Buttons.RIGHT);
        if(keycode == Input.Keys.UP)buttons.remove(Enums.Buttons.UP);
        if(keycode == Input.Keys.DOWN)buttons.remove(Enums.Buttons.DOWN);
        if(keycode == Input.Keys.SPACE)buttons.remove(Enums.Buttons.SPACE);
        if(keycode == Input.Keys.W)buttons.remove(Enums.Buttons.W);
        if(keycode == Input.Keys.A)buttons.remove(Enums.Buttons.A);
        if(keycode == Input.Keys.S)buttons.remove(Enums.Buttons.S);
        if(keycode == Input.Keys.D)buttons.remove(Enums.Buttons.D);
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        if(character == 't'){
            createTracer();
        }
        if(character == 'p'){
            sendTCP(new Network.TestPacket());
        }
        if(character == 'o'){
            sendUDP(new Network.TestPacket());
        }
        if(character == 'e'){
            printStateHistory();
        }
        if(character == 'r'){
            printPlayerList();
        }
        if(character == 'l'){
            //Throw exception
            @SuppressWarnings({"NumericOverflow", "UnusedDeclaration"})
            int kappa = 50/0;
        }
        if(character == 'i'){
            statusData.writeLog(false);
        }
        if(character == 'y'){
            boolean showPerformanceWarnings = ConVars.getBool("cl_show_performance_warnings");
            if(showPerformanceWarnings){
                ConVars.set("cl_show_performance_warnings",false);
            }else{
                ConVars.set("cl_show_performance_warnings",true);
            }
            showMessage("ShowPerformanceWarnings:" + ConVars.getBool("cl_show_performance_warnings"));
        }
        if(character == 'q'){
            KryoSerialization s = (KryoSerialization) client.getSerialization();
            NetworkEntity e = new NetworkEntity(-1,50,50,-1);
            s.write(client, buffer ,e);
            showMessage("Entity size is " + buffer.position() + " bytes");
            buffer.clear();

            int totalEntitySize = 0;

            s.write(client, buffer ,e.health);
            totalEntitySize += buffer.position();
            showMessage("Health size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.maxHealth);
            totalEntitySize += buffer.position();
            showMessage("Maxhealth size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.x);
            totalEntitySize += buffer.position();
            showMessage("X size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.y);
            totalEntitySize += buffer.position();
            showMessage("Y size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.height);
            totalEntitySize += buffer.position();
            showMessage("Height size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.width);
            totalEntitySize += buffer.position();
            showMessage("Width size is " + buffer.position() + " bytes");
            buffer.clear();

            showMessage("Total entity size is " + totalEntitySize + " bytes");
        }
        if(character == 'z'){
            showConsoleWindow();
        }
        if(character == 'x'){
            autoWalk = !autoWalk;
        }
        if(character == 'c'){
            showStatusWindow();
        }
        if(character == 'b'){
            ConVars.toggleVar("cl_clientside_prediction");
            showMessage("UseClientSidePrediction:" + ConVars.getBool("cl_clientside_prediction"));
        }
        if(character == 'v'){
            ConVars.toggleVar("cl_compress_input");
            showMessage("CompressInput:" + ConVars.getBool("cl_compress_input"));
        }
        if(character == 'p'){
            ConVars.toggleVar("cl_show_debug");
            showMessage("Show debug:" + ConVars.getBool("cl_show_debug"));
        }

        if(character == '+'){
            ConVars.addToVar("cl_interp",0.05);
            showMessage("Interpolation delay now:" + ConVars.getDouble("cl_interp"));
        }
        if(character == '-'){
            ConVars.addToVar("cl_interp",-0.05);
            if(ConVars.getDouble("cl_interp")<0){
                ConVars.set("cl_interp",0);
            }
            showMessage("Interpolation delay now:" + ConVars.getDouble("cl_interp"));
        }

        return false;
    }

    private void joinServer() throws IOException {
        Network.register(client);
        Listener listener = new Listener() {

            @Override
            public void connected(Connection connection){
                connection.setTimeout(GlobalVars.TIMEOUT);
            }

            public void received(Connection connection, Object object) {
                logReceivedPackets(connection, object);
                if (object instanceof Network.FakePing) {
                    Network.FakePing ping = (Network.FakePing)object;
                    if (ping.isReply) {
                        if (ping.id == statusData.lastPingID - 1) {
                            statusData.setFakeReturnTripTime((int)(System.currentTimeMillis() - statusData.lastPingSendTime));
                        }
                    } else {
                        ping.isReply = true;
                        sendTCP(ping);
                    }
                    return;
                }

                pendingPackets.add(object);
            }
        };
        double minPacketDelay = ConVars.getDouble("sv_min_packet_delay");
        double maxPacketDelay = ConVars.getDouble("sv_max_packet_delay");
        if(maxPacketDelay > 0){
            int msMinDelay = (int) (minPacketDelay*1000);
            int msMaxDelay = (int) (maxPacketDelay*1000);
            Listener.LagListener lagListener = new Listener.LagListener(msMinDelay,msMaxDelay,listener);
            client.addListener(lagListener);
        }else{
            client.addListener(listener);
        }

        client.start();
        client.connect(5000, serverIP, Network.portTCP, Network.portUDP);
        client.sendTCP(new Network.SpawnRequest());
    }

    private void centerCameraOnPlayer(){
        if(player != null) {
            //Cast values to int to avoid tile tearing
            camera.position.set((int) (player.getX() + player.getWidth() / 2f), (int) (player.getY() + player.getHeight() / 2), 0);
        }
        camera.update();
    }

    private void loadSounds(){

        File soundFolder = new File("resources"+File.separator+"sounds");
        showMessage("Loading sounds from: " + soundFolder);
        for (File file : soundFolder.listFiles()) {
            if (!file.isDirectory()) {
                Sound sound = Gdx.audio.newSound(new FileHandle(file));
                sounds.put(file.getName(), sound);
                showMessage("Loaded: " + file.getName());
            }
        }
    }

    private void loadTextures(){
        showMessage("Loading texture atlas");
        atlas =  new TextureAtlas("resources"+File.separator+"images"+File.separator+"sprites.atlas");
        GlobalVars.atlas = atlas;
        showMessage("Loading textures");
        Array<TextureAtlas.AtlasRegion> regions = atlas.getRegions();

        for(TextureAtlas.AtlasRegion region: regions){
            if(textures.containsKey(region.name)||animations.containsKey(region.name)){
                continue;
            }
            Array<TextureAtlas.AtlasRegion> regionArray = atlas.findRegions(region.name);
            if(regionArray.size>1){
                Animation animation = new Animation(animationFrameTime,regionArray, Animation.PlayMode.LOOP);
                animations.put(region.name,animation);
                showMessage("Loaded animation:"+region.name);
            }else{
                textures.put(region.name,regionArray.first());
                showMessage("Loaded texture:"+region.name);
            }
        }
    }

    private TextureRegion getTexture(String name){
        return getTexture(name, -1);
    }

    private TextureRegion getTexture(String name, int id){
        if(textures.containsKey(name)){
            return textures.get(name);
        }else if(animations.containsKey(name)){
            ClientEntity e = entities.get(id);
            if(e!=null){
                return animations.get(name).getKeyFrame(e.animationState);
            }else{
                return animations.get(name).getKeyFrame(getClientTime());
            }
        }else{
            return textures.get("blank");
        }
    }

    public void changeTeam(int team){
        Network.TeamChangeRequest teamChangeRequest = new Network.TeamChangeRequest();
        teamChangeRequest.team = team;
        sendTCP(teamChangeRequest);
    }

    public void requestRespawn(){
        Network.SpawnRequest spawnRequest = new Network.SpawnRequest();
        sendTCP(spawnRequest);
    }

    private float getClientTime(){
        //TODO adding half the latency to last server update this might cause some errors
        //TODO using the fake ping here because i wont be able to test this properly without adding artificial lag
        return lastServerTime + ((statusData.getFakePing()/1000f)/2f) + ((System.nanoTime() - lastAuthoritativeStateReceived) / 1000000000f);
    }

    private int getNextInputRequestID(){
        return currentInputRequest++;
    }

    private void sendUDP(Object o){
        int bytesSent = client.sendUDP(o);
        statusData.addBytesSent(bytesSent);
    }

    private void sendTCP(Object o){
        int bytesSent = client.sendTCP(o);
        statusData.addBytesSent(bytesSent);
    }

    public void selectWeapon(int weapon){
        player.slot1Weapon = weapon;
        Network.ChangeWeapon changeWeapon = new Network.ChangeWeapon();
        changeWeapon.weapon = weapon;
        sendTCP(changeWeapon);
    }


    private void printStateHistory(){
        showMessage("#State History:");
        int index = 0;
        Network.FullEntityUpdate[] stateHistoryCopy = stateHistory.toArray(new Network.FullEntityUpdate[stateHistory.size()]);
        for(Network.FullEntityUpdate state :stateHistoryCopy){
            showMessage(index + "> Time:" + state.serverTime + " InputID:" + state.lastProcessedInputID + " EntityCount:" + state.entities.size());
            index++;
        }
    }

    public void showConsoleWindow(){
        //TODO
        //We need to delay showing the window or else
        //the window steals the keyUP event on mac resulting
        //in InputProcessor getting KeyTyped events indefinately
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                consoleFrame.setVisible(true);
            }
        }).start();
    }

    public void showStatusWindow(){
        //TODO
        //We need to delay showing the window or else
        //the window steals the keyUP event on mac resulting
        //in InputProcessor getting KeyTyped events indefinately
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                statusFrame.setVisible(true);
            }
        }).start();
    }

    private void showMessage(String message){
        String line = "["+Tools.secondsToMilliTimestamp(getClientTime())+ "] " + message;
        System.out.println(line);
        consoleFrame.addLine(line);
    }

    private void logReceivedPackets(Connection connection, Object packet){
        KryoSerialization s = (KryoSerialization) client.getSerialization();
        s.write(connection, buffer ,packet);
        statusData.addBytesReceived(buffer.position());
        buffer.clear();
    }

    public void updateFakePing() {
        Network.FakePing ping = new Network.FakePing();
        ping.id = statusData.lastPingID++;
        statusData.lastPingSendTime = System.currentTimeMillis();
        sendTCP(ping);
    }

    private void addServerState(Network.FullEntityUpdate state){
        stateHistory.add(state);
        authoritativeState = state;
        lastUpdateDelay = System.nanoTime()-lastAuthoritativeStateReceived;
        lastAuthoritativeStateReceived = System.nanoTime();
        lastServerTime = authoritativeState.serverTime;

        if(player!=null){
            float errorX = ghostPlayer.getX()-player.getX();
            float errorY = ghostPlayer.getY()-player.getY();
            if(Math.abs(errorX)>0||Math.abs(errorY)>0){
                showMessage("["+state.serverTime+","+state.lastProcessedInputID+"] New state update, player position error remaining X:"+errorX+" Y:"+errorY);
            }
        }
    }

    private void loadMap(String mapName){
        map = new TmxMapLoader().load(GlobalVars.mapFolder+File.separator+mapName+File.separator+mapName+".tmx");
        float mapScale = SharedMethods.getMapScale(map);
        MapProperties p = map.getProperties();
        GlobalVars.mapWidthTiles = p.get("width", Integer.class);
        GlobalVars.mapHeightTiles = p.get("height", Integer.class);
        GlobalVars.tileWidth = (int) (p.get("tilewidth", Integer.class)* mapScale);
        GlobalVars.tileHeight = (int) (p.get("tileheight",Integer.class)* mapScale);

        mapHeight = GlobalVars.mapHeightTiles * GlobalVars.tileHeight;
        mapWidth = GlobalVars.mapWidthTiles * GlobalVars.tileWidth;
        GlobalVars.mapWidth = mapWidth;
        GlobalVars.mapHeight = mapHeight;

        mapRenderer = new OrthogonalTiledMapRenderer(map,mapScale,batch);
        GlobalVars.collisionMap = SharedMethods.createCollisionMap(map,GlobalVars.mapWidthTiles,GlobalVars.mapHeightTiles);
    }

    private void addEntity(NetworkEntity entity) {
        showMessage("Adding entity: "+entity);
        //Entities added instantly but their position starts updating only once the interpolation catches up
        authoritativeState.entities.put(entity.id,entity);
        entities.put(entity.id,new ClientEntity(entity));
    }

    private void removeEntity(int id){

        //TODO changed from only removing from latest state, might cause errors
        ClientEntity e = entities.get(id);
        showMessage("Removing entity:"+e);
        for (int rotation = 0; rotation < 360; rotation += MathUtils.random(35,65)) {
            particles.add(SharedMethods.createMovingParticle(projectileList.get("blood"), e.getCenterX(), e.getCenterY(), rotation, MathUtils.random(80,120)));
        }

        //We are not removing it from older states, should not matter, they get removed once interpolation catches up to them
        authoritativeState.entities.remove(id);
        entities.remove(id);

        if(playerList.contains(id)){
            showMessage("PlayerID "+id+" removed.");
            playerList.remove(Integer.valueOf(id));
            score.removePlayer(id);
            if(id==player.id){
                player.id = -1;
                player.slot1Weapon = 0;
                player.slot2Weapon = 1;
            }
        }
    }

    private void addAttack(Network.EntityAttacking attack){
        if(weaponList==null){
            return;
        }
        //TODO Ignoring projectile team for now
        Weapon weapon = weaponList.get(attack.weapon);
        if(weapon!=null){
            entities.get(attack.id).fireAnimationTimer = weapon.cooldown/2d;
            if(weapon.projectile.type == ProjectileDefinition.HITSCAN){
                for(Line2D.Float hitscan :SharedMethods.createHitscan(weapon,attack.x,attack.y,attack.deg)){
                    Vector2 intersection = SharedMethods.findLineIntersectionPointWithTile(hitscan.x1,hitscan.y1,hitscan.x2,hitscan.y2);
                    if(intersection!=null){
                        hitscan.x2 = intersection.x;
                        hitscan.y2 = intersection.y;
                    }
                    ArrayList<Entity> targetsHit = new ArrayList<>();
                    for(int targetId:entities.keySet()) {
                        if(targetId == attack.id){
                            continue;
                        }
                        Entity target = entities.get(targetId);
                        if(target.getJavaBounds().intersectsLine(hitscan)) {
                            targetsHit.add(target);
                        }
                    }
                    if(!targetsHit.isEmpty()){
                        Vector2 closestTarget = new Vector2(0,Float.POSITIVE_INFINITY);
                        for(Entity t:targetsHit){
                            float squaredDistance = Tools.getSquaredDistance(attack.x,attack.y,t.getCenterX(),t.getCenterY());
                            if(closestTarget.y>squaredDistance){
                                closestTarget.set(t.getID(), squaredDistance);
                            }
                        }
                        Entity t = entities.get((int)closestTarget.x);
                        Vector2 i = SharedMethods.getLineIntersectionWithRectangle(hitscan,t.getGdxBounds());
                        //FIXME i should never be null
                        if(i!=null){
                            hitscan.x2 = i.x;
                            hitscan.y2 = i.y;
                        }
                        particles.add(SharedMethods.createMovingParticle(projectileList.get("blood"),hitscan.x2,hitscan.y2,(int)Tools.getAngle(hitscan)+MathUtils.random(-10,10),MathUtils.random(60,100)));
                    }
                    if(weapon.projectile.onDestroy!=null && targetsHit.isEmpty()){
                        ProjectileDefinition def = projectileList.get(weapon.projectile.onDestroy);
                        if(def.type == ProjectileDefinition.PARTICLE){
                            particles.add(SharedMethods.createStationaryParticle(def,hitscan.x2,hitscan.y2));
                        }
                    }
                    if(weapon.projectile.tracer!=null){
                        particles.add(SharedMethods.createTracer(projectileList.get(weapon.projectile.tracer), hitscan));                    }
                }
            }else{
                projectiles.addAll(SharedMethods.createProjectiles(weapon, attack.x, attack.y, attack.deg, attack.id, -1));
            }

            if(weapon.sound!=null){
                playSoundInLocation(sounds.get(weapon.sound),attack.x,attack.y);
            }
        }
    }

    private void playSoundInLocation(Sound sound, float x, float y){
        Rectangle r = new Rectangle(camera.position.x-camera.viewportWidth/2f,camera.position.y-camera.viewportHeight/2f,camera.viewportWidth,camera.viewportHeight);
        if(r.contains(x,y)){
            sound.play(soundVolume);
        }
    }

    private void printPlayerList(){
        showMessage("#Playerlist#");
        for(int id:playerList){
            showMessage("ID:"+id);
        }
    }

    @Override
    public void resize(int width, int height) {
        windowWidth = width;
        windowHeight = height;
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        hudCamera.viewportWidth = width;
        hudCamera.viewportHeight = height;
        hudCamera.position.set(windowWidth/2,windowHeight / 2,0);
        hudCamera.update();
        centerCameraOnPlayer();
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void dispose() {
        statusData.writeLog(false);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if(button == 0){
            buttons.add(Enums.Buttons.MOUSE1);
            mouse1Pressed = true;
        }
        if(button == 1){
            buttons.add(Enums.Buttons.MOUSE2);
            mouse2Pressed = true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if(button == 0){
            buttons.remove(Enums.Buttons.MOUSE1);
        }
        if(button == 1){
            buttons.remove(Enums.Buttons.MOUSE2);
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(int amount) {
        return false;
    }

    @Override
    public void scoreChanged() {

    }

    @Override
    public void conVarChanged(String varName, String varValue) {
        if(varName.equals("cl_volume_sound")){
            soundVolume = ConVars.getFloat(varName);
        }else if(varName.equals("cl_volume_music")){
            musicVolume = ConVars.getFloat(varName);
            if(backgroundMusic!=null){
                backgroundMusic.setVolume(musicVolume);
            }
        }
    }
}
