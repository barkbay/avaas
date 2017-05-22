/******************************************************************************
 **
 ** This library is free software; you can redistribute it and/or
 ** modify it under the terms of the GNU Lesser General Public
 ** License as published by the Free Software Foundation; either
 ** version 2.1 of the License, or (at your option) any later version.
 **
 ** This library is distributed in the hope that it will be useful,
 ** but WITHOUT ANY WARRANTY; without even the implied warranty of
 ** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 ** Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public
 ** License along with this library; if not, write to the Free Software
 ** Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *********************************************************************************/

package avaas.magic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A magic signature is an offset and a sequence of bytes
 */
public class MagicSignature {

    private static final Logger logger = LoggerFactory.getLogger(MagicSignature.class);

    /**
     * The offset from where the magic numbers must be searched
     * @return
     */
    public long getOffset() {
        return offset;
    }

    /**
     * The sequence of bytes of this magic number
     * @return The sequence of bytes of this magic number
     */
    public byte[] getBytes() {
        return bytes;
    }

    private final long offset;

    private final byte[] bytes;

    private MagicSignature(long offset, byte[] bytes) {
        this.offset = offset;
        this.bytes = bytes;
    }

    /**
     * e.g. :
     * <pre>
     *     0,89 50 4E 47 0D 0A 1A 0A
     * </pre>
     */
    private static final Pattern signatureAsLineInFile = Pattern.compile("^(\\d+)\\s*,\\s*((([A-Fa-f0-9]{2})\\s?)*)$");

    /**
     * Convert a string to a magic signature
     * @param signature the magic signature as a {@link String}
     * @return May be a {@link MagicSignature}
     */
    public static Optional<MagicSignature> fromString(final String signature) {
        final Matcher matcher = signatureAsLineInFile.matcher(signature);
        if (matcher.matches()) {
            MagicSignature r = new MagicSignature(
                    Long.parseLong(matcher.group(1)),
                    byteArrayFromString(matcher.group(2))
            );
            return Optional.of(r);
        } else {
            return Optional.empty();
        }
    }

    private static final byte[] byteArrayFromString(final String string) {
        final String s = string.replaceAll("\\s+","");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * Check if a given input matches this magic signature
     * @param data
     * @return
     */
    public boolean match(final byte[] data) {
        if (data.length >= (offset + bytes.length)) {
            int pos = 0;
            for (long i = offset; i < bytes.length; i++) {
                if (data[(int)i] != bytes[pos]) return false;
                pos = pos + 1;
            }
            logger.info("Magic matches");
            return true;
        }
        logger.info("Magic NOT matches");
        return false;
    }
}
