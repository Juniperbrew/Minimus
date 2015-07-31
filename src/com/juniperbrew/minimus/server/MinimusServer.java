package com.juniperbrew.minimus.server;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.Enums;
import com.juniperbrew.minimus.ExceptionLogger;
import com.juniperbrew.minimus.Network;
import com.juniperbrew.minimus.Powerup;
import com.juniperbrew.minimus.Projectile;
import com.juniperbrew.minimus.Score;
import com.juniperbrew.minimus.SharedMethods;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.Weapon;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.components.Heading;
import com.juniperbrew.minimus.components.Health;
import com.juniperbrew.minimus.components.Position;
import com.juniperbrew.minimus.components.Rotation;
import com.juniperbrew.minimus.components.Team;
import com.juniperbrew.minimus.windows.ConsoleFrame;
import com.juniperbrew.minimus.windows.Scoreboard;
import com.juniperbrew.minimus.windows.ServerStatusFrame;
import com.juniperbrew.minimus.windows.StatusData;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.awt.geom.Line2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class MinimusServer implements ApplicationListener, InputProcessor, EntityChangeListener, Score.ScoreChangeListener, ConVars.ConVarChangeListener {

    Server server;
    ShapeRenderer shapeRenderer;
    HashMap<Integer,ServerEntity> entities = new HashMap<>();
    ConcurrentHashMap<Integer,Powerup> powerups = new ConcurrentHashMap<>();
    HashMap<Integer,EntityAI> entityAIs = new HashMap<>();
    BidiMap<Integer, Connection> playerList = new DualHashBidiMap<>();
    HashMap<Connection,Integer> lastInputIDProcessed = new HashMap<>();
    HashMap<Connection,Integer> connectionUpdateRate = new HashMap<>();
    HashMap<Connection, ArrayList<Network.UserInput>> inputQueue = new HashMap<>();
    HashMap<Connection, StatusData> connectionStatus = new HashMap<>();

    ArrayList<Integer> posChangedEntities = new ArrayList<>();
    ArrayList<Integer> healthChangedEntities = new ArrayList<>();
    ArrayList<Integer> headingChangedEntities = new ArrayList<>();
    ArrayList<Integer> rotationChangedEntities = new ArrayList<>();
    ArrayList<Integer> teamChangedEntities = new ArrayList<>();
    ArrayList<Integer> changedEntities = new ArrayList<>();

    private int networkIDCounter = 1;
    private int currentTick = 0;
    long serverStartTime;

    int screenW;
    int screenH;

    private int writeBuffer = 16384; //Default 16384
    private int objectBuffer = 4096; //Default 2048
    private long lastPingUpdate;

    HashMap<Connection,Double> attackCooldown = new HashMap<>();
    Map<Integer,Integer> playerLives = new HashMap<>();
    private ArrayList<Line2D.Float> attackVisuals = new ArrayList<>();
    private ArrayList<Projectile> projectiles = new ArrayList<>();

    ByteBuffer buffer = ByteBuffer.allocate(objectBuffer);
    long logIntervalStarted;

    ServerStatusFrame serverStatusFrame;
    ConsoleFrame consoleFrame;
    StatusData serverData;

    public ConVars conVars;

    int mapWidth;
    int mapHeight;

    float viewPortX;
    float viewPortY;
    float cameraVelocity = 300f; //pix/s

    private OrthographicCamera camera;
    EnumSet<Enums.Buttons> buttons = EnumSet.noneOf(Enums.Buttons.class);

    int pendingEntityAdds = 0;
    int pendingEntityRemovals = 0;

    ArrayList<Integer> pendingDeadEntities = new ArrayList<>();
    ArrayList<Connection> pendingPlayerSpawns = new ArrayList<>();
    ArrayList<Connection> pendingPlayerDespawns = new ArrayList<>();

    SharedMethods sharedMethods;

    Score score = new Score(this);
    Scoreboard scoreboard = new Scoreboard(score);

    TiledMap map;
    private int wave;
    private int spawnedHealthPacksCounter;
    private long lastHealthPackSpawned;
    private final int HEALTHPACK_SPAWN_DELAY = 10;

    HashMap<Integer,WaveDefinition> waveList;
    HashMap<Integer,Weapon> weaponList;

    //This should not be more than 400 which is the max distance entities can look for a destination
    final int SPAWN_AREA_WIDTH = 200;
    public boolean spawnWaves;

    Timer timer = new Timer();

    private void initialize(){
        shapeRenderer = new ShapeRenderer();
        screenW = Gdx.graphics.getWidth();
        screenH = Gdx.graphics.getHeight();
        sharedMethods = new SharedMethods(conVars, mapWidth, mapHeight);
    }

    @Override
    public void create() {
        conVars = new ConVars(this);
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionLogger("server"));
        consoleFrame = new ConsoleFrame(conVars,this);

        waveList = readWaveList();
        weaponList = readWeaponList();
        int h = Gdx.graphics.getHeight();
        int w = Gdx.graphics.getWidth();
        resize(h,w);

        map = new TmxMapLoader().load("resources\\"+conVars.get("sv_map_name"));
        mapHeight = (int) ((Integer) map.getProperties().get("height")*(Integer) map.getProperties().get("tileheight")* conVars.getDouble("sv_map_scale"));
        mapWidth = (int) ((Integer) map.getProperties().get("width")*(Integer) map.getProperties().get("tilewidth")* conVars.getDouble("sv_map_scale"));

        serverStartTime = System.nanoTime();
        serverData = new StatusData(null,serverStartTime,conVars.getInt("cl_log_interval_seconds"));
        serverStatusFrame = new ServerStatusFrame(serverData);
        startServer();
        initialize();
        startSimulation();
        Gdx.input.setInputProcessor(this);

        serverData.entitySize = measureObject(new Entity(-1, 1000000, 1000000,-1));
        showMessage("Kryo entity size:" + serverData.entitySize + "bytes");
        showMessage("0 entityComponents size:" + measureObject(createComponentList(0,true,true,true)) + "bytes");
        showMessage("pos entityComponents size:" + measureObject(createComponentList(1,true,false,false)) + "bytes");
        showMessage("heading entityComponents size:" + measureObject(createComponentList(1,false,true,false)) + "bytes");
        showMessage("health entityComponents size:" + measureObject(createComponentList(1,false,false,true)) + "bytes");
        showMessage("1 entityComponents size:" + measureObject(createComponentList(1,true,true,true)) + "bytes");
        showMessage("10 entityComponents size:" + measureObject(createComponentList(10,true,true,true)) + "bytes");
        showMessage("100 entityComponents size:" + measureObject(createComponentList(100,true,true,true)) + "bytes");
        showMessage("100 pos entityComponents size:" + measureObject(createComponentList(100, true, false, false)) + "bytes");


        showMessage("0 entity position update size:" + measureObject(createPositionList(0)) + "bytes");
        showMessage("1 entity position update size:" + measureObject(createPositionList(1)) + "bytes");
        showMessage("10 entity position update size:" + measureObject(createPositionList(10)) + "bytes");
        showMessage("100 entity position update size:" + measureObject(createPositionList(100)) + "bytes");
    }

    public void setWave(int wave){
        this.wave = wave;
        Network.WaveChanged waveChanged = new Network.WaveChanged();
        waveChanged.wave = wave;
        sendTCPtoAll(waveChanged);
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
            addNPC();
        }
    }

    private void showMessage(String message){
        System.out.println(message);
        consoleFrame.addLine(message);
    }

    private int measureKryoEntitySize(){

        Kryo kryo = new Kryo();
        kryo.register(Entity.class);
        Output output = new Output(objectBuffer);
        kryo.writeObject(output, new Entity(-1, 1000000, 1000000,-1));
        int size = output.position();
        output.close();
        return size;
    }

    private HashMap<Integer,Network.Position> createPositionList(int entityCount){
        HashMap<Integer,Network.Position> changedPositions = new HashMap<>();
        for (int i = 0; i < entityCount; i++) {
            changedPositions.put(i,new Network.Position(50,50));
        }
        return changedPositions;
    }

    private HashMap<Integer,ArrayList<Component>> createComponentList(int entityCount, boolean pos, boolean heading, boolean health){
        HashMap<Integer,ArrayList<Component>> changedComponents = new HashMap<>();
        for (int i = 0; i < entityCount; i++) {
            ArrayList<Component> components = new ArrayList<>();
            if(pos)components.add(new Position(50,50));
            if(heading)components.add(new Heading(Enums.Heading.NORTH));
            if(health)components.add(new Health(100));
            changedComponents.put(i,components);
        }
        return changedComponents;
    }

    private int measureObject(Object o){
        KryoSerialization s = (KryoSerialization) server.getSerialization();
        s.write(new Client(), buffer ,o);
        int size = buffer.position();
        buffer.clear();
        return size;
    }

    private void logReceivedPackets(Connection connection, Object packet){
        KryoSerialization s = (KryoSerialization) server.getSerialization();
        s.write(connection, buffer ,packet);
        connectionStatus.get(connection).addBytesReceived(buffer.position());
        serverData.addBytesReceived(buffer.position());
        buffer.clear();
    }

    private void logIntervalElapsed(){
        serverData.intervalElapsed();
        for(StatusData dataUsage : connectionStatus.values()){
            dataUsage.intervalElapsed();
        }
    }

    public void sendCommand(String command){
        for(Connection connection :server.getConnections()){
            connection.sendTCP(command);
        }
    }

    private void moveViewport(float delta){
        float deltaY = 0;
        float deltaX = 0;
        if(buttons.contains(Enums.Buttons.UP)){
            deltaY = cameraVelocity * delta;
        }
        if(buttons.contains(Enums.Buttons.DOWN)){
            deltaY = -1* cameraVelocity *delta;
        }
        if(buttons.contains(Enums.Buttons.LEFT)){
            deltaX = -1* cameraVelocity *delta;
        }
        if(buttons.contains(Enums.Buttons.RIGHT)){
            deltaX = cameraVelocity *delta;
        }
        viewPortX += deltaX;
        viewPortY += deltaY;
        camera.position.set(viewPortX, viewPortY, 0);
        camera.update();
    }

    private HashMap<Integer,Entity> getNetworkedEntityList(HashMap<Integer,ServerEntity> serverEntities){
        HashMap<Integer,Entity> entities = new HashMap<>();
        for(int id:serverEntities.keySet()){
            entities.put(id,serverEntities.get(id).getNetworkEntity());
        }
        return entities;
    }

    public void updateFakePing(Connection connection) {
        StatusData status = connectionStatus.get(connection);
        if(status != null){
            Network.FakePing ping = new Network.FakePing();
            ping.id = status.lastPingID++;
            status.lastPingSendTime = System.currentTimeMillis();
            sendTCP(connection,ping);
        }
    }

    private void startServer(){
        server = new Server(writeBuffer,objectBuffer);
        Network.register(server);
        server.start();
        try {
            server.bind(Network.portTCP,Network.portUDP);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Listener listener = new Listener(){
            public void connected(Connection connection){
                StatusData dataUsage = new StatusData(connection, System.nanoTime(),conVars.getInt("cl_log_interval_seconds"));
                connectionStatus.put(connection, dataUsage);
                serverStatusFrame.addConnection(connection.toString(),dataUsage);
            }

            @Override
            public void disconnected(Connection connection){
                if(playerList.containsValue(connection)){
                    pendingPlayerDespawns.add(connection);
                }
            }

            public void received (Connection connection, Object object) {

                if (object instanceof Network.SpawnRequest){
                    pendingPlayerSpawns.add(connection);
                }
                if (object instanceof Network.TeamChangeRequest){
                    Network.TeamChangeRequest teamChangeRequest = (Network.TeamChangeRequest) object;
                    entities.get(playerList.getKey(connection)).setTeam(teamChangeRequest.team);
                }
                if (object instanceof Network.SendFile){
                    Network.SendFile sendFile = (Network.SendFile) object;
                    receiveFile(connection, sendFile);
                    return;
                }

                logReceivedPackets(connection,object);

                if (object instanceof Network.FakePing) {
                    StatusData status = connectionStatus.get(connection);
                    Network.FakePing ping = (Network.FakePing)object;
                    if (ping.isReply) {
                        if (ping.id == status.lastPingID - 1) {
                            status.setFakeReturnTripTime((int)(System.currentTimeMillis() - status.lastPingSendTime));
                        }
                    } else {
                        ping.isReply = true;
                        connection.sendTCP(ping);
                    }
                }else if(object instanceof Network.UserInputs){
                    Network.UserInputs inputPacket = (Network.UserInputs) object;
                    if(inputQueue.get(connection)!=null){
                        inputQueue.get(connection).addAll(inputPacket.inputs);
                    }else{
                        ArrayList<Network.UserInput> list = new ArrayList<Network.UserInput>();
                        list.addAll(inputPacket.inputs);
                        inputQueue.put(connection,list);
                    }
                }
            }
        };
        double minPacketDelay = conVars.getDouble("sv_min_packet_delay");
        double maxPacketDelay = conVars.getDouble("sv_max_packet_delay");
        if(maxPacketDelay > 0){
            int msMinDelay = (int) (minPacketDelay*1000);
            int msMaxDelay = (int) (maxPacketDelay*1000);
            Listener.LagListener lagListener = new Listener.LagListener(msMinDelay,msMaxDelay,listener);
            server.addListener(lagListener);
        }else{
            server.addListener(listener);
        }
    }

    private float getServerTime(){
        return ((System.nanoTime()-serverStartTime)/1000000000f);
    }

    private void processCommands(){
        for(Connection connection:inputQueue.keySet()){
            ArrayList<Network.UserInput> inputList = inputQueue.get(connection);
            if(!playerList.containsValue(connection)){
                //If player has no entity we ignore all input
                inputList.clear();
                continue;
            }
            ServerEntity e = entities.get(playerList.getKey(connection));
            Network.UserInput[] inputListCopy = inputList.toArray(new Network.UserInput[inputList.size()]);

            for(Network.UserInput input : inputListCopy){
                sharedMethods.applyInput(e,input);
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
                    attackWithPlayer(connection, e.slot1Weapon, input);
                }
                if(input.buttons.contains(Enums.Buttons.MOUSE2)){
                    attackWithPlayer(connection, e.slot2Weapon, input);
                }

                if(attackCooldown.get(connection)!=null){
                    double cd = attackCooldown.get(connection);
                    cd -= (input.msec/1000d);
                    attackCooldown.put(connection,cd);
                }

                lastInputIDProcessed.put(connection,input.inputID);
                inputList.remove(input); //FIXME we are removing this element from the original arraylist
            }
        }
    }

    private void attackWithPlayer(Connection connection, int weapon, Network.UserInput input){
        if(attackCooldown.get(connection)==null){
            attackCooldown.put(connection,-1d);
        }
        if(attackCooldown.get(connection) > 0){
            return;
        }else{
            attackCooldown.put(connection,conVars.getDouble("sv_attack_delay"));
            ServerEntity e = entities.get(playerList.getKey(connection));
            if(weapon == 1){
                showMessage(input.inputID + "> ["+getServerTime()+"] Creating projectile from PlayerCenterX: "+ e.getCenterX() + " PlayerCenterY: " + e.getCenterY() + " MouseX: " + input.mouseX + " MouseY: " + input.mouseY + " PlayerRotation: " + e.getRotation());
            }
            createAttack(e, weapon);
        }
    }

    public void createAttack(ServerEntity e, int weaponSlot){

        Network.EntityAttacking entityAttacking = new Network.EntityAttacking();
        entityAttacking.x = e.getCenterX();
        entityAttacking.y = e.getCenterY();
        entityAttacking.deg = e.getRotation();
        entityAttacking.id = e.id;
        entityAttacking.weapon = weaponSlot;
        if(playerList.keySet().contains(e.id)){
            sendTCPtoAllExcept(playerList.get(e.id),entityAttacking);
        }else{
            sendTCPtoAll(entityAttacking);
        }

        Weapon weapon = weaponList.get(weaponSlot);
        if(weapon==null){
            return;
        }
        if(weapon.hitScan){
            ArrayList<Line2D.Float> hitScans = sharedMethods.createHitscanAttack(weapon,e.getCenterX(),e.getCenterY(),e.getRotation(), attackVisuals);

            for(int id:entities.keySet()){
                ServerEntity target = entities.get(id);
                for(Line2D hitScan:hitScans){
                    if(target.getJavaBounds().intersectsLine(hitScan) && target.getTeam() != e.getTeam()){
                        target.reduceHealth(weapon.damage,e.id);
                    }
                }
            }
        }else{
            projectiles.addAll(sharedMethods.createProjectileAttack(weapon,e.getCenterX(),e.getCenterY(),e.getRotation(),e.id, e.getTeam()));
        }
    }

    private HashMap<Integer, Network.Position> getChangedEntityPositions(){
        HashMap<Integer, Network.Position> positions = new HashMap<>();
        for(int id: posChangedEntities){
            ServerEntity e = entities.get(id);
            positions.put(id,new Network.Position(e.getX(),e.getY()));
        }
        return positions;
    }

    private HashMap<Integer,ArrayList<Component>> getChangedEntityComponents(){
        HashMap<Integer,ArrayList<Component>> changedComponents = new HashMap<>();
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
        return changedComponents;
    }

    private void sendUDP(Connection connection, Object o){
        int bytesSent = connection.sendUDP(o);
        connectionStatus.get(connection).addBytesSent(bytesSent);
        serverData.addBytesSent(bytesSent);
    }

    private void sendTCP(Connection connection, Object o){
        int bytesSent = connection.sendTCP(o);
        connectionStatus.get(connection).addBytesSent(bytesSent);
        serverData.addBytesSent(bytesSent);
    }

    private void sendTCPtoAllExcept(Connection connection, Object o){
        for(Connection c:server.getConnections()){
            if(!c.equals(connection)){
                sendTCP(c,o);
            }
        }
    }
    private void sendTCPtoAll(Object o){
        for(Connection c:server.getConnections()){
            sendTCP(c,o);
        }
    }

    private void updateClients(){
        if(conVars.getInt("sv_update_type")==0){
            Network.FullEntityUpdate update = new Network.FullEntityUpdate();
            update.serverTime = getServerTime();
            update.entities = getNetworkedEntityList(entities);
            for(Connection connection :server.getConnections()){
                update.lastProcessedInputID = getLastInputIDProcessed(connection);
                if(conVars.getBool("cl_udp")){
                    sendUDP(connection,update);
                }else{
                    sendTCP(connection, update);
                }
            }
        }else if(conVars.getInt("sv_update_type")==1){
            Network.EntityPositionUpdate update = new Network.EntityPositionUpdate();
            update.serverTime = getServerTime();
            update.changedEntityPositions = getChangedEntityPositions();
            for(Connection connection :server.getConnections()){
                update.lastProcessedInputID = getLastInputIDProcessed(connection);
                if(conVars.getBool("cl_udp")){
                    sendUDP(connection, update);
                }else{
                    sendTCP(connection, update);
                }
            }
        }else if(conVars.getInt("sv_update_type")==2){
            Network.EntityComponentsUpdate update = new Network.EntityComponentsUpdate();
            update.serverTime = getServerTime();
            update.changedEntityComponents = getChangedEntityComponents();
            for(Connection connection :server.getConnections()) {
                update.lastProcessedInputID = getLastInputIDProcessed(connection);
                if(conVars.getBool("cl_udp")){
                    sendUDP(connection,update);
                }else{
                    sendTCP(connection,update);
                }
            }
        }

        changedEntities.clear();
        posChangedEntities.clear();
        healthChangedEntities.clear();
        healthChangedEntities.clear();
    }

    private int getLastInputIDProcessed(Connection connection){
        if(lastInputIDProcessed.get(connection) != null){
            return lastInputIDProcessed.get(connection);
        }else{
            return -1;
        }
    }

    private void addNPC(int aiType, int weapon){
        System.out.println("Adding npc "+aiType+","+weapon);
        int width = 50;
        int height = 50;
        float spawnPosition = MathUtils.random(SPAWN_AREA_WIDTH*-1,SPAWN_AREA_WIDTH);
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

    private void addNPC(){
        addNPC(MathUtils.random(0,3),MathUtils.random(0,2));
    }

    private void removeNPC(){
        Integer[] keys = entities.keySet().toArray(new Integer[entities.keySet().size()]);
        if(keys.length != 0){
            if(playerList.values().size() < entities.size()){
                int networkID ;
                int removeIndex;
                do{
                    removeIndex = MathUtils.random(keys.length - 1);
                }while(playerList.keySet().contains(keys[removeIndex]));
                showMessage("Remove index:" + removeIndex);
                networkID = keys[removeIndex];
                removeEntity(networkID);
            }
        }
    }

    private void addEntity(ServerEntity e){
        showMessage("Adding entity ID:" + e.id);
        entities.put(e.id,e);

        Network.AddEntity addEntity = new Network.AddEntity();
        addEntity.entity=e.getNetworkEntity();
        addEntity.serverTime=getServerTime();
        sendTCPtoAll(addEntity);
    }

    public void removeAllEntities(){
        for(int id:entityAIs.keySet()){
            pendingDeadEntities.add(id);
        }
    }

    private void removeEntity(int networkID){
        showMessage("Removing npc ID:"+networkID);
        entityAIs.remove(networkID);
        entities.remove(networkID);

        if(changedEntities.contains(networkID)){
            changedEntities.remove((Integer)networkID);
        }

        Network.RemoveEntity removeEntity = new Network.RemoveEntity();
        removeEntity.networkID=networkID;
        removeEntity.serverTime=getServerTime();
        sendTCPtoAll(removeEntity);
    }

    private void updateProjectiles(float delta){
        ArrayList<Projectile> destroyedProjectiles = new ArrayList<>();
        for(Projectile projectile:projectiles){
            Line2D.Float movedPath = projectile.move(delta);

            for(int id:entities.keySet()){
                if(projectile.ownerID==id){
                    continue;
                }
                ServerEntity target = entities.get(id);
                if(target.getJavaBounds().intersectsLine(movedPath)){
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

    private int getNextNetworkID(){
        return networkIDCounter++;
    }

    private void checkPowerupCollisions(){
        for(int playerID : playerList.keySet()){
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

    private void checkPlayerEntityCollisions(){

        for(int playerID : playerList.keySet()){
            ServerEntity player = entities.get(playerID);
            Iterator<ServerEntity> iter = entities.values().iterator();
            while(iter.hasNext()){
                ServerEntity e = iter.next();
                if(e.getTeam()!=player.getTeam()){
                    if(player.getJavaBounds().intersects(e.getJavaBounds())){
                        if(!isInvulnerable(player)) {
                            player.lastDamageTaken = System.nanoTime();
                            player.reduceHealth(conVars.getInt("sv_contact_damage"),e.id);
                        }
                    }
                }
            }
        }
    }

    private boolean isInvulnerable(ServerEntity e){
        if(System.nanoTime()-e.lastDamageTaken> Tools.secondsToNano(conVars.getDouble("sv_invulnerability_timer"))){
            return false;
        }else{
            System.out.println(e.id + " is still invulnerable for " + (System.nanoTime()-e.lastDamageTaken));
            return true;
        }
    }

    private void spawnPlayer(Connection connection){
        int networkID = getNextNetworkID();
        playerList.put(networkID,connection);
        Network.FullEntityUpdate fullUpdate = new Network.FullEntityUpdate();
        fullUpdate.entities = getNetworkedEntityList(entities);
        connection.sendTCP(fullUpdate);

        int width = 50;
        int height = 50;
        float x = MathUtils.random(mapWidth -width);
        float y = MathUtils.random(mapHeight -height);
        playerLives.put(networkID, conVars.getInt("sv_start_lives"));
        ServerEntity newPlayer = new ServerEntity(networkID,x,y,1,MinimusServer.this);
        newPlayer.width = width;
        newPlayer.height = height;
        addEntity(newPlayer);

        serverData.addPlayer();
        score.addPlayer(networkID);
        Network.AddPlayer addPlayer = new Network.AddPlayer();
        addPlayer.networkID = networkID;
        sendTCPtoAllExcept(connection,addPlayer);

        Network.AssignEntity assign = new Network.AssignEntity();
        assign.networkID = networkID;
        assign.lives = playerLives.get(networkID);
        assign.velocity = conVars.getFloat("sv_player_velocity");
        assign.mapName = conVars.get("sv_map_name");
        assign.mapScale = conVars.getFloat("sv_map_scale");
        assign.playerList = new ArrayList<>(playerList.keySet());
        assign.powerups = new HashMap<>(powerups);
        assign.wave = wave;
        assign.weaponList = new HashMap<>(weaponList);
        connection.sendTCP(assign);
    }

    private void despawnPlayer(Connection connection){
        int networkID = playerList.getKey(connection);
        removeEntity(networkID);
        playerList.remove(networkID);
        serverData.removePlayer();
        score.removePlayer(networkID);
        connectionStatus.get(connection).disconnected();
    }

    private void spawnPowerup(int type, int value, int duration){
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

        Network.AddPowerup addPowerup = new Network.AddPowerup();
        addPowerup.networkID = id;
        addPowerup.powerup = powerup;
        sendTCPtoAll(addPowerup);
    }

    private void despawnPowerup(int id){
        powerups.remove(id);

        Network.RemovePowerup removePowerup = new Network.RemovePowerup();
        removePowerup.networkID = id;
        sendTCPtoAll(removePowerup);
    }

    private void startSimulation(){
        Thread thread = new Thread(new Runnable(){
            @Override
            public void run() {

                long targetTickDurationNano, tickStartTime = 0,tickEndTime = 0,tickActualDuration = 0, tickWorkDuration, sleepDuration;
                long lastUpdateSent = 0;
                while(true){
                    targetTickDurationNano= (long) (1000000000/conVars.getInt("sv_tickrate"));
                    if(tickEndTime>0){
                        tickActualDuration=tickEndTime-tickStartTime;
                    }
                    tickStartTime=System.nanoTime();
                    currentTick++;
                    float delta = tickActualDuration/1000000000f;


                    if(System.nanoTime()-lastPingUpdate>Tools.secondsToNano(conVars.getDouble("cl_ping_update_delay"))){
                        for(Connection c:server.getConnections()){
                            connectionStatus.get(c).updatePing();
                            updateFakePing(c);
                        }
                        lastPingUpdate = System.nanoTime();
                    }

                    for(Connection c:pendingPlayerSpawns){
                        spawnPlayer(c);
                    }
                    pendingPlayerSpawns.clear();

                    for(Connection c: pendingPlayerDespawns){
                        despawnPlayer(c);
                    }
                    pendingPlayerDespawns.clear();

                    if(spawnWaves){
                        if(entityAIs.isEmpty()){
                            spawnedHealthPacksCounter=0;
                            if(conVars.getBool("sv_custom_waves")){
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

                    for (int i = 0; i < pendingEntityAdds; i++) {
                        addNPC();
                    }
                    pendingEntityAdds = 0;
                    for (int i = 0; i < pendingEntityRemovals; i++) {
                        removeNPC();
                    }
                    pendingEntityRemovals = 0;

                    for(int id: pendingDeadEntities){
                        removeEntity(id);
                    }
                    pendingDeadEntities.clear();

                    for(EntityAI ai:entityAIs.values()){
                        ai.act(conVars.getDouble("sv_npc_velocity"), delta);
                    }

                    processCommands();
                    float processCommandsCheckpoint = (System.nanoTime()-tickStartTime)/1000000f;

                    updateProjectiles(delta);
                    checkPlayerEntityCollisions();
                    checkPowerupCollisions();

                    float moveNpcCheckpoint = (System.nanoTime()-tickStartTime)/1000000f;

                    if(System.nanoTime()-lastUpdateSent>(1000000000/conVars.getInt("sv_updaterate"))){
                        updateClients();
                        lastUpdateSent = System.nanoTime();
                    }
                    float updateClientsCheckpoint = (System.nanoTime()-tickStartTime)/1000000f;


                    tickWorkDuration=System.nanoTime()-tickStartTime;
                    if((tickWorkDuration/1000000f) > 20) {
                        showMessage("Tick:" + currentTick);
                        showMessage("Process commands:" + processCommandsCheckpoint);
                        showMessage("MoveNPC:" + (moveNpcCheckpoint - processCommandsCheckpoint));
                        showMessage("UpdateClients:" + (updateClientsCheckpoint - moveNpcCheckpoint));
                        showMessage("Work:" + (tickWorkDuration / 1000000f));
                    }

                    sleepDuration = targetTickDurationNano-tickWorkDuration;
                    if(sleepDuration<1){
                        sleepDuration = 1;
                    }
                    try {
                        Thread.sleep((int)(sleepDuration/1000000f),(int)(sleepDuration%1000000f));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    tickEndTime=System.nanoTime();
                }
            }
        });
        thread.start();
    }

    @Override
    public void render() {
        if(System.nanoTime()- logIntervalStarted > Tools.secondsToNano(conVars.getInt("cl_log_interval_seconds"))){
            logIntervalStarted = System.nanoTime();
            logIntervalElapsed();
        }
        serverStatusFrame.update();

        moveViewport(Gdx.graphics.getDeltaTime());
        shapeRenderer.setProjectionMatrix(camera.combined);

        Gdx.gl.glClearColor(0.5f, 0.5f, 0.5f, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glLineWidth(3);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0,0,0,1);
        shapeRenderer.rect(0, 0, mapWidth, mapHeight);
        shapeRenderer.setColor(1,1,1,1);

        ServerEntity[] entitiesCopy = entities.values().toArray(new ServerEntity[entities.values().size()]);
        for(ServerEntity e : entitiesCopy) {
            if(playerList.keySet().contains(e.id)){
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

        sharedMethods.renderAttackVisuals(shapeRenderer,attackVisuals);
        sharedMethods.renderProjectiles(shapeRenderer,projectiles);

        updateStatusData();
    }

    private void updateStatusData(){
        for(Connection c:inputQueue.keySet()){
            connectionStatus.get(c).inputQueue = inputQueue.get(c).size();
        }
        serverData.fps = Gdx.graphics.getFramesPerSecond();
        serverData.setEntityCount(entities.size());
        serverData.currentTick = currentTick;
        serverData.setServerTime((System.nanoTime() - serverStartTime) / 1000000000f);
    }

    private void addPlayerKill(int id){
        score.addPlayerKill(id);
        Network.AddPlayerKill addPlayerKill = new Network.AddPlayerKill();
        addPlayerKill.id = id;
        sendTCPtoAll(addPlayerKill);
    }
    private void addNpcKill(int id){
        score.addNpcKill(id);
        Network.AddNpcKill addNpcKill = new Network.AddNpcKill();
        addNpcKill.id = id;
        sendTCPtoAll(addNpcKill);
    }
    private void addDeath(int id){
        score.addDeath(id);
        Network.AddDeath addDeath = new Network.AddDeath();
        addDeath.id = id;
        sendTCPtoAll(addDeath);
    }

    public void showScoreboard(){
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
                scoreboard.setVisible(true);
            }
        }).start();
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

    public void showServerStatusWindow(){
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
                serverStatusFrame.setVisible(true);
            }
        }).start();
    }

    private void receiveFile(Connection c, Network.SendFile sendFile){
        String fileName = sendFile.fileName;
        byte[] data = sendFile.data;
        String dateStamp = sendFile.dateStamp;
        consoleFrame.addLine("Received file: "+fileName);
        String folderName = Tools.getUserDataDirectory()+ File.separator+"receivedfiles"+File.separator+c.getRemoteAddressUDP().getHostName()+File.separator+dateStamp+File.separator;
        File folder = new File(folderName);
        folder.mkdirs();
        File file = new File(folderName+fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
        }
        catch(FileNotFoundException ex)   {
            System.out.println("FileNotFoundException : " + ex);
            return;
        }
        catch(IOException ioe)  {
            System.out.println("IOException : " + ioe);
            return;
        }
        Network.FileReceived fileReceived = new Network.FileReceived();
        fileReceived.fileName = fileName;
        c.sendTCP(fileReceived);
    }

    private HashMap<Integer,Weapon> readWeaponList(){
        File file = new File(Tools.getUserDataDirectory()+ File.separator+"weaponlist.txt");
        if(!file.exists()){
            file = new File("resources\\"+"defaultweaponlist.txt");
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
                if(splits[0].equals("type")){
                    if(splits[1].equals("hitscan")){
                        weapon.hitScan = true;
                    }
                }
                if(splits[0].equals("name")){
                    weapon.name = splits[1];
                }
                if(splits[0].equals("range")){
                    weapon.range = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("velocity")){
                    weapon.velocity = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("spread")){
                    weapon.spread = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("projectileCount")){
                    weapon.projectileCount = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("damage")){
                    weapon.damage = Integer.parseInt(splits[1]);
                }
                if(splits[0].equals("visualDuration")){
                    weapon.visualDuration = Float.parseFloat(splits[1]);
                }
                if(splits[0].equals("sound")){
                    weapon.sound = splits[1];
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

    private HashMap<Integer,WaveDefinition> readWaveList(){
        File file = new File(Tools.getUserDataDirectory()+ File.separator+"wavelist.txt");
        if(!file.exists()){
            file = new File("resources\\"+"defaultwavelist.txt");
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

    @Override
    public boolean keyTyped(char character) {
        if (character == 'u') {
            boolean useUDP = conVars.getBool("cl_udp");
            if(useUDP){
                conVars.set("cl_udp",0);
            }else{
                conVars.set("cl_udp",1);
            }

            showMessage("UseUDP:" + conVars.getBool("cl_udp"));
        }
        if (character == 'q') {
            pendingEntityRemovals++;
        }
        if (character == 'p') {
            spawnPowerup(Powerup.HEALTH,30,30);
        }
        if (character == 'w') {
            pendingEntityAdds++;
        }
        if (character == 's') {
            showScoreboard();
        }
        if (character == '-') {
            camera.zoom += 0.05;
            camera.update();
        }
        if (character == '+') {
            camera.zoom -= 0.05;
            if(camera.zoom < 0.1){
                camera.zoom = 0.1f;
            }
            camera.update();
        }
        if (character == 'r') {
            camera.zoom = 1;
            camera.update();
        }
        if(character == 'i'){
            serverData.writeLog(true);
        }
        if (character == '1') {
            showConsoleWindow();
        }
        if (character == '2'){
            showServerStatusWindow();
        }
        if (character == '3') {
            if(conVars.getInt("sv_tickrate") <=1){
                int tickRate = conVars.getInt("sv_tickrate") / 2;
                conVars.set("sv_tickrate",tickRate);
            }else{
                int tickRate = conVars.getInt("sv_tickrate") - 1;
                conVars.set("sv_tickrate",tickRate);
            }
        }
        if (character == '4') {
            if(conVars.getInt("sv_tickrate") <1){
                int tickRate = conVars.getInt("sv_tickrate") * 2;
                conVars.set("sv_tickrate",tickRate);
                if(conVars.getInt("sv_tickrate") > 1){
                    conVars.set("sv_tickrate",1);
                }
            }else{
                int tickRate = conVars.getInt("sv_tickrate") + 1;
                conVars.set("sv_tickrate",tickRate);
            }
        }
        if (character == '8') {
            if(conVars.getInt("sv_updaterate") <=1){
                int updateRate = conVars.getInt("sv_updaterate")/2;
                conVars.set("sv_updaterate",updateRate);
            }else{
                int updateRate = conVars.getInt("sv_updaterate")-1;
                conVars.set("sv_updaterate",updateRate);
            }

        }
        if (character == '9') {
            if(conVars.getInt("sv_updaterate") <1){
                int updateRate = conVars.getInt("sv_updaterate")*2;
                conVars.set("sv_updaterate",updateRate);
                if(conVars.getInt("sv_updaterate") > 1){
                    conVars.set("sv_updaterate",1);
                }
            }else{
                int updateRate = conVars.getInt("sv_updaterate")+1;
                conVars.set("sv_updaterate",updateRate);
            }
        }
        return false;
    }

    @Override
    public void resize(int w, int h) {
        if(h > ((float)w/ mapWidth)* mapHeight){
            camera = new OrthographicCamera(mapWidth, h*((float) mapWidth /w));
        }else{
            camera = new OrthographicCamera(w*((float) mapHeight /h), mapHeight);
        }
        viewPortX = mapWidth /2f;
        viewPortY = mapHeight /2f;
        camera.position.set(viewPortX, viewPortY, 0);
        camera.update();
    }
    @Override
    public void pause() {}
    @Override
    public void resume() {}
    @Override
    public void dispose() {
        serverData.writeLog(true);
    }

    @Override
    public boolean keyDown(int keycode) {
        if(keycode == Input.Keys.F1){
            consoleFrame.showHelp();
        }
        if(keycode == Input.Keys.LEFT) buttons.add(Enums.Buttons.LEFT);
        if(keycode == Input.Keys.RIGHT) buttons.add(Enums.Buttons.RIGHT);
        if(keycode == Input.Keys.UP)buttons.add(Enums.Buttons.UP);
        if(keycode == Input.Keys.DOWN)buttons.add(Enums.Buttons.DOWN);
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if(keycode == Input.Keys.LEFT) buttons.remove(Enums.Buttons.LEFT);
        if(keycode == Input.Keys.RIGHT) buttons.remove(Enums.Buttons.RIGHT);
        if(keycode == Input.Keys.UP)buttons.remove(Enums.Buttons.UP);
        if(keycode == Input.Keys.DOWN)buttons.remove(Enums.Buttons.DOWN);
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
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
    public void positionChanged(int id) {
        if(!posChangedEntities.contains(id)){
            posChangedEntities.add(id);
        }
        if(!changedEntities.contains(id)){
            changedEntities.add(id);
        }
    }

    @Override
    public void healthChanged(int id) {
        if(!healthChangedEntities.contains(id)){
            healthChangedEntities.add(id);
        }
        if(!changedEntities.contains(id)){
            changedEntities.add(id);
        }
    }

    @Override
    public void headingChanged(int id) {
        if(!headingChangedEntities.contains(id)){
            headingChangedEntities.add(id);
        }
        if(!changedEntities.contains(id)){
            changedEntities.add(id);
        }
    }

    @Override
    public void rotationChanged(int id) {
        if(!rotationChangedEntities.contains(id)){
            rotationChangedEntities.add(id);
        }
        if(!changedEntities.contains(id)){
            changedEntities.add(id);
        }
    }

    @Override
    public void teamChanged(int id) {
        if(!teamChangedEntities.contains(id)){
            teamChangedEntities.add(id);
        }
        if(!changedEntities.contains(id)){
            changedEntities.add(id);
        }
    }

    @Override
    public void entityDied(int id, int sourceID) {
        showMessage("Entity ID" + id + " is dead.");
        if(playerList.keySet().contains(sourceID)) {
            if (playerList.keySet().contains(id)) {
                addPlayerKill(sourceID);
            } else {
                addNpcKill(sourceID);
            }
        }
        if (playerList.keySet().contains(id)) {
            addDeath(id);
            Tools.addToMap(playerLives,id,-1);
            Network.SetLives setLives = new Network.SetLives();
            setLives.lives = playerLives.get(id);
            sendTCP(playerList.get(id),setLives);
            if(playerLives.get(id) >= 0){
                //Respawn player if lives left
                ServerEntity deadPlayer = entities.get(id);
                deadPlayer.restoreMaxHealth();
                float x = MathUtils.random(mapWidth - deadPlayer.width);
                float y = MathUtils.random(mapHeight - deadPlayer.height);
                deadPlayer.moveTo(x, y);
            }else{
                pendingPlayerDespawns.add(playerList.get(id));
            }
        }else{
            pendingDeadEntities.add(id);
        }
    }

    @Override
    public void scoreChanged() {
        scoreboard.updateScoreboard();
    }

    @Override
    public void conVarChanged(String varName, String varValue) {

    }
}
