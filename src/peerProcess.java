import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.*;


public class peerProcess
{
    int peerId;
    private ServerSocket serverSocket;
    List<ConnectionHandler> connections = new ArrayList<>();

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
    FileManager fileManager;
    PeerLogger logger;
    Set<Integer> peersWithCompleteFile = Collections.synchronizedSet(new HashSet<>());

    public peerProcess(int peerId)
    {
        this.peerId = peerId;
    }


    public void readCFGs()
    {
        //read common.cfg and save to PeerProcess variables
        Properties Cfg = new Properties();
        try (FileInputStream common = new FileInputStream("Common.cfg"))
        {
            Cfg.load(common);
            neighbors = Integer.parseInt(Cfg.getProperty("NumberOfPreferredNeighbors"));
            unchokingInterval = Integer.parseInt(Cfg.getProperty("UnchokingInterval"));
            OptimisticUnchokingInterval = Integer.parseInt(Cfg.getProperty("OptimisticUnchokingInterval"));
            fileName = Cfg.getProperty("FileName");
            fileSize = Integer.parseInt(Cfg.getProperty("FileSize"));
            pieceSize = Integer.parseInt(Cfg.getProperty("PieceSize"));
            numPieces = (int) Math.ceil((double) fileSize / pieceSize);
            bitfield = new int[numPieces];
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
        }


        //read PeerInfo.cfg, create new subdirectory for each peer, save variables to arraylist
        try(BufferedReader br = new BufferedReader(new FileReader("PeerInfo.cfg")))
        {
            String line;

            while((line = br.readLine()) != null)
            {
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


                if (hasFile == 1)
                {
                    peersWithCompleteFile.add(id);
                }
                peerInfoList.add(vars);
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

        try
        {
            fileManager = new FileManager(this);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void listen()
    {
        try
        {
            serverSocket = new ServerSocket(curPort);
            
            while(true)
            {
                Socket clientSocket = serverSocket.accept();
                ConnectionHandler handler = new ConnectionHandler(clientSocket, this);
                handler.isIncoming = true;
                synchronized (connections)
                {
                    connections.add(handler);
                }
                new Thread(handler).start();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void connect(String hname, int port) throws IOException
    {
        Socket socket = new Socket(hname, port);
        ConnectionHandler handler = new ConnectionHandler(socket, this);
        synchronized (connections)
        {
            connections.add(handler);
        }
        new Thread(handler).start();
    }

    public void connectToPrevPeers()
    {
        for(String[] peer : peerInfoList)
        {
            int id = Integer.parseInt(peer[0].trim());
            String hostName = peer[1];
            int listeningPort = Integer.parseInt(peer[2].trim());

            if(id < this.peerId)
            {
                try
                {
                    connect(hostName, listeningPort);
                } 
                catch(IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }


    public void startSocket(String host, int listeningPort) throws IOException
    {
        serverSocket = new ServerSocket(listeningPort);
        System.out.println("server connected successfully");

        while(true)
        {
            Socket clientSocket = serverSocket.accept();
            System.out.println("client connected successfully");
            ConnectionHandler handler = new ConnectionHandler(clientSocket, this);
            synchronized (connections)
            {
                connections.add(handler);
            }
            new Thread(handler).start();
        }
    }

    public synchronized void checkTermination()
    {
        if (peersWithCompleteFile.size() == peerInfoList.size())
        {
            System.out.println("All peers have the complete file. Shutting down.");
            System.exit(0);
        }
    }

    public void closeSocket()
    {
        try 
        {
            if (serverSocket != null && !serverSocket.isClosed())
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
        try
        {
            p.logger = new PeerLogger(peerId);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        
        // Read peerID from command line
        // Read in Common.cfg & PeerInfo.cfg
        // PeerInfo: find if boolean value is 1 or 0
        // if 1: all bits in bitfield == 1
        // if 0: all bits in bitfield == 0
        // start the server socket in readCFGs
        p.readCFGs();
        //new Thread(() -> p.listen()).start();

        new Thread(() -> {
            try
            {
                p.startSocket(p.curHost, p.curPort);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        p.connectToPrevPeers();


        // choke/unchoke timers
        ChokeManager manager = new ChokeManager(p);
        new Thread(manager).start();

        // create subdirectory for individual peers
    }
}
