import java.io.*;

public class FileManager
{
    private peerProcess peer;
    private String filePath;

    public FileManager(peerProcess peer) throws IOException
    {
        this.peer = peer;
        this.filePath = "peer_" + peer.peerId + "/" + peer.fileName;
        initFile();
    }

    private void initFile() throws IOException
    {
        File file = new File(filePath);
        if (!file.exists())
        {
            file.getParentFile().mkdirs();
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            raf.setLength(peer.fileSize);
            raf.close();
        }
    }

    public byte[] loadPiece(int pieceIndex) throws IOException
    {
        int size = Math.min(peer.pieceSize, peer.fileSize - pieceIndex * peer.pieceSize);
        byte[] piece = new byte[size];

        RandomAccessFile raf = new RandomAccessFile(filePath, "r");
        raf.seek((long) pieceIndex * peer.pieceSize);
        raf.read(piece);
        raf.close();

        return piece;
    }

    public void savePiece(int pieceIndex, byte[] data) throws IOException
    {
        RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
        raf.seek((long) pieceIndex * peer.pieceSize);
        raf.write(data);
        raf.close();
    }
}
