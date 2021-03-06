package view;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Pair;
import viewModel.ViewModelSimulator;

import java.io.*;
import java.nio.file.Paths;
import java.util.Observable;
import java.util.Observer;

public class GUIController implements Observer {

    ViewModelSimulator viewModelSimulator;
    @FXML
    MapDisplayer mapDisplayer;
    @FXML
    Circle joystick;
    @FXML
    Circle outer_bounds;
    @FXML
    TextField IP_textField;
    @FXML
    TextField port_textField;
    @FXML
    Button connectBtn;
    @FXML
    Button calcPath_btn;
    @FXML
    Slider rudder_slider;
    @FXML
    Slider throttle_slider;
    @FXML
    RadioButton autoPilot_radio_btn;
    @FXML
    RadioButton manual_radio_btn;
    @FXML
    ImageView airplane;
    @FXML
    Button load_text_btn;
    @FXML
    TextArea text_area;

    double maxRadius = 80;
    DoubleProperty joystickValX = new SimpleDoubleProperty();
    DoubleProperty joystickValY = new SimpleDoubleProperty();

    Stage primaryStage;

    Boolean afterTakeOff = false;

    @FXML
    public void initialize() {
        initializeJoystick();
        rudder_slider.valueProperty().addListener((observableValue, number, t1) -> rudderChange());
        throttle_slider.valueProperty().addListener((observableValue, number, t1) -> throttleChange());
        load_text_btn.setOnAction(actionEvent -> loadText());
        ToggleGroup toggleGroup = new ToggleGroup();
        autoPilot_radio_btn.setToggleGroup(toggleGroup);
        manual_radio_btn.setToggleGroup(toggleGroup);
        manual_radio_btn.fire();
        autoPilot_radio_btn.setOnAction(actionEvent -> {
            if (!afterTakeOff)
                takeOff();
        });
    }

    public void initializeJoystick() {
        joystickValX.bind(joystick.layoutXProperty());
        joystickValY.bind(joystick.layoutYProperty());
        joystick.setOnMouseDragged(mouseEvent -> {
            Point2D centerPoint = new Point2D(651, 196);
            Point2D mouse = new Point2D(mouseEvent.getSceneX(), mouseEvent.getSceneY());
            double dis = centerPoint.distance(mouse);
            if (dis > maxRadius) { // if joystick get out of bounds
                double angle = Math.atan2(mouse.getY() - centerPoint.getY(), mouse.getX() - centerPoint.getX()); // cal angle between 2 points
                // force joystick to stay on his bounds
                joystick.setLayoutX(centerPoint.getX() + maxRadius * Math.cos(angle));
                joystick.setLayoutY(centerPoint.getY() + maxRadius * Math.sin(angle));
            } else { // in bounds
                joystick.setLayoutX(mouseEvent.getSceneX());
                joystick.setLayoutY(mouseEvent.getSceneY());
            }
            viewModelSimulator.joystickMovement();
        });

        joystick.setOnMouseReleased(mouseDragEvent -> {
            joystick.setLayoutX(651);
            joystick.setLayoutY(196);
            viewModelSimulator.joystickMovement();
        });
    }

    public void setViewModelSimulator(ViewModelSimulator vm) {
        viewModelSimulator = vm;
        viewModelSimulator.joystickValX.bind(this.joystickValX);
        viewModelSimulator.joystickValY.bind(this.joystickValY);
        viewModelSimulator.throttleVal.bind(throttle_slider.valueProperty());
        viewModelSimulator.rudderVal.bind(rudder_slider.valueProperty());
        this.viewModelSimulator.addObserver(this);

//        viewModelSimulator.airplaneX.bind(this.airplane.layoutXProperty());
//        viewModelSimulator.airplaneY.bind(this.airplane.layoutYProperty());
        viewModelSimulator.airplaneX.bind(this.airplane.layoutXProperty());
        viewModelSimulator.airplaneY.bind(this.airplane.layoutYProperty());
    }

    public void rudderChange() {
        viewModelSimulator.rudderChange();
    }

    public void throttleChange() {
        viewModelSimulator.throttleChange();
    }

    public void client_connect() {
        connect_popup(); // function to open the connect popup
        connectBtn.setOnAction(actionEvent -> { // set handler to connect button for client connection
            viewModelSimulator.client_connect();
            if (primaryStage != null)
                primaryStage.close();
        });
    }

    public void calc_path() {
        connect_popup();
        connectBtn.setOnAction(actionEvent -> { // set handler to connect button for server connection
            try {
                viewModelSimulator.calc_path(this.mapDisplayer.mapData);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (primaryStage != null)
                primaryStage.close();
            calcPath_btn.setDisable(true);
            calcPath_btn.setText("Connected");
        });
    }

    private void loadText() {
        viewModelSimulator.load_text(text_area);
    }

    public void load_data() throws IOException {
        viewModelSimulator.load_data(mapDisplayer, airplane);
    }

    public void connect_popup() {
        FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("./view/connect_view.fxml"));
        loader.setController(this);
        Parent root = null;
        try {
            root = loader.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
        primaryStage = new Stage();
        primaryStage.setTitle("Connect");
        primaryStage.setScene(new Scene(root));

        viewModelSimulator.server_ip.bind(IP_textField.textProperty());
        viewModelSimulator.server_port.bind(port_textField.textProperty());

        primaryStage.show();
    }

    public void takeOff() {

        class takeOffService extends Service{
            ViewModelSimulator viewModelSimulator;
            takeOffService(ViewModelSimulator viewModelSimulator){
                this.viewModelSimulator= viewModelSimulator;
            }

            @Override
            protected Task createTask() {
                String[] startEngineCommands = {"var elevator = bind simElevator",
                        "var avionics = bind simAvionics",
                        "var bat = bind simBat",
                        "var alt = bind simAltitude",
                        "var magnetos = bind simMagnetos",
                        "var throttle = bind simThrottle",
                        "var primer = bind simPrimer",
                        "var mixture = bind simMixture",
                        "var starter = bind simStarter",
                        "var autostart = bind simAutostart",
                        "elevator = 0",
                        "avionics  = 1",
                        "bat = 1",
                        "magnetos = 3",
                        "throttle = 0.2",
                        "primer = 3",
                        "mixture = 1",
                        "starter = 1",
                        "autostart = 1",
                        "sleep 7000"
                };

                this.viewModelSimulator.sentToInterpreterServer(startEngineCommands);



                String[] takeOffCommands = {
                        "var parking = bind simParking",
                        "parking = 0",
                        "var minus = -1",
                        "breaks = 0",
                        "throttle = 1",
                        "var h = heading",
                        "while alt < 1000 {",
                        "rudder = (h - heading) / 20",
                        "aileron = (minus * roll) / 70",
                        "elevator = pitch / 50",
                        "print alt",
                        "sleep 250",
                        "}"
                };

                this.viewModelSimulator.sentToInterpreterServer(takeOffCommands);
//                this.afterTakeOff = true;
                return null;
            }
        }
        new takeOffService(viewModelSimulator).start();


    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg.getClass().getName().equals("java.lang.String"))
            this.mapDisplayer.drawPath(arg.toString());
        else {
//            if (!airplane.isVisible())
//                airplane.setVisible(true);
            //Convert the observable data to String[]
            String[] data = (String[]) arg;
            Pair<Double, Double> positions = latlngToScreenXY(Double.parseDouble(data[0]), Double.parseDouble(data[1]));
            double x = positions.getKey();
            double y = positions.getValue();
            //Update the airplane position
            airplane.setLayoutX(x);
            airplane.setLayoutY(y);

            airplane.setRotate(Double.parseDouble(data[2]) - 120);
        }
    }

    double radius = 6371;

    public class referencePoint {
        public double srcX;
        double scrY;
        double lat;
        double lng;

        public referencePoint(double scrX, double scrY, double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
            this.srcX = scrX;
            this.scrY = scrY;
        }

    }

    //original
//    // Calculate global X and Y for top-left reference point
//    referencePoint p0 = new referencePoint(-4, 50, 21.443738, -158.020959);
//    // Calculate global X and Y for bottom-right reference point
//    referencePoint p1 = new referencePoint(260, 285, 21.238137136691147, -157.63410286953055);

    //1:1 map data
//    // Calculate global X and Y for top-left reference point
//    referencePoint p0  = new referencePoint(-4,50,21.443738,-158.020959);
//    // Calculate global X and Y for bottom-right reference point
//    referencePoint p1  = new referencePoint(243,202,21.238137136691147,-157.63410286953055);

    //tests
    // Calculate global X and Y for top-left reference point
    referencePoint p0 = new referencePoint(-4, 50, 21.443738, -158.020959);
    // Calculate global X and Y for bottom-right reference point
    referencePoint p1 = new referencePoint(260, 285, 21.15736059993721, -157.66010286953055);

    // This function converts lat and lng coordinates to GLOBAL X and Y positions
    public Pair<Double, Double> latlngToGlobalXY(double lat, double lng) {
        // Calculates x based on cos of average of the latitudes
        double x = radius * lng * Math.cos((p0.lat + p1.lat) / 2);
        //Calculates y based on latitude
        double y = radius * lat;
        return new Pair(x, y);
    }

    Pair<Double, Double> p0_pos = latlngToGlobalXY(p0.lat, p0.lng);
    Pair<Double, Double> p1_pos = latlngToGlobalXY(p1.lat, p1.lng);


    // This function converts lat and lng coordinates to SCREEN X and Y positions
    public Pair<Double, Double> latlngToScreenXY(double lat, double lng) {
        // Calculate global X and Y for projection point
        Pair<Double, Double> pos = latlngToGlobalXY(lat, lng);
        // Calculate the percentage of Global X position in relation to total global width
        double perX = ((pos.getKey() - p0_pos.getKey()) / (p1_pos.getKey() - p0_pos.getKey()));
        // Calculate the percentage of Global Y position in relation to total global height
        double perY = ((pos.getValue() - p0_pos.getValue()) / (p1_pos.getValue() - p0_pos.getValue()));

        // Returns the screen position based on reference points
        double returnX = p0.srcX + (p1.srcX - p0.srcX) * perX;
        double returnY = p0.scrY + (p1.scrY - p0.scrY) * perY;

        return new Pair<>(returnX, returnY);
    }

}
