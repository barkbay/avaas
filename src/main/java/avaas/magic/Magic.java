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

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * Help to check file against a white list of magic numbers
 * Magic numbers can be loaded from any kind of file with the following content :
 * <pre>
 *    # Line format is "offset, XX XX XX" where XX hexadecimal representation of a byte
 *    # Gif87a
 *    0,47 49 46 38 37 61
 *    # Gif89a
 *    0,47 49 46 38 39 61
 *    # TIFF (little endian format)
 *    0,49 49 2A 00
 *    # TIFF (big endian format)
 *    0,4D 4D 00 2A
 *    # PDF
 *    0,25 50 44 46
 *    # JPEG
 *    0,FF D8 FF
 *    # BMP
 *    0,42 4D
 *    # PNG
 *    0,89 50 4E 47 0D 0A 1A 0A
 *
 * </pre>
 */
public class Magic {

    private static final Logger logger = LoggerFactory.getLogger(Magic.class);

    public static final String DEFAULT_MAGIC_WHITELIST = "/etc/avaas/magic.txt";

    public static File MAGIC_WHITELIST = null;

    private final List<MagicSignature> signatures;

    static {
        if (Strings.isNullOrEmpty(System.getenv("MAGIC_WHITELIST"))) {
            MAGIC_WHITELIST = new File(Magic.DEFAULT_MAGIC_WHITELIST);
        } else {
            MAGIC_WHITELIST = new File(System.getenv("MAGIC_WHITELIST"));
        }
    }

    private static Magic instance = null;

    private Magic(final List<MagicSignature> signatures) {
        this.signatures = signatures;
    }

    public static final synchronized Magic getInstance() {
        if (instance == null) {
            List<MagicSignature> signatures = ImmutableList.of();
            if (!MAGIC_WHITELIST.canRead()) {
                logger.warn("Cannot read {}, falling back to default magic white list", MAGIC_WHITELIST.getAbsoluteFile());
                final URL url = Resources.getResource("default_magic.txt");
                try {
                    final List<String> strings = Resources.readLines(url, Charsets.UTF_8);
                    signatures = fromLines(strings);
                } catch (IOException e) {
                    logger.error("Unable to read default magic whitelist",e);
                }
            } else {
                signatures = fromFile(MAGIC_WHITELIST);
            }
            instance = new Magic(signatures);
        }
        return instance;
    }

    public boolean whiteListed(final byte[] data) {
        for (MagicSignature signature : signatures) {
            if (signature.match(data)) return true;
        }
        return false;
    }

    public static final List<MagicSignature> fromFile(final File file) {
        ImmutableList.Builder<MagicSignature> builder = ImmutableList.builder();
        try {
            final List<String> strings = Files.readLines(file, Charsets.UTF_8);
            return fromLines(strings);
        } catch (IOException e) {
            logger.error("Unable to read magic whitelist",e);
            return ImmutableList.of();
        }
    }

    public static final List<MagicSignature> fromLines(final List<String> lines) {
        int count = 0;
        ImmutableList.Builder<MagicSignature> builder = ImmutableList.builder();
        for (String line: lines) {
            final Optional<MagicSignature> magicSignature = MagicSignature.fromString(line);
            if (magicSignature.isPresent()) {
                builder.add(magicSignature.get());
                count = count + 1;
            }
        }
        logger.info("Loaded {} magic signatures", count);
        return builder.build();
    }

}
