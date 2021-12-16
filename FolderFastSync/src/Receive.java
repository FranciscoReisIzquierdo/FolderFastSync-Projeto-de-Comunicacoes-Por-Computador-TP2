import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class Receive implements Runnable {
    private static final int MAX_BYTES= 1024;
    private DatagramSocket mySocket;
    private List<String> myListOfFiles;
    private List<String> otherList;
    private int destPort;
    private String folder;
    private volatile Map<Integer, Integer> mapReceiver= new ConcurrentHashMap<>();
    private volatile Map<Integer, Integer> mapSender= new ConcurrentHashMap<>();
    private volatile Map<Integer, FileOutputStream> filesReceived= new ConcurrentHashMap<>();
    private Map<String, Long> otherFilesDates= new ConcurrentHashMap<>();
    private Map<String, Integer> otherFilesHashes= new ConcurrentHashMap<>();
    private Map<String, Long> myFilesDates= new ConcurrentHashMap<>();
    private volatile Boolean flag= false;
    private String pass;
    private FileOutputStream logFile;
    private ReentrantLock lock= new ReentrantLock();
    private ReentrantLock httpLock= new ReentrantLock();
    private InetAddress dest;
    public static int ACKNumber= 0;
    public static int FYNNumber= 0;
    public static int PacketNumber= 0;
    public static int LASTNumber= 0;
    public static boolean running= true;
    public static long bytes= 0;


    public Receive(DatagramSocket mySocket, List<String> myListOfFiles, int destPort, String folder, String pass, InetAddress dest) throws FileNotFoundException {
        this.mySocket = mySocket;
        this.myListOfFiles= myListOfFiles;
        this.destPort= destPort;
        this.folder= folder;
        this.otherList= new ArrayList<>();
        this.pass= pass;
        this.logFile= new FileOutputStream(Paths.get(this.folder, "log.txt").toString(), true);
        this.dest= dest;

        for(String file: this.myListOfFiles){
            File f= new File(String.valueOf(Paths.get(this.folder, file)));
            this.myFilesDates.put(file, f.lastModified());
        }
    }

    /**
     * Class to control the number of retransmissions
     */
    public class Retransmissions {
        private int retransmissions;

        public Retransmissions(){ this.retransmissions= 0; }

        public void incrementRetransmitions(){ this.retransmissions++; }

        public int getRetransmissions(){ return this.retransmissions; }
    }


    @Override
    public void run() {
        HTTPServer httpServer= null;
        try {
            httpServer = new HTTPServer(this.myListOfFiles, this.otherList, this.dest, this.httpLock, this.folder); }
        catch (IOException e) { e.printStackTrace(); }

        Thread server= new Thread(httpServer);
        server.start();

        Retransmissions r= new Retransmissions();
        List<String> mandou= new ArrayList<>();

        new Thread(new Sender(this.myListOfFiles, this.mySocket, this.destPort, this.dest, mapSender, this.pass, this.folder)).start();
        int count= 0;
        long startTime = System.currentTimeMillis();
        while (Receive.running) {
            try {
                if(count== 0) this.mySocket.setSoTimeout(10000);
                byte[] receiveBuffer = new byte[MAX_BYTES];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, 0, receiveBuffer.length);
                this.mySocket.receive(receivePacket);
                if(count == 0) this.mySocket.setSoTimeout(1000);

                Thread receiver= new Thread(new ReceiverWorker(this.mySocket, receivePacket, mapReceiver, mapSender, myListOfFiles, filesReceived, this.folder, this.otherList, flag, mandou, this.pass, r, this.logFile, this.lock, this.otherFilesHashes, this.otherFilesDates, this.myFilesDates));
                receiver.start();
                if(count== 0) count++;

            } catch(SocketException e){
                Receive.running= false;
                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;
                try {
                    byte[] logInfo= ("\nTime of execution: " + elapsedTime/1000.0 + " (seconds)\nFinal debt: " + Math.ceil((bytes * 8)/(elapsedTime/1000.0)) + " bps").getBytes(StandardCharsets.UTF_8);
                    this.logFile.write(logInfo);
                    this.logFile.close();
                }
                catch (IOException ex) { ex.printStackTrace(); }
            }
            catch (IOException e) {
                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;
                if(elapsedTime/1000.0 >= 7) {
                    try { this.logFile.write(("No connection established under 10 seconds. Connection closed!").getBytes(StandardCharsets.UTF_8)); }
                    catch (IOException ex) { ex.printStackTrace(); }
                }
                else {
                    try { this.logFile.write(("The number of retransmissions is to high.The passwords don´t match!").getBytes(StandardCharsets.UTF_8)); }
                    catch (IOException ex) { ex.printStackTrace(); }
                }

                this.mySocket.close();
            }
        }
        if(httpServer.getSocket()!= null) {
            try { httpServer.getSocket().close(); }
            catch (IOException ex) { ex.printStackTrace(); }
        }
    }
}
