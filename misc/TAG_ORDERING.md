# Tag Ordering:
Based on the guidance in the project documentation markdown files ([COMPLETE](COMPLETE_DOCUMENTATION_PRIORITIES_BREAKDOWN.md), [STRATEGY](JAVADOC_DOCUMENTATION_STRATEGY.md)) and Oracle's [How to Write Doc Comments for the Javadoc Tool](https://www.oracle.com/technical-resources/articles/java/javadoc-tool.html) reference

## Ordering for Multiple @see Tags (where a second sorting key is needed, use alphabetical order):
1. @see #field
2. @see #Constructor(Type, Type...)
3. @see #Constructor(Type id, Type id...)
4. @see #method(Type, Type,...)
5. @see #method(Type id, Type, id...)
6. @see Class
7. @see Class#field
8. @see Class#Constructor(Type, Type...)
9. @see Class#Constructor(Type id, Type id)
10. @see Class#method(Type, Type,...)
11. @see Class#method(Type id, Type id,...)
12. @see package.Class
13. @see package.Class#field
14. @see package.Class#Constructor(Type, Type...)
15. @see package.Class#Constructor(Type id, Type id)
16. @see package.Class#method(Type, Type,...)
17. @see package.Class#method(Type id, Type, id)
18. @see package

## Official Tags Order:
1. @param {listed in argument-declaration order}
2. @return
3. @throws (or @exception) {listed in alphabetical order by the exception names}
4. @see {listed in the order shown above}
5. @since
6. @deprecated

## Custom Tags Order:
7. @performance
8. @threading
9. @algorithm
10. @memory
11. @optimization