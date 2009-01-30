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

/**
 * @author athantor
 * 
 */
public final class Account {

	private String uname, pass;
	private File hdir;
	private Long quota;
	/**
	 * modifier bits;
	 * 
	 * 00000000 ← start;
	 * 
	 * disabled; anon; pass required;
	 * 
	 */
	private Integer modifier;

	public static enum Mods {
		ACTIVE(0x1), ANONYMOUS(0x2), PASSREQ(0x4);

		private Integer mod;

		Mods(Integer i) {
			this.mod = i;
		}

		public Integer getMod() {
			return mod;
		}
	}

	/**
	 * @param uname
	 *            user name
	 * @param pass
	 *            password
	 * @param hdir
	 *            home directory
	 * @param modifier
	 *            account modifier
	 */
	public Account(String uname, String pass, File hdir, Integer modifier,
			Long q) {

		if (hdir == null || !hdir.exists() || !hdir.isDirectory()) {
			throw new IllegalArgumentException("Invalid home directory");
		}

		if (uname == null || pass == null || modifier == null) {
			throw new IllegalArgumentException("Arguments can't be null");
		}

		if (q < -1) {
			throw new IllegalArgumentException("Invalid quota size: " + q);
		}

		this.uname = uname;
		this.pass = pass;
		this.hdir = hdir;
		this.modifier = modifier;
		this.quota = q;
	}

	/**
	 * @return the user name
	 */
	public String getUserName() {
		return uname;
	}

	/**
	 * @return the password
	 */
	public String getPass() {
		return pass;
	}

	/**
	 * @return the home directory
	 */
	public File getHomeDir() {
		return hdir;
	}

	/**
	 * @return the account modifier
	 */
	public Integer getModifier() {
		return modifier;
	}

	/**
	 * @return the quota
	 */
	public Long getQuota() {
		return quota;
	}

}
