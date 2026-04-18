# Preserve file names and line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable

# Replace source file paths with just "SourceFile" to avoid leaking build paths
-renamesourcefileattribute SourceFile

# Disable source-code minification (obfuscation + optimization)
# while still letting R8 shrink unused code/resources.
-dontobfuscate
-dontoptimize
