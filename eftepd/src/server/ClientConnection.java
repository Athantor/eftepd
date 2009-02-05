/*
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 ---
 Copyright (C) 2009, Krzysztof Kundzicz <athantor@gmail.com>
 */

/**
 * 
 */
package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Semaphore;

import logging.Logger;
import logging.Logger.Lvl;
import server.DataSocketCreator.Status;
import settings.Account;
import settings.SettingsManager;

/**
 * @author athantor
 * 
 */
public final class ClientConnection implements Runnable {

	private Socket csock, dsock = null;
	private DataSocketCreator dsc;
	private Logger log;
	private Boolean kill = false;
	private BufferedReader read;
	private PrintWriter write;
	private SettingsManager smngr;
	private File wdir = null;
	private Type currt;
	private InetSocketAddress port_addr;

	private String uname = null, pass = null;
	private Account accnt = null;

	private Integer idlemstime = 900000;
	private Long st_transf = 0L, st_conns = 0L;

	private enum Type {
		ASCII, IMAGE
	};

	Semaphore logsem;

	/**
	 * 
	 */
	public ClientConnection(Socket s, Logger l, SettingsManager sm) {
		if (s == null) {
			throw new IllegalArgumentException("socket is null");
		}

		csock = s;
		log = l;
		smngr = sm;
		logsem = new Semaphore(1, true);

		logsem.acquireUninterruptibly();
		log.addConnectionMsg(csock, "connected", Lvl.NORMAL);
		logsem.release();

		idlemstime = 900000;

		if (smngr.getServerSett().hasProperty("ClientIdleTimeout")) {
			String del = smngr.getServerSett().getProperty("ClientIdleTimeout")
					.trim();
			try {
				idlemstime = Integer.parseInt(del);
				if (idlemstime <= 0) {
					throw new IllegalArgumentException("ClientIdleTimeout <= 0");
				}
			} catch (Exception e) {
				logsem.acquireUninterruptibly();
				log.addMiscMsg(null, "Invalid setting „ClientTimeout”: "
						+ e.getLocalizedMessage(), Lvl.ERROR);
				logsem.release();

				idlemstime = 900000;
			}

		}

		try {
			csock.setSoTimeout(idlemstime);
		} catch (SocketException e) {
			logsem.acquireUninterruptibly();
			log.addMiscMsg(null, "Failed to set socket timeout”: "
					+ e.getLocalizedMessage(), Lvl.ERROR);
			logsem.release();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {

		try {
			read = new BufferedReader(new InputStreamReader(csock
					.getInputStream()));
			write = new PrintWriter(new OutputStreamWriter(csock
					.getOutputStream()));
		} catch (IOException e) {
			logsem.acquireUninterruptibly();

			log.addConnectionMsg(csock, "disconnected - error", Lvl.ERROR);
			log.addMiscMsg(null, String.format("Failed to get i/o with %s: %s",
					csock.getInetAddress().getHostName(), e
							.getLocalizedMessage()), Logger.Lvl.ERROR);

			logsem.release();

			return;
		}

		currt = Type.ASCII;
		printHelloMsg();

		String cmdline = "";

		try {
			while (!kill && ((cmdline = read.readLine()) != null)) {
				parseCommand(cmdline);
			}

		} catch (SocketTimeoutException e) {

			logsem.acquireUninterruptibly();
			log.addConnectionMsg(csock, "connection timeout", Lvl.NOTICE);
			logsem.release();

			write.print("421 Goodbye sleepyhead! (timeout; "
					+ (idlemstime / 1000) + "s)\r\n");
			write.flush();

			try {
				csock.close();
			} catch (IOException e1) {
				logsem.acquireUninterruptibly();
				log.addMiscMsg(null, "error closing socket: "
						+ e1.getLocalizedMessage(), Lvl.WARNING);
				logsem.release();
			}

			if (dsock == null) {
				return;
			}

		} catch (IOException e) {
			logsem.acquireUninterruptibly();
			log.addConnectionMsg(csock, "connection lost: "
					+ e.getLocalizedMessage(), Lvl.NOTICE);
			logsem.release();

			if (dsock == null) {
				return;
			}

		} /*
		 * catch (Exception e) { write.print(
		 * "451-OMG, I suck! Internal server error! Blargh! I AM DEAD!X_X\r\n451 "
		 * + e.getMessage() + "\r\n"); write.flush(); try { csock.close(); }
		 * catch (IOException e1) { }
		 * 
		 * return; }
		 */

		logsem.acquireUninterruptibly();
		log.addConnectionMsg(csock, "disconnect", Lvl.NORMAL);
		logsem.release();

		try {
			csock.close();
		} catch (IOException e) {
			logsem.acquireUninterruptibly();
			log.addConnectionMsg(csock, "disconnect failed: "
					+ e.getLocalizedMessage(), Lvl.ERROR);
			logsem.release();

			csock = null;
		}

		if (dsock == null) {
			return;
		}

	}

	/**
	 * @param readLine
	 */
	private void parseCommand(String readLine) {
		if (readLine == null) {
			return;
		}

		if (readLine.toUpperCase().startsWith("USER")) {
			doUserCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("PASS")) {
			doPassCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("NOOP")) {
			doNoopCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("QUIT")) {
			doQuitCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("ACCT")) {
			doAcctCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("CWD")) {
			doCwdCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("CDUP")) {
			doCdupCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("LIST")) {
			doListCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("PASV")) {
			doPasvCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("EPSV")) {
			doEpsvCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("PWD")) {
			doPwdCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("TYPE")) {
			doTypeCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("SYST")) {
			doSystCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("MODE")) {
			doModeCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("STRU")) {
			doStruCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("PORT")) {
			doPortCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("RETR")) {
			doRetrCmd(readLine);
		} else if (readLine.toUpperCase().startsWith("STOR")) {
			doStorCmd(readLine);
		} else {
			write.print("500 Waddya mean by '" + readLine + "'?\r\n");
			write.flush();

			logsem.acquireUninterruptibly();
			log
					.addCtlMsg(csock, "Got unknown command: " + readLine,
							Lvl.NORMAL);
			logsem.release();
		}
	}

	private void doStorCmd(String readLine) {
		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'STOR' cmd:" + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		if (!chechAreCmdArgsCntOk(readLine, 1)) {
			return;
		}

		if (wdir == null) {
			wdir = new File(accnt.getHomeDir().getAbsolutePath());
		}

		String[] cmd = readLine.split(" ", 2);
		File f = new File(cmd[1]);

		if (!f.isAbsolute()) {
			f = new File(wdir, cmd[1]);
		}
		if (!f.exists()) {
			try {
				f.createNewFile();
			} catch (IOException e) {
				write.print("450 Can't create new file: " + e.getMessage()
						+ "'\r\n");
				write.flush();
				return;
			}
		}

		if (!f.canWrite()) {
			write.print("450 Can't access file: " + f.getAbsolutePath()
					+ "'\r\n");
			write.flush();
			return;
		} else {
			write.print("150 Give it to me baby!\r\n");
			write.flush();
		}

		Socket s = getDataSocket();

		if (s == null) {
			write.print("425 Can't open socket\r\n");
			write.flush();
			return;
		}

		Long start = System.currentTimeMillis();

		final Integer WRBLKSIZE = 1024;

		FileOutputStream fos;
		try {
			fos = new FileOutputStream(f, false);
		} catch (FileNotFoundException e2) {
			try {
				s.close();
			} catch (IOException e) {

			}

			write.print("450 Can't read socket: " + e2.getMessage() + "'\r\n");
			write.flush();
			return;
		}
		;
		byte[] buf = new byte[WRBLKSIZE];

		Integer ctr = 0;
		Long totctr = 0L;
		Long quota = -1L;

		if (wdir.getAbsolutePath().compareTo(f.getParent()) == 0) {

			quota = accnt.getQuota();

			if (quota == -1) {
				if (smngr.getServerSett().hasProperty("UserQuota")) {
					try {
						quota = Long.parseLong(smngr.getServerSett()
								.getProperty("UserQuota"));
					} catch (Exception e) {
						quota = -1L;
					}
				}
			}
		}

		Long totdirsize = getTotDirSize(f);

		/*
		 * !!!!!!!!
		 */
		do {

			buf = new byte[WRBLKSIZE];
			try {
				ctr = s.getInputStream().read(buf);
			} catch (IOException e) {
				try {
					s.close();
				} catch (IOException e1) {

				}

				write.print("450 Can't read socket: " + e.getMessage()
						+ "'\r\n");
				write.flush();
				return;
			}

			if (ctr > 0) {
				try {
					if (currt == Type.ASCII) {
						buf = new String(buf, 0, ctr).replaceAll("\r\n",
								System.getProperty("line.separator"))
								.getBytes();
						ctr = buf.length;
					}

					if (quota > -1) {

						if ((totdirsize + ctr) >= quota) {

							write.print("552 Quota exceeded: "
									+ (totdirsize + ctr) + " >= " + quota);
							write.flush();

							s.close();

							logsem.acquireUninterruptibly();
							log.addXfrMsg(csock, "Quota exceeded for "
									+ f.getAbsolutePath() + ": " + totdirsize
									+ " >= " + quota, Lvl.NORMAL);
							logsem.release();

							return;
						} else {
							totdirsize += ctr;
						}
					}

					fos.write(buf, 0, ctr);

					totctr += ctr;

				} catch (IOException e) {
					try {
						s.close();
					} catch (IOException e1) {

					}

					write.print("450 Can't write to file: " + e.getMessage()
							+ "'\r\n");
					write.flush();
					return;
				}
			}
		} while ((ctr != -1));

		try {
			s.close();
		} catch (IOException e) {

		}

		Double ts = (System.currentTimeMillis() - start) / 1000.0;
		logsem.acquireUninterruptibly();
		log.addXfrMsg(csock, String.format(
				"Got from you file %s in %.2f s with %.2f KB/s ", f
						.getAbsolutePath(), ts, (totctr / 1024.0) / ts),
				Lvl.NORMAL);
		logsem.release();

		write.print(String.format(
				"226 Uploaded file %s in %.2f s with %.2f KB/s\r\n", f
						.getAbsolutePath(), ts, (totctr / 1024.0) / ts));
		write.flush();

	}

	private Long getTotDirSize(File f) {
		if (f.getParentFile() == null) {
			return -1L;
		}

		Long size = 0L;

		for (File file : f.getParentFile().listFiles()) {
			size += file.length();
		}

		return size;
	}

	/**
	 * @param readLine
	 */
	private void doRetrCmd(String readLine) {
		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'STRU' cmd:" + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		if (!chechAreCmdArgsCntOk(readLine, 1)) {
			return;
		}

		if (wdir == null) {
			wdir = new File(accnt.getHomeDir().getAbsolutePath());
		}

		String[] cmd = readLine.split(" ", 2);
		File f = new File(cmd[1]);

		if (!f.isAbsolute()) {
			f = new File(wdir, cmd[1]);
		}

		if (!f.exists() || !f.canRead()) {
			write.print("450 Can't access file: " + f.getAbsolutePath()
					+ "'\r\n");
			write.flush();
			return;
		} else {
			write.print("150 Get ready for " + f.length() + "bytes!\r\n");
			write.flush();
		}

		Socket s = getDataSocket();

		if (s == null) {
			write.print("425 Can't open socket\r\n");
			write.flush();
			return;
		}

		Long start = System.currentTimeMillis();

		final Integer RDBLKSIZE = 1024;
		FileInputStream fis;
		try {
			fis = new FileInputStream(f);
		} catch (FileNotFoundException e) {
			write.print("550 Can't read file:" + e.getMessage() + "\r\n");
			write.flush();
			return;
		}

		byte[] buf = new byte[RDBLKSIZE];
		Long ctr = 0L;

		try {
			while ((fis.available() >= RDBLKSIZE) && ((fis.read(buf)) != -1)) {

				if (currt == Type.ASCII) {
					if (!System.getProperty("line.separator").equalsIgnoreCase(
							"\r\n")) {
						buf = new String(buf, 0, RDBLKSIZE).replaceAll(
								System.getProperty("line.separator"), "\r\n")
								.getBytes();
					}

				}

				s.getOutputStream().write(buf);

				ctr += RDBLKSIZE;

				buf = new byte[RDBLKSIZE];

			}

			if ((fis.available() > 0)) {
				buf = new byte[fis.available()];
				fis.read(buf);

				if (currt == Type.ASCII) {
					if (!System.getProperty("line.separator").equalsIgnoreCase(
							"\r\n")) {
						buf = new String(buf).replaceAll(
								System.getProperty("line.separator"), "\r\n")
								.getBytes();
					}

				}

				s.getOutputStream().write(buf);
				ctr += buf.length;
			}

			s.close();

			Double ts = (System.currentTimeMillis() - start) / 1000.0;
			logsem.acquireUninterruptibly();
			log.addXfrMsg(csock, String.format(
					"Uploaded file %s in %.2f s with %.2f KB/s ", f
							.getAbsolutePath(), ts, (ctr / 1024.0) / ts),
					Lvl.NORMAL);
			logsem.release();

			write.print(String.format(
					"226 Uploaded file %s in %.2f s with %.2f KB/s\r\n", f
							.getAbsolutePath(), ts, (ctr / 1024.0) / ts));
			write.flush();

		} catch (Exception e) {
			write.print("451 Can't send file:" + e.getMessage() + "\r\n");
			write.flush();
			return;
		}

	}

	/**
	 * @param readLine
	 */
	private void doPortCmd(String readLine) {
		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'STRU' cmd:" + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		if (!chechAreCmdArgsCntOk(readLine, 1)) {
			return;
		}

		String[] cmd = readLine.split(" ", 2);
		String[] addr = cmd[1].split(",");

		if (addr.length != 6) {
			write.print("501 Invalid format of PORT address\r\n");
			write.flush();
			return;
		}

		String ip = String.format("%s.%s.%s.%s", addr[0], addr[1], addr[2],
				addr[3]);

		int port = 20;

		try {
			port = (Integer.parseInt(addr[4]) * 256)
					+ Integer.parseInt(addr[5]);
		} catch (NumberFormatException e) {
			write.print("501 Invalid format of PORT address: " + e.getMessage()
					+ "\r\n");
			write.flush();
			return;
		}

		try {
			if (InetAddress.getByName(ip) instanceof Inet6Address) {
				write
						.print("501 Invalid format of PORT address: IPv6 in PORT not supported\r\n");
				write.flush();
			} else {
				port_addr = new InetSocketAddress(InetAddress.getByName(ip),
						port);

				write.print("200 PORT OK\r\n");
				write.flush();

			}
		} catch (Exception e) {
			write.print("501 Invalid format of PORT address: " + e.getMessage()
					+ "\r\n");
			write.flush();
		}

	}

	/**
	 * @param readLine
	 */
	private void doStruCmd(String readLine) {
		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'STRU' cmd:" + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		write.print("200 STRUCTURE is always FILE\r\n");
		write.flush();

	}

	/**
	 * @param readLine
	 */
	private void doModeCmd(String readLine) {
		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'TYPE' cmd:" + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		write.print("200 MODE is always STREAM\r\n");
		write.flush();

	}

	/**
	 * @param readLine
	 */
	private void doSystCmd(String readLine) {
		/*
		 * if (accnt == null) { notLoggedInErrMsg(readLine); return; }
		 */

		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'SYST' cmd:" + readLine, Lvl.NORMAL);
		logsem.release();

		if (!chechAreCmdArgsCntOk(readLine, 0)) {
			return;
		}
		write.print("215 UNIX type: L8 (" + System.getProperty("os.name")
				+ "; " + System.getProperty("java.vendor") + " Java)\r\n");
		write.flush();
	}

	/**
	 * @param readLine
	 */
	private void doTypeCmd(String readLine) {
		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'TYPE' cmd:" + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		String[] cmd = readLine.split(" ", 3);

		if (cmd.length == 1) {
			write.print("501 You forgot tell me which type to set\r\n");
			write.flush();
		} else {
			String mod;
			if (cmd.length == 2) {
				mod = "";
			} else {
				mod = "; option is always 'N'";
			}

			if (cmd[1].equalsIgnoreCase("A")) {
				currt = Type.ASCII;
				write.print("200 Set type ASCII" + mod + "\r\n");
			} else if (cmd[1].equalsIgnoreCase("I")) {
				currt = Type.IMAGE;
				write.print("200 Set type IMAGE/BINARY" + mod + "\r\n");
			} else if (cmd[1].equalsIgnoreCase("E")
					|| cmd[1].equalsIgnoreCase("L")) {
				write.print("504 Representatyion type '" + cmd[1]
						+ "' not supported\r\n");
			} else {
				write.print("501 Representation type '" + cmd[1]
						+ "' is invalid\r\n");
			}
		}

		write.flush();

	}

	/**
	 * @param readLine
	 */
	private void doPwdCmd(String readLine) {
		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'PWD' cmd", Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		if (!chechAreCmdArgsCntOk(readLine, 0)) {
			return;
		}

		if (wdir == null) {
			wdir = new File(accnt.getHomeDir().getAbsolutePath());
		}

		write.print("257 \"" + wdir.getAbsolutePath().replaceAll("\"", "\"\"")
				+ "\" <- you are here\r\n");
		write.flush();

	}

	private Socket getDataSocket() {
		if (dsc != null) {
			if (dsc.getStatus() == Status.FINISHED) {
				Socket s = dsc.getDataSocket();
				try {
					dsc.join(250);
				} catch (InterruptedException e) {
				}

				dsc = null;

				st_conns++;
				return s;
			} else if (dsc.getStatus() == Status.WAITNIG) {
				while (dsc.getStatus() == Status.WAITNIG) {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
					}
				}
				st_conns++;
				return getDataSocket();

			} else if (dsc.getStatus() == Status.ERROR) {
				write.print("425 Can't open passive data connection: "
						+ dsc.getExc().getMessage() + "\r\n");
				write.flush();

				dsc = null;

				return null;

			} else {
				write
						.print("425 Can't open passive data connection: there is noone on the other side\r\n");
				write.flush();

				dsc = null;

				return null;
			}
		} else if (port_addr != null) {
			try {
				Socket s = new Socket(port_addr.getHostName(), port_addr
						.getPort());

				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock, "Made succesful active connection @ "
						+ port_addr, Lvl.NORMAL);
				logsem.release();

				port_addr = null;
				st_conns++;
				return s;

			} catch (Exception e) {
				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock,
						"Failed to make  succesful active connection @ "
								+ port_addr, Lvl.NORMAL);
				logsem.release();

				write.print("425 PORT FAIL: " + e.getMessage() + "\r\n");
				write.flush();

				port_addr = null;
				return null;
			}
		} else {
			try {
				Socket s = new Socket(csock.getInetAddress(), 20);

				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock,
						"Made succesful active connection @ p. 20", Lvl.NORMAL);
				logsem.release();

				return s;
			} catch (IOException e) {

				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock,
						"Failed to make succesful active connection @ p. 20",
						Lvl.WARNING);
				logsem.release();

				write.print("425 Can't open active def data connection: "
						+ e.getMessage() + "\r\n");
				write.flush();

				return null;
			}
		}

	}

	/**
	 * @param readLine
	 */
	private void doEpsvCmd(String readLine) {
		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'EPSV' command: " + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		String[] cmd = readLine.split(" ", 2);

		InetSocketAddress isa = makePassiveDataSocket(readLine);

		if (cmd.length == 2) {

			if (cmd[1].equalsIgnoreCase("ALL")) {
				write.print("504 ALL for EPSV not implemented\r\n");
			} else if (cmd[1].equalsIgnoreCase("1")
					&& !(isa.getAddress() instanceof Inet4Address)) {
				write.print("522 Network protocol not supported, use (2)\r\n");
			} else if (cmd[1].equalsIgnoreCase("2")
					&& !(isa.getAddress() instanceof Inet6Address)) {
				write.print("522 Network protocol not supported, use (1)\r\n");
			} else if (cmd[1].equalsIgnoreCase("1")
					|| cmd[1].equalsIgnoreCase("2")) {

				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock,
						"Went into EPSV on port: " + isa.getPort(), Lvl.NORMAL);
				logsem.release();

				write.print("229 Entering Extended Passive Mode (|||"
						+ isa.getPort() + "|)\r\n");
			} else {
				write
						.print("501 Authors of RFC2428 would be sad becase of you using invalid parameters\r\n");
			}
		} else if (cmd.length == 1) {

			logsem.acquireUninterruptibly();
			log.addCtlMsg(csock, "Went into EPSV on port: " + isa.getPort(),
					Lvl.NORMAL);
			logsem.release();

			write.print("229 Entering Extended Passive Mode (|||"
					+ isa.getPort() + "|)\r\n");
		} else {
			write
					.print("501 Authors of RFC2428 would be sad becase of you using invalid parameters\r\n");
		}

		write.flush();

	}

	/**
	 * @param readLine
	 */
	private void doPasvCmd(String readLine) {

		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'PASV' command", Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		String addr = makePasvAddr(makePassiveDataSocket(readLine));

		if (addr.charAt(0) != '(') {

			logsem.acquireUninterruptibly();
			log.addCtlMsg(csock, "Failed to bind PASV ssock: " + addr,
					Lvl.ERROR);
			logsem.release();

			write.print("425 " + addr + "\r\n");
		} else {

			logsem.acquireUninterruptibly();
			log.addCtlMsg(csock, "Bound PASV sock: " + addr, Lvl.NORMAL);
			logsem.release();

			write.print("227 Entering Passive Mode " + addr + "\r\n");
		}

		write.flush();

	}

	private String makePasvAddr(InetSocketAddress isa) {

		if (isa.getAddress() instanceof Inet6Address) {
			return "IPv6 in PASV not supported";
		}

		int port = isa.getPort();
		int p1 = port / 256;

		return "(" + isa.getAddress().getHostAddress().replace('.', ',') + ","
				+ p1 + "," + (port - (p1 * 256)) + ")";

	}

	/**
	 * @param readLine
	 */
	private InetSocketAddress makePassiveDataSocket(String readLine) {

		dsc = new DataSocketCreator(smngr, log);

		InetSocketAddress isa = null;

		try {
			isa = dsc.prepare();
			dsc.start();
		} catch (Exception e) {
			logsem.acquireUninterruptibly();
			log.addMiscMsg(null, "Failed to make data ssocket: "
					+ e.getLocalizedMessage(), Lvl.NORMAL);
			logsem.release();
		}

		return isa;

	}

	/**
	 * @param readLine
	 */
	private void doListCmd(String readLine) {

		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'LIST' cmd: " + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		if (wdir == null) {
			wdir = new File(accnt.getHomeDir().getAbsolutePath());
		}

		Socket s = getDataSocket();

		if (s == null) {
			return;
		}

		String[] cmd = readLine.split(" ");
		ArrayList<String> ret;

		/*
		 * if (cmd.length == 1) { write.print("150 Listing '.'\r\n");
		 * write.flush();
		 * 
		 * ret = makeCrappyLs(".");
		 * 
		 * } else if (cmd.length == 2) { write.print("150 Listing '" + cmd[1] +
		 * "'\r\n"); write.flush();
		 * 
		 * ret = makeCrappyLs(cmd[1]);
		 * 
		 * } else {
		 */
		String t = null;
		for (String cc : cmd) {
			if (!cc.equalsIgnoreCase("LIST") && !cc.startsWith("-")) {
				t = cc;
				break;
			}
		}

		if (t == null) {
			ret = makeCrappyLs(".");
			write.print("150 Listing '.'; options not supported\r\n");
			write.flush();
		} else {
			write.print("150 Listing '" + t + "'; options not supported'\r\n");
			write.flush();

			ret = makeCrappyLs(t);
		}

		// }

		if (ret != null) {
			try {
				OutputStreamWriter osw = new OutputStreamWriter(s
						.getOutputStream());

				for (String ls : ret) {
					try {
						osw.write(ls + "\r\n");
						st_transf += (ls + "\r\n").length();
					} catch (IOException e1) {
						logsem.acquireUninterruptibly();
						log.addMiscMsg(null, "Can't write to socket: "
								+ e1.getLocalizedMessage(), Lvl.ERROR);
						logsem.release();

						write.print("426 Connection b0rked: " + e1.getMessage()
								+ "\r\n");
						write.flush();

						try {
							s.close();
						} catch (IOException e) {
							logsem.acquireUninterruptibly();
							log.addMiscMsg(null, "Can't close socket: "
									+ e.getLocalizedMessage(), Lvl.ERROR);
							logsem.release();
						}

						return;
					}
				}

				osw.flush();
				osw.close();

				try {
					s.close();
				} catch (IOException e) {
					logsem.acquireUninterruptibly();
					log.addMiscMsg(null, "Can't close socket: "
							+ e.getLocalizedMessage(), Lvl.ERROR);
					logsem.release();
				}

				write.print("226 Listing done\r\n");
				write.flush();

			} catch (IOException e) {

				logsem.acquireUninterruptibly();
				log.addMiscMsg(null, "Can't open socket: "
						+ e.getLocalizedMessage(), Lvl.ERROR);
				logsem.release();

				write.print("425 Can't do data connection: " + e.getMessage()
						+ "\r\n");
				write.flush();
			}
		}

		try {
			s.close();
		} catch (IOException e) {
			logsem.acquireUninterruptibly();
			log.addMiscMsg(null, "Can't close socket: "
					+ e.getLocalizedMessage(), Lvl.ERROR);
			logsem.release();
		}

	}

	/**
	 * @param readLine
	 */
	private ArrayList<String> makeCrappyLs(String pth) {
		File thetgt = new File(pth);

		if (pth.equalsIgnoreCase("..")) {
			thetgt = wdir.getParentFile();
		} else if (pth.equalsIgnoreCase(".")) {
			thetgt = wdir.getAbsoluteFile();
		} else if (!thetgt.isAbsolute()) {
			thetgt = new File(wdir, pth);
		}

		if (!thetgt.exists()) {
			write.print("550 Object @ '" + pth + "' doesn't exist\r\n");
			write.flush();

			return null;
		}

		if (thetgt.isDirectory()) {
			return makeDirLs(thetgt);
		} else {
			return makeNotDirLs(thetgt, false);
		}
	}

	/**
	 * @param thetgt
	 * @return
	 */
	@SuppressWarnings("deprecation")
	private ArrayList<String> makeNotDirLs(File thetgt, Boolean cnt) {
		ArrayList<String> ret = new ArrayList<String>();

		Date d = new Date(thetgt.lastModified());
		String hr;

		if (d.getYear() != (new Date()).getYear()) {
			hr = String.valueOf(1900 + d.getYear());
		} else {

			hr = String.format(Locale.ENGLISH, "%tR", d);
		}

		String fn;

		if (!cnt) {
			fn = thetgt.getAbsolutePath();
		} else {
			fn = thetgt.getName();
		}

		ret.add(String.format(Locale.ENGLISH,
				"%-10s 0 unknown unknown %12d %tb %td %5s %s",
				makeFileRights(thetgt), thetgt.length(), d, d, hr, fn));

		return ret;
	}

	/**
	 * @param thetgt
	 * @return
	 */
	private String makeFileRights(File thetgt) {
		String rgt = "";

		if (thetgt.isDirectory()) {
			rgt += "d";
		} else {
			rgt += "-";
		}

		if (thetgt.canRead()) {
			rgt += "r";
		} else {
			rgt += "-";
		}

		if (thetgt.canWrite()) {
			rgt += "w";
		} else {
			rgt += "-";
		}

		if (thetgt.canExecute()) {
			rgt += "x";
		} else {
			rgt += "-";
		}

		rgt += "------";

		return rgt;
	}

	/**
	 * @param thetgt
	 * @return
	 */
	private ArrayList<String> makeDirLs(File thetgt) {

		ArrayList<String> ret = new ArrayList<String>();

		try {
			for (File s : thetgt.listFiles()) {
				ret.addAll(makeNotDirLs(s, true));
			}
		} catch (Exception e) {

			logsem.acquireUninterruptibly();
			log.addMiscMsg(null, "Can't list dir '" + thetgt.getAbsolutePath()
					+ "': " + e.getLocalizedMessage(), Lvl.NOTICE);
			logsem.release();

			write.print("550 Cat list dir @ '" + thetgt.getAbsolutePath()
					+ "': " + e.getMessage() + "\r\n");
			write.flush();

			return null;
		}

		return ret;

	}

	/**
	 * @param readLine
	 */
	private void doCdupCmd(String readLine) {
		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'CDUP' command", Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		if (!chechAreCmdArgsCntOk(readLine, 0)) {
			return;
		}

		if (wdir == null) {
			wdir = new File(accnt.getHomeDir().getAbsolutePath());
		}

		File tmp = wdir.getParentFile();
		if (tmp == null) {
			changeWDir(wdir);
		} else {
			changeWDir(tmp);
		}

	}

	/**
	 * @param readLine
	 */
	private void doCwdCmd(String readLine) {

		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'CWD' command: " + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		if (!chechAreCmdArgsCntOk(readLine, 1)) {
			return;
		}

		if (wdir == null) {
			wdir = new File(accnt.getHomeDir().getAbsolutePath());
		}

		String[] cmd = readLine.split(" ", 2);

		File ttmp = new File(cmd[1]);
		File tmp;

		if (cmd[1].equalsIgnoreCase("..")) {
			tmp = wdir.getParentFile();
			tmp = tmp == null ? wdir.getAbsoluteFile() : tmp;
		} else if (cmd[1].equalsIgnoreCase(".")) {
			tmp = wdir.getAbsoluteFile();
		} else if (cmd[1].endsWith("/.") || cmd[1].endsWith("\\.")) {
			tmp = new File(cmd[1].substring(0, cmd[1].length() - 1));
			if (!ttmp.isAbsolute()) {
				tmp = new File(wdir, tmp.getPath());
			}
		} else if (ttmp.isAbsolute()) {
			tmp = ttmp;
		} else {
			tmp = new File(wdir, cmd[1]);
		}

		changeWDir(tmp);

	}

	/**
	 * @param tmp
	 */
	private void changeWDir(File tmp) {
		if (!tmp.exists()) {
			write.print("550 Can't go to non-existent place, sorx.\r\n");
		} else if (!tmp.isDirectory()) {
			write.print("550 Don't know how to enter - it's not a dir.\r\n");
		} else if (!(tmp.canRead() && tmp.canExecute())) {
			write.print("550 I'm not allowed to go there.\r\n");
		} else {

			logsem.acquireUninterruptibly();
			log.addCtlMsg(csock, "Changed WD: " + tmp.getAbsolutePath(),
					Lvl.NORMAL);
			logsem.release();

			write.print("250 Yay! I'm now at: \"" + tmp.getAbsolutePath()
					+ "\"! I kinda like it here.\r\n");
			wdir = tmp;
		}

		write.flush();
	}

	/**
	 * @param readLine
	 * @param i
	 * @return
	 */
	private boolean chechAreCmdArgsCntOk(String readLine, Integer i) {
		Integer len = readLine.split(" ", i + 1).length;
		Boolean res = (len == (i + 1));

		if (!res) {

			logsem.acquireUninterruptibly();
			log.addCtlMsg(csock, "Got invalid args in command: " + readLine
					+ String.format(" (%s ≠ %s)", len, i + 1), Lvl.NORMAL);
			logsem.release();

			write.print(String.format(
					"501 EPIC FAIL in arguments (%s =/= %s)\r\n", len, i + 1));
			write.flush();
		}

		return res;
	}

	/**
	 * @param readLine
	 */
	private void doAcctCmd(String readLine) {

		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'ACCT' command: " + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
		} else {
			notImplementedMsg(readLine);
		}
	}

	/**
	 * @param readLine
	 */
	private void notLoggedInErrMsg(String readLine) {
		if (accnt == null) {
			write
					.print("530 I don't talk with strangers. Tell me who you are first!\r\n");
			write.flush();
		}
	}

	/**
	 * 
	 */
	private void notImplementedMsg(String rl) {

		String[] cmd = rl.split(" ", 2);
		String c;

		if (cmd.length != 2) {
			c = cmd[0];
		} else {
			c = rl;
		}

		write.print("502 I head about '" + c
				+ "' but I don't know how to do that (not implemented)\r\n");
		write.flush();

	}

	/**
	 * @param readLine
	 */
	private void doQuitCmd(String readLine) {

		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got QUIT; quitting", Lvl.NORMAL);
		logsem.release();

		write.print("221 KTHXBYE! (xfrd " + st_transf + "B in " + st_conns
				+ " data conns)\r\n");
		write.flush();

		killIt();
	}

	/**
	 * @param readLine
	 */
	private void doNoopCmd(String readLine) {
		write.print("200 Yay, you're not dead! I'm good too, BTW.\r\n");
		write.flush();

		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got NOOP command: " + readLine, Lvl.NORMAL);
		logsem.release();

	}

	/**
	 * @param readLine
	 */
	private void doPassCmd(String readLine) {

		if (uname == null) {
			logsem.acquireUninterruptibly();
			log.addCtlMsg(csock, "Got PASS before USER : " + uname, Lvl.NOTICE);
			logsem.release();

			write.print("503 PASS? But I don't know who you are yet!\r\n");
			write.flush();

			return;
		}

		if (!chechAreCmdArgsCntOk(readLine, 1)) {
			logsem.acquireUninterruptibly();
			log
					.addCtlMsg(csock, "Malformed PASS line: " + readLine,
							Lvl.NORMAL);
			logsem.release();

			return;
		}

		String[] cmd = readLine.split(" ", 2);

		if (uname != null) {
			if (accnt != null) {
				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock, "Superflous PASS: " + cmd[1], Lvl.NORMAL);
				logsem.release();

				write
						.print("230 I already know who you are - no need to prove it\r\n");
				write.flush();

				pass = cmd[1];
			} else {

				Account acc = smngr.getAccountsSett().getUserAccount(uname);

				if (acc.getPass().compareTo(cmd[1]) == 0) {
					accnt = acc;
					wdir = accnt.getHomeDir().getAbsoluteFile();

					logsem.acquireUninterruptibly();
					log.addCtlMsg(csock, "User '" + accnt.getUserName()
							+ "' logged in.", Lvl.NOTICE);
					logsem.release();

					write.print("230 O HAI, " + cmd[1] + "!");
					write.flush();

				} else {
					logsem.acquireUninterruptibly();
					log.addCtlMsg(csock, "Invalid PASS for user '" + uname
							+ "': " + cmd[1], Lvl.WARNING);
					logsem.release();

					Integer sleep = 5000;
					if (smngr.getServerSett().hasProperty("FailLoginDelayMs")) {
						String del = smngr.getServerSett().getProperty(
								"FailLoginDelayMs").trim();
						try {
							sleep = Integer.parseInt(del);
						} catch (Exception e) {
							logsem.acquireUninterruptibly();
							log.addMiscMsg(null,
									"Invalid setting „FailLoginDelayMs”: "
											+ e.getLocalizedMessage(),
									Lvl.ERROR);
							logsem.release();
						}

					}

					try {
						Thread.sleep(sleep);
					} catch (InterruptedException e) {
					}

					write.print("530 You've tried to fool me! You're not "
							+ uname + "! (invalid pass)\r\n");
					write.flush();

					return;
				}
			}
		}

	}

	/**
	 * @param readLine
	 */
	private void doUserCmd(String readLine) {
		if (accnt != null) {
			logsem.acquireUninterruptibly();
			log.addCtlMsg(csock, "Got USER when alread logged in: " + readLine,
					Lvl.NORMAL);
			logsem.release();

			write.print("530 I know you already! You won't fool me '"
					+ accnt.getUserName() + "'!\r\n");
			write.flush();

		} else {

			String[] cmd = readLine.split(" ", 2);

			if (!chechAreCmdArgsCntOk(readLine, 1)) {
				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock, "Malformed line: " + readLine, Lvl.NORMAL);
				logsem.release();

				return;
			}

			if (!smngr.getAccountsSett().hasAccount(cmd[1])) {
				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock, "Unknown user: " + cmd[1], Lvl.NOTICE);
				logsem.release();

				write
						.print("530 I don't talk with stangers! I don't know you!\r\n");
				write.flush();

				return;
				// killIt();
			}

			if ((smngr.getAccountsSett().getUserAccount(cmd[1]).getModifier() & Account.Mods.ACTIVE
					.getMod()) == 0) {
				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock, "Disabled user account: " + cmd[1],
						Lvl.NOTICE);
				logsem.release();

				write
						.print("530 I know you, but I dont like you. (account disabled)\r\n");
				write.flush();

				return;

			}

			if ((smngr.getAccountsSett().getUserAccount(cmd[1]).getModifier() & Account.Mods.PASSREQ
					.getMod()) != 0) {

				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock, "Got USER: " + readLine, Lvl.NORMAL);
				logsem.release();

				write.print("331 Is it really you, " + cmd[1]
						+ "? Prove it!\r\n");
				write.flush();

			} else {

				accnt = smngr.getAccountsSett().getUserAccount(cmd[1]);

				logsem.acquireUninterruptibly();
				log.addCtlMsg(csock, "User '" + accnt.getUserName()
						+ "' logged in.", Lvl.NOTICE);
				logsem.release();

				write.print("230 O HAI, " + accnt.getUserName() + "!\r\n");
				write.flush();

			}

			uname = cmd[1];

		}

	}

	public synchronized void killIt() {
		kill = true;
	}

	private void printHelloMsg() {

		String hm;
		if (smngr.getServerSett().hasProperty("HelloMsg")) {
			hm = ": " + smngr.getServerSett().getProperty("HelloMsg");
		} else {
			hm = "";
		}

		write.print(String.format("220-%s ver. %s @ %s%s\r\n", smngr
				.getServerSett().getProperty("SERVERNAME"), smngr
				.getServerSett().getProperty("SERVERVERSION"), csock
				.getLocalAddress().getHostName(), hm));

		// -----
		if (smngr.getServerSett().hasProperty("HelloFile")) {
			File hf = new File(smngr.getServerSett().getProperty("HelloFile"));

			if (!hf.isAbsolute()) {
				hf = new File(smngr.getTheDir().getAbsolutePath()
						+ File.separatorChar + hf.getPath());
			}

			try {
				if (hf.exists() && hf.canRead()) {
					BufferedReader br = new BufferedReader(new FileReader(hf));
					String ln;

					while ((ln = br.readLine()) != null) {
						write.print("220-" + ln + "\r\n");
					}

				} else {
					logsem.acquireUninterruptibly();
					log.addMiscMsg(null, "No access to hello file: "
							+ hf.getAbsolutePath(), Lvl.ERROR);
					logsem.release();
				}

			} catch (IOException e) {
				logsem.acquireUninterruptibly();
				log.addMiscMsg(null, "Error reading hello file ("
						+ hf.getAbsolutePath() + "): "
						+ e.getLocalizedMessage(), Lvl.ERROR);
				logsem.release();
			}

		}

		write.print("220 Please login NAO!\r\n");
		write.flush();

	}
}
