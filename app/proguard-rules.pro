# R8 rules for the release build.
#
# Kept deliberately short. AGP already generates keeps for everything named in
# the manifest — activities, services, receivers — and Compose and AndroidX
# ship their own consumer rules. What follows is only what this app knows and
# a tool cannot.

# --------------------------------------------------------------------------
# Enum constant names are data, not just identifiers.
#
# Polarity, AttachmentOwner and ReminderOwner are written into the JSON stores
# by name and read back with valueOf(). AccentMode and ThemeMode are
# remembered in the settings the same way, compared with `it.name == value`.
#
# Renaming a constant would not break the build and would not crash: the app
# would simply stop recognising what it had written before the update, fall
# back to its default, and quietly lose the setting or the habit's polarity.
# That is the worst kind of failure to ship, so all enum members in this app
# keep their names.
-keepclassmembers enum de.h4b1ts.app.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --------------------------------------------------------------------------
# The accessibility service exists only in the `full` flavour, and only its
# manifest names it. Harmless in `play`, where the class is not there to keep.
-keep class de.h4b1ts.app.block.H4b1tsAccessibilityService { *; }

# --------------------------------------------------------------------------
# Line numbers in stack traces. Without this a crash report from a tester is
# a list of unnamed frames.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
