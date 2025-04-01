public class Click {
    int row;
    int col;

    Click(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public String toString() {
        return String.format("<%d,%d>", row, col);
    }
}

