import java.util.Objects;

public class Click {
    int row;
    int col;

    Click(int row, int col) {
        this.row = row;
        this.col = col;
    }

    @Override
    public String toString() {
        return String.format("<%d,%d>", row, col);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Click)) return false;
        Click click = (Click) o;
        return row == click.row && col == click.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }
}

