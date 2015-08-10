package com.juniperbrew.minimus.server;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.GlobalVars;
import com.juniperbrew.minimus.NetworkEntity;
import com.juniperbrew.minimus.Enums;
import com.juniperbrew.minimus.ExceptionLogger;
import com.juniperbrew.minimus.Network;
import com.juniperbrew.minimus.Powerup;
import com.juniperbrew.minimus.Score;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.components.Heading;
import com.juniperbrew.minimus.components.Health;
import com.juniperbrew.minimus.components.Position;
import com.juniperbrew.minimus.windows.ConsoleFrame;
import com.juniperbrew.minimus.windows.ServerStatusFrame;
import com.juniperbrew.minimus.windows.StatusData;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class MinimusServer implements ApplicationListener, InputProcessor, Score.ScoreChangeListener, ConVars.ConVarChangeListener, World.WorldChangeListener {

    Server server;
    ShapeRenderer shapeRenderer;
    SpriteBatch batch;
    HashMap<Connection,Integer> lastInputIDProcessed = new HashMap<>();
    HashMap<Connection,Integer> connectionUpdateRate = new HashMap<>();
    HashMap<Connection, ArrayList<Network.UserInput>> inputQueue = new HashMap<>();
    HashMap<Connection, StatusData> connectionStatus = new HashMap<>();
    long lastUpdateSent;

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

    float viewPortX;
    float viewPortY;
    float cameraVelocity = 300f; //pix/s

    private OrthographicCamera camera;
    EnumSet<Enums.Buttons> buttons = EnumSet.noneOf(Enums.Buttons.class);

    int pendingRandomNpcAdds = 0;
    int pendingFollowingNpcAdds = 0;
    int pendingRandomNpcRemovals = 0;

    private ConcurrentLinkedQueue<Packet> pendingPackets = new ConcurrentLinkedQueue<>();

    Score score = new Score(this);

    World world;

    @Override
    public void create() {
        ConVars.addListener(this);
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionLogger("server"));
        consoleFrame = new ConsoleFrame(this);
        shapeRenderer = new ShapeRenderer();
        batch = new SpriteBatch();
        world = new World(this, new TmxMapLoader().load(GlobalVars.mapFolder+File.separator+ConVars.get("sv_map_name")+File.separator+ConVars.get("sv_map_name")+".tmx"));

        int h = Gdx.graphics.getHeight();
        int w = Gdx.graphics.getWidth();
        resize(h,w);

        serverStartTime = System.nanoTime();
        serverData = new StatusData(serverStartTime,ConVars.getInt("cl_log_interval_seconds"));
        serverStatusFrame = new ServerStatusFrame(serverData);
        startServer();
        startSimulation();
        Gdx.input.setInputProcessor(this);

        serverData.entitySize = measureObject(new NetworkEntity(-1, 1000000, 1000000,-1));
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
                connectionStatus.put(connection, dataUsage);
                serverStatusFrame.addConnection(connection.toString(),dataUsage);
                Network.FullEntityUpdate fullUpdate = new Network.FullEntityUpdate();
                fullUpdate.entities = world.getNetworkedEntityList();
                fullUpdate.serverTime = getServerTime();
                connection.sendTCP(fullUpdate);
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
                ArrayList<Network.UserInput> list = new ArrayList<Network.UserInput>();
                list.addAll(inputPacket.inputs);
                inputQueue.put(connection, list);
            }
        }else if (object instanceof Network.SpawnRequest){
            world.addPlayer(connection);
        }else if (object instanceof Network.TeamChangeRequest){
            Network.TeamChangeRequest teamChangeRequest = (Network.TeamChangeRequest) object;
            world.getEntity(playerList.getKey(connection)).setTeam(teamChangeRequest.team);
        }else if(object instanceof Disconnect){
            connectionStatus.get(connection).disconnected();
            if(playerList.containsValue(connection)){
                world.removePlayer(playerList.getKey(connection));
            }
        }else if(object instanceof Network.GameClockCompare){
            Network.GameClockCompare gameClockCompare = (Network.GameClockCompare) object;
            showMessage("Received gameClockCompare("+playerList.getKey(connection)+"): "+gameClockCompare.serverTime + " Delta: "+(gameClockCompare.serverTime-getServerTime()));
        }
    }

    private void doLogic(float delta){

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
    }

    private void updatePing(){
        if(System.nanoTime()-lastPingUpdate>Tools.secondsToNano(ConVars.getDouble("cl_ping_update_delay"))){
            for(Connection c:server.getConnections()){
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
                world.processInput(id, input);
                lastInputIDProcessed.put(connection,input.inputID);
            }
            inputList.clear();
        }
    }

    private void updateClients(){
        if(ConVars.getInt("sv_update_type")==0){
            Network.FullEntityUpdate update = new Network.FullEntityUpdate();
            update.serverTime = getServerTime();
            update.entities = world.getNetworkedEntityList();
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
            for(Connection connection :server.getConnections()) {
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
        for (int i = 0; i < pendingFollowingNpcAdds; i++) {
            world.addNPC(EntityAI.FOLLOWING,1);
        }
        pendingFollowingNpcAdds = 0;
        for (int i = 0; i < pendingRandomNpcRemovals; i++) {
            world.removeRandomNPC();
        }
        pendingRandomNpcRemovals = 0;
    }

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
        });
        thread.start();
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        if(System.nanoTime()- logIntervalStarted > Tools.secondsToNano(ConVars.getInt("cl_log_interval_seconds"))){
            logIntervalStarted = System.nanoTime();
            logIntervalElapsed();
        }
        serverStatusFrame.update();
        moveViewport(delta);
        shapeRenderer.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glClearColor(0.5f, 0.5f, 0.5f, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        world.render(delta, shapeRenderer,batch);

    }

    public void startWaves(){
        world.spawnWaves = true;
    }

    public void resetWaves(){
        world.removeAllEntities();
        world.spawnWaves = false;
        world.setWave(0);
    }

    private void updateStatusData(){
        for(Connection c:inputQueue.keySet()){
            connectionStatus.get(c).inputQueue = inputQueue.get(c).size();
        }
        serverData.fps = Gdx.graphics.getFramesPerSecond();
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

    public void showMessage(String message){
        String line = "["+Tools.secondsToMilliTimestamp(getServerTime())+ "] " + message;
        System.out.println(line);
        consoleFrame.addLine(line);
    }

    private int measureKryoEntitySize(){

        Kryo kryo = new Kryo();
        kryo.register(NetworkEntity.class);
        Output output = new Output(objectBuffer);
        kryo.writeObject(output, new NetworkEntity(-1, 1000000, 1000000,-1));
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
            world.spawnPowerup(Powerup.HEALTH,30,30);
        }
        if (character == 'w') {
            pendingRandomNpcAdds++;
        }
        if (character == 't') {
            pendingFollowingNpcAdds += 10;
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
        int mapWidth = world.getMapWidth();
        int mapHeight = world.getMapHeight();
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
    public void conVarChanged(String varName, String varValue) {

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
        sendTCPtoAllExcept(connection,addPlayer);

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
        Network.AddEntity addEntity = new Network.AddEntity();
        addEntity.entity=e;
        addEntity.serverTime=getServerTime();
        sendTCPtoAll(addEntity);
    }

    @Override
    public void playerLivesChanged(int id, int lives){
        showMessage("Player("+id+") lives is now: "+lives);
        Network.SetLives setLives = new Network.SetLives();
        setLives.lives = lives;
        sendTCP(playerList.get(id), setLives);
    }

    @Override
    public void powerupAdded(int id, Powerup powerup) {
        showMessage("Powerup("+powerup.type+") added: "+id);
        Network.AddPowerup addPowerup = new Network.AddPowerup();
        addPowerup.networkID = id;
        addPowerup.powerup = powerup;
        sendTCPtoAll(addPowerup);
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
    public void attackCreated(Network.EntityAttacking entityAttacking) {
        if(playerList.keySet().contains(entityAttacking.id)){
            sendTCPtoAllExcept(playerList.get(entityAttacking.id),entityAttacking);
        }else{
            sendTCPtoAll(entityAttacking);
        }
    }

    @Override
    public void message(String message) {
        showMessage(message);
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

    private class Packet{
        Connection connection;
        Object content;

        public Packet(Connection connection, Object content) {
            this.connection = connection;
            this.content = content;
        }
    }

    private class Disconnect{}
}
