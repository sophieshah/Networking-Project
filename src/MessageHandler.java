import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.ByteBuffer;
import java.sql.Connection;

public class MessageHandler
{
    boolean isInterested;
    boolean isChoked = true;
    int[] remoteBitfield;
    Set<Integer> requestedPieces = new HashSet<>();
    private int piecesDownloaded = 0;


    private InputStream in;
    private OutputStream out;

    private peerProcess peer;
    private ConnectionHandler parent;
    
    public MessageHandler(InputStream in, OutputStream out, peerProcess peer, ConnectionHandler parent) 
    {
        this.in = in;
        this.out = out;
        this.peer = peer;
        this.parent = parent;
        this.remoteBitfield = new int[peer.numPieces];
    }

    public void handleMessage() throws IOException
    {
        try {
            while(true)
            {
                Message msg = Message.unpack(in);

                switch(msg.getMessageType())
                {

                    case BITFIELD:
                        handleBitfield(msg);
                        break;
                    
                    case INTERESTED:
                        handleInterested(msg);
                        break;

                    case NOT_INTERESTED:
                        handleNotInterested(msg);
                        break;

                    case REQUEST:
                        handleRequest(msg);
                        break;

                    case PIECE:
                        handlePiece(msg);
                        break;

                    case CHOKE:
                        handleChoke(msg);
                        break;

                    case UNCHOKE:
                        handleUnchoke(msg);
                        break;

                    case HAVE:
                        handleHave(msg);
                        break;
                }
            }
        } catch(IOException e) {
            System.out.println("Connection closed");
        }
        
    }

    private void handleBitfield(Message msg) throws IOException 
    {
        // after handshake A sends bitfield msg to B to know which file pieces it has
        // if B has pieces that A doesnt have then send interested msg to B
        // otherwise send not interested msg

        byte[] payload = msg.getPayload();

        for( int i=0; i<peer.numPieces; i++)
        {
            int byteIdx = i / 8;
            int bitIdx = 7 - (i % 8);
            if (byteIdx < payload.length) {
                int bit = (payload[byteIdx] >> bitIdx) & 1;
                remoteBitfield[i] = bit;
        }
        }


        this.isInterested = false;

        for(int i=0; i<peer.numPieces; i++)
        {
            if(remoteBitfield[i] == 1 && peer.bitfield[i] == 0)
            {
                this.isInterested = true;
                break;
            }
        }

        if(isInterested)
        {
            Message newMsg = new Message(Message.MessageType.INTERESTED);
            out.write(newMsg.toByteArray());
        }
        else
        {
            Message newMsg = new Message(Message.MessageType.NOT_INTERESTED);
            out.write(newMsg.toByteArray());
        }
        out.flush();
    }

    private void handleInterested(Message msg) throws IOException
    {
        isInterested = true;
    }

    private void handleNotInterested(Message msg) throws IOException
    {
        isInterested = false;
    }

    private void handleRequest(Message msg) throws IOException
    {
        // read requested index, load piece, send piece msg
        if(isChoked)
        {
            return;
        }

        int pieceIndex = msg.getPieceIndex();

        byte[] pieceData = loadPiece(pieceIndex);

        ByteBuffer buffer = ByteBuffer.allocate(4 + pieceData.length);
        buffer.putInt(pieceIndex);
        buffer.put(pieceData);

        Message pieceMsg = new Message(Message.MessageType.PIECE, buffer.array());
        out.write(pieceMsg.toByteArray());
        out.flush();
    }

    private void handlePiece(Message msg) throws IOException
    {
        //save piece, update bitfield(savePiece), send have msg(sendHaveToNeighbors), request another piece
        int pieceIndex = msg.getPieceIndex();
        byte[] piece = msg.getPiece();

        savePiece(pieceIndex, piece);
        peer.bitfield[pieceIndex] = 1;
        sendHaveToNeighbors(pieceIndex);

        if(!isChoked)
        {
            int next = selectRandomPiece();

            if(next != -1)
            {
                ByteBuffer buffer = ByteBuffer.allocate(4);
                buffer.putInt(next);

                Message requestMsg = new Message(Message.MessageType.REQUEST, buffer.array());
                out.write(requestMsg.toByteArray());
                out.flush();
            }
        }

        requestedPieces.remove(pieceIndex);
        updateNeighborInterest();
        incrementDownload();
    }

    private void handleChoke(Message msg) throws IOException
    {
        // when neighbor chokes, must stop requesting pieces
        isChoked = true;
    }

    private void handleUnchoke(Message msg) throws IOException
    {
        // when neighbor unchokes, choose a piece they have and send request
        isChoked = false;
        int pieceIndex = selectRandomPiece();

        if(pieceIndex != -1)
        {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(pieceIndex);

            Message requestMsg = new Message(Message.MessageType.REQUEST, buffer.array());
            out.write(requestMsg.toByteArray());
            out.flush();
        }
    }

    private void handleHave(Message msg) throws IOException
    {
        int pieceIndex = msg.getPieceIndex();

        remoteBitfield[pieceIndex] = 1;

        if(peer.bitfield[pieceIndex] == 0)
        {
            Message interestedMsg = new Message(Message.MessageType.INTERESTED);
            out.write(interestedMsg.toByteArray());
        }
        else
        {
            Message notInterestedMsg = new Message(Message.MessageType.NOT_INTERESTED);
            out.write(notInterestedMsg.toByteArray());
        }
        out.flush();
    }

    private int selectRandomPiece() throws IOException
    {
        List<Integer> availablePieces = new ArrayList<>();

        for(int i=0; i<peer.numPieces; i++)
        {
            if(peer.bitfield[i] == 0 && remoteBitfield[i] == 1 && !requestedPieces.contains(i))
            {
                availablePieces.add(i);
            }
        }
        if(availablePieces.isEmpty())
        {
            return -1;
        }
        
        Random r = new Random();
        int piece = availablePieces.get(r.nextInt(availablePieces.size()));
        requestedPieces.add(piece);

        return piece;
    }

    private byte[] loadPiece(int pieceIndex) throws IOException
    {
        String path = "peer_" + peer.peerId + "/" + peer.fileName;

        RandomAccessFile file = new RandomAccessFile(path, "r");
        int size = Math.min(peer.pieceSize, peer.fileSize - pieceIndex * peer.pieceSize);
        byte[] piece = new byte[size];

        file.seek(pieceIndex * peer.pieceSize);
        file.read(piece);
        file.close();

        return piece;
    }

    private void savePiece(int index, byte[] data) throws IOException
    {
        String path = "peer_" + peer.peerId + "/" + peer.fileName;

        RandomAccessFile file = new RandomAccessFile(path, "rw");

        file.seek(index * peer.pieceSize);
        file.write(data);
        file.close();
    }

    private void sendHaveToNeighbors(int pieceIndex) throws IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceIndex);

        Message haveMsg = new Message(Message.MessageType.HAVE, buffer.array());
        
        byte[] msgBytes = haveMsg.toByteArray();

        for(ConnectionHandler c : peer.connections)
        {
            c.sendMessage(msgBytes);
        }
    }

    private void updateNeighborInterest() throws IOException
    {
        boolean interested = false;

        for(int i = 0; i < peer.numPieces; i++)
        {
            if(remoteBitfield[i] == 1 && peer.bitfield[i] == 0)
            {
                interested = true;
                break;
            }
        }

        if(interested)
        {
            Message interestedMsg = new Message(Message.MessageType.INTERESTED);
            out.write(interestedMsg.toByteArray());
        }
        else
        {
            Message notInterestedMsg = new Message(Message.MessageType.NOT_INTERESTED);
            out.write(notInterestedMsg.toByteArray());
        }
        out.flush();
    }

    
    public synchronized int getAndResetPiecesDownloaded()
    {
        // get pieces downloaded this interval and reset counter for next intervall
        int temp = piecesDownloaded;
        piecesDownloaded = 0;
        return temp;
    }

    public synchronized void incrementDownload(){
        piecesDownloaded++;
    }

    private boolean allPiecesReceived(int[] bitfield)
    {
        for(int i=0; i<bitfield.length; i++)
        {
            if(bitfield[i] == 0)
            {
                return false;
            }
        }
        return true;
    }
