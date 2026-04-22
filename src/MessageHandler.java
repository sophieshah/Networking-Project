import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.ByteBuffer;

public class MessageHandler
{
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
        try
        {
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
        } 
        catch(IOException e)
        {
            System.out.println("Connection closed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleBitfield(Message msg) throws IOException 
    {
        // after handshake A sends bitfield msg to B to know which file pieces it has
        // if B has pieces that A doesnt have then send interested msg to B
        // otherwise send not interested msg

        byte[] payload = msg.getPayload();

        for (int i=0; i<peer.numPieces; i++)
        {
            int byteIndex = i / 8;
            int bitIndex = 7 - (i % 8);
            if (byteIndex < payload.length)
            {
                int bit = (payload[byteIndex] >> bitIndex) & 1;
                remoteBitfield[i] = bit;
            }
        }

        parent.amInterestedInRemote = false;

        for(int i=0; i<peer.numPieces; i++)
        {
            if(remoteBitfield[i] == 1 && peer.bitfield[i] == 0)
            {
                parent.amInterestedInRemote = true;
                break;
            }
        }

        // Check if neighbor is complete
        boolean complete = true;
        for (int i = 0; i < remoteBitfield.length; i++)
        {
            if (remoteBitfield[i] == 0)
            {
                complete = false;
                break;
            }
        }
        if (complete)
        {
            peer.peersWithCompleteFile.add(parent.peerId);
            peer.checkTermination();
        }

        if(parent.amInterestedInRemote)
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
        parent.remoteInterestedInMe = true;
        peer.logger.logInterestedReceived(parent.peerId);
    }

    private void handleNotInterested(Message msg) throws IOException
    {
        parent.remoteInterestedInMe = false;
        peer.logger.logNotInterestedReceived(parent.peerId);
    }

    private void handleRequest(Message msg) throws IOException
    {
        // read requested index, load piece, send piece msg
        if(parent.isChoked)
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
        //save piece, update bitfield(savePiece), send have msg(sendHave), request another piece
        int pieceIndex = msg.getPieceIndex();
        byte[] piece = msg.getPiece();

        savePiece(pieceIndex, piece);
        peer.bitfield[pieceIndex] = 1;
        sendHave(pieceIndex);

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

        int count = 0;
        for (int i = 0; i < peer.bitfield.length; i++)
        {
            if (peer.bitfield[i] == 1)
            {
                count++;
            }
        }
        peer.logger.logPieceDownloaded(parent.peerId, pieceIndex, count);

        if (count == peer.numPieces)
        {
            peer.logger.logDownloadComplete();
            peer.peersWithCompleteFile.add(peer.peerId);
            peer.checkTermination();
        }
    }

    private void handleChoke(Message msg) throws IOException
    {
        isChoked = true;
        peer.logger.logChoked(parent.peerId);
    }

    private void handleUnchoke(Message msg) throws IOException
    {
        isChoked = false;
        peer.logger.logUnchoked(parent.peerId);
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
        peer.logger.logHaveReceived(parent.peerId, pieceIndex);

        remoteBitfield[pieceIndex] = 1;

        boolean complete = true;
        for (int i = 0; i < remoteBitfield.length; i++)
        {
            if (remoteBitfield[i] == 0)
            {
                complete = false;
                break;
            }
        }
        if (complete)
        {
            peer.peersWithCompleteFile.add(parent.peerId);
            peer.checkTermination();
        }

        boolean nowInterested = peer.bitfield[pieceIndex] == 0;
        if (nowInterested && !parent.amInterestedInRemote)
        {
            parent.amInterestedInRemote = true;
            Message interestedMsg = new Message(Message.MessageType.INTERESTED);
            out.write(interestedMsg.toByteArray());
            out.flush();
        }
        else if (!nowInterested && parent.amInterestedInRemote)
        {
            parent.amInterestedInRemote = false;
            Message notInterestedMsg = new Message(Message.MessageType.NOT_INTERESTED);
            out.write(notInterestedMsg.toByteArray());
            out.flush();
        }
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
        return peer.fileManager.loadPiece(pieceIndex);
    }

    private void savePiece(int index, byte[] data) throws IOException
    {
        peer.fileManager.savePiece(index, data);
    }

    private void sendHave(int pieceIndex) throws IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceIndex);

        Message haveMsg = new Message(Message.MessageType.HAVE, buffer.array());
        
        byte[] msgBytes = haveMsg.toByteArray();

        for (int i = 0; i < peer.connections.size(); i++)
        {
            ConnectionHandler c = peer.connections.get(i);
            if (c.out != null)
            {
                synchronized (c.out)
                {
                    c.out.write(msgBytes);
                    c.out.flush();
                }
            }
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

        if(interested == parent.amInterestedInRemote)
        {
            return;
        }

        parent.amInterestedInRemote = interested;

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

    public synchronized void incrementDownload()
    {
        piecesDownloaded++;
    }

}
