import java.io.*;
import java.nio.file.*;

public class FileManager {
    private String filePath;
    private int pieceSize;
    private int numPieces;
    private int fileSize;
    private RandomAccessFile raf;  // single shared file handle

    public FileManager(int peerId, String fileName, int pieceSize, int numPieces, int fileSize) {
        this.filePath = "peer_" + peerId + File.separator + fileName;
        this.pieceSize = pieceSize;
        this.numPieces = numPieces;
        this.fileSize = fileSize;
    }

    public void initEmptyFile() throws IOException {
        File f = new File(filePath);
        f.getParentFile().mkdirs();
        // Open a single shared handle for all reads and writes
        raf = new RandomAccessFile(f, "rw");
        if (f.length() != fileSize) {
            raf.setLength(fileSize);
        }
    }

    public void openExistingFile() throws IOException {
        File f = new File(filePath);
        f.getParentFile().mkdirs();
        raf = new RandomAccessFile(f, "rw");
    }

    public synchronized byte[] loadPiece(int pieceIndex) throws IOException {
        int size = getPieceSize(pieceIndex);
        byte[] data = new byte[size];
        raf.seek((long) pieceIndex * pieceSize);
        raf.readFully(data);
        return data;
    }

    public synchronized void savePiece(int pieceIndex, byte[] data) throws IOException {
        raf.seek((long) pieceIndex * pieceSize);
        raf.write(data);
    }

    private int getPieceSize(int pieceIndex) {
        if (pieceIndex == numPieces - 1) {
            int remainder = fileSize % pieceSize;
            return remainder == 0 ? pieceSize : remainder;
        }
        return pieceSize;
    }

    public void close() {
        try {
            if (raf != null) raf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}