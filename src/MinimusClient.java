import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.kryonet.Listener;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
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
    private int objectBuffer = 4096; //Default 2048
    ShapeRenderer shapeRenderer;

    boolean useClientSidePrediction = true;
    float interpDelay = 1f;  //TODO allow an option to link this to server updateRate
    //minimum interpDelay (1/serverUpdateRate)+0,5*latency

    //Counter to give each input request an unique id
    private int currentInputRequest = 0;
    //Input requests that haven't been sent yet
    ArrayList<Network.UserInput> pendingInputPacket;
    //How many times per second we gather all created inputs and send them to server
    float inputUpdateRate =20;
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

    //Velocity of all entities sent from server, if this doesn't match server velocity then player clientside prediction will be wrong and corrected each state update
    float velocity;
    int playerID;
    EnumSet<Enums.Buttons> buttons = EnumSet.noneOf(Enums.Buttons.class);

    boolean showDebug;
    boolean compressInput = true;
    long lastPingRequest;
    private ArrayList<Network.AddEntity> pendingAddedEntities;

    private ArrayList<Line2D.Float> attackVisuals;
    Timer timer;
    final long ATTACK_DELAY = (long) (0.3*1000000000);

    long lastAttackDone;
    FPSLogger fpsLogger = new FPSLogger();
    long renderStart;

    String serverIP;

    long totalUDPBytesSent;
    int totalUDPPacketsSent;
    long currentUDPBytesSent;
    int currentUDPPacketsSent;
    long currentUDPBytesSentCounter;
    int currentUDPPacketsSentCounter;



    long totalTCPBytesSent;
    int totalTCPPacketsSent;

    long totalBytesReceived;
    int totalPacketsReceived;
    long currentBytesReceived;
    int currentPacketsReceived;
    long currentBytesReceivedCounter;
    int currentPacketsReceivedCounter;

    long logTwoSecondStarted = 0;


    long clientStartTime;
    ByteBuffer buffer = ByteBuffer.allocate(objectBuffer);

    DataDialog dataDialog;

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
        attackVisuals = new ArrayList<Line2D.Float>();
        timer = new Timer();
    }

    private void twoSecondElapsed(){
        currentBytesReceived = currentBytesReceivedCounter;
        currentPacketsReceived = currentPacketsReceivedCounter;
        currentBytesReceivedCounter = 0;
        currentPacketsReceivedCounter = 0;

        currentUDPBytesSent = currentUDPBytesSentCounter;
        currentUDPPacketsSent = currentUDPPacketsSentCounter;
        currentUDPBytesSentCounter = 0;
        currentUDPPacketsSentCounter = 0;
    }

    private void logReceivedPackets(Connection connection, Object packet){
        KryoSerialization s = (KryoSerialization) client.getSerialization();
        s.write(connection, buffer ,packet);
        System.out.println("Received " + buffer.position() + " bytes");
        totalPacketsReceived++;
        totalBytesReceived += buffer.position();
        currentBytesReceivedCounter += buffer.position();
        currentPacketsReceivedCounter++;
        buffer.clear();
    }
    
    @Override
    public void create() {
        dataDialog = new DataDialog();
        clientStartTime = System.nanoTime();
        initialize();
        joinServer();
        Gdx.input.setInputProcessor(this);
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

                /*if(object instanceof Network.EntityUpdate){
                    Network.EntityUpdate update = (Network.EntityUpdate) object;
                    //System.out.println("Received entity update that has processed inputID:"+update.lastProcessedInputID+" ServerTime:"+update.serverTime);
                    pendingReceivedStates.add(update);
                }else */
                if(object instanceof Network.AddEntity){
                    Network.AddEntity addEntity = (Network.AddEntity) object;
                    pendingAddedEntities.add(addEntity);
                }else if(object instanceof Network.AssignEntity){
                    Network.AssignEntity assign = (Network.AssignEntity) object;
                    playerID = assign.networkID;
                    velocity = assign.velocity;
                }else if(object instanceof Network.FullEntityUpdate){
                    Network.FullEntityUpdate fullUpdate = (Network.FullEntityUpdate) object;
                    pendingReceivedStates.add(fullUpdate);
                    //System.out.println("Received full update");
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
        totalUDPBytesSent += client.sendUDP(o);
        currentUDPBytesSentCounter += client.sendUDP(o);

        totalUDPPacketsSent++;
        currentUDPPacketsSentCounter++;
    }

    private void sendTCP(Object o){
        totalTCPBytesSent += client.sendTCP(o);
        totalTCPPacketsSent++;
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
            createAttackVisual();
        }
    }

    private void createAttackVisual(){
        if(System.nanoTime()-lastAttackDone < ATTACK_DELAY){
            return;
        }
        lastAttackDone = System.nanoTime();
        Entity e = stateSnapshot.get(playerID);
        float originX = e.x+e.width/2;
        float originY = e.y+e.height/2;
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

        if(!useClientSidePrediction){
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
        float deltaX = 0;
        float deltaY = 0;
        if(input.buttons.contains(Enums.Buttons.UP)){
            deltaY = velocity *input.msec;
            e.heading = Enums.Heading.NORTH;
        }
        if(input.buttons.contains(Enums.Buttons.DOWN)){
            deltaY = -1* velocity *input.msec;
            e.heading = Enums.Heading.SOUTH;
        }
        if(input.buttons.contains(Enums.Buttons.LEFT)){
            deltaX = -1* velocity *input.msec;
            e.heading = Enums.Heading.WEST;
        }
        if(input.buttons.contains(Enums.Buttons.RIGHT)){
            deltaX = velocity *input.msec;
            e.heading = Enums.Heading.EAST;
        }
        //System.out.println(" Moved from ("+pos.x+","+pos.y+") to ("+(pos.x+deltaX)+","+(pos.y+deltaY)+")");
        e.x += deltaX;
        e.y += deltaY;
    }

    private void printStateHistory(){
        System.out.println("#State History:");
        int index = 0;
        Network.FullEntityUpdate[] stateHistoryCopy = stateHistory.toArray(new Network.FullEntityUpdate[stateHistory.size()]);
        for(Network.FullEntityUpdate state :stateHistoryCopy){
            System.out.println(index+"> Time:"+state.serverTime+" InputID:"+state.lastProcessedInputID+" EntityCount:"+state.entities.size());
            index++;
        }
    }

    private void createStateSnapshot(float clientTime){

        //printStateHistory();
        //If interp delay is 0 we simply copy the latest autorative state and run clientside prediction on that
        if(interpDelay == 0){
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

        float renderTime = clientTime-interpDelay;

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

            float interpAlpha;
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

    private HashMap<Integer, Entity> interpolateStates(Network.FullEntityUpdate from, Network.FullEntityUpdate to, float alpha){
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

    private Network.Position interpolate(Entity from, Entity to, float alpha){
        if(to == null){
            //If entity has dissapeared in next state we simply remove it without interpolation
            return null;
        }
        Network.Position interpPos = new Network.Position();
        interpPos.x = from.x+(to.x-from.x)*alpha;
        interpPos.y = from.y+(to.y-from.y)*alpha;
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
        if(System.nanoTime()- lastInputSent > 1000000000/inputUpdateRate){
            if(pendingInputPacket.size() > 0) {
                Network.UserInputs inputPacket = new Network.UserInputs();
                if(compressInput){
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
        if(checkpoint3-checkpoint0>1) {
            System.out.println("UpdateStates:" + (checkpoint1 - checkpoint0) + "ms");
            System.out.println("Send input:" + (checkpoint2 - checkpoint1) + "ms");
            System.out.println("PollInput&CreateStateSnapshot:" + (checkpoint3 - checkpoint2) + "ms");
        }
    }

    @Override
    public void render() {
        if((System.nanoTime()-renderStart)/1000000f > 30){
            System.out.println("Long time since last render() call:"+(System.nanoTime()-renderStart)/1000000f);
        }
        renderStart = System.nanoTime();
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        doLogic();
        dataDialog.update();

        if(System.nanoTime()- logTwoSecondStarted >2*1000000000l){
            logTwoSecondStarted = System.nanoTime();
            twoSecondElapsed();
        }

        if(stateSnapshot !=null){
            if(showDebug) {
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
                shapeRenderer.setColor(1,1,1,1); //white
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
                shapeRenderer.setColor(1,1,1,1); //white
                for(Line2D.Float line:attackVisualsCopy){
                    shapeRenderer.line(line.x1, line.y1, line.x2, line.y2);
                }
                shapeRenderer.end();
            }
        }

        if(stateSnapshot!=null){
            Gdx.graphics.setTitle("FPS:" + Gdx.graphics.getFramesPerSecond()+" Ping:"+client.getReturnTripTime()+" Entities:"+stateSnapshot.size()+" InputRequest:"+currentInputRequest+" InputQueue:"+inputQueue.size()
            +" Interp:"+interpDelay+" InputUpdateRate:"+ inputUpdateRate +" ServerTime:"+lastServerTime+" ClientTime:"+(lastServerTime+((System.nanoTime()- lastAuthoritativeStateReceived)/1000000000f)));
        }else{
            Gdx.graphics.setTitle("FPS:" + Gdx.graphics.getFramesPerSecond());
        }
        //fpsLogger.log();

        if((System.nanoTime()-renderStart)/1000000f>5){
            System.out.println("Long Render() duration:" + (System.nanoTime() - renderStart) / 1000000f);
        }
    }

    @Override
    public boolean keyDown(int keycode) {
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
        if(character == 'q'){
            KryoSerialization s = (KryoSerialization) client.getSerialization();
            Entity e = new Entity(50,50);
            s.write(client, buffer ,e);
            System.out.println("Entity size is " + buffer.position() + " bytes");
            buffer.clear();

            //Integer i = new Integer(63);
            /*int i =  1000;
            s.write(client, buffer ,i);
            System.out.println("Integer size is " + buffer.position() + " bytes");
            buffer.clear();*/

            int totalEntitySize = 0;

            s.write(client, buffer ,e.heading);
            totalEntitySize += buffer.position();
            System.out.println("Heading size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.health);
            totalEntitySize += buffer.position();
            System.out.println("Health size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.maxHealth);
            totalEntitySize += buffer.position();
            System.out.println("Maxhealth size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.x);
            totalEntitySize += buffer.position();
            System.out.println("X size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.y);
            totalEntitySize += buffer.position();
            System.out.println("Y size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.height);
            totalEntitySize += buffer.position();
            System.out.println("Height size is " + buffer.position() + " bytes");
            buffer.clear();

            s.write(client, buffer ,e.width);
            totalEntitySize += buffer.position();
            System.out.println("Width size is " + buffer.position() + " bytes");
            buffer.clear();

            System.out.println("Total entity size is " +totalEntitySize + " bytes");
        }
        if(character == '0'){
            showDebug = !showDebug;
        }
        if(character == '+'){
            interpDelay += 0.05;
            System.out.println("Interpolation delay now:"+interpDelay);
        }
        if(character == '-'){
            interpDelay -= 0.05;
            if(interpDelay<0){
                interpDelay = 0;
            }
            System.out.println("Interpolation delay now:"+interpDelay);
        }
        if (character == '1') {
            if(inputUpdateRate <=1){
                inputUpdateRate /= 2;
            }else{
                inputUpdateRate--;
            }

        }
        if (character == '2') {
            if(inputUpdateRate <1){
                inputUpdateRate *= 2;
                if(inputUpdateRate > 1){
                    inputUpdateRate = 1;
                }
            }else{
                inputUpdateRate++;
            }
        }
        if (character == '3') {
            useClientSidePrediction = !useClientSidePrediction;
            System.out.println("UseClientSidePrediction:"+useClientSidePrediction);
        }
        if (character == '4') {
            compressInput = !compressInput;
            System.out.println("CompressInput:"+compressInput);
        }
        return false;
    }

    @Override
    public void resize(int width, int height) { }

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

    class DataDialog extends JFrame{

        JLabel runtimeLabel = new JLabel();

        JLabel UDPpackets = new JLabel();
        JLabel UDPbytes = new JLabel();
        //JLabel UDPpacketsS = new JLabel();
        //JLabel UDPbytesS = new JLabel();
        JLabel UDPpacketsPerSecondCurrent = new JLabel();
        JLabel UDPbytesPerSecondCurrent = new JLabel();
        JLabel averageUDPpacketSize = new JLabel();

        JLabel packets = new JLabel();
        JLabel bytes = new JLabel();
        //JLabel packetsS = new JLabel();
        //JLabel bytesS = new JLabel();
        JLabel packetsPerSecondCurrent = new JLabel();
        JLabel bytesPerSecondCurrent = new JLabel();
        JLabel averagePacketSize = new JLabel();

        public DataDialog(){

            setLayout(new GridLayout(0,1));
            add(runtimeLabel);

            add(UDPpackets);
            add(UDPbytes);
            //add(UDPpacketsS);
            //add(UDPbytesS);
            add(UDPpacketsPerSecondCurrent);
            add(UDPbytesPerSecondCurrent);
            add(averageUDPpacketSize);

            add(new JSeparator());

            add(packets);
            add(bytes);
            //add(packetsS);
            //add(bytesS);
            add(packetsPerSecondCurrent);
            add(bytesPerSecondCurrent);
            add(averagePacketSize);

            update();
            setVisible(true);
        }

        public void update(){
            long runtime = (System.nanoTime()-clientStartTime)/1000000000;
            if(runtime==0){
                return;
            }
            runtimeLabel.setText("Runtime:"+runtime);
            if(totalUDPPacketsSent>0) {
                UDPpackets.setText("UDP packets sent:" + totalUDPPacketsSent);
                UDPbytes.setText("UDP bytes sent:" + totalUDPBytesSent);
                //UDPpacketsS.setText("UDP packets/s sent:" + totalUDPPacketsSent / runtime);
                //UDPbytesS.setText("UDP bytes/s sent:" + totalUDPBytesSent / runtime);
                UDPpacketsPerSecondCurrent.setText("Current UDP packets/s sent:"+ currentUDPPacketsSent /2);
                UDPbytesPerSecondCurrent.setText("Current UDP bytes/s sent:"+ currentUDPBytesSent /2);
                averageUDPpacketSize.setText("Average UDP packet size:" + (currentUDPBytesSent / currentUDPPacketsSent));
            }

            /*
                        if(totalTCPPacketsSent>0){
                System.out.println("TCP Packets sent:"+totalTCPPacketsSent);
                System.out.println("TCP bytes sent:"+totalTCPBytesSent);
                System.out.println("TCP bytes/s sent:"+totalTCPBytesSent/runtime);
                System.out.println("TCP packets/s sent:"+totalTCPPacketsSent/runtime);
                System.out.println("Average TCP packet size:"+(totalTCPBytesSent/totalTCPPacketsSent));
            }
             */

            if(totalPacketsReceived>0) {
                packets.setText("Packets received:" + totalPacketsReceived);
                bytes.setText("Bytes received:" + totalBytesReceived);
                //packetsS.setText("Packets/s received:" + totalPacketsReceived / runtime);
                //bytesS.setText("Bytes/s received:" + totalBytesReceived / runtime);
                packetsPerSecondCurrent.setText("Current packets/s received:"+ currentPacketsReceived /2);
                bytesPerSecondCurrent.setText("Current bytes/s received:"+ currentBytesReceived /2);
                averagePacketSize.setText("Average received packet size:" + (currentBytesReceived / currentPacketsReceived));
            }
            pack();
        }
    }
}
