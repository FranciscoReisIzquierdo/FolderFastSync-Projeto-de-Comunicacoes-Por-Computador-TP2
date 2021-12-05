import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReceiverWorker implements Runnable{
    private int destPort;
    private DatagramPacket receivedPacket;
    private DatagramSocket mySocket;
    private Map<Integer, Integer> mapReceiver;
    private Map<Integer, Integer> mapSender;
    private List<String> myListOfFiles;
    private Map<Integer, FileOutputStream> filesReceived;
    private String folder;
    private List<String> otherList;
    private Boolean flag;
    private List<String> mandou;
    private String pass;
    private Receive.Retransmissions retransmissions;
    private FileOutputStream logFile;
    private ReentrantLock lock;

    public ReceiverWorker(DatagramSocket mySocket, DatagramPacket packet, Map<Integer, Integer> mapReceiver, Map<Integer,
            Integer> mapSender, List<String> myListOfFiles, Map<Integer, FileOutputStream> filesReceived, String folder,
                          List<String> otherList, Boolean flag, List<String> mandou, String pass,
                          Receive.Retransmissions r, FileOutputStream logFile, ReentrantLock lock){

        this.receivedPacket= packet;
        this.destPort= packet.getPort();
        this.mySocket= mySocket;
        this.mapReceiver= mapReceiver;
        this.mapSender= mapSender;
        this.myListOfFiles= myListOfFiles;
        this.filesReceived= filesReceived;
        this.folder= folder;
        this.otherList= otherList;
        this.flag= flag;
        this.mandou= mandou;
        this.pass= pass;
        this.retransmissions= r;
        this.logFile= logFile;
        this.lock= lock;
    }


    /**
     * Method which gives the hash number of the file name
     * @param packet
     * @return
     */
    public static int getFileHashNumber(byte[] packet){ return ByteBuffer.wrap(Arrays.copyOfRange(packet, 8, 12)).getInt(); }


    /**
     * Method which gives the hashNumber of the packet
     * @param packet
     * @return
     */
    public static int getHashNumber(byte[] packet){ return ByteBuffer.wrap(Arrays.copyOfRange(packet, 4, 8)).getInt(); }


    /**
     * Method which gives the sequence number of a packet
     * @param packet
     * @return
     */
    public static int getSequenceNumber(byte[] packet){ return ByteBuffer.wrap(Arrays.copyOfRange(packet, 0, 4)).getInt(); }


    /**
     * Method which gives the hashNumber of the list of files
     * @param packet
     * @return
     */
    public int getHashFileListNumber(byte[] packet){ return ByteBuffer.wrap(Arrays.copyOfRange(packet, 12, 16)).getInt(); }


    /**
     * Method which gives the info in the packet
     * @param packet
     * @return
     */
    public byte[] getInfo(byte[] packet){ return Arrays.copyOfRange(packet, 12, this.receivedPacket.getLength()); }


    /**
     * Method "hash function"
     * @param str
     * @return
     */
    public static int hash(byte[] str) {
        int hash = 0;
        for (int i = 0; i < str.length && str[i]!= '\0'; i++) {
            hash = str[i] + ((hash << 5) - hash);
        }
        return hash;
    }

    /**
     * Method which decrypt the received packet
     * @param realInfo
     * @param offset
     * @param mode
     * @return
     */
    public byte[] getDecryptedInfo(byte[] realInfo, int offset, int mode){
        int deficit;
        if(mode== 0) deficit= hash(this.pass.getBytes(StandardCharsets.UTF_8)) + getHashFileListNumber(realInfo);
        else deficit= hash(this.pass.getBytes(StandardCharsets.UTF_8)) + getHashNumber(realInfo);

        byte[] files= Arrays.copyOfRange(realInfo, offset, this.receivedPacket.getLength());

        for(int i= 0; i< files.length; i++) files[i]-= deficit;

        return files;
    }


    /**
     * Method which evaluates if there are conditions to terminate the program
     * @return
     */
    public boolean checkStop(){
        for(Map.Entry<Integer, Integer> entry: this.mapSender.entrySet()){
            if(entry.getValue()!= -1) return false;
        }
        for(Map.Entry<Integer, Integer> entry: this.mapReceiver.entrySet()){
            if(entry.getValue()!= -1) return false;
        }
        return true;
    }

    /**
     * Method that creates, foreach incoming file, a FileOutputStream
     * @throws FileNotFoundException
     */
    public void receiveFiles() throws IOException {
        this.logFile.write(("List of files received: [").getBytes(StandardCharsets.UTF_8));

        for(String l: this.otherList) {
            if(!this.myListOfFiles.contains(l) && !l.equals("")){
                this.mapReceiver.put(hash(l.getBytes()), 1);
                ByteArrayOutputStream output= new ByteArrayOutputStream();
                output.write(l.getBytes(StandardCharsets.UTF_8));
                output.write(("-> ").getBytes(StandardCharsets.UTF_8));
                output.write(String.valueOf(hash(l.getBytes())).getBytes());
                output.write((" (hash of the file); ").getBytes(StandardCharsets.UTF_8));
                byte[] out= output.toByteArray();
                this.logFile.write(out);
                this.filesReceived.put(hash(l.getBytes()), new FileOutputStream(this.folder+ "\\\\" + l, true));
            }
        }
        this.logFile.write(("]\n").getBytes(StandardCharsets.UTF_8));
    }


    /**
     * Method that creates a thread per file to send
     */
    public void sendFiles(){
        this.flag= false;
        for (String l : this.myListOfFiles) {
            if (!this.otherList.contains(l) && !l.equals(""))
                new Thread(new Sender(hash(l.getBytes()), this.mapSender, this.mySocket, this.receivedPacket.getPort(), this.receivedPacket.getAddress(), l, this.folder, this.pass)).start();
        }
    }


    /**
     * Method that gets the list of files of the other client
     * @param info
     */
    public void getList(byte[] info){
        String[] lista= new String(info, StandardCharsets.UTF_8).split("§");
        this.otherList.addAll(Arrays.asList(lista));
    }


    /**
     * Method that prepares and sends FYN packets
     * @param fileName
     * @return
     * @throws IOException
     */
    public DatagramPacket sendPacketFYN(int fileName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(ByteBuffer.allocate(4).putInt(-1000).array());
        output.write(ByteBuffer.allocate(4).putInt(0).array());
        output.write(ByteBuffer.allocate(4).putInt(fileName).array());
        byte[] out = output.toByteArray();
        return new DatagramPacket(out, out.length, this.receivedPacket.getAddress(), this.destPort);
    }


    /**
     * Method that prepares and sends ACK packets
     * @param packetNumber
     * @param filename
     * @return
     * @throws IOException
     */
    public DatagramPacket sendPacketACK(int packetNumber, int filename) throws IOException {
        ByteArrayOutputStream output= new ByteArrayOutputStream();
        output.write(ByteBuffer.allocate(4).putInt(packetNumber).array());
        output.write(ByteBuffer.allocate(4).putInt(-1).array());
        output.write(ByteBuffer.allocate(4).putInt(filename).array());
        byte[] out= output.toByteArray();

        return new DatagramPacket(out, out.length, this.receivedPacket.getAddress(), this.destPort);
    }


    /**
     * Method that handles FileList packets
     * @param realInfo
     * @param hashNumberFile
     */
    public void fileListPacketHandler(byte[] realInfo, int hashNumberFile) throws IOException {
        try {
            this.lock.lock();
            if(this.retransmissions.getRetransmissions()== 1) this.logFile.write(("List of files received!\n").getBytes(StandardCharsets.UTF_8));
            byte[] list = getDecryptedInfo(realInfo, 16, 0);
            if (hash(list) != getHashFileListNumber(realInfo)) {
                this.retransmissions.incrementRetransmitions();
                if (this.retransmissions.getRetransmissions() == 10) {
                    this.logFile.write(("The number of retransmissions is to high.The passwords don´t match!").getBytes(StandardCharsets.UTF_8));
                    this.mySocket.close();
                }
                return;
            }
            getList(list);

            try { receiveFiles(); }
            catch (IOException e) { e.printStackTrace(); }

            this.flag = true;
            if (this.mapSender.get(-1) == -1) sendFiles();
            this.mapReceiver.put(hashNumberFile, -1);

            try { this.mySocket.send(sendPacketFYN(-1)); }
            catch (IOException e) { e.printStackTrace(); }

            //Verificar condições de saída
            if (checkStop()) {
                this.mySocket.close();
            }
            return;
        }
        finally { this.lock.unlock(); }
    }


    /**
     * Method that gets the file name given his hashNumber
     * @param hashNumberFile
     * @return
     */
    public String getFileName(int hashNumberFile){
        for(String l: this.otherList)
            if(hash(l.getBytes(StandardCharsets.UTF_8))== hashNumberFile) return l;
        for(String l: this.myListOfFiles)
            if(hash(l.getBytes(StandardCharsets.UTF_8))== hashNumberFile) return l;
        return "File List";
    }

    /**
     * Method that handles ACK packets
     * @param hashNumberFile
     */
    public void ackPacketHandler(int hashNumberFile, int packetNumber) throws IOException {
        this.lock.lock();
        String fileName= getFileName(hashNumberFile);

        this.logFile.write(("ACK packet number " + packetNumber + " received from file: " + fileName + "\n").getBytes(StandardCharsets.UTF_8));
        int actualNumberPacket = this.mapSender.get(hashNumberFile);
        this.mapSender.put(hashNumberFile, actualNumberPacket + 1);
        this.lock.unlock();
        return;
    }


    /**
     * Method that handles FYN packets
     * @param hashNumberFile
     */
    public void fynPacketHandler(int hashNumberFile) throws IOException {
        this.lock.lock();
        String fileName= getFileName(hashNumberFile);

        this.logFile.write(("FYN packet received from file: " + fileName + "\n").getBytes(StandardCharsets.UTF_8));
        this.mapSender.put(hashNumberFile, -1);
        if (hashNumberFile == -1 && !this.otherList.isEmpty()) sendFiles();
        //Verificar condições de saída
        if (checkStop() && !this.otherList.isEmpty()) { this.mySocket.close(); }
        this.lock.unlock();
        return;
    }


    /**
     * Method that handles LAST packets
     * @param hashNumberFile
     */
    public void lastPacketHandler(int hashNumberFile) throws IOException {
        this.lock.lock();
        String fileName= getFileName(hashNumberFile);
        this.logFile.write(("Last packet received from file: " + fileName + "\n").getBytes(StandardCharsets.UTF_8));
        try{
            this.filesReceived.get(hashNumberFile).close();
            this.mapReceiver.put(hashNumberFile, -1);
            this.mySocket.send(sendPacketFYN(hashNumberFile));
        }
        catch (IOException e) { e.printStackTrace(); }
        //Verificar condições de saída
        if(checkStop()) { this.mySocket.close(); }
        this.lock.unlock();
        return;
    }


    /**
     * Method that handles duplicated packets
     * @param packetNumber
     * @param hashNumberFile
     */
    public void duplicatedPacketHandler(int packetNumber, int hashNumberFile){
        try { this.mySocket.send(sendPacketACK(packetNumber, hashNumberFile)); }
        catch (IOException e) { e.printStackTrace(); }
        return;
    }


    /**
     * Method that builds the specified file, given the received packet
     * @param decryptedInfo
     * @param hashNumberFile
     * @param expectedNumberPacket
     */
    public void buildFile(byte[] decryptedInfo, int hashNumberFile, int expectedNumberPacket, int packetNumber) throws IOException {
        this.lock.lock();
        String fileName= getFileName(hashNumberFile);
        this.logFile.write(("Packet number " + packetNumber + " received from file: " + fileName + "\n").getBytes(StandardCharsets.UTF_8));
        this.mapReceiver.put(hashNumberFile, expectedNumberPacket + 1);
        try {
            this.filesReceived.get(hashNumberFile).write(decryptedInfo);
            this.mySocket.send(sendPacketACK(packetNumber, hashNumberFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.lock.unlock();
        return;
    }


    @Override
    public void run() {
        byte[] realInfo = this.receivedPacket.getData();
        int packetNumber = getSequenceNumber(realInfo);
        int hashNumberPacket = getHashNumber(realInfo);
        int hashNumberFile = getFileHashNumber(realInfo);
        //System.out.println("Packet Number: " + packetNumber + " HashNumberPacket: " + hashNumberPacket + " HashNumberFile: " + hashNumberFile);
        int expectedNumberPacket = 0;

        //File List Packet
        if (hashNumberPacket == -2 && this.receivedPacket.getLength()< 1024) {
            try { fileListPacketHandler(realInfo, hashNumberFile); }
            catch (IOException e) { e.printStackTrace(); }
        }

        //ACK Packet
        else if (hashNumberPacket == -1 && this.receivedPacket.getLength() == 12) {
            try { ackPacketHandler(hashNumberFile, packetNumber); }
            catch (IOException e) { e.printStackTrace(); }
        }

        // FYN Packet
        else if (hashNumberPacket == 0 && this.receivedPacket.getLength() == 12) {
            try { fynPacketHandler(hashNumberFile); }
            catch (IOException e) { e.printStackTrace(); }
        }

        //Last Packet
        else if (hashNumberPacket == 2 && this.receivedPacket.getLength() == 12) {
            try { lastPacketHandler(hashNumberFile); }
            catch (IOException e) { e.printStackTrace(); }
        }

        else{
            byte[] decryptedInfo= getDecryptedInfo(realInfo, 12, 1);

            //File chunk corrupted
            if (hashNumberPacket != hash(decryptedInfo)) { return; }

            //Packet duplicated
            else if (packetNumber < expectedNumberPacket) { duplicatedPacketHandler(packetNumber, hashNumberFile); }

            //Packet fine
            else {
                try { buildFile(decryptedInfo, hashNumberFile, expectedNumberPacket, packetNumber); }
                catch (IOException e) { e.printStackTrace(); }
            }
        }
    }
}
