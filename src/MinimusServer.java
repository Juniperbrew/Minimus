import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

import java.awt.geom.Line2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class MinimusServer implements ApplicationListener, InputProcessor {

    Server server;
    ShapeRenderer shapeRenderer;
    HashMap<Integer,Entity> entities;
    HashMap<Integer,Entity> npcs;
    HashMap<Connection,Integer> playerList;
    HashMap<Connection,Integer> lastInputIDProcessed;
    HashMap<Connection,Integer> connectionUpdateRate;
    HashMap<Connection, ArrayList<Network.UserInput>> inputQueue;

    private int networkIDCounter = 1;

    final float velocity = 0.1f;  //pix/ms
    float tickRate=60;
    float updateRate=2;

    private int currentTick=0;

    long serverStartTime;

    boolean useUDP = true;

    int screenW;
    int screenH;

    private int writeBuffer = 16384; //Default 16384
    private int objectBuffer = 4096; //Default 2048
    private long lastPingUpdate;

    HashMap<Connection,Long> lastAttackDone;
    private ArrayList<Line2D.Float> attackVisuals;
    final long ATTACK_DELAY = (long) (0.3*1000000000);
    Timer timer;

    long totalUDPBytesSent;
    int totalUDPPacketsSent;
    long totalTCPBytesSent;
    int totalTCPPacketsSent;

    private void initialize(){
        shapeRenderer = new ShapeRenderer();
        entities = new HashMap<Integer, Entity>();
        npcs = new HashMap<Integer, Entity>();
        playerList = new HashMap<Connection, Integer>();
        connectionUpdateRate = new HashMap<Connection, Integer>();
        inputQueue = new HashMap<Connection, ArrayList<Network.UserInput>>();
        lastInputIDProcessed = new HashMap<Connection, Integer>();
        lastAttackDone = new HashMap<Connection, Long>();
        attackVisuals = new ArrayList<Line2D.Float>();
        timer = new Timer();
        screenW = Gdx.graphics.getWidth();
        screenH = Gdx.graphics.getHeight();

        for (int i = 0; i < 1; i++) {
            addNPC();
        }
    }

    @Override
    public void create() {
        serverStartTime = System.nanoTime();
        startServer();
        initialize();
        startSimulation();
        Gdx.input.setInputProcessor(this);
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
                Entity newPlayer = new Entity(100,100);
                int networkID = getNextNetworkID();
                entities.put(networkID, newPlayer);
                playerList.put(connection,networkID);

                Network.AssignEntity assign = new Network.AssignEntity();
                assign.networkID = networkID;
                assign.velocity = velocity;
                connection.sendTCP(assign);

                Network.FullEntityUpdate fullUpdate = new Network.FullEntityUpdate();
                fullUpdate.entities = entities;
                connection.sendTCP(fullUpdate);

            }

            @Override
            public void disconnected(Connection connection){
                entities.remove(playerList.get(connection));
                playerList.remove(connection);
            }

            public void received (Connection connection, Object object) {
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
            Entity e = entities.get(playerList.get(connection));
            Network.UserInput[] inputListCopy = inputList.toArray(new Network.UserInput[inputList.size()]);

            for(Network.UserInput input : inputListCopy){
                //System.out.println("Processing input with ID:"+input.inputID+" duration:"+input.msec);
                moveEntity(e,input);
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
            System.out.println("Entity ID"+playerList.get(connection)+" is attacking");
            Entity e = entities.get(playerList.get(connection));
            System.out.println();
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
            ArrayList<Integer> deadEntities = new ArrayList<Integer>();
            for(int id:entities.keySet()){
                Entity target = entities.get(id);
                if(target.getBounds().intersectsLine(hitScan) && target != e){
                    target.health -= 10;
                    System.out.println("Entity ID"+id+" now has "+target.health+" health.");
                    if(target.health<=0){
                        System.out.println("Entity ID"+id+" is dead.");
                        deadEntities.add(id);
                    }
                }
            }
            for(int id: deadEntities){
                if(playerList.values().contains(id)){
                    //Respawn dead player
                    Entity deadPlayer = entities.get(id);
                    deadPlayer.health = deadPlayer.maxHealth;
                    deadPlayer.x = 0;
                    deadPlayer.y = 0;
                }else{
                    entities.remove(id);
                    npcs.remove(id);
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

    private void updateClients(){
        float serverTime = getServerTime();
        Network.FullEntityUpdate update = new Network.FullEntityUpdate();
        update.entities = entities;
        update.serverTime = serverTime;
        for(Connection connection :playerList.keySet()){
            if(lastInputIDProcessed.get(connection) != null){
                update.lastProcessedInputID = lastInputIDProcessed.get(connection);
            }else{
                update.lastProcessedInputID = -1;
            }
            if(useUDP){
                connection.sendUDP(update);
            }else{
                connection.sendTCP(update);
            }
        }
    }

    private void addNPC(){
        int width = 50;
        int height = 50;
        float x = MathUtils.random(Gdx.graphics.getWidth()-width);
        float y = MathUtils.random(Gdx.graphics.getHeight()-height);
        Entity npc = new Entity(x,y);
        npc.height = height;
        npc.width = width;
        npc.health = 90;
        int randomHeading = MathUtils.random(Enums.Heading.values().length - 1);
        npc.heading = Enums.Heading.values()[randomHeading];
        int networkID = getNextNetworkID();
        entities.put(networkID,npc);
        npcs.put(networkID,npc);
        System.out.println("Adding npc ID:"+networkID);

        Network.AddEntity addEntity = new Network.AddEntity();
        addEntity.networkID=networkID;
        addEntity.entity=npc;
        server.sendToAllTCP(addEntity);
    }

    private void removeNPC(){
        Integer[] keys = npcs.keySet().toArray(new Integer[npcs.keySet().size()]);
        if(keys.length != 0){
            int removeIndex = MathUtils.random(keys.length-1);
            System.out.println("Remove index:"+removeIndex);
            int removeID = keys[removeIndex];
            System.out.println("Removing npc ID:"+removeID);
            npcs.remove(removeID);
            entities.remove(removeID);
        }
    }

    private void moveNPC(float delta){
        Entity[] npcsCopy = npcs.values().toArray(new Entity[npcs.values().size()]);
        for(Entity e:npcsCopy){
            float x = e.x;
            float y = e.y;
            int width = e.width;
            int height = e.height;

            float newX = x;
            float newY = y;

            switch(e.heading){
                case NORTH: newY += velocity*delta; break;
                case SOUTH: newY -= velocity*delta; break;
                case WEST: newX -= velocity*delta; break;
                case EAST: newX += velocity*delta; break;
            }
            if(newX+width > screenW && e.heading==Enums.Heading.EAST){
                e.heading = Enums.Heading.WEST;
                newX = screenW-width;
            }
            if(newX<0 && e.heading== Enums.Heading.WEST){
                e.heading = Enums.Heading.EAST;
                newX = 0;
            }
            if(newY+height>screenH && e.heading== Enums.Heading.NORTH){
                e.heading = Enums.Heading.SOUTH;
                newY = screenH-height;
            }
            if(newY<0 && e.heading== Enums.Heading.SOUTH){
                e.heading = Enums.Heading.NORTH;
                newY = 0;
            }

            e.x = newX;
            e.y = newY;
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
        e.x += deltaX;
        e.y += deltaY;
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
                    targetTickDurationNano= (long) (1000000000/tickRate);
                    if(tickEndTime>0){
                        tickActualDuration=tickEndTime-tickStartTime;
                    }
                    tickStartTime=System.nanoTime();
                    currentTick++;

                    /*
                    if(System.nanoTime()-lastPingUpdate>10*1000000000){
                        for(Connection c:server.getConnections()){
                            c.updateReturnTripTime();
                        }
                        lastPingUpdate = System.nanoTime();
                    }
                    */

                    processCommands();
                    float processCommandsCheckpoint = (System.nanoTime()-tickStartTime)/1000000f;

                    moveNPC(tickActualDuration/1000000f);
                    float moveNpcCheckpoint = (System.nanoTime()-tickStartTime)/1000000f;

                    if(System.nanoTime()-lastUpdateSent>(1000000000/updateRate)){
                        updateClients();
                        lastUpdateSent = System.nanoTime();
                    }
                    float updateClientsCheckpoint = (System.nanoTime()-tickStartTime)/1000000f;

                    tickWorkDuration=System.nanoTime()-tickStartTime;
                    if((tickWorkDuration/1000000f) > 20) {
                        System.out.println("Tick:"+currentTick);
                        System.out.println("Process commands:" + processCommandsCheckpoint);
                        System.out.println("MoveNPC:" + (moveNpcCheckpoint - processCommandsCheckpoint));
                        System.out.println("UpdateClients:" + (updateClientsCheckpoint - moveNpcCheckpoint));
                        System.out.println("Work:" + (tickWorkDuration / 1000000f));
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
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for(Entity e: entities.values()){
            shapeRenderer.rect(e.x, e.y, e.width, e.height);
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

        updateTitle();
    }

    private void updateTitle(){
        StringBuilder inputQueueString = new StringBuilder("[");
        int i = 0;
        for(Connection c:inputQueue.keySet()){
            inputQueueString.append(i+":"+inputQueue.get(c).size()+" ");
            i++;
        }
        inputQueueString.deleteCharAt(inputQueueString.length()-1);
        inputQueueString.append("]");

        StringBuilder pingString = new StringBuilder("[");
        i = 0;
        for(Connection c:server.getConnections()){
            pingString.append(i+":"+c.getReturnTripTime()+" ");
            i++;
        }
        pingString.deleteCharAt(pingString.length()-1);
        pingString.append("]");
        Gdx.graphics.setTitle(
                "FPS:"+Gdx.graphics.getFramesPerSecond()
                        +" Pings"+pingString
                        +" Entities:"+entities.size()
                        +" TickRate:"+tickRate
                        +" UpdateRate:"+updateRate
                        +" Tick:"+currentTick
                        +" InputQueue:" +inputQueueString
                        +" Time:"+((System.nanoTime()-serverStartTime)/1000000000f));
    }

    @Override
    public boolean keyTyped(char character) {
        if (character == 'u') {
            useUDP = !useUDP;
            System.out.println("UseUDP:"+useUDP);
        }
        if (character == '-') {
            removeNPC();
        }
        if (character == '+') {
            addNPC();
        }
        if (character == '1') {
            if(tickRate <=1){
                tickRate /= 2;
            }else{
                tickRate--;
            }
        }
        if (character == '2') {
            if(tickRate <1){
                tickRate *= 2;
                if(tickRate > 1){
                    tickRate = 1;
                }
            }else{
                tickRate++;
            }
        }
        if (character == '8') {
            if(updateRate <=1){
                updateRate /= 2;
            }else{
                updateRate--;
            }

        }
        if (character == '9') {
            if(updateRate <1){
                updateRate *= 2;
                if(updateRate > 1){
                    updateRate = 1;
                }
            }else{
                updateRate++;
            }
        }
        return false;
    }

    @Override
    public void resize(int width, int height) {
    }
    @Override
    public void pause() {}
    @Override
    public void resume() {}
    @Override
    public void dispose() {}

    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
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
}
