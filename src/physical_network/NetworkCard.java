/*
 *  (c) K.Bryson, Dept. of Computer Science, UCL (2016)
 *  
 *  YOU MAY MODIFY THIS CLASS TO IMPLEMENT Stop & Wait ARQ PROTOCOL.
 *  (You will submit this class to Moodle.)
 *
 */

package physical_network;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.*;


/**
 * Represents a network card that can be attached to a particular wire.
 * <p>
 * It has only two key responsibilities:
 * i) Allow the sending of data frames consisting of arrays of bytes using send() method.
 * ii) Receives data frames into an input queue with a receive() method to access them.
 *
 * @author K. Bryson
 */

public class NetworkCard {

    /**
     * For how long to wait for an ACK, in milliseconds.
     */
    private final int TIMEOUT = 20000;

    /**
     * Set of all received ACKs
     */
    private HashSet<ACK> ackSet = new HashSet<ACK>();

    // Wire pair that the network card is atatched to.
    private final TwistedWirePair wire;

    // Unique device number and name given to the network card.
    private final int deviceNumber;
    private final String deviceName;

    // Default values for high, low and mid- voltages on the wire.
    private final double HIGH_VOLTAGE = 2.5;
    private final double LOW_VOLTAGE = -2.5;

    // Default value for a signal pulse width that should be used in milliseconds.
    private final int PULSE_WIDTH = 200;

    // Default value for maximum payload size in bytes.
    private final int MAX_PAYLOAD_SIZE = 1500;

    // Default value for input & output queue sizes.
    private final int QUEUE_SIZE = 5;

    /**
     * Queue of objects to be transmitted, could be DataFrame or ACK
     */
    private LinkedBlockingQueue<Object> outputQueue = new LinkedBlockingQueue<Object>(QUEUE_SIZE);

    // Input queue for dataframes being received.
    private LinkedBlockingQueue<DataFrame> inputQueue = new LinkedBlockingQueue<DataFrame>(QUEUE_SIZE);

    // Transmitter thread.
    private Thread txThread;

    // Receiver thread.
    private Thread rxThread;

    /**
     * NetworkCard constructor.
     *
     * @param number This provides the name of this device, i.e. "Network Card A".
     * @param wire   This is the shared wire that this network card is connected to.
     */
    public NetworkCard(int number, TwistedWirePair wire) {

        this.deviceNumber = number;
        this.deviceName = "NetCard" + number;
        this.wire = wire;

        txThread = this.new TXThread();
        rxThread = this.new RXThread();
    }

    /*
     * Initialize the network card.
     */
    public void init() {
        txThread.start();
        rxThread.start();
    }


    public void send(DataFrame data) throws InterruptedException {
        data.getHeader().setSource(this.deviceNumber);
        outputQueue.put(data);
    }

    public DataFrame receive() throws InterruptedException {
        return inputQueue.take();
    }

    private static class ACK {

        private int source8;
        private int destination8;
        private int number8;

        public ACK(int source8, int destination8, int number8) {
            this.source8 = source8;
            this.destination8 = destination8;
            this.number8 = number8;
        }

        public int getSource() {
            return source8;
        }

        public int getDestination() {
            return destination8;
        }

        public int getNumber() {
            return number8;
        }

        public byte[] getTransmittedBytes() {
            return new byte[]{
                    (byte) (this.source8 & 0xFF),
                    (byte) (this.getDestination() & 0xFF),
                    (byte) (this.getNumber() & 0xFF)
            };
        }
    }

    /*
     * Private inner thread class that transmits data.
     */
    private class TXThread extends CommThread {

        private int currentAckNumber = 0;

        public void run() {

            try {
                while (true) {
                    // Blocks if nothing is in queue.
                    Object transmitObject = outputQueue.take();

                    // Transmit the ACK
                    if (transmitObject instanceof ACK) {
                        this.transmitAck((ACK) transmitObject);
                        System.out.println("*** " + deviceName + " sent an ACK!");
                        continue;
                    }

                    // Unsupported object, ignore
                    if (!(transmitObject instanceof DataFrame)) continue;

                    DataFrame frame = (DataFrame) transmitObject;
                    frame.getHeader().setAck(currentAckNumber);

                    // Transmit the frame for the first time
                    this.transmitFrame(frame);

                    // Wait for ACK before proceeding, resend if no ACK is received
                    System.out.println("*** " + deviceName + " sent a frame, waiting for ACK...");
                    long startTime = System.currentTimeMillis();
                    ackAwait:
                    while (true) {
                        if (System.currentTimeMillis() - startTime > TIMEOUT) {
                            System.out.println("*** " + deviceName + " timed out while waiting for ACK! Resending frame...");
                            transmitFrame(frame);
                            startTime = System.currentTimeMillis();
                        }
                        for (Iterator<ACK> i = ackSet.iterator(); i.hasNext(); ) {
                            ACK ack = i.next();
                            if (ack.getDestination() == deviceNumber
                                    && ack.getSource() == frame.getHeader().getDestination()) {
                                if (ack.getNumber() == currentAckNumber) {
                                    // ACK received, move on to the next element in the queue
                                    System.out.println("*** " + deviceName + " received an ACK, moving on!");
                                    currentAckNumber = 1 - currentAckNumber;
                                    break ackAwait;
                                }
                                i.remove();
                            }
                        }
                        sleep(TIMEOUT / 10);
                    }
                }
            } catch (InterruptedException except) {
                System.out.println(deviceName + " Transmitter Thread Interrupted - terminated.");
            }

        }

        public void transmitAck(ACK ack) throws InterruptedException {
            if (ack != null) {
                this.transmitBytes(ack.getTransmittedBytes());
            }
        }

        /**
         * Tell the network card to send this data frame across the wire.
         * NOTE - THIS METHOD ONLY RETURNS ONCE IT HAS TRANSMITTED THE DATA FRAME.
         *
         * @param frame Data frame to transmit across the network.
         */
        public void transmitFrame(DataFrame frame) throws InterruptedException {
            if (frame != null) {
                this.transmitBytes(frame.getTransmittedBytes());
            }
        }

    }

    /*
     * Private inner thread class that receives data.
     */
    private class RXThread extends CommThread {

        /**
         * Map of ACK numbers from different hosts
         */
        private HashMap<Integer, Integer> ackMap = new HashMap<>();

        public void run() {

            try {

                // Listen for data frames.

                while (true) {

                    byte[] bytePayload = new byte[MAX_PAYLOAD_SIZE];
                    int bytePayloadIndex = 0;
                    byte receivedByte;

                    while (true) {
                        receivedByte = receiveByte();

                        if ((receivedByte & 0xFF) == 0x7E) break;

                        System.out.println(deviceName + " RECEIVED BYTE = " + Integer.toHexString(receivedByte & 0xFF));

                        // Unstuff if escaped.
                        if (receivedByte == 0x7D) {
                            receivedByte = receiveByte();
                            System.out.println(deviceName + " ESCAPED RECEIVED BYTE = " + Integer.toHexString(receivedByte & 0xFF));
                        }

                        bytePayload[bytePayloadIndex] = receivedByte;
                        bytePayloadIndex++;

                    }

                    byte[] relevantBytes = Arrays.copyOfRange(bytePayload, 0, bytePayloadIndex);

                    // If its an acknowledgement that is addressed to us, add it to the queue, otherwise treat it as a frame
                    if (relevantBytes.length == 3) {
                        int destination = relevantBytes[1] & 0xFF;

                        // Not addressed to us, ignore
                        if (destination != deviceNumber) continue;

                        ackSet.add(new ACK(
                                relevantBytes[0] & 0xFF,
                                destination,
                                relevantBytes[2] & 0xFF
                        ));
                        continue;
                    }

                    DataFrame frame = DataFrame.createFromReceivedBytes(relevantBytes);

                    // Check if data frame is corrupted
                    if (frame == null
                            || frame.getHeader() == null
                            || !frame.getHeader().verifyChecksum()
                            || frame.getData() == null
                            || !frame.getData().verifyChecksum()) {
                        System.out.println("*** " + deviceName + " received corrupted data frame! Ignoring.");
                        continue;
                    }

                    DataFrame.Header header = frame.getHeader();

                    // Check if data frame addressed to us
                    int destination = header.getDestination();
                    if (destination != deviceNumber) {
                        System.out.println(
                                "*** " + deviceName + " received data frame addressed to " + destination + ". Ignoring."
                        );
                        continue;
                    }

                    // Check if we've already processed this frame (we have to send the ACK anyway)
                    Integer lastAckNumber = ackMap.get(header.getSource());
                    if (lastAckNumber != null && lastAckNumber == header.getAck()) {
                        // We've already processed this frame, ignore it
                        continue;
                    }
                    ackMap.put(header.getSource(), header.getAck());

                    // Block receiving data if queue full
                    inputQueue.put(frame);

                    // Frame is not corrupted and is addressed to us, send ACK
                    System.out.println(
                            "*** " + deviceName + " received a frame from " + header.getSource() + ", adding ACK to queue!"
                    );
                    ACK ack = new ACK(
                            header.getDestination(),
                            header.getSource(),
                            header.getAck()
                    );
                    outputQueue.add(ack);
                }

            } catch (InterruptedException except) {
                System.out.println(deviceName + " Interrupted: " + getName());
            }

        }

    }

    /**
     * Implements methods for transmitting and receiving bytes
     */
    private class CommThread extends Thread {

        protected void transmitBytes(byte[] bytes) throws InterruptedException {
            // Low voltage signal to get ready ...
            wire.setVoltage(deviceName, LOW_VOLTAGE);
            sleep(PULSE_WIDTH * 4);

            // Send bytes in asynchronous style with 0.2 seconds gaps between them.
            for (byte _byte : bytes) {

                // Byte stuff if required.
                if (_byte == 0x7E || _byte == 0x7D)
                    transmitByte((byte) 0x7D);

                transmitByte(_byte);
            }

            // Append a 0x7E to terminate frame.
            transmitByte((byte) 0x7E);

            wire.setVoltage(deviceName, 0);
        }

        protected void transmitByte(byte value) throws InterruptedException {

            // Low voltage signal ...
            wire.setVoltage(deviceName, LOW_VOLTAGE);
            sleep(PULSE_WIDTH * 4);

            // Set initial pulse for asynchronous transmission.
            wire.setVoltage(deviceName, HIGH_VOLTAGE);
            sleep(PULSE_WIDTH);

            // Go through bits in the value (big-endian bits first) and send pulses.

            for (int bit = 0; bit < 8; bit++) {
                if ((value & 0x80) == 0x80) {
                    wire.setVoltage(deviceName, HIGH_VOLTAGE);
                } else {
                    wire.setVoltage(deviceName, LOW_VOLTAGE);
                }


                // Shift value.
                value <<= 1;

                sleep(PULSE_WIDTH);
            }
        }

        protected byte receiveByte() throws InterruptedException {

            double thresholdVoltage = (LOW_VOLTAGE + 2.0 * HIGH_VOLTAGE) / 3;
            byte value = 0;

            while (wire.getVoltage(deviceName) < thresholdVoltage) {
                sleep(PULSE_WIDTH / 10);
            }

            // Sleep till middle of next pulse.
            sleep(PULSE_WIDTH + PULSE_WIDTH / 2);

            // Use 8 next pulses for byte.
            for (int i = 0; i < 8; i++) {

                value *= 2;

                if (wire.getVoltage(deviceName) > thresholdVoltage) {
                    value += 1;
                }

                sleep(PULSE_WIDTH);
            }

            return value;
        }

    }

}
