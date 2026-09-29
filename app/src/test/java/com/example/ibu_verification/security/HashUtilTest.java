package com.example.ibu_verification.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class HashUtilTest {

    private static final String PEPPER = "test-pepper-value";

    @Test
    public void certIdHashIsDeterministic() {
        assertEquals(HashUtil.certIdHash("CER1IBAU0001", PEPPER),
                HashUtil.certIdHash("CER1IBAU0001", PEPPER));
    }

    @Test
    public void certIdHashIs64LowercaseHexCharacters() {
        String hash = HashUtil.certIdHash("CER1IBAU0001", PEPPER);
        assertEquals(64, hash.length());
        assertEquals(hash.toLowerCase(), hash);
    }

    @Test
    public void certIdHashChangesWithPepper() {
        assertNotEquals(HashUtil.certIdHash("CER1IBAU0001", "pepper-a"),
                HashUtil.certIdHash("CER1IBAU0001", "pepper-b"));
    }

    @Test
    public void certIdHashChangesWithId() {
        assertNotEquals(HashUtil.certIdHash("CER1IBAU0001", PEPPER),
                HashUtil.certIdHash("CER1IBAU0002", PEPPER));
    }
}
