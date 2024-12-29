package org.xiphis.test;

import org.junit.Assert;
import org.junit.Test;
import org.xiphis.collection.MultiMap;
import org.xiphis.collection.SinisaHashMap;

public class TestSinisaHashMap {
    @Test
    public void basicTest() {
        MultiMap<String, String> test = new SinisaHashMap<>();

        Assert.assertNull(test.remove("foo"));

        test.put("key1", "value1");
        test.put("key2", "value2");
        test.put("key3", "value3");
        test.put("key4", "value4");
        test.put("key5", "value5");
        test.put("key6", "value6");

        Assert.assertEquals(0, SinisaHashMap.check(test));

        Assert.assertEquals(6, test.size());
        Assert.assertEquals("value1", test.get("key1"));
        Assert.assertEquals("value2", test.get("key2"));
        Assert.assertEquals("value3", test.get("key3"));
        Assert.assertEquals("value4", test.get("key4"));
        Assert.assertEquals("value5", test.get("key5"));
        Assert.assertEquals("value6", test.get("key6"));

        Assert.assertEquals("value4", test.remove("key4"));
        Assert.assertEquals(0, SinisaHashMap.check(test));
        Assert.assertEquals(5, test.size());

        Assert.assertEquals("value1", test.get("key1"));
        Assert.assertEquals("value2", test.get("key2"));
        Assert.assertEquals("value3", test.get("key3"));
        Assert.assertEquals("value5", test.get("key5"));
        Assert.assertEquals("value6", test.get("key6"));

        Assert.assertArrayEquals(new String[0], test.getAll("key4").toArray(new String[0]));
        Assert.assertFalse(test.containsKey("key4"));
        Assert.assertTrue(test.containsKey("key5"));

        Assert.assertArrayEquals(new String[] { "value3" }, test.getAll("key3").toArray(new String[0]));

        Assert.assertEquals("value3", test.put("key3", "value3a"));
        Assert.assertEquals(0, SinisaHashMap.check(test));

        Assert.assertArrayEquals(new String[] { "value3a" }, test.getAll("key3").toArray(new String[0]));

        test.add("key3", "value3b");
        Assert.assertEquals(0, SinisaHashMap.check(test));

        Assert.assertArrayEquals(new String[] { "value3a", "value3b" }, test.getAll("key3").toArray(new String[0]));

        Assert.assertNull(test.put("key3", "value3c"));
        Assert.assertEquals(0, SinisaHashMap.check(test));

        Assert.assertArrayEquals(new String[] { "value3a", "value3b", "value3c" }, test.getAll("key3").toArray(new String[0]));

        Assert.assertEquals("value1", test.get("key1"));
        Assert.assertEquals("value2", test.get("key2"));
        Assert.assertEquals("value5", test.get("key5"));
        Assert.assertEquals("value6", test.get("key6"));

        Assert.assertEquals("value3c", test.remove("key3"));
        Assert.assertEquals(0, SinisaHashMap.check(test));
        Assert.assertEquals("value3b", test.remove("key3"));
        Assert.assertEquals(0, SinisaHashMap.check(test));
        Assert.assertEquals("value3a", test.remove("key3"));
        Assert.assertEquals(0, SinisaHashMap.check(test));
        Assert.assertNull(test.remove("key3"));

    }
}

