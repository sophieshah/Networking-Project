import java.io.*;

public class FileManager
{
    private peerProcess peer;
    private RandomAccessFile raf;

    public FileManager(peerProcess peer) throws IOException
    {
        this.peer = peer;
        String path = "peer_" + peer.peerId + "/" + peer.fileName;
        File file = new File(path);
        file.getParentFile().mkdirs();
        if (!file.exists())
        {
            RandomAccessFile r = new RandomAccessFile(file, "rw");
            r.setLength(peer.fileSize);
            r.close();
        }
        this.raf = new RandomAccessFile(file, "rw");
    }

    public synchronized byte[] loadPiece(int pieceIndex) throws IOException
    {
        int size = Math.min(peer.pieceSize, peer.fileSize - pieceIndex * peer.pieceSize);
        byte[] piece = new byte[size];
        long val = (long)pieceIndex*peer.pieceSize;
        raf.seek(val);
        raf.readFully(piece);
        return piece;
    }

    public synchronized void savePiece(int pieceIndex, byte[] data) throws IOException
    {
        long val = (long) pieceIndex * peer.pieceSize;
        raf.seek(val);
        raf.write(data);
    }
}
