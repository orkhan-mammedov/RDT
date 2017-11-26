
public class SRPacket extends Packet {
    private long timestamp;

    public SRPacket(int packetType, int packetLength, int sequenceNumber, byte[] payload, long timestamp) {
        super(packetType, packetLength, sequenceNumber, payload);
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
