import java.io.*;
import java.net.*;
import java.util.*;

public class ConnectionHandler implements Runnable{
    private Socket serverSocket;
    private peerProcess peer;
    int peerID;

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

            HandshakeMessage fromHandshake = new HandshakeMessage(peerID);
            out.write(fromHandshake.toByteArray());
            HandshakeMessage toHandshake = HandshakeMessage.unpack(in);
            int peerID = toHandshake.getPeerID();

            System.out.println("Handshake completed with Peer " + peerID);

            byte[] bitfieldPayload = getBitfieldPayload();
            Message bitfieldMessage = new Message(Message.MessageType.BITFIELD, bitfieldPayload);

            out.write(bitfieldMessage.toByteArray());

            // Have to handle logging 
            
            MessageHandler message = new MessageHandler(in, out);
            message.handleMessage();
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