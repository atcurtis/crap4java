package org.xiphis.text;

import org.junit.Assert;
import org.junit.Test;

import java.util.*;

/**
 * Taking test examples from:
 * https://android.googlesource.com/platform/libcore/+/d5e2817/luni/src/test/java/tests/api/java/util/FormatterTest.java
 */
public class TestFormatter {
    @Test
    public void test001() {
        String template = "%4$2s %3$2s %2$2s %1$2s";
        Formatter formatter = new Formatter(Locale.US, template);
        StringBuilder sb = new StringBuilder();
        Assert.assertEquals(jufFormat(formatter.locale(), template, "a", "b", "c", "d"),
                formatter.formatTo(sb, "a", "b", "c", "d").toString());
    }

    @Test
    public void test002() {
        String template = "e = %+10.4f";
        Formatter formatter = new Formatter(Locale.FRANCE, template);
        Assert.assertEquals(jufFormat(formatter.locale(), template, Math.E),
                formatter.format(Math.E).toString());
    }

    @Test
    public void test003() {
        String template = "Amount gained or lost since last statement: $ %(,.2f";

        Formatter formatter = new Formatter(template);
        Assert.assertEquals(jufFormat(formatter.locale(), template, -6217.58),
                formatter.format(-6217.58).toString());
    }

    @Test
    public void test004() {
        String template = "Local time: %tT";

        Calendar cal = Calendar.getInstance();
        Formatter formatter = new Formatter(template);
        Assert.assertEquals(jufFormat(formatter.locale(), template, cal),
                formatter.format(cal).toString());
    }

    @Test
    public void test005() {
        String template = "Duke's Birthday: %1$tb %1$te, %1$tY";

        Calendar c = new GregorianCalendar(1995, Calendar.MAY, 23);
        Formatter formatter = new Formatter(template);
        Assert.assertEquals(jufFormat(formatter.locale(), template, c),
                formatter.format(c).toString());
    }

    @Test
    public void test006() {
        String template = "%1$s%2$s%3$s%4$s%5$s%6$s%7$s%8$s%9$s%11$s%10$s";

        Formatter formatter = new Formatter(Locale.US, template);
        Assert.assertEquals(jufFormat(formatter.locale(), template, "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"),
                formatter.format("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11").toString());
    }

    //@Test
    public void test007() {
        String template = "%0$s";

        Formatter formatter = new Formatter(Locale.JAPAN, template);
        Assert.assertEquals(jufFormat(formatter.locale(), template, "hello"),
                formatter.format("hello").toString());
    }

    @Test
    public void test008() {
        String template = "%-1$s";

        Assert.assertThrows(UnknownFormatConversionException.class, () -> {
          Formatter formatter = new Formatter(Locale.US, template);
          formatter.format("1", "2");
        });
    }

    @Test
    public void test009() {
        String template = "%$s";

        Assert.assertThrows(UnknownFormatConversionException.class, () -> {
            Formatter formatter = new Formatter(Locale.US, template);
            formatter.format("hello", "2");
        });
    }

    @Test
    public void test010() {
        String template = "%";

        Assert.assertThrows(UnknownFormatConversionException.class, () -> {
            Formatter formatter = new Formatter(Locale.US, template);
            formatter.format("string");
        });
    }

    @Test
    public void test011() {
        String template = "%1$s%2$s%3$s%4$s%5$s%6$s%7$s%8$s%<s%s%s%<s";

        Formatter formatter = new Formatter(Locale.FRANCE, template);
        Assert.assertEquals(jufFormat(formatter.locale(), template,  "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"),
                formatter.format( "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11").toString());
    }

    @Test
    public void test012() {
        String template = "xx%1$s22%2$s%s%<s%5$s%<s&%7$h%2$s%8$s%<s%s%s%<ssuffix";

        Formatter formatter = new Formatter(Locale.FRANCE, template);
        Assert.assertEquals(jufFormat(formatter.locale(), template,  "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"),
                formatter.format( "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11").toString());
    }

    @Test
    public void test013() {
        String template = "%<s";

        Assert.assertThrows(MissingFormatArgumentException.class, () -> {
            Formatter formatter = new Formatter(Locale.US, template);
            formatter.format("hello").toString();
        });
    }

    @Test
    public void test014() {
        String template = "%123$s";

        Assert.assertThrows(MissingFormatArgumentException.class, () -> {
            Formatter formatter = new Formatter(Locale.US, template);
            formatter.format("hello").toString();
        });
    }

    @Test
    public void test015() {
        String template = "%2147483648$s"; // 2147483648 is the value of Integer.MAX_VALUE + 1

        Assert.assertThrows(IllegalFormatArgumentIndexException.class, () -> {
            Formatter formatter = new Formatter(Locale.US, template);
            formatter.format("hello").toString();
        });
    }

    @Test
    public void test016() {
        String template = "%2147483647$s"; // 2147483647 is the value of Integer.MAX_VALUE

        Assert.assertThrows(MissingFormatArgumentException.class, () -> {
            Formatter formatter = new Formatter(Locale.US, template);
            formatter.format("hello").toString();
        });
    }

    @Test
    public void test017() {
        String template = "%s%s";

        Assert.assertThrows(MissingFormatArgumentException.class, () -> {
            Formatter formatter = new Formatter(Locale.US, template);
            formatter.format("hello").toString();
        });
    }

    @Test
    public void test018() {
        String template = "$100";

        Formatter formatter = new Formatter(Locale.US, template);
        Assert.assertEquals(jufFormat(formatter.locale(), template,  100),
                formatter.format( 100).toString());
    }

    @Test
    public void test019() {
        String template = "%01$s";

        Formatter formatter = new Formatter(Locale.US, template);
        Assert.assertEquals(jufFormat(formatter.locale(), template,  "string"),
                formatter.format( "string").toString());
    }

    @Test
    public void test020() {
        Formatter f = new Formatter(Locale.US, "%1$8s");
        Assert.assertEquals("       1", f.format("1").toString());

        f = new Formatter(Locale.US, "%1$-1%");
        Assert.assertEquals("%", f.format("string").toString());

        //f = new Formatter(Locale.ITALY, "%2147483648s");
        //Assert.assertEquals("string", f.format("string").toString());
    }

    @Test
    public void test021() {
        equals(Locale.US, "%.5s", "123456");
    }

    @Test
    public void test022() {
        Assert.assertThrows(IllegalFormatPrecisionException.class, () -> new Formatter(Locale.US, "%.2147483648s"));
    }

    @Test
    public void test024() {
        equals(Locale.US, "%10.0b", Boolean.TRUE);
    }

    @Test
    public void test025() {
        equals(Locale.US, "%10.01s", "hello");
    }

    @Test
    public void test026() {
        Assert.assertThrows(UnknownFormatConversionException.class, () -> new Formatter(Locale.US, "%.s"));
    }

    @Test
    public void test027() {
        Assert.assertThrows(UnknownFormatConversionException.class, () -> new Formatter(Locale.US, "%.-5s"));
    }

    @Test
    public void test028() {
        Assert.assertThrows(UnknownFormatConversionException.class, () -> new Formatter(Locale.US, "%1.s"));
    }

    @Test
    public void test029() {
        equals(Locale.US, "%5.1s", "hello");
    }

    @Test
    public void test030() {
        equals(Locale.FRANCE, "%.0s", "hello", "2");
    }

    @Test
    public void test031() {
        String oldSeparator = System.getProperty("line.separator");
        try {
            System.setProperty("line.separator", "!\n");
            equals(Locale.US, "%1$n", 1);
            equals(Locale.KOREAN, "head%1$n%2$n", 1, new Date());
            equals(Locale.US, "%n%s", "hello");
        } finally {
            System.setProperty("line.separator", oldSeparator);
        }
    }

    @Test
    public void test032() {
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%-n"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%+n"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%#n"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "% n"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%0n"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%,n"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%(n"));
        Assert.assertThrows(IllegalFormatWidthException.class, () -> new Formatter(Locale.US, "%4n"));
        Assert.assertThrows(IllegalFormatWidthException.class, () -> new Formatter(Locale.US, "%-4n"));
        Assert.assertThrows(IllegalFormatPrecisionException.class, () -> new Formatter(Locale.US, "%.9n"));
        Assert.assertThrows(IllegalFormatPrecisionException.class, () -> new Formatter(Locale.US, "%5.9n"));
    }

    @Test
    public void test033() {
        equals(Locale.ENGLISH, "%1$%", 100);
    }

    @Test
    public void test034() {
        equals(Locale.CHINA, "%1$%%%", "hello", new Object());
    }

    @Test
    public void test035() {
        equals(Locale.CHINA, "%%%s", "hello");
    }

    @Test
    public void test036() {
        Assert.assertThrows(IllegalFormatPrecisionException.class, () -> new Formatter(Locale.US, "%.9%"));
        Assert.assertThrows(IllegalFormatPrecisionException.class, () -> new Formatter(Locale.US, "%5.9%"));

        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%+%"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%#%"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "% %"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%0%"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%,%"));
        Assert.assertThrows(IllegalFormatFlagsException.class, () -> new Formatter(Locale.US, "%(%"));
    }

    //@Test
    public void test037() {
        equals(Locale.KOREAN, "%4%", 1);
        equals(Locale.US, "%-4%", 100);
    }

    @Test
    public void test038() {
        Assert.assertThrows(DuplicateFormatFlagsException.class, () -> new Formatter(Locale.US, "%1$-#-8s"));
    }

    @Test
    public void test039() {
        final char[] chars = { '-', '#', '+', ' ', '0', ',', '(', '%', '<' };
        Arrays.sort(chars);
        for (char i = 0; i <= 256; i++) {
            // test 8 bit character
            if (Arrays.binarySearch(chars, i) >= 0 || Character.isDigit(i)
                    || Character.isLetter(i)) {
                // Do not test 0-9, a-z, A-Z and characters in the chars array.
                // They are characters used as flags, width or conversions
                continue;
            }
            final String fmt = "%" + i + "s";
            Assert.assertThrows(UnknownFormatConversionException.class, () -> new Formatter(Locale.US, fmt));
        }
    }

    @SuppressWarnings({"removal", "UnnecessaryBoxing", "CachedNumberConstructorCall"})
    @Test
    public void test040() {
        final Object[][] triple = {
                { Boolean.FALSE, "%3.2b", " fa", },
                { Boolean.FALSE, "%-4.6b", "false", },
                { Boolean.FALSE, "%.2b", "fa", },
                { Boolean.TRUE, "%3.2b", " tr", },
                { Boolean.TRUE, "%-4.6b", "true", },
                { Boolean.TRUE, "%.2b", "tr", },
                { new Character('c'), "%3.2b", " tr", },
                { new Character('c'), "%-4.6b", "true", },
                { new Character('c'), "%.2b", "tr", },
                { new Byte((byte) 0x01), "%3.2b", " tr", },
                { new Byte((byte) 0x01), "%-4.6b", "true", },
                { new Byte((byte) 0x01), "%.2b", "tr", },
                { new Short((short) 0x0001), "%3.2b", " tr", },
                { new Short((short) 0x0001), "%-4.6b", "true", },
                { new Short((short) 0x0001), "%.2b", "tr", },
                { new Integer(1), "%3.2b", " tr", },
                { new Integer(1), "%-4.6b", "true", },
                { new Integer(1), "%.2b", "tr", },
                { new Float(1.1f), "%3.2b", " tr", },
                { new Float(1.1f), "%-4.6b", "true", },
                { new Float(1.1f), "%.2b", "tr", },
                { new Double(1.1d), "%3.2b", " tr", },
                { new Double(1.1d), "%-4.6b", "true", },
                { new Double(1.1d), "%.2b", "tr", },
                { "", "%3.2b", " tr", },
                { "", "%-4.6b", "true", },
                { "", "%.2b", "tr", },
                { "string content", "%3.2b", " tr", },
                { "string content", "%-4.6b", "true", },
                { "string content", "%.2b", "tr", },
                //{ new MockFormattable(), "%3.2b", " tr", },
                //{ new MockFormattable(), "%-4.6b", "true", },
                //{ new MockFormattable(), "%.2b", "tr", },
                { (Object) null, "%3.2b", " fa", },
                { (Object) null, "%-4.6b", "false", },
                { (Object) null, "%.2b", "fa", },
        };
        final int input = 0;
        final int pattern = 1;
        final int output = 2;
        Formatter f = null;
        for (int i = 0; i < triple.length; i++) {
            f = new Formatter(Locale.FRANCE, triple[i][pattern].toString());
            //f = new Formatter(Locale.FRANCE);
            //f.format((String) triple[i][pattern], triple[i][input]);
            Assert.assertEquals("triple[" + i + "]:" + triple[i][input]
                    + ",pattern[" + i + "]:" + triple[i][pattern], triple[i][output],
                    f.format(triple[i][input]).toString());

            f = new Formatter(Locale.GERMAN, triple[i][pattern].toString().toUpperCase(Locale.US));
            //f = new Formatter(Locale.FRANCE);
            //f.format((String) triple[i][pattern], triple[i][input]);
            Assert.assertEquals("triple[" + i + "]:" + triple[i][input]
                            + ",pattern[" + i + "]:" + triple[i][pattern], triple[i][output]
                            .toString().toUpperCase(Locale.US),
                    f.format(triple[i][input]).toString());
        }
    }

    @SuppressWarnings({"removal", "UnnecessaryBoxing", "CachedNumberConstructorCall"})
    @Test
    public void test041() {
        final Object[][] triple = {
                { Boolean.FALSE, "%2.3s", "fal", },
                { Boolean.FALSE, "%-6.4s", "fals  ", },
                { Boolean.FALSE, "%.5s", "false", },
                { Boolean.TRUE, "%2.3s", "tru", },
                { Boolean.TRUE, "%-6.4s", "true  ", },
                { Boolean.TRUE, "%.5s", "true", },
                { new Character('c'), "%2.3s", " c", },
                { new Character('c'), "%-6.4s", "c     ", },
                { new Character('c'), "%.5s", "c", },
                { new Byte((byte) 0x01), "%2.3s", " 1", },
                { new Byte((byte) 0x01), "%-6.4s", "1     ", },
                { new Byte((byte) 0x01), "%.5s", "1", },
                { new Short((short) 0x0001), "%2.3s", " 1", },
                { new Short((short) 0x0001), "%-6.4s", "1     ", },
                { new Short((short) 0x0001), "%.5s", "1", },
                { new Integer(1), "%2.3s", " 1", },
                { new Integer(1), "%-6.4s", "1     ", },
                { new Integer(1), "%.5s", "1", },
                { new Float(1.1f), "%2.3s", "1.1", },
                { new Float(1.1f), "%-6.4s", "1.1   ", },
                { new Float(1.1f), "%.5s", "1.1", },
                { new Double(1.1d), "%2.3s", "1.1", },
                { new Double(1.1d), "%-6.4s", "1.1   ", },
                { new Double(1.1d), "%.5s", "1.1", },
                { "", "%2.3s", "  ", },
                { "", "%-6.4s", "      ", },
                { "", "%.5s", "", },
                { "string content", "%2.3s", "str", },
                { "string content", "%-6.4s", "stri  ", },
                { "string content", "%.5s", "strin", },
                //{ new MockFormattable(), "%2.3s", "customized format function width: 2 precision: 3", },
                //{ new MockFormattable(), "%-6.4s", "customized format function width: 6 precision: 4", },
                //{ new MockFormattable(), "%.5s", "customized format function width: -1 precision: 5", },
                { (Object) null, "%2.3s", "nul", },
                { (Object) null, "%-6.4s", "null  ", },
                { (Object) null, "%.5s", "null", },
        };
        final int input = 0;
        final int pattern = 1;
        final int output = 2;
        Formatter f = null;
        for (int i = 0; i < triple.length; i++) {
            f = new Formatter(Locale.FRANCE, triple[i][pattern].toString());
            //f = new Formatter(Locale.FRANCE);
            //f.format((String) triple[i][pattern], triple[i][input]);
            Assert.assertEquals("triple[" + i + "]:" + triple[i][input]
                            + ",pattern[" + i + "]:" + triple[i][pattern], triple[i][output],
                    f.format(triple[i][input]).toString());

            f = new Formatter(Locale.GERMAN, triple[i][pattern].toString().toUpperCase(Locale.US));
            //f = new Formatter(Locale.FRANCE);
            //f.format((String) triple[i][pattern], triple[i][input]);
            Assert.assertEquals("triple[" + i + "]:" + triple[i][input]
                            + ",pattern[" + i + "]:" + triple[i][pattern], triple[i][output]
                            .toString().toUpperCase(Locale.US),
                    f.format(triple[i][input]).toString());
        }
    }

    @SuppressWarnings({"removal", "UnnecessaryBoxing", "CachedNumberConstructorCall"})
    @Test
    public void test042() {
        final Object[] input = {
                Boolean.FALSE,
                Boolean.TRUE,
                new Character('c'),
                new Byte((byte) 0x01),
                new Short((short) 0x0001),
                new Integer(1),
                new Float(1.1f),
                new Double(1.1d),
                "",
                "string content",
                //new MockFormattable(),
                (Object) null,
        };
        Formatter f = null;
        for (int i = 0; i < input.length - 1; i++) {
            f = new Formatter(Locale.FRANCE, "%h");
            Assert.assertEquals("triple[" + i + "]:" + input[i],
                    Integer.toHexString(input[i].hashCode()),
                    f.format(input[i]).toString());
            f = new Formatter(Locale.GERMAN, "%H");
            Assert.assertEquals("triple[" + i + "]:" + input[i],
                    Integer.toHexString(input[i].hashCode()).toUpperCase(Locale.US),
                    f.format(input[i]).toString());
        }
    }

    @SuppressWarnings({"removal", "UnnecessaryBoxing", "CachedNumberConstructorCall"})
    //@Test
    public void test043() {
        /*
         * In Turkish locale, the upper case of '\u0069' is '\u0130'. The
         * following test indicate that '\u0069' is coverted to upper case
         * without using the turkish locale.
         */
        Formatter f = new Formatter(new Locale("tr"), "%S");
        Assert.assertEquals("\u0049", f.format("\u0069").toString());
        final Object[] input = {
                Boolean.FALSE,
                Boolean.TRUE,
                new Character('c'),
                new Byte((byte) 0x01),
                new Short((short) 0x0001),
                new Integer(1),
                new Float(1.1f),
                new Double(1.1d),
                "",
                "string content",
                //new MockFormattable(),
                (Object) null,
        };
        Formatter fmt = new Formatter(Locale.GERMAN, "%#s");
        for (int i = 0; i < input.length; i++) {
            if (!(input[i] instanceof Formattable)) {
                final Object arg = input[i];
                Assert.assertThrows(FormatFlagsConversionMismatchException.class, () -> fmt.format(arg).toString());
            } else {
                Assert.assertEquals("customized format function width: -1 precision: -1customized format function width: 8 precision: -1",
                        fmt.format(input[i]).toString());

                Formatter fmt2 = new Formatter(fmt.locale(), "%#s%<-#8s");
                Assert.assertEquals("customized format function width: -1 precision: -1customized format function width: 8 precision: -1",
                        f.format(input[i]).toString());
            }
        }
    }

    @Test
    public void test044() {
        final String[] flagMismatch = { "%#b", "%+b", "% b", "%0b", "%,b",
                "%(b", "%#B", "%+B", "% B", "%0B", "%,B", "%(B", "%#h", "%+h",
                "% h", "%0h", "%,h", "%(h", "%#H", "%+H", "% H", "%0H", "%,H",
                "%(H", "%+s", "% s", "%0s", "%,s", "%(s", "%+S", "% S", "%0S",
                "%,S", "%(S" };
        for (int i = 0; i < flagMismatch.length; i++) {
            String fmt = flagMismatch[i];
            Assert.assertThrows(FormatFlagsConversionMismatchException.class,
                    () -> new Formatter(Locale.US, fmt));
        }
        final String[] missingWidth = { "%-b", "%-B", "%-h", "%-H", "%-s",
                "%-S", };
        for (int i = 0; i < missingWidth.length; i++) {
            String fmt = missingWidth[i];
            Assert.assertThrows(MissingFormatWidthException.class,
                    (() -> new Formatter(Locale.US, fmt)));
        }
        // Regression test
        {
            Formatter f = Formatter.of("%c");
            Assert.assertThrows(IllegalFormatCodePointException.class, () -> f.format((byte) -0x0001).toString());
            Assert.assertThrows(IllegalFormatCodePointException.class, () -> f.format((short) -0x0001).toString());
            Assert.assertThrows(IllegalFormatCodePointException.class, () -> f.format(-0x0001).toString());
        }
    }

    @SuppressWarnings({"removal", "UnnecessaryBoxing", "CachedNumberConstructorCall"})
    @Test
    public void test045() {
        Formatter f = new Formatter(Locale.US, "%c");
        final Object[] illArgs = { Boolean.TRUE, new Float(1.1f),
                new Double(1.1d), "string content", new Float(1.1f), new Date() };
        for (int i = 0; i < illArgs.length; i++) {
            Object arg = illArgs[i];
            Assert.assertThrows(IllegalFormatConversionException.class, () -> f.format(arg).toString());
        }

        Assert.assertThrows(IllegalFormatCodePointException.class,
                () -> f.format(Integer.MAX_VALUE).toString());
    }

    @Test
    public void test046() {
        Assert.assertThrows(FormatFlagsConversionMismatchException.class,
                () -> new Formatter(Locale.US, "%#c"));
    }

    @Test
    public void test047() {
        final Object[][] triple = {
                { 'c', "%c", "c" },
                { 'c', "%-2c", "c " },
                { '\u0123', "%c", "\u0123" },
                { '\u0123', "%-2c", "\u0123 " },
                { (byte) 0x11, "%c", "\u0011" },
                { (byte) 0x11, "%-2c", "\u0011 " },
                { (short) 0x1111, "%c", "\u1111" },
                { (short) 0x1111, "%-2c", "\u1111 " },
                { 0x11, "%c", "\u0011" },
                { 0x11, "%-2c", "\u0011 " },
        };
        final int input = 0;
        final int pattern = 1;
        final int output = 2;
        for (int i = 0; i < triple.length; i++) {
            Formatter f = new Formatter(Locale.US, (String) triple[i][pattern]);
            Assert.assertEquals(triple[i][output], f.format(triple[i][input]).toString());
        }
    }

    @Test
    public void test048() {
        Formatter f = new Formatter(Locale.US, "%c");
        Assert.assertEquals(0x10000, f.format(0x10000).toString().codePointAt(0));

        Assert.assertThrows(IllegalFormatPrecisionException.class, () -> new Formatter(Locale.US, "%2.2c"));
    }

    @Test
    public void test049() {
        Formatter f = new Formatter(Locale.US, "%C");
        Assert.assertEquals("W", f.format('w').toString());
    }

    @Test
    public void test050() {
        Formatter f = new Formatter(Locale.JAPAN, "%Ced");
        Assert.assertEquals("\u1111ed", f.format(0x1111).toString());
    }

    @Test
    public void test051() {
        final Object[][] triple = {
                { 0, "%d", "0" },
                { 0, "%10d", "         0" },
                { 0, "%-1d", "0" },
                { 0, "%+d", "+0" },
                { 0, "% d", " 0" },
                { 0, "%,d", "0" },
                { 0, "%(d", "0" },
                { 0, "%08d", "00000000" },
                { 0, "%-+,(11d", "+0         " },
                { 0, "%0 ,(11d", " 0000000000" },
                { (byte) 0xff, "%d", "-1" },
                { (byte) 0xff, "%10d", "        -1" },
                { (byte) 0xff, "%-1d", "-1" },
                { (byte) 0xff, "%+d", "-1" },
                { (byte) 0xff, "% d", "-1" },
                { (byte) 0xff, "%,d", "-1" },
                { (byte) 0xff, "%(d", "(1)" },
                { (byte) 0xff, "%08d", "-0000001" },
                { (byte) 0xff, "%-+,(11d", "(1)        " },
                { (byte) 0xff, "%0 ,(11d", "(000000001)" },
                { (short) 0xf123, "%d", "-3805" },
                { (short) 0xf123, "%10d", "     -3805" },
                { (short) 0xf123, "%-1d", "-3805" },
                { (short) 0xf123, "%+d", "-3805" },
                { (short) 0xf123, "% d", "-3805" },
                { (short) 0xf123, "%,d", "-3.805" },
                { (short) 0xf123, "%(d", "(3805)" },
                { (short) 0xf123, "%08d", "-0003805" },
                { (short) 0xf123, "%-+,(11d", "(3.805)    " },
                { (short) 0xf123, "%0 ,(11d", "(00003.805)" },
                { 0x123456, "%d", "1193046" },
                { 0x123456, "%10d", "   1193046" },
                { 0x123456, "%-1d", "1193046" },
                { 0x123456, "%+d", "+1193046" },
                { 0x123456, "% d", " 1193046" },
                { 0x123456, "%,d", "1.193.046" },
                { 0x123456, "%(d", "1193046" },
                { 0x123456, "%08d", "01193046" },
                { 0x123456, "%-+,(11d", "+1.193.046 " },
                { 0x123456, "%0 ,(11d", " 01.193.046" },
                { -3, "%d", "-3" },
                { -3, "%10d", "        -3" },
                { -3, "%-1d", "-3" },
                { -3, "%+d", "-3" },
                { -3, "% d", "-3" },
                { -3, "%,d", "-3" },
                { -3, "%(d", "(3)" },
                { -3, "%08d", "-0000003" },
                { -3, "%-+,(11d", "(3)        " },
                { -3, "%0 ,(11d", "(000000003)" },
                { 0x7654321L, "%d", "124076833" },
                { 0x7654321L, "%10d", " 124076833" },
                { 0x7654321L, "%-1d", "124076833" },
                { 0x7654321L, "%+d", "+124076833" },
                { 0x7654321L, "% d", " 124076833" },
                { 0x7654321L, "%,d", "124.076.833" },
                { 0x7654321L, "%(d", "124076833" },
                { 0x7654321L, "%08d", "124076833" },
                { 0x7654321L, "%-+,(11d", "+124.076.833" },
                { 0x7654321L, "%0 ,(11d", " 124.076.833" },
                { -1L, "%d", "-1" },
                { -1L, "%10d", "        -1" },
                { -1L, "%-1d", "-1" },
                { -1L, "%+d", "-1" },
                { -1L, "% d", "-1" },
                { -1L, "%,d", "-1" },
                { -1L, "%(d", "(1)" },
                { -1L, "%08d", "-0000001" },
                { -1L, "%-+,(11d", "(1)        " },
                { -1L, "%0 ,(11d", "(000000001)" },
        };
        final int input = 0;
        final int pattern = 1;
        final int output = 2;
        for (int i = 0; i < triple.length; i++) {
            Formatter f = new Formatter(Locale.GERMAN, (String) triple[i][pattern]);
            Assert.assertEquals("triple[" + i + "]:" + triple[i][input] + ",pattern["
                    + i + "]:" + triple[i][pattern], triple[i][output],
                    f.format(triple[i][input]).toString());
        }
    }

    @Test
    public void test052() {
        final Object[][] triple = {
                { 0, "%o", "0" },
                { 0, "%-6o", "0     " },
                { 0, "%08o", "00000000" },
                { 0, "%#o", "00" },
                { 0, "%0#11o", "00000000000" },
                { 0, "%-#9o", "00       " },
                { (byte) 0xff, "%o", "377" },
                { (byte) 0xff, "%-6o", "377   " },
                { (byte) 0xff, "%08o", "00000377" },
                { (byte) 0xff, "%#o", "0377" },
                { (byte) 0xff, "%0#11o", "00000000377" },
                { (byte) 0xff, "%-#9o", "0377     " },
                { (short) 0xf123, "%o", "170443" },
                { (short) 0xf123, "%-6o", "170443" },
                { (short) 0xf123, "%08o", "00170443" },
                { (short) 0xf123, "%#o", "0170443" },
                { (short) 0xf123, "%0#11o", "00000170443" },
                { (short) 0xf123, "%-#9o", "0170443  " },
                { 0x123456, "%o", "4432126" },
                { 0x123456, "%-6o", "4432126" },
                { 0x123456, "%08o", "04432126" },
                { 0x123456, "%#o", "04432126" },
                { 0x123456, "%0#11o", "00004432126" },
                { 0x123456, "%-#9o", "04432126 " },
                { -3, "%o", "37777777775" },
                { -3, "%-6o", "37777777775" },
                { -3, "%08o", "37777777775" },
                { -3, "%#o", "037777777775" },
                { -3, "%0#11o", "037777777775" },
                { -3, "%-#9o", "037777777775" },
                { 0x7654321L, "%o", "731241441" },
                { 0x7654321L, "%-6o", "731241441" },
                { 0x7654321L, "%08o", "731241441" },
                { 0x7654321L, "%#o", "0731241441" },
                { 0x7654321L, "%0#11o", "00731241441" },
                { 0x7654321L, "%-#9o", "0731241441" },
                { -1L, "%o", "1777777777777777777777" },
                { -1L, "%-6o", "1777777777777777777777" },
                { -1L, "%08o", "1777777777777777777777" },
                { -1L, "%#o", "01777777777777777777777" },
                { -1L, "%0#11o", "01777777777777777777777" },
                { -1L, "%-#9o", "01777777777777777777777" },
        };
        final int input = 0;
        final int pattern = 1;
        final int output = 2;
        for (int i = 0; i < triple.length; i++) {
            Formatter f = new Formatter(Locale.ITALY, (String) triple[i][pattern]);
            Assert.assertEquals("triple[" + i + "]:" + triple[i][input] + ",pattern["
                    + i + "]:" + triple[i][pattern], triple[i][output],
                    f.format(triple[i][input]).toString());
        }
    }

    @Test
    public void test053() {
        final Object[][] triple = {
                { 0, "%x", "0" },
                { 0, "%-8x", "0       " },
                { 0, "%06x", "000000" },
                { 0, "%#x", "0x0" },
                { 0, "%0#12x", "0x0000000000" },
                { 0, "%-#9x", "0x0      " },
                { (byte) 0xff, "%x", "ff" },
                { (byte) 0xff, "%-8x", "ff      " },
                { (byte) 0xff, "%06x", "0000ff" },
                { (byte) 0xff, "%#x", "0xff" },
                { (byte) 0xff, "%0#12x", "0x00000000ff" },
                { (byte) 0xff, "%-#9x", "0xff     " },
                { (short) 0xf123, "%x", "f123" },
                { (short) 0xf123, "%-8x", "f123    " },
                { (short) 0xf123, "%06x", "00f123" },
                { (short) 0xf123, "%#x", "0xf123" },
                { (short) 0xf123, "%0#12x", "0x000000f123" },
                { (short) 0xf123, "%-#9x", "0xf123   " },
                { 0x123456, "%x", "123456" },
                { 0x123456, "%-8x", "123456  " },
                { 0x123456, "%06x", "123456" },
                { 0x123456, "%#x", "0x123456" },
                { 0x123456, "%0#12x", "0x0000123456" },
                { 0x123456, "%-#9x", "0x123456 " },
                { -3, "%x", "fffffffd" },
                { -3, "%-8x", "fffffffd" },
                { -3, "%06x", "fffffffd" },
                { -3, "%#x", "0xfffffffd" },
                { -3, "%0#12x", "0x00fffffffd" },
                { -3, "%-#9x", "0xfffffffd" },
                { 0x7654321L, "%x", "7654321" },
                { 0x7654321L, "%-8x", "7654321 " },
                { 0x7654321L, "%06x", "7654321" },
                { 0x7654321L, "%#x", "0x7654321" },
                { 0x7654321L, "%0#12x", "0x0007654321" },
                { 0x7654321L, "%-#9x", "0x7654321" },
                { -1L, "%x", "ffffffffffffffff" },
                { -1L, "%-8x", "ffffffffffffffff" },
                { -1L, "%06x", "ffffffffffffffff" },
                { -1L, "%#x", "0xffffffffffffffff" },
                { -1L, "%0#12x", "0xffffffffffffffff" },
                { -1L, "%-#9x", "0xffffffffffffffff" },
        };
        final int input = 0;
        final int pattern = 1;
        final int output = 2;
        for (int i = 0; i < triple.length; i++) {
            Formatter f = new Formatter(Locale.FRANCE, (String) triple[i][pattern]);
            Assert.assertEquals("triple[" + i + "]:" + triple[i][input] + ",pattern["
                    + i + "]:" + triple[i][pattern], triple[i][output],
                    f.format(triple[i][input]).toString());
            f = new Formatter(Locale.FRANCE, ((String) triple[i][pattern]).toUpperCase(Locale.US));
            Assert.assertEquals("triple[" + i + "]:" + triple[i][input] + ",pattern["
                    + i + "]:" + triple[i][pattern], triple[i][output].toString().toUpperCase(Locale.US),
                    f.format(triple[i][input]).toString());
        }
    }

    //@Test
    public void test054() {
        Date now = new Date(1147327147578L);
        Calendar paris = Calendar.getInstance(TimeZone
                .getTimeZone("Europe/Paris"), Locale.FRANCE);
        paris.set(2006, 4, 8, 12, 0, 0);
        paris.set(Calendar.MILLISECOND, 453);
        Calendar china = Calendar.getInstance(
                TimeZone.getTimeZone("GMT-08:00"), Locale.CHINA);
        china.set(2006, 4, 8, 12, 0, 0);
        china.set(Calendar.MILLISECOND, 609);
        final Object[][] lowerCaseGermanTriple = {
                { 0L, 'a', "Do." },  //$NON-NLS-2$
                { Long.MAX_VALUE, 'a', "So." },  //$NON-NLS-2$
                { -1000L, 'a', "Do." },  //$NON-NLS-2$
                { new Date(1147327147578L), 'a', "Do." },  //$NON-NLS-2$
                { paris, 'a', "Mo." },  //$NON-NLS-2$
                { china, 'a', "Mo." },  //$NON-NLS-2$
                { 0L, 'b', "Jan" },  //$NON-NLS-2$
                { Long.MAX_VALUE, 'b', "Aug" },  //$NON-NLS-2$
                { -1000L, 'b', "Jan" },  //$NON-NLS-2$
                { new Date(1147327147578L), 'b', "Mai" },  //$NON-NLS-2$
                { paris, 'b', "Mai" },  //$NON-NLS-2$
                { china, 'b', "Mai" },  //$NON-NLS-2$
                { 0L, 'c', "Do. Jan 01 08:00:00 GMT+08:00 1970" },  //$NON-NLS-2$
                { Long.MAX_VALUE, 'c', "So. Aug 17 15:18:47 GMT+08:00 292278994" },  //$NON-NLS-2$
                { -1000L, 'c', "Do. Jan 01 07:59:59 GMT+08:00 1970" },  //$NON-NLS-2$
                { new Date(1147327147578L), 'c', "Do. Mai 11 13:59:07 GMT+08:00 2006" },  //$NON-NLS-2$
                { paris, 'c', "Mo. Mai 08 12:00:00 MESZ 2006" },  //$NON-NLS-2$
                { china, 'c', "Mo. Mai 08 12:00:00 GMT-08:00 2006" },  //$NON-NLS-2$
                { 0L, 'd', "01" },  //$NON-NLS-2$
                { Long.MAX_VALUE, 'd', "17" },  //$NON-NLS-2$
                { -1000L, 'd', "01" },  //$NON-NLS-2$
                { new Date(1147327147578L), 'd', "11" },  //$NON-NLS-2$
                { paris, 'd', "08" },  //$NON-NLS-2$
                { china, 'd', "08" },  //$NON-NLS-2$
                { 0L, 'e', "1" },  //$NON-NLS-2$
                { Long.MAX_VALUE, 'e', "17" },  //$NON-NLS-2$
                { -1000L, 'e', "1" },  //$NON-NLS-2$
                { new Date(1147327147578L), 'e', "11" },  //$NON-NLS-2$
                { paris, 'e', "8" },  //$NON-NLS-2$
                { china, 'e', "8" },  //$NON-NLS-2$
                { 0L, 'h', "Jan" },  //$NON-NLS-2$
                { Long.MAX_VALUE, 'h', "Aug" },  //$NON-NLS-2$
                { -1000L, 'h', "Jan" },  //$NON-NLS-2$
                { new Date(1147327147578L), 'h', "Mai" },  //$NON-NLS-2$
                { paris, 'h', "Mai" },  //$NON-NLS-2$
                { china, 'h', "Mai" },  //$NON-NLS-2$
                { 0L, 'j', "001" },  //$NON-NLS-2$
                { Long.MAX_VALUE, 'j', "229" },  //$NON-NLS-2$
                { -1000L, 'j', "001" },  //$NON-NLS-2$
                { new Date(1147327147578L), 'j', "131" },  //$NON-NLS-2$
                { paris, 'j', "128" },  //$NON-NLS-2$
                { china, 'j', "128" },  //$NON-NLS-2$
                { 0L, 'k', "8" },  //$NON-NLS-2$
                { Long.MAX_VALUE, 'k', "15" },  //$NON-NLS-2$
                { -1000L, 'k', "7" },  //$NON-NLS-2$
                { new Date(1147327147578L), 'k', "13" },  //$NON-NLS-2$
                { paris, 'k', "12" },  //$NON-NLS-2$
                { china, 'k', "12" },  //$NON-NLS-2$
                { 0L, 'l', "8" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'l', "3" }, //$NON-NLS-2$
                { -1000L, 'l', "7" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'l', "1" }, //$NON-NLS-2$
                { paris, 'l', "12" }, //$NON-NLS-2$
                { china, 'l', "12" }, //$NON-NLS-2$
                { 0L, 'm', "01" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'm', "08" }, //$NON-NLS-2$
                { -1000L, 'm', "01" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'm', "05" }, //$NON-NLS-2$
                { paris, 'm', "05" }, //$NON-NLS-2$
                { china, 'm', "05" }, //$NON-NLS-2$
                { 0L, 'p', "vorm." }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'p', "nachm." }, //$NON-NLS-2$
                { -1000L, 'p', "vorm." }, //$NON-NLS-2$
                { new Date(1147327147578L), 'p', "nachm." }, //$NON-NLS-2$
                { paris, 'p', "nachm." }, //$NON-NLS-2$
                { china, 'p', "nachm." }, //$NON-NLS-2$
                { 0L, 'r', "08:00:00 vorm." }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'r', "03:18:47 nachm." }, //$NON-NLS-2$
                { -1000L, 'r', "07:59:59 vorm." }, //$NON-NLS-2$
                { new Date(1147327147578L), 'r', "01:59:07 nachm." }, //$NON-NLS-2$
                { paris, 'r', "12:00:00 nachm." }, //$NON-NLS-2$
                { china, 'r', "12:00:00 nachm." }, //$NON-NLS-2$
                { 0L, 's', "0" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 's', "9223372036854775" }, //$NON-NLS-2$
                { -1000L, 's', "-1" }, //$NON-NLS-2$
                { new Date(1147327147578L), 's', "1147327147" }, //$NON-NLS-2$
                { paris, 's', "1147082400" }, //$NON-NLS-2$
                { china, 's', "1147118400" }, //$NON-NLS-2$
                { 0L, 'y', "70" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'y', "94" }, //$NON-NLS-2$
                { -1000L, 'y', "70" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'y', "06" }, //$NON-NLS-2$
                { paris, 'y', "06" }, //$NON-NLS-2$
                { china, 'y', "06" }, //$NON-NLS-2$
                { 0L, 'z', "+0800" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'z', "+0800" }, //$NON-NLS-2$
                { -1000L, 'z', "+0800" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'z', "+0800" }, //$NON-NLS-2$
                { paris, 'z', "+0100" }, //$NON-NLS-2$
                { china, 'z', "-0800" }, //$NON-NLS-2$
        };
        final Object[][] lowerCaseFranceTriple = {
                { 0L, 'a', "jeu." }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'a', "dim." }, //$NON-NLS-2$
                { -1000L, 'a', "jeu." }, //$NON-NLS-2$
                { new Date(1147327147578L), 'a', "jeu." }, //$NON-NLS-2$
                { paris, 'a', "lun." }, //$NON-NLS-2$
                { china, 'a', "lun." }, //$NON-NLS-2$
                { 0L, 'b', "janv." }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'b', "ao\u00fbt" }, //$NON-NLS-2$
                { -1000L, 'b', "janv." }, //$NON-NLS-2$
                { new Date(1147327147578L), 'b', "mai" }, //$NON-NLS-2$
                { paris, 'b', "mai" }, //$NON-NLS-2$
                { china, 'b', "mai" }, //$NON-NLS-2$
                { 0L, 'c', "jeu. janv. 01 08:00:00 UTC+08:00 1970" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'c', "dim. ao\u00fbt 17 15:18:47 UTC+08:00 292278994" }, //$NON-NLS-2$
                { -1000L, 'c', "jeu. janv. 01 07:59:59 UTC+08:00 1970" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'c', "jeu. mai 11 13:59:07 UTC+08:00 2006" }, //$NON-NLS-2$
                { paris, 'c', "lun. mai 08 12:00:00 HAEC 2006" }, //$NON-NLS-2$
                { china, 'c', "lun. mai 08 12:00:00 UTC-08:00 2006" }, //$NON-NLS-2$
                { 0L, 'd', "01" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'd', "17" }, //$NON-NLS-2$
                { -1000L, 'd', "01" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'd', "11" }, //$NON-NLS-2$
                { paris, 'd', "08" }, //$NON-NLS-2$
                { china, 'd', "08" }, //$NON-NLS-2$
                { 0L, 'e', "1" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'e', "17" }, //$NON-NLS-2$
                { -1000L, 'e', "1" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'e', "11" }, //$NON-NLS-2$
                { paris, 'e', "8" }, //$NON-NLS-2$
                { china, 'e', "8" }, //$NON-NLS-2$
                { 0L, 'h', "janv." }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'h', "ao\u00fbt" }, //$NON-NLS-2$
                { -1000L, 'h', "janv." }, //$NON-NLS-2$
                { new Date(1147327147578L), 'h', "mai" }, //$NON-NLS-2$
                { paris, 'h', "mai" }, //$NON-NLS-2$
                { china, 'h', "mai" }, //$NON-NLS-2$
                { 0L, 'j', "001" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'j', "229" }, //$NON-NLS-2$
                { -1000L, 'j', "001" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'j', "131" }, //$NON-NLS-2$
                { paris, 'j', "128" }, //$NON-NLS-2$
                { china, 'j', "128" }, //$NON-NLS-2$
                { 0L, 'k', "8" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'k', "15" }, //$NON-NLS-2$
                { -1000L, 'k', "7" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'k', "13" }, //$NON-NLS-2$
                { paris, 'k', "12" }, //$NON-NLS-2$
                { china, 'k', "12" }, //$NON-NLS-2$
                { 0L, 'l', "8" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'l', "3" }, //$NON-NLS-2$
                { -1000L, 'l', "7" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'l', "1" }, //$NON-NLS-2$
                { paris, 'l', "12" }, //$NON-NLS-2$
                { china, 'l', "12" }, //$NON-NLS-2$
                { 0L, 'm', "01" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'm', "08" }, //$NON-NLS-2$
                { -1000L, 'm', "01" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'm', "05" }, //$NON-NLS-2$
                { paris, 'm', "05" }, //$NON-NLS-2$
                { china, 'm', "05" }, //$NON-NLS-2$
                { 0L, 'p', "am" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'p', "pm" }, //$NON-NLS-2$
                { -1000L, 'p', "am" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'p', "pm" }, //$NON-NLS-2$
                { paris, 'p', "pm" }, //$NON-NLS-2$
                { china, 'p', "pm" }, //$NON-NLS-2$
                { 0L, 'r', "08:00:00 AM" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'r', "03:18:47 PM" }, //$NON-NLS-2$
                { -1000L, 'r', "07:59:59 AM" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'r', "01:59:07 PM" }, //$NON-NLS-2$
                { paris, 'r', "12:00:00 PM" }, //$NON-NLS-2$
                { china, 'r', "12:00:00 PM" }, //$NON-NLS-2$
                { 0L, 's', "0" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 's', "9223372036854775" }, //$NON-NLS-2$
                { -1000L, 's', "-1" }, //$NON-NLS-2$
                { new Date(1147327147578L), 's', "1147327147" }, //$NON-NLS-2$
                { paris, 's', "1147082400" }, //$NON-NLS-2$
                { china, 's', "1147118400" }, //$NON-NLS-2$
                { 0L, 'y', "70" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'y', "94" }, //$NON-NLS-2$
                { -1000L, 'y', "70" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'y', "06" }, //$NON-NLS-2$
                { paris, 'y', "06" }, //$NON-NLS-2$
                { china, 'y', "06" }, //$NON-NLS-2$
                { 0L, 'z', "+0800" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'z', "+0800" }, //$NON-NLS-2$
                { -1000L, 'z', "+0800" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'z', "+0800" }, //$NON-NLS-2$
                { paris, 'z', "+0100" }, //$NON-NLS-2$
                { china, 'z', "-0800" }, //$NON-NLS-2$
        };
        final Object[][] lowerCaseJapanTriple = {
                { 0L, 'a', "\u6728" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'a', "\u65e5" }, //$NON-NLS-2$
                { -1000L, 'a', "\u6728" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'a', "\u6728" }, //$NON-NLS-2$
                { paris, 'a', "\u6708" }, //$NON-NLS-2$
                { china, 'a', "\u6708" }, //$NON-NLS-2$
                { 0L, 'b', "1\u6708" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'b', "8\u6708" }, //$NON-NLS-2$
                { -1000L, 'b', "1\u6708" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'b', "5\u6708" }, //$NON-NLS-2$
                { paris, 'b', "5\u6708" }, //$NON-NLS-2$
                { china, 'b', "5\u6708" }, //$NON-NLS-2$
                { 0L, 'c', "\u6728 1\u6708 01 08:00:00 GMT+08:00 1970" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'c', "\u65e5 8\u6708 17 15:18:47 GMT+08:00 292278994" }, //$NON-NLS-2$
                { -1000L, 'c', "\u6728 1\u6708 01 07:59:59 GMT+08:00 1970" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'c', "\u6728 5\u6708 11 13:59:07 GMT+08:00 2006" }, //$NON-NLS-2$
                { paris, 'c', "\u6708 5\u6708 08 12:00:00 GMT+02:00 2006" }, //$NON-NLS-2$
                { china, 'c', "\u6708 5\u6708 08 12:00:00 GMT-08:00 2006" }, //$NON-NLS-2$
                { 0L, 'd', "01" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'd', "17" }, //$NON-NLS-2$
                { -1000L, 'd', "01" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'd', "11" }, //$NON-NLS-2$
                { paris, 'd', "08" }, //$NON-NLS-2$
                { china, 'd', "08" }, //$NON-NLS-2$
                { 0L, 'e', "1" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'e', "17" }, //$NON-NLS-2$
                { -1000L, 'e', "1" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'e', "11" }, //$NON-NLS-2$
                { paris, 'e', "8" }, //$NON-NLS-2$
                { china, 'e', "8" }, //$NON-NLS-2$
                { 0L, 'h', "1\u6708" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'h', "8\u6708" }, //$NON-NLS-2$
                { -1000L, 'h', "1\u6708" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'h', "5\u6708" }, //$NON-NLS-2$
                { paris, 'h', "5\u6708" }, //$NON-NLS-2$
                { china, 'h', "5\u6708" }, //$NON-NLS-2$
                { 0L, 'j', "001" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'j', "229" }, //$NON-NLS-2$
                { -1000L, 'j', "001" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'j', "131" }, //$NON-NLS-2$
                { paris, 'j', "128" }, //$NON-NLS-2$
                { china, 'j', "128" }, //$NON-NLS-2$
                { 0L, 'k', "8" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'k', "15" }, //$NON-NLS-2$
                { -1000L, 'k', "7" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'k', "13" }, //$NON-NLS-2$
                { paris, 'k', "12" }, //$NON-NLS-2$
                { china, 'k', "12" }, //$NON-NLS-2$
                { 0L, 'l', "8" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'l', "3" }, //$NON-NLS-2$
                { -1000L, 'l', "7" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'l', "1" }, //$NON-NLS-2$
                { paris, 'l', "12" }, //$NON-NLS-2$
                { china, 'l', "12" }, //$NON-NLS-2$
                { 0L, 'm', "01" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'm', "08" }, //$NON-NLS-2$
                { -1000L, 'm', "01" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'm', "05" }, //$NON-NLS-2$
                { paris, 'm', "05" }, //$NON-NLS-2$
                { china, 'm', "05" }, //$NON-NLS-2$
                { 0L, 'p', "\u5348\u524d" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'p', "\u5348\u5f8c" }, //$NON-NLS-2$
                { -1000L, 'p', "\u5348\u524d" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'p', "\u5348\u5f8c" }, //$NON-NLS-2$
                { paris, 'p', "\u5348\u5f8c" }, //$NON-NLS-2$
                { china, 'p', "\u5348\u5f8c" }, //$NON-NLS-2$
                { 0L, 'r', "08:00:00 \u5348\u524d" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'r', "03:18:47 \u5348\u5f8c" }, //$NON-NLS-2$
                { -1000L, 'r', "07:59:59 \u5348\u524d" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'r', "01:59:07 \u5348\u5f8c" }, //$NON-NLS-2$
                { paris, 'r', "12:00:00 \u5348\u5f8c" }, //$NON-NLS-2$
                { china, 'r', "12:00:00 \u5348\u5f8c" }, //$NON-NLS-2$
                { 0L, 's', "0" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 's', "9223372036854775" }, //$NON-NLS-2$
                { -1000L, 's', "-1" }, //$NON-NLS-2$
                { new Date(1147327147578L), 's', "1147327147" }, //$NON-NLS-2$
                { paris, 's', "1147082400" }, //$NON-NLS-2$
                { china, 's', "1147118400" }, //$NON-NLS-2$
                { 0L, 'y', "70" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'y', "94" }, //$NON-NLS-2$
                { -1000L, 'y', "70" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'y', "06" }, //$NON-NLS-2$
                { paris, 'y', "06" }, //$NON-NLS-2$
                { china, 'y', "06" }, //$NON-NLS-2$
                { 0L, 'z', "+0800" }, //$NON-NLS-2$
                { Long.MAX_VALUE, 'z', "+0800" }, //$NON-NLS-2$
                { -1000L, 'z', "+0800" }, //$NON-NLS-2$
                { new Date(1147327147578L), 'z', "+0800" }, //$NON-NLS-2$
                { paris, 'z', "+0100" }, //$NON-NLS-2$
                { china, 'z', "-0800" }, //$NON-NLS-2$
        };
        final int input = 0;
        final int pattern = 1;
        final int output = 2;
        for (int i = 0; i < 90; i++) {
            System.out.println("line="+i);
            // go through legal conversion
            String formatSpecifier = "%t" + lowerCaseGermanTriple[i][pattern]; //$NON-NLS-2$
            String formatSpecifierUpper = "%T" + lowerCaseGermanTriple[i][pattern]; //$NON-NLS-2$
            // test '%t'
            Formatter f = new Formatter(Locale.GERMAN, formatSpecifier);
            Assert.assertEquals("Format pattern: " + formatSpecifier //$NON-NLS-2$
                            + " Argument: " + lowerCaseGermanTriple[i][input], //$NON-NLS-2$
                    lowerCaseGermanTriple[i][output],
                    f.format(lowerCaseGermanTriple[i][input]).toString());
            Assert.assertEquals("Format pattern: " + formatSpecifier //$NON-NLS-2$
                            + " Argument: " + lowerCaseFranceTriple[i][input], //$NON-NLS-2$
                    lowerCaseFranceTriple[i][output],
                    f.format(Locale.FRANCE, lowerCaseFranceTriple[i][input]).toString());
            Assert.assertEquals("Format pattern: " + formatSpecifier //$NON-NLS-2$
                            + " Argument: " + lowerCaseJapanTriple[i][input], //$NON-NLS-2$
                    lowerCaseJapanTriple[i][output],
                    f.format(Locale.JAPAN, lowerCaseJapanTriple[i][input]).toString());
            // test '%T'
            f = new Formatter(Locale.GERMAN, formatSpecifierUpper);
            Assert.assertEquals("Format pattern: " + formatSpecifierUpper //$NON-NLS-2$
                            + " Argument: " + lowerCaseGermanTriple[i][input], //$NON-NLS-2$
                    ((String) lowerCaseGermanTriple[i][output])
                            .toUpperCase(Locale.US),
                    f.format(lowerCaseGermanTriple[i][input]).toString());
            Assert.assertEquals("Format pattern: " + formatSpecifierUpper //$NON-NLS-2$
                            + " Argument: " + lowerCaseFranceTriple[i][input], //$NON-NLS-2$
                    ((String) lowerCaseFranceTriple[i][output])
                            .toUpperCase(Locale.US),
                    f.format(Locale.FRANCE, lowerCaseFranceTriple[i][input]).toString());
            Assert.assertEquals("Format pattern: " + formatSpecifierUpper //$NON-NLS-2$
                            + " Argument: " + lowerCaseJapanTriple[i][input], //$NON-NLS-2$
                    ((String) lowerCaseJapanTriple[i][output])
                            .toUpperCase(Locale.US),
                    f.format(Locale.JAPAN, lowerCaseJapanTriple[i][input]).toString());
        }
    }

    private void equals(Locale locale, String template, Object... args) {
        Formatter formatter = new Formatter(locale, template);
        Assert.assertEquals(jufFormat(formatter.locale(), template,  args),
                formatter.format( args).toString());
    }


    private String jufFormat(Locale locale, String template, Object... args) {
        return new java.util.Formatter(locale).format(template, args).toString();
    }
}
