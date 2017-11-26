import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class GBNReceiver {

    public static void main(String[] args) {
        if(args.length == 1){
            int expectedSequenceNumber = 0;
            String fileName = args[0];
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(fileName);
                DatagramPacket datagramPacket = new DatagramPacket(new byte[Packet.PACKET_MAX_PAYLOAD_SIZE + Packet.PACKET_HEADER_SIZE], Packet.PACKET_MAX_PAYLOAD_SIZE + Packet.PACKET_HEADER_SIZE);
                DatagramSocket sourceSocket = new DatagramSocket();
                CommonUtilities.saveReceiverInfo(sourceSocket);
                sourceSocket.receive(datagramPacket);
                Packet incomePacket = Packet.decodePacket(datagramPacket.getData());
                InetAddress destinationHost = datagramPacket.getAddress();
                int destinationPort = datagramPacket.getPort();
                CommonUtilities.printLog(false, incomePacket);

                while (incomePacket.getPacketType() != Packet.PacketType.END_OF_TRANSFER.getValue()) {
                    if (incomePacket.getSequenceNumber() == expectedSequenceNumber % GBNSender.MAX_SEQUENCE_NUMBER) {
                        Packet packet = new Packet(Packet.PacketType.ACKNOWLEDGEMENT.getValue(), Packet.PACKET_HEADER_SIZE, expectedSequenceNumber % GBNSender.MAX_SEQUENCE_NUMBER, null);
                        CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);
                        fileOutputStream.write(incomePacket.getPayload());
                        expectedSequenceNumber++;
                    } else if(incomePacket.getSequenceNumber() < expectedSequenceNumber % GBNSender.MAX_SEQUENCE_NUMBER){
                        Packet packet = new Packet(Packet.PacketType.ACKNOWLEDGEMENT.getValue(), Packet.PACKET_HEADER_SIZE, (expectedSequenceNumber - 1) % GBNSender.MAX_SEQUENCE_NUMBER, null);
                        CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);
                    } else if(incomePacket.getSequenceNumber() - (expectedSequenceNumber % GBNSender.MAX_SEQUENCE_NUMBER) >= GBNSender.WINDOW_SIZE){
                        Packet packet = new Packet(Packet.PacketType.ACKNOWLEDGEMENT.getValue(), Packet.PACKET_HEADER_SIZE, incomePacket.getSequenceNumber(), null);
                        CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);
                    }
                    sourceSocket.receive(datagramPacket);
                    incomePacket = Packet.decodePacket(datagramPacket.getData());
                    CommonUtilities.printLog(false, incomePacket);
                }

                fileOutputStream.close();

                Packet packet = new Packet(Packet.PacketType.END_OF_TRANSFER.getValue(), Packet.PACKET_HEADER_SIZE, expectedSequenceNumber % GBNSender.MAX_SEQUENCE_NUMBER, null);
                CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);
                sourceSocket.close();
            } catch (IOException e){
                System.out.println("Error during sending or receiving packets ");
                e.printStackTrace();
            }
        } else {
            System.out.println("Usage: gbnReceiver <filename>");
        }
    }
}
