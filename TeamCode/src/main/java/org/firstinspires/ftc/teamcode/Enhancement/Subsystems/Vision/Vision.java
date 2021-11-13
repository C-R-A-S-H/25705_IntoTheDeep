package org.firstinspires.ftc.teamcode.Enhancement.Subsystems.Vision;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.ElapsedTime;
import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.matrices.OpenGLMatrix;
import org.firstinspires.ftc.robotcore.external.matrices.VectorF;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.teamcode.Enhancement.Config.VisionConfig;
import org.firstinspires.ftc.teamcode.Enhancement.Subsystems.Subsystem;
import org.firstinspires.ftc.teamcode.Enhancement.Subsystems.Vision.DetectMarkerPipeline.MarkerLocation;
import org.firstinspires.ftc.teamcode.Util.QuickTelemetry;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;
import org.openftc.easyopencv.OpenCvInternalCamera;

import static org.firstinspires.ftc.robotcore.external.navigation.AngleUnit.DEGREES;
import static org.firstinspires.ftc.robotcore.external.navigation.AxesOrder.XYZ;
import static org.firstinspires.ftc.robotcore.external.navigation.AxesReference.EXTRINSIC;

/**
 * The Vision Subsystem
 *
 * @see DetectMarkerPipeline
 * @see <a href="https://github.com/OpenFTC/EasyOpenCV">EasyOpenCV</a>
 */

public class Vision extends Subsystem {
    // Since ImageTarget trackable use mm to specify their dimensions, we must use mm for all the physical dimension.
    // Define constants
    private static final float mmPerInch = 25.4f;
    private static final float mmTargetHeight = (6) * mmPerInch;
    // Constants for perimeter targets
    private static final float halfField = 72 * mmPerInch;
    private static final float quadField = 36 * mmPerInch;
    // Define where camera is in relation to center of robot in inches
    final float CAMERA_FORWARD_DISPLACEMENT = 6.0f * mmPerInch; // TODO: CALIBRATE WHEN ROBOT IS BUILT
    final float CAMERA_VERTICAL_DISPLACEMENT = 6.5f * mmPerInch;
    final float CAMERA_LEFT_DISPLACEMENT = -0.75f * mmPerInch;
    MarkerLocation finalMarkerLocation; // Marker Location
    WebcamName webcamName = null;
    OpenGLMatrix robotFromCamera = null;
    // Class Members
    private OpenGLMatrix lastLocation;
    private VuforiaLocalizer vuforia;

    private boolean targetVisible;
    private VectorF targetTranslation;
    private Orientation targetRotation;

    private OpenCvCamera camera;

    private int[] viewportContainerIds;

    // Move stuff
    HardwareMap hardwareMap;
    OpenCvInternalCamera robotCamera;
    MarkerLocation markerLocation = MarkerLocation.NOT_FOUND;
    QuickTelemetry quickTelemetry;

    /**
     * Class instantiation
     *
     * @param telemetry   Quick Telemetry
     * @param hardwareMap the hardware map
     * @param timer       how much time elapsed
     * @throws InterruptedException It might happen because the thread is interrupted.
     */
    public Vision(QuickTelemetry telemetry, HardwareMap hardwareMap, ElapsedTime timer) throws InterruptedException {
        super(telemetry, hardwareMap, timer);

        telemetry.telemetry(3, "Vision Status", "Vision initializing started");

        webcamName = hardwareMap.get(WebcamName.class, VisionConfig.WEBCAM_NAME);
        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        viewportContainerIds = OpenCvCameraFactory.getInstance().splitLayoutForMultipleViewports(cameraMonitorViewId, 2, OpenCvCameraFactory.ViewportSplitMethod.HORIZONTALLY);


        telemetry.telemetry(3, "init Vuforia", "init Vuforia started");
        initVuforia();
        telemetry.telemetry(2, "init Vuforia", "init Vuforia completed");

        OpenCvInternalCamera robotCamera;

        robotCamera = OpenCvCameraFactory.getInstance().createInternalCamera(OpenCvInternalCamera.CameraDirection.BACK, cameraMonitorViewId);
/*
        telemetry.telemetry(4, "Detect Marker", "Detecting Marker");
        DetectMarker detectMarkerRunnable = new DetectMarker(hardwareMap, robotCamera, telemetry);
        VisionConfig.finalMarkerLocation = detectMarkerRunnable.DetectMarkerRun();
        telemetry.telemetry(3, "Detect Marker", "Detected Marker");
        telemetry.telemetry(2, "Vision Status", "Vision initialized");
*/

        // Detect marker stuff
        this.hardwareMap = hardwareMap;
        this.robotCamera = robotCamera;
        this.quickTelemetry = quickTelemetry.newQuickTelemetryFile("Detect Marker Pipeline");
        VisionConfig.finalMarkerLocation = DetectMarkerRun();
    }

    private void initVuforia() {
        // Configure parameters
        VuforiaLocalizer.Parameters parameters = new VuforiaLocalizer.Parameters(viewportContainerIds[1]);
        parameters.vuforiaLicenseKey = VisionConfig.VUFORIA_KEY; //moved it to VisionConfig for easier access
        parameters.cameraName = webcamName;
        parameters.useExtendedTracking = false;

        // Instantiate the Vuforia engine
        vuforia = ClassFactory.getInstance().createVuforia(parameters);


        OpenGLMatrix robotFromCamera = createMatrix(CAMERA_LEFT_DISPLACEMENT, CAMERA_FORWARD_DISPLACEMENT, CAMERA_VERTICAL_DISPLACEMENT, 90, 0, 0);
    }


    // Helper method to create matrix to identify locations
    public OpenGLMatrix createMatrix(float x, float y, float z, float u, float v, float w) {
        return OpenGLMatrix.translation(x, y, z).multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, u, v, w));
    }

    /**
     * This method waits until the search for the marker is done, and then it return the marker
     * location. It waits until the marker is found, then it returns the marker location.
     *
     * @return Where the marker is
     * @see DetectMarkerPipeline#getMarkerLocation()
     */
    public MarkerLocation DetectMarkerRun() {
        DetectMarkerPipeline detectMarkerPipeline = new DetectMarkerPipeline(quickTelemetry);
        robotCamera.setPipeline(detectMarkerPipeline);

        // We set the viewport policy to optimized view so the preview doesn't appear 90 deg
        // out when the RC activity is in portrait. We do our actual image processing assuming
        // landscape orientation, though.
        robotCamera.setViewportRenderingPolicy(OpenCvCamera.ViewportRenderingPolicy.OPTIMIZE_VIEW);

        robotCamera.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener() {
            @Override
            public void onOpened() {
                robotCamera.startStreaming(VisionConfig.CAMERA_WIDTH, VisionConfig.CAMERA_HEIGHT, OpenCvCameraRotation.UPRIGHT);
            }

            @Override
            public void onError(int errorCode) {
                markerLocation = MarkerLocation.NOT_FOUND;
            }
        });
        if (!(markerLocation == MarkerLocation.NOT_FOUND)) {
            markerLocation = detectMarkerPipeline.getMarkerLocation();
        }
        return markerLocation;
    }
}
