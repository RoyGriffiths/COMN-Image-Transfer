import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Arrays;
import java.io.FileOutputStream;

class Receiver1b{
    public static void main(String[] args) throws Exception {

        int port = Integer.parseInt(args[0]);
        String Filename = args[1];
        File file = new File(Filename);

        //Make a socket to receive packets. Same with byte[].
        DatagramSocket welcomeSocket = new DatagramSocket(port);
        byte[] receiveData = new byte[1027];
        byte[] sendData = new byte[2];
        List receivedPkts =  new ArrayList();
        int packetNo = -1;

        //File input stream to get image. Not really sure whats happening.
        FileOutputStream fis = new FileOutputStream(file);
        System.out.println("Ready to receive");

        while (true) {
            //Receive the packets and get the byte[] from them into the byte[] made before.
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            welcomeSocket.receive(receivePacket);
            receiveData = receivePacket.getData();

            //Process the header to say what is received.
            byte[] header = Arrays.copyOfRange(receiveData, 0, 3);
            if(header[1] > 0){
                packetNo = (header[2] * 256) + header[1];
            }
            if(header[1] < 0){
                packetNo = (header[2] * 256) + (256 + header[1]);
            }
            if(header[1] == 0){
                packetNo = (header[2] * 256);
            };

            //Retrieve port and IPAddress to make packet to send back.
            InetAddress ClientIPAddress = receivePacket.getAddress();
            int ClientPort = receivePacket.getPort();
            sendData[0] = header[1];
            sendData[1] = header[2];
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ClientIPAddress, ClientPort);

            //Check if we have received this packet already.
            if(!receivedPkts.contains(packetNo)) {

                //Add the packet to received files.
                receivedPkts.add(packetNo);

                //Removes remaining 0s if the last packet.
                if (receiveData[0] == 1) {
                    List<Byte> list = new ArrayList<Byte>();
                    for (int i = 0; i < receiveData.length; i++) {
                        list.add(receiveData[i]);
                    }
                    for (int i = receiveData.length - 1; i >= 3; i--) {
                        if (receiveData[i] == 0) {
                            list.remove(i);
                        }
                        if (receiveData[i] != 0) {
                            break;
                        }
                    }
                    byte[] lasts = new byte[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        lasts[i] = list.get(i);
                    }
                    byte[] lasts2 = Arrays.copyOfRange(lasts, 3, lasts.length);
                    fis.write(lasts2);
                    welcomeSocket.send(sendPacket);
                }

                //Write as usual if not last packet.
                if (receiveData[0] != 1) {
                    byte[] imgData = Arrays.copyOfRange(receiveData, 3, receiveData.length);
                    fis.write(imgData);
                    welcomeSocket.send(sendPacket);
                }

                //Reset the data.
                receiveData = new byte[1027];

                //Terminate program when last packet received.
                if (header[0] == 1) {
                    System.out.println("Last packet received");
                    System.exit(0);
                }
            }
            else{
                welcomeSocket.send(sendPacket);
            }
        }
    }
}
