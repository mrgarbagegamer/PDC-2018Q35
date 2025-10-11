package com.github.mrgarbagegamer;

import org.apache.logging.log4j.message.AsynchronouslyFormattable;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.StringBuilderFormattable;

/**
 * A specialized Log4j2 {@link Message} for logging solution combinations with low overhead.
 *
 * <p>
 * This class serves as a high-performance logging mechanism for {@code short[]} combinations found
 * by the solver. By implementing the {@link Message} and {@link StringBuilderFormattable}
 * interfaces, it integrates directly with Log4j2's asynchronous logging architecture. This avoids
 * the performance penalties of traditional logging, such as intermediate {@link String} creation
 * and formatting overhead on critical application threads.
 * </p>
 *
 * <h2>Performance and Architecture</h2>
 * <p>
 * The primary goal of this class is to enable "garbage-free" logging. It acts as a wrapper for a
 * {@code short[]} array, deferring the expensive formatting process to a background I/O thread
 * managed by Log4j2. The {@link #formatTo(StringBuilder)} method writes the combination data
 * directly into a reusable {@link StringBuilder}, preventing object allocation in the application's
 * hot path.
 * </p>
 *
 * <h2>Limitations and Future Improvements</h2>
 * <p>
 * While this class avoids formatting overhead on hot threads, it still allocates a new
 * {@code CombinationMessage} instance for each log event. In high-frequency scenarios, this can
 * create GC pressure. Several strategies were considered to mitigate this:
 * </p>
 * <ul>
 * <li><b>Reusable Messages:</b> The most robust solution is to implement Log4j2's
 * {@link org.apache.logging.log4j.message.ReusableMessage ReusableMessage} pattern, which would
 * allow instances to be recycled by the framework. This is effective but complex to implement
 * correctly.</li>
 * <li><b>Object Pooling:</b> A simpler alternative is a custom object pool. However, a shared pool
 * could introduce contention and potential race conditions if managed improperly.</li>
 * <li><b>Reduced Logging:</b> The simplest option is to log only summary data, but this sacrifices
 * valuable, detailed output.</li>
 * </ul>
 * <p>
 * An alternative to logging altogether is to rely on statistical metrics from the
 * {@link CombinationGeneratorTask}, which could provide progress estimates with less performance
 * impact. Setting this up would be more complex, but is a viable option for future exploration.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is <strong>not</strong> fully thread-safe. The formatting methods are safe for
 * concurrent use, but methods that modify internal state, such as
 * {@link #convertTo(Grid.ValueFormat)}, are not. Instances should be confined to a single thread or
 * properly synchronized if state modifications are required.
 * </p>
 *
 * @see org.apache.logging.log4j.Logger
 * @see org.apache.logging.log4j.LogManager
 * @see org.apache.logging.log4j.LogManager#getLogger()
 * @see <a href="https://logging.apache.org/log4j/2.x/manual/async.html">Log4j2 Asynchronous
 *      Logging</a>
 * @since 2025.05 - CombinationMessage Introduction
 * @performance {@code O(list.length)} for format conversion or string formatting.
 * @threading Apart from the {@link #convertTo(Grid.ValueFormat)} method, which modifies internal
 *            state and is not thread-safe, the class is designed to be thread-safe for concurrent
 *            read operations.
 * @memory Fixed memory footprint after construction; does not allocate except if
 *         {@link #getFormattedMessage()} is called.
 */
@AsynchronouslyFormattable
public class CombinationMessage implements Message, StringBuilderFormattable 
{
    /**
     * The combination data. The interpretation of this data depends on the {@link #format} field.
     * 
     * @see #getCombination(Grid.ValueFormat)
     * @see Grid.ValueFormat
     * @since 2025.05 - CombinationMessage Introduction
     * @performance {@code O(1)} assignments and retrievals, {@code O(list.length)} for format
     *              conversion.
     * @memory Minimal footprint of ~{@code list.length × 2} bytes as a {@code short[]}.
     */
    private short[] list;
    /**
     * The {@link Grid.ValueFormat} of the combination stored in {@link #list}.
     * 
     * @see #convertTo(Grid.ValueFormat)
     * @since 2025.05 - CombinationMessage Introduction
     * @performance {@code O(1)} assignments and retrievals.
     * @memory Minimal footprint; enum reference.
     */
    private Grid.ValueFormat format;

    /**
     * Constructs a new {@code CombinationMessage}.
     *
     * @param list   The {@code short[]} representing the combination.
     * @param format The initial {@link Grid.ValueFormat} of the combination.
     * @throws IllegalArgumentException if the provided format is {@link Grid.ValueFormat#Bitmask},
     *                                  which is not supported for logging.
     * @since 2025.05 - CombinationMessage Introduction
     * @performance {@code O(1)} assignments.
     * @memory Does not allocate (except for a new {@code CombinationMessage} instance); reuses the
     *         passed {@code list}.
     */
    public CombinationMessage(short[] list, Grid.ValueFormat format) {
        // TODO: Consider adding the success/failure status to the message so it can be passed directly.
        if (format == Grid.ValueFormat.Bitmask) {
            throw new IllegalArgumentException("Cannot create CombinationMessage with Bitmask format at the moment. Use Index or PackedInt instead.");
        }
        this.list = list;
        this.format = format;
        
    }

    /**
     * Converts the internal combination list to the specified {@link Grid.ValueFormat}.
     *
     * <p>
     * This method performs an in-place conversion to avoid memory allocation, giving it an
     * {@code O(list.length)} time complexity. For greater efficiency, specialized conversion methods
     * (e.g., a dedicated {@code indexToPackedInt()}) could be created to minimize conditional checks,
     * though this would increase code complexity.
     * </p>
     *
     * @param outputFormat The target {@link Grid.ValueFormat} for the conversion.
     * @throws IllegalArgumentException if conversion to {@link Grid.ValueFormat#Bitmask} is attempted.
     * @see Grid#indexToPacked(short)
     * @see Grid#packedToIndex(short)
     * @since 2025.07 - Cell Format Support
     * @performance {@code O(list.length)} format conversion.
     * @threading Not thread-safe, as it mutates internal state.
     * @memory Does not allocate; performs in-place conversion of {@link #list}.
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
     * Formats the combination as a human-readable string into the provided {@link StringBuilder}.
     *
     * <p>
     * This is the core method for garbage-free logging. It ensures the combination is in a readable
     * {@link Grid.ValueFormat#PackedInt} format and appends it to the buffer.
     * </p>
     *
     * @param buffer The {@code StringBuilder} to which the formatted message will be appended.
     * @throws NullPointerException (implicitly) if the provided buffer is {@code null}.
     * @see StringBuilderFormattable
     * @see StringBuilderFormattable#formatTo(StringBuilder)
     * @since 2025.05 - CombinationMessage Introduction
     * @performance {@code O(list.length)} iteration through the list.
     * @threading Thread-safe, unless format conversion is triggered.
     * @memory Does not allocate; appends directly to the provided {@code StringBuilder}.
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
            // TODO: Consider formatting with leading zeros for better readability (though at a performance cost).
            buffer.append(list[i]);
        }
        buffer.append(']');
    }

    /**
     * Returns the formatted message as a {@link String}.
     *
     * <p>
     * This method serves as a fallback and is not typically called by Log4j2's asynchronous appenders.
     * It necessarily allocates a new {@link String}, defeating the purpose of a garbage-free message.
     * </p>
     *
     * @return The formatted message as a new {@code String}.
     * @see java.lang.StringBuilder#toString()
     * @since 2025.05 - CombinationMessage Introduction
     * @performance {@code O(1)} call to {@link #formatTo(StringBuilder)}
     * @threading Thread-safe, unless conversion is triggered.
     * @memory Allocates a new {@code String} for the return value, but avoids intermediate
     *         {@code String} allocations.
     */
    @Override
    public String getFormattedMessage() {
        // Fallback for legacy APIs; not GC-free, but rarely used by Log4j2 internals
        StringBuilder sb = new StringBuilder();
        formatTo(sb);
        return sb.toString();
    }

    /**
     * Returns the raw combination array, {@link #convertTo(Grid.ValueFormat) converting} it to the
     * specified format if necessary.
     *
     * <p>
     * The current design prioritizes simplicity, but for optimal performance, creating specialized
     * methods for each format conversion would reduce the overhead of conditional checks.
     * </p>
     *
     * @param outputFormat The desired {@link Grid.ValueFormat} for the returned array.
     * @return The {@code short[]} combination in the specified format.
     * @throws IllegalArgumentException if conversion to {@link Grid.ValueFormat#Bitmask} is attempted.
     * @since 2025.05 - CombinationMessage Introduction
     * @performance {@code O(list.length)} if conversion is needed; {@code O(1)} otherwise.
     * @threading Thread-safe, unless conversion is triggered.
     * @memory Does not allocate; returns the internal array, converting in-place if needed.
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

    /**
     * {@inheritDoc}
     * 
     * Not used by this message type.
     *
     * @return Always {@code null}.
     * @since 2025.05 - CombinationMessage Introduction
     * @performance {@code O(1)}.
     * @threading Thread-safe.
     * @memory Does not allocate.
     */
    @Override
    public String getFormat() {
        return null;
    }

    /**
     * {@inheritDoc}
     * 
     * Not used by this message type.
     *
     * @return Always {@code null}.
     * @since 2025.05 - CombinationMessage Introduction
     * @performance {@code O(1)}.
     * @threading Thread-safe.
     * @memory Does not allocate.
     */
    @Override
    public Object[] getParameters() {
        return null;
    }

    /**
     * {@inheritDoc}
     * 
     * Not used by this message type.
     *
     * @return Always {@code null}.
     * @since 2025.05 - CombinationMessage Introduction
     * @performance {@code O(1)}.
     * @threading Thread-safe.
     * @memory Does not allocate.
     */
    @Override
    public Throwable getThrowable() {
        return null;
    }
}