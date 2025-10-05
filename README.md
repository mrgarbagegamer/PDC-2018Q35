# PDC-2018Q35 Lights Out Solver

Performance-optimized brute-force / parallel exploration solver for hexagonal Lights Out style puzzles.

## Javadocs (GitHub Pages)

Once the GitHub Pages site is enabled (Settings -> Pages), the latest Javadocs generated from the `generation-balancing-experiments` branch will be published automatically to the `gh-pages` branch by the workflow in `.github/workflows/publish-javadocs.yml`.

Visit:

https://mrgarbagegamer.github.io/PDC-2018Q35/

(If the page 404s, wait a couple of minutes after the first successful workflow run or confirm Pages is enabled for the `gh-pages` branch.)

## Local Development

Build (skip tests):
```
mvn -DskipTests package
```
Generate only Javadocs (using plugin config):
```
mvn javadoc:javadoc
```
The aggregated API docs will be under `target/site/apidocs` and the Javadoc jar under `target/*-javadoc.jar`.

## Publishing Pipeline Summary
- Trigger: push to `generation-balancing-experiments` affecting Java sources, `pom.xml`, or the workflow file itself.
- Action: builds with JDK 24, runs `mvn -DskipTests package`.
- Output: extracts the `*-javadoc.jar` into a `javadoc/` directory.
- Deploy: commits contents to the `gh-pages` branch via `peaceiris/actions-gh-pages`.

## Custom Javadoc Tags
Custom tags defined and rendered in the Javadocs:
- `@performance` – Performance Characteristics
- `@threading` – Threading Model
- `@algorithm` – Algorithm Details
- `@memory` – Memory Management

These are registered in the `maven-javadoc-plugin` configuration in `pom.xml` so both `mvn package` and `mvn javadoc:javadoc` behave consistently.

## License
(Choose or add a LICENSE file if needed.)
