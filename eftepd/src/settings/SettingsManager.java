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
package settings;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

/**
 * @author athantor
 * 
 */
public class SettingsManager {

	private static SettingsManager instance = null;
	private static AccoutSettings accsett;
	private static ServerSettings srvsett;
	private static File settdir = null;

	private SettingsManager() throws IOException, ParserConfigurationException,
			SAXException {
		if (settdir == null) {
			throw new IllegalStateException(
					"Can't create instance b'coz settings dir is not set");
		}

		accsett = AccoutSettings.getInstance();
		srvsett = ServerSettings.getInstance();

	}

	public static void setSettingsDir(File f) {
		if (instance != null) {
			throw new IllegalStateException(
					"Can't change file after instantiation");
		}

		if (f == null || !f.exists() || !f.isDirectory()
				|| !(f.canRead() && f.canExecute())) {
			throw new IllegalArgumentException("Invalid dir");
		}

		AccoutSettings.setFile(new File(f.getAbsolutePath()
				+ File.separatorChar + "accounts.xml"));
		ServerSettings.setFile(new File(f.getAbsolutePath()
				+ File.separatorChar + "eftepd.cfg"));

		settdir = new File(f.getAbsolutePath());
	}

	public static SettingsManager getInstance() throws IOException,
			ParserConfigurationException, SAXException {
		if (instance == null) {
			instance = new SettingsManager();
		}

		return instance;
	}

	/**
	 * @return the accsett
	 */
	public AccoutSettings getAccountsSett() {
		return accsett;
	}

	/**
	 * @return the srvsett
	 */
	public ServerSettings getServerSett() {
		return srvsett;
	}

	public File getTheDir() {
		return new File(settdir.getAbsolutePath());
	}

}
