import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.*;

public class Sender1b {

    public static void main(String[] args) throws Exception {

        InetAddress RemoteHost = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);

        //Make socket to send things as well as address.
        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress IPAddress = RemoteHost;

        //Read file and get bytes to send in packets of size 1027 bytes.
        File file = new File(args[2]);
        FileInputStream inputStream = new FileInputStream(file);
        byte[] bytes = new byte[1027];

        //Header and ACK checker, when end=1, that means it is the end-of-file.
        byte packetNo = 1;
        byte packetNo2 = 0;
        byte end = 0;
        int pkt = -1;

        //Retransmissions.
        int retransmissions = 0;
        int retransmissionTime = Integer.parseInt(args[3]);

        //File size and timer starter.
        double size = (double) file.length() / 1024;
        long sendTime = System.currentTimeMillis();

        //Loop that sends packets in batches.
        for(int i = 0; i < (int) file.length(); i +=1024) {
            int available = inputStream.available();

            //For the last packet, if there is less than 1024 bytes, makes a smaller byte[] to send.
            if(available < 1024){
                byte[] last = new byte[available + 3];
                inputStream.read(last, 3, available);
                //Change end to 1 to signify end of file.
                end = 1;
                last[0] = end;
                last[1] = packetNo;
                last[2] = packetNo2;
                DatagramPacket packet = new DatagramPacket(last, last.length, IPAddress, port);
                clientSocket.send(packet);

                //Receive ACK back.
                boolean ACKcorrect = false;
                boolean ACKgot;
                int eofCheck = 0;

                while (!ACKcorrect) {
                    byte[] ACKreceive = new byte[2];
                    DatagramPacket receivePacket = new DatagramPacket(ACKreceive, ACKreceive.length);
                    try {
                        clientSocket.setSoTimeout(retransmissionTime);
                        clientSocket.receive(receivePacket);
                        ACKgot = true;
                    }
                    catch (SocketTimeoutException e) {
                        //System.out.println("Timeout");
                        ACKgot = false;
                    }
                    //Check the ACKs.
                    ACKreceive = receivePacket.getData();
                    if (ACKreceive[0] == packetNo && ACKreceive[1] == packetNo2 && ACKgot) {
                        if (ACKreceive[0] > 0 && ACKreceive[0] <= 127) {
                            pkt = (ACKreceive[1] * 256) + ACKreceive[0];
                        }
                        if (ACKreceive[0] < 0 && ACKreceive[0] < 127) {
                            pkt = (ACKreceive[1] * 256) + (256 + ACKreceive[0]);
                        }
                        if (ACKreceive[0] == 0) {
                            pkt = (ACKreceive[1] * 256);
                        }
                        //System.out.println("They received packet#: " + pkt + " " + Arrays.toString(ACKreceive));
                        ACKcorrect = true;
                    }
                    else{
                        //System.out.println("Wrong ACK or no ACK");
                        ACKcorrect = false;
                        clientSocket.send(packet);
                        retransmissions++;
                        //Tries 10 times for last packet.
                        eofCheck++;
                    }
                    if(eofCheck == 10){
                        ACKcorrect = true;
                    }
                }
                long overTime = System.currentTimeMillis();
                long total = overTime - sendTime;
                double throughput = (size*1000)/total;
                System.out.println(retransmissions + " " + (int) throughput);
            }

            //Same thing for the normal packets.
            else{
                inputStream.read(bytes, 3, 1024);
                bytes[0] = end;
                bytes[1] = packetNo;
                bytes[2] = packetNo2;
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, IPAddress, port);
                clientSocket.send(packet);

                boolean ACKcorrect = false;
                boolean ACKgot;

                while (!ACKcorrect) {
                    byte[] ACKreceive = new byte[2];
                    DatagramPacket receivePacket = new DatagramPacket(ACKreceive, ACKreceive.length);
                    try {
                        clientSocket.setSoTimeout(retransmissionTime);
                        clientSocket.receive(receivePacket);
                        ACKgot = true;
                    }
                    catch (SocketTimeoutException e) {
                        //System.out.println("Timeout");
                        ACKgot = false;
                    }
                    //Check the ACKs.
                    ACKreceive = receivePacket.getData();
                    if (ACKreceive[0] == packetNo && ACKreceive[1] == packetNo2 && ACKgot) {
                        if (ACKreceive[0] > 0 && ACKreceive[0] <= 127) {
                            pkt = (ACKreceive[1] * 256) + ACKreceive[0];
                        }
                        if (ACKreceive[0] < 0 && ACKreceive[0] < 127) {
                            pkt = (ACKreceive[1] * 256) + (256 + ACKreceive[0]);
                        }
                        if (ACKreceive[0] == 0) {
                            pkt = (ACKreceive[1] * 256);
                        }
                        //System.out.println("They received packet#: " + pkt + " " + Arrays.toString(ACKreceive));
                        ACKcorrect = true;
                    }
                    else{
                        //System.out.println("Wrong ACK or no ACK");
                        ACKcorrect = false;
                        clientSocket.send(packet);
                        retransmissions++;
                    }
                }

                //Reinitialise byte[] and increment packet #.
                bytes = new byte[1027];
                packetNo++;
                if(packetNo == 0){
                    packetNo2++;
                }
            }
        }
    }
}

