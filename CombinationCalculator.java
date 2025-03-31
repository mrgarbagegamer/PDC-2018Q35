import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.ArrayList;

public class CombinationCalculator {

    public static <T> List<List<T>> getCombinations(List<T> elements, int k) {
        if (k < 0 || k > elements.size()) {
            throw new IllegalArgumentException("Invalid value of k");
        }

        List<List<T>> combinations = new ArrayList<>();
        
        // Generate all possible combinations using streams
        IntStream.range(0, 1 << elements.size())
                .filter(i -> Integer.bitCount(i) == k)
                .forEach(i -> {
                    List<T> combination = IntStream.range(0, elements.size())
                            .filter(j -> (i & (1 << j)) != 0)
                            .mapToObj(elements::get)
                            .collect(Collectors.toList());
                    combinations.add(combination);
                });
        return combinations;
    }

    private static void populateNodeColection(List<Node> list){
        int numRows = 7; // Total rows in the grid
        int[] numCols = {16, 15, 16, 15, 16, 15, 16}; // Number of columns for each row        

        for(int row=0; row < numRows; row++){
            for(int col=0; col < numCols[row]; col++){
                Node node = new Node();
                node.row = row;
                node.col = col;

                list.add(node);
            }
        }
    }

    public static void main(String[] args) {
        List<Node> nodeList = new ArrayList<>();
        populateNodeColection(nodeList);

        //int k = 11;
        int k = 5;

        List<List<Node>> combinations = getCombinations(nodeList, k);

        System.out.println("Combinations of " + nodeList + " with size " + k + ":");
        combinations.forEach(System.out::println);
    }
}

class Node {
    int row;
    int col;

    public String toString(){
        return String.format("<%d,%d>", row, col);
    }
}