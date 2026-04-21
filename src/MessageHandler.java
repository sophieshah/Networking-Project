import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.ByteBuffer;

public class MessageHandler {
    private InputStream in;
    private ConnectionHandler conn;
    private peerProcess peer;

    public MessageHandler(InputStream in, ConnectionHandler conn, peerProcess peer) {
        this.in = in;
        this.conn = conn;
        this.peer = peer;
    }

    public void handleMessage() throws IOException {
        while (true) {
            Message msg = Message.unpack(in);

            switch (msg.getMessageType()) {
                case BITFIELD:      handleBitfield(msg);      break;
                case INTERESTED:    handleInterested(msg);    break;
                case NOT_INTERESTED:handleNotInterested(msg); break;
                case REQUEST:       handleRequest(msg);       break;
                case PIECE:         handlePiece(msg);         break;
                case CHOKE:         handleChoke(msg);         break;
                case UNCHOKE:       handleUnchoke(msg);       break;
                case HAVE:          handleHave(msg);          break;
            }
        }
    }

    private void handleBitfield(Message msg) throws IOException {
        byte[] payload = msg.getPayload();

        // Unpack bitfield bytes into conn.remoteBitfield
        for (int i = 0; i < peer.numPieces; i++) {
            int byteIdx = i / 8;
            int bitIdx = 7 - (i % 8);
            if (byteIdx < payload.length && ((payload[byteIdx] >> bitIdx) & 1) == 1) {
                conn.remoteBitfield[i] = 1;
            } else {
                conn.remoteBitfield[i] = 0;
            }
        }

        if (isInterested()) {
            conn.sendMessage(new Message(Message.MessageType.INTERESTED).toByteArray());
        } else {
            conn.sendMessage(new Message(Message.MessageType.NOT_INTERESTED).toByteArray());
        }

        // If this peer already has everything, check termination
        peer.checkAllComplete();
    }

    private void handleInterested(Message msg) throws IOException {
        conn.isInterested = true;
        peer.logger.logInterestedReceived(conn.peerId);
    }

    private void handleNotInterested(Message msg) throws IOException {
        conn.isInterested = false;
        peer.logger.logNotInterestedReceived(conn.peerId);
    }

    private void handleRequest(Message msg) throws IOException {
        if (conn.isChokingRemote) return;

        int pieceIndex = msg.getPieceIndex();
        byte[] pieceData;
        try {
            pieceData = peer.fileManager.loadPiece(pieceIndex);
        } catch (IOException e) {
            System.err.println("Could not load piece " + pieceIndex + ": " + e.getMessage());
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(4 + pieceData.length);
        buffer.putInt(pieceIndex);
        buffer.put(pieceData);

        conn.sendMessage(new Message(Message.MessageType.PIECE, buffer.array()).toByteArray());
    }

    private void handlePiece(Message msg) throws IOException {
        int pieceIndex = msg.getPieceIndex();
        byte[] piece = msg.getPiece();

        // Ignore duplicate pieces we already have
        if (peer.bitfield[pieceIndex] == 1) return;

        peer.chokeManager.recordDownload(conn.peerId, piece.length);

        peer.fileManager.savePiece(pieceIndex, piece);
        peer.bitfield[pieceIndex] = 1;

        int numPieces = peer.getPieceCount();
        peer.logger.logPieceDownloaded(conn.peerId, pieceIndex, numPieces);

        if (peer.hasCompleteFile()) {
            peer.logger.logDownloadComplete();
        }

        // Broadcast 'have' to all neighbors
        sendHaveToNeighbors(pieceIndex);

        // Request another piece if still unchoked
        if (!conn.isChoked) {
            int next = selectRandomPiece();
            if (next != -1) {
                ByteBuffer buffer = ByteBuffer.allocate(4);
                buffer.putInt(next);
                conn.sendMessage(new Message(Message.MessageType.REQUEST, buffer.array()).toByteArray());
            }
        }

        // Check if everyone is done
        peer.checkAllComplete();
    }

    private void handleChoke(Message msg) throws IOException {
        conn.isChoked = true;
        peer.logger.logChoked(conn.peerId);
    }

    private void handleUnchoke(Message msg) throws IOException {
        conn.isChoked = false;
        peer.logger.logUnchoked(conn.peerId);

        int pieceIndex = selectRandomPiece();
        if (pieceIndex != -1) {
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(pieceIndex);
            conn.sendMessage(new Message(Message.MessageType.REQUEST, buffer.array()).toByteArray());
        }
    }

    private void handleHave(Message msg) throws IOException {
        int pieceIndex = msg.getPieceIndex();
        conn.remoteBitfield[pieceIndex] = 1;

        peer.logger.logHaveReceived(conn.peerId, pieceIndex);

        if (peer.bitfield[pieceIndex] == 0) {
            conn.sendMessage(new Message(Message.MessageType.INTERESTED).toByteArray());
        } else if (!isInterested()) {
            conn.sendMessage(new Message(Message.MessageType.NOT_INTERESTED).toByteArray());
        }

        // Check termination every time a neighbor reports a new piece
        peer.checkAllComplete();
    }

    // Returns true if remote has at least one piece we don't have
    private boolean isInterested() {
        for (int i = 0; i < peer.numPieces; i++) {
            if (conn.remoteBitfield[i] == 1 && peer.bitfield[i] == 0) return true;
        }
        return false;
    }

    private int selectRandomPiece() {
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < peer.numPieces; i++) {
            if (peer.bitfield[i] == 0 && conn.remoteBitfield[i] == 1) {
                available.add(i);
            }
        }
        if (available.isEmpty()) return -1;
        return available.get(new Random().nextInt(available.size()));
    }

    private void sendHaveToNeighbors(int pieceIndex) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceIndex);
        byte[] msgBytes = new Message(Message.MessageType.HAVE, buffer.array()).toByteArray();

        for (ConnectionHandler c : peer.connections) {
            try {
                c.sendMessage(msgBytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}