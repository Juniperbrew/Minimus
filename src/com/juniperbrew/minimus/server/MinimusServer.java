package com.juniperbrew.minimus.server;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.ObjectMap;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.ConsoleReader;
import com.juniperbrew.minimus.G;
import com.juniperbrew.minimus.NetworkEntity;
import com.juniperbrew.minimus.Enums;
import com.juniperbrew.minimus.ExceptionLogger;
import com.juniperbrew.minimus.Network;
import com.juniperbrew.minimus.Powerup;
import com.juniperbrew.minimus.ProjectileDefinition;
import com.juniperbrew.minimus.Score;
import com.juniperbrew.minimus.SharedMethods;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.Weapon;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.windows.ConsoleFrame;
import com.juniperbrew.minimus.windows.ServerStatusFrame;
import com.juniperbrew.minimus.windows.StatusData;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class MinimusServer implements ApplicationListener, InputProcessor, Score.ScoreChangeListener, ConVars.ConVarChangeListener, World.WorldChangeListener, G.ConsoleLogger {

    Server server;
    ShapeRenderer shapeRenderer;
    SpriteBatch batch;
    HashMap<Connection,Integer> lastInputIDProcessed = new HashMap<>();
    HashMap<Connection,Integer> connectionUpdateRate = new HashMap<>();
    HashMap<Connection, ArrayList<Network.UserInput>> inputQueue = new HashMap<>();
    HashMap<Connection, StatusData> connectionStatus = new HashMap<>();
    long lastUpdateSent;
    ConcurrentLinkedQueue<Connection> connections = new ConcurrentLinkedQueue<>();

    BidiMap<Integer, Connection> playerList = new DualHashBidiMap<>();

    private int currentTick = 0;
    long serverStartTime;

    private int writeBuffer = 16384; //Default 16384
    private int objectBuffer = 4096; //Default 2048
    private long lastPingUpdate;

    ByteBuffer buffer = ByteBuffer.allocate(objectBuffer);
    long logIntervalStarted;

    ServerStatusFrame serverStatusFrame;
    ConsoleFrame consoleFrame;
    StatusData serverData;

    int windowWidth;
    int windowHeight;
    float viewPortX;
    float viewPortY;
    float cameraVelocity = 300f; //pix/s

    private OrthographicCamera camera;
    private OrthographicCamera hudCamera;
    EnumSet<Enums.Buttons> buttons = EnumSet.noneOf(Enums.Buttons.class);

    int pendingRandomNpcAdds = 0;
    int pendingRandomNpcRemovals = 0;
    boolean set200Entities;

    private ConcurrentLinkedQueue<Packet> pendingPackets = new ConcurrentLinkedQueue<>();

    Score score = new Score(this);

    World world;

    private final float TIMESTEP = (1/60f);
    private float ackumulator;

    Queue<Float> fpsLog = new CircularFifoQueue<>(100);
    Queue<Float> deltaLog = new CircularFifoQueue<>(100);
    Queue<Float> logicLog = new CircularFifoQueue<>(100);
    Queue<Float> renderLog = new CircularFifoQueue<>(100);
    Queue<Float> frameTimeLog = new CircularFifoQueue<>(100);
    private boolean showGraphs;

    BitmapFont font;

    @Override
    public void create() {
        //Log.TRACE();
        G.consoleLogger = this;
        ConVars.addListener(this);
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionLogger("server"));
        consoleFrame = new ConsoleFrame(this);
        new ConsoleReader(consoleFrame);
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        world = new World(this, new TmxMapLoader(), batch);
        int h = Gdx.graphics.getHeight();
        int w = Gdx.graphics.getWidth();
        camera = new OrthographicCamera();
        hudCamera = new OrthographicCamera(windowWidth,windowHeight);
        resize(h, w);
        font = new BitmapFont();

        serverStartTime = System.nanoTime();
        serverData = new StatusData(serverStartTime,ConVars.getInt("cl_log_interval_seconds"));
        serverStatusFrame = new ServerStatusFrame(serverData);
        startServer();
        Gdx.input.setInputProcessor(this);
        ObjectMap map = server.getKryo().getContext();
        System.out.println("Position registration: " + server.getKryo().getRegistration(Component.Position.class));
        for (int i = 0; i < server.getKryo().getNextRegistrationId(); i++) {
            Registration r = server.getKryo().getRegistration(i);
            System.out.print(r);
            System.out.println(" "+r.getSerializer());
        }

        serverData.entitySize = measureObject(new NetworkEntity(-1, 1, 1,-1));
        showMessage("Kryo entity size:" + serverData.entitySize + "bytes");
        showMessage("0 (empty)entityComponents size:" + measureObject(createComponentList(0, false, false, false)) + "bytes");
        showMessage("0 entityComponents size:" + measureObject(createComponentList(0, true, false, true)) + "bytes");

        showMessage("Position size:" + measureObject(new Component.Position(1000000000, 1000000000)));
        System.out.println(Float.floatToIntBits(1000000000));

        showMessage("(empty)entityComponents size:" + measureObject(createComponentList(1, false, false, false)) + "bytes");
        showMessage("(pos)entityComponents size:" + measureObject(createComponentList(1, true, false, false)) + "bytes");
        showMessage("(health)entityComponents size:" + measureObject(createComponentList(1, false, false, true)) + "bytes");

        showMessage("1 (pos+health)entityComponents size:" + measureObject(createComponentList(1, true, false, true)) + "bytes");
        //showMessage("10 (pos+health)entityComponents size:" + measureObject(createComponentList(10,true,false, true)) + "bytes");
        //showMessage("100 (pos+health)entityComponents size:" + measureObject(createComponentList(100,true,false, true)) + "bytes");

        showMessage("1 entityFullComponents size:" + measureObject(createFullComponentList(1)) + "bytes");
        //showMessage("10 entityFullComponents size:" + measureObject(createFullComponentList(10)) + "bytes");
        //showMessage("100 entityFullComponents size:" + measureObject(createFullComponentList(100)) + "bytes");
        //showMessage("100 (pos)entityComponents size:" + measureObject(createComponentList(100, true, false, false)) + "bytes");


        showMessage("0 entity position update size:" + measureObject(createPositionList(0)) + "bytes");
        showMessage("1 entity position update size:" + measureObject(createPositionList(1)) + "bytes");
        //showMessage("10 entity position update size:" + measureObject(createPositionList(10)) + "bytes");
        //showMessage("100 entity position update size:" + measureObject(createPositionList(100)) + "bytes");
    }

    private void sendFullStateUpdate(Connection c){
        Network.FullEntityUpdate fullUpdate = new Network.FullEntityUpdate();
        fullUpdate.entities = world.getNetworkedEntities();
        fullUpdate.serverTime = getServerTime();
        c.sendTCP(fullUpdate);
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
                StatusData dataUsage = new StatusData(connection, System.nanoTime(),ConVars.getInt("cl_log_interval_seconds"));
                connection.setTimeout(G.TIMEOUT);
                connectionStatus.put(connection, dataUsage);
                serverStatusFrame.addConnection(connection.toString(), dataUsage);
                sendFullStateUpdate(connection);
                connections.add(connection);
            }

            @Override
            public void disconnected(Connection connection){
                pendingPackets.add(new Packet(connection,new Disconnect()));
            }

            public void received (Connection connection, Object object) {

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
                    return;
                }
                pendingPackets.add(new Packet(connection,object));
            }
        };
        double minPacketDelay = ConVars.getDouble("sv_min_packet_delay");
        double maxPacketDelay = ConVars.getDouble("sv_max_packet_delay");
        if(maxPacketDelay > 0){
            int msMinDelay = (int) (minPacketDelay*1000);
            int msMaxDelay = (int) (maxPacketDelay*1000);
            Listener.LagListener lagListener = new Listener.LagListener(msMinDelay,msMaxDelay,listener);
            server.addListener(lagListener);
        }else{
            server.addListener(listener);
        }
    }

    private void handlePacket(Connection connection, Object object){
        if(object instanceof Network.UserInputs){
            Network.UserInputs inputPacket = (Network.UserInputs) object;
            if(inputQueue.get(connection)!=null){
                inputQueue.get(connection).addAll(inputPacket.inputs);
            }else{
                ArrayList<Network.UserInput> list = new ArrayList<>();
                list.addAll(inputPacket.inputs);
                inputQueue.put(connection, list);
            }
        }else if (object instanceof Network.SpawnRequest){
            if(world.playerList.contains(playerList.getKey(connection))){
                world.removePlayer(playerList.getKey(connection));
            }
            world.addPlayer(connection);
        }else if (object instanceof Network.TeamChangeRequest){
            Network.TeamChangeRequest teamChangeRequest = (Network.TeamChangeRequest) object;
            world.getEntity(playerList.getKey(connection)).setTeam(teamChangeRequest.team);
        }else if (object instanceof Network.ChangeWeapon){
            Network.ChangeWeapon changeWeapon = (Network.ChangeWeapon) object;
            world.setPlayerWeapon(playerList.getKey(connection), changeWeapon.weapon);
        }else if(object instanceof Disconnect){
            if(connectionStatus.containsKey(connection)){
                connectionStatus.get(connection).disconnected();
            }else{
                showMessage(connection + " disconnected but is not in the connectionlist");
            }
            if(playerList.containsValue(connection)) {
                world.removePlayer(playerList.getKey(connection));
            }
            connections.remove(connection);
        }else if(object instanceof Network.GameClockCompare){
            Network.GameClockCompare gameClockCompare = (Network.GameClockCompare) object;
            showMessage("Received gameClockCompare("+playerList.getKey(connection)+"): "+gameClockCompare.serverTime + " Delta: "+(gameClockCompare.serverTime-getServerTime()));
        }else if(object instanceof Network.BuyItem){
            Network.BuyItem buyItem = (Network.BuyItem) object;
            showMessage("Player "+playerList.getKey(connection)+" buying "+buyItem.amount+" of item "+G.shoplist.get(buyItem.id)+"("+buyItem.id+")");
            world.buyItem(playerList.getKey(connection),buyItem.id,buyItem.amount);
        }else if(object instanceof Network.SellItem){
            Network.SellItem sellItem = (Network.SellItem) object;
            showMessage("Player "+playerList.getKey(connection)+" selling "+sellItem.amount+" of item "+G.shoplist.get(sellItem.id)+"("+sellItem.id+")");
            world.sellItem(playerList.getKey(connection),sellItem.id,sellItem.amount);
        }
    }

    private void doLogic(float delta){

        long logicStart = System.nanoTime();
        updateStatusData();

        for(Packet p;(p = pendingPackets.poll())!=null;){
            handlePacket(p.connection,p.content);
        }

        updatePing();
        processPendingEntityAddsAndRemovals();
        processCommands();
        world.updateWorld(delta);

        if(System.nanoTime()-lastUpdateSent>(1000000000/ConVars.getInt("sv_updaterate"))){
            updateClients();
            lastUpdateSent = System.nanoTime();
        }
        logicLog.add(Tools.nanoToMilliFloat(System.nanoTime() - logicStart));
    }

    private void updatePing(){
        if(System.nanoTime()-lastPingUpdate>Tools.secondsToNano(ConVars.getDouble("cl_ping_update_delay"))){
            for(Connection c:connectionStatus.keySet()){
                connectionStatus.get(c).updatePing();
                updateFakePing(c);
            }
            lastPingUpdate = System.nanoTime();
        }
    }

    private void processCommands(){
        for(Connection connection:inputQueue.keySet()){
            ArrayList<Network.UserInput> inputList = inputQueue.get(connection);
            if(!playerList.containsValue(connection)){
                //If player has no entity we ignore all input
                inputList.clear();
                continue;
            }
            int id = playerList.getKey(connection);

            for(Network.UserInput input : inputList){
                world.processInput((PlayerServerEntity) world.entities.get(id), input);
                lastInputIDProcessed.put(connection,input.inputID);
            }
            inputList.clear();
        }
    }

    private void updateClients(){
        if(ConVars.getInt("sv_update_type")==0){
            Network.FullEntityUpdate update = new Network.FullEntityUpdate();
            update.serverTime = getServerTime();
            update.entities = world.getNetworkedEntities();
            for(Connection connection :server.getConnections()){
                update.lastProcessedInputID = getLastInputIDProcessed(connection);
                if(ConVars.getBool("cl_udp")){
                    sendUDP(connection,update);
                }else{
                    sendTCP(connection, update);
                }
            }
        }else if(ConVars.getInt("sv_update_type")==1){
            Network.EntityPositionUpdate update = new Network.EntityPositionUpdate();
            update.serverTime = getServerTime();
            update.changedEntityPositions = world.getChangedEntityPositions();
            for(Connection connection :server.getConnections()){
                update.lastProcessedInputID = getLastInputIDProcessed(connection);
                if(ConVars.getBool("cl_udp")){
                    sendUDP(connection, update);
                }else{
                    sendTCP(connection, update);
                }
            }
        }else if(ConVars.getInt("sv_update_type")==2){
            Network.EntityComponentsUpdate update = new Network.EntityComponentsUpdate();
            update.serverTime = getServerTime();
            update.changedEntityComponents = world.getChangedEntityComponents();
            for(Connection connection :connections) {
                update.lastProcessedInputID = getLastInputIDProcessed(connection);
                if(ConVars.getBool("cl_udp")){
                    sendUDP(connection,update);
                }else{
                    sendTCP(connection,update);
                }
            }
        }
    }

    private int getLastInputIDProcessed(Connection connection){
        if(lastInputIDProcessed.get(connection) != null){
            return lastInputIDProcessed.get(connection);
        }else{
            return -1;
        }
    }

    private float getServerTime(){
        return ((System.nanoTime()-serverStartTime)/1000000000f);
    }

    private void processPendingEntityAddsAndRemovals(){
        for (int i = 0; i < pendingRandomNpcAdds; i++) {
            world.addRandomNPC();
        }
        pendingRandomNpcAdds = 0;

        if(set200Entities){
            while(world.entities.size()<200){
                world.addRandomNPC();
            }
            set200Entities = false;
        }

        for (int i = 0; i < pendingRandomNpcRemovals; i++) {
            world.removeRandomNPC();
        }
        pendingRandomNpcRemovals = 0;
    }
/*
    private void startSimulation(){
        Thread thread = new Thread(new Runnable(){
            @Override
            public void run() {

                long targetTickDurationNano, tickStartTime = 0,tickEndTime = 0,tickActualDuration = 0, tickWorkDuration, sleepDuration;
                while(true){
                    targetTickDurationNano= (long) (1000000000/ConVars.getInt("sv_tickrate"));
                    if(tickEndTime>0){
                        tickActualDuration=tickEndTime-tickStartTime;
                    }
                    tickStartTime=System.nanoTime();
                    currentTick++;
                    float delta = tickActualDuration/1000000000f;

                    doLogic(delta);

                    tickWorkDuration=System.nanoTime()-tickStartTime;

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
        },"WorldSimulation");
        thread.start();
    }*/

    @Override
    public void render() {
        long frameStart = System.nanoTime();
        float delta = Gdx.graphics.getDeltaTime();
        deltaLog.add(delta*1000);
        if(delta>0.25){
            delta=0.25f;
        }
        ackumulator += delta;
        while (ackumulator>=TIMESTEP){
            ackumulator-=TIMESTEP;
            doLogic(TIMESTEP);
        }
        if(System.nanoTime()- logIntervalStarted > Tools.secondsToNano(ConVars.getInt("cl_log_interval_seconds"))){
            logIntervalStarted = System.nanoTime();
            logIntervalElapsed();
        }
        serverStatusFrame.update();
        moveViewport(delta);
        shapeRenderer.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        long renderStart = System.nanoTime();
        Gdx.gl.glClearColor(0.5f, 0.5f, 0.5f, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        world.render(delta, shapeRenderer, batch, camera, hudCamera);

        if(showGraphs) {
            shapeRenderer.setProjectionMatrix(hudCamera.combined);
            batch.setProjectionMatrix(hudCamera.combined);
            SharedMethods.drawLog("Fps", "fps", fpsLog, shapeRenderer, batch, font, 50, 100, 150, 100, 1, 60, 20);
            SharedMethods.drawLog("Delta", "ms", deltaLog, shapeRenderer, batch, font, 250, 100, 150, 100, 4, (1000 / 60f), (1000 / 20f));
            SharedMethods.drawLog("Logic", "ms", logicLog, shapeRenderer,batch,font, 450, 100, 150, 100, 20, 3,10);
            SharedMethods.drawLog("Render", "ms", renderLog, shapeRenderer,batch,font, 650, 100, 150, 100, 20, 5,(1000/60f));
            SharedMethods.drawLog("FrameTime", "ms", frameTimeLog, shapeRenderer, batch, font, 850, 100, 150, 100, 20, 5, (1000 / 60f));
            SharedMethods.drawLog("Download", "kB/s", serverData.kiloBytesPerSecondReceivedLog, shapeRenderer,batch,font, 1050, 100, 150, 100, 25, 2,10);
            SharedMethods.drawLog("Upload", "kB/s", serverData.kiloBytesPerSecondSentLog, shapeRenderer,batch,font, 1250, 100, 150, 100, 2, 10,100);
        }

        renderLog.add(Tools.nanoToMilliFloat(System.nanoTime() - renderStart));
        frameTimeLog.add(Tools.nanoToMilliFloat(System.nanoTime() - frameStart));
    }

    public void fillEveryonesAmmo(){
        for(int playerID:playerList.keySet()){
            fillAmmo(playerID);
        }
    }

    public void giveEveryoneAllWeapons(){
        for(int playerID:playerList.keySet()){
            giveAllWeapons(playerID);
        }
    }

    public void fillAmmo(int id){
        for(String ammoType: G.ammoList){
            addAmmo(id, ammoType, 999999);
        }
    }

    public void giveAllWeapons(int id){
        for(int weaponID : G.weaponList.keySet()){
            giveWeapon(weaponID, id);
        }
    }

    public void giveWeapon(int weaponID, int id){
        PlayerServerEntity player = (PlayerServerEntity) world.entities.get(id);
        player.setWeapon(weaponID, true);
        Network.WeaponUpdate weaponUpdate = new Network.WeaponUpdate();
        weaponUpdate.weapon = weaponID;
        sendTCP(playerList.get(id), weaponUpdate);
    }

    public void addAmmo(int id, String ammoType, int amount){
        PlayerServerEntity player = (PlayerServerEntity) world.entities.get(id);
        player.changeAmmo(ammoType, amount);
        Network.AmmoUpdate ammoUpdate = new Network.AmmoUpdate();
        ammoUpdate.ammoType = ammoType;
        ammoUpdate.amount = amount;
        sendTCP(playerList.get(id), ammoUpdate);
    }

    public void startWaves(){
        world.spawnWaves = true;
    }

    public void stopWaves(){
        world.spawnWaves = false;
    }

    public void resetWaves(){
        world.removeAllNpcs();
        world.spawnWaves = false;
        world.setWave(0);
    }

    private void updateStatusData(){
        for(Connection c:inputQueue.keySet()){
            connectionStatus.get(c).inputQueue = inputQueue.get(c).size();
        }
        serverData.fps = Gdx.graphics.getFramesPerSecond();
        fpsLog.add((float) serverData.fps);
        serverData.setEntityCount(world.getEntityCount());
        serverData.currentTick = currentTick;
        serverData.setServerTime((System.nanoTime() - serverStartTime) / 1000000000f);
    }

    public void addPlayerKill(int id){
        score.addPlayerKill(id);
        Network.AddPlayerKill addPlayerKill = new Network.AddPlayerKill();
        addPlayerKill.id = id;
        sendTCPtoAll(addPlayerKill);
    }
    public void addNpcKill(int id){
        score.addNpcKill(id);
        Network.AddNpcKill addNpcKill = new Network.AddNpcKill();
        addNpcKill.id = id;
        sendTCPtoAll(addNpcKill);
    }
    public void addDeath(int id){
        score.addDeath(id);
        Network.AddDeath addDeath = new Network.AddDeath();
        addDeath.id = id;
        sendTCPtoAll(addDeath);
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
        showMessage("Received file: "+fileName);
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

    public void showMessage(String message){
        if(message.startsWith("\n")){
            System.out.println();
            consoleFrame.addLine("");
            message = message.substring(1);
        }
        String line = "["+Tools.secondsToMilliTimestamp(getServerTime())+ "] " + message;
        System.out.println(line);
        consoleFrame.addLine(line);
    }

    private int measureKryoEntitySize(){

        Kryo kryo = new Kryo();
        Network.register(kryo);
        //kryo.register(NetworkEntity.class);
        Output output = new Output(objectBuffer);
        kryo.writeObject(output, new NetworkEntity(-1, 1000000, 1000000, -1));
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

    private HashMap<Integer,ArrayList<Component>> createFullComponentList(int entityCount) {
        HashMap<Integer, ArrayList<Component>> changedComponents = new HashMap<>();
        for (int i = 0; i < entityCount; i++) {
            ArrayList<Component> components = new ArrayList<>();
            components.add(new Component.Position(50, 50));
            components.add(new Component.Health(100));
            components.add(new Component.MaxHealth(150));
            components.add(new Component.Rotation(100));
            components.add(new Component.Team(2));
            components.add(new Component.Slot1(4));
            components.add(new Component.Slot2(8));
            changedComponents.put(i, components);
        }
        return changedComponents;
    }

    private HashMap<Integer,ArrayList<Component>> createComponentList(int entityCount, boolean pos, boolean rotation, boolean health){
        HashMap<Integer,ArrayList<Component>> changedComponents = new HashMap<>();
        for (int i = 0; i < entityCount; i++) {
            ArrayList<Component> components = new ArrayList<>();
            if(pos)components.add(new Component.Position(50,50));
            if(rotation)components.add(new Component.Rotation(100));
            if(health)components.add(new Component.Health(100));
            changedComponents.put(i, components);
        }
        return changedComponents;
    }

    private int measureObject(Object o){
        KryoSerialization s = (KryoSerialization) server.getSerialization();
        ByteBuffer measureBuffer = ByteBuffer.allocate(objectBuffer);
        s.write(new Client(), measureBuffer, o);
        int size = measureBuffer.position();
        System.out.println(measureBuffer);
        for (int i = 0; i < size; i++) {
            System.out.println(measureBuffer.array()[i]);
        }
        //System.out.println("last:"+measureBuffer.array()[size]);
        //System.out.println("last+:"+measureBuffer.array()[size+1]);
        //System.out.println("last++:"+measureBuffer.array()[size+2]);
        measureBuffer.clear();
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



    public void updateFakePing(Connection connection) {
        StatusData status = connectionStatus.get(connection);
        if(status != null){
            Network.FakePing ping = new Network.FakePing();
            ping.id = status.lastPingID++;
            status.lastPingSendTime = System.currentTimeMillis();
            sendTCP(connection,ping);
        }
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

    @Override
    public boolean keyTyped(char character) {
        if (character == 'u') {
            boolean useUDP = ConVars.getBool("cl_udp");
            if(useUDP){
                ConVars.set("cl_udp",0);
            }else{
                ConVars.set("cl_udp",1);
            }

            showMessage("UseUDP:" + ConVars.getBool("cl_udp"));
        }
        if (character == 'q') {
            pendingRandomNpcRemovals++;
        }
        if (character == 'p') {
            world.spawnHealthPack(30,30);
        }
        if (character == 'w') {
            pendingRandomNpcAdds++;
        }
        if (character == 't') {
            pendingRandomNpcAdds += 10;
        }
        if (character == 'y'){
            set200Entities = true;
        }
        if (character == 'm'){
            G.debugFeatureToggle = !G.debugFeatureToggle;
            showMessage("DebugFeatureToggle:"+ G.debugFeatureToggle);
        }
        if (character == 'l') {
            showMessage("Sending full update to all clients");
            for(Connection c : server.getConnections()){
                sendFullStateUpdate(c);
            }
        }
        if (character == 'c') {
            Network.GameClockCompare gameClockCompare = new Network.GameClockCompare();
            gameClockCompare.serverTime = getServerTime();
            showMessage("Sending gameClockCompare: "+gameClockCompare.serverTime);
            sendTCPtoAll(gameClockCompare);
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
        if (character == 'y') {
            /*
            showMessage("Printing list of stuck npcs");
            for(int id:world.entityAIs.keySet()){
                EntityAI ai = world.entityAIs.get(id);
                if(ai.destinationTimer>10){
                    showMessage("ID:" + id);
                    showMessage("DestinationX:" + ai.destinationX);
                    showMessage("DestinationY:" + ai.destinationY);
                    showMessage("CenterX:" + ai.entity.getCenterX());
                    showMessage("CenterY:" + ai.entity.getCenterY());
                    showMessage("Last target location:" + ai.targetLocation);
                }
            }
            */
            showMessage("Listing entities");
            for(ServerEntity e:world.entities.values()){
                showMessage(e.toString());
            }
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
            if(ConVars.getInt("sv_tickrate") <=1){
                int tickRate = ConVars.getInt("sv_tickrate") / 2;
                ConVars.set("sv_tickrate",tickRate);
            }else{
                int tickRate = ConVars.getInt("sv_tickrate") - 1;
                ConVars.set("sv_tickrate",tickRate);
            }
        }
        if (character == '4') {
            if(ConVars.getInt("sv_tickrate") <1){
                int tickRate = ConVars.getInt("sv_tickrate") * 2;
                ConVars.set("sv_tickrate",tickRate);
                if(ConVars.getInt("sv_tickrate") > 1){
                    ConVars.set("sv_tickrate",1);
                }
            }else{
                int tickRate = ConVars.getInt("sv_tickrate") + 1;
                ConVars.set("sv_tickrate",tickRate);
            }
        }
        if (character == '8') {
            if(ConVars.getInt("sv_updaterate") <=1){
                int updateRate = ConVars.getInt("sv_updaterate")/2;
                ConVars.set("sv_updaterate",updateRate);
            }else{
                int updateRate = ConVars.getInt("sv_updaterate")-1;
                ConVars.set("sv_updaterate",updateRate);
            }

        }
        if (character == '9') {
            if(ConVars.getInt("sv_updaterate") <1){
                int updateRate = ConVars.getInt("sv_updaterate")*2;
                ConVars.set("sv_updaterate",updateRate);
                if(ConVars.getInt("sv_updaterate") > 1){
                    ConVars.set("sv_updaterate",1);
                }
            }else{
                int updateRate = ConVars.getInt("sv_updaterate")+1;
                ConVars.set("sv_updaterate",updateRate);
            }
        }
        return false;
    }

    @Override
    public void resize(int w, int h) {
        windowWidth = w;
        windowHeight = h;
        if(h > ((float)w/ G.mapWidth)* G.mapHeight){
            camera.viewportWidth = G.mapWidth;
            camera.viewportHeight = h*((float) G.mapWidth /w);
        }else{
            camera.viewportWidth = w*((float) G.mapHeight /h);
            camera.viewportHeight = G.mapHeight;
        }
        viewPortX = G.mapWidth /2f;
        viewPortY = G.mapHeight /2f;
        camera.position.set(viewPortX, viewPortY, 0);
        camera.update();

        hudCamera.viewportWidth = w;
        hudCamera.viewportHeight = h;
        hudCamera.position.set(windowWidth/2,windowHeight / 2,0);
        hudCamera.update();

        world.cachedMap = null;
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
        if(keycode == Input.Keys.F2){
            showGraphs = !showGraphs;
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
    public void conVarChanged(String varName, String varValue) {
        if(varName.equals("sv_map")){
            if(world!=null){
                Gdx.app.postRunnable(() -> world.changeMap(varValue));
            }
        }
    }

    @Override
    public void scoreChanged() {

    }

    @Override
    public void playerAdded(Connection connection, Network.AssignEntity assign) {
        showMessage("Added player ID: " + assign.networkID);
        playerList.put(assign.networkID,connection);

        serverData.addPlayer();
        score.addPlayer(assign.networkID);
        Network.AddPlayer addPlayer = new Network.AddPlayer();
        addPlayer.networkID = assign.networkID;
        sendTCPtoAllExcept(connection, addPlayer);

        connection.sendTCP(assign);
    }

    @Override
    public void playerRemoved(int id){
        showMessage("Removed player ID: " + id);
        playerList.remove(id);
        serverData.removePlayer();
        score.removePlayer(id);
    }

    @Override
    public void entityRemoved(int id){
        showMessage("Removed entity ID: " + id);
        Network.RemoveEntity removeEntity = new Network.RemoveEntity();
        removeEntity.networkID=id;
        removeEntity.serverTime=getServerTime();
        sendTCPtoAll(removeEntity);
    }

    @Override
    public void entityAdded(NetworkEntity e){
        showMessage("Added entity ID: " + e.id);
        if(server!=null){
            Network.AddEntity addEntity = new Network.AddEntity();
            addEntity.entity=e;
            addEntity.serverTime=getServerTime();
            sendTCPtoAll(addEntity);
        }
    }

    @Override
    public void playerDied(int id, int lives){
        showMessage("Player("+id+") lives is now: "+lives);
        Network.PlayerDied playerDied = new Network.PlayerDied();
        playerDied.id = id;
        sendTCPtoAll(playerDied);
    }

    @Override
    public void playerRespawned(int id, float x, float y) {
        showMessage("Player("+id+") respawned at ("+x+","+y+")");
        Network.RespawnPlayer respawnPlayer = new Network.RespawnPlayer();
        respawnPlayer.id = id;
        respawnPlayer.x = x;
        respawnPlayer.y = y;
        sendTCPtoAll(respawnPlayer);
    }

    @Override
    public void powerupAdded(int id, Powerup powerup) {
        showMessage("Powerup(" + powerup + ") added at (" + powerup.bounds.x + "," + powerup.bounds.y + ") ID:" + id);
        if(server!=null){
            Network.AddPowerup addPowerup = new Network.AddPowerup();
            addPowerup.networkID = id;
            addPowerup.powerup = powerup;
            sendTCPtoAll(addPowerup);
        }
    }

    @Override
    public void powerupRemoved(int id) {
        showMessage("Powerup removed: "+id);
        Network.RemovePowerup removePowerup = new Network.RemovePowerup();
        removePowerup.networkID = id;
        sendTCPtoAll(removePowerup);
    }

    @Override
    public void waveChanged(int wave){
        showMessage("Current wave: "+wave);
        Network.WaveChanged waveChanged = new Network.WaveChanged();
        waveChanged.wave = wave;
        sendTCPtoAll(waveChanged);
    }

    @Override
    public void attackCreated(Network.EntityAttacking entityAttacking, Weapon weapon) {
        if(playerList.keySet().contains(entityAttacking.id)&&(weapon.projectile.type != ProjectileDefinition.PROJECTILE)){
            sendTCPtoAllExcept(playerList.get(entityAttacking.id),entityAttacking);
        }else{
            sendTCPtoAll(entityAttacking);
        }
    }

    @Override
    public void mapChanged(String mapName) {
        if(server!=null){
            Network.MapChange mapChange = new Network.MapChange();
            mapChange.mapName=mapName;
            //mapChange.powerups = new HashMap<>(world.powerups);
            sendTCPtoAll(mapChange);
            resize(windowWidth, windowHeight);
        }
    }

    @Override
    public void mapCleared(float timer) {
        Network.MapCleared mapCleared = new Network.MapCleared();
        mapCleared.timer = timer;
        sendTCPtoAll(mapCleared);
    }

    @Override
    public void playerAmmoChanged(int id, String ammoType, int value) {
        Network.AmmoUpdate ammoUpdate = new Network.AmmoUpdate();
        ammoUpdate.ammoType = ammoType;
        ammoUpdate.amount = value;
        sendTCP(playerList.get(id), ammoUpdate);
    }

    @Override
    public void playerWeaponChanged(int id, int weapon, boolean state) {
        Network.WeaponUpdate weaponUpdate = new Network.WeaponUpdate();
        weaponUpdate.weapon = weapon;
        weaponUpdate.state = state;
        sendTCP(playerList.get(id), weaponUpdate);
    }

    @Override
    public void networkedProjectileSpawned(String projectileName, float x, float y, int ownerID, int team) {
        Network.SpawnProjectile spawnProjectile = new Network.SpawnProjectile();
        spawnProjectile.projectileName = projectileName;
        spawnProjectile.x = x;
        spawnProjectile.y = y;
        spawnProjectile.ownerID = ownerID;
        spawnProjectile.team = team;
        sendTCPtoAll(spawnProjectile);
    }

    @Override
    public void playerCashChanged(int id, int cash) {
        Network.CashUpdate cashUpdate = new Network.CashUpdate();
        cashUpdate.amount = cash;
        sendTCP(playerList.get(id), cashUpdate);
    }

    @Override
    public void entityKilled(int victimID, int killerID){
        showMessage("Entity " + victimID + " killed by "+killerID+".");
        if(playerList.keySet().contains(killerID)) {
            if (playerList.keySet().contains(victimID)) {
                addPlayerKill(killerID);
            } else {
                addNpcKill(killerID);
            }
        }
        if(playerList.keySet().contains(victimID)){
            addDeath(victimID);
        }
    }

    @Override
    public void log(String message) {
        showMessage(message);
    }

    private class Packet{
        Connection connection;
        Object content;

        public Packet(Connection connection, Object content) {
            this.connection = connection;
            this.content = content;
        }
    }

    private class Disconnect{};
}
