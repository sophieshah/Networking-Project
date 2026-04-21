import java.io.*;
import java.net.*;

public class ConnectionHandler implements Runnable{
    private Socket serverSocket;
    public OutputStream out;
    private peerProcess peer;
    int peerId;
    public MessageHandler messageHandler;
    public boolean hasCompleteFile = false;

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

    public void run()
    {
        try
        {
            InputStream in = serverSocket.getInputStream();
            OutputStream out = serverSocket.getOutputStream();

            HandshakeMessage fromHandshake = new HandshakeMessage(peerId);
            out.write(fromHandshake.toByteArray());
            HandshakeMessage toHandshake = HandshakeMessage.unpack(in);
            int peerId = toHandshake.getPeerID();

            System.out.println("Handshake completed with Peer " + peerId);

            byte[] bitfieldPayload = getBitfieldPayload();
            Message bitfieldMessage = new Message(Message.MessageType.BITFIELD, bitfieldPayload);

            out.write(bitfieldMessage.toByteArray());

            // Have to handle logging 

            messageHandler = new MessageHandler(in, out, peer, this);
            messageHandler.handleMessage();
        } 
        catch (Exception e)
        {
            e.printStackTrace();
        } 
        finally
        {
            closeConnection();
        }
    }
}