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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.TreeMap;

/**
 * @author athantor
 * 
 */
public final class ServerSettings {
	private volatile static TreeMap<String, String> sett = null;
	private volatile static File settfile = null;
	private volatile static ServerSettings instance = null;

	private ServerSettings() {
		if (settfile == null) {
			throw new IllegalStateException(
					"Can't create instance b'coz settings file is not set");
		}

		sett = new TreeMap<String, String>();

	}

	public static void setFile(File f) {
		if (instance != null) {
			throw new IllegalStateException(
					"Can't change file after instantiation");
		}

		if (f == null || !f.exists() || !f.canRead()) {
			throw new IllegalArgumentException("Invalid file or no access: "
					+ f);
		}

		settfile = new File(f.getAbsolutePath());
	}

	public static ServerSettings getInstance() throws IOException {

		if (instance == null) {
			instance = new ServerSettings();
		}

		readSettings();
		return instance;
	}

	private static void readSettings() throws IOException {
		if (!settfile.canRead()) {
			throw new IOException("Can't read history file");
		}

		sett.clear();

		BufferedReader br = new BufferedReader(new FileReader(settfile));
		String line = null;

		while ((line = br.readLine()) != null) {
			if (!line.trim().startsWith("#")) {

				String[] s = line.split("=", 2);
				if (s.length == 1) {
					System.err.println("**> Invalid setting: " + line);
					continue;
				}

				if (!s[0].equalsIgnoreCase("SERVERNAME")
						|| !s[0].equalsIgnoreCase("SERVERVERSION")) {
					sett.put(s[0], s[1]);
				}
			}
		}

		br.close();

		sett.put("SERVERVERSION", "0.9");
		sett.put("SERVERNAME", "eftepd");
	}

	public void reload() throws IOException {
		readSettings();
	}

	public Boolean hasProperty(String key) {
		return sett.containsKey(key);
	}

	public String getProperty(String key) {
		return sett.get(key);
	}
}
