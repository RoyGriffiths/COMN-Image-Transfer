import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FurtherExperimentalSender {

    //Counting the pkts which have been ACKed, to be used in thread below:
    private static int accumulatedACK;

    //Bool to say whether to continue running the thread, int for pkt# and number of last ACK:
    private static boolean running = true;

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Thread for receiving ACKs:
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //Make a thread just for receiving ACKs:
    public static class getACKs extends Thread {

        //Initialise the socket to receive ACKs:
        DatagramSocket receiveSocket;

        //Make a constructor and give the port# to the socket:
        getACKs(int port) throws SocketException {
            receiveSocket = new DatagramSocket(port);
        }

        //Waits for ACKs to come, then accepts them if they are expected or smaller than what sent:
        public void run(){

            //Pkt#:
            int pkt = -1;

            //Main loop:
            while(running){

                //Make space for receiving ACK, also make a packet for it:
                byte[] ACKreceive = new byte[2];
                DatagramPacket receivePacket = new DatagramPacket(ACKreceive, ACKreceive.length);

                //Do the receiving of ACKs:
                try {
                    receiveSocket.setSoTimeout(5);
                    receiveSocket.receive(receivePacket);

                    //Get the ACK and check for pkt#:
                    ACKreceive = receivePacket.getData();
                    if (ACKreceive[0] > 0 && ACKreceive[0] <= 127) {
                        pkt = (ACKreceive[1] * 256) + ACKreceive[0];
                    }
                    if (ACKreceive[0] < 0 && ACKreceive[0] < 127) {
                        pkt = (ACKreceive[1] * 256) + (256 + ACKreceive[0]);
                    }
                    if (ACKreceive[0] == 0) {
                        pkt = (ACKreceive[1] * 256);
                    }
                    //System.out.println("Received ACK: " + pkt);

                    //Update the accumulatedACK if the received pkt is new:
                    if(accumulatedACK < pkt){
                        accumulatedACK = pkt;
                    }
                }
                catch (IOException ignored) {}
                finally {Thread.yield();}
            }
        }
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Method for converting the image into a List of byte[]
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //Method to turn the picture into a List of byte[]:
    public static List convertBytes(File file, double size) throws IOException {

        //Define our List to put byte[] in:
        List windowPkts =  new ArrayList();

        //Get the size of the file, then calculate the required# of pkts for it:
        int RequiredPktNums = (int) size + 1;
        FileInputStream inputStream = new FileInputStream(file);

        //Initialise our pkt# and byte[] to put img data in:
        byte[] bytes = new byte[1027];
        byte packetNo = 1;
        byte packetNo2 = 0;

        //For loop to put data into byte[], for the last pkt we can make a smaller byte[]:
        for(int i = 0; i < RequiredPktNums; i++){
            int available = inputStream.available();
            if(available < 1024){
                bytes = new byte[available + 3];
                inputStream.read(bytes, 3, available);
                bytes[2] = 1; // EOF tag.
            }
            else {
                inputStream.read(bytes, 3, 1024);
                bytes[2] = 0;
            }
            bytes[0] = packetNo;
            bytes[1] = packetNo2;

            //Add the byte[] to the List:
            windowPkts.add(bytes);

            //Reinitialise the byte[] and increment pkt#:
            if(i != RequiredPktNums) {
                bytes = new byte[1027];
                packetNo++;
                if (packetNo == 0) {
                    packetNo2++;
                }
            }
        }
        return(windowPkts);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Class for windows:
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static class Window{
        public List windows;
        public int counts;

        public Window(List windows, int counts){
            this.windows = windows;
            this.counts = counts;
        }

        public int getCounts(){
            return counts;
        }

        public List getWindow(){
            return windows;
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
    // Method for filling up the window:
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static Window fillUp(List window, int count, int windowSize, List allBytes){

        int sizeNow = window.size();
        int target = windowSize;
        int diff = allBytes.size() - count;

        if(diff < target - sizeNow){
            for(int i = sizeNow; i < diff + 1; i++){
                if(count == allBytes.size()){
                    break;
                }
                window.add(allBytes.get(count));
                count+=1;
            }
        }

        else{
            for(int i = sizeNow; i < target; i++){
                if(count > allBytes.size()){
                    break;
                }
                window.add(allBytes.get(count));
                count+=1;
            }
        }
        return new Window(window, count);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The main method
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) throws Exception {

        getACKs object = new getACKs(1235);
        object.start();

        //InetAddress RemoteHost = InetAddress.getByName(args[0]);
        InetAddress RemoteHost = InetAddress.getByName("localhost");
        int port = Integer.parseInt("1234");

        //Make socket to send things as well as address:
        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress IPAddress = RemoteHost;

        //Read file and get bytes to send in packets of size 1027 bytes.
        File file = new File("src/test.jpg");
        double size = (double) file.length() / 1024;

        //Retransmission time, generally 4x of the delay from the pipe setup:
        int retransmissionTime = Integer.parseInt("20");

        //Convert the image into a List of byte[], window size and window to be filled with packets:
        List allBytes = convertBytes(file, size);
        int windowSize = Integer.parseInt("5");
        List windowPkts = new ArrayList();

        //Set a count to index the two windows:
        int count = 0;

        //Array to track the flight time for each packet:
        List<Long> times = new ArrayList<Long>();

        //Make boolean for out of time pkts:
        boolean timeout = false;

        //File size, expected number of packets, and timer starter:
        long sendTime = System.currentTimeMillis();

        //Main loop that refills the window and sends pkts till end:
        while(running){

            //Fill window with made methods and class:
            Window filled = fillUp(windowPkts, count, windowSize, allBytes);
            windowPkts = filled.getWindow();
            count = filled.getCounts();

            //Removes the pkts from window that have been ACKed:
            if(windowPkts.size() > 0) {
                byte[] fst = (byte[]) windowPkts.toArray()[0];
                byte[] fstHead = new byte[2];
                fstHead[0] = fst[0];
                fstHead[1] = fst[1];
                int fstNo = getPktNo(fstHead);

                while(windowPkts.size() > 0 && fstNo <= accumulatedACK) {
                    windowPkts.remove(0);
                    if(windowPkts.size() > 0) {
                        fst = (byte[]) windowPkts.toArray()[0];
                        fstHead = new byte[2];
                        fstHead[0] = fst[0];
                        fstHead[1] = fst[1];
                        fstNo = getPktNo(fstHead);
                    }
                    times.remove(0);
                }
            }

            //In the case where times out: resend the entire window:
            if(times.size() > 0){
                long time = times.get(0);
                if(time - System.currentTimeMillis() >= retransmissionTime){
                    timeout = true;
                    for(int i = 0; i < windowPkts.size(); i++){
                        byte[] bytes =  (byte[]) windowPkts.toArray()[i];
                        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, IPAddress, port);
                        clientSocket.send(packet);
                        times.add(System.currentTimeMillis());
                        if(bytes[2] == 1){
                            for(int j = 0; j < 50; j++){
                                clientSocket.send(packet);
                            }
                            System.out.println("Reached End!");
                            running = false;
                            break;
                        }
                    }
                }
                else{
                    timeout = false;
                }
            } else{
                timeout = false;
            }

            //Send normally in the cases of it sending and ACKing in time:
            if (timeout == false) {
                //For each pkt in the window, send it:
                for(int i = 0; i < windowPkts.size(); i++){
                    //Get the byte[] from the window and send it:
                    byte[] bytes =  (byte[]) windowPkts.toArray()[i];
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length, IPAddress, port);
                    clientSocket.send(packet);
                    times.add(System.currentTimeMillis());
                    System.out.println("Successfully sent pkt: " + getPktNo(bytes));

                    //If last packet, send 50 times, then break:
                    if(bytes[2] == 1){
                        for(int j = 0; j < 50; j++){
                            clientSocket.send(packet);
                        }
                        System.out.println("Reached End!");
                        running = false;
                        break;
                    }
                }
            }
            //TimeUnit.MILLISECONDS.sleep(2);
        }

        //Calculate all the end statistics:
        long overTime = System.currentTimeMillis();
        long total = overTime - sendTime;
        double throughput = (size * 1000) / total;
        System.out.println((int) throughput);
    }
}