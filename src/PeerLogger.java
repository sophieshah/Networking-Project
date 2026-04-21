import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class PeerLogger {
    private int peerId;
    private PrintWriter writer;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public PeerLogger(int peerId) {
        this.peerId = peerId;
        try {
            FileWriter fw = new FileWriter("log_peer_" + peerId + ".log", true);
            writer = new PrintWriter(new BufferedWriter(fw));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void log(String message) {
        String timestamp = sdf.format(new Date());
        writer.println("[" + timestamp + "]: " + message);
        writer.flush();
    }

    public void logTCPConnectionTo(int remotePeerId) {
        log("Peer " + peerId + " makes a connection to Peer " + remotePeerId + ".");
    }

    public void logTCPConnectionFrom(int remotePeerId) {
        log("Peer " + peerId + " is connected from Peer " + remotePeerId + ".");
    }

    public void logPreferredNeighbors(List<Integer> neighborIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < neighborIds.size(); i++) {
            sb.append(neighborIds.get(i));
            if (i < neighborIds.size() - 1) sb.append(",");
        }
        log("Peer " + peerId + " has the preferred neighbors [" + sb + "].");
    }

    public void logOptimisticallyUnchokedNeighbor(int neighborId) {
        log("Peer " + peerId + " has the optimistically unchoked neighbor " + neighborId + ".");
    }

    public void logUnchoked(int remotePeerId) {
        log("Peer " + peerId + " is unchoked by " + remotePeerId + ".");
    }

    public void logChoked(int remotePeerId) {
        log("Peer " + peerId + " is choked by " + remotePeerId + ".");
    }

    public void logHaveReceived(int remotePeerId, int pieceIndex) {
        log("Peer " + peerId + " received the 'have' message from " + remotePeerId + " for the piece " + pieceIndex + ".");
    }

    public void logInterestedReceived(int remotePeerId) {
        log("Peer " + peerId + " received the 'interested' message from " + remotePeerId + ".");
    }

    public void logNotInterestedReceived(int remotePeerId) {
        log("Peer " + peerId + " received the 'not interested' message from " + remotePeerId + ".");
    }

    public void logPieceDownloaded(int remotePeerId, int pieceIndex, int numPieces) {
        log("Peer " + peerId + " has downloaded the piece " + pieceIndex + " from " + remotePeerId + ". Now the number of pieces it has is " + numPieces + ".");
    }

    public void logDownloadComplete() {
        log("Peer " + peerId + " has downloaded the complete file.");
    }

    public void close() {
        if (writer != null) writer.close();
    }
}