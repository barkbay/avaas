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

package avaas.clamav.rest;

import avaas.clamav.client.ClamAVClient;
import avaas.magic.Magic;
import com.google.common.base.Strings;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
public class ClamAVProxy {

    private static final Logger logger = LoggerFactory.getLogger(ClamAVProxy.class);

    private static final Pattern clamdResponsePattern = Pattern.compile("^.* (.*) FOUND.*$");

    private static final Magic magic = Magic.getInstance();

    private final CounterService globalCounterService;
    private final CounterService blacklistedCounterService;
    private final CounterService infectedCounterService;

    @Autowired
    public ClamAVProxy(CounterService globalCounterService,
                       CounterService blacklistedCounterService,
                       CounterService infectedCounterService) {
        this.globalCounterService = globalCounterService;
        this.blacklistedCounterService = blacklistedCounterService;
        this.infectedCounterService = infectedCounterService;
    }

    @Value("${clamd.host}")
    private String hostname;

    @Value("${clamd.port}")
    private int port;

    @Value("${clamd.timeout}")
    private int timeout;

    public static class ClamAVResponse {

        public enum InfectionState  { yes, no, ignore}

        private final InfectionState infected;
        private final boolean blacklist;
        private final String rawReply;
        private final String signature;
        private final String filename;
        private final String sha256;
        private final long filesize;
        private final boolean unsafe;

        public long getDuration() {
            return duration;
        }

        private final long duration;

        public boolean isBlacklist() {
            return blacklist;
        }

        public boolean isUnsafe() {
            return unsafe;
        }

        public long getFilesize() {
            return filesize;
        }

        public InfectionState getInfected() {
            return infected;
        }

        public String getRawReply() {
            return rawReply;
        }

        public String getFilename() {
            return filename;
        }

        public String getSignature() {
            return signature;
        }

        public String getSha256() {
            return sha256;
        }

        public ClamAVResponse(boolean blacklist, boolean unsafe, InfectionState infected,
                              String rawReply, String signature,
                              String filename, String sha256, long filesize, long duration) {
            this.blacklist = blacklist;
            this.unsafe = unsafe;
            this.infected = infected;
            this.rawReply = rawReply;
            this.signature = signature;
            this.filename = filename;
            this.sha256 = sha256;
            this.filesize = filesize;
            this.duration = duration;
        }

    }

    /**
     * @return Clamd status.
     */
    @RequestMapping(value = "/api/v1", method = RequestMethod.GET)
    public String ping() throws IOException {
        ClamAVClient a = new ClamAVClient(hostname, port, timeout);
        logger.info("Clamd response is {}", a.ping());
        return "Clamd responding: " + a.ping() + "\n";
    }

    /**
     * @return Clamd scan result
     */
    @RequestMapping(value = "/api/v1/scan", method = RequestMethod.POST)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success", response = ClamAVResponse.class),
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 500, message = "Failure")})
    public @ResponseBody
    ClamAVResponse handleFileUpload(@RequestParam("name") String name,
                                    @RequestParam("file") MultipartFile file)
            throws IOException {
        globalCounterService.increment("avaas.scan.call");
        MDC.clear();
        if (Strings.isNullOrEmpty(name)) throw new IllegalArgumentException("name parameter is empty or missing");
        if (!file.isEmpty()) {
            MDC.put("filename", file.getOriginalFilename());
            MDC.put("filesize", String.valueOf(file.getSize()));
            MDC.put("action", "scan");
            final String sha256 = sha256(file);
            MDC.put("sha256", sha256);
            long startTime = System.currentTimeMillis();

            if (!magic.whiteListed(file.getBytes())) {
                logger.info("scan blacklist");
                blacklistedCounterService.increment("avaas.scan.blacklisted");
                return new ClamAVResponse(true, true, ClamAVResponse.InfectionState.ignore, "", "",
                        file.getOriginalFilename(), sha256, file.getSize(), 0L);
            }

            ClamAVClient a = new ClamAVClient(hostname, port, timeout);
            byte[] replyAsBytes = this.scan(file, a);
            String replyAsString = new String(replyAsBytes, StandardCharsets.US_ASCII);
            long duration = (System.currentTimeMillis() - startTime);
            MDC.put("duration", String.valueOf(duration));
            final ClamAVResponse svcReponse;
            if (replyAsString.contains("OK") && !replyAsString.contains("FOUND")) {
                logger.info("negative scan");
                svcReponse = new ClamAVResponse(false, false, ClamAVResponse.InfectionState.no, replyAsString, "",
                                                file.getOriginalFilename(), sha256, file.getSize(), duration);
            } else {
                infectedCounterService.increment("avaas.scan.infected");
                final String signature = getSignatureNameFromReply(replyAsString);
                MDC.put("signature", signature);
                logger.warn("positive scan");
                svcReponse = new ClamAVResponse(false, true, ClamAVResponse.InfectionState.yes, replyAsString, signature,
                                                file.getOriginalFilename(), sha256, file.getSize(), duration);
            }
            MDC.clear();
            return svcReponse;
        } else throw new IllegalArgumentException("empty file");
    }

    private String getSignatureNameFromReply(final String reply) {
        final Matcher match = clamdResponsePattern.matcher(reply);
        if (match.find()) {
            return match.group(1);
        } else {
            return "";
        }
    }

    private String sha256(final MultipartFile file) {
        try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(file.getBytes());
        return bytesToHex(hash);
        } catch (Exception e) {
            logger.error("Ooops can't compute sha256", e);
            return "";
        }
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private final String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private final byte[] scan(final MultipartFile file, final ClamAVClient a) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return a.scan(is);
        }
    }
}
