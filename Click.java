import java.io.Serializable;
import java.util.Objects;

public class Click implements Serializable {
    int row;
    int col;

    Click(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() {
        return this.row;
    }

    public int getCol() {
        return this.col;
    }

    public String toString() {
        return String.format("<%d,%d>", row, col);
    }

    @Override
    public boolean equals(Object obj) {
        boolean equals = false;

        if (this == obj) {
            equals = true;
        } else if (obj == null || getClass() != obj.getClass()) {
            equals = false;
        } else {
            Click anotherClick = (Click) obj;
            equals = (this.row == anotherClick.row && this.col == anotherClick.col);
        }

        return equals;
    }

    @Override
    public int hashCode() {
        /* 
        int rowHash = 7;
        int colHash = 11;

        rowHash = 31 * rowHash + this.row;
        colHash = 31 * colHash + this.col;

        return (rowHash+colHash);
        */
        return Objects.hash(this.row, this.col);
    }
}