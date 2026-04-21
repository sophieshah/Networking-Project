import java.io.*;
import java.nio.ByteBuffer;

public class Message
{
    // message 
    public enum MessageType
    {
        CHOKE,
        UNCHOKE,
        INTERESTED,
        NOT_INTERESTED,
        HAVE,
        BITFIELD,
        REQUEST,
        PIECE
    }

    private int length; // 4-byte length field not included
    private byte type;
    private byte[] payload;

    // Constructor for have, bitfield, request, and piece messages
    public Message(MessageType messageType, byte[] payload) {
        this.type = (byte) messageType.ordinal();
        this.payload = payload;
        this.length = 1 + this.payload.length;
    }

    // Constructor for choke, unchoke, interested, and not interested messages
    public Message(MessageType messageType)
    {
        this.type = (byte) messageType.ordinal();
        this.payload = new byte[0];
        this.length = 1 + this.payload.length;
    }

    public MessageType getMessageType()
    {
        return MessageType.values()[this.type];
    }

    public byte[] getPayload()
    {
        return this.payload;
    }

    public int getLength()
    {
        return this.length;
    }

    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(type);
        if (payload.length > 0)
        {
            buffer.put(payload);
        }
        return buffer.array();
    }

    public static Message unpack(InputStream message) throws IOException
    {
        DataInputStream in = new DataInputStream(message);

        int length = in.readInt();           // guaranteed 4 bytes
        byte type = in.readByte();           // guaranteed 1 byte

        int payloadLength = length - 1;
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0)
        {
            in.readFully(payload);           // guaranteed all bytes
        }

        return new Message(MessageType.values()[type], payload);
    }

    // For have, bitfield, request, and piece
    public int getPieceIndex()
    {
        return ByteBuffer.wrap(payload).getInt();
    }

    public byte[] getPiece()
    {
        byte[] data = new byte[payload.length - 4];
        System.arraycopy(payload, 4, data, 0, payload.length - 4);
        return data;
    }
}