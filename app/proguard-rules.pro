# Keep JSON logs printing
-keep class com.appforcross.editor.logging.** { *; }
# Don't obfuscate exported classes (Activities)
-keep class com.appforcross.editor.ui.** { *; }
# Model/data classes
-keep class com.appforcross.editor.** { *; }
