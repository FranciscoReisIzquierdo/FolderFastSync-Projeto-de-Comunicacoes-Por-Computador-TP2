import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class HTTPClient implements Runnable{

    private Socket client;
    //The folder we want to sync
    private List<String> myListOfFiles;

    //The host(s) which we want to sync with
    private List<String> otherList;

    private ReentrantLock lock;
    private InetAddress cliente2IP;
    private String folder;

    public HTTPClient(Socket client, List<String> myListOfFiles, List<String> otherList, InetAddress ip2, ReentrantLock serverLock, String folder){
        this.client= client;
        this.myListOfFiles = myListOfFiles;
        this.otherList = otherList;

        this.cliente2IP= ip2;
        this.lock= serverLock;
        this.folder= folder;
    }

    //Client Handler
    private void handleClient(Socket client) throws IOException {
        this.lock.lock();
        showToClientResponse(client);

        this.lock.unlock();
    }

    public List<String> fileSentClient1(){
        List<String> list= new ArrayList<>();
        for(String l: this.myListOfFiles){
            if(!this.otherList.contains(l)) list.add(l);
        }
        return list;
    }

    public List<String> fileSentClient2(){
        List<String> list= new ArrayList<>();
        for(String l: this.otherList){
            if(!this.myListOfFiles.contains(l)) list.add(l);
        }
        return list;
    }

    private void showToClientResponse(Socket client) throws IOException{
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write("HTTP/1.1 200 OK\r\n".getBytes());
        clientOutput.write(("ContentType: text/html\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.write("<title>FolderFastSync</title>".getBytes());
        clientOutput.write("<h1>FolderFastSync</h1>".getBytes());
        clientOutput.write("\r\n\r\n".getBytes());
        clientOutput.write("<h2>Welcome to FolderFastSync status manager</h2>".getBytes());
        clientOutput.write("\r\n\r\n".getBytes());
        clientOutput.write(("<h3>Folder: " + this.folder).concat("</h3>").getBytes());
        clientOutput.write(("<h3>Clients: [" + this.cliente2IP + "]").concat("</h3>").getBytes());
        clientOutput.write(("<h3>My files:").concat(String.valueOf(this.myListOfFiles)).concat("</h3>").getBytes());
        clientOutput.write(("<h3>Files from client " + this.cliente2IP + ": ").concat(String.valueOf(this.otherList)).concat("</h3>").getBytes());
        List<String> l1= fileSentClient1();
        List<String> l2= fileSentClient2();
        clientOutput.write(("<h3>Files sent to client: " + this.cliente2IP + ": " + l1).concat("</h3>").getBytes());
        clientOutput.write(("<h3>Files received from client: " + this.cliente2IP + ": " + l2).concat("</h3>").getBytes());
        clientOutput.write(("<h3> Number of ACK packets received: " + Receive.ACKNumber).concat("<h3>").getBytes());
        clientOutput.write(("<h3> Number of FYN packets received: " + Receive.FYNNumber).concat("<h3>").getBytes());
        clientOutput.write(("<h3> Number of LAST packets received: " + Receive.LASTNumber).concat("<h3>").getBytes());
        clientOutput.write(("<h3> Number of file packets received: " + Receive.PacketNumber).concat("<h3>").getBytes());
        clientOutput.write("\r\n\r\n".getBytes());
        clientOutput.write("\r\n\r\n".getBytes());
        clientOutput.flush();
        client.close();
    }

    private static Path getFilePath(String path) {
        if ("/".equals(path)) {
            path = "/index.html";
        }

        return Paths.get("/tmp/www", path);
    }

    private static String guessContentType(Path filePath) throws IOException {
        return Files.probeContentType(filePath);
    }

    @Override
    public void run() {
        try {
            handleClient(this.client);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

