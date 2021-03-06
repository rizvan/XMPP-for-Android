/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.harmony.javax.security.auth.kerberos;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;

import org.apache.harmony.auth.internal.kerberos.v5.EncryptionKey;
import org.apache.harmony.auth.internal.nls.Messages;
import org.apache.harmony.security.utils.Array;

/**
 * This class encapsulates a Kerberos encryption key.
 * 
 */
class KeyImpl implements SecretKey, Destroyable, Serializable {

	private static final long serialVersionUID = -7889313790214321193L;

	private transient byte[] keyBytes;

	private transient int keyType;

	// indicates the ticket state
	private transient boolean destroyed;

	// Pre-calculated parity values
	// TODO the alternative for boolean table - any acceptable algorithm?
	private final static boolean[] PARITY = new boolean[] { false, true, true,
			false, true, false, false, true, true, false, false, true, false,
			true, true, false, true, false, false, true, false, true, true,
			false, false, true, true, false, true, false, false, true, true,
			false, false, true, false, true, true, false, false, true, true,
			false, true, false, false, true, false, true, true, false, true,
			false, false, true, true, false, false, true, false, true, true,
			false, true, false, false, true, false, true, true, false, false,
			true, true, false, true, false, false, true, false, true, true,
			false, true, false, false, true, true, false, false, true, false,
			true, true, false, false, true, true, false, true, false, false,
			true, true, false, false, true, false, true, true, false, true,
			false, false, true, false, true, true, false, false, true, true,
			false, true, false, false, true, true, false, false, true, false,
			true, true, false, false, true, true, false, true, false, false,
			true, false, true, true, false, true, false, false, true, true,
			false, false, true, false, true, true, false, false, true, true,
			false, true, false, false, true, true, false, false, true, false,
			true, true, false, true, false, false, true, false, true, true,
			false, false, true, true, false, true, false, false, true, false,
			true, true, false, true, false, false, true, true, false, false,
			true, false, true, true, false, true, false, false, true, false,
			true, true, false, false, true, true, false, true, false, false,
			true, true, false, false, true, false, true, true, false, false,
			true, true, false, true, false, false, true, false, true, true,
			false, true, false, false, true, true, false, false, true, false,
			true, true, false };

	// Pre-calculated reversed values
	// TODO any acceptable alternative algorithm instead of table?
	private static final byte[] REVERSE = new byte[] { 0, 64, 32, 96, 16, 80,
			48, 112, 8, 72, 40, 104, 24, 88, 56, 120, 4, 68, 36, 100, 20, 84,
			52, 116, 12, 76, 44, 108, 28, 92, 60, 124, 2, 66, 34, 98, 18, 82,
			50, 114, 10, 74, 42, 106, 26, 90, 58, 122, 6, 70, 38, 102, 22, 86,
			54, 118, 14, 78, 46, 110, 30, 94, 62, 126, 1, 65, 33, 97, 17, 81,
			49, 113, 9, 73, 41, 105, 25, 89, 57, 121, 5, 69, 37, 101, 21, 85,
			53, 117, 13, 77, 45, 109, 29, 93, 61, 125, 3, 67, 35, 99, 19, 83,
			51, 115, 11, 75, 43, 107, 27, 91, 59, 123, 7, 71, 39, 103, 23, 87,
			55, 119, 15, 79, 47, 111, 31, 95, 63, 127 };

	/**
	 * creates a secret key from a given raw bytes
	 * 
	 * @param keyBytes
	 * @param keyType
	 */
	public KeyImpl(byte[] keyBytes, int keyType) {
		this.keyBytes = new byte[keyBytes.length];
		System.arraycopy(keyBytes, 0, this.keyBytes, 0, this.keyBytes.length);
		this.keyType = keyType;
	}

	/**
	 * creates a secret key from a given password
	 * 
	 * @param principal
	 * @param password
	 * @param algorithm
	 */
	public KeyImpl(KerberosPrincipal principal, char[] password,
			String algorithm) {

		//
		// See http://www.ietf.org/rfc/rfc3961.txt for algorithm description
		//

		if (principal == null || password == null) {
			throw new NullPointerException();
		}

		if (algorithm != null && "DES".compareTo(algorithm) != 0) { //$NON-NLS-1$
			throw new IllegalArgumentException(Messages.getString("auth.49")); //$NON-NLS-1$
		}

		keyType = 3; // DES algorithm
		keyBytes = new byte[8];

		final String realm = principal.getRealm();
		final String pname = principal.getName();

		final StringBuilder buf = new StringBuilder();
		buf.append(password);
		buf.append(realm);
		buf.append(pname.substring(0, pname.length() - realm.length() - 1));

		final byte[] tmp = org.apache.harmony.luni.util.Util.getUTF8Bytes(buf
				.toString());

		// pad with 0x00 to 8 byte boundary
		final byte[] raw = new byte[tmp.length
				+ ((tmp.length % 8) == 0 ? 0 : (8 - tmp.length % 8))];
		System.arraycopy(tmp, 0, raw, 0, tmp.length);

		long k1, k2 = 0;
		boolean isOdd = false;
		// for each 8-byte block in raw byte array
		for (int i = 0; i < raw.length; i = i + 8, isOdd = !isOdd) {

			k1 = 0;
			if (isOdd) {
				// reverse
				for (int j = 7; j > -1; j--) {
					k1 = (k1 << 7) + REVERSE[raw[i + j] & 0x7F];
				}
			} else {
				for (int j = 0; j < 8; j++) {
					k1 = (k1 << 7) + (raw[i + j] & 0x7F);
				}
			}
			k2 = k2 ^ k1;
		}

		// 56-bit long to byte array (8 bytes)
		for (int i = 7; i > -1; i--) {
			keyBytes[i] = (byte) k2;
			keyBytes[i] = (byte) (keyBytes[i] << 1);
			k2 = k2 >> 7;
		}
		keyCorrection(keyBytes);

		// calculate DES-CBC check sum
		try {
			final Cipher cipher = Cipher.getInstance("DES/CBC/NoPadding"); //$NON-NLS-1$

			// use tmp key as IV
			final IvParameterSpec IV = new IvParameterSpec(keyBytes);

			// do DES encryption
			final SecretKey secretKey = new SecretKeySpec(keyBytes, "DES"); //$NON-NLS-1$
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, IV);
			final byte[] enc = cipher.doFinal(raw);

			// final last block is check sum
			System.arraycopy(enc, enc.length - 8, keyBytes, 0, 8);

			keyCorrection(keyBytes);

		} catch (final Exception e) {
			throw new RuntimeException(Messages.getString("auth.4A"), e); //$NON-NLS-1$
		}
	}

	/**
	 * if a key is destroyed then IllegalStateException should be thrown
	 */
	private void checkState() {
		if (destroyed) {
			throw new IllegalStateException(Messages.getString("auth.48")); //$NON-NLS-1$
		}
	}

	/**
	 * Destroys this key
	 */
	@Override
	public void destroy() throws DestroyFailedException {
		if (!destroyed) {
			Arrays.fill(keyBytes, (byte) 0);
			destroyed = true;
		}

	}

	@Override
	public boolean equals(Object other) {
		if (isDestroyed()) {
			return false;
		}
		if (this == other) {
			return true;
		}

		if (other instanceof KeyImpl) {
			final KeyImpl that = (KeyImpl) other;
			if (that.isDestroyed()) {
				return false;
			}
			if ((keyType == that.keyType)
					&& (keyBytes.length == that.keyBytes.length)) {
				for (int i = 0; i < keyBytes.length; i++) {
					if (keyBytes[i] != that.keyBytes[i]) {
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Method is described in <code>getAlgorithm</code> in interface
	 * <code>Key</code>
	 */
	@Override
	public final String getAlgorithm() {
		checkState();
		if (keyType == 0) {
			return "NULL"; //$NON-NLS-1$
		}
		return "DES"; //$NON-NLS-1$
	}

	/**
	 * Method is described in <code>getEncoded</code> in interface
	 * <code>Key</code>
	 */
	@Override
	public final byte[] getEncoded() {
		checkState();
		final byte[] tmp = new byte[keyBytes.length];
		System.arraycopy(keyBytes, 0, tmp, 0, tmp.length);
		return tmp;
	}

	/**
	 * Method is described in <code>getFormat</code> in interface
	 * <code>Key</code>
	 */
	@Override
	public final String getFormat() {
		checkState();
		return "RAW"; //$NON-NLS-1$
	}

	/**
	 * Returns the key type for this key
	 */
	public final int getKeyType() {
		checkState();
		return keyType;
	}

	@Override
	public int hashCode() {
		int hashcode = 0;
		for (final byte keyByte : keyBytes) {
			hashcode += keyByte;
		}
		hashcode *= keyType;
		return hashcode;
	}

	/**
	 * Determines if this key has been destroyed
	 */
	@Override
	public boolean isDestroyed() {
		return destroyed;
	}

	private void keyCorrection(byte[] key) {

		// fix parity
		for (int i = 0; i < 8; i++) {
			if (!PARITY[key[i] & 0xFF]) {
				if ((key[i] & 0x01) == 0) {
					key[i]++;
				} else {
					key[i]--;
				}
			}
		}

		// TODO if is week do XOR
		// if(DESKeySpec.isWeak(keyBytes,0)){
		// }
	}

	private void readObject(ObjectInputStream s) throws IOException,
			ClassNotFoundException {

		s.defaultReadObject();

		final EncryptionKey ekey = (EncryptionKey) EncryptionKey.ASN1
				.decode((byte[]) s.readObject());

		keyType = ekey.getType();
		keyBytes = ekey.getValue();
	}

	/**
	 * A string representation of this key
	 */
	@Override
	public String toString() {
		String s_key = null;
		final StringBuilder sb = new StringBuilder();

		if (keyBytes.length == 0) {
			s_key = "Empty Key"; //$NON-NLS-1$
		} else {
			s_key = Array.toString(keyBytes, " "); //$NON-NLS-1$
		}
		sb.append("EncryptionKey: ").append("KeyType = ").append(keyType); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("KeyBytes (Hex dump) = ").append(s_key); //$NON-NLS-1$
		return sb.toString();
	}

	private void writeObject(ObjectOutputStream s) throws IOException {

		if (destroyed) {
			throw new IOException(Messages.getString("auth.48")); //$NON-NLS-1$
		}
		s.defaultWriteObject();

		final byte[] enc = EncryptionKey.ASN1.encode(new EncryptionKey(keyType,
				keyBytes));
		s.writeObject(enc);
	}
}
