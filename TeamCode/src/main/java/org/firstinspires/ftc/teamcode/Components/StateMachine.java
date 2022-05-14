package org.firstinspires.ftc.teamcode.Components;

import static org.firstinspires.ftc.teamcode.Components.StateMachine.States.DROPPED;
import static org.firstinspires.ftc.teamcode.Components.StateMachine.States.INTAKE_DOWN;
import static org.firstinspires.ftc.teamcode.Components.StateMachine.States.IN_WAREHOUSE;
import static org.firstinspires.ftc.teamcode.Components.StateMachine.States.TURRET_STRAIGHT;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

public class StateMachine {
    boolean teleOp = false;
    public enum States {
        INTAKING(false),
        FLIPPING(false),
        SWITCHED(false),
        TRANSFERRING(false),
        INTAKE_DOWN(false),
        BASKET_TRANSFER(true),
        BASKET_CIELING(false),
        BASKET_DROP(false),
        BASKET_ARM_REST(true),
        EXTENDED(false),
        RAISED(false),
        TURRET_STRAIGHT(true),
        IN_WAREHOUSE(false),
        TRANSFERRED(false),
        DROPPED(false),
        TURRET_SHORT(true);
        boolean status;
        States(boolean value) {
            this.status = value;
        }

        public void setStatus(boolean status) {
            this.status = status;
        }
    }

    public StateMachine(LinearOpMode op, boolean isTeleOp) {
        States.values();
        if (isTeleOp) {
            INTAKE_DOWN.setStatus(false);
        }
        teleOp=isTeleOp;
    }

    public void setState(States state, boolean value) {
        state.setStatus(value);
    }
    public boolean getState(States state){return state.status;}
    public boolean checkIf(States state){
        if(state == States.INTAKING){
            return teleOp || IN_WAREHOUSE.status;
        }
        else if(state == States.FLIPPING){
            return TURRET_STRAIGHT.status && !States.EXTENDED.status && !States.RAISED.status && States.BASKET_TRANSFER.status;
        }
        else if(state == States.SWITCHED){
            return true;
        }
        else if(state == States.TRANSFERRING){
            return States.SWITCHED.status && TURRET_STRAIGHT.status && !States.EXTENDED.status && !States.RAISED.status && States.BASKET_TRANSFER.status && !States.INTAKE_DOWN.status;
        }
        else if(state == States.INTAKE_DOWN){
            return !States.SWITCHED.status;
        }
        else if(state == States.BASKET_CIELING){
            return States.BASKET_DROP.status && DROPPED.status  || States.BASKET_TRANSFER.status && States.TRANSFERRED.status;
        }
        else if(state == States.BASKET_ARM_REST){
            return States.DROPPED.status;
        }
        else if(state == States.BASKET_DROP){
            return !States.BASKET_ARM_REST.status&&States.TRANSFERRED.status;
        }
        else if(state == States.BASKET_TRANSFER){
            return States.BASKET_ARM_REST.status&&!States.EXTENDED.status;
        }
        else if(state == States.EXTENDED){
            return !States.BASKET_TRANSFER.status || !States.BASKET_ARM_REST.status;
        }
        else if(state == States.RAISED){
            return States.TRANSFERRED.status;
        }
        else{
            return true;
        }
    }
}
