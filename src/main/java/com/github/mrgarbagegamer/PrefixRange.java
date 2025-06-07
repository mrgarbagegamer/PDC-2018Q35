package com.github.mrgarbagegamer;

public class PrefixRange 
{
    public final int start, end;

    public PrefixRange(int start, int end) 
    {
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() 
    {
        return "PrefixRange[" +
                "start=" + start +
                ", end=" + end +
                ')';
    }

    @Override
    public boolean equals(Object o) 
    {
        if (this == o) return true;
        if (!(o instanceof PrefixRange)) return false;

        PrefixRange that = (PrefixRange) o;

        if (start != that.start) return false;
        return end == that.end;
    }

    @Override
    public int hashCode() 
    {
        int result = start;
        result = 31 * result + end;
        return result;
    }
}