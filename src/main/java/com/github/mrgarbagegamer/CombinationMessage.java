package com.github.mrgarbagegamer;

import org.apache.logging.log4j.message.AsynchronouslyFormattable;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.StringBuilderFormattable;

@AsynchronouslyFormattable
public class CombinationMessage implements Message, StringBuilderFormattable 
{
    private int[] list;
    private Grid.ValueFormat format;

    public CombinationMessage(int[] list, Grid.ValueFormat format) 
    {
        this.list = list;
        this.format = format;
        if (format == Grid.ValueFormat.Bitmask)
        {
            throw new IllegalArgumentException("Cannot create CombinationMessage with Bitmask format at the moment. Use Index or PackedInt instead.");
        }
    }

    public void convertTo(Grid.ValueFormat outputFormat) 
    {
        if (format == outputFormat) 
        {
            return; // No conversion needed
        } else if (outputFormat == Grid.ValueFormat.Bitmask) 
        {
            throw new IllegalArgumentException("Cannot convert to Bitmask format at the moment.");
        }

        switch (outputFormat) 
        {
            case Index:
                if (format == Grid.ValueFormat.PackedInt) 
                {
                    for (int i = 0; i < list.length; i++) 
                    {
                        list[i] = Grid.packedToIndex(list[i]); // Convert packed int to index format
                    }
                    format = Grid.ValueFormat.Index; // Update format to index
                }
                break;
            case PackedInt:
                if (format == Grid.ValueFormat.Index) 
                {
                    for (int i = 0; i < list.length; i++) 
                    {
                        list[i] = Grid.indexToPacked(list[i]); // Convert index to packed int format
                    }
                    format = Grid.ValueFormat.PackedInt; // Update format to packed int
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported output format: " + outputFormat);
        }
    }

    @Override
    public void formatTo(StringBuilder buffer)
    {
        if (format != Grid.ValueFormat.PackedInt)
        {
            convertTo(Grid.ValueFormat.PackedInt); // Ensure the format is PackedInt for human-readable output 
        }
        buffer.append('[');
        for (int i = 0, size = list.length; i < size; i++) 
        {
            if (i > 0) 
            {
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

    public int[] getCombination(Grid.ValueFormat outputFormat)
    {
        if (outputFormat == Grid.ValueFormat.Bitmask)
        {
            // TODO: Look at making this possible, either by using an array of int bitmasks or making the return type a long[]
            throw new IllegalArgumentException("Cannot convert to Bitmask format.");
        } else if (outputFormat != format)
        {
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