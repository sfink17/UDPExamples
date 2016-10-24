package com.simon;
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.*;

/**
 * Reliable UDP server that receives file sent by
 * {@link RUC RUC}. Saves file in message.txt.
 * @author Simon Fink
 */
public class RUS
{
    private static final double LOSS_RATE = 0.3;
    private static final int AVERAGE_DELAY = 100;  // milliseconds
    private static final int port = 4545;
    private static final int tempSize = 100000;
    private static int synack = -1;
    private static FileOutputStream mFos;
    private static BufferedOutputStream mBos;
    private static ByteArrayInputStream mBais;


    public static void main(String[] args) throws Exception
    {
        // Create random number generator for use in simulating
        // packet loss and network delay.
        Random random = new Random();
        mFos = new FileOutputStream("message.txt");
        mBos = new BufferedOutputStream(mFos);
        // Create a datagram socket for receiving and sending UDP packets
        // through the port specified on the command line.
        DatagramSocket socket = new DatagramSocket(port);
        byte[] tempBuf = new byte[tempSize];
        int index = 0;

        // Processing loop.
        while (true) {
            // Create a datagram packet to hold incomming UDP packet.
            DatagramPacket request = new DatagramPacket(new byte[1024], 1024);

            // Block until the host receives a UDP packet.
            socket.receive(request);


            // Decide whether to reply, or simulate packet loss.
            if (random.nextDouble() < LOSS_RATE) {
                System.out.println("   Reply not sent, " + ((Integer) synack).toString());
                continue;
            }

            // Simulate network delay.
            Thread.sleep((int) (random.nextDouble() * 2 * AVERAGE_DELAY));

            // Send reply.
            InetAddress clientHost = request.getAddress();
            int clientPort = request.getPort();
            byte[] buf = request.getData();
            int ack = ((Byte) buf[0]).intValue();
            int done = ((Byte) buf[1]).intValue();

            if (ack == (synack+1)){
                synack++;
                String ackRply = "ACK" + ((Integer) synack).toString();
                byte[] rep = ackRply.getBytes();

                DatagramPacket reply = new DatagramPacket(rep, rep.length, clientHost, clientPort);
                socket.send(reply);
                byte[] snip = Arrays.copyOfRange(buf, 2, buf.length);
                mBais = new ByteArrayInputStream(snip);
                mBais.read(tempBuf, index, buf.length);
                index += 1022;
                if (done == 1){
                    mBos.write(tempBuf, 0, index);
                    mBos.flush();

                    System.out.println("File received.");
                    break;
                }
            }
            else {
                String ackRply = "ACK" + ((Integer) synack).toString();
                byte[] rep = ackRply.getBytes();
                System.out.print(ackRply);

                DatagramPacket reply = new DatagramPacket(rep, rep.length, clientHost, clientPort);
                socket.send(reply);
            }
        }
    }
}

