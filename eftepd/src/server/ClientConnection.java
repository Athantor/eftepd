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
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Semaphore;

import logging.Logger;
import logging.Logger.Lvl;
import settings.Account;
import settings.SettingsManager;

/**
 * @author athantor
 * 
 */
public final class ClientConnection implements Runnable {

	private Socket csock, dsock = null;
	private Logger log;
	private Boolean kill = false;
	private BufferedReader read;
	private PrintWriter write;
	private SettingsManager smngr;
	private File wdir = null;

	private String uname = null, pass = null;
	private Account accnt = null;

	private Integer idlemstime = 900000;

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

		}

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

	/**
	 * @param readLine
	 */
	private void doCdupCmd(String readLine) {
		logsem.acquireUninterruptibly();
		log.addCtlMsg(csock, "Got 'CDUP' command: " + readLine, Lvl.NORMAL);
		logsem.release();

		if (accnt == null) {
			notLoggedInErrMsg(readLine);
			return;
		}

		if (!chechIsCmdOk(readLine, 1)) {
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

		if (!chechIsCmdOk(readLine, 2)) {
			return;
		}

		if (wdir == null) {
			wdir = new File(accnt.getHomeDir().getAbsolutePath());
		}

		String[] cmd = readLine.split(" ", 2);

		File ttmp = new File(cmd[1]);
		File tmp;

		if (ttmp.isAbsolute()) {
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
	private boolean chechIsCmdOk(String readLine, Integer i) {
		Integer len = readLine.split(" ", i).length;
		Boolean res = (len >= i);

		if (!res) {

			logsem.acquireUninterruptibly();
			log.addCtlMsg(csock, "Got invalid args in command: " + readLine
					+ String.format("(%s < %s)", len - 1, i - 1), Lvl.NORMAL);
			logsem.release();

			write
					.print(String.format(
							"501 EPIC FAIL in arguments (%s < %s)\r\n", i - 1,
							len - 1));
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

		write.print("221 KTHXBYE!\r\n");
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

		if (!chechIsCmdOk(readLine, 2)) {
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

					write.print("421 You've tried to fool me! You're not "
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

			if (!chechIsCmdOk(readLine, 2)) {
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
						.print("421 I don't talk with stangers! I don't know you!\r\n");
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
						.print("421 I know you, but I dont like you. (account disabled)\r\n");
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
