import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Sender1a {

    public static void main(String[] args) throws Exception {

        InetAddress RemoteHost = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);
        String Filename = args[2];

        //Make socket to send things as well as address.
        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress IPAddress = RemoteHost;

        //Read file and get bytes to send in packets of size 1027 bytes.
        File file = new File(Filename);
        FileInputStream inputStream = new FileInputStream(file);
        byte[] bytes = new byte[1027];

        //Header, when end=1, that means it is the end-of-file.
        byte packetNo = 1;
        byte packetNo2 = 0;
        byte end = 0;

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
                TimeUnit.MILLISECONDS.sleep(20);
            }

            //Same thing for the normal packets.
            else{
                inputStream.read(bytes, 3, 1024);
                bytes[0] = end;
                bytes[1] = packetNo;
                bytes[2] = packetNo2;
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length, IPAddress, port);
                clientSocket.send(packet);
                TimeUnit.MILLISECONDS.sleep(20);
                //Reinitialise byte[].
                bytes = new byte[1027];
                packetNo++;
                if(packetNo == 0){
                    packetNo2++;
                }
            }
        }
    }
}

