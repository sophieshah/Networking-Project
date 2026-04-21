import java.io.*;
import java.net.*;
import java.util.*;

import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList.*;
import java.nio.file.*;

public class peerProcess {
    int peerId;
    private ServerSocket serverSocket;

    // Thread-safe list of active connections
    List<ConnectionHandler> connections = new CopyOnWriteArrayList<>();

    //from common.cfg
    int neighbors;
    int unchokingInterval;
    int OptimisticUnchokingInterval;
    String fileName;
    int fileSize;
    int pieceSize;
    int numPieces;

    int curPort;
    String curHost;
    int curHasFile;

    // holds all the info from PeerInfo.cfg
    List<String[]> peerInfoList = new ArrayList<>();

    int[] bitfield;

    PeerLogger logger;
    FileManager fileManager;
    ChokeManager chokeManager;

    public peerProcess(int peerId) {
        this.peerId = peerId;
        this.logger = new PeerLogger(peerId);
    }

    public synchronized void addConnection(ConnectionHandler c) {
        connections.add(c);
    }

    public synchronized void removeConnection(ConnectionHandler c) {
        connections.remove(c);
    }

    public synchronized int getPieceCount() {
        int count = 0;
        for (int b : bitfield) if (b == 1) count++;
        return count;
    }

    public synchronized boolean hasCompleteFile() {
        return getPieceCount() == numPieces;
    }

    public void readCFGs()
    {
        //read common.cfg and save to PeerProcess variables
        Properties Cfg = new Properties();
        try (FileInputStream common = new FileInputStream("Common.cfg"))
        {
            Cfg.load(common);
            neighbors = Integer.parseInt(Cfg.getProperty("NumberOfPreferredNeighbors").trim());
            unchokingInterval = Integer.parseInt(Cfg.getProperty("UnchokingInterval").trim());
            OptimisticUnchokingInterval = Integer.parseInt(Cfg.getProperty("OptimisticUnchokingInterval").trim());
            fileName = Cfg.getProperty("FileName").trim();
            fileSize = Integer.parseInt(Cfg.getProperty("FileSize").trim());
            pieceSize = Integer.parseInt(Cfg.getProperty("PieceSize").trim());
            numPieces = (int) Math.ceil((double) fileSize / pieceSize);
            bitfield = new int[numPieces];
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
        }

        //read PeerInfo.cfg, create new subdirectory for each peer, save variables to arraylist
        try (BufferedReader br = new BufferedReader(new FileReader("PeerInfo.cfg")))
        {
            String line;

            while ((line = br.readLine()) != null)
            {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] vars = line.split("\\s+");

                int id = Integer.parseInt(vars[0]);
                String hostName = vars[1];
                int listeningPort = Integer.parseInt(vars[2]);
                int hasFile = Integer.parseInt(vars[3]); //1 is yes 0 is no

                //make subdir
                String dirName = "peer_" + id;
                Files.createDirectories(Paths.get(dirName));
               
                if(id == this.peerId)
                {
                    curPort = listeningPort;
                    curHost = hostName;
                    curHasFile = hasFile;
                }


                peerInfoList.add(vars);
            }
        } 
        catch (IOException e)
        {
            e.printStackTrace();
        }

        // Initialize FileManager
        fileManager = new FileManager(peerId, fileName, pieceSize, numPieces, fileSize);
        try
        {
            if (curHasFile == 0)
            {
                fileManager.initEmptyFile();
            } 
            else
            {
                fileManager.openExistingFile();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        if(curHasFile == 1)
        {
            for(int i=0; i<bitfield.length; i++)
            {
                bitfield[i] = 1;
            }
        }
        else
        {
            for(int i=0; i<bitfield.length; i++)
            {
                bitfield[i] = 0;
            }
        }

        // Initialize and start ChokeManager
        chokeManager = new ChokeManager(this);
    }

    // Accept incoming connections in a loop (runs in its own thread)
    public void startListening() throws IOException {
        serverSocket = new ServerSocket(curPort);
        System.out.println("Peer " + peerId + " listening on port " + curPort);

        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                ConnectionHandler handler = new ConnectionHandler(clientSocket, this);
                new Thread(handler).start();
            } catch (SocketException e) {
                // serverSocket was closed — shutting down
                break;
            }
        }
    }

    // Connect outward to all peers listed before this one
    public void connectToPrevPeers() {
        for (String[] peer : peerInfoList)
        {
            int id = Integer.parseInt(peer[0].trim());
            String hostName = peer[1];
            int listeningPort = Integer.parseInt(peer[2].trim());

            if(id < this.peerId)
            {
                try
                {
                    Socket socket = new Socket(hostName, listeningPort);
                    System.out.println("Peer " + peerId + " connected to Peer " + id);
                    logger.logTCPConnectionTo(id);
                    ConnectionHandler handler = new ConnectionHandler(socket, this);
                    new Thread(handler).start();
                }
                catch (IOException e)
                {
                    System.err.println("Failed to connect to peer " + id + ": " + e.getMessage());
                }
            }
        }
    }

    // Check if all peers have the complete file by inspecting all known bitfields.
    // Called after every piece download or have message.
    public synchronized void checkAllComplete() {
        // We must have the complete file ourselves
        if (!hasCompleteFile()) return;

        // We must be connected to all other peers (total peers - 1)
        int totalPeers = peerInfoList.size();
        if (connections.size() < totalPeers - 1) return;

        // Every connected neighbor must also have all pieces
        for (ConnectionHandler c : connections) {
            if (c.remoteBitfield == null) return;
            for (int i = 0; i < numPieces; i++) {
                if (c.remoteBitfield[i] == 0) return;
            }
        }

        System.out.println("All peers have downloaded the complete file. Shutting down.");
        shutdown();
    }

    private void shutdown() {
        chokeManager.stop();
        fileManager.close();
        logger.close();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    public void closeSocket()
    {
        try 
        {
            if (!serverSocket.isClosed() && serverSocket != null)
            {
                serverSocket.close();
            }
        } 
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        int peerId = Integer.parseInt(args[0].trim());
        peerProcess p = new peerProcess(peerId);

        p.readCFGs();

        // Start listening for incoming connections in a background thread
        new Thread(() -> {
            try {
                p.startListening();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Give the server socket a moment to start
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        // Connect to all peers that started before this one
        p.connectToPrevPeers();

        // Start choke/unchoke timers
        p.chokeManager.start();

        // Keep main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {}
    }
}