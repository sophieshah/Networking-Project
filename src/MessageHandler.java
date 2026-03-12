package src;
import src.Message;
import src.ConnectionHandler;
import Random;

public class MessageHandler{
    boolean isInterested;
    boolean isChoked;
    
    private void handleMessage() throws IOException {
        while(true){
            Message msg = Message.unpack(in);

            switch(msg.getMessageType()){

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

    private void handleBitfield(Message msg) throws IOException {
        // after handshake A sends bitfield msg to B to know which file pieces it has
        // if B has pieces that A doesnt have then send interested msg to B
        // otherwise send not interested msg

        byte[] payload = msg.getPayload();

        boolean isInterested = false;

        for(int i=0; i<payload.length; i++){
            if(payload[i] == 1 && peer.bitfield[i] == 0){
                isInterested = true;
                break;
            }
        }

        if(isInterested){
            Message newMsg = new Message(Message.MessageType.INTERESTED);
            out.write(newMsg.toByteArray());
        }
        else{
            Message newMsg = new Message(Message.MessageType.NOT_INTERESTED);
            out.write(newMsg.toByteArray());
        }
    }

    private void handleInterested(Message msg) throws IOException {
        isInterested = true;
    }

    private void handleNotInterested(Message msg) throws IOException {
        isInterested = false;
    }

    private void handleRequest(Message msg) throws IOException {
        // read requested index, load piece, send piece msg
        int pieceIdx = msg.getPieceIndex();

        byte[] pieceData = loadPiece(pieceIdx);

        ByteBuffer buffer = ByteBuffer.allocate(4+ pieceData.length);
        buffer.putInt(pieceIdx);
        buffer.put(pieceData);

        Message pieceMsg = new Message(Message.MessageType.PIECE, buffer.array());
        out.write(pieceMsg.toByteArray());
        out.flush();
    }

    private void handlePiece(Message msg) throws IOException {
        //save piece, update bitfield(savePiece), send have msg(sendHaveToNeighbors), request another piece
        int pieceIdx = msg.getPieceIndex();
        byte[] piece = msg.getPiece();

        savePiece(pieceIdx, piece);
        peer.bitfield[pieceIdx] = 1;
        sendHaveToNeighbors(pieceIdx);

        if(!isChoked){
            int next = selectRandomPiece();

            if(next != -1){
                ByteBuffer buffer = ByteBuffer.allocate(4);
                buffer.putInt(next);

                Message requestMsg = new Message(Message.MessageType.REQUEST, buffer.array());
                out.write(requestMsg.toByteArray());
                out.flush();
            }
        }
    }

    private void handleChoke(Message msg) throws IOException {
        // when neighbor chokes, must stop requesting pieces
        isChoked = true;
    }

    private void handleUnchoke(Message msg) throws IOException {
        // when neighbor unchokes, choose a piece they have and send request
        isChoked = false;
        int pieceIdx = selectRandomPiece();

        if(pieceIdx != -1){
            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(pieceIdx);

            Message requestmsg = new Message(Message.MessageType.REQUEST, buffer.array());
            out.write(requestMsg.toByteArray());
            out.flush();
        }
    }

    private void handleHave(Message msg) throws IOException {
        int pieceIdx = msg.getPieceIndex();

        remoteBitfield[pieceIdx] = 1;

        if(peer.bitfield[pieceIdx] == 0){
            Message interestedMsg = new Message(Message.MessageType.INTERESTED);
            out.write(interestedMsg.toByteArray());
        }
        else{
            Message notInterestedMsg = new Message(Message.MessageType.NOT_INTERESTED);
            out.write(notInterestedMsg.toByteArray());
        }
        out.flush();
    }

    private int selectRandomPiece() throws IOException{
        List<Integer> availablePieces = new ArrayList<>();

        for(int i=0; <peer.numPieces; i++){
            if(peer.bitfield[i] == 0 && remoteBitfield[i] == 1){
                availablePieces.add(i);
            }
        }
        if(availablePieces.isEmpty()){
            return -1
        }
        
        Random r = new Random();
        return availablePieces.get(r.nextInt(availablePieces.size()));
    }

    private byte[] loadPiece(int pieceIdx) throws IOException{
        String path = "peer_" + peer.peerId + "/" + peer.fileName;

        RandomAccessFile file = new RandomAccessFile(path, "r");
        byte[] piece = new byte[peer.pieceSize];

        file.seek(pieceIdx * peer.pieceSize);
        file.read(piece);
        file.close();

        return piece;
    }

    private void savePiece(int idx, byte[] data) throws IOException{
        String path = "peer_" + peer.peerId + "/" + peer.fileName;

        RandomAccessFile file = new RandomAccessFile(path, "rw");
        byte[] piece = new byte[peer.pieceSize];

        file.seek(idx * peer.pieceSize);
        file.write(data);
        file.close();
    }

    private void sendHaveToNeighbors(int pieceIdx) throws IOException{
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceIndex);

        Message haveMsg = new Message(Message.MessageType.HAVE, buffer.array());
        
        byte[] msgBytes = haveMsg.toByteArray();

        for(ConnectionHandler c : peer.connections){
            c.out.write(msgBytes);
            c.out.flush();
        }
    }
}

