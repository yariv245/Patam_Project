package commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

import servers.MyInterpreter;

public class OpenServerCommand implements Command {
    public static ServerSocket server;

    @Override
    public Integer doCommand(List<String> command) {
        System.out.println("Open server command start port: "+command.get(0) + " Frequency: "+command.get(1));
        new Thread(() -> runServer(Integer.parseInt(command.get(0)), Integer.parseInt(command.get(1)))).start();
        return 2;
    }

    private void runServer(int port, int freq) {
        try {
            server = new ServerSocket(port);
            server.setSoTimeout(freq * 10);
            boolean stop = false;
            System.out.print("Waiting for clients\n");
            while (!stop) {
                try {
                    // Reach to this point
                    System.out.print(".");
                    Socket client = server.accept();
					System.out.println("Client Connected");
                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String line = null;
                    while ((line = in.readLine()) != null) {// simX + "," + simY + "," + simZ
                        try {
                            String[] values = line.split(",");
                            MyInterpreter.putSymbolTable("simX", Double.parseDouble(values[0]));
                            MyInterpreter.putSymbolTable("simY", Double.parseDouble(values[1]));
                            MyInterpreter.putSymbolTable("simZ", Double.parseDouble(values[2]));
                        } catch (NumberFormatException e) {
                        }
                    }
                    in.close();
                    client.close();
                } catch (SocketTimeoutException e) {
                }
            }
            server.close();
        } catch (IOException e) {
        }

    }

}
