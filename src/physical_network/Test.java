package physical_network;

/**
 * @author Timur Kuzhagaliyev
 */
public class Test {

    public static void main(String[] args) {
        int[] integers = new int[]{
                Integer.parseInt("1110011001100110", 2),
                Integer.parseInt("1101010101010101", 2)
        };
        int sum = 0;
        for (int i = 0; i < integers.length; i++) {
            int localSum = sum + integers[i];
            sum = (localSum & 0xffff) + (localSum >> 16);
        }
        int invertedSum = ~sum & 0xffff;
        System.out.println("> 1011101110111100");
        System.out.println("= " + Integer.toBinaryString(sum));
        System.out.println("> 0100010001000011");
        System.out.println("= " + Integer.toBinaryString(invertedSum));

        byte lowByte = (byte) (invertedSum & 0xff);
        byte highByte = (byte) ((invertedSum >> 8) & 0xff);

        System.out.println(Integer.toBinaryString(highByte) + "0" + Integer.toBinaryString(lowByte));
    }

}
