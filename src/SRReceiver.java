import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class SRReceiver {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: srReceiver <filename>");
            System.exit(1);
        }

        int expectedSequenceNumber = 0;
        String fileName = args[0];
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(fileName);
            DatagramPacket datagramPacket = new DatagramPacket(new byte[Packet.PACKET_MAX_PAYLOAD_SIZE + Packet.PACKET_HEADER_SIZE], Packet.PACKET_MAX_PAYLOAD_SIZE + Packet.PACKET_HEADER_SIZE);
            DatagramSocket sourceSocket = new DatagramSocket();
            CommonUtilities.saveReceiverInfo(sourceSocket);
            Map<Integer, Packet> outOfOrderPackets = new HashMap<>();
            sourceSocket.receive(datagramPacket);
            Packet incomePacket = Packet.decodePacket(datagramPacket.getData());
            InetAddress destinationHost = datagramPacket.getAddress();
            int destinationPort = datagramPacket.getPort();
            CommonUtilities.printLog(false, incomePacket);

            // Receive data packets from sender until EOT packet
            while (incomePacket.getPacketType() != Packet.PacketType.END_OF_TRANSFER.getValue()) {
                int originalPacketPosition = incomePacket.getSequenceNumber() - (expectedSequenceNumber % SRSender.MAX_SEQUENCE_NUMBER);
                if (incomePacket.getSequenceNumber() < SRSender.WINDOW_SIZE && SRSender.MAX_SEQUENCE_NUMBER - (expectedSequenceNumber % SRSender.MAX_SEQUENCE_NUMBER) <= SRSender.WINDOW_SIZE) {
                    originalPacketPosition = SRSender.MAX_SEQUENCE_NUMBER + incomePacket.getSequenceNumber() - (expectedSequenceNumber % SRSender.MAX_SEQUENCE_NUMBER);
                } else if ((expectedSequenceNumber % SRSender.MAX_SEQUENCE_NUMBER) < SRSender.WINDOW_SIZE && SRSender.MAX_SEQUENCE_NUMBER - incomePacket.getSequenceNumber() <= SRSender.WINDOW_SIZE) {
                    originalPacketPosition = incomePacket.getSequenceNumber() - SRSender.MAX_SEQUENCE_NUMBER - (expectedSequenceNumber % SRSender.MAX_SEQUENCE_NUMBER);
                }
                if (originalPacketPosition >= 0 && originalPacketPosition < SRSender.WINDOW_SIZE) {
                    Packet packet = new Packet(Packet.PacketType.ACKNOWLEDGEMENT.getValue(), Packet.PACKET_HEADER_SIZE, incomePacket.getSequenceNumber(), null);
                    CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);
                    outOfOrderPackets.put(expectedSequenceNumber + originalPacketPosition, incomePacket);

                    // Check if we have received the packet that matches our expected base sequence number
                    if(originalPacketPosition == 0){
                        int i = expectedSequenceNumber;
                        int ceilSequenceNumber = expectedSequenceNumber + SRSender.WINDOW_SIZE;
                        // Check if buffer contains packets to be written to file
                        for(; i < ceilSequenceNumber; i++){
                            if(!outOfOrderPackets.containsKey(i)){
                                break;
                            } else {
                                fileOutputStream.write(outOfOrderPackets.get(i).getPayload());
                                outOfOrderPackets.remove(i);
                                expectedSequenceNumber++;
                            }
                        }
                    }
                } else if(originalPacketPosition < 0){
                    Packet packet = new Packet(Packet.PacketType.ACKNOWLEDGEMENT.getValue(), Packet.PACKET_HEADER_SIZE, incomePacket.getSequenceNumber(), null);
                    CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);
                }
                sourceSocket.receive(datagramPacket);
                incomePacket = Packet.decodePacket(datagramPacket.getData());
                CommonUtilities.printLog(false, incomePacket);
            }
            fileOutputStream.close();

            // Send EOT packet and exit
            Packet packet = new Packet(Packet.PacketType.END_OF_TRANSFER.getValue(), Packet.PACKET_HEADER_SIZE, expectedSequenceNumber % SRSender.MAX_SEQUENCE_NUMBER, null);
            CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);
            sourceSocket.close();
        } catch (IOException e){
            System.out.println("Error during sending or receiving packets ");
            e.printStackTrace();
        }
    }
}

