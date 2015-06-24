package com.juniperbrew.minimus.server;

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
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.Enums;
import com.juniperbrew.minimus.Network;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.components.Heading;
import com.juniperbrew.minimus.components.Health;
import com.juniperbrew.minimus.components.Position;
import com.juniperbrew.minimus.windows.ConsoleFrame;
import com.juniperbrew.minimus.windows.ServerStatusFrame;
import com.juniperbrew.minimus.windows.StatusData;

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
    HashMap<Integer,ServerEntity> entities = new HashMap<>();
    HashMap<Integer,EntityAI> entityAIs = new HashMap<>();
    HashMap<Connection,Integer> playerList = new HashMap<>();
    HashMap<Connection,Integer> lastInputIDProcessed = new HashMap<>();
    HashMap<Connection,Integer> connectionUpdateRate = new HashMap<>();
    HashMap<Connection, ArrayList<Network.UserInput>> inputQueue = new HashMap<>();
    HashMap<Connection, StatusData> connectionStatus = new HashMap<>();

    ArrayList<Integer> posChangedEntities = new ArrayList<>();
    ArrayList<Integer> healthChangedEntities = new ArrayList<>();
    ArrayList<Integer> headingChangedEntities = new ArrayList<>();
    ArrayList<Integer> changedEntities = new ArrayList<>();

    private int networkIDCounter = 1;
    private int currentTick=0;
    long serverStartTime;

    int screenW;
    int screenH;

    private int writeBuffer = 16384; //Default 16384
    private int objectBuffer = 4096; //Default 2048
    private long lastPingUpdate;

    HashMap<Connection,Long> lastAttackDone = new HashMap<>();
    private ArrayList<Line2D.Float> attackVisuals = new ArrayList<>();
    final long ATTACK_DELAY = (long) (0.3*1000000000);
    Timer timer = new Timer();

    ByteBuffer buffer = ByteBuffer.allocate(objectBuffer);
    long logIntervalStarted;

    ServerStatusFrame serverStatusFrame;
    ConsoleFrame consoleFrame;
    StatusData statusData;

    ConVars conVars;

    final int MAP_WIDTH = 2000;
    final int MAP_HEIGHT = 1500;

    float viewPortX;
    float viewPortY;
    float cameraVelocity = 300f; //pix/s

    private OrthographicCamera camera;
    EnumSet<Enums.Buttons> buttons = EnumSet.noneOf(Enums.Buttons.class);

    int pendingEntityAdds = 0;
    int pendingEntityRemovals = 0;

    private void initialize(){
        shapeRenderer = new ShapeRenderer();
        screenW = Gdx.graphics.getWidth();
        screenH = Gdx.graphics.getHeight();

        for (int i = 0; i < 20; i++) {
            addNPC();
        }
    }

    @Override
    public void create() {

        conVars = new ConVars();
        consoleFrame = new ConsoleFrame(conVars,this);

        int h = Gdx.graphics.getHeight();
        int w = Gdx.graphics.getWidth();
        resize(h,w);

        serverStartTime = System.nanoTime();
        statusData = new StatusData(null,serverStartTime,conVars.getInt("cl_log_interval_seconds"));
        serverStatusFrame = new ServerStatusFrame(statusData);
        startServer();
        initialize();
        startSimulation();
        Gdx.input.setInputProcessor(this);

        statusData.entitySize = measureObject(new Entity(-1, 1000000, 1000000));
        showMessage("Kryo entity size:" + statusData.entitySize + "bytes");
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

                Network.FullEntityUpdate fullUpdate = new Network.FullEntityUpdate();
                fullUpdate.entities = getNetworkedEntityList(entities);
                connection.sendTCP(fullUpdate);

                StatusData dataUsage = new StatusData(connection, System.nanoTime(),conVars.getInt("cl_log_interval_seconds"));
                connectionStatus.put(connection, dataUsage);

                int networkID = getNextNetworkID();
                int width = 50;
                int height = 50;
                float x = MathUtils.random(MAP_WIDTH-width);
                float y = MathUtils.random(MAP_HEIGHT-height);
                ServerEntity newPlayer = new ServerEntity(networkID,x,y,MinimusServer.this);
                newPlayer.width = width;
                newPlayer.height = height;
                addEntity(newPlayer);

                playerList.put(connection,networkID);
                serverStatusFrame.addConnection(connection.toString(),dataUsage);
                Network.AddPlayer addPlayer = new Network.AddPlayer();
                addPlayer.networkID = networkID;
                sendTCPtoAllExcept(connection,addPlayer);

                Network.AssignEntity assign = new Network.AssignEntity();
                assign.networkID = networkID;
                assign.velocity = (float)conVars.get("sv_velocity");
                assign.mapHeight = MAP_HEIGHT;
                assign.mapWidth = MAP_WIDTH;
                assign.playerList = new ArrayList<>(playerList.values());
                connection.sendTCP(assign);
            }

            @Override
            public void disconnected(Connection connection){
                int networkID = playerList.get(connection);
                entities.remove(networkID);
                playerList.remove(connection);
                connectionStatus.get(connection).disconnected();
                Network.RemovePlayer removePlayer = new Network.RemovePlayer();
                removePlayer.networkID = networkID;
                sendTCPtoAllExcept(connection,removePlayer);
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
            Network.EntityAttacking entityAttacking = new Network.EntityAttacking();
            entityAttacking.id = playerList.get(connection);
            entityAttacking.weapon = 0;
            sendTCPtoAllExcept(connection,entityAttacking);

            ServerEntity e = entities.get(playerList.get(connection));

            float originX = 0;
            float originY = 0;
            switch(e.getHeading()){
                case NORTH: originX = e.getX()+e.width/2; originY = e.getY()+e.height; break;
                case SOUTH: originX = e.getX()+e.width/2; originY = e.getY(); break;
                case WEST: originX = e.getX(); originY = e.getY()+e.height/2; break;
                case EAST: originX = e.getX()+e.width; originY = e.getY()+e.height/2; break;
            }

            float targetX = e.getX()+e.width/2;
            float targetY = e.getY()+e.height/2;
            switch (e.getHeading()){
                case NORTH: targetY += 200; break;
                case SOUTH: targetY -= 200; break;
                case EAST: targetX += 200; break;
                case WEST: targetX -= 200; break;
            }
            final Line2D.Float hitScan = new Line2D.Float(originX,originY,targetX,targetY);
            ArrayList<Integer> deadEntities = new ArrayList<>();
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
                    removeEntity(id);
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
        for(int id: posChangedEntities){
            ServerEntity e = entities.get(id);
            positions.put(id,new Network.Position((float)e.getX(),(float)e.getY()));
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
                components.add(new Position((float)e.getX(),(float)e.getY()));
            }
            if(headingChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Heading(e.getHeading()));
            }
            if(healthChangedEntities.contains(id)){
                ServerEntity e = entities.get(id);
                components.add(new Health(e.getHealth()));
            }
        }
        return changedComponents;
    }

    private void sendUDP(Connection connection, Object o){
        int bytesSent = connection.sendUDP(o);
        connectionStatus.get(connection).addBytesSent(bytesSent);
        statusData.addBytesSent(bytesSent);
    }

    private void sendTCP(Connection connection, Object o){
        int bytesSent = connection.sendTCP(o);
        connectionStatus.get(connection).addBytesSent(bytesSent);
        statusData.addBytesSent(bytesSent);
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
            for(Connection connection :playerList.keySet()){
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
            for(Connection connection :playerList.keySet()){
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
            for(Connection connection :playerList.keySet()) {
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
        npc.setHeading(Enums.Heading.values()[randomHeading]);
        entityAIs.put(networkID,new EntityAI(npc));
        addEntity(npc);
    }

    private void removeNPC(){
        Integer[] keys = entities.keySet().toArray(new Integer[entities.keySet().size()]);
        if(keys.length != 0){
            if(playerList.values().size() < entities.size()){
                int networkID ;
                int removeIndex;
                do{
                    removeIndex = MathUtils.random(keys.length-1);
                }while(playerList.values().contains(keys[removeIndex]));
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
        addEntity.entity=e.getEntity();
        addEntity.serverTime=getServerTime();
        sendTCPtoAll(addEntity);
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

    private void moveNPC(float delta){

        for(EntityAI ai:entityAIs.values()){
            ai.move(conVars.get("sv_velocity"),delta);
        }

        if(posChangedEntities.size()<conVars.getInt("sv_max_moving_entities")&&entityAIs.size()>0){
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
        double deltaX = 0;
        double deltaY = 0;
        if(input.buttons.contains(Enums.Buttons.UP)){
            deltaY = conVars.get("sv_velocity") *input.msec;
            e.setHeading(Enums.Heading.NORTH);
        }
        if(input.buttons.contains(Enums.Buttons.DOWN)){
            deltaY = -1* conVars.get("sv_velocity") *input.msec;
            e.setHeading(Enums.Heading.SOUTH);
        }
        if(input.buttons.contains(Enums.Buttons.LEFT)){
            deltaX = -1* conVars.get("sv_velocity") *input.msec;
            e.setHeading(Enums.Heading.WEST);
        }
        if(input.buttons.contains(Enums.Buttons.RIGHT)){
            deltaX = conVars.get("sv_velocity") *input.msec;
            e.setHeading(Enums.Heading.EAST);
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

        double newX = e.getX()+deltaX;
        double newY = e.getY()+deltaY;
        e.moveTo((float)newX,(float)newY);
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
        Gdx.gl.glLineWidth(3);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0,0,0,1);
        shapeRenderer.rect(0, 0, MAP_WIDTH, MAP_HEIGHT);
        shapeRenderer.setColor(1,1,1,1);
        ServerEntity[] serverEntitiesCopy = entities.values().toArray(new ServerEntity[entities.values().size()]);
        //TODO might want to make copy of playerlist too, to prevent concurrent modifications
        for(ServerEntity e: serverEntitiesCopy){
            if(playerList.values().contains(e.id)){
                shapeRenderer.setColor(0,0,1,1);
            }else{
                shapeRenderer.setColor(1,1,1,1);
            }
            shapeRenderer.rect((float)e.getX(), (float)e.getY(), e.width, e.height);
            float health = 1-((float)e.getHealth()/e.maxHealth);
            int healthWidth = (int) (e.width*health);
            shapeRenderer.setColor(1,0,0,1); //red
            shapeRenderer.rect((float)e.getX(),(float)e.getY(),healthWidth,e.height);
        }
        shapeRenderer.end();

        Line2D.Float[] attackVisualsCopy = attackVisuals.toArray(new Line2D.Float[attackVisuals.size()]);
        if(attackVisualsCopy.length>0){
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(1,0,0,1); //red
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
        statusData.setServerTime((System.nanoTime() - serverStartTime) / 1000000000f);
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
    public void resize(int w, int h) {
        if(h > ((float)w/MAP_WIDTH)*MAP_HEIGHT){
            camera = new OrthographicCamera(MAP_WIDTH, h*((float)MAP_WIDTH/w));
        }else{
            camera = new OrthographicCamera(w*((float)MAP_HEIGHT/h), MAP_HEIGHT);
        }
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
        if(!posChangedEntities.contains(id)){
            posChangedEntities.add(id);
        }
        if(!changedEntities.contains(id)){
            changedEntities.add(id);
        }
    }

    @Override
    public void healthChanged(int id) {
        showMessage("Entity "+id+" health changed");
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
}
