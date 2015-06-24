package com.juniperbrew.minimus.client;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.kryonet.Listener;
import com.juniperbrew.minimus.ConVars;
import com.juniperbrew.minimus.Entity;
import com.juniperbrew.minimus.Enums;
import com.juniperbrew.minimus.Network;
import com.juniperbrew.minimus.Tools;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.components.Heading;
import com.juniperbrew.minimus.components.Health;
import com.juniperbrew.minimus.components.Position;
import com.juniperbrew.minimus.windows.ClientStatusFrame;
import com.juniperbrew.minimus.windows.ConsoleFrame;
import com.juniperbrew.minimus.windows.StatusData;

import java.awt.geom.Line2D;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class MinimusClient implements ApplicationListener, InputProcessor {

    Client client;
    private int writeBuffer = 8192; //Default 8192
    private int objectBuffer = 8192; //Default 2048
    ShapeRenderer shapeRenderer;

    //Counter to give each input request an unique id
    private int currentInputRequest = 0;
    //Input requests that haven't been sent yet
    ArrayList<Network.UserInput> pendingInputPacket;
    //How many times per second we gather all created inputs and send them to server
    //float inputUpdateRate =20;
    private long lastInputSent;
    //Sent input requests that haven't been acknowledged on server yet
    ArrayList<Network.UserInput> inputQueue;

    //Network thread adds new states to this list
    ArrayList<Network.FullEntityUpdate> pendingReceivedStates;//ArrayList<Network.EntityUpdate> pendingReceivedStates;
    //Stored states used for interpolation, each time a state snapshot is created we remove all states older than clientTime-interpolation,
    //one older state than this is still stored in interpFrom untill we reach the next interpolation interval
    ArrayList<Network.FullEntityUpdate> stateHistory;//ArrayList<Network.EntityUpdate> stateHistory;
    //This stores the state with latest servertime, should be same as last state in stateHistory
    Network.FullEntityUpdate authoritativeState;//Network.EntityUpdate authoritativeState;
    //This is basically authoritativeState.serverTime
    float lastServerTime;
    //System.nanoTime() for when authoritativeState is changed
    long lastAuthoritativeStateReceived;

    //This is what we render
    private HashMap<Integer,Entity> stateSnapshot;

    //StateSnapshot is an interpolation between these two states
    private Network.FullEntityUpdate interpFrom;//private Network.EntityUpdate interpFrom;
    private Network.FullEntityUpdate interpTo;//private Network.EntityUpdate interpTo;

    private ArrayList<Network.AddEntity> pendingAddedEntities;
    private ArrayList<Network.RemoveEntity> pendingRemovedEntities;


    int playerID;
    EnumSet<Enums.Buttons> buttons = EnumSet.noneOf(Enums.Buttons.class);
    private ArrayList<Line2D.Float> attackVisuals;
    Timer timer;
    final float ATTACK_DELAY = 0.3f;
    long lastAttackDone;

    long lastPingRequest;
    long renderStart;
    long logIntervalStarted;
    long clientStartTime;

    String serverIP;

    ConVars conVars;
    StatusData statusData;
    ClientStatusFrame statusFrame;
    ConsoleFrame consoleFrame;

    private OrthographicCamera camera;

    ArrayList<Integer> playerList;

    int mapWidth;
    int mapHeight;

    ByteBuffer buffer = ByteBuffer.allocate(objectBuffer);

    public MinimusClient(String ip){
        serverIP = ip;
    }

    private void initialize(){
        inputQueue = new ArrayList<Network.UserInput>();
        shapeRenderer = new ShapeRenderer();
        stateHistory = new ArrayList<Network.FullEntityUpdate>();
        pendingReceivedStates = new ArrayList<Network.FullEntityUpdate>();
        pendingInputPacket = new ArrayList<Network.UserInput>();
        pendingAddedEntities = new ArrayList<Network.AddEntity>();
        pendingRemovedEntities = new ArrayList<>();
        playerList = new ArrayList<>();
        attackVisuals = new ArrayList<Line2D.Float>();
        timer = new Timer();
    }

    private void logIntervalElapsed(){
        statusData.intervalElapsed();
    }

    private void logReceivedPackets(Connection connection, Object packet){
        KryoSerialization s = (KryoSerialization) client.getSerialization();
        s.write(connection, buffer ,packet);
        statusData.addBytesReceived(buffer.position());
        buffer.clear();
    }
    
    @Override
    public void create() {
        int h = Gdx.graphics.getHeight();
        int w = Gdx.graphics.getWidth();
        camera = new OrthographicCamera(h, w);
        centerCameraOnPlayer();

        conVars = new ConVars();
        consoleFrame = new ConsoleFrame(conVars);
        clientStartTime = System.nanoTime();
        statusData = new StatusData(null,clientStartTime,conVars.getInt("cl_log_interval_seconds"));
        statusFrame = new ClientStatusFrame(statusData);
        initialize();
        joinServer();
        Gdx.input.setInputProcessor(this);
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

    private void showHelp(){
        try(BufferedReader reader = new BufferedReader(new FileReader("clientHelp.txt"))){
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

    private void showMessage(String message){
        System.out.println(message);
        consoleFrame.addLine(message);
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
                        changedEntity.x = pos.x;
                        changedEntity.y = pos.y;
                    }
                    if(component instanceof Heading) {
                        Heading heading = (Heading) component;
                        changedEntity.heading = heading.heading;
                    }
                    if(component instanceof Health) {
                        Health health = (Health) component;
                        changedEntity.health = health.health;
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

    private void joinServer(){
        client = new Client(writeBuffer,objectBuffer);
        Network.register(client);
        client.addListener(new Listener() {

            @Override
            public void connected(Connection connection){
            }

            public void received(Connection connection, Object object) {
                logReceivedPackets(connection, object);

                if(object instanceof Network.FullEntityUpdate){
                    Network.FullEntityUpdate fullUpdate = (Network.FullEntityUpdate) object;
                    pendingReceivedStates.add(fullUpdate);
                    //System.out.println("Received full update");
                }else if(object instanceof Network.EntityPositionUpdate){
                    Network.EntityPositionUpdate update = (Network.EntityPositionUpdate) object;
                    if(authoritativeState!=null) {
                        pendingReceivedStates.add(applyEntityPositionUpdate(update));
                    }else{
                        showMessage("Received entity position update but there is no complete state to apply it to");
                    }
                }else if(object instanceof Network.EntityComponentsUpdate){
                    Network.EntityComponentsUpdate update = (Network.EntityComponentsUpdate) object;
                    if(authoritativeState!=null) {
                        pendingReceivedStates.add(applyEntityComponentUpdate(update));
                    }else{
                        showMessage("Received entity component update but there is no complete state to apply it to");
                    }
                }else if(object instanceof Network.EntityAttacking){
                    Network.EntityAttacking entityAttacking = (Network.EntityAttacking) object;
                    showMessage("PlayerID "+entityAttacking.id+" attacking with weapon "+entityAttacking.weapon);
                    //TODO try adding the attacks to different states
                    createAttackVisual(authoritativeState.entities.get(entityAttacking.id));
                }else if(object instanceof Network.AddPlayer){
                    Network.AddPlayer addPlayer = (Network.AddPlayer) object;
                    showMessage("PlayerID "+addPlayer.networkID+" added.");
                    playerList.add(addPlayer.networkID);
                }else if(object instanceof Network.RemovePlayer){
                    Network.RemovePlayer removePlayer = (Network.RemovePlayer) object;
                    showMessage("PlayerID "+removePlayer.networkID+" removed.");
                    playerList.remove((Integer) removePlayer.networkID);
                }else if(object instanceof Network.AddEntity){
                    Network.AddEntity addEntity = (Network.AddEntity) object;
                    pendingAddedEntities.add(addEntity);
                }else if(object instanceof Network.RemoveEntity){
                    Network.RemoveEntity removeEntity = (Network.RemoveEntity) object;
                    pendingRemovedEntities.add(removeEntity);
                }else if(object instanceof Network.AssignEntity){
                    Network.AssignEntity assign = (Network.AssignEntity) object;
                    playerID = assign.networkID;
                    conVars.set("sv_velocity",assign.velocity);
                    mapHeight = assign.mapHeight;
                    mapWidth = assign.mapWidth;
                    playerList = assign.playerList;
                }else if(object instanceof String){
                    String command = (String) object;
                    consoleFrame.giveCommand(command);
                }
            }
        });
        client.start();
        try {
            client.connect(5000, serverIP, Network.portTCP, Network.portUDP);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    private void pollInput(short delta){
        if(buttons.size()>0){
            int inputRequestID = getNextInputRequestID();
            Network.UserInput input = new Network.UserInput();
            input.msec = delta;
            input.buttons = buttons.clone();
            input.inputID = inputRequestID;
            inputQueue.add(input);
            pendingInputPacket.add(input);
        }
        if(buttons.contains(Enums.Buttons.SPACE)){
            playerAttack();
        }
    }

    private void playerAttack(){
        if(System.nanoTime()-lastAttackDone < Tools.secondsToNano(ATTACK_DELAY)){
            return;
        }
        lastAttackDone = System.nanoTime();
        createAttackVisual(stateSnapshot.get(playerID));
    }

    private void createAttackVisual(Entity e){

        float originX = 0;
        float originY = 0;

        switch(e.heading){
            case NORTH: originX = e.x+e.width/2; originY = e.y+e.height; break;
            case SOUTH: originX = e.x+e.width/2; originY = e.y; break;
            case WEST: originX = e.x; originY = e.y+e.height/2; break;
            case EAST: originX = e.x+e.width; originY = e.y+e.height/2; break;
        }

        float targetX = e.x+e.width/2;
        float targetY = e.y+e.height/2;
        switch (e.heading){
            case NORTH: targetY += 200; break;
            case SOUTH: targetY -= 200; break;
            case EAST: targetX += 200; break;
            case WEST: targetX -= 200; break;
        }

        final Line2D.Float hitScan = new Line2D.Float(originX,originY,targetX,targetY);
        attackVisuals.add(hitScan);
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                attackVisuals.remove(hitScan);
            }
        };
        timer.schedule(task,1000);
    }

    private void runClientSidePrediction(HashMap<Integer, Entity> state){

        if(!conVars.getBool("cl_clientside_prediction")){
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
            //System.out.print("Predicting player inputID:" + input.inputID);
            moveEntity(state.get(playerID),input);
        }
    }

    private void moveEntity(Entity e, Network.UserInput input){
        double deltaX = 0;
        double deltaY = 0;
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
        if(conVars.getBool("cl_check_map_collisions")){
            if(e.x+e.width+deltaX>mapWidth){
                deltaX = mapWidth-e.x-e.width;
            }
            if(e.x+deltaX<0){
                deltaX = 0-e.x;
            }
            if(e.y+e.height+deltaY>mapHeight){
                deltaY = mapHeight-e.y-e.height;
            }
            if(e.y+deltaY<0){
                deltaY = 0-e.y;
            }
        }
        e.x += deltaX;
        e.y += deltaY;
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
        if(conVars.get("cl_interp") <= 0){
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

        double renderTime = clientTime-conVars.get("cl_interp");

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
                    interpEntity.x = interpPos.x;
                    interpEntity.y = interpPos.y;
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
        interpPos.x = (float)(from.x+(to.x-from.x)*alpha);
        interpPos.y = (float)(from.y+(to.y-from.y)*alpha);
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

    private void applyNetworkUpdates(){
        //Add new states to state history, sort states and store the latest state
        if(pendingReceivedStates.size()>0){
            stateHistory.addAll(pendingReceivedStates);
            Collections.sort(stateHistory); //FIXME do i need this
            pendingReceivedStates.clear();

            if(authoritativeState==null){
                authoritativeState = stateHistory.get(stateHistory.size()-1);
                lastAuthoritativeStateReceived = System.nanoTime();
            }else if (stateHistory.get(stateHistory.size()-1).serverTime != authoritativeState.serverTime){
                authoritativeState = stateHistory.get(stateHistory.size()-1);
                lastAuthoritativeStateReceived = System.nanoTime();
            }
            lastServerTime = authoritativeState.serverTime;
            //System.out.println("InputID:"+authoritativeState.lastProcessedInputID+" Time:"+authoritativeState.serverTime+" is now authoritative state.");
        }
        //Entities will be added to latest state despite their add time
        for(Network.AddEntity addEntity:pendingAddedEntities){
            stateHistory.get(stateHistory.size()-1).entities.put(addEntity.entity.id,addEntity.entity);
        }
        //Entities will be removed from latest state despite their remove time
        for(Network.RemoveEntity removeEntity:pendingRemovedEntities){
            stateHistory.get(stateHistory.size()-1).entities.remove(removeEntity.networkID);
        }
        pendingAddedEntities.clear();
        pendingRemovedEntities.clear();
    }

    private void doLogic(){

        long logicStart = System.nanoTime();
        float checkpoint0 = (System.nanoTime()-logicStart)/1000000f;
        float delta = Gdx.graphics.getDeltaTime();

        //Request ping update every 10 seconds
        if(System.nanoTime()-lastPingRequest>10*1000000000l){
            System.out.println("Checking ping");
            client.updateReturnTripTime();
            lastPingRequest = System.nanoTime();
        }

        applyNetworkUpdates();

        float checkpoint1 = (System.nanoTime()-logicStart)/1000000f;

        //Send all gathered inputs at set interval
        if(System.nanoTime()- lastInputSent > 1000000000/conVars.getInt("cl_input_update_rate")){
            if(pendingInputPacket.size() > 0) {
                Network.UserInputs inputPacket = new Network.UserInputs();
                if(conVars.getBool("cl_compress_input")){
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
        float checkpoint2 = (System.nanoTime()-logicStart)/1000000f;

        //Dont pollInput or create snapshot if we have no state from server
        if (authoritativeState != null){
            pollInput((short) (delta*1000));
            float clientTime = lastServerTime + ((System.nanoTime() - lastAuthoritativeStateReceived) / 1000000000f);
            createStateSnapshot(clientTime); //FIXME Will cause concurrent modifications
        }
        float checkpoint3 = (System.nanoTime()-logicStart)/1000000f;
        if(checkpoint3-checkpoint0>1&& conVars.getBool("cl_show_performance_warnings")) {
            showMessage("UpdateStates:" + (checkpoint1 - checkpoint0) + "ms");
            showMessage("Send input:" + (checkpoint2 - checkpoint1) + "ms");
            showMessage("PollInput&CreateStateSnapshot:" + (checkpoint3 - checkpoint2) + "ms");
        }
    }

    @Override
    public void render() {
        if((System.nanoTime()-renderStart)/1000000f > 30 && conVars.getBool("cl_show_performance_warnings")){
            showMessage("Long time since last render() call:" + (System.nanoTime() - renderStart) / 1000000f);
        }
        if(System.nanoTime()- logIntervalStarted > Tools.secondsToNano(conVars.getInt("cl_log_interval_seconds"))){
            logIntervalStarted = System.nanoTime();
            logIntervalElapsed();
        }
        renderStart = System.nanoTime();

        doLogic();
        centerCameraOnPlayer();
        shapeRenderer.setProjectionMatrix(camera.combined);

        Gdx.gl.glClearColor(0.5f, 0.5f, 0.5f, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glLineWidth(3);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0,0,0,1);
        shapeRenderer.rect(0, 0, mapWidth, mapHeight);
        shapeRenderer.end();

        statusFrame.update();

        if(stateSnapshot !=null){
            if(conVars.getBool("cl_show_debug")) {
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(1, 0, 0, 1); //red
                for (Entity e : interpFrom.entities.values()) {
                    shapeRenderer.rect(e.x, e.y, 50, 50);
                }
                shapeRenderer.setColor(0, 1, 0, 1); //green
                for (Entity e : interpTo.entities.values()) {
                    shapeRenderer.rect(e.x, e.y, 50, 50);
                }
                shapeRenderer.setColor(0, 0, 1, 1); //blue
                for (Entity e : authoritativeState.entities.values()) {
                    shapeRenderer.rect(e.x, e.y, 50, 50);
                }
                shapeRenderer.end();
            }
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            //Render entities
            for(int id: stateSnapshot.keySet()){ //FIXME will there ever be an entity that is not in stateSnapshot, yes when adding entities on server so we get nullpointer here
                Entity e = stateSnapshot.get(id);
                if(playerList.contains(e.id)){
                    shapeRenderer.setColor(0,0,1,1);
                }else{
                    shapeRenderer.setColor(1,1,1,1);
                }
                shapeRenderer.rect(e.x, e.y, e.width, e.height);
                shapeRenderer.rect(e.x, e.y, e.width, e.height);
                float health = 1-((float)e.health/e.maxHealth);
                int healthWidth = (int) (e.width*health);
                shapeRenderer.setColor(1,0,0,1); //red
                shapeRenderer.rect(e.x,e.y,healthWidth,e.height);
            }
            shapeRenderer.end();
            Line2D.Float[] attackVisualsCopy = attackVisuals.toArray(new Line2D.Float[attackVisuals.size()]);
            if(attackVisualsCopy.length>0){
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(1,0,0,1); //red
                for(Line2D.Float line:attackVisualsCopy){
                    //TODO getting nullpointers here when spamming attack for some time
                    shapeRenderer.line(line.x1, line.y1, line.x2, line.y2);
                }
                shapeRenderer.end();
            }
        }

        if(stateSnapshot!=null){
            statusData.setEntityCount(stateSnapshot.size());
        }
        statusData.setFps(Gdx.graphics.getFramesPerSecond());
        statusData.setServerTime(lastServerTime);
        statusData.setClientTime(lastServerTime+((System.nanoTime()- lastAuthoritativeStateReceived)/1000000000f));
        statusData.currentInputRequest = currentInputRequest;
        statusData.inputQueue = inputQueue.size();

        if((System.nanoTime()-renderStart)/1000000f>30 && conVars.getBool("cl_show_performance_warnings")){
            showMessage("Long Render() duration:" + (System.nanoTime() - renderStart) / 1000000f);
        }
    }

    /*private void drawLine(SpriteBatch batch, int x1, int y1, int x2, int y2, int thickness) {
        int dx = x2-x1;
        int dy = y2-y1;
        float dist = (float)Math.sqrt(dx*dx + dy*dy);
        float rad = (float)Math.atan2(dy, dx);
        batch.draw(whiteTexture,x1,y1,)
        batch.draw(whiteTexture, x1, y1, dist, thickness, 0, 0, rad);
    }*/

    private void printPlayerList(){
        showMessage("#Playerlist#");
        for(int id:playerList){
            showMessage("ID:"+id);
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if(keycode == Input.Keys.F1){
            showHelp();
        }
        if(keycode == Input.Keys.LEFT) buttons.add(Enums.Buttons.LEFT);
        if(keycode == Input.Keys.RIGHT) buttons.add(Enums.Buttons.RIGHT);
        if(keycode == Input.Keys.UP)buttons.add(Enums.Buttons.UP);
        if(keycode == Input.Keys.DOWN)buttons.add(Enums.Buttons.DOWN);
        if(keycode == Input.Keys.SPACE)buttons.add(Enums.Buttons.SPACE);
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        if(keycode == Input.Keys.LEFT) buttons.remove(Enums.Buttons.LEFT);
        if(keycode == Input.Keys.RIGHT) buttons.remove(Enums.Buttons.RIGHT);
        if(keycode == Input.Keys.UP)buttons.remove(Enums.Buttons.UP);
        if(keycode == Input.Keys.DOWN)buttons.remove(Enums.Buttons.DOWN);
        if(keycode == Input.Keys.SPACE)buttons.remove(Enums.Buttons.SPACE);
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
        if(character == 'w'){
            boolean showPerformanceWarnings = conVars.getBool("cl_show_performance_warnings");
            if(showPerformanceWarnings){
                conVars.set("cl_show_performance_warnings",false);
            }else{
                conVars.set("cl_show_performance_warnings",true);
            }
            showMessage("ShowPerformanceWarnings:" + conVars.getBool("cl_show_performance_warnings"));
        }
        if(character == 'q'){
            KryoSerialization s = (KryoSerialization) client.getSerialization();
            Entity e = new Entity(-1,50,50);
            s.write(client, buffer ,e);
            showMessage("Entity size is " + buffer.position() + " bytes");
            buffer.clear();

            int totalEntitySize = 0;

            s.write(client, buffer ,e.heading);
            totalEntitySize += buffer.position();
            showMessage("Heading size is " + buffer.position() + " bytes");
            buffer.clear();

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
        if(character == '1'){
            showConsoleWindow();
        }
        if(character == '2'){
            showStatusWindow();
        }

        if (character == '3') {
            conVars.toggleVar("cl_clientside_prediction");
            showMessage("UseClientSidePrediction:" + conVars.getBool("cl_clientside_prediction"));
        }
        if (character == '4') {
            conVars.toggleVar("cl_compress_input");
            showMessage("CompressInput:" + conVars.getBool("cl_compress_input"));
        }
        if(character == '0'){
            conVars.toggleVar("cl_show_debug");
            showMessage("Show debug:" + conVars.getBool("cl_show_debug"));
        }

        if(character == '+'){
            conVars.addToVar("cl_interp",0.05);
            showMessage("Interpolation delay now:" + conVars.get("cl_interp"));
        }
        if(character == '-'){
            conVars.addToVar("cl_interp",-0.05);
            if(conVars.get("cl_interp")<0){
                conVars.set("cl_interp",0);
            }
            showMessage("Interpolation delay now:" + conVars.get("cl_interp"));
        }

        return false;
    }

    private void centerCameraOnPlayer(){
        Entity player = null;
        if(stateSnapshot!=null){
            player = stateSnapshot.get(playerID);
        }
        if(player != null){
            camera.position.set(player.x+player.width/2f, player.y+player.height/2, 0);
        }else{
            camera.position.set(mapWidth/2f, mapHeight/2f, 0);
        }
        camera.update();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        centerCameraOnPlayer();
    }

    @Override
    public void pause() {}

    @Override
    public void resume() {}

    @Override
    public void dispose() {}

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

}
