package com.github.mrgarbagegamer;

import org.apache.logging.log4j.message.AsynchronouslyFormattable;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.StringBuilderFormattable;
import it.unimi.dsi.fastutil.ints.IntList;

@AsynchronouslyFormattable
public class CombinationMessage implements Message, StringBuilderFormattable 
{
    private final IntList list;

    public CombinationMessage(IntList list) 
    {
        this.list = list;
    }

    @Override
    public void formatTo(StringBuilder buffer) 
    {
        buffer.append('[');
        for (int i = 0, size = list.size(); i < size; i++) 
        {
            if (i > 0) 
            {
                buffer.append(',');
                buffer.append(' ');
            }
            buffer.append(list.getInt(i));
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