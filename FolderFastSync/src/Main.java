import java.io.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;


public class Main {

    public static void main(String[] args) throws SocketException, InterruptedException, FileNotFoundException, UnknownHostException {
        if(args.length< 2 || args.length> 3){
            System.out.println("Number of arguments invalid");
            return;
        }
        int port1= 50001;
        int port2= 50000;

        //192.168.56.1

        InetAddress ipDest= InetAddress.getByName(args[1]);
        File f= new File(args[0]);
        if(!f.exists()){
            System.out.println("The file path doesn't exist");
            return;
        }
        List<String> l= Arrays.asList(f.list());

        String pass= null;
        Console console = System.console();
        if (console == null) {
            System.out.print("Type the password:");
            Scanner sc= new Scanner(System.in);
            pass= sc.nextLine();
        }

        else{
            char[] passwordArray = console.readPassword("Type the password:");
            pass= String.valueOf(passwordArray);
        }

        if(args.length== 2) {
            DatagramSocket s1 = new DatagramSocket(port1);
            Thread r1 = new Thread(new Receive(s1, l, port2, args[0], pass, ipDest));
            r1.start();
            r1.join();
        }

        else if (args.length== 3){
            DatagramSocket s2= new DatagramSocket(port2);
            Thread r2 = new Thread(new Receive(s2, l, port1, args[0], pass, ipDest));
            r2.start();
            r2.join();
        }
    }
}
