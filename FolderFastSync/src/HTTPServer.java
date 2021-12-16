import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class HTTPServer implements Runnable{

    //Our server socket which will listen to clients
    private ServerSocket http_server;

    //The folder we want to sync
    private List<String> myListOfFiles;

    //The host(s) which we want to sync with
    private List<String> otherList;

    private ReentrantLock lock;

    private InetAddress cliente2IP;
    private String folder;

    //Our http port will be 8080
    private static final int port= 80;

    /**
     * Constructor creates a socket at port 80 to handle TCP connection
     * @param myListOfFiles
     * @param otherList
     * @param ip2
     * @param serverLock
     * @param folder
     * @throws IOException
     */
    public HTTPServer(List<String> myListOfFiles, List<String> otherList, InetAddress ip2, ReentrantLock serverLock, String folder) throws IOException {
        try {
            this.http_server = new ServerSocket(port);
            this.myListOfFiles = myListOfFiles;
            this.otherList = otherList;
            this.cliente2IP= ip2;
            this.lock= serverLock;
            this.folder= folder;
        }
        catch (IOException e){ System.out.println("Couldn´t create the socket (Constructor)"); }
    }

    /**
     * Method that return the http socket
     * @return
     */
    public ServerSocket getSocket(){ return this.http_server; }


    //TCP socket is listenning...it will debug once we open a browser and put something like http://<host>:8080/
    /**
     * Method that run from this thread (it waits for http client to connect)
     */
    public void run(){
        if(this.http_server!= null) {
            try {
                while (true) {
                    //HTTP socket is listening for clients
                    Socket client = this.http_server.accept();
                    Thread dedicated_thread = new Thread(new HTTPClient(client, this.myListOfFiles, this.otherList, this.cliente2IP, this.lock, this.folder));
                    dedicated_thread.start();
                }
            } catch (IOException ignored) {

            }
        }
    }
}
