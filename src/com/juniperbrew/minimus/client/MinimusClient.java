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
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.Intersector;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.kryonet.Listener;
import com.juniperbrew.minimus.*;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.components.Heading;
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
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class MinimusClient implements ApplicationListener, InputProcessor,Score.ScoreChangeListener, ConVars.ConVarChangeListener {

    Client client;
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

    //This is what we render
    private HashMap<Integer,Entity> stateSnapshot = new HashMap<>();

    //StateSnapshot is an interpolation between these two states
    private Network.FullEntityUpdate interpFrom;
    private Network.FullEntityUpdate interpTo;

    private ConcurrentLinkedQueue pendingPackets = new ConcurrentLinkedQueue<>();

    private HashMap<Integer,Powerup> powerups = new HashMap<>();
    ArrayList<Integer> playerList = new ArrayList<>();

    int currentWave;
    int playerID = -1;
    int slot1Weapon = 0;
    int slot2Weapon = 1;
    EnumSet<Enums.Buttons> buttons = EnumSet.noneOf(Enums.Buttons.class);
    private ConcurrentLinkedQueue<AttackVisual> attackVisuals = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Projectile> projectiles = new ConcurrentLinkedQueue<>();
    double attackCooldown;

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
    SharedMethods sharedMethods;

    Texture spriteSheet;

    TextureRegion down;
    SpriteBatch batch;

    Score score;

    HashMap<String,Sound> sounds = new HashMap<>();

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

    float lastMouseX = -1;
    float lastMouseY = -1;

    public MinimusClient(String ip) throws IOException {
        serverIP = ip;
        ConVars.addListener(this);
        soundVolume = ConVars.getFloat("cl_volume_sound");
        musicVolume = ConVars.getFloat("cl_volume_music");
        consoleFrame = new ConsoleFrame(this);
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
        hudCamera.position.set(windowWidth/2,windowHeight/2,0);
        hudCamera.update();

        Gdx.input.setInputProcessor(this);
        spriteSheet = new Texture(Gdx.files.internal("resources"+File.separator+"spritesheetAlpha.png"));
        showMessage("Spritesheet width:" + spriteSheet.getWidth());
        showMessage("Spritesheet height:" + spriteSheet.getHeight());
        down = new TextureRegion(spriteSheet,171,129,16,22);

        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        font.setColor(Color.RED);

        loadSounds();

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
            //Entities added to latest state despite their add time
            authoritativeState.entities.put(addEntity.entity.id, addEntity.entity);
        }else if(object instanceof Network.RemoveEntity){
            Network.RemoveEntity removeEntity = (Network.RemoveEntity) object;
            removeEntity(removeEntity.networkID);
        }else if(object instanceof Network.AssignEntity){
            showMessage("Assigning entity");
            Network.AssignEntity assign = (Network.AssignEntity) object;
            setPlayerID(assign.networkID);
            ConVars.set("sv_player_velocity", assign.velocity);

            loadMap(assign.mapName, assign.mapScale);
            lives = assign.lives;

            playerList = assign.playerList;
            powerups = assign.powerups;
            currentWave = assign.wave;
            weaponList = assign.weaponList;
            for(int id : playerList){
                showMessage("Adding id " + id + " to score");
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
        }


        if(object instanceof String){
            String command = (String) object;
            consoleFrame.giveCommand(command);
        }
    }

    private void doLogic(){

        float delta = Gdx.graphics.getDeltaTime();

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

        sendInputPackets();
        updateProjectiles(delta);

        //Dont pollInput or create snapshot if we have no state from server
        if (authoritativeState != null){
            if(stateSnapshot.get(playerID)!=null){
                pollInput((short) (delta*1000));
            }
            createStateSnapshot(getClientTime());
        }

        if(stateSnapshot!=null){
            statusData.setEntityCount(stateSnapshot.size());
        }
        statusData.setFps(Gdx.graphics.getFramesPerSecond());
        statusData.setServerTime(lastServerTime);
        statusData.setClientTime(getClientTime());
        statusData.currentInputRequest = currentInputRequest;
        statusData.inputQueue = inputQueue.size();
        statusFrame.update();
    }

    private void pollInput(short delta){
        float mouseX = camera.position.x-(camera.viewportWidth/2)+Gdx.input.getX();
        float mouseY = camera.position.y+(camera.viewportHeight/2)-Gdx.input.getY();
        if(lastMouseX==-1&&lastMouseY==-1){
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        if(buttons.size()>0||mouse1Pressed||mouse2Pressed||lastMouseX!=mouseX||lastMouseY!=mouseY){
            int inputRequestID = getNextInputRequestID();
            Network.UserInput input = new Network.UserInput();
            input.msec = delta;
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
        Entity player = stateSnapshot.get(playerID);
        if(player != null){
            sharedMethods.applyInput(player,input, mapWidth, mapHeight);
        }

        EnumSet<Enums.Buttons> buttons = input.buttons;

        if(buttons.contains(Enums.Buttons.NUM1)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                slot2Weapon = 0;
            }else{
                slot1Weapon = 0;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM2)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                slot2Weapon = 1;
            }else{
                slot1Weapon = 1;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM3)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                slot2Weapon = 2;
            }else{
                slot1Weapon = 2;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM4)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                slot2Weapon = 3;
            }else{
                slot1Weapon = 3;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM5)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                slot2Weapon = 4;
            }else{
                slot1Weapon = 4;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM6)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                slot2Weapon = 5;
            }else{
                slot1Weapon = 5;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM7)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                slot2Weapon = 6;
            }else{
                slot1Weapon = 6;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM8)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                slot2Weapon = 7;
            }else{
                slot1Weapon = 7;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM9)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                slot2Weapon = 8;
            }else{
                slot1Weapon = 8;
            }
        }
        if(buttons.contains(Enums.Buttons.NUM0)){
            if(buttons.contains(Enums.Buttons.SHIFT)){
                slot2Weapon = 9;
            }else{
                slot1Weapon = 9;
            }
        }
        if(buttons.contains(Enums.Buttons.MOUSE1)){
            playerAttack(stateSnapshot.get(playerID), slot1Weapon);
            mouse1Pressed = false;
        }
        if(buttons.contains(Enums.Buttons.MOUSE2)){
            playerAttack(stateSnapshot.get(playerID), slot2Weapon);
            mouse2Pressed = false;
        }
        attackCooldown -= (input.msec/1000d);
    }

    private void playerAttack(Entity player, int weaponSlot){
        if(attackCooldown>0){
            return;
        }
        attackCooldown = ConVars.getDouble("sv_attack_delay");
        //TODO Ignoring projectile team for now

        Weapon weapon = weaponList.get(weaponSlot);
        ProjectileDefinition projectileDefinition = weapon.projectile;
        if(weapon!=null){
            if(projectileDefinition.hitscan){
                sharedMethods.createHitscanAttack(weapon, player.getCenterX(), player.getCenterY(), player.getRotation(), attackVisuals);
            }else{
                projectiles.addAll(sharedMethods.createProjectileAttack(weapon, player.getCenterX(), player.getCenterY(), player.getRotation(), player.id, -1));
            }
            if(weapon.sound!=null){
                sounds.get(weapon.sound).play(soundVolume);
            }
        }
    }

    private void runClientSidePrediction(HashMap<Integer, Entity> state){

        if(playerID==-1){
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
            sharedMethods.applyInput(state.get(playerID),input,mapWidth,mapHeight);
        }
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

    private void createStateSnapshot(double clientTime){

        //printStateHistory();
        //If interp delay is 0 we simply copy the latest authorative state and run clientside prediction on that
        if(ConVars.getDouble("cl_interp") <= 0){
            HashMap<Integer, Entity> authoritativeStateCopy = new HashMap<Integer, Entity>();
            for(int id : authoritativeState.entities.keySet()){
                Entity e = authoritativeState.entities.get(id);
                Entity entityCopy = new Entity(e);
                authoritativeStateCopy.put(id,entityCopy);
            }
            runClientSidePrediction(authoritativeStateCopy);
            stateSnapshot = authoritativeStateCopy;
            return;
        }

        double renderTime = clientTime-ConVars.getDouble("cl_interp");

        //Copy this because it's modified from network thread
        Network.FullEntityUpdate[] stateHistoryCopy = stateHistory.toArray(new Network.FullEntityUpdate[stateHistory.size()]);

        //Find between which two states renderTime is
        ArrayList<Network.FullEntityUpdate> oldStates = new ArrayList<Network.FullEntityUpdate>();
        //The list is sorted starting with the oldest states, the first state we find that is newer than renderTime is thus our interpolation target
        for(Network.FullEntityUpdate state:stateHistoryCopy){ //FIXME Concurrent modification
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
        for (int i = stateHistoryCopy.length-1; i >= 0; i--) {
            if(stateHistoryCopy[i].serverTime<renderTime){
                interpFrom = stateHistoryCopy[i];
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

            HashMap<Integer, Entity> interpEntities = interpolateStates(interpFrom, interpTo, interpAlpha);

            runClientSidePrediction(interpEntities);
            stateSnapshot = interpEntities;
        }
    }

    private HashMap<Integer, Entity> interpolateStates(Network.FullEntityUpdate from, Network.FullEntityUpdate to, double alpha){
        HashMap<Integer, Entity> interpEntities = new HashMap<Integer, Entity>();
        //FIXME if interpolation destination is missing entity we need to remove it from result
        for(int id: from.entities.keySet()){
            if(id != playerID) {
                Entity fromPos = from.entities.get(id);
                Entity toPos = to.entities.get(id);
                Network.Position interpPos = interpolate(fromPos, toPos, alpha);
                //System.out.println("Interpolating from ("+fromPos.x+","+fromPos.y+") to ("+toPos.x+","+toPos.y+") Result ("+interpPos.x+","+interpPos.y+")");
                if (interpPos != null){
                    Entity interpEntity = new Entity(fromPos);
                    interpEntity.moveTo(interpPos.x,interpPos.y);
                    Entity authorativeEntity = authoritativeState.entities.get(id);
                    if(authorativeEntity != null){
                        interpEntity.setHealth(authorativeEntity.getHealth());
                    }
                    interpEntities.put(id, interpEntity);
                }
            }else{
                //For player position we use latest server position and apply clientside prediction on it later
                //FIXME copy this value or else you'll be modifying the authoritative state
                Entity player = authoritativeState.entities.get(playerID);
                Entity playerCopy = new Entity(player);
                interpEntities.put(id, playerCopy);
            }
        }
        return interpEntities;
    }

    private Network.Position interpolate(Entity from, Entity to, double alpha){
        if(to == null){
            //If entity has dissapeared in next state we simply remove it without interpolation
            return null;
        }
        Network.Position interpPos = new Network.Position();
        interpPos.x = (float)(from.getX()+(to.getX()-from.getX())*alpha);
        interpPos.y = (float)(from.getY()+(to.getY()-from.getY())*alpha);
        return interpPos;
    }

    private ArrayList<Network.UserInput> compressInputPacket(ArrayList<Network.UserInput> inputs){
        //System.out.println("Packing inputs:"+inputs.size());
        ArrayList<Network.UserInput> packedInputs = new ArrayList<Network.UserInput>();
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
                    ArrayList<Network.UserInput> packedInputs = compressInputPacket(pendingInputPacket);
                    inputPacket.inputs = packedInputs;
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
            projectile.move(delta);
            for(int id:stateSnapshot.keySet()){
                if(id==projectile.ownerID){
                    continue;
                }
                Entity target = stateSnapshot.get(id);
                if(Intersector.overlapConvexPolygons(projectile.getHitbox(), target.getPolygonBounds())) {
                    if(id == playerID){
                        sounds.get("hurt.ogg").play(soundVolume);
                    }else{
                        sounds.get("hit.ogg").play(soundVolume);
                    }
                    projectile.destroyed = true;
                }
            }
            if(projectile.destroyed){
                destroyedProjectiles.add(projectile);
            }
        }
        projectiles.removeAll(destroyedProjectiles);
    }

    @Override
    public void render() {

        doLogic();

        renderStart = System.nanoTime();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glLineWidth(3);
        Gdx.gl.glScissor((int) (windowWidth/2-camera.position.x), (int) (windowHeight/2-camera.position.y), mapWidth, mapHeight);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);

        if(stateSnapshot !=null){

            centerCameraOnPlayer();
            shapeRenderer.setProjectionMatrix(camera.combined);
            batch.setProjectionMatrix(camera.combined);

            if(mapRenderer!=null){
                mapRenderer.setView(camera);
                mapRenderer.render();
            }

            if(ConVars.getBool("cl_show_debug")) {
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(1, 0, 0, 1); //red
                for (Entity e : interpFrom.entities.values()) {
                    shapeRenderer.rect(e.getX(), e.getY(), e.width / 2, e.height / 2, e.width, e.height, 1, 1, e.getRotation());
                }
                shapeRenderer.setColor(0, 1, 0, 1); //green
                for (Entity e : interpTo.entities.values()) {
                    shapeRenderer.rect(e.getX(), e.getY(), e.width / 2, e.height / 2, e.width, e.height, 1, 1, e.getRotation());
                }
                shapeRenderer.setColor(0, 0, 1, 1); //blue
                for (Entity e : authoritativeState.entities.values()) {
                    shapeRenderer.rect(e.getX(), e.getY(), e.width / 2, e.height / 2, e.width, e.height, 1, 1, e.getRotation());
                }
                shapeRenderer.end();
            }
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            //Render entities
            for(int id: stateSnapshot.keySet()){ //FIXME will there ever be an entity that is not in stateSnapshot, yes when adding entities on server so we get nullpointer here
                Entity e = stateSnapshot.get(id);
                float health = 1-((float)e.getHealth()/e.maxHealth);
                if(playerList.contains(e.id)){
                    //Cast player position to int because we are centering the camera using casted values too
                    int playerX = (int)e.getX();
                    int playerY = (int)e.getY();
                    batch.begin();
                    batch.draw(down,playerX,playerY,e.width/2,e.height/2,e.width,e.height,1,1,e.getRotation()+180,true);
                    batch.end();
                    int healthbarWidth = e.width+20;
                    int healthbarHeight = 10;
                    int healthbarXOffset = -10;
                    int healthbarYOffset = e.width+10;
                    shapeRenderer.setColor(0,1,0,1);
                    shapeRenderer.rect(playerX+healthbarXOffset, playerY+healthbarYOffset, healthbarWidth,healthbarHeight);
                    shapeRenderer.setColor(1,0,0,1);
                    float healthWidth = healthbarWidth*health;
                    shapeRenderer.rect(playerX+healthbarXOffset, playerY+healthbarYOffset, healthWidth,healthbarHeight);
                }else{
                    shapeRenderer.setColor(1,1,1,1);
                    shapeRenderer.rect(e.getX(), e.getY(), e.width / 2, e.height / 2, e.width, e.height, 1, 1, e.getRotation());
                    float healthWidth = e.width*health;
                    shapeRenderer.setColor(1,0,0,1); //red
                    shapeRenderer.rect(e.getX(), e.getY(), e.width / 2, e.height / 2, healthWidth, e.height, 1, 1, e.getRotation());
                }
            }

            Powerup[] powerupsCopy = powerups.values().toArray(new Powerup[powerups.values().size()]);
            for(Powerup p : powerupsCopy){
                shapeRenderer.setColor(1, 0.4f, 0, 1); //safety orange
                shapeRenderer.circle(p.x,p.y,5);
            }

            shapeRenderer.end();
            sharedMethods.renderAttackVisuals(shapeRenderer,attackVisuals);
            sharedMethods.renderProjectiles(shapeRenderer,projectiles);
        }

        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        //Draw HUD
        batch.begin();
        batch.setProjectionMatrix(hudCamera.combined);
        int offset = 0;
        for(int id: playerList){
            font.draw(batch, id +" | Kills: "+ score.getPlayerKills(id)+ " Civilians killed: "+score.getNpcKills(id) + " Deaths: "+score.getDeaths(id) + " Team: "+authoritativeState.entities.get(id).getTeam(), 5, windowHeight-5-offset);
            offset += 20;
        }
        font.draw(batch, "Mouse 1: "+ (weaponList.get(slot1Weapon)!=null?weaponList.get(slot1Weapon).name:"N/A"), 5, 40);
        font.draw(batch, "Mouse 2: "+ (weaponList.get(slot2Weapon)!=null?weaponList.get(slot2Weapon).name:"N/A"), 5, 20);
        glyphLayout.setText(font, "Wave "+currentWave);
        font.draw(batch, "Wave "+currentWave,windowWidth/2-glyphLayout.width/2 ,windowHeight-5);
        glyphLayout.setText(font, "Lives: "+lives);
        font.draw(batch, "Lives: "+lives,windowWidth-glyphLayout.width ,windowHeight-5);
        batch.end();
    }

    private Network.FullEntityUpdate applyEntityPositionUpdate(Network.EntityPositionUpdate update){
        HashMap<Integer, Network.Position> changedEntityPositions = update.changedEntityPositions;
        HashMap<Integer,Entity> newEntityList = new HashMap<>();
        HashMap<Integer,Entity> oldEntityList = authoritativeState.entities;
        for(int id: oldEntityList.keySet()){
            Entity e = oldEntityList.get(id);
            if(changedEntityPositions.containsKey(id)){
                Network.Position pos = changedEntityPositions.get(id);
                Entity movedEntity = new Entity(e);
                movedEntity.moveTo(pos.x,pos.y);
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
        HashMap<Integer,Entity> newEntityList = new HashMap<>();
        HashMap<Integer,Entity> oldEntityList = authoritativeState.entities;
        for(int id: oldEntityList.keySet()) {
            Entity e = oldEntityList.get(id);
            if (changedEntityComponents.containsKey(id)) {
                ArrayList<Component> components = changedEntityComponents.get(id);
                Entity changedEntity = new Entity(e);
                for(Component component:components){
                    if(component instanceof Position){
                        Position pos = (Position) component;
                        changedEntity.moveTo(pos.x,pos.y);
                    }
                    if(component instanceof Heading) {
                        Heading heading = (Heading) component;
                        changedEntity.setHeading(heading.heading);
                    }
                    if(component instanceof Health) {
                        Health health = (Health) component;
                        changedEntity.setHealth(health.health);
                    }
                    if(component instanceof Rotation){
                        Rotation rotation = (Rotation) component;
                        changedEntity.setRotation(rotation.degrees);
                    }
                    if(component instanceof Team){
                        Team team = (Team) component;
                        changedEntity.setTeam(team.team);
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
            Entity e = new Entity(-1,50,50,-1);
            s.write(client, buffer ,e);
            showMessage("Entity size is " + buffer.position() + " bytes");
            buffer.clear();

            int totalEntitySize = 0;

            s.write(client, buffer ,e.getHeading());
            totalEntitySize += buffer.position();
            showMessage("Heading size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.getHealth());
            totalEntitySize += buffer.position();
            showMessage("Health size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.maxHealth);
            totalEntitySize += buffer.position();
            showMessage("Maxhealth size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.getX());
            totalEntitySize += buffer.position();
            showMessage("X size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.getY());
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
        if(character == '0'){
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
        Entity player = null;
        if(stateSnapshot!=null){
            player = stateSnapshot.get(playerID);
        }
        if(player != null) {
            //Cast values to int to avoid tile tearing
            camera.position.set((int) (player.getX() + player.width / 2f), (int) (player.getY() + player.height / 2), 0);
        }
        camera.update();
    }

    private void loadSounds(){

        File soundFolder = new File("resources"+File.separator+"sounds");
        showMessage("Loading sounds from: " + soundFolder);
        for (final File file : soundFolder.listFiles()) {
            if (!file.isDirectory()) {
                Sound sound = Gdx.audio.newSound(new FileHandle(file));
                sounds.put(file.getName(), sound);
                showMessage("Loaded: " + file.getName());
            }
        }
    }

    private void setPlayerID(int id){
        playerID = id;
        Gdx.graphics.setTitle(this.getClass().getSimpleName()+"["+playerID+"]");
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
        lastAuthoritativeStateReceived = System.nanoTime();
        lastServerTime = authoritativeState.serverTime;
    }

    private void loadMap(String mapName, float mapScale){
        map = new TmxMapLoader().load("resources"+File.separator+mapName);
        mapRenderer = new OrthogonalTiledMapRenderer(map,mapScale,batch);
        mapHeight = (int) ((Integer) map.getProperties().get("height")*(Integer) map.getProperties().get("tileheight")*mapScale);
        mapWidth = (int) ((Integer) map.getProperties().get("width")*(Integer) map.getProperties().get("tilewidth")*mapScale);
    }

    private void removeEntity(int id){
        //Entities will be removed from latest state despite their remove time
        authoritativeState.entities.remove(id);
        if(playerList.contains(id)){
            showMessage("PlayerID "+id+" removed.");
            playerList.remove(Integer.valueOf(id));
            score.removePlayer(id);
            if(id==playerID){
                setPlayerID(-1);
                slot1Weapon = 0;
                slot2Weapon = 1;
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
            if(weapon.projectile.hitscan){
                sharedMethods.createHitscanAttack(weapon,attack.x,attack.y,attack.deg, attackVisuals);
            }else{
                projectiles.addAll(sharedMethods.createProjectileAttack(weapon, attack.x, attack.y, attack.deg, attack.id, -1));
            }
            if(weapon.sound!=null){
                sounds.get(weapon.sound).play(soundVolume);
            }
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
