import java.io.*;
import java.util.*;

public class PeerLogger
{
    private int peerId;
    private PrintWriter writer;

    public PeerLogger(int peerId) throws IOException
    {
        this.peerId = peerId;
        writer = new PrintWriter(new FileWriter("log_peer_" + peerId + ".log", true));
    }

    private synchronized void log(String message)
    {
        writer.println("[" + new Date() + "]: " + message);
        writer.flush();
    }

    public void logConnectionTo(int remotePeerId)
    {
        log("Peer " + peerId + " makes a connection to Peer " + remotePeerId + ".");
    }

    public void logConnectionFrom(int remotePeerId)
    {
        log("Peer " + peerId + " is connected from Peer " + remotePeerId + ".");
    }

    public void logPreferredNeighbors(List<Integer> neighborIds)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < neighborIds.size(); i++)
        {
            if (i > 0)
            {
                sb.append(",");
            }
            sb.append(neighborIds.get(i));
        }
        log("Peer " + peerId + " has the preferred neighbors " + sb.toString() + ".");
    }

    public void logOptimisticNeighbor(int neighborId)
    {
        log("Peer " + peerId + " has the optimistically unchoked neighbor " + neighborId + ".");
    }

    public void logUnchoked(int remotePeerId)
    {
        log("Peer " + peerId + " is unchoked by " + remotePeerId + ".");
    }

    public void logChoked(int remotePeerId)
    {
        log("Peer " + peerId + " is choked by " + remotePeerId + ".");
    }

    public void logHaveReceived(int remotePeerId, int pieceIndex)
    {
        log("Peer " + peerId + " received the 'have' message from " + remotePeerId + " for the piece " + pieceIndex + ".");
    }

    public void logInterestedReceived(int remotePeerId)
    {
        log("Peer " + peerId + " received the 'interested' message from " + remotePeerId + ".");
    }

    public void logNotInterestedReceived(int remotePeerId)
    {
        log("Peer " + peerId + " received the 'not interested' message from " + remotePeerId + ".");
    }

    public void logPieceDownloaded(int remotePeerId, int pieceIndex, int totalPieces)
    {
        log("Peer " + peerId + " has downloaded the piece " + pieceIndex + " from " + remotePeerId + ". Now the number of pieces it has is " + totalPieces + ".");
    }

    public void logDownloadComplete()
    {
        log("Peer " + peerId + " has downloaded the complete file.");
    }
}
