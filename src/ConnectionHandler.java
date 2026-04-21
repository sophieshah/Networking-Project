import java.io.*;
import java.net.*;
import java.util.*;

public class ConnectionHandler implements Runnable {
    private Socket serverSocket;
    public OutputStream out;
    private peerProcess peer;
    int peerId = -1;

    public volatile boolean isInterested = false;    // remote peer is interested in us
    public volatile boolean isChoked = true;         // we are choked by remote peer
    public volatile boolean isChokingRemote = true;  // we are choking the remote peer

    // Tracks which pieces the remote peer has — set by MessageHandler
    public int[] remoteBitfield;

    public ConnectionHandler(Socket serverSocket, peerProcess peer) {
        this.serverSocket = serverSocket;
        this.peer = peer;
    }

    private void closeConnection()
    {
        try {
            if (!serverSocket.isClosed() && serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private byte[] getBitfieldPayload()
    {
        int numPieces = (int) Math.ceil(peer.numPieces);
        byte[] payload = new byte[(numPieces + 7) / 8];
        for (int i = 0; i < numPieces; i++)
        {
            if (peer.bitfield[i] == 1)
            {
                payload[i / 8] |= (1 << (7 - (i % 8)));
            }
        }
        return payload;
    }

    public synchronized void sendMessage(byte[] data) throws IOException {
        if (out != null) {
            out.write(data);
            out.flush();
        }
    }

    public void run()
    {
        try{
            InputStream in = serverSocket.getInputStream();
            out = serverSocket.getOutputStream();

            HandshakeMessage fromHandshake = new HandshakeMessage(peer.peerId);
            out.write(fromHandshake.toByteArray());
            out.flush();
            HandshakeMessage toHandshake = HandshakeMessage.unpack(in);
            peerId = toHandshake.getPeerID();

            // Initialize remote bitfield now that we know the peer
            remoteBitfield = new int[peer.numPieces];

            System.out.println("Handshake completed with Peer " + peerId);

            if (peerId > peer.peerId) {
                peer.logger.logTCPConnectionFrom(peerId);
            }

            // Send bitfield if we have any pieces
            boolean hasPieces = false;
            for (int b : peer.bitfield) {
                if (b == 1) { hasPieces = true; break; }
            }
            if (hasPieces) {
                byte[] bitfieldPayload = getBitfieldPayload();
                Message bitfieldMessage = new Message(Message.MessageType.BITFIELD, bitfieldPayload);
                out.write(bitfieldMessage.toByteArray());
                out.flush();
            }

            // Register this connection with peerProcess
            peer.addConnection(this);

            // Start message handling loop
            MessageHandler handler = new MessageHandler(in, this, peer);
            handler.handleMessage();

        } catch (EOFException | SocketException e) {
            System.out.println("Connection closed with peer " + peerId);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            peer.removeConnection(this);
            closeConnection();
        }
    }
}