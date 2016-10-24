package com.simon;
import java.io.*;
import java.net.*;
import java.text.*;
import java.lang.*;
import java.nio.channels.*;


/**
 * A model for a reliable UDP client.
 * Deals with packet loss through cumulative acknowledgement protocol.
 * Contains threads for sending and receiving data.
 * Uses modified FileInputSream class taken from user ykaganovich at
 * <a href="http://stackoverflow.com/questions/1094703/java-file-input-with-rewind-reset-capability">stackoverflow.com</a>
 * to allow for resets to points within the file.
 *
 * @author Simon Fink
 */
public class RUC
{
	private static DatagramSocket socket;
	private static ByteArrayOutputStream output;
	private static BufferedWriter writer;
	private static SimpleDateFormat timeFormat;
	private static InetAddress address;
	private static DatagramPacket serverPacket;
	private static UdpWriterThread mWriter;
	private static UdpReaderThread mReader;
	private static boolean nack;
	private static boolean waitingForLast;
	private static int synack;
	private static int lastSyn;
	private static FileChannel myFileChannel;

	/**
 	 * An overridden FilterInputStream making {@link FileInputStream#mark() FileInputStream.mark()} supported.
 	 * Necessary for resetting of file to last acknowledged point.
 	 */

	private static class MarkableFileInputStream extends FilterInputStream {

		private long mark = -1;


		public MarkableFileInputStream(FileInputStream fis) {
			super(fis);
			myFileChannel = fis.getChannel();
		}

		@Override
		public boolean markSupported() {
			return true;
		}

		@Override
		public synchronized void mark(int readlimit) {
			try {
				mark = myFileChannel.position();
			} catch (IOException ex) {
				mark = -1;
			}
		}

		@Override
		public synchronized void reset() throws IOException {
			if (mark == -1) {
				throw new IOException("not marked");
			}
			myFileChannel.position(mark);
		}
	}

	/**
 	 * Thread for handling file sending.
 	 */

	private static class UdpWriterThread extends Thread{
		private final File mFile;
		private final FileInputStream mFis;
		private final MarkableFileInputStream mMis;

		/*
 	     * @param file The file to be sent
 	     */

		public UdpWriterThread(File file) throws Exception{
			mFile = file;
			mFis = new FileInputStream(file);
			mMis = new MarkableFileInputStream(mFis);
		}

		public void run(){

			byte[] buf = new byte[1024];
			int syn = 0;
			mMis.mark(0);

			outer:
			while (true) {
				if (nack){
					syn = synack;
					try {
						mMis.reset();
						long skipped = mMis.skip((syn)*1022);
						while (skipped < (syn*1022)){
							skipped = mMis.skip((syn*1022)-skipped);
						}
						nack = false;
					}
					catch (IOException io) {
						System.out.println("Whoops");
					}
				}
				try {

					// Simple header indicating sequence number and last packet boolean.
					buf[0] = ((Integer) syn).byteValue();
					buf[1] = 0;

					int fin = mMis.read(buf, 2, buf.length - 2);
					System.out.print(synack);
					if (fin == -1 && synack != syn-1) continue;
					else if (fin == -1) {
						buf[1] = 1;
						lastSyn = syn;
						System.out.println(lastSyn);
						DatagramPacket last = new DatagramPacket(buf, buf.length, address, 4545);
						waitingForLast = true;
						while (waitingForLast){
							socket.send(last);
							if (lastSyn == synack){
								waitingForLast = false;
								System.out.println("File sent.");
								break outer;
							}
							try {
								Thread.sleep(50);
							}
							catch (InterruptedException ie){
								System.out.println("Could not write file to buffer.");
							}

						}
					}
					socket.send(new DatagramPacket(buf, buf.length, address, 4545));
					try {
						Thread.sleep(100);
					}
					catch (InterruptedException ie){
						System.out.println("Could not write file to buffer.");
					}
					syn++;
				}
				catch (IOException o) {
					System.out.println("Could not write file to buffer.");
					break;
				}
			}
		}
	}

	/**
 	 * Thread for handling acknowledgement packets from {@link RUS RUS}
 	 */

	private static class UdpReaderThread extends Thread{
		long timeout;
		boolean inTimeout = false;

		public void run() {
			synack = 0;
			while (true) {
				try{
					socket.receive(serverPacket);
					byte [] buf = serverPacket.getData();
					String isGood = new String(buf, 0, buf.length);
					if (isGood.contains("ACK" + ((Integer) synack).toString())){
						if (nack) nack = false;
						synack++;
						if (lastSyn == synack){
							break;
						}
					}
					else if (!inTimeout){
						timeout = System.currentTimeMillis();
						nack = true;
						inTimeout = true;
					}
					else if ((System.currentTimeMillis() - timeout) > 500){
						timeout = System.currentTimeMillis();
						nack = true;
						inTimeout = true;
					}


				}
				catch (IOException o) {
					System.out.println("Could not read server response");
				}
			}
		}
	}


	public static void main(String[] args) throws Exception
	{
		// These comments can be deleted to give the option to specify file
		// to send and host as arguments.
		/* Get command line argument.
		if (args.length != 2) {
			System.out.println("Required arguments: host and file");
			return;
		}
		 */
		String host = "127.0.0.1";
		File mFile = new File("src/com/simon/howdy.txt");

		// Create a datagram socket for receiving and sending UDP packets
		// through the port specified on the command line.
		socket = new DatagramSocket();

		output = new ByteArrayOutputStream(1024);
		writer = new BufferedWriter(new OutputStreamWriter(output));
		timeFormat = new SimpleDateFormat("HH:mm:ss:SSS XXX");
		address = InetAddress.getByName(host);
		serverPacket = new DatagramPacket(new byte[1024], 1024);
		mWriter = new UdpWriterThread(mFile);
		mReader = new UdpReaderThread();
		nack = false;
		lastSyn = -1;

		mReader.start();
		mWriter.start();

	}

}
