import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;

import javax.jws.soap.SOAPBinding;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;

/**
 * Created by Juniperbrew on 23.1.2015.
 */
public class Network {

    static public final int portTCP = 54555;
    static public final int portUDP = 54556;

    static public void register (EndPoint endPoint) {
        Kryo kryo = endPoint.getKryo();
        kryo.register(Message.class);
        kryo.register(UserInput.class);
        kryo.register(UpdateRate.class);
        kryo.register(EntityPositionUpdate.class);
        kryo.register(Position.class);
        kryo.register(HashMap.class);
        kryo.register(AssignEntity.class);
        kryo.register(TestPacket.class);
        kryo.register(ArrayList.class);
        kryo.register(UserInputs.class);
        kryo.register(FullEntityUpdate.class);
        kryo.register(AddEntity.class);
        kryo.register(Entity.class);
        kryo.register(Enums.Buttons.class);
        kryo.register(Enums.Heading.class);
        kryo.register(EnumSet.class);
    }

    public static class Message{
        public String text;
    }

    public static class UpdateRate{
        public int updateRate;
    }

    public static class UserInput implements Comparable<UserInput>{
        public int inputID;
        public short msec;
        EnumSet<Enums.Buttons> buttons = EnumSet.allOf(Enums.Buttons.class);

        @Override
        public int compareTo(UserInput o) {
            Integer me = inputID;
            Integer other = o.inputID;
            return me.compareTo(other);
        }

    }

    public static class UserInputs{
        ArrayList<UserInput> inputs;
    }

    public static class TestPacket{
        public int inputID;
        public short msec;
        public boolean up;
        public boolean down;
        public boolean left;
        public boolean right;
    }

    public static class AssignEntity{
        public int networkID;
        public float velocity;
        public int mapHeight;
        public int mapWidth;
    }

    public static class AddEntity{
        public int networkID;
        public Entity entity;
    }

    public static class FullEntityUpdate implements Comparable<FullEntityUpdate>{
        public float serverTime;
        public int lastProcessedInputID;
        public HashMap<Integer,Entity> entities;

        @Override
        public int compareTo(FullEntityUpdate o) {
            Float me = serverTime;
            Float other = o.serverTime;
            return me.compareTo(other);
        }
    }

    public static class EntityPositionUpdate implements Comparable<EntityPositionUpdate>{
        public float serverTime;
        public int lastProcessedInputID;
        public HashMap<Integer,Position> changedEntityPositions;

        @Override
        public int compareTo(EntityPositionUpdate o) {
            Float me = serverTime;
            Float other = o.serverTime;
            return me.compareTo(other);
        }
    }

    public static class Position{
        public float x;
        public float y;

        public Position(){}

        public Position(float x, float y){
            this.x = x;
            this.y = y;
        }
    }
}
