# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# PDFBox checks for the optional JPEG2000 decoder via reflection. The app does
# not bundle that decoder, so retain PDFBox's normal fallback behaviour.
-dontwarn com.gemalto.jp2.JP2Decoder

# Career-agent state is persisted and decoded by Moshi's reflective enum adapter.
# Keep enum constant names so a minified release can restore values such as ACTIVE.
-keepclassmembers enum com.example.tielink.domain.model.** {
    <fields>;
}

# The chat draft uses Moshi's reflective Kotlin adapter. R8 otherwise erases the
# List<PersistedAgentMessage> signature, so restored messages become maps and
# crash when the view model reads them after a release build.
-keepattributes Signature
-keep class com.example.tielink.domain.model.PersistedAgentChatDraft { *; }
-keep class com.example.tielink.domain.model.PersistedAgentMessage { *; }
-keep class com.example.tielink.domain.model.PersistedCardSnapshot { *; }
