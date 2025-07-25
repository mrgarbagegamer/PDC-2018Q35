package com.github.mrgarbagegamer;

import org.jctools.queues.MessagePassingQueue;

/**
 * High-performance circular buffer for worker thread batching.
 * Eliminates ArrayDeque overhead while maintaining the same semantics.
 * This object is now pooled and recycled, with the assumption that only one thread will have access to the object at a time.
 */
public final class WorkBatch implements MessagePassingQueue.Consumer<short[]>, MessagePassingQueue.Supplier<short[]>
{
    private final short[][] buffer;
    private final int capacity;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public WorkBatch(int capacity)
    {
        this.capacity = capacity;
        this.buffer = new short[capacity][];
    }

    /**
     * Adds a combination by copying its contents into the array at the tail of the buffer.
     * This avoids the caller needing to clone and prevents allocation of temporary objects.
     * @param source The source combination array.
     * @return true if the element was added, false if the batch is full.
     */
    public boolean add(short[] source) 
    {
        if (size >= capacity)
        {
            return false;
        }
        if (buffer[tail] == null) buffer[tail] = new short[source.length];
        System.arraycopy(source, 0, buffer[tail], 0, source.length);
        tail = (tail + 1) % capacity;
        size++;
        return true;
    }

    /**
     * Adds a combination by assembling it from a prefix and a final element.
     * This avoids the caller needing to create a temporary full combination array.
     * @param prefix The prefix of the combination.
     * @param lastElement The final element to append.
     * @return true if the element was added, false if the batch is full.
     */
    public boolean add(short[] prefix, short lastElement)
    {
        if (size >= capacity)
        {
            return false;
        }

        short[] dest = buffer[tail];
        if (dest == null)
        {
            dest = new short[prefix.length + 1];
            buffer[tail] = dest;
        }

        System.arraycopy(prefix, 0, dest, 0, prefix.length);
        dest[prefix.length] = lastElement;

        tail = (tail + 1) % capacity;
        size++;
        return true;
    }

    /**
     * Remove and return next combination.
     * @return result if there is a valid combination in the array, null if the batch is empty.
     */
    public short[] poll() 
    {
        if (size == 0)
        {
            return null;
        }
        
        short[] result = buffer[head];
        head = (head + 1) % capacity;
        size--;
        return result;
    }

    /**
     * Check if batch is empty.
     */
    public boolean isEmpty() 
    {
        return size == 0;
    }

    /**
     * Get current batch size.
     */
    public int size() 
    {
        return size;
    }

    /**
     * Get remaining capacity.
     */
    public int remainingCapacity() 
    {
        return capacity - size;
    }

    /**
     * "Clears" the batch for reuse, making sure not to null the previous arrays (as this would force add() to create a new array).
     */
    public void clear() 
    {
        head = 0;
        tail = 0;
        size = 0;
    }

    /**
     * MessagePassingQueue.Consumer implementation for JCTools integration.
     */
    @Override
    public void accept(short[] combination) 
    {
        add(combination);
    }

    /**
     * MessagePassingQueue.Supplier implementation for JCTools integration.
     */
    @Override
    public short[] get() 
    {
        return poll();
    }

    /**
     * Check if batch is full.
     */
    public boolean isFull() 
    {
        return size >= capacity;
    }
}