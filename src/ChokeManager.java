import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

public class ChokeManager {
    private peerProcess peer;
    private Timer preferredNeighborTimer;
    private Timer optimisticUnchokeTimer;

    // Tracks bytes downloaded from each peer during current interval
    // key = peerId, value = bytes downloaded from them
    private Map<Integer, Long> downloadRates = new HashMap<>();

    // Currently unchoked peers (preferred + optimistic)
    private Set<Integer> unchokedPeers = new HashSet<>();
    private int optimisticallyUnchokedPeer = -1;

    public ChokeManager(peerProcess peer) {
        this.peer = peer;
    }

    public synchronized void recordDownload(int fromPeerId, int bytes) {
        downloadRates.merge(fromPeerId, (long) bytes, Long::sum);
    }

    public void start() {
        preferredNeighborTimer = new Timer(true);
        preferredNeighborTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                recalculatePreferredNeighbors();
            }
        }, peer.unchokingInterval * 1000L, peer.unchokingInterval * 1000L);

        optimisticUnchokeTimer = new Timer(true);
        optimisticUnchokeTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                recalculateOptimisticUnchoke();
            }
        }, peer.OptimisticUnchokingInterval * 1000L, peer.OptimisticUnchokingInterval * 1000L);
    }

    public void stop() {
        if (preferredNeighborTimer != null) preferredNeighborTimer.cancel();
        if (optimisticUnchokeTimer != null) optimisticUnchokeTimer.cancel();
    }

    private synchronized void recalculatePreferredNeighbors() {
        // Collect all interested neighbors
        List<ConnectionHandler> interested = new ArrayList<>();
        for (ConnectionHandler c : peer.connections) {
            if (c.isInterested) {
                interested.add(c);
            }
        }

        // Sort by download rate (descending), or randomly if peer has complete file
        boolean hasCompleteFile = peer.hasCompleteFile();
        if (hasCompleteFile) {
            Collections.shuffle(interested);
        } else {
            interested.sort((a, b) -> {
                long rateA = downloadRates.getOrDefault(a.peerId, 0L);
                long rateB = downloadRates.getOrDefault(b.peerId, 0L);
                if (rateA != rateB) return Long.compare(rateB, rateA);
                return new Random().nextInt(3) - 1; // random tiebreak
            });
        }

        // Pick top k preferred neighbors
        Set<Integer> newPreferred = new HashSet<>();
        int k = Math.min(peer.neighbors, interested.size());
        for (int i = 0; i < k; i++) {
            newPreferred.add(interested.get(i).peerId);
        }

        // Send choke/unchoke messages as needed
        for (ConnectionHandler c : peer.connections) {
            boolean shouldBeUnchoked = newPreferred.contains(c.peerId)
                    || c.peerId == optimisticallyUnchokedPeer;
            boolean currentlyUnchoked = unchokedPeers.contains(c.peerId);

            if (shouldBeUnchoked && !currentlyUnchoked) {
                sendUnchoke(c);
            } else if (!shouldBeUnchoked && currentlyUnchoked) {
                sendChoke(c);
            }
        }

        unchokedPeers.clear();
        unchokedPeers.addAll(newPreferred);
        if (optimisticallyUnchokedPeer != -1) {
            unchokedPeers.add(optimisticallyUnchokedPeer);
        }

        // Log preferred neighbors
        List<Integer> preferredList = new ArrayList<>(newPreferred);
        peer.logger.logPreferredNeighbors(preferredList);

        // Reset download rate counters for next interval
        downloadRates.clear();
    }

    private synchronized void recalculateOptimisticUnchoke() {
        // Collect choked but interested neighbors
        List<ConnectionHandler> candidates = new ArrayList<>();
        for (ConnectionHandler c : peer.connections) {
            boolean isPreferred = unchokedPeers.contains(c.peerId)
                    && c.peerId != optimisticallyUnchokedPeer;
            if (c.isInterested && !isPreferred) {
                candidates.add(c);
            }
        }

        if (candidates.isEmpty()) return;

        Collections.shuffle(candidates);
        ConnectionHandler chosen = candidates.get(0);

        // Unchoke the new optimistic peer if not already unchoked
        if (!unchokedPeers.contains(chosen.peerId)) {
            sendUnchoke(chosen);
            unchokedPeers.add(chosen.peerId);
        }

        optimisticallyUnchokedPeer = chosen.peerId;
        peer.logger.logOptimisticallyUnchokedNeighbor(optimisticallyUnchokedPeer);
    }

    private void sendChoke(ConnectionHandler c) {
        try {
            Message msg = new Message(Message.MessageType.CHOKE);
            c.sendMessage(msg.toByteArray());
            c.isChokingRemote = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendUnchoke(ConnectionHandler c) {
        try {
            Message msg = new Message(Message.MessageType.UNCHOKE);
            c.sendMessage(msg.toByteArray());
            c.isChokingRemote = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}