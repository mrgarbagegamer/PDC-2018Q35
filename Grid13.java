import java.util.ArrayList;
public class Grid13 extends Grid 
{
    void initialize() 
    {
        // Initialize for Q13/Kermit

        for (int row = 2; row <= 4; row += 2)
        {
            for (int col = 1; col <= 14; col++)
            {
                this.grid[row][col] = true;
                Integer[] cell = {row, col};
                // Add the true cells to the trueCells map
                this.trueCells.put(row * 100 + col, new ArrayList<Integer[]>() {{ add(cell); }});
            }
        }

        this.grid[3][0] = true;
        this.trueCells.put(300, new ArrayList<Integer[]>() {{ add(new Integer[] {3, 0}); }});

        this.grid[3][14] = true;
        this.trueCells.put(300, new ArrayList<Integer[]>() {{ add(new Integer[] {3, 14}); }});

    }

}