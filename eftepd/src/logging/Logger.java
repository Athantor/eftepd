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
package logging;

import java.net.Socket;

/**
 * @author athantor
 * 
 */
public interface Logger {
	public enum Lvl {
		NORMAL, NOTICE, WARNING, ERROR, CRITICAL
	}

	/**
	 * Adds info about connection to server
	 * 
	 * @param csock
	 *            client socket
	 * @param msg
	 *            message
	 * @param l
	 *            Message level
	 */
	public void addConnectionMsg(Socket csock, String msg, Lvl l);

	/**
	 * Adds info about control connection
	 * 
	 * @param csock
	 *            client socket
	 * @param msg
	 *            message
	 * @param l
	 *            Message level
	 */
	public void addCtlMsg(Socket csock, String msg, Lvl l);

	/**
	 * Adds info about transfers
	 * 
	 * @param csock
	 *            client socket
	 * @param msg
	 *            message
	 * @param l
	 *            Message level
	 */
	public void addXfrMsg(Socket csock, String msg, Lvl l);

	/**
	 * Adds misc msg
	 * 
	 * @param csock
	 *            client socket
	 * @param msg
	 *            message
	 * @param l
	 *            Message level
	 */
	public void addMiscMsg(Socket csock, String msg, Lvl l);

	/**
	 * Saves the log
	 */
	public void saveLog();

}
