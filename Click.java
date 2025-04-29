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
}

