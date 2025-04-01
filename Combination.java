import java.util.ArrayList;
import java.util.List;

public class Combination {

    public static List<List<Node>> getCombinations(List<Node> arr, int k) {
        List<List<Node>> result = new ArrayList<>();
        generateCombinations(arr, k, 0, new ArrayList<>(), result);
        return result;
    }

    private static void generateCombinations(List<Node> arr, int k, int start, List<Node> current, List<List<Node>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }

        for (int i = start; i < arr.size(); i++) {
            current.add(arr.get(i));
            generateCombinations(arr, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }


    private static void populateNodeColection(List<Node> list){
        int numRows = 7; // Total rows in the grid
        int[] numCols = {16, 15, 16, 15, 16, 15, 16}; // Number of columns for each row        

        for(int row=0; row < numRows; row++){
            for(int col=0; col < numCols[row]; col++){
                list.add( new Node(row, col) );
            }
        }
    }

    public static void main(String[] args) {
        List<Node> nodeList = new ArrayList<>();
        populateNodeColection(nodeList);

        int k = 11;

        List<List<Node>> combinations = getCombinations(nodeList, k);
        /* 
        for (List<Node> combination : combinations) {
            System.out.println(combination);
        }
            */

        System.out.println("Number of combinations = " + combinations.size());
    }
}


class Node {
    int row;
    int col;

    Node(int row, int col){
        this.row = row;
        this.col = col;
    }

    public String toString(){
        return String.format("<%d,%d>", row, col);
    }
}