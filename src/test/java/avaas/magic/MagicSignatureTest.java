package avaas.magic;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Created by morello-08393 on 21/05/17.
 */
public class MagicSignatureTest {

    @Test
    public void fromString() throws Exception {
        final Optional<MagicSignature> magicSignature = MagicSignature.fromString("0, CAFEBABE00");
        assertTrue(magicSignature.isPresent());
        final MagicSignature sig = magicSignature.get();
        final byte[] expected = new byte[] { (byte)0xca, (byte)0xfe, (byte)0xba, (byte)0xbe, (byte)0x00 };
        assertArrayEquals(expected, sig.getBytes());
        assertEquals(0, sig.getOffset());
    }

    @Test
    public void fromString2() throws Exception {
        final Optional<MagicSignature> magicSignature = MagicSignature.fromString("5, CA FE BA BE 00");
        assertTrue(magicSignature.isPresent());
        final MagicSignature sig = magicSignature.get();
        final byte[] expected = new byte[] { (byte)0xca, (byte)0xfe, (byte)0xba, (byte)0xbe, (byte)0x00 };
        assertArrayEquals(expected, sig.getBytes());
        assertEquals(5, sig.getOffset());
    }


    @Test
    public void fromStringNull() throws Exception {
        final Optional<MagicSignature> magicSignature = MagicSignature.fromString("#0, CAFEBABE00");
        assertFalse(magicSignature.isPresent());
    }

}