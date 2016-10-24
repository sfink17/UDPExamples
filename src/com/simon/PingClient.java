package com.simon;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.lang.*;


/**
 * Server to process ping requests over UDP.
 *
 * Uses code taken from Kurose' programming UDP programming assignment.
 * @author Simon Fink
 */
public class PingClient
{
	public static Timer timer;
	public static DatagramSocket socket;
	public static ByteArrayOutputStream output;
    public static BufferedWriter writer;
    public static SimpleDateFormat timeFormat;
    public static InetAddress address;
    public static DatagramPacket serverPacket;
	public static long sent;
	 

   public static void main(String[] args) throws Exception
   {
      // Get command line argument.
      if (args.length != 1) {
         System.out.println("Required arguments: host");
         return;
      }
      String host = args[0];

      // Create a datagram socket for receiving and sending UDP packets
      // through the port specified on the command line.
      socket = new DatagramSocket();
      socket.setSoTimeout(1000);
	 
	  output = new ByteArrayOutputStream(1024);
      writer = new BufferedWriter(new OutputStreamWriter(output));
      timeFormat = new SimpleDateFormat("HH:mm:ss:SSS XXX");
      address = InetAddress.getByName(host);
      serverPacket = new DatagramPacket(new byte[1024], 1024);
	  for (int i = 0; i < 10; i++) {
		String ping = "PING " + i + " " + timeFormat.format(new Date()) + "\r \n";
		try {
		writer.write(ping, 0, ping.length());
		writer.flush();
		byte[] buf = output.toByteArray();
		sent = System.currentTimeMillis();
		socket.send(new DatagramPacket(buf, output.size(), address, 4545));
		output.reset();   
	    }
	    catch (IOException o) {
		    System.out.println("Could not write ping to buffer.");
			break;
	    }
		
		try{
			socket.receive(serverPacket);
			long pingMs = System.currentTimeMillis() - sent;
			Thread.sleep(1000 - pingMs);
			printData(pingMs, serverPacket);
		    }
		catch (SocketTimeoutException s) {
			    System.out.println("Ping timed out.");
		    }
	  }
     
   }

   /**
    * Print ping data to the standard output stream.
    *
    * @param ping The round trip packet delay between client and server.
    * @param request A datagram packet containing the ping information received by the server.
    * @see PingServer#printData
    */
   private static void printData(long ping, DatagramPacket request) throws Exception
   {
      // Obtain references to the packet's array of bytes.
      byte[] buf = request.getData();

      // Wrap the bytes in a byte array input stream,
      // so that you can read the data as a stream of bytes.
      ByteArrayInputStream bais = new ByteArrayInputStream(buf);

      // Wrap the byte array output stream in an input stream reader,
      // so you can read the data as a stream of characters.
      InputStreamReader isr = new InputStreamReader(bais);

      // Wrap the input stream reader in a bufferred reader,
      // so you can read the character data a line at a time.
      // (A line is a sequence of chars terminated by any combination of \r and \n.) 
      BufferedReader br = new BufferedReader(isr);

      // The message data is contained in a single line, so read this line.
	  String pingStr = ((Long) ping).toString();
      String line = pingStr + "ms" + br.readLine().substring(4);

      // Print host address and data received from it.
      System.out.println(
         "Received from " + 
         request.getAddress().getHostAddress() + 
         ": " +
         new String(line) );
   }
}
