import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class CommonUtilities {
    public static class ChannelInfo{
        private InetAddress destinationHost;
        private Integer destinationPort;

        public ChannelInfo(InetAddress destinationHost, Integer destinationPort) {
            this.destinationHost = destinationHost;
            this.destinationPort = destinationPort;
        }

        public InetAddress getDestinationHost() {
            return destinationHost;
        }

        public Integer getDestinationPort() {
            return destinationPort;
        }
    }


    public static ChannelInfo readChannelInfo(){
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("channelInfo")))) {
            String line = bufferedReader.readLine();
            String[] lineParts = line.split(" ");
            return new ChannelInfo(InetAddress.getByName(lineParts[0]), Integer.valueOf(lineParts[1]));
        } catch (IOException e) {
            System.out.println("Error occurred while reading channelInfo file ");
            e.printStackTrace();
            return null;
        }
    }

    public static void saveReceiverInfo(DatagramSocket socket){
        try{
            PrintWriter writer = new PrintWriter("recvInfo", "UTF-8");
            writer.print(InetAddress.getLocalHost().getHostAddress());
            writer.print(" ");
            writer.print(socket.getLocalPort());
            writer.close();
        } catch (IOException e) {
            System.out.println("Error occurred while creating file with address info ");
            e.printStackTrace();
        }
    }

    public static void printLog(boolean isSend, Packet packet){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("PKT ");
        if(isSend){
            stringBuilder.append("SEND ");
        } else {
            stringBuilder.append("RECV ");
        }
        if(packet.getPacketType() == Packet.PacketType.DATA.getValue()){
            stringBuilder.append("DAT ");
        } else if(packet.getPacketType() == Packet.PacketType.ACKNOWLEDGEMENT.getValue()){
            stringBuilder.append("ACK ");
        } else{
            stringBuilder.append("EOT ");
        }
        stringBuilder.append(packet.getPacketLength());
        stringBuilder.append(" ");
        stringBuilder.append(packet.getSequenceNumber());

        System.out.println(stringBuilder);
    }

    public static void sendPacket(InetAddress destinationHost, int destinationPort, DatagramSocket sourceSocket, Packet packet){
        try {
            byte[] encodedPacket = Packet.encodePacket(packet);
            DatagramPacket datagramPacket = new DatagramPacket(encodedPacket, encodedPacket.length, destinationHost, destinationPort);
            sourceSocket.send(datagramPacket);
            printLog(true, packet);
        } catch (IOException e) {
            System.out.println("Error occurred while sending the packet.");
            e.printStackTrace();
        }
    }
}
