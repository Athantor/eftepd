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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author athantor
 * 
 */
public class ClientManager extends Thread {

	private volatile CopyOnWriteArrayList<Thread> clients;
	private volatile LinkedBlockingQueue<Thread> newcts;
	private volatile Boolean exit = false;

	/**
	 * @param exit
	 *            the exit to set
	 */
	public synchronized void setExit(Boolean exit) {
		this.exit = exit;
	}

	/**
	 * 
	 */
	public ClientManager() {
		clients = new CopyOnWriteArrayList<Thread>();
		newcts = new LinkedBlockingQueue<Thread>();
		exit = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		while (!exit) {

			removeDead();
			addNew();

			try {
				sleep(1000);
			} catch (InterruptedException e) {
			}
		}

	}

	/**
	 * 
	 */
	private synchronized void addNew() {
		while (!newcts.isEmpty()) {
			Thread t = newcts.poll();
			t.start();
			clients.add(t);
		}

	}

	/**
	 * 
	 */
	private synchronized void removeDead() {

		CopyOnWriteArrayList<Thread> old = new CopyOnWriteArrayList<Thread>();

		for (Thread t : clients) {
			if (!t.isAlive()) {
				old.add(t);
			}
		}

		if (!old.isEmpty()) {
			clients.removeAll(old);
		}

	}

	public synchronized Boolean addClient(Thread t) {
		try {
			newcts.put(t);
			return true;
		} catch (InterruptedException e) {
			return false;
		}
	}

	public synchronized Boolean addClient(Runnable t) {
		return addClient(new Thread(t));
	}

	public synchronized Integer getClientsCount() {
		return clients.size() + newcts.size();
	}
}
