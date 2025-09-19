package com.github.mrgarbagegamer;

import org.apache.logging.log4j.message.AsynchronouslyFormattable;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.StringBuilderFormattable;

/**
 * CombinationMessage - [Configuration Purpose - e.g., "Q35 puzzle initial state"]
 * 
 * <p>[What this configuration represents and why it exists as a separate class.]</p>
 * 
 * <h2>Configuration Details</h2>
 * <p>[Specific configuration values and their meaning in the domain context.]</p>
 * 
 * <h2>Initialization Strategy</h2>
 * <p>[How values are computed/determined. Pre-computation rationale.]</p>
 * 
 * <h3>6/11 - ~54.5% of documentation completed</h3>
 * 
 * @algorithm [If complex initialization logic is involved]
 * @since [When this configuration was introduced]
 * @see [Related configuration classes]
 */
@AsynchronouslyFormattable
public class CombinationMessage implements Message, StringBuilderFormattable 
{
    /**
     * The combination represented as a <code>short[]</code>. The format of the combination is indicated
     * by the {@link #format} field.
     * 
     * @since 2025.05.31 - CombinationMessage Introduction
     * @see #getCombination(com.github.mrgarbagegamer.Grid.ValueFormat)
     * @see Grid.ValueFormat
     */
    private short[] list;
    /**
     * The format of the combination stored in {@link #list}. This field indicates how the values in the
     * combination should be interpreted.
     * 
     * @since 2025.05.31 - CombinationMessage Introduction
     * @see #convertTo(com.github.mrgarbagegamer.Grid.ValueFormat)
     * @see Grid.ValueFormat
     */
    private Grid.ValueFormat format;

    /**
     * Creates a new CombinationMessage with the specified combination list and format.
     * 
     * @param list the <code>short[]</code> representing the combination.
     * @param format the {@link Grid.ValueFormat} indicating the format of the combination.
     * @throws IllegalArgumentException if the provided format is {@link Grid.ValueFormat#Bitmask}.
     * @since 2025.05.31 - CombinationMessage Introduction
     */
    public CombinationMessage(short[] list, Grid.ValueFormat format) {
        this.list = list;
        this.format = format;
        if (format == Grid.ValueFormat.Bitmask) {
            throw new IllegalArgumentException("Cannot create CombinationMessage with Bitmask format at the moment. Use Index or PackedInt instead.");
        }
    }

    /**
     * Converts the internal combination list to the specified output {@link Grid.ValueFormat format}.
     * This method modifies the internal state of the CombinationMessage instance, changing the format
     * to the desired output format for better readability.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method performs in-place conversion of the combination list, which is efficient in terms of
     * memory usage, avoiding the need for additional allocations. However, it does iterate through the
     * entire list, resulting in a time complexity of O(n), where n is the number of elements in the
     * list. The conversion process involves simple arithmetic operations, which are generally fast,
     * though the number of <code>switch</code> cases and conversions may introduce some overhead.
     * </p>
     * 
     * <p>
     * For greater efficiency, one would ideally create specialized methods for each conversion path
     * (e.g., {@link Grid.ValueFormat#Index} to {@link Grid.ValueFormat#PackedInt} and vice versa). This
     * would reduce the number of conditional checks during conversion, leading to faster execution
     * times, at the cost of increased code complexity and maintenance, but with Java, those sacrifices
     * are often worth it.
     * </p>
     * 
     * @param outputFormat the desired {@link Grid.ValueFormat} to convert the combination to.
     * @throws IllegalArgumentException if the provided output format is
     *                                  {@link Grid.ValueFormat#Bitmask}.
     * @since 2025.07.18 - Cell Format Support
     * @performance O(n) where n is the number of elements in {@link #list}.
     * @threading Not thread-safe; should be called in a single-threaded context or synchronized
     *            externally.
     * @memory In-place conversion; does not allocate additional memory for the list.
     * @see Grid#packedToIndex(short)
     * @see Grid#indexToPacked(short)
     */
    public void convertTo(Grid.ValueFormat outputFormat) {
        if (format == outputFormat) {
            return; // No conversion needed
        } else if (outputFormat == Grid.ValueFormat.Bitmask) {
            throw new IllegalArgumentException("Cannot convert to Bitmask format at the moment.");
        }

        switch (outputFormat) {
            case Index:
                if (format == Grid.ValueFormat.PackedInt) {
                    for (int i = 0; i < list.length; i++) {
                        list[i] = Grid.packedToIndex(list[i]); // Convert packed int to index format
                    }
                    format = Grid.ValueFormat.Index; // Update format to index
                }
                break;
            case PackedInt:
                if (format == Grid.ValueFormat.Index) {
                    for (int i = 0; i < list.length; i++) {
                        list[i] = (short) Grid.indexToPacked(list[i]); // Convert index to packed int format
                    }
                    format = Grid.ValueFormat.PackedInt; // Update format to packed int
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported output format: " + outputFormat);
        }
    }

    /**
     * Formats the combination into a human-readable string representation onto the provided
     * {@link java.lang.StringBuilder StringBuilder}. Through the use of a provided buffer,
     * this method avoids unnecessary memory allocations, making it suitable for high-performance
     * logging scenarios.
     * 
     * @param buffer the {@link java.lang.StringBuilder StringBuilder} to append the formatted message to.
     * @throws NullPointerException (implicitly) if the provided buffer is null.
     * @since 2025.05.31 - CombinationMessage Introduction
     * @performance O(n) where n is the number of elements in {@link #list}.
     * @threading Thread-safe, as the method does not modify shared state.
     * @memory Does not allocate.
     * @see org.apache.logging.log4j.util.StringBuilderFormattable
     * @see org.apache.logging.log4j.util.StringBuilderFormattable#formatTo(StringBuilder)
     */
    @Override
    public void formatTo(StringBuilder buffer) {
        if (format != Grid.ValueFormat.PackedInt) {
            convertTo(Grid.ValueFormat.PackedInt); // Ensure the format is PackedInt for human-readable output 
        }
        buffer.append('[');
        for (int i = 0, size = list.length; i < size; i++) {
            if (i > 0) {
                buffer.append(',');
                buffer.append(' ');
            }
            buffer.append(list[i]);
        }
        buffer.append(']');
    }

    @Override
    public String getFormattedMessage() 
    {
        // Fallback for legacy APIs; not GC-free, but rarely used by Log4j2 internals
        StringBuilder sb = new StringBuilder();
        formatTo(sb);
        return sb.toString();
    }

    /**
     * Returns the combination in the specified {@link Grid.ValueFormat format}. If the current format
     * of the combination does not match the requested output format, it will be converted in-place to
     * the desired format before being returned.
     * 
     * <h3>Performance Considerations</h3>
     * <p>
     * This method may perform an in-place conversion of the combination list if the current format does
     * not match the requested output format. The conversion process has a time complexity of O(n),
     * where n is the number of elements in the list, due to the need to iterate through the entire
     * list. The conversion involves simple arithmetic operations, which are generally fast, but the
     * number of <code>switch</code> cases and conversions may introduce some overhead.
     * </p>
     * 
     * <p>
     * For optimal performance, it may be beneficial to create specialized methods for each format to
     * avoid the overhead of conditional checks during conversion. However, this would increase code
     * complexity and maintenance efforts. The current design prioritizes simplicity and
     * maintainability.
     * </p>
     * 
     * @param outputFormat the desired {@link Grid.ValueFormat} for the returned combination.
     * @return the combination as a <code>short[]</code> in the specified format.
     * @throws IllegalArgumentException if the provided output format is {@link Grid.ValueFormat#Bitmask}.
     * @since 2025.05.31 - CombinationMessage Introduction
     * @performance O(n) where n is the number of elements in {@link #list} if conversion is needed; O(1) if no conversion is needed.
     * @threading Not thread-safe; should be called in a single-threaded context or synchronized externally.
     * @memory In-place conversion; does not allocate additional memory for the list.
     * @see #convertTo(Grid.ValueFormat)
     */
    public short[] getCombination(Grid.ValueFormat outputFormat) {
        if (outputFormat == Grid.ValueFormat.Bitmask) {
            // TODO: Look at making this possible, either by using an array of int bitmasks or making the return type a long[]
            throw new IllegalArgumentException("Cannot convert to Bitmask format.");
        } else if (outputFormat != format) {
            convertTo(outputFormat); // Convert to the requested format if needed
        }
        return list; // Return the combination in the requested format
    }

    @Override
    public String getFormat() 
    {
        return null;
    }

    @Override
    public Object[] getParameters() 
    {
        return null;
    }

    @Override
    public Throwable getThrowable() 
    {
        return null;
    }
}