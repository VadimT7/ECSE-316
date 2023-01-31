import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.*;
import java.util.Arrays;
import java.util.ListIterator;

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
        if (args.length < 2) {
            System.out.println("ERROR   Not enough arguments were supplied.");
            System.exit(1);
        }

        // Parse command line arguments
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
                    System.out.println("Dns Client sending request for " + SERVER_NAME);
                    System.out.println("Server: " + IPAddress);
                    System.out.println("Request type: " + QUERY_TYPE);

                    // Send the request and then receive it back from the server
                    long sendStartTime = System.currentTimeMillis();
                    socket.send(requestPacket);
                    socket.receive(responsePacket);
                    long sendEndTime = System.currentTimeMillis();

                    System.out.println("Response received after " + (sendEndTime - sendStartTime) / 1000 + " seconds (" + retryCount + " retries)");

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

    public static void fetchArguments(String args[]) {
        ListIterator<String> iter = Arrays.asList(args).listIterator();

        while (iter.hasNext()) {
            String argument = iter.next();
            switch (argument) {
                case "-mx":
                    QUERY_TYPE = "MX";
                    break;
                case "-ns":
                    QUERY_TYPE = "NS";
                    break;
                case "-t":
                    String timeoutValue = iter.next();
                    TIMEOUT = Integer.parseInt(timeoutValue) * 1000;
                    break;
                case "-p":
                    String portNumber = iter.next();
                    PORT = Integer.parseInt(portNumber);
                    break;
                case "-r":
                    String maxRetries = iter.next();
                    MAX_RETRIES = Integer.parseInt(maxRetries);
                    break;
            }

            if (argument.charAt(0) == '@') {
                IPAddress = argument.substring(1); // Remove the "@" from the IP Address

                // Separate the IPAddress provided as an argument into separate parts for easier future usability
                String[] ipAddressParts = IPAddress.split("\\.");
                for (int i = 0; i < ipAddressParts.length; i++) {
                    parsedIPAddress[i] = (byte) Integer.parseInt(ipAddressParts[i]); // set the global variable for future use
                }

                if (iter.hasNext()) {
                    SERVER_NAME = iter.next(); // Fetch the domain name
                } else {
                    throw new IllegalArgumentException("ERROR   No Domain Name Was Found. Please provide a Domain Name argument following the IP Address argument.");
                }
            }
        }
    }

    private static void printResult(byte[] response) {
        int pointer = 12;
        int answerCount = (response[6] & 0xff) << 8 | (response[7] & 0xff);

        for (int i = 0; i < answerCount; i++) {
            int length = (response[pointer + 1] & 0xff) << 8 | (response[pointer + 2] & 0xff);
            pointer += length + 5;
        }

        System.out.println("***Answer Section (" + answerCount + " records)***");
        for (int i = 0; i < answerCount; i++) {
            int length = (response[pointer + 1] & 0xff) << 8 | (response[pointer + 2] & 0xff);
            if ((response[pointer] & 0xff) == 1) {
                System.out.println("IPv4    " + (response[pointer + 3] & 0xff) + "." + (response[pointer + 4] & 0xff) + "." + (response[pointer + 5] & 0xff) + "." + (response[pointer + 6] & 0xff));
            } else if ((response[pointer] & 0xff) == 28) {
                System.out.println("IPv6    " + String.format("%x", response[pointer + 3] & 0xff) + ":" + String.format("%x", response[pointer + 4] & 0xff) + ":" + String.format("%x", response[pointer + 5] & 0xff) + ":" + String.format("%x", response[pointer + 6] & 0xff) + ":" + String.format("%x", response[pointer + 7] & 0xff) + ":" + String.format("%x", response[pointer + 8] & 0xff) + ":" + String.format("%x", response[pointer + 9] & 0xff) + ":" + String.format("%x", response[pointer + 10] & 0xff));
            }
            pointer += length + 5;
        }
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
        String[] domainNameParts = domainName.split("\\."); // remove the dot from the domain
        byte[] request = new byte[12 + domainNameParts.length + (domainName.length() - (domainNameParts.length - 1)) + 1]; // calculate the necessary number of bytes to allocate to the request
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

        // Add the domain name to the array representing the request
        int index = 12;
        for (int i = 0; i < domainNameParts.length; i++) {
            request[index++] = (byte) domainNameParts[i].length(); // store the length of the string
            for (int j = 0; j < domainNameParts[i].length(); j++) { // store the string itself
                request[index++] = (byte) domainNameParts[i].charAt(j); // character by character
            }
        }
        request[index] = (byte) 0; // set last byte to 0 to indicate end of request

        return request;
    }
}

