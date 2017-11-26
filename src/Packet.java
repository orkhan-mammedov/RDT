import java.nio.ByteBuffer;
import java.util.Arrays;

/*
 * This is a class which defines a packet format. The format of the message follows the one required by assignment.
 */
public class Packet {
    private int packetType;
    private int packetLength;
    private int sequenceNumber;
    private byte[] payload;

    public static final int PACKET_HEADER_SIZE = 12;
    public static final int PACKET_MAX_PAYLOAD_SIZE = 500;

    public Packet(int packetType, int packetLength, int sequenceNumber, byte[] payload){
        setPacketType(packetType);
        setPacketLength(packetLength);
        setSequenceNumber(sequenceNumber);
        setPayload(payload);
    }

    public int getPacketType() {
        return packetType;
    }

    public void setPacketType(int packetType) {
        this.packetType = packetType;
    }

    public int getPacketLength() {
        return packetLength;
    }

    public void setPacketLength(int packetLength) {
        this.packetLength = packetLength;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    /*
     * This method is used to convert the instance of Packet to byte array.
     */
    public static byte[] encodePacket(Packet packet){
        byte[] packetTypePart = ByteBuffer.allocate(4).putInt(packet.getPacketType()).array();
        byte[] packetLengthPart = ByteBuffer.allocate(4).putInt(packet.getPacketLength()).array();
        byte[] sequenceNumberPart = ByteBuffer.allocate(4).putInt(packet.getSequenceNumber()).array();
        byte[] encodedPacket;

        if(packet.getPacketType() == PacketType.DATA.getValue()){
            encodedPacket = new byte[packet.getPacketLength()];
        } else {
            encodedPacket = new byte[PACKET_HEADER_SIZE];
        }

        System.arraycopy(packetTypePart, 0, encodedPacket, 0, 4);
        System.arraycopy(packetLengthPart, 0, encodedPacket, 4, 4);
        System.arraycopy(sequenceNumberPart, 0, encodedPacket, 8, 4);

        if(packet.getPacketType() == PacketType.DATA.getValue()){
            System.arraycopy(packet.getPayload(), 0, encodedPacket, 12, packet.getPacketLength() - PACKET_HEADER_SIZE);
        }

        return encodedPacket;
    }

    /*
     * This method is used to convert byte array to the new instance of Packet.
     */
    public static Packet decodePacket(byte[] packet){
        int packetType = ByteBuffer.wrap(packet, 0, 4).getInt();
        int packetLength = ByteBuffer.wrap(packet, 4, 4).getInt();
        int sequenceNumber = ByteBuffer.wrap(packet, 8, 4).getInt();
        byte[] payload = new byte[0];

        if(packetType == PacketType.DATA.getValue()){
            payload = Arrays.copyOfRange(packet, 12, packetLength);
        }

        return new Packet(packetType, packetLength, sequenceNumber, payload);
    }

    public enum PacketType{
        DATA(0),
        ACKNOWLEDGEMENT(1),
        END_OF_TRANSFER(2);

        private int value;

        private PacketType(int value){
            this.value = value;
        }

        public int getValue(){
            return value;
        }
    }
}
