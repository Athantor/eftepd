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

package server;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import settings.SettingsManager;

public final class eftepd {

	/**
	 * @param args
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException,
			ParserConfigurationException, SAXException {
		if (args.length != 1) {
			System.err.println("Niepoprawna ilosc argumentow: " + args.length
					+ "!");
			System.exit(1);
		} else {
			SettingsManager.setSettingsDir(new File(args[0]));
			SettingsManager sm = SettingsManager.getInstance();

			Server s = new Server(sm);
			s.dajesz();
		}
	}

}
