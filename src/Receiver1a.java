import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Arrays;
import java.io.FileOutputStream;

class Receiver1a{
    public static void main(String[] args) throws Exception {

        int port = Integer.parseInt(args[0]);
        String Filename = args[1];

        //Make a socket to receive packets. Same with byte[].
        DatagramSocket welcomeSocket = new DatagramSocket(port);
        byte[] receiveData = new byte[1027];

        //File input stream to get image. Not really sure whats happening.
        FileOutputStream fis = new FileOutputStream(Filename);

        while (true) {
            //Receive the packets and get the byte[] from them into the byte[] made before.
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            welcomeSocket.receive(receivePacket);
            receiveData = receivePacket.getData();

            //Removes remaining 0s if the last packet.
            if(receiveData[0] == 1){
                List<Byte> list = new ArrayList<Byte>();
                for(int i = 0; i < receiveData.length; i++){
                    list.add(receiveData[i]);
                }
                for(int i = receiveData.length-1; i >= 3; i--){
                    if(receiveData[i] == 0){
                        list.remove(i);
                    }
                    if(receiveData[i] != 0){
                        break;
                    }
                }
                byte[] lasts = new byte[list.size()];
                for(int i = 0; i<list.size(); i++){
                    lasts[i] = list.get(i);
                }
                byte[] lasts2 = Arrays.copyOfRange(lasts, 3, lasts.length);
                fis.write(lasts2);
            }

            //Remove the header.
            if(receiveData[0] != 1) {
                byte[] imgData = Arrays.copyOfRange(receiveData, 3, receiveData.length);
                fis.write(imgData);
            }

            //Reset data.
            byte[] header = Arrays.copyOfRange(receiveData, 0, 3);
            receiveData = new byte[1027];

            //Terminate program when last packet received.
            if(header[0]==1){
                System.exit(0);
            }
        }
    }
}
