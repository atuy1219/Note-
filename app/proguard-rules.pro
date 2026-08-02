# Kotlin serialization generates referenced serializers at compile time. Keep model names in
# stack traces while allowing R8 to remove unused code and resources.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
