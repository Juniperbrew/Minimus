package com.juniperbrew.minimus.components;

/**
 * Created by Juniperbrew on 22.6.2015.
 */
public class Component {

    public static class Health extends Component {
        public int health;

        public Health(){}

        public Health(int health){
            this.health = health;
        }
    }

    public static class MaxHealth extends Component {
        public int health;

        public MaxHealth(){}

        public MaxHealth(int health){
            this.health = health;
        }
    }

    public static class Position extends Component {
        public float x;
        public float y;

        public Position(){}

        public Position(float x, float y){
            this.x = x;
            this.y = y;
        }
    }

    public static class Rotation extends Component {
        public float degrees;

        public Rotation(){}

        public Rotation(float degrees){
            this.degrees = degrees;
        }
    }

    public static class Slot1 extends Component{
        public int weaponID;

        public Slot1() {
        }

        public Slot1(int weaponID) {

            this.weaponID = weaponID;
        }
    }

    public static class Slot2 extends Component {
        public int weaponID;

        public Slot2() {
        }

        public Slot2(int weaponID) {

            this.weaponID = weaponID;
        }
    }

    public static class Team extends Component{

        public int team;

        public Team(){}

        public Team(int team){
            this.team = team;
        }

    }

}
