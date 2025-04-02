import java.io.Serializable;

public class Click implements Serializable{
    int row;
    int col;

    Click(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow(){
        return this.row;
    }

    public int getCol(){
        return this.col;
    }

    public String toString() {
        return String.format("<%d,%d>", row, col);
    }
}

