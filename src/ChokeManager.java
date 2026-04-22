import java.io.*;
import java.util.*;

public class ChokeManager implements Runnable
{
    private peerProcess peer;
    private ConnectionHandler optimisticNeighbor;

    public ChokeManager(peerProcess peer)
    {
        this.peer = peer;
    }

    public void run()
    {
        new Thread(() -> {
            while(true)
            {
                try
                {
                    Thread.sleep(peer.unchokingInterval * 1000);
                    updatePreferred();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(() -> {
            while(true)
            {
                try
                {
                    Thread.sleep(peer.OptimisticUnchokingInterval * 1000);
                    updateOptimistic();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void updatePreferred()
    {
        List<ConnectionHandler> interested = new ArrayList<>();

        synchronized (peer.connections)
        {
            for (ConnectionHandler c : peer.connections)
            {
                if (c.remoteInterestedInMe)
                    interested.add(c);
            }
        }

        List<ConnectionHandler> preferred = new ArrayList<>();

        if (hasCompleteFile())
        {
            Collections.shuffle(interested);
        }
        else
        {
            Map<ConnectionHandler, Integer> rates = new HashMap<>();
            for (ConnectionHandler c : interested)
            {
                int rate = c.messageHandler != null ? c.messageHandler.getAndResetPiecesDownloaded() : 0;
                rates.put(c, rate);
            }
            Random rand = new Random();
            interested.sort((a, b) -> {
                int diff = rates.get(b) - rates.get(a);
                if (diff != 0) {
                    return diff;
                }
               if (rand.nextInt(2) == 0)
                {
                    return 1;
                }
                return -1;
            });
        }

        for (int i = 0; i < interested.size() && i < peer.neighbors; i++)
        {
            preferred.add(interested.get(i));
        }

        List<Integer> preferredIds = new ArrayList<>();
        for (ConnectionHandler c : preferred)
            preferredIds.add(c.peerId);
        peer.logger.logPreferredNeighbors(preferredIds);

        synchronized (peer.connections)
        {
            for (ConnectionHandler c : peer.connections)
            {
                if (c.messageHandler != null)
                {
                    boolean complete = true;
                    for (int bit : c.messageHandler.remoteBitfield)
                    {
                        if (bit == 0) { complete = false; break; }
                    }
                    if (complete)
                    {
                        peer.peersWithCompleteFile.add(c.peerId);
                    }
                }
            }
        }
        peer.checkTermination();

        synchronized (peer.connections)
        {
            for (ConnectionHandler c : peer.connections)
            {
                if (preferred.contains(c))
                {
                    if (c.isChoked)
                    {
                        c.isChoked = false;
                        sendMessage(c, new Message(Message.MessageType.UNCHOKE));
                    }
                }
                else if (c != optimisticNeighbor)
                {
                    if (!c.isChoked)
                    {
                        c.isChoked = true;
                        sendMessage(c, new Message(Message.MessageType.CHOKE));
                    }
                }
            }
        }
    }

    private void updateOptimistic()
    {
        List<ConnectionHandler> candidates = new ArrayList<>();

        synchronized (peer.connections)
        {
            for (ConnectionHandler c : peer.connections)
            {
                if (c.isChoked && c.remoteInterestedInMe)
                {
                    candidates.add(c);
                }
            }
        }

        if (candidates.isEmpty())
        {
            return;
        }

        Collections.shuffle(candidates);
        ConnectionHandler chosen = candidates.get(0);

        if (optimisticNeighbor != null && optimisticNeighbor != chosen)
        {
            optimisticNeighbor.isChoked = true;
            sendMessage(optimisticNeighbor, new Message(Message.MessageType.CHOKE));
        }

        optimisticNeighbor = chosen;
        peer.logger.logOptimisticNeighbor(chosen.peerId);
        chosen.isChoked = false;
        sendMessage(chosen, new Message(Message.MessageType.UNCHOKE));
    }

    private boolean hasCompleteFile()
    {
        for (int bit : peer.bitfield)
        {
            if (bit == 0)
            {
                return false;
            }
        }
        return true;
    }

    private void sendMessage(ConnectionHandler c, Message msg)
    {
        if (c.out != null)
        {
            try
            {
                synchronized (c.out)
                {
                    c.out.write(msg.toByteArray());
                    c.out.flush();
                }
            }
            catch (IOException e)
            {
                // connection already closed, skip
            }
        }
    }
}
