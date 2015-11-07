import com.badlogic.gdx.Game;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.DefaultSerializers;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.minlog.Log;
import com.juniperbrew.minimus.Network;
import com.juniperbrew.minimus.NetworkEntity;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.server.ServerEntity;

import javax.xml.bind.SchemaOutputResolver;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;

/**
 * Created by Juniperbrew on 30.9.2015.
 */
public class KryoTester {

    private int unitsPerMeter = 512;
    private int positionBoundXY = 32;
    private int quantiziedPositionScalingXY = 1;
    private int quantiziedPositionBoundXY = quantiziedPositionScalingXY*unitsPerMeter*positionBoundXY-1;
    private int unitsPerCircle = 512;
    private int quantiziedRotationBound = unitsPerCircle - 1;

    int writeBufferSize = 5000;
    int readBufferSize = 5000;
    ByteBuffer writeBuffer = ByteBuffer.allocate(writeBufferSize);
    ByteBuffer readBuffer = ByteBuffer.allocate(readBufferSize);

    ByteBufferInput input = new ByteBufferInput();
    ByteBufferOutput output = new ByteBufferOutput();

    Kryo kryo = new Kryo();

    public KryoTester(){
        //Log.TRACE();
        Network.register(kryo);
        kryo.register(Position.class);
        kryo.register(Rotation.class);
        kryo.register(TestEntity.class,new EntitySerializer());
        kryo.setReferences(false);
        kryo.setRegistrationRequired(true);
        input.setBuffer(readBuffer);
        output.setBuffer(writeBuffer);
        //output.setVarIntsEnabled(false);
        //output.setOutputStream(System.out);
        //kryo.setReferences(false);
        //printRegistrations();

        //oldTests();

        GameState state1 = new GameState();
        GameState state2 = new GameState();
        state2.entities.get(0).x = 34;

        //state2.writeDeltaState(state1,kryo,output);
        state2.writeFullState(kryo,output);

        TestEntity e = new TestEntity();

        //serializeState(state);

        //serialize(e);
        System.out.println("Buffer total size: "+ output.position());
    }

    private void serializeState(ArrayList<TestEntity> state){
        for(TestEntity e : state){
            serialize(e);
        }
    }

    private void serialize(Object o){
        for(Field field : o.getClass().getDeclaredFields()){

            if(field.isAnnotationPresent(Networked.class)){
                System.out.println("Networked: " + field);
                Networked a = field.getAnnotation(Networked.class);
                Object value = null;
                try {
                    value = field.get(o);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    return;
                }
                if(a.toInt()){
                    writeObject(((Float) value).intValue());
                    //writeAndPrintObject(((Float) value).intValue());
                }else {
                    writeObject(value);
                    //writeAndPrintObject(value);
                }
            }else{
                System.out.println(field);
            }

        }
    }

    private int writeObject(Object o){
        System.out.println("Writing: "+o);
        int position = output.position();
        kryo.writeObject(output,o);
        printBuffer(output, position, output.position());
        System.out.println("Size: "+(output.position()-position));
        return output.position()-position;
    }

    private void updateClient(GameState newState, Connection c, Kryo kryo, ByteBufferOutput output){
        kryo.getContext().put("connection",c);

        kryo.getContext().put(c,newState);

    }

    private class GameState{
        public HashMap<Integer,TestEntity> entities;

        public GameState() {
            entities = new HashMap<>();
            for (int i = 0; i < 2; i++) {
                entities.put(i,new TestEntity());
            }
        }

        public void writeDeltaState(GameState previousState, Kryo kryo, ByteBufferOutput output){
            kryo.getContext().get("connection");
            for(int id : previousState.entities.keySet()){

            }
            kryo.getContext().put("state",this);
        }

        public void writeFullState(Kryo kryo, ByteBufferOutput output){
            for(TestEntity e : entities.values()){
                kryo.writeObject(output,e);
            }
        }
    }

    private class Position{
        public int x;
        public int y;

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private class Rotation{
        public int rotation;

        public Rotation(int rotation) {
            this.rotation = rotation;
        }
    }

    private class TestEntity{
        @Networked(type = Networked.DELTA, toInt = true) float x;
        @Networked(type = Networked.DELTA, toInt = true) float y;
        @Networked(type = Networked.DELTA, toInt = true) float rotation;

        @Networked(type = Networked.FULL) String image;

        double weaponCooldown;

        public TestEntity() {
            x = 50;
            y = 60;
            rotation = 78;
            image = "civilian";
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Networked {
        public final int DELTA = 1;
        public final int FULL = 2;
        boolean toInt() default false;
        int type();
    }

    public class GameStateSerializer extends Serializer<GameState>{

        @Override
        public void write(Kryo kryo, Output output, GameState state) {
            Connection c = (Connection) kryo.getContext().get("connection");
            GameState lastAckGameState = (GameState) kryo.getContext().get(c);
            kryo.writeObject(output,state);
        }

        @Override
        public GameState read(Kryo kryo, Input input, Class<GameState> type) {
            Connection c = (Connection) kryo.getContext().get("connection");
            GameState state = new GameState();
            kryo.getContext().put(c,state);
            return state;
        }
    }

    public class EntitySerializer extends Serializer<TestEntity> {

        @Override
        public void write(Kryo kryo, Output output, TestEntity e) {
            for(Field field : TestEntity.class.getDeclaredFields()){
                if(field.isAnnotationPresent(Networked.class)){
                    System.out.println("Writing networked: " + field);
                    Networked a = field.getAnnotation(Networked.class);
                    Object value = null;
                    try {
                        value = field.get(e);
                    } catch (IllegalAccessException e1) {
                        e1.printStackTrace();
                        return;
                    }
                    if(a.toInt()){
                        writeObject(((Float) value).intValue());
                    }else {
                        writeObject(value);
                    }
                }else{
                    System.out.println(field);
                }
            }
        }

        @Override
        public TestEntity read(Kryo kryo, Input input, Class<TestEntity> type) {
            TestEntity e = new TestEntity();
            for(Field field : type.getDeclaredFields()){
                if(field.isAnnotationPresent(Networked.class)){
                    System.out.println("Reading networked: " + field);
                    Networked a = field.getAnnotation(Networked.class);
                    try {
                        field.set(e, input.readInt());
                    } catch (IllegalAccessException e1) {
                        e1.printStackTrace();
                    }
                }else{
                    System.out.println(field);
                }
            }
            return e;
        }
    }

    public static void main(String[] args) {
        new KryoTester();
    }














    private void oldTests(){

        NetworkEntity e1 = new NetworkEntity(0,4,4,50,50,100,150,1,"ciavilian");
        NetworkEntity e2 = new NetworkEntity(0,7,4,51,50,101,151,2,"civilian");
        Field[] fields = NetworkEntity.class.getFields();
        benchMark(10, "compareFields(e1, e2, fields)", () -> compareFields(e1, e2, fields));
        benchMark(10, "compareFields(e1, e2, NetworkEntity.class.getFields())",() -> compareFields(e1, e2, NetworkEntity.class.getFields()));

        measureIndividualComponents();
        writeAndPrintClassAndObject(createFullComponentMap(1));
        writeAndPrintClassAndObject(createPartialComponentMap(1,true,false,false,true,false,false,false));

        NetworkEntity e = new NetworkEntity(0, quantiziedPositionBoundXY,50,5);
        e.rotation = 360;
        packEntityPosition(e);
    }

    private void benchMark(int loops, String function, Runnable runnable){
        for (int i = 0; i < loops; i++) {
            System.out.println("Testing function: " +function);
            long start = System.nanoTime();
            int counter = 0;
            while(System.nanoTime()-start < 1*1000000000l){
                runnable.run();
                counter++;
            }
            System.out.println("Counter: "+counter);
        }
    }

    private void printBuffer(ByteBufferOutput output, int from, int to){
        for (int i = from; i < to; i++) {
            byte b = output.toBytes()[i];
            System.out.println(b + " | " + Integer.toString(b, 2));
        }
    }

    private int printBuffer(ByteBufferOutput output){
        int size = output.position();
        System.out.println("Size: " + size);
        for (int i = 0; i < size; i++) {
            byte b = output.toBytes()[i];
            System.out.println(b + " | " + Integer.toString(b, 2));
        }
        System.out.println();
        return size;
    }

    private void measureIndividualComponents() {

        int size = 0;
        size += writeAndPrintClassAndObject(new Component.Position(50, 50));
        size += writeAndPrintClassAndObject(new Component.Rotation(100));
        size += writeAndPrintClassAndObject(new Component.Health(100));
        size += writeAndPrintClassAndObject(new Component.MaxHealth(150));
        size += writeAndPrintClassAndObject(new Component.Slot1(-8));
        size += writeAndPrintClassAndObject(new Component.Slot2(8));
        size += writeAndPrintClassAndObject(new Component.Team(5));

        size += writeAndPrintClassAndObject(new Position(50,50));
        size += writeAndPrintClassAndObject(new Rotation(100));
        HashMap map = new HashMap<Integer,ArrayList<Component>>();
        ArrayList<Component> list = new ArrayList<>();
        map.put(0, list);
        size += writeAndPrintClassAndObject(map);
        System.out.println("Total size: "+ size);
    }

    private int measureObject(Object o){
        kryo.writeObject(output, o);
        return output.position();
    }

    private HashMap<Integer,ArrayList<Component>> createFullComponentMap(int entityCount) {
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
            System.out.println(i);
        }
        return changedComponents;
    }

    private HashMap<Integer,ArrayList<Component>> createPartialComponentMap(int entityCount, boolean pos, boolean health, boolean maxHealth, boolean rotation, boolean team, boolean slot1, boolean slot2) {
        HashMap<Integer, ArrayList<Component>> changedComponents = new HashMap<>();
        for (int i = 0; i < entityCount; i++) {
            ArrayList<Component> components = new ArrayList<>();
            if(pos)components.add(new Component.Position(50, 50));
            if(health)components.add(new Component.Health(100));
            if(maxHealth)components.add(new Component.MaxHealth(150));
            if(rotation)components.add(new Component.Rotation(100));
            if(team)components.add(new Component.Team(2));
            if(slot1)components.add(new Component.Slot1(4));
            if(slot2)components.add(new Component.Slot2(8));
            changedComponents.put(i, components);
        }
        return changedComponents;
    }

    private HashMap<Integer,ArrayList<Component>> createSingleComponentMap(int entityCount, Component component) {
        HashMap<Integer, ArrayList<Component>> changedComponents = new HashMap<>();
        for (int i = 0; i < entityCount; i++) {
            ArrayList<Component> components = new ArrayList<>();
            components.add(component);
            changedComponents.put(i,components);
        }
        return changedComponents;
    }


    private void compareFields(Object o1, Object o2, Field[] fields){
        for(Field field : fields){
            try {
                if(!field.get(o1).equals(field.get(o2))){
                    //System.out.println(field.getName() + " changed.");
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private BitSet compare(NetworkEntity e1, NetworkEntity e2){
        BitSet changed = new BitSet();
        int changedFields = 0;
        BitSet delta = new BitSet();
        if(e1.x != e2.x || e1.y != e2.y){
            //System.out.println("Position changed");
            changedFields = changedFields | 1;
            changed.set(0);
        }
        if(e1.rotation != e2.rotation){
            //System.out.println("Rotation changed");
            changedFields = changedFields | 2;
            changed.set(1);
        }
        if(e1.health != e2.health){
            //System.out.println("Health changed");
            changedFields = changedFields | 4;
            changed.set(2);
        }
        if(e1.maxHealth != e2.maxHealth){
            //System.out.println("Max health changed");
            changedFields = changedFields | 8;
            changed.set(3);
        }
        if(e1.team != e2.team){
            //System.out.println("Team changed");
            changedFields = changedFields | 16;
            changed.set(4);
        }
        if(e1.width!=e2.width || e1.height != e2.height){
            //System.out.println("Size changed");
            changedFields = changedFields | 32;
            changed.set(5);
        }
        if(!e1.image.equals(e2.image)) {
            //System.out.println("Image changed");
            changedFields = changedFields | 64;
            changed.set(6);
        }

        //changedFields = changedFields | 512;
        //changedFields = changedFields | 1073741824;
        //changedFields =~ changedFields;
        //changedFields = changedFields | 2147483647;

        //System.out.println(Integer.toBinaryString(2147483647));

        //System.out.println(changedFields);
        int length = Integer.toBinaryString(changedFields).length();
        //System.out.println(Integer.toBinaryString(changedFields) + " Length:"+length);
        //printBitSet(changed, length);
        return changed;
    }

    private void printBitSet(BitSet bitSet, int length){
        for (int i = length-1; i >= 0; i--) {
            if(bitSet.get(i)){
                System.out.print("1");
            }else{
                System.out.print("0");
            }
        }
        System.out.println();
    }

    private byte[] packEntityPosition(NetworkEntity e){

        if(Math.abs(e.x)>quantiziedPositionBoundXY){
            System.out.println("POSITION X TOO LARGE");
            return null;
        }
        if(Math.abs(e.y)>quantiziedPositionBoundXY){
            System.out.println("POSITION X TOO LARGE");
            return null;
        }
        if(Math.abs(e.rotation)>quantiziedRotationBound){
            System.out.println("ROTATION TOO LARGE");
            return null;
        }
        int quantiziedX = (int)e.x;
        int quantiziedY = (int)e.y;
        int quantiziedRotation = Math.round((quantiziedRotationBound / 360f)*e.rotation);

        System.out.println("#Position");
        System.out.println("X:"+e.x);
        System.out.println("QuaniziedX:"+quantiziedX);
        System.out.println("Limit:"+quantiziedPositionBoundXY);
        System.out.println("Bits: "+Math.log(quantiziedPositionBoundXY+1)/Math.log(2));
        System.out.println("Value: "+Integer.toBinaryString(quantiziedX) + " Length:"+Integer.toBinaryString(quantiziedX).length());
        System.out.println();
        System.out.println("#Rotation");
        System.out.println("Rotation:"+e.rotation);
        System.out.println("Quantizied rotation:"+quantiziedRotation);
        System.out.println("Limit:"+quantiziedRotationBound);
        System.out.println("Bits: "+Math.log(quantiziedRotationBound+1)/Math.log(2));
        System.out.println("Value: "+Integer.toBinaryString(quantiziedRotation) + " Length:"+Integer.toBinaryString(quantiziedRotation).length());
/*
        int intX = Float.floatToIntBits(x);
        int intY = Float.floatToIntBits(y);
        System.out.println(Float.intBitsToFloat(intX));
        System.out.println(Float.intBitsToFloat(intY));
        System.out.println(intX);
        System.out.println(intY);
        BitSet bitSet = new BitSet();*/
        return null;
    }

    /*private int quantisizeValue(float value, int bounds){
        System.out.println("Value:"+value);
        System.out.println("QuaniziedX:"+quantiziedX);
        System.out.println("Limit:"+bounds);
        System.out.println("Bits: "+Math.log(bounds+1)/Math.log(2));
        System.out.println("Value: "+Integer.toBinaryString(quantiziedX) + " Length:"+Integer.toBinaryString(quantiziedX).length());
    }*/

    private int writeAndPrintObject(Object o){
        kryo.writeObject(output, o);
        int size = printBuffer(output);
        output.clear();
        return size;
    }

    private int writeAndPrintClassAndObject(Object o){
        kryo.writeClassAndObject(output, o);
        int size = printBuffer(output);
        output.clear();
        return size;
    }

    private ArrayList<NetworkEntity> createEntityList(){
        ArrayList<NetworkEntity> entities = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            entities.add(new NetworkEntity());
        }
        return entities;
    }

    private HashMap<Integer,NetworkEntity> createEntityMap(){
        HashMap<Integer, NetworkEntity> entities = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            entities.put(i, new NetworkEntity());
        }
        return entities;
    }

    private void printRegistrations(){
        for (int i = 0; i < kryo.getNextRegistrationId(); i++) {
            Registration r = kryo.getRegistration(i);
            System.out.print(r);
            System.out.println(" " + r.getSerializer());
        }
    }
}
