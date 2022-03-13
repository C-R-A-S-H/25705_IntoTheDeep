package org.firstinspires.ftc.teamcode.robots.reachRefactor.subsystem;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.canvas.Canvas;
import com.acmerobotics.dashboard.config.Config;
import com.qualcomm.robotcore.hardware.DistanceSensor;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.Range;

import java.util.LinkedHashMap;
import java.util.Map;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.teamcode.robots.reachRefactor.simulation.DistanceSensorSim;
import org.firstinspires.ftc.teamcode.robots.reachRefactor.simulation.ServoSim;

import static org.firstinspires.ftc.teamcode.robots.reachRefactor.util.Constants.ELBOW_TO_WRIST;
import static org.firstinspires.ftc.teamcode.robots.reachRefactor.util.Constants.HIGH_TIER_SHIPPING_HUB_HEIGHT;
import static org.firstinspires.ftc.teamcode.robots.reachRefactor.util.Constants.SHOULDER_AXLE_TO_GROUND_HEIGHT;
import static org.firstinspires.ftc.teamcode.robots.reachRefactor.util.Constants.SHOULDER_TO_ELBOW;
import static org.firstinspires.ftc.teamcode.robots.reachRefactor.util.Utils.*;

import org.firstinspires.ftc.teamcode.statemachine.Stage;
import org.firstinspires.ftc.teamcode.statemachine.StateMachine;

@Config(value = "FFCrane")
public class Crane implements Subsystem {
    public static int SHOULDER_HOME_PWM = 1550;
    public static int ELBOW_HOME_PWM = 1500;
    public static int WRIST_HOME_PWM = 1500;

    public static double SHOULDER_PWM_PER_DEGREE = 600.0 / 90.0;
    public static double ELBOW_PWM_PER_DEGREE = -600.0 / 90.0;
    public static double WRIST_PWM_PER_DEGREE = 750.0 / 180.0;

    public static double SHOULDER_DEG_MIN = -90; // negative angles are counter clockwise while looking at the left side
                                                 // of the robot
    public static double ELBOW_DEG_MIN = -80;
    public static double WRIST_DEG_MIN = -180;

    public static double SHOULDER_DEG_MAX = 90;
    public static double ELBOW_DEG_MAX = 140;
    public static double WRIST_DEG_MAX = 180;

    public static double P = 0.995;
    public static double BUCKET_TRIGGER_DISTANCE = 5;

    public Turret turret;

    public Servo shoulderServo, elbowServo, wristServo;
    private final DistanceSensor bucketDistanceSensor;

    private double bucketDistance;
    private double shoulderTargetAngle, elbowTargetAngle, wristTargetAngle;
    private boolean dumping;
    private boolean toHomeEnabled;

    private Articulation articulation;

    public Crane(HardwareMap hardwareMap, Turret turret, boolean simulated) {
        if (simulated) {
            shoulderServo = new ServoSim();
            elbowServo = new ServoSim();
            wristServo = new ServoSim();
            bucketDistanceSensor = new DistanceSensorSim(100);
        } else {
            shoulderServo = hardwareMap.get(Servo.class, "firstLinkServo");
            elbowServo = hardwareMap.get(Servo.class, "secondLinkServo");
            wristServo = hardwareMap.get(Servo.class, "bucketServo");
            bucketDistanceSensor = hardwareMap.get(DistanceSensor.class, "distBucket");
        }

        this.turret = turret;
        articulation = Articulation.MANUAL;
        toHomeEnabled = true;
    }

    public enum Articulation {
        TEST_INIT(0, 0, 0, 0, 5, 0),
        MANUAL(0, 0, 0, 0, 0, 0),

        INIT(-90, 0, 90, 0, 1.5f, 90),
        HOME(0, 0, 0, 0, 0, 0),

        LOWEST_TIER(75, 130, 20, 1.5f, 130),
        MIDDLE_TIER(60, 130, 40, 1f, 150),
//        HIGH_TIER(14.57741692662239, 113, 50.37986606359482, 1f, 170),
//        HIGH_TIER_LEFT(14.57741692662239, 113, 50.37986606359482, -90, 1f, 180),
//        HIGH_TIER_RIGHT(14.57741692662239, 113, 50.37986606359482, 90, 1f, 170),
        HIGH_TIER(15, HIGH_TIER_SHIPPING_HUB_HEIGHT + 5, 1.25f),
        HIGH_TIER_LEFT(15, HIGH_TIER_SHIPPING_HUB_HEIGHT + 5, -90, 1.25f),
        HIGH_TIER_RIGHT(15, HIGH_TIER_SHIPPING_HUB_HEIGHT + 5, 90, 1.25f),
        TRANSFER(-45, -50, -20, 0, 0.4f, 0),

        TEST_1(25, 90, 25, 1.5f, 0),
        TEST_2(45, 90, 90, 1f, 0),
        TEST_3(65, 90, 135, 1f, 0),

        AUTON_LOWEST_TIER(45.957, 47.5, 44.253, 1.5f, 110),
        AUTON_MIDDLE_TIER(29.9, 71.69, 55, 1f, 120),
        AUTON_HIGH_TIER(22.07, 110, 62.1226, 1f, 180),

        SHARED_SHIPPING_HUB(75, 130, 20, 1.5f, 130),

        AUTON_FFUTSE_UP(0, 0, 0, 0, 0, 0),
        AUTON_FFUTSE_HOME(0, 0, -90, 0, 0, 0),
        STOW_FFUTSE(0, 0, -90, 0, 0, 0),
        RELEASE_FFUTSE(0, 0, -90, 0, 0, 0),

        AUTON_FFUTSE_LEFT(75, 130, 20, -30, 1.5f, 130),
        AUTON_FFUTSE_MIDDLE(75, 130, 20, 0, 1.5f, 130),
        AUTON_FFUTSE_RIGHT(75, 130, 20, 30, 1.5f, 130),

        CAP(30, 140, 0, 0, 1, 170);

        public double shoulderPos, elbowPos, wristPos;
        public double turretAngle;
        public float toHomeTime;
        public double dumpPos;
        public boolean turret, ik;

        private double dx, dy;

        Articulation(double shoulderPos, double elbowPos, double wristPos, double turretAngle, float toHomeTime, double dumpPos) {
            this.shoulderPos = shoulderPos;
            this.elbowPos = elbowPos;
            this.wristPos = wristPos;
            this.turretAngle = turretAngle;
            this.toHomeTime = toHomeTime;
            this.dumpPos = dumpPos;
            turret = true;
        }

        Articulation(double shoulderPos, double elbowPos, double wristPos, float toHomeTime, double dumpPos) {
            this.shoulderPos = shoulderPos;
            this.elbowPos = elbowPos;
            this.wristPos = wristPos;
            this.toHomeTime = toHomeTime;
            this.dumpPos = dumpPos;
            turret = false;
        }

        Articulation(double dx, double dy, double turretAngle, float toHomeTime) {
            this.ik = true;
            this.turretAngle = turretAngle;
            this.toHomeTime = toHomeTime;

            dy = dy - SHOULDER_AXLE_TO_GROUND_HEIGHT;
            this.dx = dx;
            this.dy = dy;

            turret = true;
        }

        Articulation(double dx, double dy, float toHomeTime) {
            this.ik = true;
            this.toHomeTime = toHomeTime;

            dy = dy - SHOULDER_AXLE_TO_GROUND_HEIGHT;
            this.dx = dx;
            this.dy = dy;

            turret = false;
        }

    }

    private boolean checkTargetPositions(Articulation articulation) {
        return shoulderTargetAngle == articulation.shoulderPos &&
                elbowTargetAngle == articulation.elbowPos &&
                wristTargetAngle == articulation.wristPos;
    }

    private float currentToHomeTime = Articulation.HOME.toHomeTime;
    private double currentDumpPos = 0;
    private final StateMachine main = getStateMachine(new Stage())
            .addSingleState(() -> {
                dumping = false;
            })
            .addTimedState(() -> checkTargetPositions(articulation) ? 0 : 0.5f, () -> {
                if (!checkTargetPositions(articulation) && articulation.turret)
                    turret.setTargetHeading(Articulation.HOME.turretAngle);
            }, () -> {})
            .addTimedState(() -> checkTargetPositions(articulation) ? 0 : currentToHomeTime, () -> {
                if(!checkTargetPositions(articulation))
                    setTargetPositionsNoTurret(Articulation.HOME);
            }, () -> { })
            .addTimedState(() -> checkTargetPositions(articulation) ? 0 : articulation.toHomeTime , () -> setTargetPositions(articulation),
                    () -> {
                        currentToHomeTime = articulation.toHomeTime;
                        if (articulation.dumpPos != 0)
                            currentDumpPos = articulation.dumpPos;
                    })

            .build();
    private final StateMachine mainNoHome = getStateMachine(new Stage())
            .addSingleState(() -> {
                dumping = false;
            })
            .addTimedState(() -> articulation.toHomeTime, () -> {
                setTargetPositions(articulation);
                currentToHomeTime = articulation.toHomeTime;
                if (articulation.dumpPos != 0)
                    currentDumpPos = articulation.dumpPos;
            }, () -> {})
            .build();

    private double x;
    private long startTime;
    private final StateMachine mainIk = getStateMachine(new Stage())
            .addSingleState(() -> {
                dumping = false;
                x = 2;
            })
            .addTimedState(() -> currentToHomeTime, () -> setTargetPositions(Articulation.HOME), () -> {})
            .addSingleState(() -> {
                startTime = System.nanoTime();
            })
            .addState(() -> {
                double t = (System.nanoTime() - startTime) * 1e-9;
                x = map(t, 0, articulation.toHomeTime, 2, articulation.dx);

                double c = -articulation.dx / Math.log(1 - P);
                double y = articulation.dy * (1 - Math.exp(-x / c));

                double[] angles = craneIK(x, y);

                if(angles != null) {
                    setTargetPositions(angles[0], angles[1], angles[2]);
                    currentDumpPos = angles[3];
                }
                return t >= articulation.toHomeTime;
            })
            .addSingleState(() -> {
                double[] angles = craneIK(articulation.dx, articulation.dy);

                if(angles != null) {
                    setTargetPositions(angles[0], angles[1], angles[2]);
                    currentDumpPos = angles[3];
                }
                currentToHomeTime = articulation.toHomeTime;
            })
            .build();

    private final StateMachine mainIkNoHome = getStateMachine(new Stage())
            .addSingleState(() -> {
                dumping = false;
                x = 2;
                startTime = System.nanoTime();
            })
            .addState(() -> {
                double t = (System.nanoTime() - startTime) * 1e-9;
                x = map(t, 0, articulation.toHomeTime, 2, articulation.dx);

                double c = -articulation.dx / Math.log(1 - P);
                double y = articulation.dy * (1 - Math.exp(-x / c));

                double[] angles = craneIK(x, y);

                if(angles != null) {
                    setTargetPositions(angles[0], angles[1], angles[2]);
                    currentDumpPos = angles[3];
                }
                return t >= articulation.toHomeTime;
            })
            .addSingleState(() -> {
                double[] angles = craneIK(articulation.dx, articulation.dy);

                if(angles != null) {
                    setTargetPositions(angles[0], angles[1], angles[2]);
                    currentDumpPos = angles[3];
                }
                currentToHomeTime = articulation.toHomeTime;
            })
            .build();

    private final StateMachine init = getStateMachine(new Stage())
            .addTimedState(2f, () -> setTargetPositions(Articulation.INIT), () -> {
            })
            .build();

    public boolean articulate(Articulation articulation) {
        if (articulation.equals(Articulation.MANUAL))
            return true;
        else if (articulation.equals(Articulation.INIT)) {
            this.articulation = articulation;
            if (init.execute()) {
                this.articulation = Articulation.MANUAL;
                return true;
            }
        } else {
            this.articulation = articulation;
            if(articulation.ik) {
                if (toHomeEnabled ? mainIk.execute() : mainIkNoHome.execute()) {
                    this.articulation = Articulation.MANUAL;
                    return true;
                }
            } else {
                if (toHomeEnabled ? main.execute() : mainNoHome.execute()) {
                    this.articulation = Articulation.MANUAL;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void update(Canvas fieldOverlay) {

        bucketDistance = bucketDistanceSensor.getDistance(DistanceUnit.INCH);

        articulate(articulation);

        if (shoulderTargetAngle > 180)
            shoulderTargetAngle -= 360;
        if (elbowTargetAngle > 180)
            elbowTargetAngle -= 360;
        if (wristTargetAngle > 180)
            wristTargetAngle -= 360;

        shoulderServo.setPosition(servoNormalize(shoulderServoValue(shoulderTargetAngle)));
        elbowServo.setPosition(servoNormalize(elbowServoValue(elbowTargetAngle)));
        wristServo.setPosition(servoNormalize(wristServoValue(wristTargetAngle)));

        if (articulation != Articulation.MANUAL)
            turret.setTargetHeading(articulation.turretAngle);
        turret.update(fieldOverlay);
    }

    @Override
    public void stop() {
        articulation = Articulation.HOME;
    }

    @Override
    public String getTelemetryName() {
        return "Crane";
    }

    @Override
    public Map<String, Object> getTelemetry(boolean debug) {
        Map<String, Object> telemetryMap = new LinkedHashMap<>();

        telemetryMap.put("Current Articulation", articulation);

        if (debug) {
            telemetryMap.put("Shoulder Target Angle", shoulderTargetAngle);
            telemetryMap.put("Elbow Target Angle", elbowTargetAngle);
            telemetryMap.put("Wrist Target Angle", wristTargetAngle);
            telemetryMap.put("Wrist Dump Angle", currentDumpPos);

            telemetryMap.put("Shoulder Target PWM", shoulderServoValue(shoulderTargetAngle));
            telemetryMap.put("Elbow Target PWM", elbowServoValue(elbowTargetAngle));
            telemetryMap.put("Wrist Target PWM", wristServoValue(wristTargetAngle));

            telemetryMap.put("bucket distance", bucketDistance);

            double shoulderAngle = wrapAngleRad(Math.toRadians(90 - shoulderTargetAngle));
            double elbowAngle = -wrapAngleRad(Math.toRadians(180 - elbowTargetAngle));
            double wristAngle = wrapAngleRad(Math.toRadians(180) - wrapAngleRad(-elbowAngle + Math.toRadians(wristTargetAngle)));

            telemetryMap.put("horizontal distance", SHOULDER_TO_ELBOW * Math.cos(shoulderAngle) + ELBOW_TO_WRIST * Math.cos(shoulderAngle + elbowAngle));
            telemetryMap.put("vertical distance", SHOULDER_TO_ELBOW * Math.sin(shoulderAngle) + ELBOW_TO_WRIST * Math.sin(shoulderAngle + elbowAngle));
            telemetryMap.put("wrist absolute angle", wristAngle);
        }

        telemetryMap.put("Turret:", "");
        Map<String, Object> turretTelemetryMap = turret.getTelemetry(debug);
        telemetryMap.putAll(turretTelemetryMap);

        return telemetryMap;
    }

    public void dump() {
        setWristTargetAngle(currentDumpPos);
        dumping = true;
    }

    private void setTargetPositions(double shoulderPos, double elbowPos, double wristPos) {
        setShoulderTargetAngle(shoulderPos);
        setElbowTargetAngle(elbowPos);
        setWristTargetAngle(wristPos);
    }

    private void setTargetPositions(Articulation articulation) {
        setTargetPositions(articulation.shoulderPos, articulation.elbowPos, articulation.wristPos);

        if (articulation.turret)
            turret.setTargetHeading(Articulation.HOME.turretAngle);
    }

    public void setTargetPositionsNoTurret(Articulation articulation) {
        setTargetPositions(articulation.shoulderPos, articulation.elbowPos, articulation.wristPos);
    }

    // ----------------------------------------------------------------------------------------------
    // Getters And Setters
    // ----------------------------------------------------------------------------------------------

    // take the supplied relative-to-home target value in degrees
    // and convert to servo setting
    private double shoulderServoValue(double targetPos) {
        double newPos = Range.clip(targetPos, SHOULDER_DEG_MIN, SHOULDER_DEG_MAX);
        newPos = newPos * SHOULDER_PWM_PER_DEGREE + SHOULDER_HOME_PWM;
        return newPos;
    }

    private double elbowServoValue(double targetPos) {
        double newPos = Range.clip(targetPos, ELBOW_DEG_MIN, ELBOW_DEG_MAX);
        newPos = newPos * ELBOW_PWM_PER_DEGREE + ELBOW_HOME_PWM;
        return newPos;
    }

    private double wristServoValue(double targetPos) {
        double newPos = Range.clip(targetPos, WRIST_DEG_MIN, WRIST_DEG_MAX);
        newPos = newPos * WRIST_PWM_PER_DEGREE + WRIST_HOME_PWM;
        return newPos;
    }

    public void setShoulderTargetAngle(double shoulderTargetAngle) {
        this.shoulderTargetAngle = wrapAngle(shoulderTargetAngle);
    }

    public void setElbowTargetAngle(double elbowTargetAngle) {
        this.elbowTargetAngle = wrapAngle(elbowTargetAngle);
    }

    public void setWristTargetAngle(double wristTargetAngle) {
        this.wristTargetAngle = wrapAngle(wristTargetAngle);
    }

    public void setDumpPos(double dumpPos) {
        this.currentDumpPos = wrapAngle(dumpPos);
    }

    public double getShoulderTargetAngle() {
        return shoulderTargetAngle;
    }

    public double getElbowTargetAngle() {
        return elbowTargetAngle;
    }

    public double getWristTargetAngle() {
        return wristTargetAngle;
    }

    public Articulation getArticulation() {
        return articulation;
    }

    public boolean isDumping() {
        return dumping;
    }

    public double getBucketDistance() {
        return bucketDistance;
    }

    public void setToHomeEnabled(boolean toHomeEnabled) {
        this.toHomeEnabled = toHomeEnabled;
    }
}
