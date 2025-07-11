package com.github.mrgarbagegamer;

public class TaskPool 
{
    private final CombinationGeneratorTask[] arrays;
    private final int capacity;
    private int head = 0;
    private int tail = 0;
    private int size = 0;

    public TaskPool(int capacity) 
    {
        this.capacity = capacity;
        this.arrays = new CombinationGeneratorTask[capacity];
    }

    /**
     * Get a CombinationGeneratorTask from the pool, returning a new one if the pool is empty.
     */
    public CombinationGeneratorTask get()
    {
        if (size == 0) return new CombinationGeneratorTask(); // Return a new task if the pool is empty
        
        CombinationGeneratorTask task = arrays[head];
        if (task != null) 
        {
            arrays[head] = null;
            head = (head + 1) % capacity;
            size--;
            return task;
        }

        return new CombinationGeneratorTask(); // Return a new task if the pool is empty
    }

    /**
     * Return a CombinationGeneratorTask to the pool if there's space.
     */
    public void put(CombinationGeneratorTask task)
    {
        if (task == null || size >= capacity) return;
        
        arrays[tail] = task;
        tail = (tail + 1) % capacity;
        size++;
    }

    /**
     * Check if the pool is empty.
     */
    public boolean isEmpty()
    {
        return size == 0;
    }

    /**
     * Get the current pool size.
     */
    public int size()
    {
        return size;
    }
}
