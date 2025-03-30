import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

public class CombinationGenerator {

    public static <T> Set<Set<T>> generateCombinations(HashSet<T> set, int k) {
        Set<Set<T>> combinations = new HashSet<>();
        List<T> list = new ArrayList<>(set);
        generateCombinationsRecursive(list, k, 0, new HashSet<>(), combinations);
        return combinations;
    }

    private static <T> void generateCombinationsRecursive(List<T> list, int k, int index, Set<T> current, Set<Set<T>> combinations) {
        if (current.size() == k) {
            combinations.add(new HashSet<>(current));
            return;
        }

        if (index >= list.size()) {
            return;
        }
        
        // Include current element
        current.add(list.get(index));
        generateCombinationsRecursive(list, k, index + 1, current, combinations);

        // Exclude current element
        current.remove(list.get(index));
        generateCombinationsRecursive(list, k, index + 1, current, combinations);
    }

    public static void main(String[] args) {
        HashSet<Node> nodeSet = new HashSet<>();
        populateNodeSet(nodeSet);

        int k = 5;
        Set<Set<Node>> combinations = generateCombinations(nodeSet, k);
        System.out.println("Combinations of size " + k + " from the set: " + combinations);
    }

    private static void populateNodeSet(HashSet<Node> set){
        int numRows = 7; // Total rows in the grid
        int[] numCols = {16, 15, 16, 15, 16, 15, 16}; // Number of columns for each row        

        for(int row=0; row < numRows; row++){
            for(int col=0; col < numCols[row]; col++){
                Node node = new Node();
                node.row = row;
                node.col = col;

                set.add(node);
            }
        }
    }
}

class Node {
    int row;
    int col;

    public String toString(){
        return String.format("<%d,%d>", row, col);
    }
}
