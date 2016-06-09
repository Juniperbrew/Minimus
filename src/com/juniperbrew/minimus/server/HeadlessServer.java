package com.juniperbrew.minimus.server;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.juniperbrew.minimus.*;
import com.juniperbrew.minimus.Console;
import com.juniperbrew.minimus.windows.StatusData;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Juniperbrew on 18/05/16.
 */
public class HeadlessServer implements Game.WorldChangeListener, Console.ServerCommands, Score.ScoreChangeListener, ConVars.ConVarChangeListener{

    boolean running = true;

    Server server;
    private int writeBuffer = 16384; //Default 16384
    private int objectBuffer = 4096; //Default 2048

    ByteBuffer buffer = ByteBuffer.allocate(objectBuffer); //For calculating packet sizes

    StatusData serverData;
    private long lastPingUpdate;
    HashMap<Connection,Integer> lastInputIDProcessed = new HashMap<>();
    long lastUpdateSent;
    HashMap<Connection, StatusData> connectionStatus = new HashMap<>();
    HashMap<Connection, ArrayList<Network.UserInput>> inputQueue = new HashMap<>();
    ConcurrentLinkedQueue<Connection> connections = new ConcurrentLinkedQueue<>();

    Queue<Float> fpsLog = new CircularFifoQueue<>(100);
    Queue<Float> logicLog = new CircularFifoQueue<>(100);

    private ConcurrentLinkedQueue<Packet> pendingPackets = new ConcurrentLinkedQueue<>();

    BidiMap<Integer, Connection> playerList = new DualHashBidiMap<>();

    private int currentTick = 0;
    long serverStartTime;

    Score score = new Score(this);

    int pendingRandomNpcAdds = 0;
    int pendingRandomNpcRemovals = 0;
    boolean set200Entities;

    Game game;

    public HeadlessServer(){
        G.console = new Console(this);
        ConVars.addListener(this);
        game = new Game(this);
        startServer();
        Scanner scanner = new Scanner(System.in);
        new Thread(() -> {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            while(running){
                try {
                    String s =  br.readLine();
                    System.out.println("FOUND LINE: "+s);
                    G.console.giveCommand(s);
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }}).start();
    }

    private void startServer(){
        serverStartTime = System.nanoTime();
        serverData = new StatusData(serverStartTime,ConVars.getInt("cl_log_interval_seconds"));
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
                StatusData dataUsage = new StatusData(connection, System.nanoTime(), ConVars.getInt("cl_log_interval_seconds"));
                connection.setTimeout(G.TIMEOUT);
                connectionStatus.put(connection, dataUsage);
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
        startSimulation();
    }

    private void startSimulation(){
        Thread thread = new Thread(new Runnable(){
            @Override
            public void run() {

                long targetTickDurationNano, tickStartTime = 0,tickEndTime = 0,tickActualDuration = 0, tickWorkDuration, sleepDuration;
                while(running){
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
        game.updateWorld(delta);

        if(System.nanoTime()-lastUpdateSent>(1000000000/ConVars.getInt("sv_updaterate"))){
            updateClients();
            lastUpdateSent = System.nanoTime();
        }
        logicLog.add(Tools.nanoToMilliFloat(System.nanoTime() - logicStart));
    }

    private void updateClients(){
        if(ConVars.getInt("sv_update_type")==0){
            Network.FullEntityUpdate update = new Network.FullEntityUpdate();
            update.serverTime = getServerTime();
            update.entities = game.getNetworkedEntities();
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
            update.changedEntityPositions = game.getChangedEntityPositions();
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
            update.changedEntityComponents = game.getChangedEntityComponents();
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

    private void updateStatusData(){
        for(Connection c:inputQueue.keySet()){
            connectionStatus.get(c).inputQueue = inputQueue.get(c).size();
        }
        //serverData.fps = Gdx.graphics.getFramesPerSecond();
        fpsLog.add((float) serverData.fps);
        serverData.setEntityCount(game.getEntityCount());
        serverData.currentTick = currentTick;
        serverData.setServerTime((System.nanoTime() - serverStartTime) / 1000000000f);
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

    public void updateFakePing(Connection connection) {
        StatusData status = connectionStatus.get(connection);
        if(status != null){
            Network.FakePing ping = new Network.FakePing();
            ping.id = status.lastPingID++;
            status.lastPingSendTime = System.currentTimeMillis();
            sendTCP(connection,ping);
        }
    }

    private void processPendingEntityAddsAndRemovals(){
        for (int i = 0; i < pendingRandomNpcAdds; i++) {
            game.addRandomNPC();
        }
        pendingRandomNpcAdds = 0;

        if(set200Entities){
            while(game.entities.size()<200){
                game.addRandomNPC();
            }
            set200Entities = false;
        }

        for (int i = 0; i < pendingRandomNpcRemovals; i++) {
            game.removeRandomNPC();
        }
        pendingRandomNpcRemovals = 0;
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
                game.processInput((PlayerServerEntity) game.entities.get(id), input);
                lastInputIDProcessed.put(connection,input.inputID);
            }
            inputList.clear();
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
            showMessage("Got spawnrequest from "+connection);
            if(game.playerList.contains(playerList.getKey(connection))){
                game.removePlayer(playerList.getKey(connection));
            }
            game.addPlayer(connection);
        }else if (object instanceof Network.TeamChangeRequest){
            Network.TeamChangeRequest teamChangeRequest = (Network.TeamChangeRequest) object;
            game.getEntity(playerList.getKey(connection)).setTeam(teamChangeRequest.team);
        }else if (object instanceof Network.ChangeWeapon){
            Network.ChangeWeapon changeWeapon = (Network.ChangeWeapon) object;
            game.setPlayerWeapon(playerList.getKey(connection), changeWeapon.weapon);
        }else if(object instanceof Disconnect){
            if(connectionStatus.containsKey(connection)){
                connectionStatus.get(connection).disconnected();
            }else{
                showMessage(connection + " disconnected but is not in the connectionlist");
            }
            if(playerList.containsValue(connection)) {
                game.removePlayer(playerList.getKey(connection));
            }
            connections.remove(connection);
        }else if(object instanceof Network.GameClockCompare){
            Network.GameClockCompare gameClockCompare = (Network.GameClockCompare) object;
            showMessage("Received gameClockCompare("+playerList.getKey(connection)+"): "+gameClockCompare.serverTime + " Delta: "+(gameClockCompare.serverTime-getServerTime()));
        }else if(object instanceof Network.BuyItem){
            Network.BuyItem buyItem = (Network.BuyItem) object;
            showMessage("Player "+playerList.getKey(connection)+" buying "+buyItem.amount+" of item "+G.shoplist.get(buyItem.id)+"("+buyItem.id+")");
            game.buyItem(playerList.getKey(connection),buyItem.id,buyItem.amount);
        }else if(object instanceof Network.SellItem){
            Network.SellItem sellItem = (Network.SellItem) object;
            showMessage("Player "+playerList.getKey(connection)+" selling "+sellItem.amount+" of item "+G.shoplist.get(sellItem.id)+"("+sellItem.id+")");
            game.sellItem(playerList.getKey(connection),sellItem.id,sellItem.amount);
        }else if(object instanceof Network.CompleteQuest){
            showMessage("Player "+playerList.getKey(connection)+" completed the quest.");
            game.completeQuest();
            //Confirm quest completion for all clients
            sendTCPtoAll(object);
        }
    }

    private void sendFullStateUpdate(Connection c){
        Network.FullEntityUpdate fullUpdate = new Network.FullEntityUpdate();
        fullUpdate.entities = game.getNetworkedEntities();
        fullUpdate.serverTime = getServerTime();
        c.sendTCP(fullUpdate);
    }

    private void receiveFile(Connection c, Network.SendFile sendFile){
        String fileName = sendFile.fileName;
        byte[] data = sendFile.data;
        String dateStamp = sendFile.dateStamp;
        showMessage("Received file: " + fileName);
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

    private float getServerTime(){
        return ((System.nanoTime()-serverStartTime)/1000000000f);
    }

    private void logReceivedPackets(Connection connection, Object packet){
        KryoSerialization s = (KryoSerialization) server.getSerialization();
        s.write(connection, buffer ,packet);
        connectionStatus.get(connection).addBytesReceived(buffer.position());
        serverData.addBytesReceived(buffer.position());
        buffer.clear();
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

    public void showMessage(String message){
        if(message.startsWith("\n")){
            System.out.println();
            message = message.substring(1);
        }
        String line = "["+Tools.secondsToMilliTimestamp(getServerTime())+ "] " + message;
        System.out.println(line);
    }

    @Override
    public void conVarChanged(String varName, String varValue) {
        if(varName.equals("sv_map")){
            if(game !=null){
                game.changeMap(varValue);
            }
        }
        if(varName.equals("sv_campaign")){
            if(game !=null){
                game.loadCampaign(varValue);
            }
        }
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
    public void reassignPlayers() {
        for(Connection c : playerList.values().toArray(new Connection[playerList.values().size()])){
            game.addPlayer(c);
        }
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
    public void sendCommand(String command) {

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

    @Override
    public void giveAllWeapons(int id){
        for(int weaponID : G.weaponList.keySet()){
            giveWeapon(weaponID, id);
        }
    }

    //Maybe add this to console commands too
    public void giveWeapon(int weaponID, int id){
        PlayerServerEntity player = (PlayerServerEntity) game.entities.get(id);
        player.setWeapon(weaponID, true);
    }

    @Override
    public void addAmmo(int id, String ammoType, int amount){
        PlayerServerEntity player = (PlayerServerEntity) game.entities.get(id);
        player.changeAmmo(ammoType, amount);
        Network.AmmoUpdate ammoUpdate = new Network.AmmoUpdate();
        ammoUpdate.ammoType = ammoType;
        ammoUpdate.amount = amount;
        sendTCP(playerList.get(id), ammoUpdate);
    }

    @Override
    public void startWaves(){
        game.spawnWaves = true;
    }

    @Override
    public void stopWaves(){
        game.spawnWaves = false;
    }

    @Override
    public void resetWaves(){
        game.removeAllNpcs();
        game.spawnWaves = false;
        game.setWave(0);
    }

    @Override
    public void scoreChanged() {

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

    public static void main(String[] args) {
        new HeadlessServer();
    }
}
