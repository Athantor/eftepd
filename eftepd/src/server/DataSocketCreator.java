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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Semaphore;

import logging.Logger;
import logging.Logger.Lvl;
import settings.SettingsManager;

/**
 * @author athantor
 * 
 */
public final class DataSocketCreator extends Thread {

	private ServerSocket ss;
	private volatile Socket ds = null;
	private volatile SettingsManager smngr;
	private volatile Status st;
	private Exception exc;
	private volatile Logger log;

	private Semaphore logsem;

	public enum Status {
		NOTSTARTED, PREPD, WAITNIG, FINISHED, ERROR
	}

	/**
	 * 
	 */
	public DataSocketCreator(SettingsManager sm, Logger l) {
		smngr = sm;
		log = l;

		logsem = new Semaphore(1, true);

		st = Status.NOTSTARTED;
		try {
			ss = new ServerSocket();
		} catch (IOException e) {
			st = Status.ERROR;
			exc = e;
			return;
		}
	}

	public InetSocketAddress prepare() throws Exception {
		try {
			if (smngr.getServerSett().hasProperty("BindAddress")) {
				String addr = smngr.getServerSett().getProperty("BindAddress");

				ss.bind(new InetSocketAddress(addr, 0));

			} else {
				ss.bind(new InetSocketAddress(0));
			}

			ss.setSoTimeout(0);
		} catch (Exception e) {
			st = Status.ERROR;
			throw e;
		}

		st = Status.PREPD;
		return (InetSocketAddress) ss.getLocalSocketAddress();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		st = Status.WAITNIG;

		try {
			ds = ss.accept();

			st = Status.FINISHED;

			logsem.acquireUninterruptibly();
			log.addConnectionMsg(ds, "data conn", Lvl.NORMAL);
			logsem.release();

			return;
		} catch (IOException e) {
			st = Status.ERROR;
			exc = e;
			return;
		}
	}

	/**
	 * @return the ds
	 */
	public synchronized Socket getDataSocket() {
		return ds;
	}

	/**
	 * @return the st
	 */
	public synchronized Status getStatus() {
		return st;
	}

	/**
	 * @return the e
	 */
	public synchronized Exception getExc() {
		return exc;
	}

}
