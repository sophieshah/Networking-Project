import java.io.*;

public class FileManager
{
    private peerProcess peer;
    private RandomAccessFile raf;

    public FileManager(peerProcess peer) throws IOException
    {
        this.peer = peer;
        String filePath = "peer_" + peer.peerId + "/" + peer.fileName;
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        if (!file.exists())
        {
            RandomAccessFile init = new RandomAccessFile(file, "rw");
            init.setLength(peer.fileSize);
            init.close();
        }
        this.raf = new RandomAccessFile(file, "rw");
    }

    public synchronized byte[] loadPiece(int pieceIndex) throws IOException
    {
        int size = Math.min(peer.pieceSize, peer.fileSize - pieceIndex * peer.pieceSize);
        byte[] piece = new byte[size];
        raf.seek((long) pieceIndex * peer.pieceSize);
        raf.readFully(piece);
        return piece;
    }

    public synchronized void savePiece(int pieceIndex, byte[] data) throws IOException
    {
        raf.seek((long) pieceIndex * peer.pieceSize);
        raf.write(data);
    }
}
