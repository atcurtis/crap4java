/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.xiphis.text;

import org.xiphis.collection.Pair;
import org.xiphis.collection.WeakStringHashMap;
import org.xiphis.util.MathUtil;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An interpreter for printf-style format strings.  This class provides support
 * for layout justification and alignment, common formats for numeric, string,
 * and date/time data, and locale-specific output.  Common Java types such as
 * {@code byte}, {@link java.math.BigDecimal BigDecimal}, and {@link Calendar}
 * are supported.
 *
 * <p> Formatted printing for the Java language is heavily inspired by C's
 * {@code printf}.  Although the format strings are similar to C, some
 * customizations have been made to accommodate the Java language and exploit
 * some of its features.  Also, Java formatting is more strict than C's; for
 * example, if a conversion is incompatible with a flag, an exception will be
 * thrown.  In C inapplicable flags are silently ignored.  The format strings
 * are thus intended to be recognizable to C programmers but not necessarily
 * completely compatible with those in C.
 *
 * <p> Examples of expected usage:
 *
 * <blockquote><pre>
 *   // Explicit argument indices may be used to re-order output.
 *   Formatter formatter = new Formatter(Locale.US, "%4$2s %3$2s %2$2s %1$2s");
 *   StringBuilder sb = new StringBuilder();
 *   formatter.formatTo(sb, "a", "b", "c", "d").toString()
 *   // -&gt; " d  c  b  a"
 *
 *   // Optional locale as the first argument can be used to get
 *   // locale-specific formatting of numbers.  The precision and width can be
 *   // given to round and align the value.
 *   Formatter formatter = new Formatter(Locale.FRANCE, "e = %+10.4f");
 *   formatter.format(Math.E).toString()
 *   // -&gt; "e =    +2,7183"
 *
 *   // The '(' numeric flag may be used to format negative numbers with
 *   // parentheses rather than a minus sign.  Group separators are
 *   // automatically inserted.
 *   Formatter formatter = new Formatter("Amount gained or lost since last statement: $ %(,.2f");
 *   formatter.format(balanceDelta).toString();
 *   // -&gt; "Amount gained or lost since last statement: $ (6,217.58)"
 * </pre></blockquote>
 *
 * <p> Convenience methods for common formatting requests exist as illustrated
 * by the following invocations:
 *
 * <blockquote><pre>
 *   // Writes a formatted string to System.out.
 *   System.out.format("Local time: %tT", Calendar.getInstance());
 *   // -&gt; "Local time: 13:34:18"
 *
 *   // Writes formatted output to System.err.
 *   System.err.printf("Unable to open file '%1$s': %2$s",
 *                     fileName, exception.getMessage());
 *   // -&gt; "Unable to open file 'food': No such file or directory"
 * </pre></blockquote>
 *
 * <p> Like C's {@code sprintf(3)}, Strings may be formatted using the static
 * method {@link String#format(String,Object...) String.format}:
 *
 * <blockquote><pre>
 *   // Format a string containing a date.
 *   import java.util.Calendar;
 *   import java.util.GregorianCalendar;
 *   import static java.util.Calendar.*;
 *
 *   Calendar c = new GregorianCalendar(1995, MAY, 23);
 *   String s = String.format("Duke's Birthday: %1$tb %1$te, %1$tY", c);
 *   // -&gt; s == "Duke's Birthday: May 23, 1995"
 * </pre></blockquote>
 *
 * <h2><a id="org">Organization</a></h2>
 *
 * <p> This specification is divided into two sections.  The first section, <a
 * href="#summary">Summary</a>, covers the basic formatting concepts.  This
 * section is intended for users who want to get started quickly and are
 * familiar with formatted printing in other programming languages.  The second
 * section, <a href="#detail">Details</a>, covers the specific implementation
 * details.  It is intended for users who want more precise specification of
 * formatting behavior.
 *
 * <h2><a id="summary">Summary</a></h2>
 *
 * <p> This section is intended to provide a brief overview of formatting
 * concepts.  For precise behavioral details, refer to the <a
 * href="#detail">Details</a> section.
 *
 * <h3><a id="syntax">Format String Syntax</a></h3>
 *
 * <p> Every method which produces formatted output requires a <i>format
 * string</i> and an <i>argument list</i>.  The format string is a {@link
 * String} which may contain fixed text and one or more embedded <i>format
 * specifiers</i>.  Consider the following example:
 *
 * <blockquote><pre>
 *   Calendar c = ...;
 *   String s = String.format("Duke's Birthday: %1$tm %1$te,%1$tY", c);
 * </pre></blockquote>
 *
 * This format string is the first argument to the {@code format} method.  It
 * contains three format specifiers "{@code %1$tm}", "{@code %1$te}", and
 * "{@code %1$tY}" which indicate how the arguments should be processed and
 * where they should be inserted in the text.  The remaining portions of the
 * format string are fixed text including {@code "Dukes Birthday: "} and any
 * other spaces or punctuation.
 *
 * The argument list consists of all arguments passed to the method after the
 * format string.  In the above example, the argument list is of size one and
 * consists of the {@link java.util.Calendar Calendar} object {@code c}.
 *
 * <ul>
 *
 * <li> The format specifiers for general, character, and numeric types have
 * the following syntax:
 *
 * <blockquote><pre>
 *   %[argument_index$][flags][width][.precision]conversion
 * </pre></blockquote>
 *
 * <p> The optional <i>argument_index</i> is a decimal integer indicating the
 * position of the argument in the argument list.  The first argument is
 * referenced by "{@code 1$}", the second by "{@code 2$}", etc.
 *
 * <p> The optional <i>flags</i> is a set of characters that modify the output
 * format.  The set of valid flags depends on the conversion.
 *
 * <p> The optional <i>width</i> is a positive decimal integer indicating
 * the minimum number of characters to be written to the output.
 *
 * <p> The optional <i>precision</i> is a non-negative decimal integer usually
 * used to restrict the number of characters.  The specific behavior depends on
 * the conversion.
 *
 * <p> The required <i>conversion</i> is a character indicating how the
 * argument should be formatted.  The set of valid conversions for a given
 * argument depends on the argument's data type.
 *
 * <li> The format specifiers for types which are used to represents dates and
 * times have the following syntax:
 *
 * <blockquote><pre>
 *   %[argument_index$][flags][width]conversion
 * </pre></blockquote>
 *
 * <p> The optional <i>argument_index</i>, <i>flags</i> and <i>width</i> are
 * defined as above.
 *
 * <p> The required <i>conversion</i> is a two character sequence.  The first
 * character is {@code 't'} or {@code 'T'}.  The second character indicates
 * the format to be used.  These characters are similar to but not completely
 * identical to those defined by GNU {@code date} and POSIX
 * {@code strftime(3c)}.
 *
 * <li> The format specifiers which do not correspond to arguments have the
 * following syntax:
 *
 * <blockquote><pre>
 *   %[flags][width]conversion
 * </pre></blockquote>
 *
 * <p> The optional <i>flags</i> and <i>width</i> is defined as above.
 *
 * <p> The required <i>conversion</i> is a character indicating content to be
 * inserted in the output.
 *
 * </ul>
 *
 * <h3> Conversions </h3>
 *
 * <p> Conversions are divided into the following categories:
 *
 * <ol>
 *
 * <li> <b>General</b> - may be applied to any argument
 * type
 *
 * <li> <b>Character</b> - may be applied to basic types which represent
 * Unicode characters: {@code char}, {@link Character}, {@code byte}, {@link
 * Byte}, {@code short}, and {@link Short}. This conversion may also be
 * applied to the types {@code int} and {@link Integer} when {@link
 * Character#isValidCodePoint} returns {@code true}
 *
 * <li> <b>Numeric</b>
 *
 * <ol>
 *
 * <li> <b>Integral</b> - may be applied to Java integral types: {@code byte},
 * {@link Byte}, {@code short}, {@link Short}, {@code int} and {@link
 * Integer}, {@code long}, {@link Long}, and {@link java.math.BigInteger
 * BigInteger} (but not {@code char} or {@link Character})
 *
 * <li><b>Floating Point</b> - may be applied to Java floating-point types:
 * {@code float}, {@link Float}, {@code double}, {@link Double}, and {@link
 * java.math.BigDecimal BigDecimal}
 *
 * </ol>
 *
 * <li> <b>Date/Time</b> - may be applied to Java types which are capable of
 * encoding a date or time: {@code long}, {@link Long}, {@link Calendar},
 * {@link Date} and {@link TemporalAccessor TemporalAccessor}
 *
 * <li> <b>Percent</b> - produces a literal {@code '%'}
 * (<code>'&#92;u0025'</code>)
 *
 * <li> <b>Line Separator</b> - produces the platform-specific line separator
 *
 * </ol>
 *
 * <p> For category <i>General</i>, <i>Character</i>, <i>Numeric</i>,
 * <i>Integral</i> and <i>Date/Time</i> conversion, unless otherwise specified,
 * if the argument <i>arg</i> is {@code null}, then the result is "{@code null}".
 *
 * <p> The following table summarizes the supported conversions.  Conversions
 * denoted by an upper-case character (i.e. {@code 'B'}, {@code 'H'},
 * {@code 'S'}, {@code 'C'}, {@code 'X'}, {@code 'E'}, {@code 'G'},
 * {@code 'A'}, and {@code 'T'}) are the same as those for the corresponding
 * lower-case conversion characters except that the result is converted to
 * upper case according to the rules of the prevailing {@link java.util.Locale
 * Locale}. If there is no explicit locale specified, either at the
 * construction of the instance or as a parameter to its method
 * invocation, then the {@link java.util.Locale.Category#FORMAT default locale}
 * is used.
 *
 *
 * <table class="striped">
 * <caption style="display:none">genConv</caption>
 * <thead>
 * <tr><th scope="col" style="vertical-align:bottom"> Conversion
 *     <th scope="col" style="vertical-align:bottom"> Argument Category
 *     <th scope="col" style="vertical-align:bottom"> Description
 * </thead>
 * <tbody>
 * <tr><th scope="row" style="vertical-align:top"> {@code 'b'}, {@code 'B'}
 *     <td style="vertical-align:top"> general
 *     <td> If the argument <i>arg</i> is {@code null}, then the result is
 *     "{@code false}".  If <i>arg</i> is a {@code boolean} or {@link
 *     Boolean}, then the result is the string returned by {@link
 *     String#valueOf(boolean) String.valueOf(arg)}.  Otherwise, the result is
 *     "true".
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'h'}, {@code 'H'}
 *     <td style="vertical-align:top"> general
 *     <td> The result is obtained by invoking
 *     {@code Integer.toHexString(arg.hashCode())}.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 's'}, {@code 'S'}
 *     <td style="vertical-align:top"> general
 *     <td> If <i>arg</i> implements {@link Formattable}, then
 *     {@link Formattable#formatTo arg.formatTo} is invoked. Otherwise, the
 *     result is obtained by invoking {@code arg.toString()}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'c'}, {@code 'C'}
 *     <td style="vertical-align:top"> character
 *     <td> The result is a Unicode character
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'd'}
 *     <td style="vertical-align:top"> integral
 *     <td> The result is formatted as a decimal integer
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'o'}
 *     <td style="vertical-align:top"> integral
 *     <td> The result is formatted as an octal integer
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'x'}, {@code 'X'}
 *     <td style="vertical-align:top"> integral
 *     <td> The result is formatted as a hexadecimal integer
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'e'}, {@code 'E'}
 *     <td style="vertical-align:top"> floating point
 *     <td> The result is formatted as a decimal number in computerized
 *     scientific notation
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'f'}
 *     <td style="vertical-align:top"> floating point
 *     <td> The result is formatted as a decimal number
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'g'}, {@code 'G'}
 *     <td style="vertical-align:top"> floating point
 *     <td> The result is formatted using computerized scientific notation or
 *     decimal format, depending on the precision and the value after rounding.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'a'}, {@code 'A'}
 *     <td style="vertical-align:top"> floating point
 *     <td> The result is formatted as a hexadecimal floating-point number with
 *     a significand and an exponent. This conversion is <b>not</b> supported
 *     for the {@code BigDecimal} type despite the latter's being in the
 *     <i>floating point</i> argument category.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 't'}, {@code 'T'}
 *     <td style="vertical-align:top"> date/time
 *     <td> Prefix for date and time conversion characters.  See <a
 *     href="#dt">Date/Time Conversions</a>.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code '%'}
 *     <td style="vertical-align:top"> percent
 *     <td> The result is a literal {@code '%'} (<code>'&#92;u0025'</code>)
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'n'}
 *     <td style="vertical-align:top"> line separator
 *     <td> The result is the platform-specific line separator
 *
 * </tbody>
 * </table>
 *
 * <p> Any characters not explicitly defined as conversions are illegal and are
 * reserved for future extensions.
 *
 * <h3><a id="dt">Date/Time Conversions</a></h3>
 *
 * <p> The following date and time conversion suffix characters are defined for
 * the {@code 't'} and {@code 'T'} conversions.  The types are similar to but
 * not completely identical to those defined by GNU {@code date} and POSIX
 * {@code strftime(3c)}.  Additional conversion types are provided to access
 * Java-specific functionality (e.g. {@code 'L'} for milliseconds within the
 * second).
 *
 * <p> The following conversion characters are used for formatting times:
 *
 * <table class="striped">
 * <caption style="display:none">time</caption>
 * <tbody>
 * <tr><th scope="row" style="vertical-align:top"> {@code 'H'}
 *     <td> Hour of the day for the 24-hour clock, formatted as two digits with
 *     a leading zero as necessary i.e. {@code 00 - 23}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'I'}
 *     <td> Hour for the 12-hour clock, formatted as two digits with a leading
 *     zero as necessary, i.e.  {@code 01 - 12}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'k'}
 *     <td> Hour of the day for the 24-hour clock, i.e. {@code 0 - 23}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'l'}
 *     <td> Hour for the 12-hour clock, i.e. {@code 1 - 12}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'M'}
 *     <td> Minute within the hour formatted as two digits with a leading zero
 *     as necessary, i.e.  {@code 00 - 59}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'S'}
 *     <td> Seconds within the minute, formatted as two digits with a leading
 *     zero as necessary, i.e. {@code 00 - 60} ("{@code 60}" is a special
 *     value required to support leap seconds).
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'L'}
 *     <td> Millisecond within the second formatted as three digits with
 *     leading zeros as necessary, i.e. {@code 000 - 999}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'N'}
 *     <td> Nanosecond within the second, formatted as nine digits with leading
 *     zeros as necessary, i.e. {@code 000000000 - 999999999}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'p'}
 *     <td> Locale-specific {@linkplain
 *     java.text.DateFormatSymbols#getAmPmStrings morning or afternoon} marker
 *     in lower case, e.g."{@code am}" or "{@code pm}". Use of the conversion
 *     prefix {@code 'T'} forces this output to upper case.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'z'}
 *     <td> <a href="http://www.ietf.org/rfc/rfc0822.txt">RFC&nbsp;822</a>
 *     style numeric time zone offset from GMT, e.g. {@code -0800}.  This
 *     value will be adjusted as necessary for Daylight Saving Time.  For
 *     {@code long}, {@link Long}, and {@link Date} the time zone used is
 *     the {@linkplain TimeZone#getDefault() default time zone} for this
 *     instance of the Java virtual machine.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'Z'}
 *     <td> A string representing the abbreviation for the time zone.  This
 *     value will be adjusted as necessary for Daylight Saving Time.  For
 *     {@code long}, {@link Long}, and {@link Date} the  time zone used is
 *     the {@linkplain TimeZone#getDefault() default time zone} for this
 *     instance of the Java virtual machine.  The Formatter's locale will
 *     supersede the locale of the argument (if any).
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 's'}
 *     <td> Seconds since the beginning of the epoch starting at 1 January 1970
 *     {@code 00:00:00} UTC, i.e. {@code Long.MIN_VALUE/1000} to
 *     {@code Long.MAX_VALUE/1000}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'Q'}
 *     <td> Milliseconds since the beginning of the epoch starting at 1 January
 *     1970 {@code 00:00:00} UTC, i.e. {@code Long.MIN_VALUE} to
 *     {@code Long.MAX_VALUE}.
 *
 * </tbody>
 * </table>
 *
 * <p> The following conversion characters are used for formatting dates:
 *
 * <table class="striped">
 * <caption style="display:none">date</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'B'}
 *     <td> Locale-specific {@linkplain java.text.DateFormatSymbols#getMonths
 *     full month name}, e.g. {@code "January"}, {@code "February"}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'b'}
 *     <td> Locale-specific {@linkplain
 *     java.text.DateFormatSymbols#getShortMonths abbreviated month name},
 *     e.g. {@code "Jan"}, {@code "Feb"}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'h'}
 *     <td> Same as {@code 'b'}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'A'}
 *     <td> Locale-specific full name of the {@linkplain
 *     java.text.DateFormatSymbols#getWeekdays day of the week},
 *     e.g. {@code "Sunday"}, {@code "Monday"}
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'a'}
 *     <td> Locale-specific short name of the {@linkplain
 *     java.text.DateFormatSymbols#getShortWeekdays day of the week},
 *     e.g. {@code "Sun"}, {@code "Mon"}
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'C'}
 *     <td> Four-digit year divided by {@code 100}, formatted as two digits
 *     with leading zero as necessary, i.e. {@code 00 - 99}
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'Y'}
 *     <td> Year, formatted as at least four digits with leading zeros as
 *     necessary, e.g. {@code 0092} equals {@code 92} CE for the Gregorian
 *     calendar.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'y'}
 *     <td> Last two digits of the year, formatted with leading zeros as
 *     necessary, i.e. {@code 00 - 99}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'j'}
 *     <td> Day of year, formatted as three digits with leading zeros as
 *     necessary, e.g. {@code 001 - 366} for the Gregorian calendar.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'm'}
 *     <td> Month, formatted as two digits with leading zeros as necessary,
 *     i.e. {@code 01 - 13}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'd'}
 *     <td> Day of month, formatted as two digits with leading zeros as
 *     necessary, i.e. {@code 01 - 31}
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'e'}
 *     <td> Day of month, formatted as two digits, i.e. {@code 1 - 31}.
 *
 * </tbody>
 * </table>
 *
 * <p> The following conversion characters are used for formatting common
 * date/time compositions.
 *
 * <table class="striped">
 * <caption style="display:none">composites</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'R'}
 *     <td> Time formatted for the 24-hour clock as {@code "%tH:%tM"}
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'T'}
 *     <td> Time formatted for the 24-hour clock as {@code "%tH:%tM:%tS"}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'r'}
 *     <td> Time formatted for the 12-hour clock as {@code "%tI:%tM:%tS %Tp"}.
 *     The location of the morning or afternoon marker ({@code '%Tp'}) may be
 *     locale-dependent.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'D'}
 *     <td> Date formatted as {@code "%tm/%td/%ty"}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'F'}
 *     <td> <a href="http://www.w3.org/TR/NOTE-datetime">ISO&nbsp;8601</a>
 *     complete date formatted as {@code "%tY-%tm-%td"}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'c'}
 *     <td> Date and time formatted as {@code "%ta %tb %td %tT %tZ %tY"},
 *     e.g. {@code "Sun Jul 20 16:17:00 EDT 1969"}.
 *
 * </tbody>
 * </table>
 *
 * <p> Any characters not explicitly defined as date/time conversion suffixes
 * are illegal and are reserved for future extensions.
 *
 * <h3> Flags </h3>
 *
 * <p> The following table summarizes the supported flags.  <i>y</i> means the
 * flag is supported for the indicated argument types.
 *
 * <table class="striped">
 * <caption style="display:none">genConv</caption>
 * <thead>
 * <tr><th scope="col" style="vertical-align:bottom"> Flag <th scope="col" style="vertical-align:bottom"> General
 *     <th scope="col" style="vertical-align:bottom"> Character <th scope="col" style="vertical-align:bottom"> Integral
 *     <th scope="col" style="vertical-align:bottom"> Floating Point
 *     <th scope="col" style="vertical-align:bottom"> Date/Time
 *     <th scope="col" style="vertical-align:bottom"> Description
 * </thead>
 * <tbody>
 * <tr><th scope="row"> '-' <td style="text-align:center; vertical-align:top"> y
 *     <td style="text-align:center; vertical-align:top"> y
 *     <td style="text-align:center; vertical-align:top"> y
 *     <td style="text-align:center; vertical-align:top"> y
 *     <td style="text-align:center; vertical-align:top"> y
 *     <td> The result will be left-justified.
 *
 * <tr><th scope="row"> '#' <td style="text-align:center; vertical-align:top"> y<sup>1</sup>
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> y<sup>3</sup>
 *     <td style="text-align:center; vertical-align:top"> y
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td> The result should use a conversion-dependent alternate form
 *
 * <tr><th scope="row"> '+' <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> y<sup>4</sup>
 *     <td style="text-align:center; vertical-align:top"> y
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td> The result will always include a sign
 *
 * <tr><th scope="row"> '&nbsp;&nbsp;' <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> y<sup>4</sup>
 *     <td style="text-align:center; vertical-align:top"> y
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td> The result will include a leading space for positive values
 *
 * <tr><th scope="row"> '0' <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> y
 *     <td style="text-align:center; vertical-align:top"> y
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td> The result will be zero-padded
 *
 * <tr><th scope="row"> ',' <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> y<sup>2</sup>
 *     <td style="text-align:center; vertical-align:top"> y<sup>5</sup>
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td> The result will include locale-specific {@linkplain
 *     java.text.DecimalFormatSymbols#getGroupingSeparator grouping separators}
 *
 * <tr><th scope="row"> '(' <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> -
 *     <td style="text-align:center; vertical-align:top"> y<sup>4</sup>
 *     <td style="text-align:center; vertical-align:top"> y<sup>5</sup>
 *     <td style="text-align:center"> -
 *     <td> The result will enclose negative numbers in parentheses
 *
 * </tbody>
 * </table>
 *
 * <p> <sup>1</sup> Depends on the definition of {@link Formattable}.
 *
 * <p> <sup>2</sup> For {@code 'd'} conversion only.
 *
 * <p> <sup>3</sup> For {@code 'o'}, {@code 'x'}, and {@code 'X'}
 * conversions only.
 *
 * <p> <sup>4</sup> For {@code 'd'}, {@code 'o'}, {@code 'x'}, and
 * {@code 'X'} conversions applied to {@link java.math.BigInteger BigInteger}
 * or {@code 'd'} applied to {@code byte}, {@link Byte}, {@code short}, {@link
 * Short}, {@code int} and {@link Integer}, {@code long}, and {@link Long}.
 *
 * <p> <sup>5</sup> For {@code 'e'}, {@code 'E'}, {@code 'f'},
 * {@code 'g'}, and {@code 'G'} conversions only.
 *
 * <p> Any characters not explicitly defined as flags are illegal and are
 * reserved for future extensions.
 *
 * <h3> Width </h3>
 *
 * <p> The width is the minimum number of characters to be written to the
 * output.  For the line separator conversion, width is not applicable; if it
 * is provided, an exception will be thrown.
 *
 * <h3> Precision </h3>
 *
 * <p> For general argument types, the precision is the maximum number of
 * characters to be written to the output.
 *
 * <p> For the floating-point conversions {@code 'a'}, {@code 'A'}, {@code 'e'},
 * {@code 'E'}, and {@code 'f'} the precision is the number of digits after the
 * radix point.  If the conversion is {@code 'g'} or {@code 'G'}, then the
 * precision is the total number of digits in the resulting magnitude after
 * rounding.
 *
 * <p> For character, integral, and date/time argument types and the percent
 * and line separator conversions, the precision is not applicable; if a
 * precision is provided, an exception will be thrown.
 *
 * <h3> Argument Index </h3>
 *
 * <p> The argument index is a decimal integer indicating the position of the
 * argument in the argument list.  The first argument is referenced by
 * "{@code 1$}", the second by "{@code 2$}", etc.
 *
 * <p> Another way to reference arguments by position is to use the
 * {@code '<'} (<code>'&#92;u003c'</code>) flag, which causes the argument for
 * the previous format specifier to be re-used.  For example, the following two
 * statements would produce identical strings:
 *
 * <blockquote><pre>
 *   Calendar c = ...;
 *   String s1 = String.format("Duke's Birthday: %1$tm %1$te,%1$tY", c);
 *
 *   String s2 = String.format("Duke's Birthday: %1$tm %&lt;te,%&lt;tY", c);
 * </pre></blockquote>
 *
 * <hr>
 * <h2><a id="detail">Details</a></h2>
 *
 * <p> This section is intended to provide behavioral details for formatting,
 * including conditions and exceptions, supported data types, localization, and
 * interactions between flags, conversions, and data types.  For an overview of
 * formatting concepts, refer to the <a href="#summary">Summary</a>
 *
 * <p> Any characters not explicitly defined as conversions, date/time
 * conversion suffixes, or flags are illegal and are reserved for
 * future extensions.  Use of such a character in a format string will
 * cause an {@link UnknownFormatConversionException} or {@link
 * UnknownFormatFlagsException} to be thrown.
 *
 * <p> If the format specifier contains a width or precision with an invalid
 * value or which is otherwise unsupported, then a {@link
 * IllegalFormatWidthException} or {@link IllegalFormatPrecisionException}
 * respectively will be thrown. Similarly, values of zero for an argument
 * index will result in an {@link java.util.IllegalFormatException}.
 *
 * <p> If a format specifier contains a conversion character that is not
 * applicable to the corresponding argument, then an {@link
 * IllegalFormatConversionException} will be thrown.
 *
 * <p> Values of <i>precision</i> must be in the range zero to
 * {@link Integer#MAX_VALUE}, inclusive, otherwise
 * {@link IllegalFormatPrecisionException} is thrown.</p>
 *
 * <p> Values of <i>width</i> must be in the range one to
 * {@link Integer#MAX_VALUE}, inclusive, otherwise
 * {@link IllegalFormatWidthException} will be thrown
 * Note that widths can appear to have a negative value, but the negative sign
 * is a <i>flag</i>. For example in the format string {@code "%-20s"} the
 * <i>width</i> is <i>20</i> and the <i>flag</i> is "-".</p>
 *
 * <p> Values of <i>index</i> must be in the range one to
 * {@link Integer#MAX_VALUE}, inclusive, otherwise
 * {@link java.util.IllegalFormatException} will be thrown.</p>
 *
 * <p> All specified exceptions may be thrown by any of the {@code format}
 * methods of {@code Formatter} as well as by any {@code format} convenience
 * methods such as {@link String#format(String,Object...) String.format} and
 * {@link java.io.PrintStream#printf(String,Object...) PrintStream.printf}.
 *
 * <p> For category <i>General</i>, <i>Character</i>, <i>Numeric</i>,
 * <i>Integral</i> and <i>Date/Time</i> conversion, unless otherwise specified,
 * if the argument <i>arg</i> is {@code null}, then the result is "{@code null}".
 *
 * <p> Conversions denoted by an upper-case character (i.e. {@code 'B'},
 * {@code 'H'}, {@code 'S'}, {@code 'C'}, {@code 'X'}, {@code 'E'},
 * {@code 'G'}, {@code 'A'}, and {@code 'T'}) are the same as those for the
 * corresponding lower-case conversion characters except that the result is
 * converted to upper case according to the rules of the prevailing {@link
 * java.util.Locale Locale}. If there is no explicit locale specified,
 * either at the construction of the instance or as a parameter to its method
 * invocation, then the {@link java.util.Locale.Category#FORMAT default locale}
 * is used.
 *
 * <h3><a id="dgen">General</a></h3>
 *
 * <p> The following general conversions may be applied to any argument type:
 *
 * <table class="striped">
 * <caption style="display:none">dgConv</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'b'}
 *     <td style="vertical-align:top"> <code>'&#92;u0062'</code>
 *     <td> Produces either "{@code true}" or "{@code false}" as returned by
 *     {@link Boolean#toString(boolean)}.
 *
 *     <p> If the argument is {@code null}, then the result is
 *     "{@code false}".  If the argument is a {@code boolean} or {@link
 *     Boolean}, then the result is the string returned by {@link
 *     String#valueOf(boolean) String.valueOf()}.  Otherwise, the result is
 *     "{@code true}".
 *
 *     <p> If the {@code '#'} flag is given, then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'B'}
 *     <td style="vertical-align:top"> <code>'&#92;u0042'</code>
 *     <td> The upper-case variant of {@code 'b'}.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'h'}
 *     <td style="vertical-align:top"> <code>'&#92;u0068'</code>
 *     <td> Produces a string representing the hash code value of the object.
 *
 *     <p> The result is obtained by invoking
 *     {@code Integer.toHexString(arg.hashCode())}.
 *
 *     <p> If the {@code '#'} flag is given, then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'H'}
 *     <td style="vertical-align:top"> <code>'&#92;u0048'</code>
 *     <td> The upper-case variant of {@code 'h'}.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 's'}
 *     <td style="vertical-align:top"> <code>'&#92;u0073'</code>
 *     <td> Produces a string.
 *
 *     <p> If the argument implements {@link Formattable}, then
 *     its {@link Formattable#formatTo formatTo} method is invoked.
 *     Otherwise, the result is obtained by invoking the argument's
 *     {@code toString()} method.
 *
 *     <p> If the {@code '#'} flag is given and the argument is not a {@link
 *     Formattable}, then a {@link FormatFlagsConversionMismatchException}
 *     will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'S'}
 *     <td style="vertical-align:top"> <code>'&#92;u0053'</code>
 *     <td> The upper-case variant of {@code 's'}.
 *
 * </tbody>
 * </table>
 *
 * <p> The following <a id="dFlags">flags</a> apply to general conversions:
 *
 * <table class="striped">
 * <caption style="display:none">dFlags</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code '-'}
 *     <td style="vertical-align:top"> <code>'&#92;u002d'</code>
 *     <td> Left justifies the output.  Spaces (<code>'&#92;u0020'</code>) will be
 *     added at the end of the converted value as required to fill the minimum
 *     width of the field.  If the width is not provided, then a {@link
 *     MissingFormatWidthException} will be thrown.  If this flag is not given
 *     then the output will be right-justified.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code '#'}
 *     <td style="vertical-align:top"> <code>'&#92;u0023'</code>
 *     <td> Requires the output use an alternate form.  The definition of the
 *     form is specified by the conversion.
 *
 * </tbody>
 * </table>
 *
 * <p> The <a id="genWidth">width</a> is the minimum number of characters to
 * be written to the
 * output.  If the length of the converted value is less than the width then
 * the output will be padded by <code>'&nbsp;&nbsp;'</code> (<code>'&#92;u0020'</code>)
 * until the total number of characters equals the width.  The padding is on
 * the left by default.  If the {@code '-'} flag is given, then the padding
 * will be on the right.  If the width is not specified then there is no
 * minimum.
 *
 * <p> The precision is the maximum number of characters to be written to the
 * output.  The precision is applied before the width, thus the output will be
 * truncated to {@code precision} characters even if the width is greater than
 * the precision.  If the precision is not specified then there is no explicit
 * limit on the number of characters.
 *
 * <h3><a id="dchar">Character</a></h3>
 *
 * This conversion may be applied to {@code char} and {@link Character}.  It
 * may also be applied to the types {@code byte}, {@link Byte},
 * {@code short}, and {@link Short}, {@code int} and {@link Integer} when
 * {@link Character#isValidCodePoint} returns {@code true}.  If it returns
 * {@code false} then an {@link IllegalFormatCodePointException} will be
 * thrown.
 *
 * <table class="striped">
 * <caption style="display:none">charConv</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'c'}
 *     <td style="vertical-align:top"> <code>'&#92;u0063'</code>
 *     <td> Formats the argument as a Unicode character as described in <a
 *     href="../lang/Character.html#unicode">Unicode Character
 *     Representation</a>.  This may be more than one 16-bit {@code char} in
 *     the case where the argument represents a supplementary character.
 *
 *     <p> If the {@code '#'} flag is given, then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'C'}
 *     <td style="vertical-align:top"> <code>'&#92;u0043'</code>
 *     <td> The upper-case variant of {@code 'c'}.
 *
 * </tbody>
 * </table>
 *
 * <p> The {@code '-'} flag defined for <a href="#dFlags">General
 * conversions</a> applies.  If the {@code '#'} flag is given, then a {@link
 * FormatFlagsConversionMismatchException} will be thrown.
 *
 * <p> The width is defined as for <a href="#genWidth">General conversions</a>.
 *
 * <p> The precision is not applicable.  If the precision is specified then an
 * {@link IllegalFormatPrecisionException} will be thrown.
 *
 * <h3><a id="dnum">Numeric</a></h3>
 *
 * <p> Numeric conversions are divided into the following categories:
 *
 * <ol>
 *
 * <li> <a href="#dnint"><b>Byte, Short, Integer, and Long</b></a>
 *
 * <li> <a href="#dnbint"><b>BigInteger</b></a>
 *
 * <li> <a href="#dndec"><b>Float and Double</b></a>
 *
 * <li> <a href="#dnbdec"><b>BigDecimal</b></a>
 *
 * </ol>
 *
 * <p> Numeric types will be formatted according to the following algorithm:
 *
 * <p><b><a id="L10nAlgorithm"> Number Localization Algorithm</a></b>
 *
 * <p> After digits are obtained for the integer part, fractional part, and
 * exponent (as appropriate for the data type), the following transformation
 * is applied:
 *
 * <ol>
 *
 * <li> Each digit character <i>d</i> in the string is replaced by a
 * locale-specific digit computed relative to the current locale's
 * {@linkplain java.text.DecimalFormatSymbols#getZeroDigit() zero digit}
 * <i>z</i>; that is <i>d&nbsp;-&nbsp;</i> {@code '0'}
 * <i>&nbsp;+&nbsp;z</i>.
 *
 * <li> If a decimal separator is present, a locale-specific {@linkplain
 * java.text.DecimalFormatSymbols#getDecimalSeparator decimal separator} is
 * substituted.
 *
 * <li> If the {@code ','} (<code>'&#92;u002c'</code>)
 * <a id="L10nGroup">flag</a> is given, then the locale-specific {@linkplain
 * java.text.DecimalFormatSymbols#getGroupingSeparator grouping separator} is
 * inserted by scanning the integer part of the string from least significant
 * to most significant digits and inserting a separator at intervals defined by
 * the locale's {@linkplain java.text.DecimalFormat#getGroupingSize() grouping
 * size}.
 *
 * <li> If the {@code '0'} flag is given, then the locale-specific {@linkplain
 * java.text.DecimalFormatSymbols#getZeroDigit() zero digits} are inserted
 * after the sign character, if any, and before the first non-zero digit, until
 * the length of the string is equal to the requested field width.
 *
 * <li> If the value is negative and the {@code '('} flag is given, then a
 * {@code '('} (<code>'&#92;u0028'</code>) is prepended and a {@code ')'}
 * (<code>'&#92;u0029'</code>) is appended.
 *
 * <li> If the value is negative (or floating-point negative zero) and
 * {@code '('} flag is not given, then a {@code '-'} (<code>'&#92;u002d'</code>)
 * is prepended.
 *
 * <li> If the {@code '+'} flag is given and the value is positive or zero (or
 * floating-point positive zero), then a {@code '+'} (<code>'&#92;u002b'</code>)
 * will be prepended.
 *
 * </ol>
 *
 * <p> If the value is NaN or positive infinity the literal strings "NaN" or
 * "Infinity" respectively, will be output.  If the value is negative infinity,
 * then the output will be "(Infinity)" if the {@code '('} flag is given
 * otherwise the output will be "-Infinity".  These values are not localized.
 *
 * <p><a id="dnint"><b> Byte, Short, Integer, and Long </b></a>
 *
 * <p> The following conversions may be applied to {@code byte}, {@link Byte},
 * {@code short}, {@link Short}, {@code int} and {@link Integer},
 * {@code long}, and {@link Long}.
 *
 * <table class="striped">
 * <caption style="display:none">IntConv</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'd'}
 *     <td style="vertical-align:top"> <code>'&#92;u0064'</code>
 *     <td> Formats the argument as a decimal integer. The <a
 *     href="#L10nAlgorithm">localization algorithm</a> is applied.
 *
 *     <p> If the {@code '0'} flag is given and the value is negative, then
 *     the zero padding will occur after the sign.
 *
 *     <p> If the {@code '#'} flag is given then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'o'}
 *     <td style="vertical-align:top"> <code>'&#92;u006f'</code>
 *     <td> Formats the argument as an integer in base eight.  No localization
 *     is applied.
 *
 *     <p> If <i>x</i> is negative then the result will be an unsigned value
 *     generated by adding 2<sup>n</sup> to the value where {@code n} is the
 *     number of bits in the type as returned by the static {@code SIZE} field
 *     in the {@linkplain Byte#SIZE Byte}, {@linkplain Short#SIZE Short},
 *     {@linkplain Integer#SIZE Integer}, or {@linkplain Long#SIZE Long}
 *     classes as appropriate.
 *
 *     <p> If the {@code '#'} flag is given then the output will always begin
 *     with the radix indicator {@code '0'}.
 *
 *     <p> If the {@code '0'} flag is given then the output will be padded
 *     with leading zeros to the field width following any indication of sign.
 *
 *     <p> If {@code '('}, {@code '+'}, '&nbsp;&nbsp;', or {@code ','} flags
 *     are given then a {@link FormatFlagsConversionMismatchException} will be
 *     thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'x'}
 *     <td style="vertical-align:top"> <code>'&#92;u0078'</code>
 *     <td> Formats the argument as an integer in base sixteen. No
 *     localization is applied.
 *
 *     <p> If <i>x</i> is negative then the result will be an unsigned value
 *     generated by adding 2<sup>n</sup> to the value where {@code n} is the
 *     number of bits in the type as returned by the static {@code SIZE} field
 *     in the {@linkplain Byte#SIZE Byte}, {@linkplain Short#SIZE Short},
 *     {@linkplain Integer#SIZE Integer}, or {@linkplain Long#SIZE Long}
 *     classes as appropriate.
 *
 *     <p> If the {@code '#'} flag is given then the output will always begin
 *     with the radix indicator {@code "0x"}.
 *
 *     <p> If the {@code '0'} flag is given then the output will be padded to
 *     the field width with leading zeros after the radix indicator or sign (if
 *     present).
 *
 *     <p> If {@code '('}, <code>'&nbsp;&nbsp;'</code>, {@code '+'}, or
 *     {@code ','} flags are given then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'X'}
 *     <td style="vertical-align:top"> <code>'&#92;u0058'</code>
 *     <td> The upper-case variant of {@code 'x'}.  The entire string
 *     representing the number will be converted to {@linkplain
 *     String#toUpperCase upper case} including the {@code 'x'} (if any) and
 *     all hexadecimal digits {@code 'a'} - {@code 'f'}
 *     (<code>'&#92;u0061'</code> -  <code>'&#92;u0066'</code>).
 *
 * </tbody>
 * </table>
 *
 * <p> If the conversion is {@code 'o'}, {@code 'x'}, or {@code 'X'} and
 * both the {@code '#'} and the {@code '0'} flags are given, then result will
 * contain the radix indicator ({@code '0'} for octal and {@code "0x"} or
 * {@code "0X"} for hexadecimal), some number of zeros (based on the width),
 * and the value.
 *
 * <p> If the {@code '-'} flag is not given, then the space padding will occur
 * before the sign.
 *
 * <p> The following <a id="intFlags">flags</a> apply to numeric integral
 * conversions:
 *
 * <table class="striped">
 * <caption style="display:none">intFlags</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code '+'}
 *     <td style="vertical-align:top"> <code>'&#92;u002b'</code>
 *     <td> Requires the output to include a positive sign for all positive
 *     numbers.  If this flag is not given then only negative values will
 *     include a sign.
 *
 *     <p> If both the {@code '+'} and <code>'&nbsp;&nbsp;'</code> flags are given
 *     then an {@link IllegalFormatFlagsException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> <code>'&nbsp;&nbsp;'</code>
 *     <td style="vertical-align:top"> <code>'&#92;u0020'</code>
 *     <td> Requires the output to include a single extra space
 *     (<code>'&#92;u0020'</code>) for non-negative values.
 *
 *     <p> If both the {@code '+'} and <code>'&nbsp;&nbsp;'</code> flags are given
 *     then an {@link IllegalFormatFlagsException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code '0'}
 *     <td style="vertical-align:top"> <code>'&#92;u0030'</code>
 *     <td> Requires the output to be padded with leading {@linkplain
 *     java.text.DecimalFormatSymbols#getZeroDigit zeros} to the minimum field
 *     width following any sign or radix indicator except when converting NaN
 *     or infinity.  If the width is not provided, then a {@link
 *     MissingFormatWidthException} will be thrown.
 *
 *     <p> If both the {@code '-'} and {@code '0'} flags are given then an
 *     {@link IllegalFormatFlagsException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code ','}
 *     <td style="vertical-align:top"> <code>'&#92;u002c'</code>
 *     <td> Requires the output to include the locale-specific {@linkplain
 *     java.text.DecimalFormatSymbols#getGroupingSeparator group separators} as
 *     described in the <a href="#L10nGroup">"group" section</a> of the
 *     localization algorithm.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code '('}
 *     <td style="vertical-align:top"> <code>'&#92;u0028'</code>
 *     <td> Requires the output to prepend a {@code '('}
 *     (<code>'&#92;u0028'</code>) and append a {@code ')'}
 *     (<code>'&#92;u0029'</code>) to negative values.
 *
 * </tbody>
 * </table>
 *
 * <p> If no <a id="intdFlags">flags</a> are given the default formatting is
 * as follows:
 *
 * <ul>
 *
 * <li> The output is right-justified within the {@code width}
 *
 * <li> Negative numbers begin with a {@code '-'} (<code>'&#92;u002d'</code>)
 *
 * <li> Positive numbers and zero do not include a sign or extra leading
 * space
 *
 * <li> No grouping separators are included
 *
 * </ul>
 *
 * <p> The <a id="intWidth">width</a> is the minimum number of characters to
 * be written to the output.  This includes any signs, digits, grouping
 * separators, radix indicator, and parentheses.  If the length of the
 * converted value is less than the width then the output will be padded by
 * spaces (<code>'&#92;u0020'</code>) until the total number of characters equals
 * width.  The padding is on the left by default.  If {@code '-'} flag is
 * given then the padding will be on the right.  If width is not specified then
 * there is no minimum.
 *
 * <p> The precision is not applicable.  If precision is specified then an
 * {@link IllegalFormatPrecisionException} will be thrown.
 *
 * <p><a id="dnbint"><b> BigInteger </b></a>
 *
 * <p> The following conversions may be applied to {@link
 * java.math.BigInteger}.
 *
 * <table class="striped">
 * <caption style="display:none">bIntConv</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'd'}
 *     <td style="vertical-align:top"> <code>'&#92;u0064'</code>
 *     <td> Requires the output to be formatted as a decimal integer. The <a
 *     href="#L10nAlgorithm">localization algorithm</a> is applied.
 *
 *     <p> If the {@code '#'} flag is given {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'o'}
 *     <td style="vertical-align:top"> <code>'&#92;u006f'</code>
 *     <td> Requires the output to be formatted as an integer in base eight.
 *     No localization is applied.
 *
 *     <p> If <i>x</i> is negative then the result will be a signed value
 *     beginning with {@code '-'} (<code>'&#92;u002d'</code>).  Signed output is
 *     allowed for this type because unlike the primitive types it is not
 *     possible to create an unsigned equivalent without assuming an explicit
 *     data-type size.
 *
 *     <p> If <i>x</i> is positive or zero and the {@code '+'} flag is given
 *     then the result will begin with {@code '+'} (<code>'&#92;u002b'</code>).
 *
 *     <p> If the {@code '#'} flag is given then the output will always begin
 *     with {@code '0'} prefix.
 *
 *     <p> If the {@code '0'} flag is given then the output will be padded
 *     with leading zeros to the field width following any indication of sign.
 *
 *     <p> If the {@code ','} flag is given then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'x'}
 *     <td style="vertical-align:top"> <code>'&#92;u0078'</code>
 *     <td> Requires the output to be formatted as an integer in base
 *     sixteen.  No localization is applied.
 *
 *     <p> If <i>x</i> is negative then the result will be a signed value
 *     beginning with {@code '-'} (<code>'&#92;u002d'</code>).  Signed output is
 *     allowed for this type because unlike the primitive types it is not
 *     possible to create an unsigned equivalent without assuming an explicit
 *     data-type size.
 *
 *     <p> If <i>x</i> is positive or zero and the {@code '+'} flag is given
 *     then the result will begin with {@code '+'} (<code>'&#92;u002b'</code>).
 *
 *     <p> If the {@code '#'} flag is given then the output will always begin
 *     with the radix indicator {@code "0x"}.
 *
 *     <p> If the {@code '0'} flag is given then the output will be padded to
 *     the field width with leading zeros after the radix indicator or sign (if
 *     present).
 *
 *     <p> If the {@code ','} flag is given then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'X'}
 *     <td style="vertical-align:top"> <code>'&#92;u0058'</code>
 *     <td> The upper-case variant of {@code 'x'}.  The entire string
 *     representing the number will be converted to {@linkplain
 *     String#toUpperCase upper case} including the {@code 'x'} (if any) and
 *     all hexadecimal digits {@code 'a'} - {@code 'f'}
 *     (<code>'&#92;u0061'</code> - <code>'&#92;u0066'</code>).
 *
 * </tbody>
 * </table>
 *
 * <p> If the conversion is {@code 'o'}, {@code 'x'}, or {@code 'X'} and
 * both the {@code '#'} and the {@code '0'} flags are given, then result will
 * contain the base indicator ({@code '0'} for octal and {@code "0x"} or
 * {@code "0X"} for hexadecimal), some number of zeros (based on the width),
 * and the value.
 *
 * <p> If the {@code '0'} flag is given and the value is negative, then the
 * zero padding will occur after the sign.
 *
 * <p> If the {@code '-'} flag is not given, then the space padding will occur
 * before the sign.
 *
 * <p> All <a href="#intFlags">flags</a> defined for Byte, Short, Integer, and
 * Long apply.  The <a href="#intdFlags">default behavior</a> when no flags are
 * given is the same as for Byte, Short, Integer, and Long.
 *
 * <p> The specification of <a href="#intWidth">width</a> is the same as
 * defined for Byte, Short, Integer, and Long.
 *
 * <p> The precision is not applicable.  If precision is specified then an
 * {@link IllegalFormatPrecisionException} will be thrown.
 *
 * <p><a id="dndec"><b> Float and Double</b></a>
 *
 * <p> The following conversions may be applied to {@code float}, {@link
 * Float}, {@code double} and {@link Double}.
 *
 * <table class="striped">
 * <caption style="display:none">floatConv</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'e'}
 *     <td style="vertical-align:top"> <code>'&#92;u0065'</code>
 *     <td> Requires the output to be formatted using <a
 *     id="scientific">computerized scientific notation</a>.  The <a
 *     href="#L10nAlgorithm">localization algorithm</a> is applied.
 *
 *     <p> The formatting of the magnitude <i>m</i> depends upon its value.
 *
 *     <p> If <i>m</i> is NaN or infinite, the literal strings "NaN" or
 *     "Infinity", respectively, will be output.  These values are not
 *     localized.
 *
 *     <p> If <i>m</i> is positive-zero or negative-zero, then the exponent
 *     will be {@code "+00"}.
 *
 *     <p> Otherwise, the result is a string that represents the sign and
 *     magnitude (absolute value) of the argument.  The formatting of the sign
 *     is described in the <a href="#L10nAlgorithm">localization
 *     algorithm</a>. The formatting of the magnitude <i>m</i> depends upon its
 *     value.
 *
 *     <p> Let <i>n</i> be the unique integer such that 10<sup><i>n</i></sup>
 *     &lt;= <i>m</i> &lt; 10<sup><i>n</i>+1</sup>; then let <i>a</i> be the
 *     mathematically exact quotient of <i>m</i> and 10<sup><i>n</i></sup> so
 *     that 1 &lt;= <i>a</i> &lt; 10. The magnitude is then represented as the
 *     integer part of <i>a</i>, as a single decimal digit, followed by the
 *     decimal separator followed by decimal digits representing the fractional
 *     part of <i>a</i>, followed by the exponent symbol {@code 'e'}
 *     (<code>'&#92;u0065'</code>), followed by the sign of the exponent, followed
 *     by a representation of <i>n</i> as a decimal integer, as produced by the
 *     method {@link Long#toString(long, int)}, and zero-padded to include at
 *     least two digits.
 *
 *     <p> The number of digits in the result for the fractional part of
 *     <i>m</i> or <i>a</i> is equal to the precision.  If the precision is not
 *     specified then the default value is {@code 6}. If the precision is less
 *     than the number of digits which would appear after the decimal point in
 *     the string returned by {@link Float#toString(float)} or {@link
 *     Double#toString(double)} respectively, then the value will be rounded
 *     using the {@linkplain java.math.RoundingMode#HALF_UP round half up
 *     algorithm}.  Otherwise, zeros may be appended to reach the precision.
 *     For a canonical representation of the value, use {@link
 *     Float#toString(float)} or {@link Double#toString(double)} as
 *     appropriate.
 *
 *     <p>If the {@code ','} flag is given, then an {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'E'}
 *     <td style="vertical-align:top"> <code>'&#92;u0045'</code>
 *     <td> The upper-case variant of {@code 'e'}.  The exponent symbol
 *     will be {@code 'E'} (<code>'&#92;u0045'</code>).
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'g'}
 *     <td style="vertical-align:top"> <code>'&#92;u0067'</code>
 *     <td> Requires the output to be formatted in general scientific notation
 *     as described below. The <a href="#L10nAlgorithm">localization
 *     algorithm</a> is applied.
 *
 *     <p> After rounding for the precision, the formatting of the resulting
 *     magnitude <i>m</i> depends on its value.
 *
 *     <p> If <i>m</i> is greater than or equal to 10<sup>-4</sup> but less
 *     than 10<sup>precision</sup> then it is represented in <i><a
 *     href="#decimal">decimal format</a></i>.
 *
 *     <p> If <i>m</i> is less than 10<sup>-4</sup> or greater than or equal to
 *     10<sup>precision</sup>, then it is represented in <i><a
 *     href="#scientific">computerized scientific notation</a></i>.
 *
 *     <p> The total number of significant digits in <i>m</i> is equal to the
 *     precision.  If the precision is not specified, then the default value is
 *     {@code 6}.  If the precision is {@code 0}, then it is taken to be
 *     {@code 1}.
 *
 *     <p> If the {@code '#'} flag is given then an {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'G'}
 *     <td style="vertical-align:top"> <code>'&#92;u0047'</code>
 *     <td> The upper-case variant of {@code 'g'}.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'f'}
 *     <td style="vertical-align:top"> <code>'&#92;u0066'</code>
 *     <td> Requires the output to be formatted using <a id="decimal">decimal
 *     format</a>.  The <a href="#L10nAlgorithm">localization algorithm</a> is
 *     applied.
 *
 *     <p> The result is a string that represents the sign and magnitude
 *     (absolute value) of the argument.  The formatting of the sign is
 *     described in the <a href="#L10nAlgorithm">localization
 *     algorithm</a>. The formatting of the magnitude <i>m</i> depends upon its
 *     value.
 *
 *     <p> If <i>m</i> NaN or infinite, the literal strings "NaN" or
 *     "Infinity", respectively, will be output.  These values are not
 *     localized.
 *
 *     <p> The magnitude is formatted as the integer part of <i>m</i>, with no
 *     leading zeroes, followed by the decimal separator followed by one or
 *     more decimal digits representing the fractional part of <i>m</i>.
 *
 *     <p> The number of digits in the result for the fractional part of
 *     <i>m</i> or <i>a</i> is equal to the precision.  If the precision is not
 *     specified then the default value is {@code 6}. If the precision is less
 *     than the number of digits which would appear after the decimal point in
 *     the string returned by {@link Float#toString(float)} or {@link
 *     Double#toString(double)} respectively, then the value will be rounded
 *     using the {@linkplain java.math.RoundingMode#HALF_UP round half up
 *     algorithm}.  Otherwise, zeros may be appended to reach the precision.
 *     For a canonical representation of the value, use {@link
 *     Float#toString(float)} or {@link Double#toString(double)} as
 *     appropriate.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'a'}
 *     <td style="vertical-align:top"> <code>'&#92;u0061'</code>
 *     <td> Requires the output to be formatted in hexadecimal exponential
 *     form.  No localization is applied.
 *
 *     <p> The result is a string that represents the sign and magnitude
 *     (absolute value) of the argument <i>x</i>.
 *
 *     <p> If <i>x</i> is negative or a negative-zero value then the result
 *     will begin with {@code '-'} (<code>'&#92;u002d'</code>).
 *
 *     <p> If <i>x</i> is positive or a positive-zero value and the
 *     {@code '+'} flag is given then the result will begin with {@code '+'}
 *     (<code>'&#92;u002b'</code>).
 *
 *     <p> The formatting of the magnitude <i>m</i> depends upon its value.
 *
 *     <ul>
 *
 *     <li> If the value is NaN or infinite, the literal strings "NaN" or
 *     "Infinity", respectively, will be output.
 *
 *     <li> If <i>m</i> is zero then it is represented by the string
 *     {@code "0x0.0p0"}.
 *
 *     <li> If <i>m</i> is a {@code double} value with a normalized
 *     representation then substrings are used to represent the significand and
 *     exponent fields.  The significand is represented by the characters
 *     {@code "0x1."} followed by the hexadecimal representation of the rest
 *     of the significand as a fraction.  The exponent is represented by
 *     {@code 'p'} (<code>'&#92;u0070'</code>) followed by a decimal string of the
 *     unbiased exponent as if produced by invoking {@link
 *     Integer#toString(int) Integer.toString} on the exponent value.  If the
 *     precision is specified, the value is rounded to the given number of
 *     hexadecimal digits.
 *
 *     <li> If <i>m</i> is a {@code double} value with a subnormal
 *     representation then, unless the precision is specified to be in the range
 *     1 through 12, inclusive, the significand is represented by the characters
 *     {@code '0x0.'} followed by the hexadecimal representation of the rest of
 *     the significand as a fraction, and the exponent represented by
 *     {@code 'p-1022'}.  If the precision is in the interval
 *     [1,&nbsp;12], the subnormal value is normalized such that it
 *     begins with the characters {@code '0x1.'}, rounded to the number of
 *     hexadecimal digits of precision, and the exponent adjusted
 *     accordingly.  Note that there must be at least one nonzero digit in a
 *     subnormal significand.
 *
 *     </ul>
 *
 *     <p> If the {@code '('} or {@code ','} flags are given, then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'A'}
 *     <td style="vertical-align:top"> <code>'&#92;u0041'</code>
 *     <td> The upper-case variant of {@code 'a'}.  The entire string
 *     representing the number will be converted to upper case including the
 *     {@code 'x'} (<code>'&#92;u0078'</code>) and {@code 'p'}
 *     (<code>'&#92;u0070'</code> and all hexadecimal digits {@code 'a'} -
 *     {@code 'f'} (<code>'&#92;u0061'</code> - <code>'&#92;u0066'</code>).
 *
 * </tbody>
 * </table>
 *
 * <p> All <a href="#intFlags">flags</a> defined for Byte, Short, Integer, and
 * Long apply.
 *
 * <p> If the {@code '#'} flag is given, then the decimal separator will
 * always be present.
 *
 * <p> If no <a id="floatdFlags">flags</a> are given the default formatting
 * is as follows:
 *
 * <ul>
 *
 * <li> The output is right-justified within the {@code width}
 *
 * <li> Negative numbers begin with a {@code '-'}
 *
 * <li> Positive numbers and positive zero do not include a sign or extra
 * leading space
 *
 * <li> No grouping separators are included
 *
 * <li> The decimal separator will only appear if a digit follows it
 *
 * </ul>
 *
 * <p> The <a id="floatDWidth">width</a> is the minimum number of characters
 * to be written to the output.  This includes any signs, digits, grouping
 * separators, decimal separators, exponential symbol, radix indicator,
 * parentheses, and strings representing infinity and NaN as applicable.  If
 * the length of the converted value is less than the width then the output
 * will be padded by spaces (<code>'&#92;u0020'</code>) until the total number of
 * characters equals width.  The padding is on the left by default.  If the
 * {@code '-'} flag is given then the padding will be on the right.  If width
 * is not specified then there is no minimum.
 *
 * <p> If the <a id="floatDPrec">conversion</a> is {@code 'e'},
 * {@code 'E'} or {@code 'f'}, then the precision is the number of digits
 * after the decimal separator.  If the precision is not specified, then it is
 * assumed to be {@code 6}.
 *
 * <p> If the conversion is {@code 'g'} or {@code 'G'}, then the precision is
 * the total number of significant digits in the resulting magnitude after
 * rounding.  If the precision is not specified, then the default value is
 * {@code 6}.  If the precision is {@code 0}, then it is taken to be
 * {@code 1}.
 *
 * <p> If the conversion is {@code 'a'} or {@code 'A'}, then the precision
 * is the number of hexadecimal digits after the radix point.  If the
 * precision is not provided, then all of the digits as returned by {@link
 * Double#toHexString(double)} will be output.
 *
 * <p><a id="dnbdec"><b> BigDecimal </b></a>
 *
 * <p> The following conversions may be applied {@link java.math.BigDecimal
 * BigDecimal}.
 *
 * <table class="striped">
 * <caption style="display:none">floatConv</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'e'}
 *     <td style="vertical-align:top"> <code>'&#92;u0065'</code>
 *     <td> Requires the output to be formatted using <a
 *     id="bscientific">computerized scientific notation</a>.  The <a
 *     href="#L10nAlgorithm">localization algorithm</a> is applied.
 *
 *     <p> The formatting of the magnitude <i>m</i> depends upon its value.
 *
 *     <p> If <i>m</i> is positive-zero or negative-zero, then the exponent
 *     will be {@code "+00"}.
 *
 *     <p> Otherwise, the result is a string that represents the sign and
 *     magnitude (absolute value) of the argument.  The formatting of the sign
 *     is described in the <a href="#L10nAlgorithm">localization
 *     algorithm</a>. The formatting of the magnitude <i>m</i> depends upon its
 *     value.
 *
 *     <p> Let <i>n</i> be the unique integer such that 10<sup><i>n</i></sup>
 *     &lt;= <i>m</i> &lt; 10<sup><i>n</i>+1</sup>; then let <i>a</i> be the
 *     mathematically exact quotient of <i>m</i> and 10<sup><i>n</i></sup> so
 *     that 1 &lt;= <i>a</i> &lt; 10. The magnitude is then represented as the
 *     integer part of <i>a</i>, as a single decimal digit, followed by the
 *     decimal separator followed by decimal digits representing the fractional
 *     part of <i>a</i>, followed by the exponent symbol {@code 'e'}
 *     (<code>'&#92;u0065'</code>), followed by the sign of the exponent, followed
 *     by a representation of <i>n</i> as a decimal integer, as produced by the
 *     method {@link Long#toString(long, int)}, and zero-padded to include at
 *     least two digits.
 *
 *     <p> The number of digits in the result for the fractional part of
 *     <i>m</i> or <i>a</i> is equal to the precision.  If the precision is not
 *     specified then the default value is {@code 6}.  If the precision is
 *     less than the number of digits to the right of the decimal point then
 *     the value will be rounded using the
 *     {@linkplain java.math.RoundingMode#HALF_UP round half up
 *     algorithm}.  Otherwise, zeros may be appended to reach the precision.
 *     For a canonical representation of the value, use {@link
 *     BigDecimal#toString()}.
 *
 *     <p> If the {@code ','} flag is given, then an {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'E'}
 *     <td style="vertical-align:top"> <code>'&#92;u0045'</code>
 *     <td> The upper-case variant of {@code 'e'}.  The exponent symbol
 *     will be {@code 'E'} (<code>'&#92;u0045'</code>).
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'g'}
 *     <td style="vertical-align:top"> <code>'&#92;u0067'</code>
 *     <td> Requires the output to be formatted in general scientific notation
 *     as described below. The <a href="#L10nAlgorithm">localization
 *     algorithm</a> is applied.
 *
 *     <p> After rounding for the precision, the formatting of the resulting
 *     magnitude <i>m</i> depends on its value.
 *
 *     <p> If <i>m</i> is greater than or equal to 10<sup>-4</sup> but less
 *     than 10<sup>precision</sup> then it is represented in <i><a
 *     href="#bdecimal">decimal format</a></i>.
 *
 *     <p> If <i>m</i> is less than 10<sup>-4</sup> or greater than or equal to
 *     10<sup>precision</sup>, then it is represented in <i><a
 *     href="#bscientific">computerized scientific notation</a></i>.
 *
 *     <p> The total number of significant digits in <i>m</i> is equal to the
 *     precision.  If the precision is not specified, then the default value is
 *     {@code 6}.  If the precision is {@code 0}, then it is taken to be
 *     {@code 1}.
 *
 *     <p> If the {@code '#'} flag is given then an {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'G'}
 *     <td style="vertical-align:top"> <code>'&#92;u0047'</code>
 *     <td> The upper-case variant of {@code 'g'}.
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'f'}
 *     <td style="vertical-align:top"> <code>'&#92;u0066'</code>
 *     <td> Requires the output to be formatted using <a id="bdecimal">decimal
 *     format</a>.  The <a href="#L10nAlgorithm">localization algorithm</a> is
 *     applied.
 *
 *     <p> The result is a string that represents the sign and magnitude
 *     (absolute value) of the argument.  The formatting of the sign is
 *     described in the <a href="#L10nAlgorithm">localization
 *     algorithm</a>. The formatting of the magnitude <i>m</i> depends upon its
 *     value.
 *
 *     <p> The magnitude is formatted as the integer part of <i>m</i>, with no
 *     leading zeroes, followed by the decimal separator followed by one or
 *     more decimal digits representing the fractional part of <i>m</i>.
 *
 *     <p> The number of digits in the result for the fractional part of
 *     <i>m</i> or <i>a</i> is equal to the precision. If the precision is not
 *     specified then the default value is {@code 6}.  If the precision is
 *     less than the number of digits to the right of the decimal point
 *     then the value will be rounded using the
 *     {@linkplain java.math.RoundingMode#HALF_UP round half up
 *     algorithm}.  Otherwise, zeros may be appended to reach the precision.
 *     For a canonical representation of the value, use {@link
 *     BigDecimal#toString()}.
 *
 * </tbody>
 * </table>
 *
 * <p> All <a href="#intFlags">flags</a> defined for Byte, Short, Integer, and
 * Long apply.
 *
 * <p> If the {@code '#'} flag is given, then the decimal separator will
 * always be present.
 *
 * <p> The <a href="#floatdFlags">default behavior</a> when no flags are
 * given is the same as for Float and Double.
 *
 * <p> The specification of <a href="#floatDWidth">width</a> and <a
 * href="#floatDPrec">precision</a> is the same as defined for Float and
 * Double.
 *
 * <h3><a id="ddt">Date/Time</a></h3>
 *
 * <p> This conversion may be applied to {@code long}, {@link Long}, {@link
 * Calendar}, {@link Date} and {@link TemporalAccessor TemporalAccessor}
 *
 * <table class="striped">
 * <caption style="display:none">DTConv</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 't'}
 *     <td style="vertical-align:top"> <code>'&#92;u0074'</code>
 *     <td> Prefix for date and time conversion characters.
 * <tr><th scope="row" style="vertical-align:top"> {@code 'T'}
 *     <td style="vertical-align:top"> <code>'&#92;u0054'</code>
 *     <td> The upper-case variant of {@code 't'}.
 *
 * </tbody>
 * </table>
 *
 * <p> The following date and time conversion character suffixes are defined
 * for the {@code 't'} and {@code 'T'} conversions.  The types are similar to
 * but not completely identical to those defined by GNU {@code date} and
 * POSIX {@code strftime(3c)}.  Additional conversion types are provided to
 * access Java-specific functionality (e.g. {@code 'L'} for milliseconds
 * within the second).
 *
 * <p> The following conversion characters are used for formatting times:
 *
 * <table class="striped">
 * <caption style="display:none">time</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top"> {@code 'H'}
 *     <td style="vertical-align:top"> <code>'&#92;u0048'</code>
 *     <td> Hour of the day for the 24-hour clock, formatted as two digits with
 *     a leading zero as necessary i.e. {@code 00 - 23}. {@code 00}
 *     corresponds to midnight.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'I'}
 *     <td style="vertical-align:top"> <code>'&#92;u0049'</code>
 *     <td> Hour for the 12-hour clock, formatted as two digits with a leading
 *     zero as necessary, i.e.  {@code 01 - 12}.  {@code 01} corresponds to
 *     one o'clock (either morning or afternoon).
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'k'}
 *     <td style="vertical-align:top"> <code>'&#92;u006b'</code>
 *     <td> Hour of the day for the 24-hour clock, i.e. {@code 0 - 23}.
 *     {@code 0} corresponds to midnight.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'l'}
 *     <td style="vertical-align:top"> <code>'&#92;u006c'</code>
 *     <td> Hour for the 12-hour clock, i.e. {@code 1 - 12}.  {@code 1}
 *     corresponds to one o'clock (either morning or afternoon).
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'M'}
 *     <td style="vertical-align:top"> <code>'&#92;u004d'</code>
 *     <td> Minute within the hour formatted as two digits with a leading zero
 *     as necessary, i.e.  {@code 00 - 59}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'S'}
 *     <td style="vertical-align:top"> <code>'&#92;u0053'</code>
 *     <td> Seconds within the minute, formatted as two digits with a leading
 *     zero as necessary, i.e. {@code 00 - 60} ("{@code 60}" is a special
 *     value required to support leap seconds).
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'L'}
 *     <td style="vertical-align:top"> <code>'&#92;u004c'</code>
 *     <td> Millisecond within the second formatted as three digits with
 *     leading zeros as necessary, i.e. {@code 000 - 999}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'N'}
 *     <td style="vertical-align:top"> <code>'&#92;u004e'</code>
 *     <td> Nanosecond within the second, formatted as nine digits with leading
 *     zeros as necessary, i.e. {@code 000000000 - 999999999}.  The precision
 *     of this value is limited by the resolution of the underlying operating
 *     system or hardware.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'p'}
 *     <td style="vertical-align:top"> <code>'&#92;u0070'</code>
 *     <td> Locale-specific {@linkplain
 *     java.text.DateFormatSymbols#getAmPmStrings morning or afternoon} marker
 *     in lower case, e.g."{@code am}" or "{@code pm}".  Use of the
 *     conversion prefix {@code 'T'} forces this output to upper case.  (Note
 *     that {@code 'p'} produces lower-case output.  This is different from
 *     GNU {@code date} and POSIX {@code strftime(3c)} which produce
 *     upper-case output.)
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'z'}
 *     <td style="vertical-align:top"> <code>'&#92;u007a'</code>
 *     <td> <a href="http://www.ietf.org/rfc/rfc0822.txt">RFC&nbsp;822</a>
 *     style numeric time zone offset from GMT, e.g. {@code -0800}.  This
 *     value will be adjusted as necessary for Daylight Saving Time.  For
 *     {@code long}, {@link Long}, and {@link Date} the time zone used is
 *     the {@linkplain TimeZone#getDefault() default time zone} for this
 *     instance of the Java virtual machine.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'Z'}
 *     <td style="vertical-align:top"> <code>'&#92;u005a'</code>
 *     <td> A string representing the abbreviation for the time zone.  This
 *     value will be adjusted as necessary for Daylight Saving Time.  For
 *     {@code long}, {@link Long}, and {@link Date} the time zone used is
 *     the {@linkplain TimeZone#getDefault() default time zone} for this
 *     instance of the Java virtual machine.  The Formatter's locale will
 *     supersede the locale of the argument (if any).
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 's'}
 *     <td style="vertical-align:top"> <code>'&#92;u0073'</code>
 *     <td> Seconds since the beginning of the epoch starting at 1 January 1970
 *     {@code 00:00:00} UTC, i.e. {@code Long.MIN_VALUE/1000} to
 *     {@code Long.MAX_VALUE/1000}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'Q'}
 *     <td style="vertical-align:top"> <code>'&#92;u004f'</code>
 *     <td> Milliseconds since the beginning of the epoch starting at 1 January
 *     1970 {@code 00:00:00} UTC, i.e. {@code Long.MIN_VALUE} to
 *     {@code Long.MAX_VALUE}. The precision of this value is limited by
 *     the resolution of the underlying operating system or hardware.
 *
 * </tbody>
 * </table>
 *
 * <p> The following conversion characters are used for formatting dates:
 *
 * <table class="striped">
 * <caption style="display:none">date</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'B'}
 *     <td style="vertical-align:top"> <code>'&#92;u0042'</code>
 *     <td> Locale-specific {@linkplain java.text.DateFormatSymbols#getMonths
 *     full month name}, e.g. {@code "January"}, {@code "February"}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'b'}
 *     <td style="vertical-align:top"> <code>'&#92;u0062'</code>
 *     <td> Locale-specific {@linkplain
 *     java.text.DateFormatSymbols#getShortMonths abbreviated month name},
 *     e.g. {@code "Jan"}, {@code "Feb"}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'h'}
 *     <td style="vertical-align:top"> <code>'&#92;u0068'</code>
 *     <td> Same as {@code 'b'}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'A'}
 *     <td style="vertical-align:top"> <code>'&#92;u0041'</code>
 *     <td> Locale-specific full name of the {@linkplain
 *     java.text.DateFormatSymbols#getWeekdays day of the week},
 *     e.g. {@code "Sunday"}, {@code "Monday"}
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'a'}
 *     <td style="vertical-align:top"> <code>'&#92;u0061'</code>
 *     <td> Locale-specific short name of the {@linkplain
 *     java.text.DateFormatSymbols#getShortWeekdays day of the week},
 *     e.g. {@code "Sun"}, {@code "Mon"}
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'C'}
 *     <td style="vertical-align:top"> <code>'&#92;u0043'</code>
 *     <td> Four-digit year divided by {@code 100}, formatted as two digits
 *     with leading zero as necessary, i.e. {@code 00 - 99}
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'Y'}
 *     <td style="vertical-align:top"> <code>'&#92;u0059'</code> <td> Year, formatted to at least
 *     four digits with leading zeros as necessary, e.g. {@code 0092} equals
 *     {@code 92} CE for the Gregorian calendar.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'y'}
 *     <td style="vertical-align:top"> <code>'&#92;u0079'</code>
 *     <td> Last two digits of the year, formatted with leading zeros as
 *     necessary, i.e. {@code 00 - 99}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'j'}
 *     <td style="vertical-align:top"> <code>'&#92;u006a'</code>
 *     <td> Day of year, formatted as three digits with leading zeros as
 *     necessary, e.g. {@code 001 - 366} for the Gregorian calendar.
 *     {@code 001} corresponds to the first day of the year.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'm'}
 *     <td style="vertical-align:top"> <code>'&#92;u006d'</code>
 *     <td> Month, formatted as two digits with leading zeros as necessary,
 *     i.e. {@code 01 - 13}, where "{@code 01}" is the first month of the
 *     year and ("{@code 13}" is a special value required to support lunar
 *     calendars).
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'd'}
 *     <td style="vertical-align:top"> <code>'&#92;u0064'</code>
 *     <td> Day of month, formatted as two digits with leading zeros as
 *     necessary, i.e. {@code 01 - 31}, where "{@code 01}" is the first day
 *     of the month.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'e'}
 *     <td style="vertical-align:top"> <code>'&#92;u0065'</code>
 *     <td> Day of month, formatted as two digits, i.e. {@code 1 - 31} where
 *     "{@code 1}" is the first day of the month.
 *
 * </tbody>
 * </table>
 *
 * <p> The following conversion characters are used for formatting common
 * date/time compositions.
 *
 * <table class="striped">
 * <caption style="display:none">composites</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'R'}
 *     <td style="vertical-align:top"> <code>'&#92;u0052'</code>
 *     <td> Time formatted for the 24-hour clock as {@code "%tH:%tM"}
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'T'}
 *     <td style="vertical-align:top"> <code>'&#92;u0054'</code>
 *     <td> Time formatted for the 24-hour clock as {@code "%tH:%tM:%tS"}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'r'}
 *     <td style="vertical-align:top"> <code>'&#92;u0072'</code>
 *     <td> Time formatted for the 12-hour clock as {@code "%tI:%tM:%tS
 *     %Tp"}.  The location of the morning or afternoon marker
 *     ({@code '%Tp'}) may be locale-dependent.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'D'}
 *     <td style="vertical-align:top"> <code>'&#92;u0044'</code>
 *     <td> Date formatted as {@code "%tm/%td/%ty"}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'F'}
 *     <td style="vertical-align:top"> <code>'&#92;u0046'</code>
 *     <td> <a href="http://www.w3.org/TR/NOTE-datetime">ISO&nbsp;8601</a>
 *     complete date formatted as {@code "%tY-%tm-%td"}.
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'c'}
 *     <td style="vertical-align:top"> <code>'&#92;u0063'</code>
 *     <td> Date and time formatted as {@code "%ta %tb %td %tT %tZ %tY"},
 *     e.g. {@code "Sun Jul 20 16:17:00 EDT 1969"}.
 *
 * </tbody>
 * </table>
 *
 * <p> The {@code '-'} flag defined for <a href="#dFlags">General
 * conversions</a> applies.  If the {@code '#'} flag is given, then a {@link
 * FormatFlagsConversionMismatchException} will be thrown.
 *
 * <p> The width is the minimum number of characters to
 * be written to the output.  If the length of the converted value is less than
 * the {@code width} then the output will be padded by spaces
 * (<code>'&#92;u0020'</code>) until the total number of characters equals width.
 * The padding is on the left by default.  If the {@code '-'} flag is given
 * then the padding will be on the right.  If width is not specified then there
 * is no minimum.
 *
 * <p> The precision is not applicable.  If the precision is specified then an
 * {@link IllegalFormatPrecisionException} will be thrown.
 *
 * <h3><a id="dper">Percent</a></h3>
 *
 * <p> The conversion does not correspond to any argument.
 *
 * <table class="striped">
 * <caption style="display:none">DTConv</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top">{@code '%'}
 *     <td> The result is a literal {@code '%'} (<code>'&#92;u0025'</code>)
 *
 * <p> The width is the minimum number of characters to
 * be written to the output including the {@code '%'}.  If the length of the
 * converted value is less than the {@code width} then the output will be
 * padded by spaces (<code>'&#92;u0020'</code>) until the total number of
 * characters equals width.  The padding is on the left.  If width is not
 * specified then just the {@code '%'} is output.
 *
 * <p> The {@code '-'} flag defined for <a href="#dFlags">General
 * conversions</a> applies.  If any other flags are provided, then a
 * {@link IllegalFormatFlagsException } will be thrown.
 *
 * <p> The precision is not applicable.  If the precision is specified an
 * {@link IllegalFormatPrecisionException} will be thrown.
 *
 * </tbody>
 * </table>
 *
 * <h3><a id="dls">Line Separator</a></h3>
 *
 * <p> The conversion does not correspond to any argument.
 *
 * <table class="striped">
 * <caption style="display:none">DTConv</caption>
 * <tbody>
 *
 * <tr><th scope="row" style="vertical-align:top">{@code 'n'}
 *     <td> the platform-specific line separator as returned by {@link
 *     System#lineSeparator()}.
 *
 * </tbody>
 * </table>
 *
 * <p> Flags, width, and precision are not applicable.  If any are provided an
 * {@link IllegalFormatFlagsException}, {@link IllegalFormatWidthException},
 * and {@link IllegalFormatPrecisionException}, respectively will be thrown.
 *
 * <h3><a id="dpos">Argument Index</a></h3>
 *
 * <p> Format specifiers can reference arguments in three ways:
 *
 * <ul>
 *
 * <li> <i>Explicit indexing</i> is used when the format specifier contains an
 * argument index.  The argument index is a decimal integer indicating the
 * position of the argument in the argument list.  The first argument is
 * referenced by "{@code 1$}", the second by "{@code 2$}", etc.  An argument
 * may be referenced more than once.
 *
 * <p> For example:
 *
 * <blockquote><pre>
 *   formatter.format("%4$s %3$s %2$s %1$s %4$s %3$s %2$s %1$s",
 *                    "a", "b", "c", "d")
 *   // -&gt; "d c b a d c b a"
 * </pre></blockquote>
 *
 * <li> <i>Relative indexing</i> is used when the format specifier contains a
 * {@code '<'} (<code>'&#92;u003c'</code>) flag which causes the argument for
 * the previous format specifier to be re-used.  If there is no previous
 * argument, then a {@link MissingFormatArgumentException} is thrown.
 *
 * <blockquote><pre>
 *    formatter.format("%s %s %&lt;s %&lt;s", "a", "b", "c", "d")
 *    // -&gt; "a b b b"
 *    // "c" and "d" are ignored because they are not referenced
 * </pre></blockquote>
 *
 * <li> <i>Ordinary indexing</i> is used when the format specifier contains
 * neither an argument index nor a {@code '<'} flag.  Each format specifier
 * which uses ordinary indexing is assigned a sequential implicit index into
 * argument list which is independent of the indices used by explicit or
 * relative indexing.
 *
 * <blockquote><pre>
 *   formatter.format("%s %s %s %s", "a", "b", "c", "d")
 *   // -&gt; "a b c d"
 * </pre></blockquote>
 *
 * </ul>
 *
 * <p> It is possible to have a format string which uses all forms of indexing,
 * for example:
 *
 * <blockquote><pre>
 *   formatter.format("%2$s %s %&lt;s %s", "a", "b", "c", "d")
 *   // -&gt; "b a a b"
 *   // "c" and "d" are ignored because they are not referenced
 * </pre></blockquote>
 *
 * <p> The maximum number of arguments is limited by the maximum dimension of a
 * Java array as defined by
 * <cite>The Java Virtual Machine Specification</cite>.
 * If the argument index does not correspond to an
 * available argument, then a {@link MissingFormatArgumentException} is thrown.
 *
 * <p> If there are more arguments than format specifiers, the extra arguments
 * are ignored.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to any
 * method or constructor in this class will cause a {@link
 * NullPointerException} to be thrown.
 *
 * @author  Iris Clark
 * @since 1.5
 */
public class Formatter {

    private static final ThreadLocal<WeakStringHashMap<Formatter>> FORMAT_MAP
            = ThreadLocal.withInitial(WeakStringHashMap::new);

    private final Locale l;
    List<FormatString> fsa;

    public static Formatter of(String format) {
        return FORMAT_MAP.get().computeIfAbsent(format, Formatter::new);
    }

    public Formatter(String format) {
        this(Locale.getDefault(Locale.Category.FORMAT), format);
    }

    public Formatter(Locale l, String format) {
        this.l = l;
        fsa = parse(format);
    }

    public Locale locale() {
        return l;
    }

    public StringBuilder formatTo(StringBuilder sb, Object... argParam) {
        format(argParam).formatTo(sb);
        return sb;
    }

    public StringBuilder formatTo(StringBuilder sb, Locale locale, Object... argParam) {
        format(locale, argParam).formatTo(sb);
        return sb;
    }

    public StringBuilderFormattable format(Object... argParam) {
        return format(l, argParam);
    }

    public StringBuilderFormattable format(Locale locale, Object... argParam) {
        Object[] args = argParam.clone();
        return Msg.of(sb -> {
            // index of last argument referenced
            int last = -1;
            // last ordinary index
            int lasto = -1;

            for (FormatString fs : fsa) {
                int index = fs.index();
                switch (index) {
                    case -2 ->  // fixed string, "%n", or "%%"
                            fs.print(sb, null, locale);
                    case -1 -> {  // relative index
                        if (last < 0 || (args != null && last > args.length - 1))
                            throw new MissingFormatArgumentException(fs.toString());
                        fs.print(sb, (args == null ? null : args[last]), locale);
                    }
                    case 0 -> {  // ordinary index
                        lasto++;
                        last = lasto;
                        if (args != null && lasto > args.length - 1)
                            throw new MissingFormatArgumentException(fs.toString());
                        fs.print(sb, (args == null ? null : args[lasto]), locale);
                    }
                    default -> {  // explicit index
                        last = index - 1;
                        if (args != null && last > args.length - 1)
                            throw new MissingFormatArgumentException(fs.toString());
                        fs.print(sb, (args == null ? null : args[last]), locale);
                    }
                }
            }
        });
    }


    // %[argument_index$][flags][width][.precision][t]conversion
    @SuppressWarnings("RegExpRedundantEscape")
    private static final String formatSpecifier
            = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

    private static final Pattern fsPattern = Pattern.compile(formatSpecifier);

    /**
     * Finds format specifiers in the format string.
     */
    private List<FormatString> parse(String s) {
        ArrayList<FormatString> al = new ArrayList<>();
        int i = 0;
        int max = s.length();
        Matcher m = null; // create if needed
        while (i < max) {
            int n = s.indexOf('%', i);
            if (n < 0) {
                // No more format specifiers, but since
                // i < max there's some trailing text
                al.add(new FixedString(s, i, max));
                break;
            }
            if (i != n) {
                // Previous characters were fixed text
                al.add(new FixedString(s, i, n));
            }
            i = n + 1;
            if (i >= max) {
                // Trailing %
                throw new UnknownFormatConversionException("%");
            }
            char c = s.charAt(i);
            if (Conversion.isValid(c)) {
                al.add(new FormatSpecifier(c).build(this));
                i++;
            } else {
                if (m == null) {
                    m = fsPattern.matcher(s);
                }
                // We have already parsed a '%' at n, so we either have a
                // match or the specifier at n is invalid
                if (m.find(n) && m.start() == n) {
                    al.add(new FormatSpecifier(s, m).build(this));
                    i = m.end();
                } else {
                    throw new UnknownFormatConversionException(String.valueOf(c));
                }
            }
        }
        return al;
    }

    private interface FormatString {
        int index();
        void print(StringBuilder sb, Object arg, Locale l);
        String toString();
    }

    private static class FixedString implements FormatString {
        private final String s;
        private final int start;
        private final int end;
        FixedString(String s) {
            this(s, 0, s.length());
        }
        FixedString(String s, int start, int end) {
            this.s = s;
            this.start = start;
            this.end = end;
        }
        public int index() { return -2; }
        public void print(StringBuilder sb, Object arg, Locale l) {
            sb.append(s, start, end);
        }
        public String toString() { return s.substring(start, end); }
    }

    // -- Methods to support throwing exceptions --

    private static void failMismatch(int f, char c) {
        String fs = Flags.toString(f);
        throw new FormatFlagsConversionMismatchException(fs, c);
    }

    private void failConversion(char c, Object arg) {
        throw new IllegalFormatConversionException(c, arg.getClass());
    }

    private static final class FormatSpecifier {
        private int index = 0;
        private int flags = Flags.NONE;
        private int width = -1;
        private int precision = -1;
        private boolean dt = false;
        private char c;

        private void index(String s, int start, int end) {
            if (start >= 0) {
                try {
                    // skip the trailing '$'
                    index = Integer.parseInt(s, start, end - 1, 10);
                    if (index <= 0) {
                        throw new IllegalFormatArgumentIndexException(index);
                    }
                } catch (NumberFormatException x) {
                    throw new IllegalFormatArgumentIndexException(Integer.MIN_VALUE);
                }
            }
        }

        public int index() {
            return index;
        }

        public int width() {
            return width;
        }

        private int flags() {
            return flags;
        }

        private int precision() {
            return precision;
        }

        private boolean isDateTime() {
            return dt;
        }

        private char formatChar() {
            return c;
        }

        private void flags(String s, int start, int end) {
            flags = Flags.parse(s, start, end);
            if (Flags.contains(flags, Flags.PREVIOUS))
                index = -1;
        }

        private void width(String s, int start, int end) {
            if (start >= 0) {
                try {
                    width = Integer.parseInt(s, start, end, 10);
                    if (width < 0)
                        throw new IllegalFormatWidthException(width);
                } catch (NumberFormatException x) {
                    throw new IllegalFormatWidthException(Integer.MIN_VALUE);
                }
            }
        }

        private void precision(String s, int start, int end) {
            if (start >= 0) {
                try {
                    // skip the leading '.'
                    precision = Integer.parseInt(s, start + 1, end, 10);
                    if (precision < 0)
                        throw new IllegalFormatPrecisionException(precision);
                } catch (NumberFormatException x) {
                    throw new IllegalFormatPrecisionException(Integer.MIN_VALUE);
                }
            }
        }

        private void conversion(char conv) {
            c = conv;
            if (!dt) {
                if (!Conversion.isValid(c)) {
                    throw new UnknownFormatConversionException(String.valueOf(c));
                }
                if (Character.isUpperCase(c)) {
                    flags = Flags.add(flags, Flags.UPPERCASE);
                    c = Character.toLowerCase(c);
                }
                if (Conversion.isText(c)) {
                    index = -2;
                }
            }
        }

        FormatSpecifier(char conv) {
            c = conv;
            if (Character.isUpperCase(conv)) {
                flags = Flags.UPPERCASE;
                c = Character.toLowerCase(conv);
            }
            if (Conversion.isText(conv)) {
                index = -2;
            }
        }

        FormatSpecifier(String s, Matcher m) {
            index(s, m.start(1), m.end(1));
            flags(s, m.start(2), m.end(2));
            width(s, m.start(3), m.end(3));
            precision(s, m.start(4), m.end(4));

            int tTStart = m.start(5);
            if (tTStart >= 0) {
                dt = true;
                if (s.charAt(tTStart) == 'T') {
                    flags = Flags.add(flags, Flags.UPPERCASE);
                }
            }
            conversion(s.charAt(m.start(6)));

            if (dt)
                checkDateTime();
            else if (Conversion.isGeneral(c))
                checkGeneral();
            else if (Conversion.isCharacter(c))
                checkCharacter();
            else if (Conversion.isInteger(c))
                checkInteger();
            else if (Conversion.isFloat(c))
                checkFloat();
            else if (Conversion.isText(c))
                checkText();
            else
                throw new UnknownFormatConversionException(String.valueOf(c));
        }

        FormatString build(Formatter fmt) {
            if (dt) {
                return fmt.new PrintDateTime(this);
            }
            switch(c) {
                case Conversion.DECIMAL_INTEGER:
                case Conversion.OCTAL_INTEGER:
                case Conversion.HEXADECIMAL_INTEGER:
                    return fmt.new PrintInteger(this);
                case Conversion.DECIMAL_FLOAT:
                case Conversion.SCIENTIFIC:
                case Conversion.GENERAL:
                case Conversion.HEXADECIMAL_FLOAT:
                    return fmt.new PrintFloat(this);
                case Conversion.CHARACTER:
                    return fmt.new PrintCharacter(this);
                case Conversion.BOOLEAN:
                    return new PrintBoolean(this);
                case Conversion.STRING:
                    return new PrintString(this);
                case Conversion.HASHCODE:
                    return new PrintHashCode(this);
                case Conversion.LINE_SEPARATOR:
                    return new FixedString(System.lineSeparator());
                case Conversion.PERCENT_SIGN:
                    return new FixedString("%");
                default:
                    assert false;
            }
            throw new UnknownFormatConversionException(String.valueOf(c));
        }

        private void checkGeneral() {
            if ((c == Conversion.BOOLEAN || c == Conversion.HASHCODE)
                    && Flags.contains(flags, Flags.ALTERNATE))
                failMismatch(Flags.ALTERNATE, c);
            // '-' requires a width
            if (width == -1 && Flags.contains(flags, Flags.LEFT_JUSTIFY))
                throw new MissingFormatWidthException(toString());
            checkBadFlags(Flags.PLUS | Flags.LEADING_SPACE | Flags.ZERO_PAD |
                    Flags.GROUP | Flags.PARENTHESES);
        }

        private void checkDateTime() {
            if (precision != -1)
                throw new IllegalFormatPrecisionException(precision);
            if (!DateTime.isValid(c))
                throw new UnknownFormatConversionException("t" + c);
            checkBadFlags(Flags.ALTERNATE | Flags.PLUS | Flags.LEADING_SPACE |
                    Flags.ZERO_PAD | Flags.GROUP | Flags.PARENTHESES);
            // '-' requires a width
            if (width == -1 && Flags.contains(flags, Flags.LEFT_JUSTIFY))
                throw new MissingFormatWidthException(toString());
        }

        private void checkCharacter() {
            if (precision != -1)
                throw new IllegalFormatPrecisionException(precision);
            checkBadFlags(Flags.ALTERNATE | Flags.PLUS | Flags.LEADING_SPACE |
                    Flags.ZERO_PAD | Flags.GROUP | Flags.PARENTHESES);
            // '-' requires a width
            if (width == -1 && Flags.contains(flags, Flags.LEFT_JUSTIFY))
                throw new MissingFormatWidthException(toString());
        }

        private void checkInteger() {
            checkNumeric();
            if (precision != -1)
                throw new IllegalFormatPrecisionException(precision);

            if (c == Conversion.DECIMAL_INTEGER)
                checkBadFlags(Flags.ALTERNATE);
            else if (c == Conversion.OCTAL_INTEGER)
                checkBadFlags(Flags.GROUP);
            else
                checkBadFlags(Flags.GROUP);
        }

        private void checkBadFlags(int badFlags) {
            if ((flags & badFlags) != 0) {
                failMismatch(flags & badFlags, c);
            }
        }

        private void checkFloat() {
            checkNumeric();
            if (c == Conversion.DECIMAL_FLOAT) {
                assert true;
            } else if (c == Conversion.HEXADECIMAL_FLOAT) {
                checkBadFlags(Flags.PARENTHESES | Flags.GROUP);
            } else if (c == Conversion.SCIENTIFIC) {
                checkBadFlags(Flags.GROUP);
            } else if (c == Conversion.GENERAL) {
                checkBadFlags(Flags.ALTERNATE);
            }
        }

        private void checkNumeric() {
            if (width != -1 && width < 0)
                throw new IllegalFormatWidthException(width);

            if (precision != -1 && precision < 0)
                throw new IllegalFormatPrecisionException(precision);

            // '-' and '0' require a width
            if (width == -1
                    && (Flags.containsAny(flags, Flags.LEFT_JUSTIFY | Flags.ZERO_PAD)))
                throw new MissingFormatWidthException(toString());

            // bad combination
            if ((Flags.contains(flags, Flags.PLUS | Flags.LEADING_SPACE))
                    || (Flags.contains(flags, Flags.LEFT_JUSTIFY | Flags.ZERO_PAD)))
                throw new IllegalFormatFlagsException(Flags.toString(flags));
        }

        private void checkText() {
            if (precision != -1)
                throw new IllegalFormatPrecisionException(precision);
            switch (c) {
                case Conversion.PERCENT_SIGN:
                    if (flags != Flags.LEFT_JUSTIFY
                            && flags != Flags.NONE)
                        throw new IllegalFormatFlagsException(Flags.toString(flags));
                    // '-' requires a width
                    if (width == -1 && Flags.contains(flags, Flags.LEFT_JUSTIFY))
                        throw new MissingFormatWidthException(toString());
                    break;
                case Conversion.LINE_SEPARATOR:
                    if (width != -1)
                        throw new IllegalFormatWidthException(width);
                    if (flags != Flags.NONE)
                        throw new IllegalFormatFlagsException(Flags.toString(flags));
                    break;
                default:
                    assert false;
            }
        }

        public String toString() {
            return Formatter.toString(index, flags, width, precision, dt, c);
        }
    }

    // Caching DecimalFormatSymbols. Non-volatile to avoid thread slamming.
    private static Pair<Locale, DecimalFormatSymbols> DFS = null;
    private static DecimalFormatSymbols getDecimalFormatSymbols(Locale locale) {
        // Capture local copy to avoid thread race.
        Pair<Locale, DecimalFormatSymbols> dfs = DFS;
        if (dfs != null && dfs.first().equals(locale)) {
            return dfs.second();
        }
        // Fetch a new local instance of DecimalFormatSymbols. Note that DFS are mutable
        // and this instance is reserved for Formatter.
        dfs = Pair.of(locale, DecimalFormatSymbols.getInstance(locale));
        // Non-volatile here is acceptable heuristic.
        DFS = dfs;
        return dfs.second();
    }

    // Use zero from cached DecimalFormatSymbols.
    private static char getZero(Locale locale) {
        return locale == null ? '0' : getDecimalFormatSymbols(locale).getZeroDigit();
    }

    // Use decimal separator from cached DecimalFormatSymbols.
    private static char getDecimalSeparator(Locale locale) {
        return locale == null ? '.' : getDecimalFormatSymbols(locale).getDecimalSeparator();
    }

    // Use grouping separator from cached DecimalFormatSymbols.
    private static char getGroupingSeparator(Locale locale) {
        return locale == null ? ',' : getDecimalFormatSymbols(locale).getGroupingSeparator();
    }

    private static abstract class AbstractFormat implements FormatString {
        protected final int index;
        protected final int flags;
        protected final int width;
        protected final int precision;
        protected final boolean dt;
        protected final char c;

        AbstractFormat(FormatSpecifier fmt) {
            index = fmt.index();
            flags = fmt.flags();
            width = fmt.width();
            precision = fmt.precision();
            dt = fmt.isDateTime();
            c = fmt.formatChar();
        }

        @Override
        public int index() {
            return index;
        }

        protected void checkBadFlags(@SuppressWarnings("SameParameterValue") int badFlags) {
            if ((flags & badFlags) != 0) {
                failMismatch(flags & badFlags, c);
            }
        }

        protected void print(StringBuilder sb, CharSequence s, Locale l) {
            if (precision != -1 && precision < s.length())
                s = s.subSequence(0, precision);
            if (Flags.contains(flags, Flags.UPPERCASE))
                s = toUpperCaseWithLocale(s, l);
            appendJustified(sb, s);
        }

        protected int adjustWidth(int width, int flags, boolean neg) {
            int newW = width;
            if (newW != -1 && neg && Flags.contains(flags, Flags.PARENTHESES))
                newW--;
            return newW;
        }

        // Add trailing zeros
        protected void trailingZeros(StringBuilder sb, int nzeros) {
            for (; nzeros > 0; nzeros--) {
                sb.append('0');
            }
        }

        protected CharSequence toUpperCaseWithLocale(CharSequence s, Locale l) {
            if (s instanceof String str) {
                return str.toUpperCase(Objects.requireNonNullElse(l,
                        Locale.getDefault(Locale.Category.FORMAT)));
            } else {
                String str = s.toString();
                String upper = str.toUpperCase(Objects.requireNonNullElse(l,
                        Locale.getDefault(Locale.Category.FORMAT)));
                return str.equals(upper) ? s : upper;
            }
        }

        protected void appendJustified(StringBuilder a, CharSequence cs) {
            if (width == -1) {
                a.append(cs);
                return;
            }
            boolean padRight = Flags.contains(flags, Flags.LEFT_JUSTIFY);
            int sp = width - cs.length();
            if (padRight) {
                a.append(cs);
            }
            for (; sp > 0; sp--) {
                a.append(' ');
            }
            if (!padRight) {
                a.append(cs);
            }
        }

        @SuppressWarnings("UnusedReturnValue")
        protected StringBuilder localizedMagnitude(@SuppressWarnings("SameParameterValue") StringBuilder sb,
                                                   long value, int flags, int width, Locale l) {
            return localizedMagnitude(sb, Long.toString(value, 10), 0, flags, width, l);
        }

        protected StringBuilder localizedMagnitude(StringBuilder sb,
                                                   @Nonnull CharSequence value, final int offset, int f, int width,
                                                   Locale l) {
            if (sb == null) {
                sb = new StringBuilder();
            }
            int begin = sb.length();

            char zero = getZero(l);

            // determine localized grouping separator and size
            char grpSep = '\0';
            int  grpSize = -1;
            char decSep = '\0';

            int len = value.length();
            int dot = len;
            for (int j = offset; j < len; j++) {
                if (value.charAt(j) == '.') {
                    dot = j;
                    break;
                }
            }

            if (dot < len) {
                decSep  = getDecimalSeparator(l);
            }

            if (Flags.contains(f, Flags.GROUP)) {
                grpSep = getGroupingSeparator(l);

                if (l == null || l.equals(Locale.US)) {
                    grpSize = 3;
                } else {
                    NumberFormat nf = NumberFormat.getNumberInstance(l);
                    if (nf instanceof DecimalFormat) {
                        //noinspection PatternVariableCanBeUsed
                        DecimalFormat df = (DecimalFormat) nf;
                        grpSize = df.getGroupingSize();
                    }
                    // Some locales do not use grouping (the number
                    // pattern for these locales does not contain group, e.g.
                    // ("#0.###")), but specify a grouping separator.
                    // To avoid unnecessary identification of the position of
                    // grouping separator, reset its value with null character
                    if (!nf.isGroupingUsed() || grpSize == 0) {
                        grpSep = '\0';
                    }
                }
            }

            // localize the digits inserting group separators as necessary
            for (int j = offset; j < len; j++) {
                if (j == dot) {
                    sb.append(decSep);
                    // no more group separators after the decimal separator
                    grpSep = '\0';
                    continue;
                }

                char c = value.charAt(j);
                sb.append((char) ((c - '0') + zero));
                if (grpSep != '\0' && j != dot - 1 && ((dot - j) % grpSize == 1)) {
                    sb.append(grpSep);
                }
            }

            // apply zero padding
            if (width != -1 && Flags.contains(f, Flags.ZERO_PAD)) {
                for (int k = sb.length(); k < width; k++) {
                    sb.insert(begin, zero);
                }
            }

            return sb;
        }

        // Specialized localization of exponents, where the source value can only
        // contain characters '0' through '9', starting at index offset, and no
        // group separators is added for any locale.
        protected void localizedMagnitudeExp(StringBuilder sb, char[] value,
                                             @SuppressWarnings("SameParameterValue") final int offset,
                                             Locale l) {
            char zero = getZero(l);

            int len = value.length;
            for (int j = offset; j < len; j++) {
                char c = value[j];
                sb.append((char) ((c - '0') + zero));
            }
        }

        @Override
        public String toString() {
            return Formatter.toString(index, flags, width, precision, dt, c);
        }
    }

    private static String toString(int index, int flags, int width, int precision, boolean dt, char c) {
        StringBuilder sb = new StringBuilder("%");
        // Flags.UPPERCASE is set internally for legal conversions.
        sb.append(Flags.toString(Flags.remove(flags, Flags.UPPERCASE)));
        if (index > 0)
            sb.append(index).append('$');
        if (width != -1)
            sb.append(width);
        if (precision != -1)
            sb.append('.').append(precision);
        if (dt)
            sb.append(Flags.contains(flags, Flags.UPPERCASE) ? 'T' : 't');
        sb.append(Flags.contains(flags, Flags.UPPERCASE)
                ? Character.toUpperCase(c) : c);
        return sb.toString();
    }

    private class PrintDateTime extends AbstractFormat {

        PrintDateTime(FormatSpecifier fmt) {
            super(fmt);
        }

        @Override
        public void print(StringBuilder sb, Object arg, Locale l) {
            for (;;) {
                if (arg == null) {
                    print(sb, "null", l);
                    return;
                }
                Calendar cal = null;

                // Instead of Calendar.setLenient(true), perhaps we should
                // wrap the IllegalArgumentException that might be thrown?
                if (arg instanceof Long lv) {
                    // Note that the following method uses an instance of the
                    // default time zone (TimeZone.getDefaultRef().
                    cal = Calendar.getInstance(l == null ? Locale.US : l);
                    cal.setTimeInMillis(lv);
                } else if (arg instanceof LongSupplier ls) {
                    cal = Calendar.getInstance(l == null ? Locale.US : l);
                    cal.setTimeInMillis(ls.getAsLong());
                } else if (arg instanceof Date dv) {
                    // Note that the following method uses an instance of the
                    // default time zone (TimeZone.getDefaultRef().
                    cal = Calendar.getInstance(l == null ? Locale.US : l);
                    cal.setTime(dv);
                } else if (arg instanceof Calendar cv) {
                    cal = (Calendar) cv.clone();
                    cal.setLenient(true);
                } else if (arg instanceof TemporalAccessor tas) {
                    print(tas, c, l, sb);
                    return;
                } else {
                    if (arg instanceof Supplier<?> sv) {
                        Object nextArg = sv.get();
                        if (nextArg != sv) {
                            arg = nextArg;
                            continue;
                        }
                    }
                    failConversion(c, arg);
                }
                // Use the provided locale so that invocations of
                // localizedMagnitude() use optimizations for null.
                print(cal, c, l, sb);
                break;
            }
        }

        private void print(Calendar t, char c, Locale l, StringBuilder a) {
            StringBuilder sb = new StringBuilder();
            print(sb, t, c, l);

            // justify based on width
            if (Flags.contains(flags, Flags.UPPERCASE)) {
                appendJustified(a, toUpperCaseWithLocale(sb, l));
            } else {
                appendJustified(a, sb);
            }
        }

        private StringBuilder print(StringBuilder sb, Calendar t, char c, Locale l) {
            if (sb == null)
                sb = new StringBuilder();
            switch (c) {
            case DateTime.HOUR_OF_DAY_0: // 'H' (00 - 23)
            case DateTime.HOUR_0:        // 'I' (01 - 12)
            case DateTime.HOUR_OF_DAY:   // 'k' (0 - 23) -- like H
            case DateTime.HOUR:        { // 'l' (1 - 12) -- like I
                int i = t.get(Calendar.HOUR_OF_DAY);
                if (c == DateTime.HOUR_0 || c == DateTime.HOUR)
                    i = (i == 0 || i == 12 ? 12 : i % 12);
                int flags = (c == DateTime.HOUR_OF_DAY_0
                               || c == DateTime.HOUR_0
                               ? Flags.ZERO_PAD
                               : Flags.NONE);
                sb.append(localizedMagnitude(null, i, flags, 2, l));
                break;
            }
            case DateTime.MINUTE:      { // 'M' (00 - 59)
                int i = t.get(Calendar.MINUTE);
                sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 2, l));
                break;
            }
            case DateTime.NANOSECOND:  { // 'N' (000000000 - 999999999)
                int i = t.get(Calendar.MILLISECOND) * 1000000;
                sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 9, l));
                break;
            }
            case DateTime.MILLISECOND: { // 'L' (000 - 999)
                int i = t.get(Calendar.MILLISECOND);
                sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 3, l));
                break;
            }
            case DateTime.MILLISECOND_SINCE_EPOCH: { // 'Q' (0 - 99...?)
                long i = t.getTimeInMillis();
                sb.append(localizedMagnitude(null, i, Flags.NONE, width, l));
                break;
            }
            case DateTime.AM_PM:       { // 'p' (am or pm)
                // Calendar.AM = 0, Calendar.PM = 1, LocaleElements defines upper
                String[] ampm = { "AM", "PM" };
                if (l != null && l != Locale.US) {
                    DateFormatSymbols dfs = DateFormatSymbols.getInstance(l);
                    ampm = dfs.getAmPmStrings();
                }
                String s = ampm[t.get(Calendar.AM_PM)];
                sb.append(s.toLowerCase(Objects.requireNonNullElse(l,
                            Locale.getDefault(Locale.Category.FORMAT))));
                break;
            }
            case DateTime.SECONDS_SINCE_EPOCH: { // 's' (0 - 99...?)
                long i = t.getTimeInMillis() / 1000;
                sb.append(localizedMagnitude(null, i, Flags.NONE, width, l));
                break;
            }
            case DateTime.SECOND:      { // 'S' (00 - 60 - leap second)
                int i = t.get(Calendar.SECOND);
                sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 2, l));
                break;
            }
            case DateTime.ZONE_NUMERIC: { // 'z' ({-|+}####) - ls minus?
                int i = t.get(Calendar.ZONE_OFFSET) + t.get(Calendar.DST_OFFSET);
                boolean neg = i < 0;
                sb.append(neg ? '-' : '+');
                if (neg)
                    i = -i;
                int min = i / 60000;
                // combine minute and hour into a single integer
                int offset = (min / 60) * 100 + (min % 60);
                sb.append(localizedMagnitude(null, offset, Flags.ZERO_PAD, 4, l));
                break;
            }
            case DateTime.ZONE:        { // 'Z' (symbol)
                TimeZone tz = t.getTimeZone();
                sb.append(tz.getDisplayName((t.get(Calendar.DST_OFFSET) != 0),
                                           TimeZone.SHORT,
                                           Objects.requireNonNullElse(l, Locale.US)));
                break;
            }

            // Date
            case DateTime.NAME_OF_DAY_ABBREV:     // 'a'
            case DateTime.NAME_OF_DAY:          { // 'A'
                int i = t.get(Calendar.DAY_OF_WEEK);
                Locale lt = Objects.requireNonNullElse(l, Locale.US);
                DateFormatSymbols dfs = DateFormatSymbols.getInstance(lt);
                if (c == DateTime.NAME_OF_DAY)
                    sb.append(dfs.getWeekdays()[i]);
                else
                    sb.append(dfs.getShortWeekdays()[i]);
                break;
            }
            case DateTime.NAME_OF_MONTH_ABBREV:   // 'b'
            case DateTime.NAME_OF_MONTH_ABBREV_X: // 'h' -- same b
            case DateTime.NAME_OF_MONTH:        { // 'B'
                int i = t.get(Calendar.MONTH);
                Locale lt = Objects.requireNonNullElse(l, Locale.US);
                DateFormatSymbols dfs = DateFormatSymbols.getInstance(lt);
                if (c == DateTime.NAME_OF_MONTH)
                    sb.append(dfs.getMonths()[i]);
                else
                    sb.append(dfs.getShortMonths()[i]);
                break;
            }
            case DateTime.CENTURY:                // 'C' (00 - 99)
            case DateTime.YEAR_2:                 // 'y' (00 - 99)
            case DateTime.YEAR_4:               { // 'Y' (0000 - 9999)
                int i = t.get(Calendar.YEAR);
                int size = 2;
                switch (c) {
                    case DateTime.CENTURY -> i /= 100;
                    case DateTime.YEAR_2  -> i %= 100;
                    case DateTime.YEAR_4  -> size = 4;
                }
                sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, size, l));
                break;
            }
            case DateTime.DAY_OF_MONTH_0:         // 'd' (01 - 31)
            case DateTime.DAY_OF_MONTH:         { // 'e' (1 - 31) -- like d
                int i = t.get(Calendar.DATE);
                int flags = (c == DateTime.DAY_OF_MONTH_0
                               ? Flags.ZERO_PAD
                               : Flags.NONE);
                sb.append(localizedMagnitude(null, i, flags, 2, l));
                break;
            }
            case DateTime.DAY_OF_YEAR:          { // 'j' (001 - 366)
                int i = t.get(Calendar.DAY_OF_YEAR);
                sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 3, l));
                break;
            }
            case DateTime.MONTH:                { // 'm' (01 - 12)
                int i = t.get(Calendar.MONTH) + 1;
                sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 2, l));
                break;
            }

            // Composites
            case DateTime.TIME:         // 'T' (24 hour hh:mm:ss - %tH:%tM:%tS)
            case DateTime.TIME_24_HOUR:    { // 'R' (hh:mm same as %H:%M)
                char sep = ':';
                print(sb, t, DateTime.HOUR_OF_DAY_0, l).append(sep);
                print(sb, t, DateTime.MINUTE, l);
                if (c == DateTime.TIME) {
                    sb.append(sep);
                    print(sb, t, DateTime.SECOND, l);
                }
                break;
            }
            case DateTime.TIME_12_HOUR:    { // 'r' (hh:mm:ss [AP]M)
                char sep = ':';
                print(sb, t, DateTime.HOUR_0, l).append(sep);
                print(sb, t, DateTime.MINUTE, l).append(sep);
                print(sb, t, DateTime.SECOND, l).append(' ');
                // this may be in wrong place for some locales
                StringBuilder tsb = new StringBuilder();
                print(tsb, t, DateTime.AM_PM, l);

                sb.append(toUpperCaseWithLocale(tsb, l));
                break;
            }
            case DateTime.DATE_TIME:    { // 'c' (Sat Nov 04 12:02:33 EST 1999)
                char sep = ' ';
                print(sb, t, DateTime.NAME_OF_DAY_ABBREV, l).append(sep);
                print(sb, t, DateTime.NAME_OF_MONTH_ABBREV, l).append(sep);
                print(sb, t, DateTime.DAY_OF_MONTH_0, l).append(sep);
                print(sb, t, DateTime.TIME, l).append(sep);
                print(sb, t, DateTime.ZONE, l).append(sep);
                print(sb, t, DateTime.YEAR_4, l);
                break;
            }
            case DateTime.DATE:            { // 'D' (mm/dd/yy)
                char sep = '/';
                print(sb, t, DateTime.MONTH, l).append(sep);
                print(sb, t, DateTime.DAY_OF_MONTH_0, l).append(sep);
                print(sb, t, DateTime.YEAR_2, l);
                break;
            }
            case DateTime.ISO_STANDARD_DATE: { // 'F' (%Y-%m-%d)
                char sep = '-';
                print(sb, t, DateTime.YEAR_4, l).append(sep);
                print(sb, t, DateTime.MONTH, l).append(sep);
                print(sb, t, DateTime.DAY_OF_MONTH_0, l);
                break;
            }
            default:
                assert false;
            }
            return sb;
        }

        private void print(TemporalAccessor t, char c, Locale l, StringBuilder a) {
            StringBuilder sb = new StringBuilder();
            print(sb, t, c, l);
            // justify based on width
            if (Flags.contains(flags, Flags.UPPERCASE)) {
                appendJustified(a, toUpperCaseWithLocale(sb, l));
            } else {
                appendJustified(a, sb);
            }
        }

        private StringBuilder print(StringBuilder sb, TemporalAccessor t, char c,
                                 Locale l) {
            if (sb == null)
                sb = new StringBuilder();
            try {
                switch (c) {
                    case DateTime.HOUR_OF_DAY_0: {  // 'H' (00 - 23)
                        int i = t.get(ChronoField.HOUR_OF_DAY);
                        sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 2, l));
                        break;
                    }
                    case DateTime.HOUR_OF_DAY: {   // 'k' (0 - 23) -- like H
                        int i = t.get(ChronoField.HOUR_OF_DAY);
                        sb.append(localizedMagnitude(null, i, Flags.NONE, 2, l));
                        break;
                    }
                    case DateTime.HOUR_0:      {  // 'I' (01 - 12)
                        int i = t.get(ChronoField.CLOCK_HOUR_OF_AMPM);
                        sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 2, l));
                        break;
                    }
                    case DateTime.HOUR:        { // 'l' (1 - 12) -- like I
                        int i = t.get(ChronoField.CLOCK_HOUR_OF_AMPM);
                        sb.append(localizedMagnitude(null, i, Flags.NONE, 2, l));
                        break;
                    }
                    case DateTime.MINUTE:      { // 'M' (00 - 59)
                        int i = t.get(ChronoField.MINUTE_OF_HOUR);
                        sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 2, l));
                        break;
                    }
                    case DateTime.NANOSECOND:  { // 'N' (000000000 - 999999999)
                        int i;
                        try {
                            i = t.get(ChronoField.NANO_OF_SECOND);
                        } catch (UnsupportedTemporalTypeException u) {
                            i = t.get(ChronoField.MILLI_OF_SECOND) * 1000000;
                        }
                        sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 9, l));
                        break;
                    }
                    case DateTime.MILLISECOND: { // 'L' (000 - 999)
                        int i = t.get(ChronoField.MILLI_OF_SECOND);
                        sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 3, l));
                        break;
                    }
                    case DateTime.MILLISECOND_SINCE_EPOCH: { // 'Q' (0 - 99...?)
                        long i = t.getLong(ChronoField.INSTANT_SECONDS) * 1000L +
                                t.getLong(ChronoField.MILLI_OF_SECOND);
                        sb.append(localizedMagnitude(null, i, Flags.NONE, width, l));
                        break;
                    }
                    case DateTime.AM_PM:       { // 'p' (am or pm)
                        // Calendar.AM = 0, Calendar.PM = 1, LocaleElements defines upper
                        String[] ampm = { "AM", "PM" };
                        if (l != null && l != Locale.US) {
                            DateFormatSymbols dfs = DateFormatSymbols.getInstance(l);
                            ampm = dfs.getAmPmStrings();
                        }
                        String s = ampm[t.get(ChronoField.AMPM_OF_DAY)];
                        sb.append(s.toLowerCase(Objects.requireNonNullElse(l,
                                Locale.getDefault(Locale.Category.FORMAT))));
                        break;
                    }
                    case DateTime.SECONDS_SINCE_EPOCH: { // 's' (0 - 99...?)
                        long i = t.getLong(ChronoField.INSTANT_SECONDS);
                        sb.append(localizedMagnitude(null, i, Flags.NONE, width, l));
                        break;
                    }
                    case DateTime.SECOND:      { // 'S' (00 - 60 - leap second)
                        int i = t.get(ChronoField.SECOND_OF_MINUTE);
                        sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 2, l));
                        break;
                    }
                    case DateTime.ZONE_NUMERIC: { // 'z' ({-|+}####) - ls minus?
                        int i = t.get(ChronoField.OFFSET_SECONDS);
                        boolean neg = i < 0;
                        sb.append(neg ? '-' : '+');
                        if (neg)
                            i = -i;
                        int min = i / 60;
                        // combine minute and hour into a single integer
                        int offset = (min / 60) * 100 + (min % 60);
                        sb.append(localizedMagnitude(null, offset, Flags.ZERO_PAD, 4, l));
                        break;
                    }
                    case DateTime.ZONE:        { // 'Z' (symbol)
                        ZoneId zid = t.query(TemporalQueries.zone());
                        if (zid == null) {
                            throw new IllegalFormatConversionException(c, t.getClass());
                        }
                        if (!(zid instanceof ZoneOffset) &&
                                t.isSupported(ChronoField.INSTANT_SECONDS)) {
                            Instant instant = Instant.from(t);
                            sb.append(TimeZone.getTimeZone(zid.getId())
                                    .getDisplayName(zid.getRules().isDaylightSavings(instant),
                                            TimeZone.SHORT,
                                            Objects.requireNonNullElse(l, Locale.US)));
                            break;
                        }
                        sb.append(zid.getId());
                        break;
                    }
                    // Date
                    case DateTime.NAME_OF_DAY_ABBREV:     // 'a'
                    case DateTime.NAME_OF_DAY:          { // 'A'
                        int i = t.get(ChronoField.DAY_OF_WEEK) % 7 + 1;
                        Locale lt = Objects.requireNonNullElse(l, Locale.US);
                        DateFormatSymbols dfs = DateFormatSymbols.getInstance(lt);
                        if (c == DateTime.NAME_OF_DAY)
                            sb.append(dfs.getWeekdays()[i]);
                        else
                            sb.append(dfs.getShortWeekdays()[i]);
                        break;
                    }
                    case DateTime.NAME_OF_MONTH_ABBREV:   // 'b'
                    case DateTime.NAME_OF_MONTH_ABBREV_X: // 'h' -- same b
                    case DateTime.NAME_OF_MONTH:        { // 'B'
                        int i = t.get(ChronoField.MONTH_OF_YEAR) - 1;
                        Locale lt = Objects.requireNonNullElse(l, Locale.US);
                        DateFormatSymbols dfs = DateFormatSymbols.getInstance(lt);
                        if (c == DateTime.NAME_OF_MONTH)
                            sb.append(dfs.getMonths()[i]);
                        else
                            sb.append(dfs.getShortMonths()[i]);
                        break;
                    }
                    case DateTime.CENTURY:                // 'C' (00 - 99)
                    case DateTime.YEAR_2:                 // 'y' (00 - 99)
                    case DateTime.YEAR_4:               { // 'Y' (0000 - 9999)
                        int i = t.get(ChronoField.YEAR_OF_ERA);
                        int size = 2;
                        switch (c) {
                            case DateTime.CENTURY -> i /= 100;
                            case DateTime.YEAR_2  -> i %= 100;
                            case DateTime.YEAR_4  -> size = 4;
                        }
                        sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, size, l));
                        break;
                    }
                    case DateTime.DAY_OF_MONTH_0:         // 'd' (01 - 31)
                    case DateTime.DAY_OF_MONTH:         { // 'e' (1 - 31) -- like d
                        int i = t.get(ChronoField.DAY_OF_MONTH);
                        int flags = (c == DateTime.DAY_OF_MONTH_0
                                ? Flags.ZERO_PAD
                                : Flags.NONE);
                        sb.append(localizedMagnitude(null, i, flags, 2, l));
                        break;
                    }
                    case DateTime.DAY_OF_YEAR:          { // 'j' (001 - 366)
                        int i = t.get(ChronoField.DAY_OF_YEAR);
                        sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 3, l));
                        break;
                    }
                    case DateTime.MONTH:                { // 'm' (01 - 12)
                        int i = t.get(ChronoField.MONTH_OF_YEAR);
                        sb.append(localizedMagnitude(null, i, Flags.ZERO_PAD, 2, l));
                        break;
                    }

                    // Composites
                    case DateTime.TIME:         // 'T' (24 hour hh:mm:ss - %tH:%tM:%tS)
                    case DateTime.TIME_24_HOUR:    { // 'R' (hh:mm same as %H:%M)
                        char sep = ':';
                        print(sb, t, DateTime.HOUR_OF_DAY_0, l).append(sep);
                        print(sb, t, DateTime.MINUTE, l);
                        if (c == DateTime.TIME) {
                            sb.append(sep);
                            print(sb, t, DateTime.SECOND, l);
                        }
                        break;
                    }
                    case DateTime.TIME_12_HOUR:    { // 'r' (hh:mm:ss [AP]M)
                        char sep = ':';
                        print(sb, t, DateTime.HOUR_0, l).append(sep);
                        print(sb, t, DateTime.MINUTE, l).append(sep);
                        print(sb, t, DateTime.SECOND, l).append(' ');
                        // this may be in wrong place for some locales
                        StringBuilder tsb = new StringBuilder();
                        print(tsb, t, DateTime.AM_PM, l);
                        sb.append(toUpperCaseWithLocale(tsb, l));
                        break;
                    }
                    case DateTime.DATE_TIME:    { // 'c' (Sat Nov 04 12:02:33 EST 1999)
                        char sep = ' ';
                        print(sb, t, DateTime.NAME_OF_DAY_ABBREV, l).append(sep);
                        print(sb, t, DateTime.NAME_OF_MONTH_ABBREV, l).append(sep);
                        print(sb, t, DateTime.DAY_OF_MONTH_0, l).append(sep);
                        print(sb, t, DateTime.TIME, l).append(sep);
                        print(sb, t, DateTime.ZONE, l).append(sep);
                        print(sb, t, DateTime.YEAR_4, l);
                        break;
                    }
                    case DateTime.DATE:            { // 'D' (mm/dd/yy)
                        char sep = '/';
                        print(sb, t, DateTime.MONTH, l).append(sep);
                        print(sb, t, DateTime.DAY_OF_MONTH_0, l).append(sep);
                        print(sb, t, DateTime.YEAR_2, l);
                        break;
                    }
                    case DateTime.ISO_STANDARD_DATE: { // 'F' (%Y-%m-%d)
                        char sep = '-';
                        print(sb, t, DateTime.YEAR_4, l).append(sep);
                        print(sb, t, DateTime.MONTH, l).append(sep);
                        print(sb, t, DateTime.DAY_OF_MONTH_0, l);
                        break;
                    }
                    default:
                        assert false;
                }
            } catch (DateTimeException x) {
                throw new IllegalFormatConversionException(c, t.getClass());
            }
            return sb;
        }

    }

    private class PrintFloat extends PrintInteger {

        PrintFloat(FormatSpecifier fmt) {
            super(fmt);
        }

        private void print(StringBuilder sb, float value, Locale l) {
            print(sb, (double) value, l);
        }

        private void print(StringBuilder out, BigDecimal value, Locale l) {
            if (c == Conversion.HEXADECIMAL_FLOAT) {
                failConversion(c, value);
            }
            StringBuilder sb = new StringBuilder();
            boolean neg = value.signum() == -1;
            BigDecimal v = value.abs();
            // leading sign indicator
            leadingSign(sb, neg);
            // the value
            print(sb, v, l, flags, c, precision, neg);
            // trailing sign indicator
            trailingSign(sb, neg);
            // justify based on width
            appendJustified(out, sb);
        }

        private void print(StringBuilder out, double value, Locale l) {
            StringBuilder sb = new StringBuilder();
            boolean neg = Double.compare(value, 0.0) == -1;

            if (!Double.isNaN(value)) {
                double v = Math.abs(value);

                // leading sign indicator
                leadingSign(sb, neg);

                // the value
                if (!Double.isInfinite(v))
                    print(sb, v, l, flags, c, precision, neg);
                else
                    sb.append(Flags.contains(flags, Flags.UPPERCASE)
                            ? "INFINITY" : "Infinity");

                // trailing sign indicator
                trailingSign(sb, neg);
            } else {
                sb.append(Flags.contains(flags, Flags.UPPERCASE) ? "NAN" : "NaN");
            }

            // justify based on width
            appendJustified(out, sb);
        }

        // !Double.isInfinite(value) && !Double.isNaN(value)
        protected void print(StringBuilder sb, double value, Locale l,
                             int flags, char c, int precision, boolean neg) {
            if (c == Conversion.SCIENTIFIC) {
                print(sb, BigDecimal.valueOf(value), l, flags, c, precision, neg);
            } else if (c == Conversion.DECIMAL_FLOAT) {
                print(sb, BigDecimal.valueOf(value), l, flags, c, precision, neg);
            } else if (c == Conversion.GENERAL) {
                print(sb, BigDecimal.valueOf(value), l, flags, c, precision, neg);
            } else if (c == Conversion.HEXADECIMAL_FLOAT) {
                int prec = precision;
                if (precision == -1)
                    // assume that we want all of the digits
                    prec = 0;
                else if (precision == 0)
                    prec = 1;

                String s = hexDouble(value, prec);

                StringBuilder va = new StringBuilder();
                boolean upper = Flags.contains(flags, Flags.UPPERCASE);
                sb.append(upper ? "0X" : "0x");

                if (Flags.contains(flags, Flags.ZERO_PAD)) {
                    int leadingCharacters = 2;
                    if(Flags.contains(flags, Flags.LEADING_SPACE) ||
                            Flags.contains(flags, Flags.PLUS) || neg) {
                        leadingCharacters = 3;
                    }
                    trailingZeros(sb, width - s.length() - leadingCharacters);
                }

                int idx = s.indexOf('p');
                if (upper) {
                    String tmp = s.substring(0, idx);
                    // don't localize hex
                    tmp = tmp.toUpperCase(Locale.ROOT);
                    va.append(tmp);
                } else {
                    va.append(s, 0, idx);
                }
                if (prec != 0) {
                    addZeros(va, prec);
                }
                sb.append(va);
                sb.append(upper ? 'P' : 'p');
                sb.append(s, idx+1, s.length());
            }
        }

        protected void print(StringBuilder sb, BigDecimal value, Locale l,
                             int flags, char c, int precision, boolean neg) {
            if (c == Conversion.SCIENTIFIC) {
                // Create a new BigDecimal with the desired precision.
                int prec = (precision == -1 ? 6 : precision);
                int scale = value.scale();
                int origPrec = value.precision();
                int nzeros = 0;
                int compPrec;

                if (prec > origPrec - 1) {
                    compPrec = origPrec;
                    nzeros = prec - (origPrec - 1);
                } else {
                    compPrec = prec + 1;
                }

                MathContext mc = new MathContext(compPrec);
                BigDecimal v
                        = new BigDecimal(value.unscaledValue(), scale, mc);

                BigDecimalLayout bdl
                        = new BigDecimalLayout(v.unscaledValue(), v.scale(),
                        java.util.Formatter.BigDecimalLayoutForm.SCIENTIFIC);

                StringBuilder mant = bdl.mantissa();

                // Add a decimal point if necessary.  The mantissa may not
                // contain a decimal point if the scale is zero (the internal
                // representation has no fractional part) or the original
                // precision is one. Append a decimal point if '#' is set or if
                // we require zero padding to get to the requested precision.
                if ((origPrec == 1 || !bdl.hasDot())
                        && (nzeros > 0 || (Flags.contains(flags, Flags.ALTERNATE)))) {
                    mant.append('.');
                }

                // Add trailing zeros in the case precision is greater than
                // the number of available digits after the decimal separator.
                trailingZeros(mant, nzeros);

                StringBuilder exp = bdl.exponent();
                int newW = width;
                if (width != -1) {
                    newW = adjustWidth(width - exp.length() - 1, flags, neg);
                }
                localizedMagnitude(sb, mant, 0, flags, newW, l);

                sb.append(Flags.contains(flags, Flags.UPPERCASE) ? 'E' : 'e');

                flags = Flags.remove(flags, Flags.GROUP);
                char sign = exp.charAt(0);
                assert(sign == '+' || sign == '-');
                sb.append(sign);

                sb.append(localizedMagnitude(null, exp, 1, flags, -1, l));
            } else if (c == Conversion.DECIMAL_FLOAT) {
                // Create a new BigDecimal with the desired precision.
                int prec = (precision == -1 ? 6 : precision);
                int scale = value.scale();

                if (scale > prec) {
                    // more "scale" digits than the requested "precision"
                    int compPrec = value.precision();
                    if (compPrec <= scale) {
                        // case of 0.xxxxxx
                        value = value.setScale(prec, RoundingMode.HALF_UP);
                    } else {
                        compPrec -= (scale - prec);
                        value = new BigDecimal(value.unscaledValue(),
                                scale,
                                new MathContext(compPrec));
                    }
                }
                BigDecimalLayout bdl = new BigDecimalLayout(
                        value.unscaledValue(),
                        value.scale(),
                        java.util.Formatter.BigDecimalLayoutForm.DECIMAL_FLOAT);

                StringBuilder mant = bdl.mantissa();
                int nzeros = (bdl.scale() < prec ? prec - bdl.scale() : 0);

                // Add a decimal point if necessary.  The mantissa may not
                // contain a decimal point if the scale is zero (the internal
                // representation has no fractional part).  Append a decimal
                // point if '#' is set or we require zero padding to get to the
                // requested precision.
                if (bdl.scale() == 0 && (Flags.contains(flags, Flags.ALTERNATE)
                        || nzeros > 0)) {
                    mant.append('.');
                }

                // Add trailing zeros if the precision is greater than the
                // number of available digits after the decimal separator.
                trailingZeros(mant, nzeros);

                localizedMagnitude(sb, mant, 0, flags, adjustWidth(width, flags, neg), l);
            } else if (c == Conversion.GENERAL) {
                int prec = precision;
                if (precision == -1)
                    prec = 6;
                else if (precision == 0)
                    prec = 1;

                value = value.round(new MathContext(prec));
                //noinspection ComparatorResultComparison
                if ((value.equals(BigDecimal.ZERO))
                        || ((value.compareTo(BigDecimal.valueOf(1, 4)) != -1)
                        && (value.compareTo(BigDecimal.valueOf(1, -prec)) == -1))) {

                    int e = - value.scale()
                            + (value.unscaledValue().toString().length() - 1);

                    // xxx.yyy
                    //   g precision (# sig digits) = #x + #y
                    //   f precision = #y
                    //   exponent = #x - 1
                    // => f precision = g precision - exponent - 1
                    // 0.000zzz
                    //   g precision (# sig digits) = #z
                    //   f precision = #0 (after '.') + #z
                    //   exponent = - #0 (after '.') - 1
                    // => f precision = g precision - exponent - 1
                    prec = prec - e - 1;

                    print(sb, value, l, flags, Conversion.DECIMAL_FLOAT, prec,
                            neg);
                } else {
                    print(sb, value, l, flags, Conversion.SCIENTIFIC, prec - 1, neg);
                }
            } else //noinspection RedundantIfStatement
                if (c == Conversion.HEXADECIMAL_FLOAT) {
                // This conversion isn't supported.  The error should be
                // reported earlier.
                assert false;
            }
        }

        @Override
        protected void print(StringBuilder out, long value, Locale l) {
            throw new IllegalStateException();
        }

        // Add zeros to the requested precision.
        protected void addZeros(StringBuilder sb, int prec) {
            // Look for the dot.  If we don't find one, the we'll need to add
            // it before we add the zeros.
            int len = sb.length();
            int i;
            for (i = 0; i < len; i++) {
                if (sb.charAt(i) == '.') {
                    break;
                }
            }
            boolean needDot = i == len;

            // Determine existing precision.
            int outPrec = len - i - (needDot ? 0 : 1);
            assert (outPrec <= prec) : "(outPrec:" + outPrec + ") <= (prec:" + prec + ")";
            if (outPrec == prec) {
                return;
            }

            // Add dot if previously determined to be necessary.
            if (needDot) {
                sb.append('.');
            }

            // Add zeros.
            trailingZeros(sb, prec - outPrec);
        }
        // Method assumes that d > 0.
        protected String hexDouble(double d, int prec) {
            // Let Double.toHexString handle simple cases
            if (!Double.isFinite(d) || d == 0.0 || prec == 0 || prec >= 13) {
                // remove "0x"
                return Double.toHexString(d).substring(2);
            } else {
                //noinspection ConstantValue
                assert (prec >= 1 && prec <= 12);

                int exponent = Math.getExponent(d);
                boolean subnormal
                        = (exponent == Double.MIN_EXPONENT - 1);

                // If this is subnormal input so normalize (could be faster to
                // do as integer operation).
                if (subnormal) {
                    double scaleUp = Math.scalb(1.0, 54);
                    d *= scaleUp;
                    // Calculate the exponent.  This is not just exponent + 54
                    // since the former is not the normalized exponent.
                    exponent = Math.getExponent(d);
                    assert exponent >= Double.MIN_EXPONENT &&
                            exponent <= Double.MAX_EXPONENT : exponent;
                }

                int precision = 1 + prec * 4;
                int shiftDistance
                        = MathUtil.SIGNIFICAND_WIDTH - precision;
                //noinspection ConstantValue
                assert (shiftDistance >= 1 && shiftDistance < MathUtil.SIGNIFICAND_WIDTH);

                long doppel = Double.doubleToLongBits(d);
                // Deterime the number of bits to keep.
                long newSignif
                        = (doppel & (MathUtil.EXP_BIT_MASK
                        | MathUtil.SIGNIF_BIT_MASK))
                        >> shiftDistance;
                // Bits to round away.
                long roundingBits = doppel & ~(~0L << shiftDistance);

                // To decide how to round, look at the low-order bit of the
                // working significand, the highest order discarded bit (the
                // round bit) and whether any of the lower order discarded bits
                // are nonzero (the sticky bit).

                boolean leastZero = (newSignif & 0x1L) == 0L;
                boolean round
                        = ((1L << (shiftDistance - 1)) & roundingBits) != 0L;
                //noinspection ConstantValue
                boolean sticky = shiftDistance > 1 &&
                        (~(1L << (shiftDistance - 1)) & roundingBits) != 0;
                if ((leastZero && round && sticky) || (!leastZero && round)) {
                    newSignif++;
                }

                long signBit = doppel & MathUtil.SIGN_BIT_MASK;
                newSignif = signBit | (newSignif << shiftDistance);
                double result = Double.longBitsToDouble(newSignif);

                if (Double.isInfinite(result)) {
                    // Infinite result generated by rounding
                    return "1.0p1024";
                } else {
                    String res = Double.toHexString(result).substring(2);
                    if (!subnormal)
                        return res;
                    else {
                        // Create a normalized subnormal string.
                        int idx = res.indexOf('p');
                        if (idx == -1) {
                            // No 'p' character in hex string.
                            assert false;
                            throw new IllegalFormatException();
                            //return null;
                        } else {
                            // Get exponent and append at the end.
                            String exp = res.substring(idx + 1);
                            int iexp = Integer.parseInt(exp) - 54;
                            return res.substring(0, idx) + "p"
                                    + Integer.toString(iexp);
                        }
                    }
                }
            }
        }

        @Override
        public void print(StringBuilder sb, Object arg, Locale l) {
            for (;;) {
                if (arg == null)
                    print(sb, "null", l);
                else if (arg instanceof Float fv)
                    print(sb, fv.floatValue(), l);
                else if (arg instanceof Double dv)
                    print(sb, dv.doubleValue(), l);
                else if (arg instanceof BigDecimal bi)
                    print(sb, bi, l);
                else if (arg instanceof DoubleSupplier dv)
                    print(sb, dv.getAsDouble(), l);
                else {
                    if (arg instanceof Supplier<?> sv) {
                        Object nextArg = sv.get();
                        if (nextArg != sv) {
                            arg = nextArg;
                            continue;
                        }
                    }
                    failConversion(c, arg);
                }
                break;
            }
        }
    }

    private class PrintInteger extends AbstractFormat {

        PrintInteger(FormatSpecifier fmt) {
            super(fmt);
        }

        private void print(StringBuilder sb, byte value, Locale l) {
            long v = value;
            if (value < 0
                    && (c == Conversion.OCTAL_INTEGER
                    || c == Conversion.HEXADECIMAL_INTEGER)) {
                v += (1L << 8);
            }
            print(sb, v, l);
        }

        private void print(StringBuilder sb, short value, Locale l) {
            long v = value;
            if (value < 0
                    && (c == Conversion.OCTAL_INTEGER
                    || c == Conversion.HEXADECIMAL_INTEGER)) {
                v += (1L << 16);
                //noinspection ConstantValue
                assert v >= 0 : v;
            }
            print(sb, v, l);
        }

        private void print(StringBuilder sb, int value, Locale l) {
            long v = value;
            if (value < 0
                    && (c == Conversion.OCTAL_INTEGER
                    || c == Conversion.HEXADECIMAL_INTEGER)) {
                v += (1L << 32);
                //noinspection ConstantValue
                assert v >= 0 : v;
            }
            print(sb, v, l);
        }

        protected void print(StringBuilder out, long value, Locale l) {

            StringBuilder sb = new StringBuilder();

            if (c == Conversion.DECIMAL_INTEGER) {
                boolean neg = value < 0;
                String valueStr = Long.toString(value, 10);

                // leading sign indicator
                leadingSign(sb, neg);

                // the value
                localizedMagnitude(sb, valueStr, neg ? 1 : 0, flags, adjustWidth(width, flags, neg), l);

                // trailing sign indicator
                trailingSign(sb, neg);
            } else if (c == Conversion.OCTAL_INTEGER) {
                checkBadFlags(Flags.PARENTHESES | Flags.LEADING_SPACE | Flags.PLUS);
                String s = Long.toOctalString(value);
                int len = (Flags.contains(flags, Flags.ALTERNATE)
                        ? s.length() + 1
                        : s.length());

                // apply ALTERNATE (radix indicator for octal) before ZERO_PAD
                if (Flags.contains(flags, Flags.ALTERNATE))
                    sb.append('0');
                if (Flags.contains(flags, Flags.ZERO_PAD)) {
                    trailingZeros(sb, width - len);
                }
                sb.append(s);
            } else if (c == Conversion.HEXADECIMAL_INTEGER) {
                checkBadFlags(Flags.PARENTHESES | Flags.LEADING_SPACE | Flags.PLUS);
                CharSequence s = Long.toHexString(value);
                int len = (Flags.contains(flags, Flags.ALTERNATE)
                        ? s.length() + 2
                        : s.length());

                // apply ALTERNATE (radix indicator for hex) before ZERO_PAD
                if (Flags.contains(flags, Flags.ALTERNATE))
                    sb.append(Flags.contains(flags, Flags.UPPERCASE) ? "0X" : "0x");
                if (Flags.contains(flags, Flags.ZERO_PAD)) {
                    trailingZeros(sb, width - len);
                }
                if (Flags.contains(flags, Flags.UPPERCASE))
                    s = toUpperCaseWithLocale(s, l);
                sb.append(s);
            }

            // justify based on width
            appendJustified(out, sb);
        }

        // neg := val < 0
        @SuppressWarnings("UnusedReturnValue")
        StringBuilder leadingSign(StringBuilder sb, boolean neg) {
            if (!neg) {
                if (Flags.contains(flags, Flags.PLUS)) {
                    sb.append('+');
                } else if (Flags.contains(flags, Flags.LEADING_SPACE)) {
                    sb.append(' ');
                }
            } else {
                if (Flags.contains(flags, Flags.PARENTHESES))
                    sb.append('(');
                else
                    sb.append('-');
            }
            return sb;
        }

        // neg := val < 0
        @SuppressWarnings("UnusedReturnValue")
        StringBuilder trailingSign(StringBuilder sb, boolean neg) {
            if (neg && Flags.contains(flags, Flags.PARENTHESES))
                sb.append(')');
            return sb;
        }

        private void print(StringBuilder out, BigInteger value, Locale l) {
            StringBuilder sb = new StringBuilder();
            boolean neg = value.signum() == -1;
            BigInteger v = value.abs();

            // leading sign indicator
            leadingSign(sb, neg);

            // the value
            if (c == Conversion.DECIMAL_INTEGER) {
                localizedMagnitude(sb, v.toString(), 0, flags, adjustWidth(width, flags, neg), l);
            } else if (c == Conversion.OCTAL_INTEGER) {
                String s = v.toString(8);

                int len = s.length() + sb.length();
                if (neg && Flags.contains(flags, Flags.PARENTHESES))
                    len++;

                // apply ALTERNATE (radix indicator for octal) before ZERO_PAD
                if (Flags.contains(flags, Flags.ALTERNATE)) {
                    len++;
                    sb.append('0');
                }
                if (Flags.contains(flags, Flags.ZERO_PAD)) {
                    trailingZeros(sb, width - len);
                }
                sb.append(s);
            } else if (c == Conversion.HEXADECIMAL_INTEGER) {
                CharSequence s = v.toString(16);

                int len = s.length() + sb.length();
                if (neg && Flags.contains(flags, Flags.PARENTHESES))
                    len++;

                // apply ALTERNATE (radix indicator for hex) before ZERO_PAD
                if (Flags.contains(flags, Flags.ALTERNATE)) {
                    len += 2;
                    sb.append(Flags.contains(flags, Flags.UPPERCASE) ? "0X" : "0x");
                }
                if (Flags.contains(flags, Flags.ZERO_PAD)) {
                    trailingZeros(sb, width - len);
                }
                if (Flags.contains(flags, Flags.UPPERCASE))
                    s = toUpperCaseWithLocale(s, l);
                sb.append(s);
            }

            // trailing sign indicator
            trailingSign(sb, (value.signum() == -1));

            // justify based on width
            appendJustified(out, sb);
        }

        @Override
        public void print(StringBuilder sb, Object arg, Locale l) {
            for (;;) {
                if (arg == null)
                    print(sb, "null", l);
                else if (arg instanceof Byte bv)
                    print(sb, bv.byteValue(), l);
                else if (arg instanceof Short sv)
                    print(sb, sv.shortValue(), l);
                else if (arg instanceof Integer iv)
                    print(sb, iv.intValue(), l);
                else if (arg instanceof Long lv)
                    print(sb, lv.longValue(), l);
                else if (arg instanceof BigInteger bi)
                    print(sb, bi, l);
                else if (arg instanceof LongSupplier lv)
                    print(sb, lv.getAsLong(), l);
                else if (arg instanceof IntSupplier iv)
                    print(sb, iv.getAsInt(), l);
                else {
                    if (arg instanceof Supplier<?> sv) {
                        Object nextArg = sv.get();
                        if (nextArg != sv) {
                            arg = nextArg;
                            continue;
                        }
                    }
                    failConversion(c, arg);
                }
                break;
            }
        }

        protected class BigDecimalLayout {
            private StringBuilder mant;
            private StringBuilder exp;
            private boolean dot = false;
            private int scale;

            public BigDecimalLayout(BigInteger intVal, int scale, java.util.Formatter.BigDecimalLayoutForm form) {
                layout(intVal, scale, form);
            }

            public boolean hasDot() {
                return dot;
            }

            public int scale() {
                return scale;
            }

            public StringBuilder mantissa() {
                return mant;
            }

            // The exponent will be formatted as a sign ('+' or '-') followed
            // by the exponent zero-padded to include at least two digits.
            public StringBuilder exponent() {
                return exp;
            }

            private void layout(BigInteger intVal, int scale, java.util.Formatter.BigDecimalLayoutForm form) {
                String coeff = intVal.toString();
                this.scale = scale;

                // Construct a buffer, with sufficient capacity for all cases.
                // If E-notation is needed, length will be: +1 if negative, +1
                // if '.' needed, +2 for "E+", + up to 10 for adjusted
                // exponent.  Otherwise it could have +1 if negative, plus
                // leading "0.00000"
                int len = coeff.length();
                mant = new StringBuilder(len + 14);

                if (scale == 0) {
                    if (len > 1) {
                        mant.append(coeff.charAt(0));
                        if (form == java.util.Formatter.BigDecimalLayoutForm.SCIENTIFIC) {
                            mant.append('.');
                            dot = true;
                            mant.append(coeff, 1, len);
                            exp = new StringBuilder("+");
                            if (len < 10) {
                                exp.append('0').append(len - 1);
                            } else {
                                exp.append(len - 1);
                            }
                        } else {
                            mant.append(coeff, 1, len);
                        }
                    } else {
                        mant.append(coeff);
                        if (form == java.util.Formatter.BigDecimalLayoutForm.SCIENTIFIC) {
                            exp = new StringBuilder("+00");
                        }
                    }
                } else if (form == java.util.Formatter.BigDecimalLayoutForm.DECIMAL_FLOAT) {
                    // count of padding zeros

                    if (scale >= len) {
                        // 0.xxx form
                        mant.append("0.");
                        dot = true;
                        trailingZeros(mant, scale - len);
                        mant.append(coeff);
                    } else {
                        if (scale > 0) {
                            // xx.xx form
                            int pad = len - scale;
                            mant.append(coeff, 0, pad);
                            mant.append('.');
                            dot = true;
                            mant.append(coeff, pad, len);
                        } else { // scale < 0
                            // xx form
                            mant.append(coeff, 0, len);
                            if (intVal.signum() != 0) {
                                trailingZeros(mant, -scale);
                            }
                            this.scale = 0;
                        }
                    }
                } else {
                    // x.xxx form
                    mant.append(coeff.charAt(0));
                    if (len > 1) {
                        mant.append('.');
                        dot = true;
                        mant.append(coeff, 1, len);
                    }
                    exp = new StringBuilder();
                    long adjusted = -(long) scale + (len - 1);
                    if (adjusted != 0) {
                        long abs = Math.abs(adjusted);
                        // require sign
                        exp.append(adjusted < 0 ? '-' : '+');
                        if (abs < 10) {
                            exp.append('0');
                        }
                        exp.append(abs);
                    } else {
                        exp.append("+00");
                    }
                }
            }
        }
    }

    private class PrintCharacter extends AbstractFormat {

        PrintCharacter(FormatSpecifier fmt) {
            super(fmt);
        }

        @Override
        public void print(StringBuilder sb, Object arg, Locale l) {
            for (;;) {
                if (arg == null) {
                    print(sb, "null", l);
                    return;
                }
                String s = null;
                if (arg instanceof Character ch) {
                    s = ch.toString();
                } else if (arg instanceof Byte bv) {
                    byte i = bv;
                    if (Character.isValidCodePoint(i))
                        s = new String(Character.toChars(i));
                    else
                        throw new IllegalFormatCodePointException(i);
                } else if (arg instanceof Short sv) {
                    short i = sv;
                    if (Character.isValidCodePoint(i))
                        s = new String(Character.toChars(i));
                    else
                        throw new IllegalFormatCodePointException(i);
                } else if (arg instanceof Integer iv) {
                    int i = iv;
                    if (Character.isValidCodePoint(i))
                        s = new String(Character.toChars(i));
                    else
                        throw new IllegalFormatCodePointException(i);
                } else if (arg instanceof IntSupplier iv) {
                    int i = iv.getAsInt();
                    if (Character.isValidCodePoint(i))
                        s = new String(Character.toChars(i));
                    else
                        throw new IllegalFormatCodePointException(i);
                } else {
                    if (arg instanceof Supplier<?> sv) {
                        Object nextArg = sv.get();
                        if (nextArg != sv) {
                            arg = nextArg;
                            continue;
                        }
                    }
                    failConversion(c, arg);
                }
                print(sb, s, l);
                break;
            }
        }
    }

    private static class PrintString extends AbstractFormat {
        PrintString(FormatSpecifier fmt) {
            super(fmt);
        }

        @Override
        public void print(StringBuilder sb, Object arg, Locale l) {
            for (;;) {
                if (arg instanceof Formattable fv) {
                    java.util.Formatter fmt = new java.util.Formatter(sb, l);
                    fv.formatTo(fmt, flags, width, precision);
                } else {
                    if (Flags.contains(flags, Flags.ALTERNATE))
                        failMismatch(Flags.ALTERNATE, 's');
                    if (arg == null)
                        print(sb, "null", l);
                    else if (arg instanceof CharSequence cs)
                        print(sb, cs, l);
                    else {
                        if (arg instanceof Supplier<?> sv) {
                            Object nextArg = sv.get();
                            if (nextArg != sv) {
                                arg = nextArg;
                                continue;
                            }
                        }
                        print(sb, arg.toString(), l);
                    }
                }
                break;
            }
        }
    }

    private static class PrintBoolean extends AbstractFormat {

        PrintBoolean(FormatSpecifier fmt) {
            super(fmt);
        }

        @Override
        public void print(StringBuilder sb, Object arg, Locale l) {
            for (;;) {
                String s;
                if (arg instanceof BooleanSupplier bs)
                    s = Boolean.toString(bs.getAsBoolean());
                if (arg instanceof Supplier<?> sv) {
                    Object nextArg = sv.get();
                    if (nextArg != sv) {
                        arg = nextArg;
                        continue;
                    }
                    s = Boolean.toString(true);
                } else if (arg != null)
                    s = ((arg instanceof Boolean bv)
                            ? bv.toString()
                            : Boolean.toString(true));
                else
                    s = Boolean.toString(false);
                print(sb, s, l);
                break;
            }
        }
    }

    private static class PrintHashCode extends AbstractFormat {

        PrintHashCode(FormatSpecifier fmt) {
            super(fmt);
        }

        @Override
        public void print(StringBuilder sb, Object arg, Locale l) {
            String s = (arg == null
                    ? "null"
                    : Integer.toHexString(arg.hashCode()));
            print(sb, s, l);
        }
    }

    private static class Flags {

        static final int NONE          = 0;      // ''

        // duplicate declarations from Formattable.java
        @SuppressWarnings("PointlessBitwiseExpression")
        static final int LEFT_JUSTIFY  = 1<<0;   // '-'
        static final int UPPERCASE     = 1<<1;   // '^'
        static final int ALTERNATE     = 1<<2;   // '#'

        // numerics
        static final int PLUS          = 1<<3;   // '+'
        static final int LEADING_SPACE = 1<<4;   // ' '
        static final int ZERO_PAD      = 1<<5;   // '0'
        static final int GROUP         = 1<<6;   // ','
        static final int PARENTHESES   = 1<<7;   // '('

        // indexing
        static final int PREVIOUS      = 1<<8;   // '<'

        public static boolean contains(int flags, int f) {
            return (flags & f) == f;
        }

        public static boolean containsAny(int flags, int f) {
            return (flags & f) != 0;
        }

        private static int add(int flags, int f) {
            return flags | f;
        }

        public static int remove(int flags, int f) {
            return flags & ~f;
        }

        public static int parse(String s, int start, int end) {
            int f = 0;
            for (int i = start; i < end; i++) {
                char c = s.charAt(i);
                int v = parse(c);
                if (contains(f, v))
                    throw new DuplicateFormatFlagsException(toString(v));
                f = add(f, v);
            }
            return f;
        }

        // parse those flags which may be provided by users
        private static int parse(char c) {
            return switch (c) {
                case '-' -> LEFT_JUSTIFY;
                case '#' -> ALTERNATE;
                case '+' -> PLUS;
                case ' ' -> LEADING_SPACE;
                case '0' -> ZERO_PAD;
                case ',' -> GROUP;
                case '(' -> PARENTHESES;
                case '<' -> PREVIOUS;
                default -> throw new UnknownFormatFlagsException(String.valueOf(c));
            };
        }

        // Returns a string representation of the current {@code Flags}.
        public static String toString(int f) {
            StringBuilder sb = new StringBuilder();
            if (contains(f, LEFT_JUSTIFY))  sb.append('-');
            if (contains(f, UPPERCASE))     sb.append('^');
            if (contains(f, ALTERNATE))     sb.append('#');
            if (contains(f, PLUS))          sb.append('+');
            if (contains(f, LEADING_SPACE)) sb.append(' ');
            if (contains(f, ZERO_PAD))      sb.append('0');
            if (contains(f, GROUP))         sb.append(',');
            if (contains(f, PARENTHESES))   sb.append('(');
            if (contains(f, PREVIOUS))      sb.append('<');
            return sb.toString();
        }
    }

    private static class Conversion {
        // Byte, Short, Integer, Long, BigInteger
        // (and associated primitives due to autoboxing)
        static final char DECIMAL_INTEGER     = 'd';
        static final char OCTAL_INTEGER       = 'o';
        static final char HEXADECIMAL_INTEGER = 'x';
        static final char HEXADECIMAL_INTEGER_UPPER = 'X';

        // Float, Double, BigDecimal
        // (and associated primitives due to autoboxing)
        static final char SCIENTIFIC          = 'e';
        static final char SCIENTIFIC_UPPER    = 'E';
        static final char GENERAL             = 'g';
        static final char GENERAL_UPPER       = 'G';
        static final char DECIMAL_FLOAT       = 'f';
        static final char HEXADECIMAL_FLOAT   = 'a';
        static final char HEXADECIMAL_FLOAT_UPPER = 'A';

        // Character, Byte, Short, Integer
        // (and associated primitives due to autoboxing)
        static final char CHARACTER           = 'c';
        static final char CHARACTER_UPPER     = 'C';

        // java.util.Date, java.util.Calendar, long
        static final char DATE_TIME           = 't';
        static final char DATE_TIME_UPPER     = 'T';

        // if (arg.TYPE != boolean) return boolean
        // if (arg != null) return true; else return false;
        static final char BOOLEAN             = 'b';
        static final char BOOLEAN_UPPER       = 'B';
        // if (arg instanceof Formattable) arg.formatTo()
        // else arg.toString();
        static final char STRING              = 's';
        static final char STRING_UPPER        = 'S';
        // arg.hashCode()
        static final char HASHCODE            = 'h';
        static final char HASHCODE_UPPER      = 'H';

        static final char LINE_SEPARATOR      = 'n';
        static final char PERCENT_SIGN        = '%';

        static boolean isValid(char c) {
            return switch (c) {
                case BOOLEAN,
                     BOOLEAN_UPPER,
                     STRING,
                     STRING_UPPER,
                     HASHCODE,
                     HASHCODE_UPPER,
                     CHARACTER,
                     CHARACTER_UPPER,
                     DECIMAL_INTEGER,
                     OCTAL_INTEGER,
                     HEXADECIMAL_INTEGER,
                     HEXADECIMAL_INTEGER_UPPER,
                     SCIENTIFIC,
                     SCIENTIFIC_UPPER,
                     GENERAL,
                     GENERAL_UPPER,
                     DECIMAL_FLOAT,
                     HEXADECIMAL_FLOAT,
                     HEXADECIMAL_FLOAT_UPPER,
                     LINE_SEPARATOR,
                     PERCENT_SIGN -> true;
                default -> false;
            };
        }

        // Returns true iff the Conversion is applicable to all objects.
        static boolean isGeneral(char c) {
            return switch (c) {
                case BOOLEAN,
                     BOOLEAN_UPPER,
                     STRING,
                     STRING_UPPER,
                     HASHCODE,
                     HASHCODE_UPPER -> true;
                default -> false;
            };
        }

        // Returns true iff the Conversion is applicable to character.
        static boolean isCharacter(char c) {
            return switch (c) {
                case CHARACTER,
                     CHARACTER_UPPER -> true;
                default -> false;
            };
        }

        // Returns true iff the Conversion is an integer type.
        static boolean isInteger(char c) {
            return switch (c) {
                case DECIMAL_INTEGER,
                     OCTAL_INTEGER,
                     HEXADECIMAL_INTEGER,
                     HEXADECIMAL_INTEGER_UPPER -> true;
                default -> false;
            };
        }

        // Returns true iff the Conversion is a floating-point type.
        static boolean isFloat(char c) {
            return switch (c) {
                case SCIENTIFIC,
                     SCIENTIFIC_UPPER,
                     GENERAL,
                     GENERAL_UPPER,
                     DECIMAL_FLOAT,
                     HEXADECIMAL_FLOAT,
                     HEXADECIMAL_FLOAT_UPPER -> true;
                default -> false;
            };
        }

        // Returns true iff the Conversion does not require an argument
        static boolean isText(char c) {
            return switch (c) {
                case LINE_SEPARATOR, PERCENT_SIGN -> true;
                default -> false;
            };
        }
    }

    private static class DateTime {
        static final char HOUR_OF_DAY_0 = 'H'; // (00 - 23)
        static final char HOUR_0        = 'I'; // (01 - 12)
        static final char HOUR_OF_DAY   = 'k'; // (0 - 23) -- like H
        static final char HOUR          = 'l'; // (1 - 12) -- like I
        static final char MINUTE        = 'M'; // (00 - 59)
        static final char NANOSECOND    = 'N'; // (000000000 - 999999999)
        static final char MILLISECOND   = 'L'; // jdk, not in gnu (000 - 999)
        static final char MILLISECOND_SINCE_EPOCH = 'Q'; // (0 - 99...?)
        static final char AM_PM         = 'p'; // (am or pm)
        static final char SECONDS_SINCE_EPOCH = 's'; // (0 - 99...?)
        static final char SECOND        = 'S'; // (00 - 60 - leap second)
        static final char TIME          = 'T'; // (24 hour hh:mm:ss)
        static final char ZONE_NUMERIC  = 'z'; // (-1200 - +1200) - ls minus?
        static final char ZONE          = 'Z'; // (symbol)

        // Date
        static final char NAME_OF_DAY_ABBREV    = 'a'; // 'a'
        static final char NAME_OF_DAY           = 'A'; // 'A'
        static final char NAME_OF_MONTH_ABBREV  = 'b'; // 'b'
        static final char NAME_OF_MONTH         = 'B'; // 'B'
        static final char CENTURY               = 'C'; // (00 - 99)
        static final char DAY_OF_MONTH_0        = 'd'; // (01 - 31)
        static final char DAY_OF_MONTH          = 'e'; // (1 - 31) -- like d
        static final char NAME_OF_MONTH_ABBREV_X  = 'h'; // -- same b
        static final char DAY_OF_YEAR           = 'j'; // (001 - 366)
        static final char MONTH                 = 'm'; // (01 - 12)
        static final char YEAR_2                = 'y'; // (00 - 99)
        static final char YEAR_4                = 'Y'; // (0000 - 9999)

        // Composites
        static final char TIME_12_HOUR  = 'r'; // (hh:mm:ss [AP]M)
        static final char TIME_24_HOUR  = 'R'; // (hh:mm same as %H:%M)
        static final char DATE_TIME             = 'c';
        // (Sat Nov 04 12:02:33 EST 1999)
        static final char DATE                  = 'D'; // (mm/dd/yy)
        static final char ISO_STANDARD_DATE     = 'F'; // (%Y-%m-%d)

        static boolean isValid(char c) {
            return switch (c) {
                case HOUR_OF_DAY_0, HOUR_0, HOUR_OF_DAY, HOUR, MINUTE, NANOSECOND, MILLISECOND, MILLISECOND_SINCE_EPOCH,
                     AM_PM, SECONDS_SINCE_EPOCH, SECOND, TIME, ZONE_NUMERIC, ZONE -> true;
                // Date
                case NAME_OF_DAY_ABBREV, NAME_OF_DAY, NAME_OF_MONTH_ABBREV, NAME_OF_MONTH, CENTURY, DAY_OF_MONTH_0,
                     DAY_OF_MONTH, NAME_OF_MONTH_ABBREV_X, DAY_OF_YEAR, MONTH, YEAR_2, YEAR_4 -> true;
                // Composites
                case TIME_12_HOUR, TIME_24_HOUR, DATE_TIME, DATE, ISO_STANDARD_DATE -> true;
                default -> false;
            };
        }
    }
}
