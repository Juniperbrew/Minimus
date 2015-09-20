package com.juniperbrew.minimus;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.EndPoint;
import com.juniperbrew.minimus.components.Component;
import com.juniperbrew.minimus.components.Health;
import com.juniperbrew.minimus.components.Rotation;
import com.juniperbrew.minimus.components.Team;

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
        kryo.register(RemoveEntity.class);
        kryo.register(NetworkEntity.class);
        kryo.register(Enums.Buttons.class);
        kryo.register(Enums.Heading.class);
        kryo.register(EnumSet.class);
        kryo.register(com.juniperbrew.minimus.components.Position.class);
        kryo.register(Health.class);
        kryo.register(EntityComponentsUpdate.class);
        kryo.register(PlayerList.class);
        kryo.register(AddPlayer.class);
        kryo.register(RemovePlayer.class);
        kryo.register(EntityAttacking.class);
        kryo.register(AddPlayerKill.class);
        kryo.register(AddNpcKill.class);
        kryo.register(AddDeath.class);
        kryo.register(Rotation.class);
        kryo.register(Team.class);
        kryo.register(FakePing.class);
        kryo.register(SendFile.class);
        kryo.register(FileReceived.class);
        kryo.register(byte[].class);
        kryo.register(WaveChanged.class);
        kryo.register(SpawnRequest.class);
        kryo.register(SetLives.class);
        kryo.register(Powerup.class);
        kryo.register(HealthPack.class);
        kryo.register(WeaponPickup.class);
        kryo.register(AmmoPickup.class);
        kryo.register(AddPowerup.class);
        kryo.register(RemovePowerup.class);
        kryo.register(Weapon.class);
        kryo.register(ProjectileDefinition.class);
        kryo.register(TeamChangeRequest.class);
        kryo.register(GameClockCompare.class);
        kryo.register(Color.class);
        kryo.register(MapChange.class);
        kryo.register(AddAmmo.class);
        kryo.register(WeaponAdded.class);
        kryo.register(Rectangle.class);
        kryo.register(SpawnProjectile.class);
        kryo.register(ChangeWeapon.class);
    }

    public static class WeaponAdded{
        public int weapon;
    }

    public static class AddAmmo{
        public String ammoType;
        public int amount;
    }

    public static class MapChange{
        public String mapName;
        public HashMap<Integer,Powerup> powerups;
    }

    public static class Message{
        public String text;
    }

    public static class WaveChanged{
        public int wave;
    }

    public static class GameClockCompare{
        public float serverTime;
    }

    public static class SpawnRequest{}

    public static class TeamChangeRequest{
        public int team;
    }

    public static class FakePing {
        public int id;
        public boolean isReply;
    }

    public static class UpdateRate{
        public int updateRate;
    }

    public static class SendFile{
        public String dateStamp;
        public String fileName;
        public byte[] data;
    }

    public static class FileReceived{
        public String fileName;
    }

    public static class EntityAttacking{
        public int id;
        public int weapon;
        public int deg;
        public float x;
        public float y;
        public HashMap<String,Float> projectileModifiers;
    }

    public static class AddPlayerKill {
        public int id;
    }
    public static class AddNpcKill {
        public int id;
    }
    public static class AddDeath {
        public int id;
    }

    public static class UserInput implements Comparable<UserInput>{
        public int inputID;
        public short msec;
        public EnumSet<Enums.Buttons> buttons = EnumSet.allOf(Enums.Buttons.class);
        public float mouseX;
        public float mouseY;

        @Override
        public int compareTo(UserInput o) {
            Integer me = inputID;
            Integer other = o.inputID;
            return me.compareTo(other);
        }
    }

    public static class UserInputs{
        public ArrayList<UserInput> inputs;
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
        public String mapName;
        public int lives;
        public int wave;
        public ArrayList<Integer> playerList;
        public HashMap<Integer,Powerup> powerups;
        public HashMap<Integer,Weapon> weaponList;
        public int primaryWeaponCount;
        public HashMap<String,ProjectileDefinition> projectileList;
        public HashMap<String,Integer> ammo;
        public HashMap<Integer,Boolean> weapons;
    }

    public static class AddEntity{
        public float serverTime;
        public NetworkEntity entity;
    }

    public static class RemoveEntity{
        public float serverTime;
        public int networkID;
    }

    public static class FullEntityUpdate implements Comparable<FullEntityUpdate>{
        public float serverTime;
        public int lastProcessedInputID;
        public HashMap<Integer,NetworkEntity> entities;

        @Override
        public int compareTo(FullEntityUpdate o) {
            Float me = serverTime;
            Float other = o.serverTime;
            return me.compareTo(other);
        }
    }

    public static class EntityPositionUpdate{
        public float serverTime;
        public int lastProcessedInputID;
        public HashMap<Integer,Position> changedEntityPositions;
    }

    public static class EntityComponentsUpdate{
        public float serverTime;
        public int lastProcessedInputID;
        public HashMap<Integer,ArrayList<Component>> changedEntityComponents;
    }

    public static class PlayerList{
        public ArrayList<Integer> playerList;
    }

    public static class AddPlayer{
        public int networkID;
    }

    public static class AddPowerup{
        public int networkID;
        public Powerup powerup;
    }

    public static class RemovePowerup{
        public int networkID;
    }

    public static class RemovePlayer{
        public int networkID;
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

    public static class SetLives{
        public int lives;
    }

    public static class SpawnProjectile{
        public String projectileName;
        public float x;
        public float y;
        public int ownerID;
        public int team;
    }

    public static class ChangeWeapon{
        public int weapon;
    }
}
