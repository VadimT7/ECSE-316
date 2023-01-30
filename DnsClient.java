import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.*;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Random;

public class DnsClient {
    private static int MAX_RETRIES = 3;
    private static int TIMEOUT = 5000;
    private static int PORT = 53;
    private static String QUERY_TYPE = "A";
    private static String SERVER_NAME;
    private static String IPAddress;
    private static byte[] parsedIPAddress = new byte[4]; // IP Address without dots
    private static final int BUFFER_SIZE = 512; // Number of bytes used for the response buffer

    public static void main(String[] args) throws IOException {
        // parse command line arguments
        if (args.length < 2) {
            System.out.println("ERROR   Not enough arguments were supplied.");
            System.exit(1);
        }
        fetchArguments(args);

        try {
            byte[] request = constructRequest(SERVER_NAME);
            InetAddress dnsAddress = InetAddress.getByAddress(parsedIPAddress);
            byte[] response = new byte[BUFFER_SIZE];

            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT);
            DatagramPacket requestPacket = new DatagramPacket(request, request.length, dnsAddress, PORT);
            DatagramPacket responsePacket = new DatagramPacket(response, response.length);

            int retryCount = 0;
            while (retryCount < MAX_RETRIES) {
                try {
                    long sendStartTime = System.currentTimeMillis();
                    socket.send(requestPacket);
                    socket.receive(responsePacket);
                    long sendEndTime = System.currentTimeMillis();

                    System.out.println("Response received after " + (sendEndTime - sendStartTime) / 1000 + " seconds (" + retryCount + "retries)");

                    if (checkResponse(request, response)) {
                        printResult(response);
                        break;
                    } else {
                        System.out.println("ERROR   Unexpected response. Retrying...");
                        retryCount++;
                    }
                } catch (IOException e) {
                    System.out.println("ERROR   Request timed out. Retrying...");
                    retryCount++;
                }
            }

            if (retryCount == MAX_RETRIES) {
                System.out.println("ERROR   Maximum retry limit reached. Stopping execution.");
            }
            socket.close();
        } catch (UnknownHostException e) {
            System.out.println("ERROR   Unable to resolve DNS server address: " + IPAddress);
        } catch (SocketException e) {
            System.out.println("ERROR   Error creating socket: " + e.getMessage());
        }
    }

    private static void printResult(byte[] response) {
    }

    private static boolean checkResponse(byte[] request, byte[] response) {
        int idx = 12;
        int qdcount = (response[4] & 0xff) << 8 | (response[5] & 0xff);
        if (qdcount != 1) {
            return false;
        }
        while (response[idx] != 0) {
            idx += (response[idx] & 0xff) + 1;
        }
        idx += 5;
        int ancount = (response[6] & 0xff) << 8 | (response[7] & 0xff);
        int nameLength = (response[idx] & 0xff) << 8 | (response[idx + 1] & 0xff);
        if (nameLength != (response.length - idx - 10)) {
            return false;
        }
        for (int i = 0; i < nameLength; i++) {
            if (response[idx + 2 + i] != request[idx + 2 + i]) {
                return false;
            }
        }
        int type = (response[idx + nameLength + 2] & 0xff) << 8 | (response[idx + nameLength + 3] & 0xff);
        if (type != 1) {
            return false;
        }
        int rdlength = (response[idx + nameLength + 8] & 0xff) << 8 | (response[idx + nameLength + 9] & 0xff);
        if (rdlength != 4) {
            return false;
        }
        return true;
    }


    private static byte[] constructRequest(String domainName) {
        byte[] request = new byte[12 + domainName.length() + 1];
        int requestId = (int) (Math.random() * 65536);

        request[0] = (byte) (requestId >> 8);
        request[1] = (byte) requestId;
        request[2] = (byte) 1;
        request[3] = (byte) 0;
        request[4] = (byte) 0;
        request[5] = (byte) 1;
        request[6] = (byte) 0;
        request[7] = (byte) 0;
        request[8] = (byte) 0;
        request[9] = (byte) 0;
        request[10] = (byte) 0;
        request[11] = (byte) 0;

        int index = 12;
        String[] domainNameParts = domainName.split("\\.");
        for (int i = 0; i < domainNameParts.length; i++) {
            request[index++] = (byte) domainNameParts[i].length();
            parsedIPAddress[i] = (byte) Integer.parseInt(domainNameParts[i]);
            for (int j = 0; j < domainNameParts[i].length(); j++) {
                request[index++] = (byte) domainNameParts[i].charAt(j);
            }
        }
        request[index++] = (byte) 0;

        return request;
    }


    // Constructs the DNS query packet based on the hostname and query type

    /*private static byte[] constructRequest(String domainName, String queryType) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        //Generate random transaction ID for query
        Random r = new Random();
        int transactionID = r.nextInt(65535);
        dos.writeShort(transactionID);

        //Set flags
        int flags = 0x0100;
        dos.writeShort(flags);

        //Number of questions
        dos.writeShort(0x0001);

        //Number of answers
        dos.writeShort(0x0000);

        //Number of authority
        dos.writeShort(0x0000);

        //Number of additional
        dos.writeShort(0x0000);

        String[] separated_ip_address = IPAddress.split("\\."); //

        for (int i = 0; i < separated_ip_address.length; i++) {
            parsedIPAddress[i] = (byte) Integer.parseInt(separated_ip_address[i]);
        }

        //Write the query
        String[] domainParts = domainName.split("\\.");
        for(String domainPart : separated_ip_address) {
            parsedIPAddress[i] = (byte) Integer.parseInt(domainPart);
            //byte[] domainBytes = domainPart.getBytes("UTF-8");
            dos.writeByte(parsedIPAddress.length);
            dos.write(parsedIPAddress);
        }
        dos.writeByte(0x00);
        dos.writeChars(queryType);
        dos.writeShort(0x0001);

        return baos.toByteArray();
    }
*/

    private static void parseResponse(byte[] response) {
        // parses the DNS response packet
        // ...
    }

    public static void fetchArguments(String args[]) {
        ListIterator<String> iter = Arrays.asList(args).listIterator();

        while (iter.hasNext()) {
            String argument = iter.next();
            switch (argument) {
                case "-t":
                    TIMEOUT = Integer.parseInt(iter.next()) * 1000;
                    break;
                case "-r":
                    MAX_RETRIES = Integer.parseInt(iter.next());
                    break;
                case "-p":
                    PORT = Integer.parseInt(iter.next());
                    break;
                case "-mx":
                    QUERY_TYPE = "MX";
                    break;
                case "-ns":
                    QUERY_TYPE = "ns";
                    break;
            }

            if (argument.charAt(0) == '@') {
                IPAddress = argument.substring(1); // remove the "@"

                if (iter.next() != null) {
                    SERVER_NAME = iter.next();
                } else {
                    throw new IllegalArgumentException("ERROR   No Domain Name Was Found. Please provide a domain name as an argument following the IP Address.");
                }
            }
        }
    }
}

