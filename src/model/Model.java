package model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Pair;
import view.MapDisplayer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Observable;
import java.util.concurrent.CompletableFuture;

public class Model extends Observable {

    //CalcPath server variables
    Socket socketCalcServer;
    PrintWriter outClientCalcServer;
    BufferedReader inClientCalcServer;

    //Interpreter server variables
    Socket socketInterpreter;
    PrintWriter outInterpreter;
    BufferedReader inInterpreter;
    private volatile boolean stopClientInterpreter = false;
    private volatile boolean stopServerInterpreter = false;
    public DoubleProperty scale;

    double airplaneX;
    double airplaneY;
    public Model() {
        scale = new SimpleDoubleProperty();
        startServerInterpreter(5404);
    }

    public void sendJoystickValToSim(double joystickValX, double joystickValY) {
        //Convert joystick values to FlightSimulator valid values then send it
        double aileron = (joystickValX - 651) / 80; //left-right -1 to 1 ~~~ 731 = 1 ,571 = -1
        double elevator = (joystickValY - 196) / 80; //up-down -1 to 1 ~~~ 276 = 1 ,116 = -1
        String[] move = {
                "aileron = " + aileron,
                "elevator = " + elevator
        };
        sentToInterpreterServer(move);
    }

    public void rudderChange(double rudderVal) {
        String[] move = {
                "rudder = " + rudderVal
        };
        sentToInterpreterServer(move);
    }

    public void throttleChange(double throttleVal) {
        String[] move = {
                "throttle = " + throttleVal
        };
        sentToInterpreterServer(move);
    }

    public void connectToInterpreterServer(String ip, int port) {
        //connect the GUI to MyInterpreter server
        startClientInterpreter(ip, port); // TODO:Need to check when it finish
        String[] initial = {
                "var aileron",
                "var elevator",
                "var rudder",
                "var throttle",
                "var breaks",
                "var heading",
                "var alt",
                "var pitch",
                "var roll",

                "roll = bind simRoll",
                "breaks = bind simBreaks",
                "heading = bind simHeading",
                "alt = bind simAltitude",
                "pitch = bind simPitch",
                "aileron = bind simAileron",
                "elevator = bind simElevator",
                "rudder = bind simRudder",
                "throttle = bind simThrottle"
        };
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        sentToInterpreterServer(initial);
    }

    public void connectToCalcServer(String ip, int port, int[][] matrix, double targetX, double targetY, double airplaneX, double airplaneY) {
        try {
            //Check if we already created connection once
            if (this.socketCalcServer == null) {

            }
            socketCalcServer = new Socket(ip, port);
            this.outClientCalcServer = new PrintWriter(socketCalcServer.getOutputStream());
            this.inClientCalcServer = new BufferedReader(new InputStreamReader(socketCalcServer.getInputStream()));

            //Send the matrix
            for (int[] ints : matrix) {
                StringBuilder line = new StringBuilder();
                for (int anInt : ints) {
                    line.append(anInt).append(",");
                }
                line.deleteCharAt(line.length() - 1);
                outClientCalcServer.println(line);
                outClientCalcServer.flush();
            }
            outClientCalcServer.println("end");

            //Send the airplane position
           Pair<Integer, Integer> airplanePositions = calcPositions(airplaneX, airplaneY);
            //outClientCalcServer.println(airplanePositions.getKey()+","+airplanePositions.getValue());
//            outClientCalcServer.println(0 + "," + 0);
//            outClientCalcServer.println((int) airplaneY + "," + (int) airplaneX);
            outClientCalcServer.println(63 + "," + 52);

            //Send the target position
            outClientCalcServer.println((int) 40.68936170212766 + "," + (int) 130.98484848484847);
//            System.out.println("@@@@@@@   X  :" +targetX + "@@@@@@@   Y  :"+targetY);

            outClientCalcServer.flush();

            CompletableFuture.supplyAsync(() -> {
                //Wait for response
                String response;
                while (true) {
                    try {
                        if (!((response = inClientCalcServer.readLine()) == null)) break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return response;
            }).thenAccept(path -> {
                //Once the response (path) is returned, notify the ViewModel
                setChanged();
                notifyObservers(path);
//                outClientCalcServer.close();
                try {
                    this.socketCalcServer.close();
                    inClientCalcServer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                this.outClientCalcServer.close();
            });


        } catch (IOException e) {
            System.out.println("Failed!!!");
        }
    }

    private Pair<Integer, Integer> calcPositions(double x, double y) {
        int lng, lat;
        lat = (int) (Math.abs(Math.abs(x) - 21.443738) * scale.get());
        lng = (int) (Math.abs(Math.abs(y) - 158.020959) * scale.get());
        return new Pair<>(lng, lat);
    }

    public void startClientInterpreter(String ip, int port) { // start client to connect to MyInterpreter server
        new Thread(() -> runClientInterpreter(ip, port)).start();
    }

    private void runClientInterpreter(String ip, int port) {
        while (!stopClientInterpreter) {
            try {
                socketInterpreter = new Socket(ip, port);
                outInterpreter = new PrintWriter(socketInterpreter.getOutputStream());
                while (!stopClientInterpreter) {

                }

            } catch (IOException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                }
            }
        }
    }

    public void closeClientInterpreterServer() {
        stopClientInterpreter = true;
    }

    // start client to connect to MyInterpreter server
    public void startServerInterpreter(int port) {
        new Thread(() -> runServerInterpreter(port)).start();
    }

    private void runServerInterpreter(int port) {
        try {
            ServerSocket server = new ServerSocket(port);
            server.setSoTimeout(1000);
            System.out.print("Gui server Thread: Waiting for interpreter\n");
            while (!stopServerInterpreter) {
                try {
                    System.out.print(".");
                    Socket socket = server.accept();
                    System.out.println("Gui server Thread: interpreter Connected");
                    inInterpreter = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String line;
                    String[] lineSeparated;
                    while (!(line = inInterpreter.readLine()).equals("bye")) {
                        // xVal,yVal,headingVal
                        lineSeparated = line.split(",");
                        airplaneX = Double.parseDouble(lineSeparated[1]);
                        airplaneY = Double.parseDouble(lineSeparated[0]);
                        setChanged(); // notify viewmodel -> notify view. data from server arrived
                        notifyObservers(lineSeparated);
                        System.out.println("Gui server received: " + line);
                    }
                    System.out.println("MyInterpreter server: Client DisConnected");
                    inInterpreter.close();
                    socket.close();
                } catch (SocketTimeoutException e) {
                    e.printStackTrace();
                }
            }
            server.close();
        } catch (IOException ignored) {
        }
    }

    public void sentToInterpreterServer(String[] lines) { // send data to MyInterpreter server
        if (outInterpreter == null)
            return;

        for (String line : lines) {
            outInterpreter.println(line);
            outInterpreter.flush();
        }
        outInterpreter.println("bye");
        outInterpreter.flush();
    }

}

