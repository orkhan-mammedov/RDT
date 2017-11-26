JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $*.java

CLASSES = \
	Packet.java \
	SRPacket.java \
	CommonUtilities.java \
	GBNReceiver.java \
	GBNSender.java \
	SRReceiver.java \
	SRSender.java \

default: classes

all: classes

classes: $(CLASSES:.java=.class)
	chmod +x gbnReceiver
	chmod +x gbnSender
	chmod +x srReceiver
	chmod +x srSender

clean:
	$(RM) *.class