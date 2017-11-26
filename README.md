# Reliable Data Transfer

Reliable Data Transfer is working implementation of two reliable protocols used in computer networks, which are Go-Back-N and Selective Repeat.

## How to build?

To build senders and receiver, run: make all.

## Design

- Packet (Packet.java)
  This class is the representation of the packet that is being created and sent by Go-Back-N Sender and received by Go-Back-N Receiver. The class has two static methods encodePacket() and decodePacket() which are used to convert Java Packet object to byte stream and byte stream to Java Packet object respectively

- Common Utilities (CommonUtilities.java)
  This class contains only static methods which are used by GBN and SR senders and receivers. The methods readChannelInfo() and saveReceiverInfo() are used for reading and creating addressing files. printLog() method is used to print log messages to console. sendPacket() is convenience method used for sending the provided Packet to specified address.

- Go-Back-N Sender (GBNSender.java)
  This class implements the sender part of Go-Back-N protocol. It uses one Java Timer object to start countown for the lowest (in terms of sequence number) packet in window when needed. The timer also has GBNTimerTask class instance which describes that the fact that the whole window needs to be resend on the timeout event. Apart from above there is one while loop, inside which the portions of file are read and send to receiver until window is full. When window is full the sender starts calls receive() method on its socket to get ACKs from receiver. When ACK is received the sender checks if it is greater than current base sequence number, and if it is then the timer is restarted.
  The above process continues unitll the whole file is read and sent. After that END_OF_TRANSFER type packet is sent to receiver. When END_OF_TRANSFER type packet is received from receiver the program terminates.

- Go-Back-N Receiver (GBNReceiver.java)
  This class implements the receiver part of Go-Back-N protocol. It also has while loop. The receiver then calls receive() on its socket, in order to wait for incoming data packets. Once received, the sequence number of the packet is compared to the expected sequence number, and if it mathes then ACK is sent to sender with the same seuqnce number. If sequence numbers do not match then receiver resend the last ACK packet. Once EOT packet is received, receiver send EOT packet to sender and exits.

 - Selective Repeat Sender (SRSender.java)
  This class implements the sender part of Selective Repeat protocol. It uses one Java Timer object to start countown for the lowest (in terms of timestamp) packet in window when needed. Appart from one timer, it also keeps track of all the inflight packets in the hash map, with keys being sequnce number and value being SRPacket object (which extends Packet class, by adding additianal field timestamp, the anticipated time of arrival). This way, whenever the pakcet is ACKed by receiver, sender would remove that packet from the map. In addition, when the timer timeouts, we first check if there is any packet with timestamp grater than the current time. If there is one we resend it. Then we reschedule timer with timeout = current time - packet with the smallest timestamp. The above process continues unitll the whole file is read and sent. After that END_OF_TRANSFER type packet is sent to receiver. When END_OF_TRANSFER type packet is received from receiver the program terminates.

- Selective Repeat Receiver (SRReceiver.java)
  This class implements the receiver part of Selective Repeat protocol. It also has while loop. The receiver then calls receive() on its socket, in order to wait for incoming data packets. Once received, ACK is sent to sender with the same seuqnce number. If the secuence was out of order we keep that packet in the map. If it was expected sequence number we wite out that packet to file and then check to see if there are any conitinious packets in the map. If there are then those packets are written out to the file, and removed from map. Once EOT packet is received, receiver send EOT packet to sender and exits.