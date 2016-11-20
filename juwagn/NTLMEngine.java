/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package juwagn;

import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class NTLMEngine {

    // Flags we use; descriptions according to:
    // http://davenport.sourceforge.net/ntlm.html
    // and
    // http://msdn.microsoft.com/en-us/library/cc236650%28v=prot.20%29.aspx
    protected static final int FLAG_REQUEST_UNICODE_ENCODING = 0x00000001;      // Unicode string encoding requested
    protected static final int FLAG_REQUEST_TARGET = 0x00000004;                      // Requests target field
    protected static final int FLAG_REQUEST_SIGN = 0x00000010;  // Requests all messages have a signature attached, in NEGOTIATE message.
    protected static final int FLAG_REQUEST_SEAL = 0x00000020;  // Request key exchange for message confidentiality in NEGOTIATE message.  MUST be used in conjunction with 56BIT.
    protected static final int FLAG_REQUEST_LAN_MANAGER_KEY = 0x00000080;    // Request Lan Manager key instead of user session key
    protected static final int FLAG_REQUEST_NTLMv1 = 0x00000200; // Request NTLMv1 security.  MUST be set in NEGOTIATE and CHALLENGE both
    protected static final int FLAG_DOMAIN_PRESENT = 0x00001000;        // Domain is present in message
    protected static final int FLAG_WORKSTATION_PRESENT = 0x00002000;   // Workstation is present in message
    protected static final int FLAG_REQUEST_ALWAYS_SIGN = 0x00008000;   // Requests a signature block on all messages.  Overridden by REQUEST_SIGN and REQUEST_SEAL.
    protected static final int FLAG_REQUEST_NTLM2_SESSION = 0x00080000; // From server in challenge, requesting NTLM2 session security
    protected static final int FLAG_REQUEST_VERSION = 0x02000000;       // Request protocol version
    protected static final int FLAG_TARGETINFO_PRESENT = 0x00800000;    // From server in challenge message, indicating targetinfo is present
    protected static final int FLAG_REQUEST_128BIT_KEY_EXCH = 0x20000000; // Request explicit 128-bit key exchange
    protected static final int FLAG_REQUEST_EXPLICIT_KEY_EXCH = 0x40000000;     // Request explicit key exchange
    protected static final int FLAG_REQUEST_56BIT_ENCRYPTION = 0x80000000;      // Must be used in conjunction with SEAL


    private static String MAIN_HOST;
    private static String UnicodeLittleUnmarked = "UnicodeLittleUnmarked"; //equals "UTF-16LE"

    /** The signature string as bytes in the default encoding */
    private static final byte[] SIGNATURE = { (byte)'N', (byte)'T', (byte)'L', (byte)'M', (byte)'S', (byte)'S', (byte)'P', (byte)0x00 };

    /** Secure random generator */
    private static final java.security.SecureRandom RND_GEN;

    //private static String TYPE_1_MESSAGE = "";

    static {
        java.security.SecureRandom rnd = null;
        try {
            rnd = java.security.SecureRandom.getInstance("SHA1PRNG");
        } catch (Exception e) {
        }
        RND_GEN = rnd;
        try {
            MAIN_HOST = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            MAIN_HOST = "";
        }
        try {
            " ".getBytes(UnicodeLittleUnmarked);
        } catch (Exception e) {
            UnicodeLittleUnmarked = "UTF-16LE";
        }
        /*
        try {
            TYPE_1_MESSAGE = new Type1Message().getResponse();;
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }*/
    }                 

 

    /**
     * Returns the response for the given message.
     *
     * @param message
     *            the message that was received from the server.
     * @param username
     *            the username to authenticate with.
     * @param password
     *            the password to authenticate with.
     * @param host
     *            The host.
     * @param domain
     *            the NT domain to authenticate in.
     * @return The response.
     * @throws
     *             If the messages cannot be retrieved.
     */
    static String getResponseFor(String message, String username, String password, String host, String domain) throws Exception {
        if(host == null) {
          host = MAIN_HOST;
        }

        //System.out.println(">>>>" + message + " user>" + username + " pass>" + password + " host>" + host + " domain>" + domain);
        String response;
        if (message == null || message.trim().equals("")) {
            response = getType1Message(host, domain); //response = new Type1Message(domain, host).getResponse();
        } else {
            Type2Message t2m = new Type2Message(message);
            response = getType3Message(username, password, host, domain, t2m.getChallenge(), t2m.getFlags(), t2m.getTarget(), t2m.getTargetInfo());
        }
        return response;
    }

    /**
     * Creates the first message (type 1 message) in the NTLM authentication
     * sequence. This message includes the user name, domain and host for the
     * authentication session.
     *
     * @param host
     *            the computer name of the host requesting authentication.
     * @param domain
     *            The domain to authenticate with.
     * @return String the message to add to the HTTP request header.
     */
    static String getType1Message(String host, String domain) throws Exception {
        // For compatibility reason do not include domain and host in type 1 message
        //return new Type1Message(domain, host).getResponse();
        //return TYPE_1_MESSAGE;

        try {
           Type1Message TYPE_1_MESSAGE = new Type1Message(domain);
           if(TYPE_1_MESSAGE != null) { 
             return TYPE_1_MESSAGE.getResponse();
           }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return null;
    }

    /**
     * Creates the type 3 message using the given server nonce. The type 3
     * message includes all the information for authentication, host, domain,
     * username and the result of encrypting the nonce sent by the server using
     * the user's password as the key.
     *
     * @param user
     *            The user name. This should not include the domain name.
     * @param password
     *            The password.
     * @param host
     *            The host that is originating the authentication request.
     * @param domain
     *            The domain to authenticate within.
     * @param nonce
     *            the 8 byte array the server sent.
     * @return The type 3 message.
     * @throws Exception
     *             
     */
    static String getType3Message(String user, String password, String host, String domain, byte[] nonce, int type2Flags, String target, byte[] targetInformation) throws Exception {
        return new Type3Message(domain, host, user, password, nonce, type2Flags, target, targetInformation).getResponse();
    }

    /** Strip dot suffix from a name */
    private static String stripDotSuffix(String value) {
        if (value == null) {
            return null;
        }
        int index = value.indexOf(".");
        if (index != -1) {
            return value.substring(0, index);
        }
        return value;
    }

    /** Convert host to standard form */
    private static String convertHost(String host) {
        return stripDotSuffix(host);
    }

    /** Convert domain to standard form */
    private static String convertDomain(String domain) {
        return stripDotSuffix(domain);
    }

    private static int readULong(byte[] src, int index) throws Exception {
        if (src.length < index + 4) {
            throw new Exception("NTLM authentication - buffer too small for DWORD");
        }
        return (src[index] & 0xff) | ((src[index + 1] & 0xff) << 8)
                | ((src[index + 2] & 0xff) << 16) | ((src[index + 3] & 0xff) << 24);
    }

    private static int readUShort(byte[] src, int index) throws Exception {
        if (src.length < index + 2) {
            throw new Exception("NTLM authentication - buffer too small for WORD");
        }
        return (src[index] & 0xff) | ((src[index + 1] & 0xff) << 8);
    }

    private static byte[] readSecurityBuffer(byte[] src, int index) throws Exception {
        int length = readUShort(src, index);
        int offset = readULong(src, index + 4);
        if (src.length < offset + length) {
            throw new Exception(
                    "NTLM authentication - buffer too small for data item");
        }
        byte[] buffer = new byte[length];
        System.arraycopy(src, offset, buffer, 0, length);
        return buffer;
    }

    /** Calculate a challenge block */
    private static byte[] makeRandomChallenge() throws Exception {
        if (RND_GEN == null) {
            throw new Exception("Random generator not available");
        }
        byte[] rval = new byte[8];
        synchronized (RND_GEN) {
            RND_GEN.nextBytes(rval);
        }
        return rval;
    }

    /** Calculate a 16-byte secondary key */
    private static byte[] makeSecondaryKey() throws Exception {
        if (RND_GEN == null) {
            throw new Exception("Random generator not available");
        }
        byte[] rval = new byte[16];
        synchronized (RND_GEN) {
            RND_GEN.nextBytes(rval);
        }
        return rval;
    }

    protected static class CipherGen {
        protected boolean ntlmHashType = false;

        protected String domain;
        protected String user;
        protected String password;
        protected byte[] challenge;
        protected String target;
        protected byte[] targetInformation;

        // Information we can generate but may be passed in (for testing)
        protected byte[] clientChallenge;
        protected byte[] clientChallenge2;
        protected byte[] secondaryKey;
        protected byte[] timestamp;

        // Stuff we always generate
        protected byte[] lmHash = null;
        protected byte[] lmResponse = null;
        protected byte[] ntlmHash = null;
        protected byte[] ntlmResponse = null;
        protected byte[] ntlmv2Hash = null;
        protected byte[] lmv2Hash = null;
        protected byte[] lmv2Response = null;
        protected byte[] ntlmv2Blob = null;
        protected byte[] ntlmv2Response = null;
        protected byte[] ntlm2SessionResponse = null;
        protected byte[] lm2SessionResponse = null;
        protected byte[] lmUserSessionKey = null;
        protected byte[] ntlmUserSessionKey = null;
        protected byte[] ntlmv2UserSessionKey = null;
        protected byte[] ntlm2SessionResponseUserSessionKey = null;
        protected byte[] lanManagerSessionKey = null;

        public CipherGen(String domain, String user, String password,
            byte[] challenge, String target, byte[] targetInformation,
            byte[] clientChallenge, byte[] clientChallenge2,
            byte[] secondaryKey, byte[] timestamp) {
            this.domain = domain;
            this.target = target;
            this.user = user;
            this.password = password;
            this.challenge = challenge;
            this.targetInformation = targetInformation;
            this.clientChallenge = clientChallenge;
            this.clientChallenge2 = clientChallenge2;
            this.secondaryKey = secondaryKey;
            this.timestamp = timestamp;
        }

        public CipherGen(boolean ntlmHashType, String domain, String user, String password,
            byte[] challenge, String target, byte[] targetInformation) {
            this(domain, user, password, challenge, target, targetInformation, null, null, null, null); // CipherGen gen = new CipherGen(unqualifiedDomain, user, password, nonce, target, targetInformation);
            this.ntlmHashType = ntlmHashType;
        }

        /** Calculate and return client challenge */
        public byte[] getClientChallenge()
            throws Exception {
            if (clientChallenge == null) {
                clientChallenge = makeRandomChallenge();
            }
            return clientChallenge;
        }

        /** Calculate and return second client challenge */
        public byte[] getClientChallenge2()
            throws Exception {
            if (clientChallenge2 == null) {
                clientChallenge2 = makeRandomChallenge();
            }
            return clientChallenge2;
        }

        /** Calculate and return random secondary key */
        public byte[] getSecondaryKey()
            throws Exception {
            if (secondaryKey == null) {
                secondaryKey = makeSecondaryKey();
            }
            return secondaryKey;
        }

        /** Calculate and return the LMResponse */
        public byte[] getLMResponse()
            throws Exception {
            if (lmResponse == null) {
                lmResponse = lmResponse(getLMHash(), challenge);
            }
            return lmResponse;
        }

        /** Calculate and return the NTLMResponse */
        public byte[] getNTLMResponse()
            throws Exception {
            if (ntlmResponse == null) {
                ntlmResponse = lmResponse(getNTLMHash(), challenge);
            }
            return ntlmResponse;
        }

        /** Calculate and return the LMHash */
        public byte[] getLMHash()
            throws Exception {
            if (lmHash == null) {
              if(!ntlmHashType) {
                lmHash = lmHash(password);
                System.out.println("LM HASH: " + ServerListener.encodeBase64(lmHash));
              } else { 
                lmHash = NTLMEngine.makeBinaryLogon(password, 1);
                System.out.println("LM HASH passed by user!");
              }
            }
            return lmHash;
        }

        /** Calculate and return the NTLMHash */
        public byte[] getNTLMHash()
            throws Exception {
            if (ntlmHash == null) {
              if(!ntlmHashType) {
                ntlmHash = ntlmHash(password);
                System.out.println("NT HASH: " + ServerListener.encodeBase64(ntlmHash));
              } else { 
                ntlmHash = NTLMEngine.makeBinaryLogon(password, 0);
                System.out.println("NT HASH passed by user!");
              }
            }
            return ntlmHash;
        }

        /** Calculate the LMv2 hash */
        public byte[] getLMv2Hash()
            throws Exception {
            if (lmv2Hash == null) {
                lmv2Hash = lmv2Hash(domain, user, getNTLMHash());
            }
            return lmv2Hash;
        }

        /** Calculate the NTLMv2 hash */
        public byte[] getNTLMv2Hash()
            throws Exception {
            if (ntlmv2Hash == null) {
                ntlmv2Hash = ntlmv2Hash(domain, user, getNTLMHash());
            }
            return ntlmv2Hash;
        }

        /** Calculate a timestamp */
        public byte[] getTimestamp() {
            if (timestamp == null) {
                long time = System.currentTimeMillis();
                time += 11644473600000l; // milliseconds from January 1, 1601 -> epoch.
                time *= 10000; // tenths of a microsecond.
                // convert to little-endian byte array.
                timestamp = new byte[8];
                for (int i = 0; i < 8; i++) {
                    timestamp[i] = (byte) time;
                    time >>>= 8;
                }
            }
            return timestamp;
        }

        /** Calculate the NTLMv2Blob */
        public byte[] getNTLMv2Blob()
            throws Exception {
            if (ntlmv2Blob == null) {
                ntlmv2Blob = createBlob(getClientChallenge2(), targetInformation, getTimestamp());
            }
            return ntlmv2Blob;
        }

        /** Calculate the NTLMv2Response */
        public byte[] getNTLMv2Response()
            throws Exception {
            if (ntlmv2Response == null) {
                ntlmv2Response = lmv2Response(getNTLMv2Hash(), challenge, getNTLMv2Blob());
            }
            return ntlmv2Response;
        }

        /** Calculate the LMv2Response */
        public byte[] getLMv2Response()
            throws Exception {
            if (lmv2Response == null) {
                lmv2Response = lmv2Response(getLMv2Hash(), challenge, getClientChallenge());
            }
            return lmv2Response;
        }

        /** Get NTLM2SessionResponse */
        public byte[] getNTLM2SessionResponse()
            throws Exception {
            if (ntlm2SessionResponse == null) {
                ntlm2SessionResponse = ntlm2SessionResponse(getNTLMHash(), challenge, getClientChallenge());
            }
            return ntlm2SessionResponse;
        }

        /** Calculate and return LM2 session response */
        public byte[] getLM2SessionResponse()
            throws Exception {
            if (lm2SessionResponse == null) {
                byte[] clientChallenge = getClientChallenge();
                lm2SessionResponse = new byte[24];
                System.arraycopy(clientChallenge, 0, lm2SessionResponse, 0, clientChallenge.length);
                Arrays.fill(lm2SessionResponse, clientChallenge.length, lm2SessionResponse.length, (byte) 0x00);
            }
            return lm2SessionResponse;
        }

        /** Get LMUserSessionKey */
        public byte[] getLMUserSessionKey()
            throws Exception {
            if (lmUserSessionKey == null) {
                lmUserSessionKey = new byte[16];
                System.arraycopy(getLMHash(), 0, lmUserSessionKey, 0, 8);
                Arrays.fill(lmUserSessionKey, 8, 16, (byte) 0x00);
            }
            return lmUserSessionKey;
        }

        /** Get NTLMUserSessionKey */
        public byte[] getNTLMUserSessionKey()
            throws Exception {
            if (ntlmUserSessionKey == null) {
                MD4 md4 = new MD4();
                md4.update(getNTLMHash());
                ntlmUserSessionKey = md4.getOutput();
            }
            return ntlmUserSessionKey;
        }

        /** GetNTLMv2UserSessionKey */
        public byte[] getNTLMv2UserSessionKey()
            throws Exception {
            if (ntlmv2UserSessionKey == null) {
                byte[] ntlmv2hash = getNTLMv2Hash();
                byte[] truncatedResponse = new byte[16];
                System.arraycopy(getNTLMv2Response(), 0, truncatedResponse, 0, 16);
                ntlmv2UserSessionKey = hmacMD5(truncatedResponse, ntlmv2hash);
            }
            return ntlmv2UserSessionKey;
        }

        /** Get NTLM2SessionResponseUserSessionKey */
        public byte[] getNTLM2SessionResponseUserSessionKey()
            throws Exception {
            if (ntlm2SessionResponseUserSessionKey == null) {
                byte[] ntlm2SessionResponseNonce = getLM2SessionResponse();
                byte[] sessionNonce = new byte[challenge.length + ntlm2SessionResponseNonce.length];
                System.arraycopy(challenge, 0, sessionNonce, 0, challenge.length);
                System.arraycopy(ntlm2SessionResponseNonce, 0, sessionNonce, challenge.length, ntlm2SessionResponseNonce.length);
                ntlm2SessionResponseUserSessionKey = hmacMD5(sessionNonce, getNTLMUserSessionKey());
            }
            return ntlm2SessionResponseUserSessionKey;
        }

        /** Get LAN Manager session key */
        public byte[] getLanManagerSessionKey()
            throws Exception {
            if (lanManagerSessionKey == null) {
                try {
                    byte[] keyBytes = new byte[14];
                    System.arraycopy(getLMHash(), 0, keyBytes, 0, 8);
                    Arrays.fill(keyBytes, 8, keyBytes.length, (byte)0xbd);
                    Key lowKey = createDESKey(keyBytes, 0);
                    Key highKey = createDESKey(keyBytes, 7);
                    byte[] truncatedResponse = new byte[8];
                    System.arraycopy(getLMResponse(), 0, truncatedResponse, 0, truncatedResponse.length);
                    Cipher des = Cipher.getInstance("DES/ECB/NoPadding");
                    des.init(Cipher.ENCRYPT_MODE, lowKey);
                    byte[] lowPart = des.doFinal(truncatedResponse);
                    des = Cipher.getInstance("DES/ECB/NoPadding");
                    des.init(Cipher.ENCRYPT_MODE, highKey);
                    byte[] highPart = des.doFinal(truncatedResponse);
                    lanManagerSessionKey = new byte[16];
                    System.arraycopy(lowPart, 0, lanManagerSessionKey, 0, lowPart.length);
                    System.arraycopy(highPart, 0, lanManagerSessionKey, lowPart.length, highPart.length);
                } catch (Exception e) {
                    throw new Exception(e.getMessage(), e);
                }
            }
            return lanManagerSessionKey;
        }
    }

    static byte[] makeBinaryLogon(String password, int pos) {
       if(pos == 0) { //NT PASS WISHED, left side
         pos = password.indexOf(":");
         if(pos > -1) {
           String test = password.substring(0, pos);
           if(test.length() < 1) {
             password = password.substring(pos + 1); 
           }
         } //else password is ntlmhash as is
       } else { //LM pass wished, right side
         pos = password.indexOf(":");
         if(pos > -1) {
           String test = password.substring(pos + 1);
           if(test.length() < 1) {
             password = password.substring(0, pos); 
           }
         } //else should not happen, but to avoid errors assume left side
       }
       if(password.length() < 1) { return new byte[0]; }
       return ServerListener.decodeBase64(password);
    }

    /** Calculates HMAC-MD5 */
    static byte[] hmacMD5(byte[] value, byte[] key)
        throws Exception {
        HMACMD5 hmacMD5 = new HMACMD5(key);
        hmacMD5.update(value);
        return hmacMD5.getOutput();
    }

    /** Calculates RC4 */
    static byte[] RC4(byte[] value, byte[] key) throws Exception {
        try {
            Cipher rc4 = Cipher.getInstance("RC4");
            rc4.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "RC4"));
            return rc4.doFinal(value);
        } catch (Exception e) {
            //throw new Exception(e.getMessage(), e);
            try {
              RC4_Impl cipher = new RC4_Impl(); 
              cipher.initEncrypt(key);
              return cipher.crypt(value);
            } catch(Exception sd) { 
              throw new Exception(e.getMessage(), e);
            }
        }
    }

    /**
     * Calculates the NTLM2 Session Response for the given challenge, using the
     * specified password and client challenge.
     *
     * @return The NTLM2 Session Response. This is placed in the NTLM response
     *         field of the Type 3 message; the LM response field contains the
     *         client challenge, null-padded to 24 bytes.
     */
    static byte[] ntlm2SessionResponse(byte[] ntlmHash, byte[] challenge,
            byte[] clientChallenge) throws Exception {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(challenge);
            md5.update(clientChallenge);
            byte[] digest = md5.digest();

            byte[] sessionHash = new byte[8];
            System.arraycopy(digest, 0, sessionHash, 0, 8);
            return lmResponse(ntlmHash, sessionHash);
        } catch (Exception e) {
            try {
              MD5_Impl md5 = new MD5_Impl();
              md5.update(challenge);
              md5.update(clientChallenge);
              byte[] digest = md5.digest();
  
              byte[] sessionHash = new byte[8];
              System.arraycopy(digest, 0, sessionHash, 0, 8);
              return lmResponse(ntlmHash, sessionHash);
            } catch (Exception ee) {
              if (e instanceof Exception) {
                  throw (Exception) e;
              }
              throw new Exception(e.getMessage(), e);
            }
        }
    }

    /**
     * Creates the LM Hash of the user's password.
     *
     * @param password
     *            The password.
     *
     * @return The LM Hash of the given password, used in the calculation of the
     *         LM Response.
     */
    protected static byte[] lmHash(String password) throws Exception {
        try {
            byte[] oemPassword = password.toUpperCase(Locale.ROOT).getBytes("US-ASCII");
            int length = Math.min(oemPassword.length, 14);
            byte[] keyBytes = new byte[14];
            System.arraycopy(oemPassword, 0, keyBytes, 0, length);
            Key lowKey = createDESKey(keyBytes, 0);
            Key highKey = createDESKey(keyBytes, 7);
            byte[] magicConstant = "KGS!@#$%".getBytes("US-ASCII");
            Cipher des = Cipher.getInstance("DES/ECB/NoPadding");
            des.init(Cipher.ENCRYPT_MODE, lowKey);
            byte[] lowHash = des.doFinal(magicConstant);
            des.init(Cipher.ENCRYPT_MODE, highKey);
            byte[] highHash = des.doFinal(magicConstant);
            byte[] lmHash = new byte[16];
            System.arraycopy(lowHash, 0, lmHash, 0, 8);
            System.arraycopy(highHash, 0, lmHash, 8, 8);
            return lmHash;
        } catch (Exception e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    /**
     * Creates the NTLM Hash of the user's password.
     *
     * @param password
     *            The password.
     *
     * @return The NTLM Hash of the given password, used in the calculation of
     *         the NTLM Response and the NTLMv2 and LMv2 Hashes.
     */
    protected static byte[] ntlmHash(String password) throws Exception {
        byte[] unicodePassword = password.getBytes(UnicodeLittleUnmarked);
        MD4 md4 = new MD4();
        md4.update(unicodePassword);
        return md4.getOutput();
    }

    /**
     * Creates the LMv2 Hash of the user's password.
     *
     * @return The LMv2 Hash, used in the calculation of the NTLMv2 and LMv2
     *         Responses.
     */
    private static byte[] lmv2Hash(String domain, String user, byte[] ntlmHash)
            throws Exception {
        HMACMD5 hmacMD5 = new HMACMD5(ntlmHash);
        // Upper case username, upper case domain!
        hmacMD5.update(user.toUpperCase(Locale.ROOT).getBytes(UnicodeLittleUnmarked));
        if (domain != null) {
            hmacMD5.update(domain.toUpperCase(Locale.ROOT).getBytes(UnicodeLittleUnmarked));
        }
        return hmacMD5.getOutput();
    }

    /**
     * Creates the NTLMv2 Hash of the user's password.
     *
     * @return The NTLMv2 Hash, used in the calculation of the NTLMv2 and LMv2
     *         Responses.
     */
    private static byte[] ntlmv2Hash(String domain, String user, byte[] ntlmHash)
            throws Exception {
        HMACMD5 hmacMD5 = new HMACMD5(ntlmHash);
        // Upper case username, mixed case target!!
        hmacMD5.update(user.toUpperCase(Locale.ROOT).getBytes(UnicodeLittleUnmarked));
        if (domain != null) {
            hmacMD5.update(domain.getBytes(UnicodeLittleUnmarked));
        }
        return hmacMD5.getOutput();
    }

    /**
     * Creates the LM Response from the given hash and Type 2 challenge.
     *
     * @param hash
     *            The LM or NTLM Hash.
     * @param challenge
     *            The server challenge from the Type 2 message.
     *
     * @return The response (either LM or NTLM, depending on the provided hash).
     */
    private static byte[] lmResponse(byte[] hash, byte[] challenge) throws Exception {
        try {
            byte[] keyBytes = new byte[21];
            System.arraycopy(hash, 0, keyBytes, 0, 16);
            Key lowKey = createDESKey(keyBytes, 0);
            Key middleKey = createDESKey(keyBytes, 7);
            Key highKey = createDESKey(keyBytes, 14);
            Cipher des = Cipher.getInstance("DES/ECB/NoPadding");
            des.init(Cipher.ENCRYPT_MODE, lowKey);
            byte[] lowResponse = des.doFinal(challenge);
            des.init(Cipher.ENCRYPT_MODE, middleKey);
            byte[] middleResponse = des.doFinal(challenge);
            des.init(Cipher.ENCRYPT_MODE, highKey);
            byte[] highResponse = des.doFinal(challenge);
            byte[] lmResponse = new byte[24];
            System.arraycopy(lowResponse, 0, lmResponse, 0, 8);
            System.arraycopy(middleResponse, 0, lmResponse, 8, 8);
            System.arraycopy(highResponse, 0, lmResponse, 16, 8);
            return lmResponse;
        } catch (Exception e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    /**
     * Creates the LMv2 Response from the given hash, client data, and Type 2
     * challenge.
     *
     * @param hash
     *            The NTLMv2 Hash.
     * @param clientData
     *            The client data (blob or client challenge).
     * @param challenge
     *            The server challenge from the Type 2 message.
     *
     * @return The response (either NTLMv2 or LMv2, depending on the client
     *         data).
     */
    private static byte[] lmv2Response(byte[] hash, byte[] challenge, byte[] clientData)
            throws Exception {
        HMACMD5 hmacMD5 = new HMACMD5(hash);
        hmacMD5.update(challenge);
        hmacMD5.update(clientData);
        byte[] mac = hmacMD5.getOutput();
        byte[] lmv2Response = new byte[mac.length + clientData.length];
        System.arraycopy(mac, 0, lmv2Response, 0, mac.length);
        System.arraycopy(clientData, 0, lmv2Response, mac.length, clientData.length);
        return lmv2Response;
    }

    /**
     * Creates the NTLMv2 blob from the given target information block and
     * client challenge.
     *
     * @param targetInformation
     *            The target information block from the Type 2 message.
     * @param clientChallenge
     *            The random 8-byte client challenge.
     *
     * @return The blob, used in the calculation of the NTLMv2 Response.
     */
    private static byte[] createBlob(byte[] clientChallenge, byte[] targetInformation, byte[] timestamp) {
        byte[] blobSignature = new byte[] { (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x00 };
        byte[] reserved = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        byte[] unknown1 = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        byte[] unknown2 = new byte[] { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
        byte[] blob = new byte[blobSignature.length + reserved.length + timestamp.length + 8
                + unknown1.length + targetInformation.length + unknown2.length];
        int offset = 0;
        System.arraycopy(blobSignature, 0, blob, offset, blobSignature.length);
        offset += blobSignature.length;
        System.arraycopy(reserved, 0, blob, offset, reserved.length);
        offset += reserved.length;
        System.arraycopy(timestamp, 0, blob, offset, timestamp.length);
        offset += timestamp.length;
        System.arraycopy(clientChallenge, 0, blob, offset, 8);
        offset += 8;
        System.arraycopy(unknown1, 0, blob, offset, unknown1.length);
        offset += unknown1.length;
        System.arraycopy(targetInformation, 0, blob, offset, targetInformation.length);
        offset += targetInformation.length;
        System.arraycopy(unknown2, 0, blob, offset, unknown2.length);
        offset += unknown2.length;
        return blob;
    }

    /**
     * Creates a DES encryption key from the given key material.
     *
     * @param bytes
     *            A byte array containing the DES key material.
     * @param offset
     *            The offset in the given byte array at which the 7-byte key
     *            material starts.
     *
     * @return A DES encryption key created from the key material starting at
     *         the specified offset in the given byte array.
     */
    private static Key createDESKey(byte[] bytes, int offset) {
        byte[] keyBytes = new byte[7];
        System.arraycopy(bytes, offset, keyBytes, 0, 7);
        byte[] material = new byte[8];
        material[0] = keyBytes[0];
        material[1] = (byte) (keyBytes[0] << 7 | (keyBytes[1] & 0xff) >>> 1);
        material[2] = (byte) (keyBytes[1] << 6 | (keyBytes[2] & 0xff) >>> 2);
        material[3] = (byte) (keyBytes[2] << 5 | (keyBytes[3] & 0xff) >>> 3);
        material[4] = (byte) (keyBytes[3] << 4 | (keyBytes[4] & 0xff) >>> 4);
        material[5] = (byte) (keyBytes[4] << 3 | (keyBytes[5] & 0xff) >>> 5);
        material[6] = (byte) (keyBytes[5] << 2 | (keyBytes[6] & 0xff) >>> 6);
        material[7] = (byte) (keyBytes[6] << 1);
        oddParity(material);
        return new SecretKeySpec(material, "DES");
    }

    /**
     * Applies odd parity to the given byte array.
     *
     * @param bytes
     *            The data whose parity bits are to be adjusted for odd parity.
     */
    private static void oddParity(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            boolean needsParity = (((b >>> 7) ^ (b >>> 6) ^ (b >>> 5) ^ (b >>> 4) ^ (b >>> 3)
                    ^ (b >>> 2) ^ (b >>> 1)) & 0x01) == 0;
            if (needsParity) {
                bytes[i] |= (byte) 0x01;
            } else {
                bytes[i] &= (byte) 0xfe;
            }
        }
    }

    /** NTLM message generation, base class */
    static class NTLMMessage {
        /** The current response */
        private byte[] messageContents = null;

        /** The current output position */
        private int currentOutputPosition = 0;

        /** Constructor to use when message contents are not yet known */
        NTLMMessage() {
        }

        /** Constructor to use when message contents are known */
        NTLMMessage(String messageBody, int expectedType) throws Exception {                
            messageContents = ServerListener.decodeBase64(messageBody); //messageContents = Base64.decodeBase64(messageBody.getBytes("ASCII"));
            // Look for NTLM message
            if (messageContents.length < SIGNATURE.length) {
                throw new Exception("NTLM message decoding error - packet too short");
            }
            int i = 0;
            while (i < SIGNATURE.length) {
                if (messageContents[i] != SIGNATURE[i]) {
                    throw new Exception(
                            "NTLM message expected - instead got unrecognized bytes");
                }
                i++;
            }

            // Check to be sure there's a type 2 message indicator next
            int type = readULong(SIGNATURE.length);
            if (type != expectedType) {
                throw new Exception("NTLM type " + Integer.toString(expectedType)
                        + " message expected - instead got type " + Integer.toString(type));
            }

            currentOutputPosition = messageContents.length;
        }

        /**
         * Get the length of the signature and flags, so calculations can adjust
         * offsets accordingly.
         */
        protected int getPreambleLength() {
            return SIGNATURE.length + 4;
        }

        /** Get the message length */
        protected int getMessageLength() {
            return currentOutputPosition;
        }

        /** Read a byte from a position within the message buffer */
        protected byte readByte(int position) throws Exception {
            if (messageContents.length < position + 1) {
                throw new Exception("NTLM: Message too short");
            }
            return messageContents[position];
        }

        /** Read a bunch of bytes from a position in the message buffer */
        protected void readBytes(byte[] buffer, int position) throws Exception {
            if (messageContents.length < position + buffer.length) {
                throw new Exception("NTLM: Message too short");
            }
            System.arraycopy(messageContents, position, buffer, 0, buffer.length);
        }

        /** Read a ushort from a position within the message buffer */
        protected int readUShort(int position) throws Exception {
            return NTLMEngine.readUShort(messageContents, position);
        }

        /** Read a ulong from a position within the message buffer */
        protected int readULong(int position) throws Exception {
            return NTLMEngine.readULong(messageContents, position);
        }

        /** Read a security buffer from a position within the message buffer */
        protected byte[] readSecurityBuffer(int position) throws Exception {
            return NTLMEngine.readSecurityBuffer(messageContents, position);
        }

        /**
         * Prepares the object to create a response of the given length.
         *
         * @param maxlength
         *            the maximum length of the response to prepare, not
         *            including the type and the signature (which this method
         *            adds).
         */
        protected void prepareResponse(int maxlength, int messageType) {
            messageContents = new byte[maxlength];
            currentOutputPosition = 0;
            addBytes(SIGNATURE);
            addULong(messageType);
        }

        /**
         * Adds the given byte to the response.
         *
         * @param b
         *            the byte to add.
         */
        protected void addByte(byte b) {
            messageContents[currentOutputPosition] = b;
            currentOutputPosition++;
        }

        /**
         * Adds the given bytes to the response.
         *
         * @param bytes
         *            the bytes to add.
         */
        protected void addBytes(byte[] bytes) {
            if (bytes == null) {
                return;
            }

            for (int i = 0; i < bytes.length; i++) {
                messageContents[currentOutputPosition] = bytes[i];
                currentOutputPosition++;
            }
        }

        /** Adds a USHORT to the response */
        protected void addUShort(int value) {
            addByte((byte) (value & 0xff));
            addByte((byte) (value >> 8 & 0xff));
        }

        /** Adds a ULong to the response */
        protected void addULong(int value) {
            addByte((byte) (value & 0xff));
            addByte((byte) (value >> 8 & 0xff));
            addByte((byte) (value >> 16 & 0xff));
            addByte((byte) (value >> 24 & 0xff));
        }

        /**
         * Returns the response that has been generated after shrinking the
         * array if required and base64 encodes the response.
         *
         * @return The response as above.
         */
        String getResponse() {
            byte[] resp;
            if (messageContents.length > currentOutputPosition) {
                byte[] tmp = new byte[currentOutputPosition];
                System.arraycopy(messageContents, 0, tmp, 0, currentOutputPosition);
                resp = tmp;
            } else {
                resp = messageContents;
            }
            return ServerListener.encodeBase64(resp);//EncodingUtils.getAsciiString(Base64.encodeBase64(resp));
        }

    }
               
    /** Type 1 message assembly class */
    static class Type1Message extends NTLMMessage {
        private byte[] hostBytes;
        private byte[] domainBytes;
        private boolean ntlmHashType = false;

        private String checkType(String domain) {
            if(domain == null) { return null; }
            if(domain.startsWith("*")) { 
              domain = domain.substring(1);
              this.ntlmHashType = true;
            }
            return domain;
        }

        Type1Message(String domain, String host) throws Exception {
            super();
            this.checkType(domain);
            // Strip off domain name from the host!
            String unqualifiedHost = convertHost(host);
            // Use only the base domain name!
            String unqualifiedDomain = convertDomain(domain);

            hostBytes = unqualifiedHost != null ? unqualifiedHost.getBytes(UnicodeLittleUnmarked) : null;
            domainBytes = unqualifiedDomain != null ? unqualifiedDomain.toUpperCase(Locale.ROOT).getBytes(UnicodeLittleUnmarked) : null;
        }

        Type1Message(String domain) {
            super();
            this.checkType(domain);
            hostBytes = null;
            domainBytes = null;
        }

        Type1Message() {
            super();
            hostBytes = null;
            domainBytes = null;
        }
        /**
         * Getting the response involves building the message before returning
         * it
         */
        String getResponse() {
            // Now, build the message. Calculate its length first, including
            // signature or type.
            //int finalLength = 32 + 8;
            int finalLength = 32 + 8 + (hostBytes != null ? hostBytes.length : 0) + (domainBytes != null ? domainBytes.length : 0);

            // Set up the response. This will initialize the signature, message
            // type, and flags.
            prepareResponse(finalLength, 1);

            // Flags. These are the complete set of flags we support.
            
            addULong(
                    (hostBytes != null ? FLAG_WORKSTATION_PRESENT : 0) |
                    (domainBytes != null ? FLAG_DOMAIN_PRESENT : 0) |

                    // Required flags
                    //FLAG_REQUEST_LAN_MANAGER_KEY |
                    FLAG_REQUEST_NTLMv1 |
                    FLAG_REQUEST_NTLM2_SESSION |

                    // Protocol version request
                    FLAG_REQUEST_VERSION |

                    // Recommended privacy settings
                    FLAG_REQUEST_ALWAYS_SIGN |
                    //FLAG_REQUEST_SEAL |
                    //FLAG_REQUEST_SIGN |

                    // These must be set according to documentation, based on use of SEAL above
                    FLAG_REQUEST_128BIT_KEY_EXCH |
                    FLAG_REQUEST_56BIT_ENCRYPTION |
                    //FLAG_REQUEST_EXPLICIT_KEY_EXCH |

                    FLAG_REQUEST_UNICODE_ENCODING);

            // Domain length (two times).
            addUShort(domainBytes != null ? domainBytes.length : 0);
            addUShort(domainBytes != null ? domainBytes.length : 0);

            // Domain offset.
            addULong((domainBytes != null ? domainBytes.length : 0) + 32 + 8);

            // Host length (two times).
            addUShort(hostBytes != null ? hostBytes.length : 0);
            addUShort(hostBytes != null ? hostBytes.length : 0);

            // Host offset (always 32 + 8).
            addULong((hostBytes != null ? hostBytes.length : 0) + 32 + 8);

            // Version
            addUShort(0x0105);
            // Build
            addULong(2600);
            // NTLM revision
            addUShort(0x0f00);

            // Host (workstation) String.
            if (hostBytes != null) {
                addBytes(hostBytes);
            }
            // Domain String.
            if (domainBytes != null) {
                addBytes(domainBytes);
            }

            return super.getResponse();
        }

    }

    /** Type 2 message class */
    static class Type2Message extends NTLMMessage {
        protected byte[] challenge;
        protected String target;
        protected byte[] targetInfo;
        protected int flags;

        Type2Message(String message) throws Exception {
            super(message, 2);

            challenge = new byte[8];
            readBytes(challenge, 24);

            flags = readULong(20);

            if ((flags & FLAG_REQUEST_UNICODE_ENCODING) == 0) {
                throw new Exception(
                        "NTLM type 2 message indicates no support for Unicode. Flags are: "
                                + Integer.toString(flags));
            }

            // Do the target!
            target = null;
            // The TARGET_DESIRED flag is said to not have understood semantics
            // in Type2 messages, so use the length of the packet to decide
            // how to proceed instead
            if (getMessageLength() >= 12 + 8) {
                byte[] bytes = readSecurityBuffer(12);
                if (bytes.length != 0) {
                    try {
                        target = new String(bytes, UnicodeLittleUnmarked);
                    } catch (UnsupportedEncodingException e) {
                        throw new Exception(e.getMessage(), e);
                    }
                }
            }

            // Do the target info!
            targetInfo = null;
            // TARGET_DESIRED flag cannot be relied on, so use packet length
            if (getMessageLength() >= 40 + 8) {
                byte[] bytes = readSecurityBuffer(40);
                if (bytes.length != 0) {
                    targetInfo = bytes;
                }
            }
        }

        /** Retrieve the challenge */
        byte[] getChallenge() {
            return challenge;
        }

        /** Retrieve the target */
        String getTarget() {
            return target;
        }

        /** Retrieve the target info */
        byte[] getTargetInfo() {
            return targetInfo;
        }

        /** Retrieve the response flags */
        int getFlags() {
            return flags;
        }

    }

    /** Type 3 message assembly class */
    static class Type3Message extends NTLMMessage {
        // Response flags from the type2 message
        protected boolean ntlmHashType = false;

        protected int type2Flags;

        protected byte[] domainBytes;
        protected byte[] hostBytes;
        protected byte[] userBytes;

        protected byte[] lmResp;
        protected byte[] ntResp;
        protected byte[] sessionKey;


        /** Constructor. Pass the arguments we will need */
        Type3Message(String domain, String host, String user, String password, byte[] nonce, int type2Flags, String target, byte[] targetInformation) throws Exception {
            if(domain != null && domain.startsWith("*")) { 
              domain = domain.substring(1);
              this.ntlmHashType = true;
            }
            // Save the flags
            this.type2Flags = type2Flags;

            // Strip off domain name from the host!
            String unqualifiedHost = convertHost(host);
            // Use only the base domain name!
            String unqualifiedDomain = convertDomain(domain);

            // Create a cipher generator class.  Use domain BEFORE it gets modified!
            CipherGen gen = new CipherGen(this.ntlmHashType, unqualifiedDomain, user, password, nonce, target, targetInformation);

            // Use the new code to calculate the responses, including v2 if that
            // seems warranted.
            byte[] userSessionKey;
            try {
                // This conditional may not work on Windows Server 2008 R2 and above, where it has not yet
                // been tested
                if (((type2Flags & FLAG_TARGETINFO_PRESENT) != 0) &&
                    targetInformation != null && target != null) {
                    // NTLMv2
                    ntResp = gen.getNTLMv2Response();
                    lmResp = gen.getLMv2Response();
                    System.out.println("AUTH TYPE NTLM2 and REQUEST_LAN_MANAGER_KEY: " + ((type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY) != 0));
                    if ((type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY) != 0) {
                        userSessionKey = gen.getLanManagerSessionKey();
                    } else {
                        userSessionKey = gen.getNTLMv2UserSessionKey();
                    }
                } else {
                    // NTLMv1
                    System.out.println("AUTH TYPE NTLM1 and FLAG_REQUEST_NTLM2_SESSION and REQUEST_LAN_MANAGER_KEY: " + ((type2Flags & FLAG_REQUEST_NTLM2_SESSION) != 0) + "_" + ((type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY) != 0));
                    if ((type2Flags & FLAG_REQUEST_NTLM2_SESSION) != 0) {
                        // NTLM2 session stuff is requested
                        ntResp = gen.getNTLM2SessionResponse();
                        lmResp = gen.getLM2SessionResponse();
                        if ((type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY) != 0) {
                            userSessionKey = gen.getLanManagerSessionKey();
                        } else {
                            userSessionKey = gen.getNTLM2SessionResponseUserSessionKey();
                        }
                    } else {
                        ntResp = gen.getNTLMResponse();
                        lmResp = gen.getLMResponse();
                        if ((type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY) != 0) {
                            userSessionKey = gen.getLanManagerSessionKey();
                        } else {
                            userSessionKey = gen.getNTLMUserSessionKey();
                        }
                    }
                }
            } catch (Exception e) {
                // This likely means we couldn't find the MD4 hash algorithm -
                // fail back to just using LM
                System.out.println("AUTH TYPE NTLM UNKNOWN and FLAG_REQUEST_LAN_MANAGER_KEY: " + ((type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY) != 0));
                ntResp = new byte[0];
                lmResp = gen.getLMResponse();
                if ((type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY) != 0) {
                    userSessionKey = gen.getLanManagerSessionKey();
                } else {
                    userSessionKey = gen.getLMUserSessionKey();
                }
            }

            if ((type2Flags & FLAG_REQUEST_SIGN) != 0) {
                if ((type2Flags & FLAG_REQUEST_EXPLICIT_KEY_EXCH) != 0) {
                    sessionKey = RC4(gen.getSecondaryKey(), userSessionKey);
                } else {
                    sessionKey = userSessionKey;
                }
            } else {
                sessionKey = null;
            }
            hostBytes = unqualifiedHost != null ? unqualifiedHost.getBytes(UnicodeLittleUnmarked) : null;
            domainBytes = unqualifiedDomain != null ? unqualifiedDomain.toUpperCase(Locale.ROOT).getBytes(UnicodeLittleUnmarked) : null;
            userBytes = user.getBytes(UnicodeLittleUnmarked);
        }

        /** Assemble the response */
        String getResponse() {
            int ntRespLen = ntResp.length;
            int lmRespLen = lmResp.length;

            int domainLen = domainBytes != null ? domainBytes.length : 0;
            int hostLen = hostBytes != null ? hostBytes.length : 0;
            int userLen = userBytes.length;
            int sessionKeyLen;
            if (sessionKey != null) {
                sessionKeyLen = sessionKey.length;
            } else {
                sessionKeyLen = 0;
            }

            // Calculate the layout within the packet
            int lmRespOffset = 72;  // allocate space for the version
            int ntRespOffset = lmRespOffset + lmRespLen;
            int domainOffset = ntRespOffset + ntRespLen;
            int userOffset = domainOffset + domainLen;
            int hostOffset = userOffset + userLen;
            int sessionKeyOffset = hostOffset + hostLen;
            int finalLength = sessionKeyOffset + sessionKeyLen;

            // Start the response. Length includes signature and type
            prepareResponse(finalLength, 3);

            // LM Resp Length (twice)
            addUShort(lmRespLen);
            addUShort(lmRespLen);

            // LM Resp Offset
            addULong(lmRespOffset);

            // NT Resp Length (twice)
            addUShort(ntRespLen);
            addUShort(ntRespLen);

            // NT Resp Offset
            addULong(ntRespOffset);

            // Domain length (twice)
            addUShort(domainLen);
            addUShort(domainLen);

            // Domain offset.
            addULong(domainOffset);

            // User Length (twice)
            addUShort(userLen);
            addUShort(userLen);

            // User offset
            addULong(userOffset);

            // Host length (twice)
            addUShort(hostLen);
            addUShort(hostLen);

            // Host offset
            addULong(hostOffset);

            // Session key length (twice)
            addUShort(sessionKeyLen);
            addUShort(sessionKeyLen);

            // Session key offset
            addULong(sessionKeyOffset);

            // Flags.
            addULong(
                    //FLAG_WORKSTATION_PRESENT |
                    //FLAG_DOMAIN_PRESENT |

                    // Required flags
                    (type2Flags & FLAG_REQUEST_LAN_MANAGER_KEY) |
                    (type2Flags & FLAG_REQUEST_NTLMv1) |
                    (type2Flags & FLAG_REQUEST_NTLM2_SESSION) |

                    // Protocol version request
                    FLAG_REQUEST_VERSION |

                    // Recommended privacy settings
                    (type2Flags & FLAG_REQUEST_ALWAYS_SIGN) |
                    (type2Flags & FLAG_REQUEST_SEAL) |
                    (type2Flags & FLAG_REQUEST_SIGN) |

                    // These must be set according to documentation, based on use of SEAL above
                    (type2Flags & FLAG_REQUEST_128BIT_KEY_EXCH) |
                    (type2Flags & FLAG_REQUEST_56BIT_ENCRYPTION) |
                    (type2Flags & FLAG_REQUEST_EXPLICIT_KEY_EXCH) |

                    (type2Flags & FLAG_TARGETINFO_PRESENT) |
                    (type2Flags & FLAG_REQUEST_UNICODE_ENCODING) |
                    (type2Flags & FLAG_REQUEST_TARGET)
            );

            // Version
            addUShort(0x0105);
            // Build
            addULong(2600);
            // NTLM revision
            addUShort(0x0f00);

            // Add the actual data
            addBytes(lmResp);
            addBytes(ntResp);
            addBytes(domainBytes);
            addBytes(userBytes);
            addBytes(hostBytes);
            if (sessionKey != null) {
                addBytes(sessionKey);
            }

            return super.getResponse();
        }
    }

    static void writeULong(byte[] buffer, int value, int offset) {
        buffer[offset] = (byte) (value & 0xff);
        buffer[offset + 1] = (byte) (value >> 8 & 0xff);
        buffer[offset + 2] = (byte) (value >> 16 & 0xff);
        buffer[offset + 3] = (byte) (value >> 24 & 0xff);
    }

    static int F(int x, int y, int z) {
        return ((x & y) | (~x & z));
    }

    static int G(int x, int y, int z) {
        return ((x & y) | (x & z) | (y & z));
    }

    static int H(int x, int y, int z) {
        return (x ^ y ^ z);
    }

    static int rotintlft(int val, int numbits) {
        return ((val << numbits) | (val >>> (32 - numbits)));
    }

    /**
     * Cryptography support - MD4. The following class was based loosely on the
     * RFC and on code found at http://www.cs.umd.edu/~harry/jotp/src/md.java.
     * Code correctness was verified by looking at MD4.java from the jcifs
     * library (http://jcifs.samba.org). It was massaged extensively to the
     * final form found here by Karl Wright (kwright@metacarta.com).
     */
    static class MD4 {
        protected int A = 0x67452301;
        protected int B = 0xefcdab89;
        protected int C = 0x98badcfe;
        protected int D = 0x10325476;
        protected long count = 0L;
        protected byte[] dataBuffer = new byte[64];

        MD4() {
        }

        void update(byte[] input) {
            // We always deal with 512 bits at a time. Correspondingly, there is
            // a buffer 64 bytes long that we write data into until it gets
            // full.
            int curBufferPos = (int) (count & 63L);
            int inputIndex = 0;
            while (input.length - inputIndex + curBufferPos >= dataBuffer.length) {
                // We have enough data to do the next step. Do a partial copy
                // and a transform, updating inputIndex and curBufferPos
                // accordingly
                int transferAmt = dataBuffer.length - curBufferPos;
                System.arraycopy(input, inputIndex, dataBuffer, curBufferPos, transferAmt);
                count += transferAmt;
                curBufferPos = 0;
                inputIndex += transferAmt;
                processBuffer();
            }

            // If there's anything left, copy it into the buffer and leave it.
            // We know there's not enough left to process.
            if (inputIndex < input.length) {
                int transferAmt = input.length - inputIndex;
                System.arraycopy(input, inputIndex, dataBuffer, curBufferPos, transferAmt);
                count += transferAmt;
                curBufferPos += transferAmt;
            }
        }

        byte[] getOutput() {
            // Feed pad/length data into engine. This must round out the input
            // to a multiple of 512 bits.
            int bufferIndex = (int) (count & 63L);
            int padLen = (bufferIndex < 56) ? (56 - bufferIndex) : (120 - bufferIndex);
            byte[] postBytes = new byte[padLen + 8];
            // Leading 0x80, specified amount of zero padding, then length in
            // bits.
            postBytes[0] = (byte) 0x80;
            // Fill out the last 8 bytes with the length
            for (int i = 0; i < 8; i++) {
                postBytes[padLen + i] = (byte) ((count * 8) >>> (8 * i));
            }

            // Update the engine
            update(postBytes);

            // Calculate final result
            byte[] result = new byte[16];
            writeULong(result, A, 0);
            writeULong(result, B, 4);
            writeULong(result, C, 8);
            writeULong(result, D, 12);
            return result;
        }

        protected void processBuffer() {
            // Convert current buffer to 16 ulongs
            int[] d = new int[16];

            for (int i = 0; i < 16; i++) {
                d[i] = (dataBuffer[i * 4] & 0xff) + ((dataBuffer[i * 4 + 1] & 0xff) << 8)
                        + ((dataBuffer[i * 4 + 2] & 0xff) << 16)
                        + ((dataBuffer[i * 4 + 3] & 0xff) << 24);
            }

            // Do a round of processing
            int AA = A;
            int BB = B;
            int CC = C;
            int DD = D;
            round1(d);
            round2(d);
            round3(d);
            A += AA;
            B += BB;
            C += CC;
            D += DD;

        }

        protected void round1(int[] d) {
            A = rotintlft((A + F(B, C, D) + d[0]), 3);
            D = rotintlft((D + F(A, B, C) + d[1]), 7);
            C = rotintlft((C + F(D, A, B) + d[2]), 11);
            B = rotintlft((B + F(C, D, A) + d[3]), 19);

            A = rotintlft((A + F(B, C, D) + d[4]), 3);
            D = rotintlft((D + F(A, B, C) + d[5]), 7);
            C = rotintlft((C + F(D, A, B) + d[6]), 11);
            B = rotintlft((B + F(C, D, A) + d[7]), 19);

            A = rotintlft((A + F(B, C, D) + d[8]), 3);
            D = rotintlft((D + F(A, B, C) + d[9]), 7);
            C = rotintlft((C + F(D, A, B) + d[10]), 11);
            B = rotintlft((B + F(C, D, A) + d[11]), 19);

            A = rotintlft((A + F(B, C, D) + d[12]), 3);
            D = rotintlft((D + F(A, B, C) + d[13]), 7);
            C = rotintlft((C + F(D, A, B) + d[14]), 11);
            B = rotintlft((B + F(C, D, A) + d[15]), 19);
        }

        protected void round2(int[] d) {
            A = rotintlft((A + G(B, C, D) + d[0] + 0x5a827999), 3);
            D = rotintlft((D + G(A, B, C) + d[4] + 0x5a827999), 5);
            C = rotintlft((C + G(D, A, B) + d[8] + 0x5a827999), 9);
            B = rotintlft((B + G(C, D, A) + d[12] + 0x5a827999), 13);

            A = rotintlft((A + G(B, C, D) + d[1] + 0x5a827999), 3);
            D = rotintlft((D + G(A, B, C) + d[5] + 0x5a827999), 5);
            C = rotintlft((C + G(D, A, B) + d[9] + 0x5a827999), 9);
            B = rotintlft((B + G(C, D, A) + d[13] + 0x5a827999), 13);

            A = rotintlft((A + G(B, C, D) + d[2] + 0x5a827999), 3);
            D = rotintlft((D + G(A, B, C) + d[6] + 0x5a827999), 5);
            C = rotintlft((C + G(D, A, B) + d[10] + 0x5a827999), 9);
            B = rotintlft((B + G(C, D, A) + d[14] + 0x5a827999), 13);

            A = rotintlft((A + G(B, C, D) + d[3] + 0x5a827999), 3);
            D = rotintlft((D + G(A, B, C) + d[7] + 0x5a827999), 5);
            C = rotintlft((C + G(D, A, B) + d[11] + 0x5a827999), 9);
            B = rotintlft((B + G(C, D, A) + d[15] + 0x5a827999), 13);

        }

        protected void round3(int[] d) {
            A = rotintlft((A + H(B, C, D) + d[0] + 0x6ed9eba1), 3);
            D = rotintlft((D + H(A, B, C) + d[8] + 0x6ed9eba1), 9);
            C = rotintlft((C + H(D, A, B) + d[4] + 0x6ed9eba1), 11);
            B = rotintlft((B + H(C, D, A) + d[12] + 0x6ed9eba1), 15);

            A = rotintlft((A + H(B, C, D) + d[2] + 0x6ed9eba1), 3);
            D = rotintlft((D + H(A, B, C) + d[10] + 0x6ed9eba1), 9);
            C = rotintlft((C + H(D, A, B) + d[6] + 0x6ed9eba1), 11);
            B = rotintlft((B + H(C, D, A) + d[14] + 0x6ed9eba1), 15);

            A = rotintlft((A + H(B, C, D) + d[1] + 0x6ed9eba1), 3);
            D = rotintlft((D + H(A, B, C) + d[9] + 0x6ed9eba1), 9);
            C = rotintlft((C + H(D, A, B) + d[5] + 0x6ed9eba1), 11);
            B = rotintlft((B + H(C, D, A) + d[13] + 0x6ed9eba1), 15);

            A = rotintlft((A + H(B, C, D) + d[3] + 0x6ed9eba1), 3);
            D = rotintlft((D + H(A, B, C) + d[11] + 0x6ed9eba1), 9);
            C = rotintlft((C + H(D, A, B) + d[7] + 0x6ed9eba1), 11);
            B = rotintlft((B + H(C, D, A) + d[15] + 0x6ed9eba1), 15);

        }

    }

    /**
     * Cryptography support - HMACMD5 - algorithmically based on various web
     * resources by Karl Wright
     */
    static class HMACMD5 {
        protected byte[] ipad;
        protected byte[] opad;
        protected MessageDigest md5 = null;
        protected MD5_Impl md5f = null;

        HMACMD5(byte[] key) throws Exception {
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (Exception ex) {
               // Umm, the algorithm doesn't exist - throw an
               // Exception!
               try {
                 md5f = new MD5_Impl(); //fallback  
               } catch (Exception ee) {
                 throw new Exception(
                        "Error getting md5 message digest implementation: " + ex.getMessage(), ex);
               }
            }

            // Initialize the pad buffers with the key
            ipad = new byte[64];
            opad = new byte[64];

            int keyLength = key.length;
            if (keyLength > 64) {
                // Use MD5 of the key instead, as described in RFC 2104
                if(md5 != null) {
                  md5.update(key);
                  key = md5.digest();
                } else {
                  md5f.update(key);
                  key = md5f.digest();
                }
                keyLength = key.length;
            }
            int i = 0;
            while (i < keyLength) {
                ipad[i] = (byte) (key[i] ^ (byte) 0x36);
                opad[i] = (byte) (key[i] ^ (byte) 0x5c);
                i++;
            }
            while (i < 64) {
                ipad[i] = (byte) 0x36;
                opad[i] = (byte) 0x5c;
                i++;
            }

            // Very important: update the digest with the ipad buffer
            if(md5 != null) {
              md5.reset();
              md5.update(ipad);
            } else {
              md5f.reset();
              md5f.update(ipad);
            }

        }

        /** Grab the current digest. This is the "answer". */
        byte[] getOutput() throws Exception {
          if(md5 != null) {
            byte[] digest = md5.digest();
            md5.update(opad);
            return md5.digest(digest);
          } else {
            byte[] digest = md5f.digest();
            md5f.update(opad);
            return md5f.digest(digest);
          }
        }

        /** Update by adding a complete array */
        void update(byte[] input) throws Exception {
          if(md5 != null) {
            md5.update(input);
          } else {
            md5f.update(input);
          }
        }

        /** Update the algorithm */
        void update(byte[] input, int offset, int length) throws Exception {
          if(md5 != null) {
            md5.update(input, offset, length);
          } else {
            md5f.engineUpdate(input, offset, length);
          }
        }

    }

    public String generateType1Msg (
            String domain,
            String workstation) throws Exception {
        return getType1Message(workstation, domain);
    }

    public String generateType3Msg (
            String username,
            String password,
            String domain,
            String workstation,
            String challenge) throws Exception {
        Type2Message t2m = new Type2Message(challenge);
        return getType3Message(
                username,
                password,
                workstation,
                domain,
                t2m.getChallenge(),
                t2m.getFlags(),
                t2m.getTarget(),
                t2m.getTargetInfo());
    }

    static class RC4_Impl {      
      private static int UNINITIALIZED = 0;
      private static int ENCRYPT = 1;
      private static int DECRYPT = 2;
      
      private int[] sBox = new int[256];      
      private int x, y;
      private static int BLOCK_SIZE = 1;
      private int state;
      private String cipherName = "RC4";

      public RC4_Impl() { }
      
      public void engineInitEncrypt(byte[] key) throws Exception {
        makeKey(key);
        state = ENCRYPT;
      }                            
      
      public void engineInitDecrypt(byte[] key) throws Exception {
        makeKey(key);
        state = DECRYPT;
      }                            
      
      protected int engineUpdate(byte[] in, int inOffset, int inLen, byte[] out, int outOffset) {
        
        if (in == out
            && (outOffset >= inOffset && outOffset < inOffset + inLen || inOffset >= outOffset
                && inOffset < outOffset + inLen)) {
          byte[] newin = new byte[inLen];
          System.arraycopy(in, inOffset, newin, 0, inLen);
          in = newin;
          inOffset = 0;
        }
    
        rc4(in, inOffset, inLen, out, outOffset);
    
        return inLen;
      }
          
      private void rc4(byte[] in, int inOffset, int inLen, byte[] out,
          int outOffset) {
        int xorIndex, t;
    
        for (int i = 0; i < inLen; i++) {
          x = (x + 1) & 0xFF;
          y = (sBox[x] + y) & 0xFF;
    
          t = sBox[x];
          sBox[x] = sBox[y];
          sBox[y] = t;
    
          xorIndex = (sBox[x] + sBox[y]) & 0xFF;
          out[outOffset++] = (byte) (in[inOffset++] ^ sBox[xorIndex]);
        }
      }    
      
      private void makeKey(byte[] userkey) throws Exception {
        int len = userkey.length;
    
        x = y = 0;
        for (int i = 0; i < 256; i++)
          sBox[i] = i;
    
        int i1 = 0, i2 = 0, t;
    
        for (int i = 0; i < 256; i++) {
          t = sBox[i];
          i2 = ((userkey[i1] & 0xFF) + t + i2) & 0xFF;
    
          sBox[i] = sBox[i2];
          sBox[i2] = t;
    
          i1 = (i1 + 1) % len;
        }
      }
    
      public byte[] crypt(byte[] data, int position, int length) {
        byte[] buffer = new byte[length];
        engineUpdate(data, position, length, buffer, 0);
        return buffer;
      }
    
      public byte[] crypt(byte[] data) {
        byte[] buffer = new byte[data.length];
        engineUpdate(data, 0, data.length, buffer, 0);
        return buffer;
      }
    
      public void crypt(byte[] in, int in_offset, int length, byte[] out, int out_offset) {
        engineUpdate(in, in_offset, length, out, out_offset);
      }
    
       protected RC4_Impl initEncrypt(byte[] k) throws Exception { 
         this.engineInitEncrypt(k);
         return this;
       }
    }

    static class MD5_Impl {
      private byte[] buffer;
      
      private int buffered;
    
      private String algorithm;
    
      
      private int count;
    
      private static int MAX_COUNT = (1 << 28) - 1;
    
      
      private int data_length;
      
      private static int HASH_LENGTH = 16;
      
      private static int DATA_LENGTH = 64;
    
      private int[] data;
    
      private int[] digest;
    
      private byte[] tmp;
    
      public MD5_Impl() {
        this("MD5");
        java_init();
        engineReset();
      }
    
      protected MD5_Impl(String algorithm) {
        this.algorithm = algorithm;
        data_length = engineGetDataLength();
        buffer = new byte[data_length];
      }
    
      
      protected int bitcount() {
        return count * 8;
      }
    
      
      public void engineResetMain() {
        buffered = 0;
        count = 0;
      }
    
      
      public void engineUpdate(byte b) throws Exception {
        byte[] data = { b };
        engineUpdate(data, 0, 1);
      }
    
      public void engineUpdate(byte[] data) throws Exception {
        engineUpdate(data, 0, data.length);
      }

      public void update(byte[] data) throws Exception {
        engineUpdate(data, 0, data.length);
      }

      public void engineUpdate(byte[] data, int offset, int length) throws Exception {
        count += length;
        if (count > MAX_COUNT)
          throw new Exception(getAlgorithm()
              + ": Maximum input length exceeded");
    
        int datalen = data_length;
        int remainder;
    
        while (length >= (remainder = datalen - buffered)) {
          System.arraycopy(data, offset, buffer, buffered, remainder);
          engineTransform(buffer);
          length -= remainder;
          offset += remainder;
          buffered = 0;
        }
    
        if (length > 0) {
          System.arraycopy(data, offset, buffer, buffered, length);
          buffered += length;
        }
      }
    
      public byte[] digest() {
        return engineDigest(buffer, buffered);
      }
      
      public byte[] engineDigest() {
        return engineDigest(buffer, buffered);
      }
    
      protected String getAlgorithm() {
        return this.algorithm;
      }  
     
      protected int engineGetDigestLength() {
        return HASH_LENGTH;
      }
    
      
      protected int engineGetDataLength() {
        return DATA_LENGTH;
      }
    
      private void java_init() {
        digest = new int[HASH_LENGTH / 4];
        data = new int[DATA_LENGTH / 4];
        tmp = new byte[DATA_LENGTH];
      }
      
      public void reset() {
        this.engineResetMain();
        java_reset();
      }

      public void engineReset() {
        this.engineResetMain();
        java_reset();
      }
    
      private void java_reset() {
        digest[0] = 0x67452301;
        digest[1] = 0xEFCDAB89;
        digest[2] = 0x98BADCFE;
        digest[3] = 0x10325476;
      }
    
      
      protected void engineTransform(byte[] in) {
        java_transform(in);
      }
    
      private void java_transform(byte[] in) {
        byte2int(in, 0, data, 0, DATA_LENGTH / 4);
        transform(data);
      }
    
      public byte[] digest(byte[] buffer) {
        return engineDigest(buffer, buffer.length);
      }

      public byte[] engineDigest(byte[] in, int length) {
        byte b[] = java_digest(in, length);
        engineReset();
        return b;
      }
    
      private byte[] java_digest(byte[] in, int pos) {
        if (pos != 0)
          System.arraycopy(in, 0, tmp, 0, pos);
    
        tmp[pos++] = -128; 
    
        if (pos > DATA_LENGTH - 8) {
          while (pos < DATA_LENGTH)
            tmp[pos++] = 0;
    
          byte2int(tmp, 0, data, 0, DATA_LENGTH / 4);
          transform(data);
          pos = 0;
        }
    
        while (pos < DATA_LENGTH - 8)
          tmp[pos++] = 0;
    
        byte2int(tmp, 0, data, 0, (DATA_LENGTH / 4) - 2);
    
        int bc = bitcount();
        data[14] = bc;
        data[15] = 0;
    
        transform(data);
    
        byte buf[] = new byte[HASH_LENGTH];
    
        
        int off = 0;
        for (int i = 0; i < HASH_LENGTH / 4; ++i) {
          int d = digest[i];
          buf[off++] = (byte) d;
          buf[off++] = (byte) (d >>> 8);
          buf[off++] = (byte) (d >>> 16);
          buf[off++] = (byte) (d >>> 24);
        }
        return buf;
      }
        
      static protected int F(int x, int y, int z) {
        return (z ^ (x & (y ^ z)));
      }
    
      static protected int G(int x, int y, int z) {
        return (y ^ (z & (x ^ y)));
      }
    
      static protected int H(int x, int y, int z) {
        return (x ^ y ^ z);
      }
    
      static protected int I(int x, int y, int z) {
        return (y ^ (x | ~z));
      }
    
      static protected int FF(int a, int b, int c, int d, int k, int s, int t) {
        a += k + t + F(b, c, d);
        a = (a << s | a >>> -s);
        return a + b;
      }
    
      static protected int GG(int a, int b, int c, int d, int k, int s, int t) {
        a += k + t + G(b, c, d);
        a = (a << s | a >>> -s);
        return a + b;
      }
    
      static protected int HH(int a, int b, int c, int d, int k, int s, int t) {
        a += k + t + H(b, c, d);
        a = (a << s | a >>> -s);
        return a + b;
      }
    
      static protected int II(int a, int b, int c, int d, int k, int s, int t) {
        a += k + t + I(b, c, d);
        a = (a << s | a >>> -s);
        return a + b;
      }
    
      protected void transform(int M[]) {
        int a, b, c, d;
    
        a = digest[0];
        b = digest[1];
        c = digest[2];
        d = digest[3];
    
        a = FF(a, b, c, d, M[0], 7, 0xd76aa478);
        d = FF(d, a, b, c, M[1], 12, 0xe8c7b756);
        c = FF(c, d, a, b, M[2], 17, 0x242070db);
        b = FF(b, c, d, a, M[3], 22, 0xc1bdceee);
        a = FF(a, b, c, d, M[4], 7, 0xf57c0faf);
        d = FF(d, a, b, c, M[5], 12, 0x4787c62a);
        c = FF(c, d, a, b, M[6], 17, 0xa8304613);
        b = FF(b, c, d, a, M[7], 22, 0xfd469501);
        a = FF(a, b, c, d, M[8], 7, 0x698098d8);
        d = FF(d, a, b, c, M[9], 12, 0x8b44f7af);
        c = FF(c, d, a, b, M[10], 17, 0xffff5bb1);
        b = FF(b, c, d, a, M[11], 22, 0x895cd7be);
        a = FF(a, b, c, d, M[12], 7, 0x6b901122);
        d = FF(d, a, b, c, M[13], 12, 0xfd987193);
        c = FF(c, d, a, b, M[14], 17, 0xa679438e);
        b = FF(b, c, d, a, M[15], 22, 0x49b40821);
    
        a = GG(a, b, c, d, M[1], 5, 0xf61e2562);
        d = GG(d, a, b, c, M[6], 9, 0xc040b340);
        c = GG(c, d, a, b, M[11], 14, 0x265e5a51);
        b = GG(b, c, d, a, M[0], 20, 0xe9b6c7aa);
        a = GG(a, b, c, d, M[5], 5, 0xd62f105d);
        d = GG(d, a, b, c, M[10], 9, 0x02441453);
        c = GG(c, d, a, b, M[15], 14, 0xd8a1e681);
        b = GG(b, c, d, a, M[4], 20, 0xe7d3fbc8);
        a = GG(a, b, c, d, M[9], 5, 0x21e1cde6);
        d = GG(d, a, b, c, M[14], 9, 0xc33707d6);
        c = GG(c, d, a, b, M[3], 14, 0xf4d50d87);
        b = GG(b, c, d, a, M[8], 20, 0x455a14ed);
        a = GG(a, b, c, d, M[13], 5, 0xa9e3e905);
        d = GG(d, a, b, c, M[2], 9, 0xfcefa3f8);
        c = GG(c, d, a, b, M[7], 14, 0x676f02d9);
        b = GG(b, c, d, a, M[12], 20, 0x8d2a4c8a);
    
        a = HH(a, b, c, d, M[5], 4, 0xfffa3942);
        d = HH(d, a, b, c, M[8], 11, 0x8771f681);
        c = HH(c, d, a, b, M[11], 16, 0x6d9d6122);
        b = HH(b, c, d, a, M[14], 23, 0xfde5380c);
        a = HH(a, b, c, d, M[1], 4, 0xa4beea44);
        d = HH(d, a, b, c, M[4], 11, 0x4bdecfa9);
        c = HH(c, d, a, b, M[7], 16, 0xf6bb4b60);
        b = HH(b, c, d, a, M[10], 23, 0xbebfbc70);
        a = HH(a, b, c, d, M[13], 4, 0x289b7ec6);
        d = HH(d, a, b, c, M[0], 11, 0xeaa127fa);
        c = HH(c, d, a, b, M[3], 16, 0xd4ef3085);
        b = HH(b, c, d, a, M[6], 23, 0x04881d05);
        a = HH(a, b, c, d, M[9], 4, 0xd9d4d039);
        d = HH(d, a, b, c, M[12], 11, 0xe6db99e5);
        c = HH(c, d, a, b, M[15], 16, 0x1fa27cf8);
        b = HH(b, c, d, a, M[2], 23, 0xc4ac5665);
    
        a = II(a, b, c, d, M[0], 6, 0xf4292244);
        d = II(d, a, b, c, M[7], 10, 0x432aff97);
        c = II(c, d, a, b, M[14], 15, 0xab9423a7);
        b = II(b, c, d, a, M[5], 21, 0xfc93a039);
        a = II(a, b, c, d, M[12], 6, 0x655b59c3);
        d = II(d, a, b, c, M[3], 10, 0x8f0ccc92);
        c = II(c, d, a, b, M[10], 15, 0xffeff47d);
        b = II(b, c, d, a, M[1], 21, 0x85845dd1);
        a = II(a, b, c, d, M[8], 6, 0x6fa87e4f);
        d = II(d, a, b, c, M[15], 10, 0xfe2ce6e0);
        c = II(c, d, a, b, M[6], 15, 0xa3014314);
        b = II(b, c, d, a, M[13], 21, 0x4e0811a1);
        a = II(a, b, c, d, M[4], 6, 0xf7537e82);
        d = II(d, a, b, c, M[11], 10, 0xbd3af235);
        c = II(c, d, a, b, M[2], 15, 0x2ad7d2bb);
        b = II(b, c, d, a, M[9], 21, 0xeb86d391);
    
        digest[0] += a;
        digest[1] += b;
        digest[2] += c;
        digest[3] += d;
      }
    
      
      
      private static void byte2int(byte[] src, int srcOffset, int[] dst,
          int dstOffset, int length) {
        while (length-- > 0) {
          
          dst[dstOffset++] = (src[srcOffset++] & 0xFF)
              | ((src[srcOffset++] & 0xFF) << 8)
              | ((src[srcOffset++] & 0xFF) << 16)
              | ((src[srcOffset++] & 0xFF) << 24);
        }
      }
    }

}
