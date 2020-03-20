import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Arrays;
import java.io.FileOutputStream;

public class Receiver2b {

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Class for buffered packets:
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static class buffered{
      public int pktNo;
      public byte[] buffering;
      boolean eof;

      public buffered(int pktNo, byte[] buffering, boolean eof){
        this.pktNo = pktNo;
        this.buffering = buffering;
        this.eof = eof;
      }

      public int getBuffPktNo(){
        return pktNo;
      }

      public byte[] getBuffData(){
        return buffering;
      }

      public boolean getBuffEof(){
        return eof;
      }
    }

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
    // Main method for receiving ACKs:
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) throws Exception {

        //Debug variables:
        /*
        int port = Integer.parseInt("1235");
        String Filename = "Output.jpg";
        int ClientPort = 1236;
        int windowSize = 5;
        */

        //Setup the port, output file and window size:
        int port = Integer.parseInt(args[0]);
        String Filename = args[1];
        int windowSize = Integer.parseInt(args[2]);
        File file = new File(Filename);

        //Make a socket to receive packets. Same with byte[].
        DatagramSocket welcomeSocket = new DatagramSocket(port);
        byte[] receiveData = new byte[1027];
        byte[] sendData = new byte[2];
        List receivedPkts =  new ArrayList();
        int packetNo = -1;
        int previousPktNo = 0;
        byte[] prevPkt = new byte[2];

        //Boolean to see if in List written:
        boolean got;

        //Let's make a List, a base, and end int for the rcv window:
        List<buffered> rcvWindow = new ArrayList();
        //PriorityQueue<buffered> rcvWindow = new PriorityQueue();
        int base = 1;
        int windowEnd = base + windowSize - 1;

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
            packetNo = getPktNo(header);

            //Retrieve port and IPAddress to make packet to send back.
            InetAddress ClientIPAddress = receivePacket.getAddress();
            int ClientPort = port + 1;

            //Set up the ACK pkt:
            sendData[0] = header[0];
            sendData[1] = header[1];
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ClientIPAddress, ClientPort);

            if(base > packetNo && receivedPkts.contains(packetNo)){
              welcomeSocket.send(sendPacket);
            }

            //Check if we have received this packet already:
            if(!receivedPkts.contains(packetNo)) {

                //Only if the received packet is within the window, buffer:
                if(packetNo >= base && packetNo <= windowEnd){
                  welcomeSocket.send(sendPacket);

                    //Add the packet to received files.
                    receivedPkts.add(packetNo);

                    //If we received the base packet, write it, check the other buffered pkts, then slide the window:
                    if(packetNo == base && receiveData[2] != 1){
                        byte[] imgData = Arrays.copyOfRange(receiveData, 3, receiveData.length);
                        //System.out.println("Wrote pkt: " + packetNo);

                        fis.write(imgData);
                        base++;
                        windowEnd++;

                        //We need to write the remaining pkts in the List if they proceed the base:
                        while(rcvWindow.size() > 0){
                          buffered fstQueue = (buffered)rcvWindow.get(0);
                          int queueNo = fstQueue.getBuffPktNo();

                          if(queueNo == base){
                            //Handle normal files:
                            if(fstQueue.getBuffEof() == false){
                              imgData = Arrays.copyOfRange(fstQueue.getBuffData(), 3, receiveData.length);
                              fis.write(imgData);
                            }

                            //Handle last file:
                            if(fstQueue.getBuffEof() == true){
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
                              if(rcvWindow.size() == 0){
                                  byte[] lasts2 = Arrays.copyOfRange(lasts, 3, lasts.length);
                                  fis.write(lasts2);
                                  System.out.println("Last packet received");
                                  System.exit(0);
                              }
                            }
                            base++;
                            windowEnd++;
                            rcvWindow.remove(0);
                          }
                          else{
                            break;
                          }
                        }
                    }

                    else{
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
                          byte[] lasts = new byte[list.size()];
                          for (int i = 0; i < list.size(); i++) {
                              lasts[i] = list.get(i);
                          }
                          if(rcvWindow.size() == 0){
                              byte[] lasts2 = Arrays.copyOfRange(lasts, 3, lasts.length);
                              fis.write(lasts2);
                              System.out.println("Last packet received");
                              System.exit(0);
                          }
                          else{
                            buffered buffer = new buffered(getPktNo(receiveData), receiveData, true);
                            rcvWindow.add(buffer);
                          }
                        }
                        //If it's not the EOF, just add to buffer:
                        else{
                          buffered buffer = new buffered(getPktNo(receiveData), receiveData, false);
                            rcvWindow.add(buffer);
                        }
                    }
                }
                //Reset the data.
                receiveData = new byte[1027];
            }
        }
    }
}
