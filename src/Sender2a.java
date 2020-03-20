import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Sender2a {

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
                    if(accumulatedACK <= pkt){
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

        //Debug params:
        /*
        InetAddress RemoteHost = InetAddress.getByName("localhost");
        int port = Integer.parseInt("1235");
        File file = new File("test.jpg");
        int retransmissionTime = Integer.parseInt("20");
        int windowSize = Integer.parseInt("1");
        */


        //Get all the inputs:
        InetAddress RemoteHost = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);
        File file = new File(args[2]);
        int retransmissionTime = Integer.parseInt(args[3]);       //Generally 4x of the delay from the pipe setup:
        int windowSize = Integer.parseInt(args[4]);

        //Start the receiving ACK thread:
        getACKs object = new getACKs(port + 1);
        object.start();

        //Make socket to send things as well as address:
        DatagramSocket clientSocket = new DatagramSocket();
        InetAddress IPAddress = RemoteHost;

        //Get the total size of the file:
        double size = (double) file.length() / 1024;

        //Convert the image into a List of byte[], window size and window to be filled with packets:
        List allBytes = convertBytes(file, size);
        List windowPkts = new ArrayList();

        //Set a count to index the two windows:
        int count = 0;

        //Counts to track how many times the last pkt got sent:
        int lastTries = 0;

        //Array to track the flight time for each packet:
        List<Long> times = new ArrayList<Long>();
        long lastTime = -1;

        //Make boolean for out of time pkts:
        boolean timeout;
        boolean none = true;
        boolean lastSent = false;

        //File size, expected number of packets, and timer starter:
        long sendTime = System.currentTimeMillis();

        //Main loop that refills the window and sends pkts till end:
        while(running){

            //Some checks to stop the program:
            if(accumulatedACK == 879 || (accumulatedACK == 879 && lastTries > 50)){
                running = false;
            }
            if(lastSent == true && System.currentTimeMillis() - lastTime > 500){
                //System.out.println("Exited");
                long overTime = System.currentTimeMillis() - 500;
                long total = overTime - sendTime;
                double throughput = (size * 1000) / total;
                System.out.println((float) throughput);
                System.exit(0);
            }

            //Fill window with made methods and class:
            Window filled = fillUp(windowPkts, count, windowSize, allBytes);
            windowPkts = filled.getWindow();
            count = filled.getCounts();


            //Removes the pkts from window that have been ACKed as well as times:
            if(windowPkts.size() > 0) {
                byte[] fst = (byte[]) windowPkts.toArray()[0];
                byte[] fstHead = new byte[2];
                fstHead[0] = fst[0];
                fstHead[1] = fst[1];
                int fstNo = getPktNo(fstHead);

                while(windowPkts.size() > 0 && fstNo <= accumulatedACK) {
                    windowPkts.remove(0);
                    times.remove(0);
                    if(windowPkts.size() > 0) {
                        fst = (byte[]) windowPkts.toArray()[0];
                        fstHead = new byte[2];
                        fstHead[0] = fst[0];
                        fstHead[1] = fst[1];
                        fstNo = getPktNo(fstHead);
                    }
                }
            }

            //In the case where times out: resend the entire window:
            if(times.size() > 0){
                long time = times.get(0);
                if(System.currentTimeMillis() - time >= retransmissionTime){
                    timeout = true;
                    times = new ArrayList();
                    for(int i = 0; i < windowPkts.size(); i++){
                        byte[] bytes =  (byte[]) windowPkts.toArray()[i];
                        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, IPAddress, port);
                        clientSocket.send(packet);
                        //System.out.println("sentpkt" + getPktNo(bytes));
                        times.add(System.currentTimeMillis());
                        if(bytes[2] == 1){
                            for(int j = 0; j < 50; j++){
                                clientSocket.send(packet);
                                lastTries+=1;
                            }
                            lastSent = true;
                            lastTime = System.currentTimeMillis();
                        }
                    }
                    //System.out.println("Window, TO");
                }
                else{
                    timeout = false;
                    none = false;
                }
            } else{
                timeout = false;
                none = true;
            }

            //Send normally in the cases of it sending and ACKing in time:
            if (timeout == false && none == true) {
                times = new ArrayList();
                //For each pkt in the window, send it:
                for(int i = 0; i < windowPkts.size(); i++){
                    //Get the byte[] from the window and send it:
                    byte[] bytes =  (byte[]) windowPkts.toArray()[i];
                    DatagramPacket packet = new DatagramPacket(bytes, bytes.length, IPAddress, port);
                    clientSocket.send(packet);
                    times.add(System.currentTimeMillis());
                    //If last packet, send 50 times, then break:
                    if(bytes[2] == 1){
                        for(int j = 0; j < 50; j++){
                            clientSocket.send(packet);
                            lastTries+=1;
                            lastSent = true;
                        }
                        lastSent = true;
                        lastTime = System.currentTimeMillis();
                    }
                }
                //System.out.println("Window");
            }
        }
        //Calculate all the end statistics:
        long overTime = System.currentTimeMillis();
        long total = overTime - sendTime;
        double throughput = (size * 1000) / total;
        System.out.println((float) throughput);
        //object.stop();
    }
}
