package com.github.mrgarbagegamer;

// TODO: Update Javadoc
/**
 * The main application entry point and orchestrator for the Lights Out puzzle solver.
 *
 * <p>
 * This class is responsible for initializing and coordinating the entire puzzle-solving process. It
 * parses command-line arguments and dispatches execution to the {@link Solver}.
 * </p>
 *
 * @since 2025.04 - Multi-threaded Refactor
 * @performance ~{@code O(1)} for orchestration tasks.
 * @threading Thread-safe; single-threaded orchestration.
 */
public class StartYourMonkeys {

    /**
     * The main entry point for the solver application.
     *
     * <p>
     * Parses command-line arguments, constructs a {@link SolverConfiguration}, and dispatches to
     * {@link Solver#solve()} and {@link Solver#reportResults()}.
     * </p>
     *
     * @param args Command-line arguments:
     *             <ul>
     *             <li>{@code args[0]}: Number of clicks to test (e.g., 17).</li>
     *             <li>{@code args[1]}: Total number of threads to use (e.g., 16).</li>
     *             <li>{@code args[2]}: The puzzle ID to solve (13, 22, or 35).</li>
     *             </ul>
     * @throws IllegalArgumentException if any command-line arguments are invalid.
     * @since 2025.04 - Multi-threaded Refactor
     * @performance ~{@code O(1)} for most orchestration tasks.
     * @threading Single-threaded orchestration.
     */
    public static void main(String[] args) {
        // Parse user inputs with defaults
        final SolverConfiguration config = createConfigFromInputs(args);

        // Dispatch to the solver:
        final Solver solver = Solver.ofConfig(config);
        solver.solve();
        solver.reportResults();
    }

    private static SolverConfiguration createConfigFromInputs(String[] userInput) {
        final SolverConfiguration.Builder configBuilder = SolverConfiguration.builder();

        try {
            switch (userInput.length) {
                case 3:
                    configBuilder.baseGrid(SolverConfiguration
                            .createGridForPuzzle(Integer.parseInt(userInput[2])));
                    // Fall through to set numThreads and numClicks
                case 2:
                    configBuilder.numThreads(Integer.parseInt(userInput[1]));
                    // Fall through to set numClicks
                case 1:
                    configBuilder.numClicks(Integer.parseInt(userInput[0]));
                    break;
                case 0:
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Too many arguments provided. Expected up to 3 arguments.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid input format. Please provide integers only.", e);
        }

        // We let the builder methods handle both defaults and validation
        return configBuilder.build();
    }
}
