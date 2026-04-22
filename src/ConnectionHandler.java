import java.io.*;
import java.net.*;

public class ConnectionHandler implements Runnable{
    private Socket serverSocket;
    public OutputStream out;
    private peerProcess peer;
    int peerId;
    public MessageHandler messageHandler;
    public boolean hasCompleteFile = false;
    public boolean amInterestedInRemote = false;
    public boolean remoteInterestedInMe = false;
    public boolean isChoked = true;
    public boolean isIncoming = false;

    public ConnectionHandler(Socket serverSocket, peerProcess peer)
    {
        this.serverSocket = serverSocket;
        this.peer = peer;
    }

    private void closeConnection()
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

    private byte[] getBitfieldPayload()
    {
        int numPieces = peer.numPieces;
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

    public void run()
    {
        try
        {
            InputStream in = serverSocket.getInputStream();
            this.out = serverSocket.getOutputStream();

            HandshakeMessage fromHandshake = new HandshakeMessage(peer.peerId);
            out.write(fromHandshake.toByteArray());
            out.flush();
            HandshakeMessage toHandshake = HandshakeMessage.unpack(in);
            this.peerId = toHandshake.getPeerID();

            if (isIncoming)
            {
                peer.logger.logConnectionFrom(peerId);
            } 
            else
            {
                peer.logger.logConnectionTo(peerId);
            }

            System.out.println("Handshake completed with Peer " + peerId);

            byte[] bitfieldPayload = getBitfieldPayload();
            Message bitfieldMessage = new Message(Message.MessageType.BITFIELD, bitfieldPayload);

            out.write(bitfieldMessage.toByteArray());
            out.flush();

            messageHandler = new MessageHandler(in, out, peer, this);
            messageHandler.handleMessage();
        } 
        catch (Exception e)
        {
            e.printStackTrace();
        } 
        finally
        {
            if (messageHandler != null)
            {
                boolean complete = true;
                for (int bit : messageHandler.remoteBitfield)
                {
                    if (bit == 0)
                    { 
                        complete = false;
                        break;
                    }
                }
                if (complete)
                {
                    peer.peersWithCompleteFile.add(peerId);
                    peer.checkTermination();
                }
            }
            closeConnection();
        }
    }
}
