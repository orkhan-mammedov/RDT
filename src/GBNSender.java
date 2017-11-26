import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class GBNSender {
    public static final int MAX_SEQUENCE_NUMBER = 256;
    public static final int WINDOW_SIZE = 10;
    private static Timer timer = new Timer();

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: gbnSender <timeout> <filename>");
            System.exit(1);
        }

        int timeout = Integer.valueOf(args[0]);
        String fileName = String.valueOf(args[1]);
        CommonUtilities.ChannelInfo channelInfo = CommonUtilities.readChannelInfo();
        InetAddress destinationHost = channelInfo.getDestinationHost();
        int destinationPort = channelInfo.getDestinationPort();


        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(new File(fileName)))) {
            DatagramPacket ackDatagramPacket = new DatagramPacket(new byte[Packet.PACKET_HEADER_SIZE], Packet.PACKET_HEADER_SIZE);
            DatagramSocket sourceSocket = new DatagramSocket();
            int currentPayloadSize = 0;
            int nextSequenceNumber = 0;
            int currentBaseSequenceNumber = 0;
            byte[] buffer = new byte[Packet.PACKET_MAX_PAYLOAD_SIZE];
            Map<Integer, Packet> inFlightPackets = new HashMap<>();

            while (true) {
                if(nextSequenceNumber < currentBaseSequenceNumber + WINDOW_SIZE){
                    if((currentPayloadSize = bufferedInputStream.read(buffer)) <= 0){
                        break;
                    }
                    Packet packet = new Packet(Packet.PacketType.DATA.getValue(), currentPayloadSize + Packet.PACKET_HEADER_SIZE, nextSequenceNumber % MAX_SEQUENCE_NUMBER, Arrays.copyOf(buffer, currentPayloadSize));
                    CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);
                    if(currentBaseSequenceNumber == nextSequenceNumber){
                        timer.cancel();
                        timer.purge();
                        timer = new Timer();
                        timer.schedule(new GBNTimerTask(timeout, inFlightPackets, currentBaseSequenceNumber, destinationHost,
                                destinationPort, sourceSocket), timeout);
                    }
                    synchronized (inFlightPackets) {
                        inFlightPackets.put(nextSequenceNumber, packet);
                    }
                    nextSequenceNumber++;
                } else {
                    // Window is full, so need to wait to receive ACKs from receiver
                    sourceSocket.receive(ackDatagramPacket);
                    Packet ackPacket = Packet.decodePacket(ackDatagramPacket.getData());
                    CommonUtilities.printLog(false, ackPacket);
                    int acknowledgedPackets = ackPacket.getSequenceNumber() - (currentBaseSequenceNumber % MAX_SEQUENCE_NUMBER);
                    if(ackPacket.getSequenceNumber() < GBNSender.WINDOW_SIZE && GBNSender.MAX_SEQUENCE_NUMBER - (currentBaseSequenceNumber % GBNSender.MAX_SEQUENCE_NUMBER) <= GBNSender.WINDOW_SIZE){
                        acknowledgedPackets = GBNSender.MAX_SEQUENCE_NUMBER - (currentBaseSequenceNumber % GBNSender.MAX_SEQUENCE_NUMBER) + ackPacket.getSequenceNumber();
                    }

                    if (acknowledgedPackets < WINDOW_SIZE && acknowledgedPackets >= 0) {
                        // Remove from registrar map all the packets that were cumulatively acknowledged
                        for (int i = currentBaseSequenceNumber; i <= currentBaseSequenceNumber + acknowledgedPackets; i++) {
                            synchronized (inFlightPackets) {
                                inFlightPackets.remove(i);
                            }
                        }

                        // Update base sequence number
                        currentBaseSequenceNumber = currentBaseSequenceNumber + acknowledgedPackets + 1;

                        // Restart timer
                        if(currentBaseSequenceNumber == nextSequenceNumber){
                            timer.cancel();
                            timer.purge();
                            timer = new Timer();
                        } else {
                            timer.cancel();
                            timer.purge();
                            timer = new Timer();
                            timer.schedule(new GBNTimerTask(timeout, inFlightPackets, currentBaseSequenceNumber,
                                    destinationHost, destinationPort, sourceSocket), timeout);
                        }
                    }
                }
            }

            if (!inFlightPackets.isEmpty()) {
                // Wait for the remaining packets to be acknowledged
                while (!inFlightPackets.isEmpty()) {
                    sourceSocket.receive(ackDatagramPacket);
                    Packet incomePacket = Packet.decodePacket(ackDatagramPacket.getData());
                    CommonUtilities.printLog(false, incomePacket);
                    int acknowledgedPackets = (incomePacket.getSequenceNumber() - (currentBaseSequenceNumber % MAX_SEQUENCE_NUMBER));
                    if (acknowledgedPackets < WINDOW_SIZE && acknowledgedPackets >= 0) {// TODO: check <=
                        // Remove from our map all the packets that were cumulatively acknowledged
                        synchronized (inFlightPackets) {
                            for (int i = currentBaseSequenceNumber; i <= currentBaseSequenceNumber + acknowledgedPackets; i++) {
                                inFlightPackets.remove(i);
                            }
                        }

                        // Restart timer
                        timer.cancel();
                        timer.purge();
                        timer = new Timer();
                        timer.schedule(new GBNTimerTask(timeout, inFlightPackets, currentBaseSequenceNumber, destinationHost, destinationPort, sourceSocket), timeout);
                    }
                }
            }
            timer.cancel();
            timer.purge();

            // Send EOT to receiver
            Packet packet = new Packet(Packet.PacketType.END_OF_TRANSFER.getValue(), Packet.PACKET_HEADER_SIZE, currentBaseSequenceNumber % MAX_SEQUENCE_NUMBER, null);
            CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);

            // Wait for EOT from receiver
            sourceSocket.receive(ackDatagramPacket);
            Packet incomePacket = Packet.decodePacket(ackDatagramPacket.getData());
            CommonUtilities.printLog(false, incomePacket);
            while (incomePacket.getPacketType() != Packet.PacketType.END_OF_TRANSFER.getValue()) {
                sourceSocket.receive(ackDatagramPacket);
                incomePacket = Packet.decodePacket(ackDatagramPacket.getData());
                CommonUtilities.printLog(false, incomePacket);
            }
            sourceSocket.close();
            System.exit(0);
        } catch (IOException e) {
            System.out.println("Error occurred while reading/sending provided file ");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static class GBNTimerTask extends TimerTask {
        private int timeout;
        private Map<Integer, Packet> inFlightPackets;
        private int baseSequenceNumber;
        private InetAddress destinationHost;
        private int destinationPort;
        private DatagramSocket sourceSocket;

        public GBNTimerTask(int timeout, Map<Integer, Packet> inFlightPackets, int baseSequenceNumber,
                            InetAddress destinationHost, int destinationPort, DatagramSocket sourceSocket) {
            this.timeout = timeout;
            this.inFlightPackets = inFlightPackets;
            this.baseSequenceNumber = baseSequenceNumber;
            this.destinationHost = destinationHost;
            this.destinationPort = destinationPort;
            this.sourceSocket = sourceSocket;
        }

        @Override
        public void run() {
            timer.cancel();
            timer.purge();
            timer = new Timer();
            timer.schedule(new GBNTimerTask(timeout, inFlightPackets, baseSequenceNumber,
                                            destinationHost, destinationPort, sourceSocket), timeout);
            for (int i = baseSequenceNumber; i < baseSequenceNumber + WINDOW_SIZE; i++) {
                if (inFlightPackets.containsKey(i)) {
                    CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, inFlightPackets.get(i));
                }
            }
        }
    }
}
