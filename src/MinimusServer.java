import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import java.awt.geom.Line2D;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class MinimusServer implements ApplicationListener, InputProcessor, EntityChangeListener {

    Server server;
    ShapeRenderer shapeRenderer;
    HashMap<Integer,ServerEntity> entities;
    HashMap<Integer,EntityAI> entityAIs;
    HashMap<Connection,Integer> playerList;
    HashMap<Connection,Integer> lastInputIDProcessed;
    HashMap<Connection,Integer> connectionUpdateRate;
    HashMap<Connection, ArrayList<Network.UserInput>> inputQueue;
    HashMap<Connection, StatusData> connectionStatus;

    ArrayList<Integer> movedEntities;

    private int networkIDCounter = 1;

    private int currentTick=0;

    long serverStartTime;


    int screenW;
    int screenH;

    private int writeBuffer = 16384; //Default 16384
    private int objectBuffer = 4096; //Default 2048
    private long lastPingUpdate;

    HashMap<Connection,Long> lastAttackDone;
    private ArrayList<Line2D.Float> attackVisuals;
    final long ATTACK_DELAY = (long) (0.3*1000000000);
    Timer timer;

    ByteBuffer buffer = ByteBuffer.allocate(objectBuffer);
    long logIntervalStarted;

    ServerStatusFrame serverStatusFrame;
    ConsoleFrame consoleFrame;
    StatusData statusData;

    ConVars conVars;

    final int MAP_WIDTH = 1000;
    final int MAP_HEIGHT = 1000;

    float viewPortX;
    float viewPortY;
    float cameraVelocity = 300f; //pix/s

    private OrthographicCamera camera;
    EnumSet<Enums.Buttons> buttons = EnumSet.noneOf(Enums.Buttons.class);

    int pendingEntityAdds = 0;
    int pendingEntityRemovals = 0;

    int movingEntityCount;

    private void initialize(){
        shapeRenderer = new ShapeRenderer();
        entities = new HashMap<Integer, ServerEntity>();
        playerList = new HashMap<Connection, Integer>();
        connectionUpdateRate = new HashMap<Connection, Integer>();
        connectionStatus = new HashMap<Connection, StatusData>();
        inputQueue = new HashMap<Connection, ArrayList<Network.UserInput>>();
        lastInputIDProcessed = new HashMap<Connection, Integer>();
        lastAttackDone = new HashMap<Connection, Long>();
        attackVisuals = new ArrayList<Line2D.Float>();
        movedEntities = new ArrayList<>();
        entityAIs = new HashMap<>();
        timer = new Timer();
        screenW = Gdx.graphics.getWidth();
        screenH = Gdx.graphics.getHeight();

        for (int i = 0; i < 1; i++) {
            addNPC();
        }
    }

    @Override
    public void create() {

        int h = Gdx.graphics.getHeight();
        int w = Gdx.graphics.getWidth();
        camera = new OrthographicCamera(h, w);
        viewPortX = MAP_WIDTH/2f;
        viewPortY = MAP_HEIGHT/2f;
        camera.position.set(viewPortX, viewPortY, 0);

        conVars = new ConVars();
        consoleFrame = new ConsoleFrame(conVars,this);
        serverStartTime = System.nanoTime();
        statusData = new StatusData(null,serverStartTime,conVars.getInt("cl_log_interval_seconds"));
        serverStatusFrame = new ServerStatusFrame(statusData);
        startServer();
        initialize();
        startSimulation();
        Gdx.input.setInputProcessor(this);

        statusData.entitySize = measureKryoEntitySize();
        showMessage("Kryo entity size:" + statusData.entitySize + "bytes");
    }

    private void showMessage(String message){
        System.out.println(message);
        consoleFrame.addLine(message);
    }

    private void showHelp(){
        try(BufferedReader reader = new BufferedReader(new FileReader("serverHelp.txt"))){
            String line;
            while((line = reader.readLine())!=null){
                showMessage(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int measureKryoEntitySize(){

        Kryo kryo = new Kryo();
        kryo.register(Entity.class);
        Output output = new Output(objectBuffer);
        kryo.writeObject(output, new Entity(-1, 1000000, 1000000));
        int size = output.position();
        output.close();
        return size;
    }

    private void logReceivedPackets(Connection connection, Object packet){
        KryoSerialization s = (KryoSerialization) server.getSerialization();
        s.write(connection, buffer ,packet);
        connectionStatus.get(connection).addBytesReceived(buffer.position());
        statusData.addBytesReceived(buffer.position());
        buffer.clear();
    }

    private void logIntervalElapsed(){
        statusData.intervalElapsed();
        for(StatusData dataUsage : connectionStatus.values()){
            dataUsage.intervalElapsed();
        }
    }

    public void sendCommand(String command){
        for(Connection connection :playerList.keySet()){
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
            entities.put(id,serverEntities.get(id).getEntity());
        }
        return entities;
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

        server.addListener(new Listener(){
            public void connected(Connection connection){
                int networkID = getNextNetworkID();
                ServerEntity newPlayer = new ServerEntity(networkID,100,100,MinimusServer.this);
                entities.put(networkID, newPlayer);
                playerList.put(connection,networkID);
                StatusData dataUsage = new StatusData(connection, System.nanoTime(),conVars.getInt("cl_log_interval_seconds"));
                connectionStatus.put(connection, dataUsage);
                serverStatusFrame.addConnection(connection.toString(),dataUsage);

                Network.AssignEntity assign = new Network.AssignEntity();
                assign.networkID = networkID;
                assign.velocity = conVars.get("sv_velocity");
                assign.mapHeight = MAP_HEIGHT;
                assign.mapWidth = MAP_WIDTH;
                connection.sendTCP(assign);

                Network.FullEntityUpdate fullUpdate = new Network.FullEntityUpdate();
                fullUpdate.entities = getNetworkedEntityList(entities);
                connection.sendTCP(fullUpdate);

            }

            @Override
            public void disconnected(Connection connection){
                entities.remove(playerList.get(connection));
                playerList.remove(connection);
                connectionStatus.get(connection).disconnected();
            }

            public void received (Connection connection, Object object) {
                logReceivedPackets(connection,object);
                if(object instanceof Network.Message){
                    //Handle messages
                }
                if(object instanceof Network.UserInputs){
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
        });
    }

    private float getServerTime(){
        return ((System.nanoTime()-serverStartTime)/1000000000f);
    }

    private void processCommands(){
        for(Connection connection:inputQueue.keySet()){
            ArrayList<Network.UserInput> inputList = inputQueue.get(connection);
            ServerEntity e = entities.get(playerList.get(connection));
            Network.UserInput[] inputListCopy = inputList.toArray(new Network.UserInput[inputList.size()]);

            for(Network.UserInput input : inputListCopy){
                //System.out.println("Processing input with ID:"+input.inputID+" duration:"+input.msec);
                movePlayer(e, input);
                if(input.buttons.contains(Enums.Buttons.SPACE)){
                    attackWithEntity(connection);
                }
                lastInputIDProcessed.put(connection,input.inputID);
                inputList.remove(input); //FIXME we are removing this element from the original arraylist
            }
        }
    }

    private void attackWithEntity(Connection connection){
        if(lastAttackDone.get(connection)==null){
            lastAttackDone.put(connection,System.nanoTime());
        }else if(System.nanoTime()-lastAttackDone.get(connection)<ATTACK_DELAY){ //1 second attack delay
            return;
        }else{
            lastAttackDone.put(connection,System.nanoTime());
            showMessage("Entity ID" + playerList.get(connection) + " is attacking");
            ServerEntity e = entities.get(playerList.get(connection));
            float originX = e.getX()+e.width/2;
            float originY = e.getY()+e.height/2;
            float targetX = e.getX()+e.width/2;
            float targetY = e.getY()+e.height/2;
            switch (e.heading){
                case NORTH: targetY += 200; break;
                case SOUTH: targetY -= 200; break;
                case EAST: targetX += 200; break;
                case WEST: targetX -= 200; break;
            }
            final Line2D.Float hitScan = new Line2D.Float(originX,originY,targetX,targetY);
            ArrayList<Integer> deadEntities = new ArrayList<Integer>();
            for(int id:entities.keySet()){
                ServerEntity target = entities.get(id);
                if(target.getBounds().intersectsLine(hitScan) && target != e){
                    target.reduceHealth(10);
                    showMessage("Entity ID" + id + " now has " + target.getHealth() + " health.");
                    if(target.getHealth()<=0){
                        showMessage("Entity ID" + id + " is dead.");
                        deadEntities.add(id);
                    }
                }
            }
            for(int id: deadEntities){
                if(playerList.values().contains(id)){
                    //Respawn dead player
                    ServerEntity deadPlayer = entities.get(id);
                    deadPlayer.restoreMaxHealth();
                    deadPlayer.moveTo(0, 0);
                }else{
                    entities.remove(id);
                    //npcs.remove(id);
                }
            }
            attackVisuals.add(hitScan);
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    attackVisuals.remove(hitScan);
                }
            };
            timer.schedule(task,1000);
        }
    }

    private HashMap<Integer, Network.Position> getChangedEntityPositions(){
        HashMap<Integer, Network.Position> positions = new HashMap<>();
        for(int id: movedEntities){
            ServerEntity e = entities.get(id);
            positions.put(id,new Network.Position(e.getX(),e.getY()));
        }
        return positions;
    }

    private void updateClients(){
        if(conVars.getBool("sv_send_full_updates")){
            Network.FullEntityUpdate update = new Network.FullEntityUpdate();
            update.serverTime = getServerTime();
            update.entities = getNetworkedEntityList(entities);
            for(Connection connection :playerList.keySet()){
                if(lastInputIDProcessed.get(connection) != null){
                    update.lastProcessedInputID = lastInputIDProcessed.get(connection);
                }else{
                    update.lastProcessedInputID = -1;
                }
                if(conVars.getBool("cl_udp")){
                    int bytesSent = connection.sendUDP(update);
                    connectionStatus.get(connection).addBytesSent(bytesSent);
                    statusData.addBytesSent(bytesSent);
                }else{
                    int bytesSent = connection.sendTCP(update);
                    connectionStatus.get(connection).addBytesSent(bytesSent);
                    statusData.addBytesSent(bytesSent);
                }
            }
        }else{
            Network.EntityPositionUpdate update = new Network.EntityPositionUpdate();
            update.serverTime = getServerTime();
            update.changedEntityPositions = getChangedEntityPositions();
            for(Connection connection :playerList.keySet()){
                if(lastInputIDProcessed.get(connection) != null){
                    update.lastProcessedInputID = lastInputIDProcessed.get(connection);
                }else{
                    update.lastProcessedInputID = -1;
                }
                if(conVars.getBool("cl_udp")){
                    int bytesSent = connection.sendUDP(update);
                    connectionStatus.get(connection).addBytesSent(bytesSent);
                    statusData.addBytesSent(bytesSent);
                }else{
                    int bytesSent = connection.sendTCP(update);
                    connectionStatus.get(connection).addBytesSent(bytesSent);
                    statusData.addBytesSent(bytesSent);
                }
            }
        }

        movedEntities.clear();
    }

    private void addNPC(){
        int width = 50;
        int height = 50;
        float x = MathUtils.random(MAP_WIDTH-width);
        float y = MathUtils.random(MAP_HEIGHT-height);
        int networkID = getNextNetworkID();
        ServerEntity npc = new ServerEntity(networkID,x,y,this);
        npc.height = height;
        npc.width = width;
        npc.reduceHealth(10);
        int randomHeading = MathUtils.random(Enums.Heading.values().length - 1);
        npc.heading = Enums.Heading.values()[randomHeading];
        entities.put(networkID,npc);
        entityAIs.put(networkID,new EntityAI(npc));
        showMessage("Adding npc ID:" + networkID);

        Network.AddEntity addEntity = new Network.AddEntity();
        addEntity.networkID=networkID;
        addEntity.entity=npc.getEntity();
        server.sendToAllTCP(addEntity);
    }

    private void removeNPC(){
        Integer[] keys = entities.keySet().toArray(new Integer[entities.keySet().size()]);
        if(keys.length != 0){
            if(playerList.values().size() < entities.size()){
                int removeIndex;
                do{
                    removeIndex = MathUtils.random(keys.length-1);
                }while(playerList.values().contains(keys[removeIndex]));
                showMessage("Remove index:" + removeIndex);
                int removeID = keys[removeIndex];
                showMessage("Removing npc ID:"+removeID);
                entityAIs.remove(removeID);
                entities.remove(removeID);
            }
        }
    }

    private void moveNPC(float delta){

        for(EntityAI ai:entityAIs.values()){
            ai.move(conVars.get("sv_velocity"),delta);
        }

        if(movedEntities.size()<conVars.getInt("sv_max_moving_entities")&&entityAIs.size()>0){
            Integer[] keys = entityAIs.keySet().toArray(new Integer[entityAIs.keySet().size()]);
            int startIndex = MathUtils.random(keys.length-1);
            for (int i = 0; i < keys.length; i++) {
                int actualIndex = (startIndex+i)%keys.length;
                EntityAI ai = entityAIs.get(keys[actualIndex]);
                if(!ai.hasDestination){
                    ai.setRandomDestination(MAP_WIDTH,MAP_HEIGHT);
                    break;
                }
            }
        }

        /*
        Entity[] entitiesCopy = entities.values().toArray(new Entity[entities.values().size()]);
        for(Entity e:entitiesCopy){
            if(playerList.values().contains(e.id)){
                continue;
            }
            float x = e.x;
            float y = e.y;
            int width = e.width;
            int height = e.height;

            float newX = x;
            float newY = y;

            switch(e.heading){
                case NORTH: newY += conVars.get("sv_velocity")*delta; break;
                case SOUTH: newY -= conVars.get("sv_velocity")*delta; break;
                case WEST: newX -= conVars.get("sv_velocity")*delta; break;
                case EAST: newX += conVars.get("sv_velocity")*delta; break;
            }
            if(newX+width > MAP_WIDTH && e.heading==Enums.Heading.EAST){
                e.heading = Enums.Heading.WEST;
                newX = MAP_WIDTH-width;
            }
            if(newX<0 && e.heading== Enums.Heading.WEST){
                e.heading = Enums.Heading.EAST;
                newX = 0;
            }
            if(newY+height>MAP_HEIGHT && e.heading== Enums.Heading.NORTH){
                e.heading = Enums.Heading.SOUTH;
                newY = MAP_HEIGHT-height;
            }
            if(newY<0 && e.heading== Enums.Heading.SOUTH){
                e.heading = Enums.Heading.NORTH;
                newY = 0;
            }

            e.x = newX;
            e.y = newY;
        }
        */
    }

    private void movePlayer(ServerEntity e, Network.UserInput input){
        float deltaX = 0;
        float deltaY = 0;
        if(input.buttons.contains(Enums.Buttons.UP)){
            deltaY = conVars.get("sv_velocity") *input.msec;
            e.heading = Enums.Heading.NORTH;
        }
        if(input.buttons.contains(Enums.Buttons.DOWN)){
            deltaY = -1* conVars.get("sv_velocity") *input.msec;
            e.heading = Enums.Heading.SOUTH;
        }
        if(input.buttons.contains(Enums.Buttons.LEFT)){
            deltaX = -1* conVars.get("sv_velocity") *input.msec;
            e.heading = Enums.Heading.WEST;
        }
        if(input.buttons.contains(Enums.Buttons.RIGHT)){
            deltaX = conVars.get("sv_velocity") *input.msec;
            e.heading = Enums.Heading.EAST;
        }
        if(conVars.getBool("sv_check_map_collisions")) {
            if (e.getX() + e.width + deltaX > MAP_WIDTH) {
                deltaX = MAP_WIDTH - e.getX() - e.width;
            }
            if (e.getX() + deltaX < 0) {
                deltaX = 0 - e.getX();
            }
            if (e.getY() + e.height + deltaY > MAP_HEIGHT) {
                deltaY = MAP_HEIGHT - e.getY() - e.height;
            }
            if (e.getY() + deltaY < 0) {
                deltaY = 0 - e.getY();
            }
        }

        float newX = e.getX()+deltaX;
        float newY = e.getY()+deltaY;
        e.moveTo(newX,newY);
    }

    private int getNextNetworkID(){
        return networkIDCounter++;
    }

    private void startSimulation(){
        new Thread(new Runnable(){
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


                    if(System.nanoTime()-lastPingUpdate>10*1000000000l){
                        System.out.println("Updating pings");
                        for(Connection c:server.getConnections()){
                            c.updateReturnTripTime();
                        }
                        lastPingUpdate = System.nanoTime();
                    }

                    for (int i = 0; i < pendingEntityAdds; i++) {
                        addNPC();
                    }
                    pendingEntityAdds = 0;
                    for (int i = 0; i < pendingEntityRemovals; i++) {
                        removeNPC();
                    }
                    pendingEntityRemovals = 0;

                    processCommands();
                    float processCommandsCheckpoint = (System.nanoTime()-tickStartTime)/1000000f;

                    moveNPC(tickActualDuration/1000000f);
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
        }).start();
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
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0,0,0,1);
        shapeRenderer.rect(0, 0, MAP_WIDTH, MAP_HEIGHT);
        shapeRenderer.setColor(1,1,1,1);
        ServerEntity[] serverEntitiesCopy = entities.values().toArray(new ServerEntity[entities.values().size()]);
        //TODO might want to make copy of playerlist too, to prevent concurrent modifications
        for(ServerEntity e: serverEntitiesCopy){
            if(playerList.values().contains(e.id)){
                shapeRenderer.setColor(0,0,1,1);
                shapeRenderer.rect(e.getX(), e.getY(), e.width, e.height);
                shapeRenderer.setColor(1,1,1,1);
            }else{
                shapeRenderer.rect(e.getX(), e.getY(), e.width, e.height);
            }
        }
        shapeRenderer.end();

        Line2D.Float[] attackVisualsCopy = attackVisuals.toArray(new Line2D.Float[attackVisuals.size()]);
        if(attackVisualsCopy.length>0){
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(1,1,1,1); //white
            for(Line2D.Float line:attackVisualsCopy){
                shapeRenderer.line(line.x1, line.y1, line.x2, line.y2);
            }
            shapeRenderer.end();
        }

        updateStatusData();
    }

    private void updateStatusData(){
        for(Connection c:inputQueue.keySet()){
            connectionStatus.get(c).inputQueue = inputQueue.get(c).size();
        }
        statusData.fps = Gdx.graphics.getFramesPerSecond();
        statusData.entityCount = entities.size();
        statusData.currentTick = currentTick;
        statusData.serverTime = (System.nanoTime() - serverStartTime) / 1000000000f;
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
        if (character == 'w') {
            pendingEntityAdds++;
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
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        viewPortX = MAP_WIDTH/2f;
        viewPortY = MAP_HEIGHT/2f;
        camera.position.set(viewPortX, viewPortY, 0);
        camera.update();
    }
    @Override
    public void pause() {}
    @Override
    public void resume() {}
    @Override
    public void dispose() {}

    @Override
    public boolean keyDown(int keycode) {
        if(keycode == Input.Keys.F1){
            showHelp();
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
        if(!movedEntities.contains(id)){
            movedEntities.add(id);
        }
    }

    @Override
    public void healthChanged(int id) {
        showMessage("Entity "+id+" health changed");
    }
}
