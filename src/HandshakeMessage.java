import java.io.*;
import java.nio.ByteBuffer;

public class HandshakeMessage
{
    private static String header = "P2PFILESHARINGPROJ";
    private static int zeroBits = 10;
    private static int messageLength = 32;

    private int peerID = 4;
    public HandshakeMessage(int peerId)
    {
        this.peerID = peerId;
    }

    public int getPeerID()
    {
        return this.peerID;
    }

    public byte[] toByteArray()
    {
        ByteBuffer buffer = ByteBuffer.allocate(messageLength);
        buffer.put(header.getBytes());
        buffer.put(new byte[zeroBits]);
        buffer.putInt(peerID);
        return buffer.array();
    }

    public static HandshakeMessage unpack(InputStream stream) throws IOException
    {
        DataInputStream in = new DataInputStream(stream);
        byte[] data = new byte[messageLength];
        in.readFully(data);

        // Header will alwyas be "P2PFILESHARINGPROJ"
        String h = new String(data, 0, 18);

        int peerID = ByteBuffer.wrap(data, 28, 4).getInt();
        
        return new HandshakeMessage(peerID);
    }
}