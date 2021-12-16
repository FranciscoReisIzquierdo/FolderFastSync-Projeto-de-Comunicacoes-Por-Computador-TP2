import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Sender implements Runnable{
    private int hashNumberFile;
    private Map<Integer, Integer> mapSequenceNumber;
    private DatagramSocket mySocket;
    private int destPort;
    private InetAddress dest;
    private String file;
    private List<String> list;
    private static final int MAX_BYTES= 1012;
    private String pasta;
    private String pass;

    public Sender(int hashNumberFile, Map<Integer, Integer> mapSequenceNumber, DatagramSocket mySocket, int destPort, InetAddress dest, String file, String folder, String pass){
        this.hashNumberFile= hashNumberFile;
        this.mapSequenceNumber= mapSequenceNumber;
        this.mapSequenceNumber.put(hashNumberFile, 1);
        this.mySocket= mySocket;
        this.destPort= destPort;
        this.dest= dest;
        this.file= file;
        this.pasta= folder;
        this.pass= pass;
    }

    public Sender(List<String> list, DatagramSocket mySocket, int destPort, InetAddress dest, Map<Integer, Integer> mapSender, String pass, String pasta){
        this.list= list;
        this.mySocket= mySocket;
        this.destPort= destPort;
        this.dest= dest;
        this.hashNumberFile= -1;
        this.mapSequenceNumber= mapSender;
        this.mapSequenceNumber.put(-1, 0);
        this.pass= pass;
        this.pasta= pasta;
    }

    public byte[] createPacketList() throws IOException {
        String result = "", prefix = "";
        for (String s: this.list) {
            result += prefix + s;
            File f= new File(String.valueOf(Paths.get(this.pasta, s)));
            prefix = "§";
            byte[] fileContent = Files.readAllBytes(Paths.get(this.pasta, s));
            result+= prefix + hash(fileContent);

            result+= prefix + f.lastModified();

        }
        return result.getBytes();
    }


    /**
     * Method to send the list of files to the client connected
     * @throws IOException
     */
    public void sendingList() throws IOException {
        byte[] buffer= createPacketList();
        ByteArrayOutputStream output= new ByteArrayOutputStream();
        output.write(ByteBuffer.allocate(4).putInt(0).array());
        output.write(ByteBuffer.allocate(4).putInt(-2).array());
        output.write(ByteBuffer.allocate(4).putInt(-1).array());
        output.write(ByteBuffer.allocate(4).putInt(hash(buffer)).array());

        byte[] encryptedInfo= encrypt(buffer, this.pass, hash(buffer));
        output.write(encryptedInfo);
        byte[] out= output.toByteArray();
        DatagramPacket packetList= new DatagramPacket(out, out.length, this.dest, this.destPort);

        while(mapSequenceNumber.get(-1)!= -1){
            try {
                this.mySocket.send(packetList);
                int timer= 200;
                while(this.mapSequenceNumber.get(-1)!= -1 && timer> 0){
                    TimeUnit.MILLISECONDS.sleep(1);
                    timer--;
                }
                if(this.mapSequenceNumber.get(-1)== -1) break;
            } catch (IOException | InterruptedException e) { return; }
        }
    }


    /**
     * Method to send the files to the client connected
     */
    public void sendingFiles() {
        int currentSequence= 1;
        DatagramPacket packet= null;
        try {
            packet = sendPacket(currentSequence, this.file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        while((currentSequence= mapSequenceNumber.get(this.hashNumberFile))!= -1){
            try {
                this.mySocket.send(packet);
                int timer= 200;
                while(currentSequence== this.mapSequenceNumber.get(this.hashNumberFile) && timer> 0){
                    TimeUnit.MILLISECONDS.sleep(1);
                    timer--;
                }
                if(this.mapSequenceNumber.get(this.hashNumberFile)== -1) break;
                if(currentSequence!= mapSequenceNumber.get(this.hashNumberFile)) packet= sendPacket(currentSequence+ 1, this.file);
            }
            catch (IOException | InterruptedException e) { e.printStackTrace(); }
        }
    }


    /**
     * Method to send datagram packets
     * @param packetNumber
     * @return
     */
    public DatagramPacket sendPacket(int packetNumber, String fileName) throws IOException {
        byte[] out= preparePacket(packetNumber, fileName);
        return new DatagramPacket(out, out.length, this.dest, this.destPort);
    }


    /**
     * This method is just for "debugging"
     * @param packet_number
     * @return
     * @throws IOException
     */
    public byte[] preparePacket(int packet_number, String filename) throws IOException {
        Path path = Paths.get(this.pasta, filename);
        long bytes = Files.size(path);

        if(bytes<= (packet_number- 1)* MAX_BYTES){
            ByteArrayOutputStream output= new ByteArrayOutputStream();
            output.write(ByteBuffer.allocate(4).putInt(packet_number).array());
            output.write(ByteBuffer.allocate(4).putInt(2).array());
            output.write(ByteBuffer.allocate(4).putInt(hash(filename.getBytes())).array());
            byte[] out= output.toByteArray();
            return out;
        }
        byte[] info= new byte[MAX_BYTES];
        FileInputStream r= new FileInputStream(Paths.get(this.pasta, filename).toString());
        r.getChannel().position((long) (packet_number - 1) * MAX_BYTES);
        int value= r.read(info);
        info= Arrays.copyOf(info, value);

        ByteArrayOutputStream output= new ByteArrayOutputStream();
        output.write(ByteBuffer.allocate(4).putInt(packet_number).array());
        output.write(ByteBuffer.allocate(4).putInt(hash(info)).array());
        output.write(ByteBuffer.allocate(4).putInt(hash(filename.getBytes())).array());
        byte[] encryptedPacket= encrypt(info, this.pass, hash(info));
        output.write(encryptedPacket);
        byte[] out= output.toByteArray();
        return out;
    }

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

    @Override
    public void run() {
        if(this.hashNumberFile== -1) {
            try { sendingList(); }
            catch (IOException e) { e.printStackTrace(); }
        }
        else{
            sendingFiles();
        }
    }


    /**
     * Method to encrypt the packets that are sent
     * @param packet
     * @param password
     * @return
     */
    public static byte[] encrypt(byte[] packet, String password, int hash){
        int hashNumber= hash(password.getBytes(StandardCharsets.UTF_8));
        int deficit= hashNumber+ hash;
        for(int i= 0; i< packet.length; i++) packet[i]+= deficit;

        return packet;
    }
}
