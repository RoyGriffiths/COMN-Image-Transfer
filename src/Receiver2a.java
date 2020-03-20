import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Arrays;
import java.io.FileOutputStream;

class Receiver2a{

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Method calculating the pkt#:
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static int getPktNo(byte[] head){
        int packetNo = -1;

        if (head[0] > 0) {
            packetNo = (head[1] * 256) + head[0];
        }
        if (head[0] < 0) {
            packetNo = (head[1] * 256) + (256 + head[0]);
        }
        if (head[0] == 0) {
            packetNo = (head[1] * 256);
        }
        return packetNo;
    }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //Mainb Method:
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) throws Exception {

        //Debug variables:
        //int port = Integer.parseInt("1235");
        //String Filename = "utput.jpg";
        //int ClientPort = 1236;

        //Setup the port and output file:
        int port = Integer.parseInt(args[0]);
        String Filename = args[1];
        File file = new File(Filename);

        //Make a socket to receive packets. Same with byte[].
        DatagramSocket welcomeSocket = new DatagramSocket(port);
        byte[] receiveData = new byte[1027];
        byte[] sendData = new byte[2];
        List receivedPkts =  new ArrayList();
        int packetNo = -1;
        int previousPktNo = 0;
        byte[] prevPkt = new byte[2];

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
            if(header[0] > 0){
                packetNo = (header[1] * 256) + header[0];
            }
            if(header[0] < 0){
                packetNo = (header[1] * 256) + (256 + header[0]);
            }
            if(header[0] == 0){
                packetNo = (header[1] * 256);
            };

            //Retrieve port and IPAddress to make packet to send back.
            InetAddress ClientIPAddress = receivePacket.getAddress();
            int ClientPort = port + 1;

            //Check if we have received this packet already.
            if(!receivedPkts.contains(packetNo)) {

                //Only if the packet has come in the right order, accept and update previous pkt No.
                if(previousPktNo == packetNo -1){

                    previousPktNo = packetNo;
                    prevPkt[0] = header[0];
                    prevPkt[1] = header[1];

                    sendData[0] = header[0];
                    sendData[1] = header[1];
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ClientIPAddress, ClientPort);

                    //Add the packet to received files.
                    receivedPkts.add(packetNo);

                    //Removes remaining 0s if the last packet.
                    if (receiveData[2] == 1) {
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
                        //if(list.get(list.size()-1) == -111){
                        //  for(int j = 1024; j > 97; j--){
                        //    list.remove(j);
                        //  }
                        //}
                        byte[] lasts = new byte[list.size()];
                        for (int i = 0; i < list.size(); i++) {
                            lasts[i] = list.get(i);
                        }
                        //System.out.println("Received: " + packetNo);
                        //System.out.println(Arrays.toString(receiveData));
                        byte[] lasts2 = Arrays.copyOfRange(lasts, 3, lasts.length);
                        fis.write(lasts2);
                        welcomeSocket.send(sendPacket);
                    }

                    //Write as usual if not last packet.
                    if (receiveData[2] != 1) {
                        //System.out.println("Received: " + packetNo);
                        byte[] imgData = Arrays.copyOfRange(receiveData, 3, receiveData.length);
                        fis.write(imgData);
                        welcomeSocket.send(sendPacket);
                        //System.out.println("Sent ack for " + getPktNo(receiveData));
                    }

                    //Reset the data.
                    receiveData = new byte[1027];

                    //Terminate program when last packet received.
                    if (header[2] == 1) {
                        System.out.println("Last packet received");
                        System.exit(0);
                    }
                }
            }

            //If we receive an out-of-order packet, reACK pkt with highest in-order seq No.
            else{
                DatagramPacket sendPacket = new DatagramPacket(prevPkt, prevPkt.length, ClientIPAddress, ClientPort);
                welcomeSocket.send(sendPacket);
            }
        }
    }
}
