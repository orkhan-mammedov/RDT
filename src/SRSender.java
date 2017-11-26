import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class SRSender {
    public static final int MAX_SEQUENCE_NUMBER = 256;
    public static final int WINDOW_SIZE = 10;
    private static Timer timer = new Timer();

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: srSender <timeout> <filename>");
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
            boolean firstPacketSent = false;
            boolean fileRead = false;
            int currentPayloadSize = 0;
            int nextSequenceNumber = 0;
            int currentBaseSequenceNumber = 0;
            byte[] buffer = new byte[Packet.PACKET_MAX_PAYLOAD_SIZE];
            Map<Integer, SRPacket> inFlightPackets = new HashMap<>();

            while (true) {
                if(nextSequenceNumber < currentBaseSequenceNumber + WINDOW_SIZE && !fileRead){
                    if((currentPayloadSize = bufferedInputStream.read(buffer)) <= 0){
                        fileRead = true;
                        continue;
                    }
                    long timestamp = System.currentTimeMillis();
                    SRPacket packet = new SRPacket(Packet.PacketType.DATA.getValue(), currentPayloadSize + Packet.PACKET_HEADER_SIZE,
                            nextSequenceNumber % MAX_SEQUENCE_NUMBER, Arrays.copyOf(buffer, currentPayloadSize), timestamp);
                    CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);
                    synchronized (inFlightPackets) {
                        inFlightPackets.put(nextSequenceNumber, packet);
                    }
                    if(!firstPacketSent){ // When the first packet is sent, the timer needs to be started
                        timer.schedule(new SRTimerTask(inFlightPackets, destinationHost, destinationPort, sourceSocket, timeout), timeout);
                        firstPacketSent = true;
                    }
                    nextSequenceNumber++;
                } else {
                    // Window is full, so need to process all the received ACKs from receiver
                    sourceSocket.receive(ackDatagramPacket);
                    Packet ackPacket = Packet.decodePacket(ackDatagramPacket.getData());
                    CommonUtilities.printLog(false, ackPacket);

                    int originalPacketPosition = ackPacket.getSequenceNumber() - (currentBaseSequenceNumber % SRSender.MAX_SEQUENCE_NUMBER);
                    if (ackPacket.getSequenceNumber() < SRSender.WINDOW_SIZE && SRSender.MAX_SEQUENCE_NUMBER - (currentBaseSequenceNumber % SRSender.MAX_SEQUENCE_NUMBER) <= SRSender.WINDOW_SIZE) {
                        originalPacketPosition = SRSender.MAX_SEQUENCE_NUMBER + ackPacket.getSequenceNumber() - (currentBaseSequenceNumber % SRSender.MAX_SEQUENCE_NUMBER);
                    }

                    if (originalPacketPosition >= 0 && originalPacketPosition < SRSender.WINDOW_SIZE) {
                        // Need to remove ACKed packet from inFlightPackets
                        synchronized (inFlightPackets) {
                            inFlightPackets.remove(currentBaseSequenceNumber + originalPacketPosition); // TODO Potential flow
                        }
                        if (originalPacketPosition == 0) {
                            // Update the base sequence number
                            int j = currentBaseSequenceNumber;
                            int ceilSequenceNumber = currentBaseSequenceNumber + SRSender.WINDOW_SIZE;
                            for (; j < ceilSequenceNumber; j++) {
                                if (inFlightPackets.containsKey(j)) {
                                    break;
                                }
                                currentBaseSequenceNumber++; // TODO: what if j = (currentBaseSequenceNumber % MAX_SEQUENCE_NUMBER) + WINDOW_SIZE + 1
                            }
                        }
                    }
                    if (fileRead && inFlightPackets.isEmpty()) {
                        break;
                    }
                }
            }

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

    private static class SRTimerTask extends TimerTask {
        private Map<Integer, SRPacket> inFlightPackets;
        private InetAddress destinationHost;
        private int destinationPort;
        private DatagramSocket sourceSocket;
        private long timeout;

        public SRTimerTask(Map<Integer, SRPacket> inFlightPackets,
                           InetAddress destinationHost, int destinationPort, DatagramSocket sourceSocket, long timeout) {
            this.inFlightPackets = inFlightPackets;
            this.destinationHost = destinationHost;
            this.destinationPort = destinationPort;
            this.sourceSocket = sourceSocket;
            this.timeout = timeout;
        }

        @Override
        public void run() {
            long minTime = Long.MAX_VALUE;
            timer.cancel();
            timer.purge();
            synchronized (inFlightPackets) {
                Set<Integer> keys = inFlightPackets.keySet();
                long currentTime = System.currentTimeMillis();
                for (int i : keys) {
                    if (currentTime >= inFlightPackets.get(i).getTimestamp()) {
                        SRPacket packet = inFlightPackets.get(i);
                        CommonUtilities.sendPacket(destinationHost, destinationPort, sourceSocket, packet);
                        packet.setTimestamp(System.currentTimeMillis() + timeout);
                        break;
                    }
                }

                for (int i : keys) {
                    if (inFlightPackets.get(i).getTimestamp() < minTime && currentTime < inFlightPackets.get(i).getTimestamp()) {
                        minTime = inFlightPackets.get(i).getTimestamp();
                    }
                }

                timer = new Timer();
                if(minTime == Long.MAX_VALUE) {
                    timer.schedule(new SRTimerTask(inFlightPackets, destinationHost, destinationPort, sourceSocket, timeout), timeout);
                } else {
                    timer.schedule(new SRTimerTask(inFlightPackets, destinationHost, destinationPort, sourceSocket, timeout), minTime - currentTime);
                }
            }
        }
    }
}
