/*
 *  (c) K.Bryson, Dept. of Computer Science, UCL (2016)
 *  
 *  YOU MAY MODIFY THIS CLASS TO IMPLEMENT Stop & Wait ARQ PROTOCOL.
 *  (You will submit this class to Moodle.)
 *  
 */

package physical_network;

/**
 * Encapsulates the data for a network 'data frame'.
 * At the moment this just includes a data byte array.
 * This may need to be extended to include necessary header information.
 *
 * @author kevin-b
 */

public class DataFrame {

    private Header header;
    private Data data;

    public DataFrame() {
        this.data = new Data();
        this.header = new Header();
        this.header.setPayloadLength(this.data.getLength());
    }

    public DataFrame(byte[] payload) {
        this.data = new Data(payload);
        this.header = new Header();
        this.header.setPayloadLength(this.data.getLength());
    }

    public DataFrame(byte[] payload, int destination) {
        this(payload);
        this.header.setDestination(destination);
    }

    public DataFrame(String payload) {
        this(payload.getBytes());
    }

    public DataFrame(String payload, int destination) {
        this(payload.getBytes(), destination);
    }

    public String toString() {
        return data.toString();
    }

    /*
     * A factory method that can be used to create a data frame
     * from an array of bytes that have been received.
     */
    public static DataFrame createFromReceivedBytes(byte[] bytes) {

        // Invalid array
        if (bytes == null || bytes.length < 9) return null;

        int source = bytes[0] & 0xFF;
        int destination = bytes[1] & 0xFF;
        int ack = bytes[2] & 0xFF;
        int payloadLength = ((bytes[3] & 0xFF) << 8) | (bytes[4] & 0xFF);

        // Data length in the header does not match the received data
        if (bytes.length - 9 != payloadLength) return null;

        int headerChecksum = ((bytes[5] & 0xFF) << 8) | (bytes[6] & 0xFF);

        byte[] payload = new byte[payloadLength];
        System.arraycopy(bytes, 7, payload, 0, payloadLength);
        int payloadChecksum = ((bytes[bytes.length - 2] & 0xFF) << 8) | (bytes[bytes.length - 1] & 0xFF);

        DataFrame frame = new DataFrame();
        frame.getHeader().setSource(source);
        frame.getHeader().setDestination(destination);
        frame.getHeader().setAck(ack);
        frame.getHeader().setPayloadLength(payloadLength);
        frame.getHeader().setChecksum(headerChecksum);
        frame.getData().setBytes(payload);
        frame.getData().setChecksum16(payloadChecksum);

        return frame;
    }

    /*
     * This method should return the byte sequence of the transmitted bytes.
     * At the moment it is just the data data ... but extensions should
     * include needed header information for the data frame.
     * Note that this does not need sentinel or byte stuffing
     * to be implemented since this is carried out as the data
     * frame is transmitted and received.
     */
    public byte[] getTransmittedBytes() {
        byte[] header = this.header.getTransmittedBytes();
        byte[] data = this.data.getTransmittedBytes();
        return this.concat(header, data);
    }

    /**
     * Concatenates 2 byte arrays, here because I'm not sure whether we're allowed to import libraries
     */
    public byte[] concat(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        byte[] c = new byte[aLen + bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }

    public Header getHeader() {
        return header;
    }

    public Data getData() {
        return data;
    }

    /**
     * Represents the header of a data frame
     */
    public class Header {
        private int source8;
        private int destination8;
        private int ack8;
        private int payloadLength16;
        private int checksum16;

        private byte[] transmittedBytes;

        public boolean verifyChecksum() {
            return this.checksum16 == calculateChecksum();
        }

        public void updateChecksum() {
            this.checksum16 = calculateChecksum();
            this.transmittedBytes = new byte[]{
                    (byte) (this.source8 & 0xFF),
                    (byte) (this.destination8 & 0xFF),
                    (byte) (this.ack8 & 0xFF),
                    (byte) ((this.payloadLength16 >> 8) & 0xFF),
                    (byte) (this.payloadLength16 & 0xFF),
                    (byte) ((this.checksum16 >> 8) & 0xFF),
                    (byte) (this.checksum16 & 0xFF)
            };
        }

        private int calculateChecksum() {
            int[] integers = new int[]{
                    source8,
                    destination8,
                    ack8,
                    payloadLength16
            };
            int sum = 0;
            for (int integer : integers) {
                int localSum = sum + integer;
                sum = (localSum & 0xFFFF) + (localSum >> 16);
            }
            return ~sum & 0xFFFF;
        }

        public void setSource(int source8) {
            this.source8 = source8;
            updateChecksum();
        }

        public void setDestination(int destination8) {
            this.destination8 = destination8;
            updateChecksum();
        }

        public void setAck(int ack8) {
            this.ack8 = ack8;
            updateChecksum();
        }

        public void setPayloadLength(int payloadLength16) {
            this.payloadLength16 = payloadLength16;
            updateChecksum();
        }

        public void setChecksum(int checksum) {
            this.checksum16 = checksum;
        }

        public int getSource() {
            return source8;
        }

        public int getDestination() {
            return destination8;
        }

        public int getAck() {
            return ack8;
        }

        public byte[] getTransmittedBytes() {
            return transmittedBytes;
        }
    }

    /**
     * Represents the data of the data frame
     */
    public class Data {
        private byte[] bytes;
        private int checksum16;

        private byte[] transmittedBytes;

        public Data() {
            this.bytes = new byte[0];
        }

        public Data(byte[] payload) {
            this.bytes = payload;
            updateChecksum();
        }

        public boolean verifyChecksum() {
            return this.checksum16 == this.calculateChecksum();
        }

        public void updateChecksum() {
            this.checksum16 = calculateChecksum();
            byte[] checksum = new byte[]{
                    (byte) ((this.checksum16 >> 8) & 0xff),
                    (byte) (this.checksum16 & 0xff)
            };
            this.transmittedBytes = concat(this.bytes, checksum);
        }

        private int calculateChecksum() {
            int sum = 0;

            for (byte _byte : this.bytes) {
                int localSum = sum + (int) _byte;
                sum = (localSum & 0xffff) + (localSum >> 16);
            }

            return ~sum & 0xffff;
        }

        public String toString() {
            return new String(this.bytes);
        }

        public void setBytes(byte[] bytes) {
            this.bytes = bytes;
            updateChecksum();
        }

        public void setChecksum16(int checksum16) {
            this.checksum16 = checksum16;
        }

        public int getLength() {
            return this.bytes.length;
        }

        public byte[] getTransmittedBytes() {
            return transmittedBytes;
        }
    }
}

