# AndroidViewModel/SavedState factories invoke these constructors reflectively.
# Keep only the constructors, allowing the implementation to be optimized.
-keepclassmembers class com.example.contentstudio.** extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
    public <init>(android.app.Application, androidx.lifecycle.SavedStateHandle);
}
