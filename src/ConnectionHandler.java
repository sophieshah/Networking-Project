import java.io.*;
import java.net.*;
import java.util.*;

public class ConnectionHandler implements Runnable {
    private Socket serverSocket;
    int peerId;

    public ConnectionHandler(Socket serverSocket) {
        this.serverSocket = serverSocket;
    }

    private void closeConnection() {
        try {
            if (!serverSocket.isClosed() && serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }
}